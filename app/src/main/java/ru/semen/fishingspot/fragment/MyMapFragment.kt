package ru.semen.fishingspot.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentMapBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MyMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val spotViewModel: SpotViewModel by activityViewModels()
    private val TAG = "MAP_LIFECYCLE"

    private var lastUserLocation: GeoPoint? = null
    private var isLocationAlreadyShown = false
    private var locationOverlay: MyLocationNewOverlay? = null

    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433) // Архангельск
    private val CITY_ZOOM = 11.0
    private val MAX_ZOOM_LIMIT = 18.5

    // ✅ СОВРЕМЕННЫЙ И НАДЕЖНЫЙ СПОСОБ ЗАПРОСА РАЗРЕШЕНИЙ
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "👍 Разрешение получено, запускаем GPS")
            startLocationTracking()
        } else {
            Log.w(TAG, "🚫 Разрешение отклонено")
            hideLoading()
            // Если отказали, хотя бы показываем дефолтный город
            binding.mapView.controller.setCenter(DEFAULT_POINT)
            binding.mapView.controller.setZoom(CITY_ZOOM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isLocationAlreadyShown = false

        val mapView = binding.mapView
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setMaxZoomLevel(MAX_ZOOM_LIMIT)

        // ✅ УБИРАЕМ КВАДРАТИКИ: задаем цвет фона, пока грузятся тайлы
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        // 🌟 Инициализируем оверлей, но НЕ включаем поиск локации здесь!
        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        binding.btnAddPoint.setOnClickListener {
            val center = mapView.mapCenter
            val bundle = Bundle().apply {
                putDouble("LAT", center.latitude)
                putDouble("LON", center.longitude)
            }
            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_mainTabs_to_addPoint, bundle)
        }

        spotViewModel.allSpots.observe(viewLifecycleOwner) { spots ->
            Log.d(TAG, "LiveData update: ${spots.size} spots")
            drawMarkers(spots)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onResume()

        if (lastUserLocation != null && !isLocationAlreadyShown) {
            Log.d(TAG, "Мгновенный переход на сохраненную позицию")
            binding.mapView.controller.setCenter(lastUserLocation)
            binding.mapView.controller.setZoom(CITY_ZOOM)
            isLocationAlreadyShown = true
            hideLoading()
        } else if (lastUserLocation == null) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking()
            }
        }
    }

    override fun onStop() {
        binding.mapView.onPause()
        // Отключаем локацию при уходе с экрана для экономии батареи
        locationOverlay?.disableMyLocation()
        super.onStop()
    }

    // ✅ ЗАЩИТА ОТ ЗАВИСАНИЯ ПРИ "ХОЛОДНОМ СТАРТЕ" GPS
    private fun startLocationTracking() {
        if (_binding == null) return
        showLoading()

        // 🌟 ГЛАВНОЕ ИСПРАВЛЕНИЕ: Включаем отслеживание локации ТОЛЬКО тогда,
        // когда разрешение железно предоставлено. Движок OSMDroid успешно свяжется с GPS.
        locationOverlay?.enableMyLocation()

        // 1. Сразу показываем дефолтную точку, чтобы экран не был пустым
        binding.mapView.controller.setCenter(DEFAULT_POINT)
        binding.mapView.controller.setZoom(CITY_ZOOM)

        // 2. Таймер-страховка: если GPS не найден за 6 секунд, убираем загрузку
        val timeoutRunnable = Runnable {
            if (_binding != null && !isLocationAlreadyShown) {
                Log.d(TAG, "⏱️ GPS таймаут (6 сек). Скрываем загрузку, показываем карту.")
                hideLoading()
            }
        }
        binding.mapView.postDelayed(timeoutRunnable, 6000)

        // 3. Ждем реальный GPS
        locationOverlay?.runOnFirstFix {
            val location = locationOverlay?.myLocation
            if (location != null) {
                val newLocation = GeoPoint(location.latitude, location.longitude)

                requireActivity().runOnUiThread {
                    if (_binding != null) {
                        // Отменяем таймер, так как GPS успешно сработал!
                        binding.mapView.removeCallbacks(timeoutRunnable)

                        lastUserLocation = newLocation
                        if (!isLocationAlreadyShown) {
                            // 🌟 Плавная анимация центрирования (выглядит профессионально)
                            binding.mapView.controller.animateTo(lastUserLocation)
                            binding.mapView.controller.setZoom(CITY_ZOOM)
                            isLocationAlreadyShown = true
                        }
                        hideLoading()
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "🔐 Разрешение уже есть, запускаем GPS")
            startLocationTracking()
        } else {
            Log.d(TAG, "⚠️ Запрашиваем разрешение...")
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun showLoading() {
        if (_binding != null) {
            binding.progressLocation.visibility = View.VISIBLE
            binding.tvLoadingText.visibility = View.VISIBLE
            binding.tvLoadingText.text = "Определяем местоположение..."
        }
    }

    private fun hideLoading() {
        if (_binding != null) {
            binding.progressLocation.visibility = View.GONE
            binding.tvLoadingText.visibility = View.GONE
        }
    }

    private fun drawMarkers(spots: List<FishingSpot>) {
        if (_binding == null) return

        val mapView = binding.mapView
        mapView.overlays.removeAll { it is Marker } // Очищаем только маркеры

        spots.forEach { spot ->
            val point = GeoPoint(spot.latitude, spot.longitude)
            val marker = Marker(mapView)
            marker.position = point
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.metka4567))

            marker.setOnMarkerClickListener { _, _ ->
                Log.d(TAG, "КЛИК! ID: ${spot.id}")
                val detailsBundle = Bundle().apply {
                    putLong("spot_id", spot.id)
                    putString("spot_name", spot.name)
                    putString("spot_desc", spot.description)
                    putDouble("spot_weight", spot.catchWeight)
                    putLong("spot_created", spot.createdAt)
                    putDouble("spot_lat", spot.latitude)
                    putDouble("spot_lon", spot.longitude)
                    putString("spot_author", spot.authorId)
                    putBoolean("spot_is_verified", spot.isVerified)
                    putInt("source_tab", R.id.nav_my_places)
                }
                findNavController().navigate(R.id.action_map_to_details, detailsBundle)
                true
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    override fun onDestroyView() {
        locationOverlay?.disableMyLocation()
        _binding = null
        super.onDestroyView()
    }
}