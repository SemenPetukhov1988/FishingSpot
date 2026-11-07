package ru.semen.fishingspot.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433)
    private val CITY_ZOOM = 11.0
    private val MAX_ZOOM_LIMIT = 18.5

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startLocationTracking() else {
            Log.w(TAG, "🚫 Разрешение отклонено")
            hideLoading()
            binding.mapView.controller.setCenter(DEFAULT_POINT)
            binding.mapView.controller.setZoom(CITY_ZOOM)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isLocationAlreadyShown = false

        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setMaxZoomLevel(MAX_ZOOM_LIMIT)
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        // ✅ ЕДИНЫЙ МЕТОД ВОЗВРАТА: Обновляем режим карты + переключаем таб
        val navigateBackToCurrentTab = Runnable {
            val currentTabId = spotViewModel.selectedTabId.value ?: R.id.nav_my_places

            // 1. Явно обновляем режим карты ВМЕСТО того чтобы надеяться на observer
            when (currentTabId) {
                R.id.nav_global_map -> spotViewModel.switchToPublicSpots()
                else -> spotViewModel.switchToMySpots()
            }

            // 2. Переключаем таб (без inclusive=true, чтобы сохранить состояние карты)
            val bundle = Bundle().apply {
                putInt("active_tab", currentTabId)
            }

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mainTabsFragment, inclusive = false)
                .setEnterAnim(R.anim.slide_in_left)
                .setExitAnim(R.anim.slide_out_right)
                .build()

            findNavController().navigate(R.id.mainTabsFragment, bundle, navOptions)
        }

        binding.btnAddPoint.setOnClickListener {
            val center = mapView.mapCenter
            val bundle = Bundle().apply {
                putDouble("LAT", center.latitude)
                putDouble("LON", center.longitude)
            }
            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_mainTabs_to_addPoint, bundle)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBackToCurrentTab.run()
                }
            }
        )

        spotViewModel.currentSpots.observe(viewLifecycleOwner) { spots ->
            Log.d(TAG, "LiveData update: ${spots.size} spots (Режим: ${spotViewModel.mapMode.value})")
            drawMarkers(spots)
        }

        spotViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (_binding == null || message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        // ✅ ПРИНУДИТЕЛЬНОЕ ОБНОВЛЕНИЕ ДАННЫХ ПРИ ВОЗВРАТЕ
        spotViewModel.refreshMap()

        if (lastUserLocation != null && !isLocationAlreadyShown) {
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
        locationOverlay?.disableMyLocation()
        super.onStop()
    }

    private fun startLocationTracking() {
        if (_binding == null) return
        showLoading()
        locationOverlay?.enableMyLocation()
        binding.mapView.controller.setCenter(DEFAULT_POINT)
        binding.mapView.controller.setZoom(CITY_ZOOM)

        val timeoutRunnable = Runnable {
            if (_binding != null && !isLocationAlreadyShown) {
                Log.d(TAG, "⏱️ GPS таймаут (6 сек)")
                hideLoading()
            }
        }
        binding.mapView.postDelayed(timeoutRunnable, 6000)

        locationOverlay?.runOnFirstFix {
            val location = locationOverlay?.myLocation
            if (location != null) {
                val newLocation = GeoPoint(location.latitude, location.longitude)
                requireActivity().runOnUiThread {
                    if (_binding != null) {
                        binding.mapView.removeCallbacks(timeoutRunnable)
                        lastUserLocation = newLocation
                        if (!isLocationAlreadyShown) {
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
            startLocationTracking()
        } else {
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
        mapView.overlays.removeAll { it is Marker }

        val currentTabId = spotViewModel.selectedTabId.value ?: R.id.nav_my_places

        spots.forEach { spot ->
            val point = GeoPoint(spot.latitude, spot.longitude)
            val marker = Marker(mapView)
            marker.position = point
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.metka4567))

            marker.setOnMarkerClickListener { _, _ ->
                Log.d(TAG, "КЛИК! documentId: ${spot.documentId}")
                val detailsBundle = Bundle().apply {
                    putString("document_id", spot.documentId)
                    putInt("source_tab", currentTabId)
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