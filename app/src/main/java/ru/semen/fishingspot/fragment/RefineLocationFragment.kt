package ru.semen.fishingspot.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentRefineLocationBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RefineLocationFragment : Fragment() {

    private var _binding: FragmentRefineLocationBinding? = null
    private val binding get() = _binding!!

    private var spotName: String = ""
    private var spotDesc: String = ""
    private var spotWeight: Double = 0.0
    private var isPublic: Boolean = false
    private var photoPath: String? = null

    private val spotViewModel: SpotViewModel by activityViewModels()
    private val TAG = "FISHING_DEBUG"

    private var locationManager: LocationManager? = null
    private var lastUserLocation: Point? = null
    private var isLocationAlreadyShown = false
    private var isZoomAdjusted = false

    private val DEFAULT_POINT = Point(55.7520, 37.6175)
    private val CITY_ZOOM = 15.0f
    private val TARGET_ZOOM = 17.5f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "--- [1] onCreateView ---")
        _binding = FragmentRefineLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "--- [2] onViewCreated ---")

        spotName = arguments?.getString("NAME") ?: "Без названия"
        spotDesc = arguments?.getString("DESC") ?: ""
        spotWeight = arguments?.getDouble("WEIGHT") ?: 0.0
        isPublic = arguments?.getBoolean("IS_PUBLIC") ?: false
        photoPath = arguments?.getString("PHOTO_PATH")

        MapKitFactory.getInstance().onStart()
        binding.refineMapView.onStart()

        checkLocationPermission()

        binding.btnDone.setOnClickListener {
            val currentZoom = binding.refineMapView.map.cameraPosition.zoom

            if (!isZoomAdjusted && currentZoom < 16.0f) {
                Log.d(TAG, "[ZOOM] Зум $currentZoom слишком мал. Приближаем до $TARGET_ZOOM")

                val centerPoint = binding.refineMapView.map.cameraPosition.target
                binding.refineMapView.map.move(
                    CameraPosition(centerPoint, TARGET_ZOOM, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 0.6f),
                    null
                )

                isZoomAdjusted = true
                Toast.makeText(context, "📍 Уточните место: переместите точку точно на берег", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val finalPoint = binding.refineMapView.map.cameraPosition.target
            Log.d(TAG, "--- [CLICK] Готово. Коорд: ${finalPoint.latitude}, ${finalPoint.longitude} ---")
            checkWaterAndSave(finalPoint)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "--- [3] onStart ---")
        binding.refineMapView.onStart()

        if (lastUserLocation != null && !isLocationAlreadyShown) {
            Log.d(TAG, "[GPS] Мгновенный переход на сохраненную позицию")
            binding.refineMapView.map.move(
                CameraPosition(lastUserLocation!!, CITY_ZOOM, 0.0f, 0.0f),
                Animation(Animation.Type.SMOOTH, 0.0f),
                null
            )
            isLocationAlreadyShown = true
            hideLoading()
        } else if (lastUserLocation == null) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking()
            }
        }
    }

    private fun startLocationTracking() {
        Log.d(TAG, "[GPS] Запуск requestSingleUpdate...")
        showLoading()

        if (locationManager == null) {
            locationManager = MapKitFactory.getInstance().createLocationManager()
        }

        binding.refineMapView.map.move(
            CameraPosition(DEFAULT_POINT, CITY_ZOOM, 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 0.0f),
            null
        )

        locationManager?.requestSingleUpdate(locationListener)
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (_binding == null) return
            val point = location.position
            lastUserLocation = point
            Log.d(TAG, "[GPS] ✅ ПОЛУЧЕНЫ КООРДИНАТЫ: ${point.latitude}, ${point.longitude}")

            if (!isLocationAlreadyShown) {
                binding.refineMapView.map.move(
                    CameraPosition(point, CITY_ZOOM, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 1.0f),
                    null
                )
                isLocationAlreadyShown = true
            }
            hideLoading()
            stopLocationUpdates()
        }

        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {
            Log.d(TAG, "[GPS] Статус: $status")
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            Toast.makeText(context, "Нет прав на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() { locationManager?.unsubscribe(locationListener) }

    private fun showLoading() {
        binding.progressLocation.visibility = View.VISIBLE
        binding.tvLoadingText.visibility = View.VISIBLE
        binding.tvLoadingText.text = "Ищем ваше местоположение..."
    }

    private fun hideLoading() {
        binding.progressLocation.visibility = View.GONE
        binding.tvLoadingText.visibility = View.GONE
    }

    /**
     * ✅ СТРОГИЙ ПОИСК ВОДЫ: Только реки и озера. Без фонтанов и труб.
     */
    private fun checkWaterAndSave(point: Point) {
        Log.d(TAG, "[OSM] Начало строгой проверки (радиус 30м)...")

        binding.btnDone.isEnabled = false
        binding.btnDone.text = "Проверка..."
        binding.progressLocation.visibility = View.VISIBLE
        binding.tvLoadingText.visibility = View.VISIBLE
        binding.tvLoadingText.text = "Проверяем водоем..."

        Thread {
            try {
                // ✅ ЗАПРОС ТОЛЬКО НА ПРИРОДНЫЕ ВОДОЕМЫ
                val query = "[out:json][timeout:15];(" +
                        "way[\"waterway\"~\"^(river|stream|canal)$\"](around:30,${point.latitude},${point.longitude});" +
                        "way[\"natural\"=\"water\"][\"water\"~\"^(lake|reservoir|pond|river)$\"](around:30,${point.latitude},${point.longitude});" +
                        "relation[\"natural\"=\"water\"][\"water\"~\"^(lake|reservoir|pond|river)$\"](around:30,${point.latitude},${point.longitude});" +
                        ");out tags;"

                val url = URL("https://overpass-api.de/api/interpreter")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                }

                val postData = "data=" + URLEncoder.encode(query, "UTF-8")
                conn.outputStream.use { it.write(postData.toByteArray()) }

                val responseCode = conn.responseCode
                Log.d(TAG, "[OSM] HTTP Код: $responseCode")

                var hasWater = false
                var isServerError = false

                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }

                    // ✅ ИСПРАВЛЕННАЯ ПРОВЕРКА:
                    // Мы ищем наличие ключа "elements", но исключаем случай пустого массива "elements": []
                    // Также проверяем, что внутри есть хоть какие-то данные (id или type)
                    val hasElementsKey = response.contains("\"elements\"")
                    val isEmptyArray = response.contains("\"elements\":[]") || response.contains("\"elements\": [ ]")

                    // Если ключ есть, массив не пустой ИЛИ есть другие признаки данных
                    hasWater = hasElementsKey && !isEmptyArray

                    // Дополнительная проверка для надежности: ищем реальные данные внутри элементов
                    if (hasWater && !response.contains("\"id\"")) {
                        hasWater = false // Если нет ID, значит это просто пустая структура
                    }

                    Log.d(TAG, "[OSM] Проверка: Ключ=$hasElementsKey, Пусто=$isEmptyArray, Итог=$hasWater")
                    if (hasWater) Log.d(TAG, "[OSM] Данные: ${response.take(200)}")

                } else {
                    isServerError = true
                    Log.e(TAG, "[OSM] Ошибка сервера: $responseCode")
                }

                conn.disconnect()

                requireActivity().runOnUiThread {
                    binding.btnDone.isEnabled = true
                    binding.btnDone.text = "Готово"
                    binding.progressLocation.visibility = View.GONE
                    binding.tvLoadingText.visibility = View.GONE

                    when {
                        isServerError -> {
                            Toast.makeText(context, "Сервер OSM недоступен. Сохранено как личная.", Toast.LENGTH_LONG).show()
                            saveSpot(point, false)
                        }
                        hasWater -> {
                            Log.d(TAG, "[OSM] ✅ ВОДА НАЙДЕНА!")
                            saveSpot(point, isPublic)
                        }
                        else -> {
                            Log.w(TAG, "[OSM] ⚠️ ВОДА НЕ НАЙДЕНА")
                            showNoWaterDialog(point)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[OSM] ❌ ИСКЛЮЧЕНИЕ: ${e.message}")
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    binding.btnDone.isEnabled = true
                    binding.btnDone.text = "Готово"
                    binding.progressLocation.visibility = View.GONE
                    binding.tvLoadingText.visibility = View.GONE

                    Toast.makeText(context, "Ошибка сети. Сохранено как личная.", Toast.LENGTH_LONG).show()
                    saveSpot(point, false)
                }
            }
        }.start()
    }

    private fun showNoWaterDialog(point: Point) {
        AlertDialog.Builder(requireContext())
            .setTitle("Точка не у воды")
            .setMessage("В радиусе 30 метров не найдено рек или озер. Сохранить как ЛИЧНУЮ точку?")
            .setPositiveButton("Сохранить как личную") { _, _ ->
                saveSpot(point, false)
            }
            .setNegativeButton("Уточнить место", null)
            .show()
    }

    private fun saveSpot(point: Point, publicStatus: Boolean) {
        Log.d(TAG, "[SAVE] Lat: ${point.latitude}, Lon: ${point.longitude}, Public: $publicStatus")

        spotViewModel.addFullSpot(
            lat = point.latitude,
            lon = point.longitude,
            name = spotName,
            description = spotDesc,
            weight = spotWeight,
            isPublic = publicStatus,
            photoPath = photoPath
        )

        try {
            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_refine_to_tabs)
        } catch (e: Exception) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onStop() {
        binding.refineMapView.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}