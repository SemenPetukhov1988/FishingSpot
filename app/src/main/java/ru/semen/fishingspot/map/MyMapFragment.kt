package ru.semen.fishingspot.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentMapBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MyMapFragment : Fragment() {

    companion object {
        var returnLat: Double? = null
        var returnLon: Double? = null
        var returnZoom: Double? = null
        var pendingReturnMode: SpotViewModel.MapMode? = null
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val spotViewModel: SpotViewModel by activityViewModels()
    private val TAG = "MAP_LIFECYCLE"

    private var lastUserLocation: GeoPoint? = null
    private var isLocationAlreadyShown = false
    private var locationOverlay: MyLocationNewOverlay? = null
    private var lastShownMode: SpotViewModel.MapMode? = null

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

        val currentMode = spotViewModel.mapMode.value

        if (returnLat != null && returnLon != null && pendingReturnMode == currentMode) {
            Log.d(TAG, "📍 Возврат из деталей: восстанавливаем камеру, zoom=${returnZoom}")
            mapView.controller.setZoom(returnZoom ?: CITY_ZOOM)
            mapView.controller.setCenter(GeoPoint(returnLat!!, returnLon!!))
            isLocationAlreadyShown = true
            hideLoading()
        } else {
            Log.d(TAG, "🔄 Свежий вход/смена режима. Сброс сохранённой камеры.")
            clearPendingCamera()
            checkLocationPermission()
        }

        lastShownMode = currentMode

        spotViewModel.mapMode.observe(viewLifecycleOwner) { mode ->
            if (_binding == null) return@observe
            if (mode != lastShownMode) {
                Log.d(TAG, "🔀 Смена режима: ${lastShownMode} → ${mode}. Сброс камеры.")
                lastShownMode = mode
                clearPendingCamera()
                resetCameraForModeChange()
            }
        }

        val navigateBackToCurrentTab = Runnable {
            val currentTabId = spotViewModel.selectedTabId
            val bundle = Bundle().apply { putInt("active_tab", currentTabId) }
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mainTabsFragment, inclusive = false)
                .setLaunchSingleTop(true)
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
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { navigateBackToCurrentTab.run() }
            }
        )

        spotViewModel.currentSpots.observe(viewLifecycleOwner) { spots ->
            Log.d(TAG, "LiveData: ${spots.size} spots (${spotViewModel.mapMode.value})")
            drawMarkers(spots)
        }

        spotViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (_binding == null || message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun clearPendingCamera() {
        returnLat = null
        returnLon = null
        returnZoom = null
        pendingReturnMode = null
    }

    private fun resetCameraForModeChange() {
        if (_binding == null) return
        val mapView = binding.mapView
        isLocationAlreadyShown = false

        val target = lastUserLocation ?: DEFAULT_POINT
        mapView.controller.setCenter(target)
        mapView.controller.setZoom(CITY_ZOOM)

        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && lastUserLocation == null) {
            startLocationTracking()
        } else {
            hideLoading()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        spotViewModel.refreshMap()

        if (!isLocationAlreadyShown && _binding != null) {
            if (lastUserLocation != null) {
                binding.mapView.controller.setCenter(lastUserLocation)
                binding.mapView.controller.setZoom(CITY_ZOOM)
                isLocationAlreadyShown = true
                hideLoading()
            } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.mapView?.onPause()
    }

    override fun onStop() {
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
                Log.d(TAG, "⏱️ GPS таймаут")
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

    // ✅ ОБНОВЛЕННЫЙ МЕТОД ОТРИСОВКИ МАРКЕРОВ С АВТОРОМ
    private fun drawMarkers(spots: List<FishingSpot>) {
        if (_binding == null) return
        val mapView = binding.mapView

        // Удаляем ТОЛЬКО наши кастомные оверлеи, оставляя геолокацию
        mapView.overlays.removeAll { it is AuthorMarkerOverlay }

        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.metka4567)

        spots.forEach { spot ->
            val overlay = AuthorMarkerOverlay(
                spot = spot,
                iconDrawable = icon,
                onClick = {
                    Log.d(TAG, "КЛИК! ${spot.documentId}")

                    returnLat = spot.latitude
                    returnLon = spot.longitude
                    returnZoom = mapView.zoomLevelDouble
                    pendingReturnMode = spotViewModel.mapMode.value

                    val detailsBundle = Bundle().apply {
                        putString("document_id", spot.documentId)
                        putInt("source_tab", spotViewModel.selectedTabId)
                    }
                    findNavController().navigate(R.id.action_map_to_details, detailsBundle)
                }
            )
            mapView.overlays.add(overlay)
        }

        mapView.invalidate()
    }

    // ✅ КАСТОМНЫЙ ОВЕРЛЕЙ ДЛЯ МАРКЕРА С ТЕКСТОМ АВТОРА
    inner class AuthorMarkerOverlay(
        private val spot: FishingSpot,
        private val iconDrawable: Drawable?,
        private val onClick: () -> Unit
    ) : Overlay() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return

            val point = GeoPoint(spot.latitude, spot.longitude)
            val screenPos = Point()
            mapView.projection.toPixels(point, screenPos)

            iconDrawable?.let { drawable ->
                val w = drawable.intrinsicWidth
                val h = drawable.intrinsicHeight
                drawable.setBounds(screenPos.x - w / 2, screenPos.y - h, screenPos.x + w / 2, screenPos.y)
                drawable.draw(canvas)

                canvas.drawText(
                    spot.authorName.ifEmpty { "Неизвестно" },
                    screenPos.x.toFloat(),
                    screenPos.y + 16f,
                    paint
                )
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView): Boolean {
            val tapX = e?.x?.toInt() ?: 0
            val tapY = e?.y?.toInt() ?: 0
            val tapPoint = Point(tapX, tapY)

            val markerScreen = Point()
            mapView.projection.toPixels(GeoPoint(spot.latitude, spot.longitude), markerScreen)

            val dx = Math.abs(tapX - markerScreen.x)
            val dy = Math.abs(tapY - markerScreen.y)

            if (dx < 40 && dy < 60) {
                onClick()
                return true
            }
            return false
        }
    }

    override fun onDestroyView() {
        locationOverlay?.disableMyLocation()
        _binding = null
        super.onDestroyView()
    }
}