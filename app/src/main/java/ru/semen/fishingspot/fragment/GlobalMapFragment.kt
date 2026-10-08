package ru.semen.fishingspot.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentGlobalMapBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class GlobalMapFragment : Fragment() {

    private var _binding: FragmentGlobalMapBinding? = null
    private val binding get() = _binding!!

    private val spotViewModel: SpotViewModel by activityViewModels()
    private val TAG = "GLOBAL_MAP"

    private var lastUserLocation: GeoPoint? = null
    private var isLocationAlreadyShown = false
    private var locationOverlay: MyLocationNewOverlay? = null

    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433) // Архангельск
    private val CITY_ZOOM = 11.0
    private val MAX_ZOOM_LIMIT = 18.5

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
        Log.d(TAG, "onViewCreated")
        isLocationAlreadyShown = false

        val mapView = binding.mapView
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // ✅ НАТИВНОЕ ОГРАНИЧЕНИЕ ЗУМА
        mapView.setMaxZoomLevel(MAX_ZOOM_LIMIT)

        // ✅ УБИРАЕМ КВАДРАТИКИ: задаем цвет фона, пока грузятся тайлы
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.enableMyLocation()
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        // ✅ ПОДПИСКА НА ПУБЛИЧНЫЕ ТОЧКИ ИЗ FIREBASE
        spotViewModel.publicSpots.observe(viewLifecycleOwner) { spots ->
            Log.d(TAG, "LiveData update: ${spots.size} public spots")
            drawPublicMarkers(spots)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        binding.mapView.onResume()
        spotViewModel.startListeningPublicSpots()

        if (lastUserLocation != null && !isLocationAlreadyShown) {
            Log.d(TAG, "Мгновенный переход на сохраненную позицию")
            binding.mapView.controller.setCenter(lastUserLocation)
            binding.mapView.controller.setZoom(CITY_ZOOM)
            isLocationAlreadyShown = true
        } else if (lastUserLocation == null) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking()
            }
        }
    }

    override fun onStop() {
        binding.mapView.onPause()
        spotViewModel.stopListeningPublicSpots()
        super.onStop()
    }

    // ✅ ТОТ САМЫЙ РАБОЧИЙ МЕТОД ИЗ MyMapFragment
    private fun startLocationTracking() {
        if (_binding == null) return

        binding.mapView.controller.setCenter(DEFAULT_POINT)
        binding.mapView.controller.setZoom(CITY_ZOOM)

        locationOverlay?.runOnFirstFix {
            val location = locationOverlay?.myLocation
            if (location != null) {
                val newLocation = GeoPoint(location.latitude, location.longitude)

                // ✅ КРИТИЧЕСКИ ВАЖНО: Переходим в главный поток для обновления UI
                requireActivity().runOnUiThread {
                    if (_binding != null) {
                        lastUserLocation = newLocation
                        if (!isLocationAlreadyShown) {
                            binding.mapView.controller.setCenter(lastUserLocation)
                            binding.mapView.controller.setZoom(CITY_ZOOM)
                            isLocationAlreadyShown = true
                        }
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "🔐 Разрешение уже есть")
            startLocationTracking()
        } else {
            Log.d(TAG, "⚠️ Запрашиваем разрешение...")
            requestPermissions(arrayOf(permission), 1002)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "👍 Разрешение получено")
            startLocationTracking()
        } else {
            Log.w(TAG, "🚫 Разрешение отклонено")
        }
    }

    private fun drawPublicMarkers(spots: List<SpotViewModel.PublicSpot>) {
        if (_binding == null) return

        val mapView = binding.mapView
        mapView.overlays.removeAll { it is Marker } // Очищаем только маркеры

        spots.forEach { spot ->
            val point = GeoPoint(spot.latitude, spot.longitude)
            val marker = Marker(mapView)
            marker.position = point

            // 🎨 ВЫБОР ИКОНКИ В ЗАВИСИМОСТИ ОТ СТАТУСА
            val iconRes = if (spot.isVerified) R.drawable.metka78 else R.drawable.lokation
            marker.setIcon(ContextCompat.getDrawable(requireContext(), iconRes))

            // ✅ ОБРАБОТКА КЛИКА ПО МАРКЕРУ
            marker.setOnMarkerClickListener { _, _ ->
                Log.d(TAG, "КЛИК! ID: ${spot.id}")

                val detailsBundle = Bundle().apply {
                    putString("spot_id", spot.id)
                    putString("spot_name", spot.name)
                    putString("spot_desc", spot.description)
                    putDouble("spot_weight", spot.catchWeight)
                    putLong("spot_created", spot.createdAt)
                    putDouble("spot_lat", spot.latitude)
                    putDouble("spot_lon", spot.longitude)
                    putString("spot_author", spot.authorId)
                    putBoolean("spot_is_verified", spot.isVerified)
                }

                try {
                    findNavController().navigate(R.id.spotDetailsFragment, detailsBundle)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка навигации", e)
                }
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