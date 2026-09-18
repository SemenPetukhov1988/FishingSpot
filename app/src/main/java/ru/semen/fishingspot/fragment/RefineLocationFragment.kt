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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentRefineLocationBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel
import kotlin.math.* // Для расчетов расстояния

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

    // ✅ РАДИУС ДОВЕРИЯ GPS (250 метров)
    private val MAX_DISTANCE_METERS = 250.0

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

            // ✅ ЗАПУСК ЛОКАЛЬНОЙ ПРОВЕРКИ
            checkWaterLocalAndSave(finalPoint)
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
     * ✅ ПРОВЕРКА ВОДЫ + АНТИ-ЧИТ (ЗАКОММЕНТИРОВАНО ДЛЯ ТЕСТОВ)
     */
    private fun checkWaterLocalAndSave(point: Point) {
        Log.d(TAG, "[LOCAL] Проверка оффлайн-базы...")

        binding.btnDone.isEnabled = false
        binding.btnDone.text = "Проверка..."

        lifecycleScope.launch {
            val isWater = spotViewModel.findNearbyWater(point.latitude, point.longitude)

            val distance = if (lastUserLocation != null) {
                calculateDistance(lastUserLocation!!, point)
            } else {
                Double.MAX_VALUE
            }

            Log.d(TAG, "[CHECK] Расстояние до пользователя: ${distance.toInt()} м.")

            binding.btnDone.isEnabled = true
            binding.btnDone.text = "Готово"

            if (isWater && distance < MAX_DISTANCE_METERS) {
                // ✅ ВСЕ ЧЕСТНО: Вода есть и рыболов рядом
                Log.d(TAG, "[SUCCESS] Точка сохранена как ПУБЛИЧНАЯ")
                saveSpot(point, isPublic)

            } else if (isWater) {
                // ⚠️ ВОДА ЕСТЬ, НО РЫБОЛОВ ДАЛЕКО
                Log.w(TAG, "[WARNING] Рыболов далеко! (${distance.toInt()} м)")
                showFarAwayDialog(point)

            } else {
                // ❌ ВОДЫ НЕТ ВООБЩЕ
                showNoWaterDialog(point)
            }
        }
    }

    /**
     * Диалог для тех, кто пытается схитрить
     */
    private fun showFarAwayDialog(point: Point) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Вы слишком далеко!")
            .setMessage("Расстояние до вас более 250 метров. Сохранить как ЛИЧНУЮ?")
            .setPositiveButton("Сохранить как личную") { d, _ ->
                d.dismiss() // ✅ 1. Сразу закрываем диалог
                if (isAdded) saveSpot(point, false) // 2. Потом сохраняем
            }
            .setNegativeButton("Отмена") { d, _ ->
                d.dismiss() // ✅ Закрываем при отмене
            }
            .create()

        dialog.show()
    }

    private fun showNoWaterDialog(point: Point) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Точка не у воды")
            .setMessage("В базе нет водоемов рядом. Сохранить как ЛИЧНУЮ?")
            .setPositiveButton("Сохранить как личную") { d, _ ->
                d.dismiss() // ✅ 1. Сразу закрываем диалог
                if (isAdded) saveSpot(point, false) // 2. Потом сохраняем
            }
            .setNegativeButton("Уточнить место") { d, _ ->
                d.dismiss() // ✅ Закрываем при отказе
            }
            .create()

        dialog.show()
    }

    private fun saveSpot(point: Point, publicStatus: Boolean) {
        // ✅ ПРОВЕРКА: Фрагмент всё еще прикреплен к активности?
        if (!isAdded) {
            Log.w(TAG, "[SAVE] Пропуск сохранения: фрагмент уже уничтожен.")
            return
        }

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
            // Если навигация не сработала, просто закрываем экран
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Формула Гаверсинуса для расчета расстояния в метрах
     */
    private fun calculateDistance(p1: Point, p2: Point): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
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