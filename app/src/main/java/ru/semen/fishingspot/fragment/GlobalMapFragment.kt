package ru.semen.fishingspot.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentGlobalMapBinding // Убедись, что имя совпадает с твоим XML!
import ru.semen.fishingspot.viewmodel.SpotViewModel

class GlobalMapFragment : Fragment() {

    private var _binding: FragmentGlobalMapBinding? = null
    private val binding get() = _binding!!

    private var locationManager: LocationManager? = null
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var viewAlive = false
    private var isLocationAlreadyShown = false
    private val tapListeners = mutableListOf<MapObjectTapListener>()

    private val DEFAULT_POINT = Point(55.7520, 37.6175)
    private val CITY_ZOOM = 11.0f
    private val MAX_ZOOM_LIMIT = 18.5f

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (!viewAlive || _binding == null) return

            val point = location.position
            Log.d("GLOBAL_MAP", "✅ GPS получен: ${point.latitude}, ${point.longitude}")

            if (!isLocationAlreadyShown) {
                binding.mapView.map.move(
                    CameraPosition(point, CITY_ZOOM, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 1.0f),
                    null
                )
                isLocationAlreadyShown = true
            }
            stopLocationUpdates()
        }

        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlobalMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewAlive = true
        isLocationAlreadyShown = false

        // ✅ ОГРАНИЧЕНИЕ ЗУМА (как в MyMapFragment)
        binding.mapView.map.addCameraListener { _, cameraPosition, _, _ ->
            if (cameraPosition.zoom > MAX_ZOOM_LIMIT) {
                binding.mapView.map.move(
                    CameraPosition(cameraPosition.target, MAX_ZOOM_LIMIT, cameraPosition.azimuth, cameraPosition.tilt),
                    Animation(Animation.Type.SMOOTH, 0.0f),
                    null
                )
            }
        }

        checkLocationPermission()

        // ✅ ПОДПИСКА НА ПУБЛИЧНЫЕ ТОЧКИ ИЗ FIREBASE
        spotViewModel.publicSpots.observe(viewLifecycleOwner) { spots ->
            Log.d("GLOBAL_MAP", "🔄 Обновление публичных точек: ${spots.size}")
            drawPublicMarkers(spots)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()

        // ✅ Запускаем слушатель Firebase только когда фрагмент виден
        spotViewModel.startListeningPublicSpots()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestLocationOnce()
        }
    }

    override fun onStop() {
        binding.mapView.onStop()
        // ✅ Останавливаем слушатель для экономии трафика и батареи
        spotViewModel.stopListeningPublicSpots()
        super.onStop()
    }

    private fun requestLocationOnce() {
        if (locationManager == null) {
            locationManager = MapKitFactory.getInstance().createLocationManager()
        }
        locationManager?.requestSingleUpdate(locationListener)
    }

    private fun stopLocationUpdates() {
        locationManager?.unsubscribe(locationListener)
    }

    private fun checkLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            requestLocationOnce()
        } else {
            requestPermissions(arrayOf(permission), 1002)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationOnce()
        }
    }

    // ✅ ОТРИСОВКА МАРКЕРОВ С РАЗНЫМИ ИКОНКАМИ
    // ✅ ОТРИСОВКА МАРКЕРОВ С РАЗНЫМИ ИКОНКАМИ
    private fun drawPublicMarkers(spots: List<SpotViewModel.PublicSpot>) {
        if (_binding == null) return

        tapListeners.clear()
        binding.mapView.map.mapObjects.clear()

        spots.forEach { spot ->
            val point = Point(spot.latitude, spot.longitude)
            val placemark = binding.mapView.map.mapObjects.addPlacemark(point)

            // 🎨 ВЫБОР ИКОНКИ В ЗАВИСИМОСТИ ОТ СТАТУСА
            val iconRes = if (spot.isVerified) {
                R.drawable.metka78 // Твоя иконка для проверенной точки
            } else {
                R.drawable.lokation // Твоя иконка для сомнительной точки
            }

            placemark.setIcon(ImageProvider.fromResource(requireContext(), iconRes))
            placemark.userData = spot

            // ✅ ОБРАБОТКА КЛИКА ПО МАРКЕРУ
            val listener = object : MapObjectTapListener {
                override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                    val clickedSpot = mapObject.userData as? SpotViewModel.PublicSpot ?: return false

                    Log.d("GLOBAL_MAP", "КЛИК по публичной точке! ID: ${clickedSpot.id}, Verified: ${clickedSpot.isVerified}")

                    // ✅ ВОТ ЗДЕСЬ МЫ СОБИРАЕМ "РЮКЗАК" С ДАННЫМИ ДЛЯ ЭКРАНА ДЕТАЛЕЙ
                    val detailsBundle = Bundle().apply {
                        putString("spot_id", clickedSpot.id)
                        putString("spot_name", clickedSpot.name)
                        putString("spot_desc", clickedSpot.description)
                        putDouble("spot_weight", clickedSpot.catchWeight)
                        putLong("spot_created", clickedSpot.createdAt)

                        // ✅ ДОБАВЛЕНЫ КООРДИНАТЫ (чтобы показать их текстом на экране деталей)
                        putDouble("spot_lat", clickedSpot.latitude)
                        putDouble("spot_lon", clickedSpot.longitude)

                        // ✅ ЭТИ ДВЕ СТРОКИ УЖЕ БЫЛИ У ТЕБЯ, ОСТАВЛЯЕМ ИХ
                        putString("spot_author", clickedSpot.authorId)
                        putBoolean("spot_is_verified", clickedSpot.isVerified)
                    }

                    try {
                        // ✅ БЕЗОПАСНАЯ НАВИГАЦИЯ: переходим напрямую по ID фрагмента
                        findNavController().navigate(R.id.spotDetailsFragment, detailsBundle)
                    } catch (e: Exception) {
                        Log.e("GLOBAL_MAP", "Ошибка навигации", e)
                    }
                    return true
                }
            }

            tapListeners.add(listener)
            placemark.addTapListener(listener)
        }
    }

    override fun onDestroyView() {
        viewAlive = false
        tapListeners.clear()
        _binding = null
        super.onDestroyView()
    }
}