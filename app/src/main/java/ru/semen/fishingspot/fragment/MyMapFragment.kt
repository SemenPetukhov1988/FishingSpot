package ru.semen.fishingspot.fragment

import android.Manifest
import android.content.pm.PackageManager
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
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentMapBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MyMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var locationManager: LocationManager? = null
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var lastKnownSpots: List<FishingSpot> = emptyList()
    private val TAG = "MAP_LIFECYCLE"

    private val tapListeners = mutableListOf<MapObjectTapListener>()
    private var viewAlive = false

    private var lastUserLocation: Point? = null
    private var isLocationAlreadyShown = false

    private val DEFAULT_POINT = Point(55.7520, 37.6175) // Москва
    private val CITY_ZOOM = 11.0f
    private val MAX_ZOOM_LIMIT = 18.5f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "👍 Разрешение получено")
            startLocationTracking()
        } else {
            Log.w(TAG, "🚫 Разрешение отклонено")
            hideLoading()
        }
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (!viewAlive || _binding == null) return

            val point = location.position
            lastUserLocation = point

            Log.d(TAG, "✅ GPS получен: ${point.latitude}, ${point.longitude}")

            if (!isLocationAlreadyShown) {
                binding.mapView.map.move(
                    CameraPosition(point, CITY_ZOOM, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 1.0f),
                    null
                )
                isLocationAlreadyShown = true
            }

            binding.mapView.map.mapObjects.addPlacemark(point)
            hideLoading()
            stopLocationUpdates()
        }

        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        viewAlive = true
        isLocationAlreadyShown = false

        // ✅ ОГРАНИЧЕНИЕ ЗУМА ЧЕРЕЗ CAMERA LISTENER
        binding.mapView.map.addCameraListener(object : com.yandex.mapkit.map.CameraListener {
            override fun onCameraPositionChanged(
                map: com.yandex.mapkit.map.Map,
                cameraPosition: CameraPosition,
                reason: com.yandex.mapkit.map.CameraUpdateReason,
                finished: Boolean
            ) {
                if (cameraPosition.zoom > MAX_ZOOM_LIMIT) {
                    map.move(
                        CameraPosition(cameraPosition.target, MAX_ZOOM_LIMIT, cameraPosition.azimuth, cameraPosition.tilt),
                        Animation(Animation.Type.SMOOTH, 0.0f),
                        null
                    )
                }
            }
        })

        checkLocationPermission()

        binding.btnAddPoint.setOnClickListener {
            val cameraPosition = binding.mapView.map.cameraPosition
            val bundle = Bundle().apply {
                putDouble("LAT", cameraPosition.target.latitude)
                putDouble("LON", cameraPosition.target.longitude)
            }
            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_mainTabs_to_addPoint, bundle)
        }

        spotViewModel.allSpots.observe(viewLifecycleOwner) { spots ->
            Log.d(TAG, "LiveData update: ${spots.size} spots")
            lastKnownSpots = spots
            drawMarkers(spots)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        binding.mapView.onStart()

        if (lastUserLocation != null && !isLocationAlreadyShown) {
            Log.d(TAG, "Мгновенный переход на сохраненную позицию")
            binding.mapView.map.move(
                CameraPosition(lastUserLocation!!, CITY_ZOOM, 0.0f, 0.0f),
                Animation(Animation.Type.SMOOTH, 0.5f),
                null
            )
            binding.mapView.map.mapObjects.addPlacemark(lastUserLocation!!)
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
        if (_binding == null) return
        showLoading()

        if (locationManager == null) {
            locationManager = MapKitFactory.getInstance().createLocationManager()
        }

        val instantAnimation = Animation(Animation.Type.SMOOTH, 0.0f)
        binding.mapView.map.move(
            CameraPosition(DEFAULT_POINT, CITY_ZOOM, 0.0f, 0.0f),
            instantAnimation,
            null
        )

        locationManager?.requestSingleUpdate(locationListener)
    }

    private fun checkLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "🔐 Разрешение уже есть")
            startLocationTracking()
        } else {
            Log.d(TAG, "⚠️ Запрашиваем разрешение...")
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.unsubscribe(locationListener)
    }

    private fun showLoading() {
        binding.progressLocation.visibility = View.VISIBLE
        binding.tvLoadingText.visibility = View.VISIBLE
        binding.tvLoadingText.text = "Определяем местоположение..."
    }

    private fun hideLoading() {
        binding.progressLocation.visibility = View.GONE
        binding.tvLoadingText.visibility = View.GONE
    }

    private fun drawMarkers(spots: List<FishingSpot>) {
        if (_binding == null) return

        tapListeners.clear()
        binding.mapView.map.mapObjects.clear()

        spots.forEach { spot ->
            val point = Point(spot.latitude, spot.longitude)
            val placemark = binding.mapView.map.mapObjects.addPlacemark(point)

            placemark.setIcon(com.yandex.runtime.image.ImageProvider.fromResource(
                requireContext(), R.drawable.lokation))

            placemark.userData = spot

            val listener = object : MapObjectTapListener {
                override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                    Log.d(TAG, "КЛИК! ID: ${spot.id}")
                    val clickedSpot = mapObject.userData as? FishingSpot ?: return false

                    val detailsBundle = Bundle().apply {
                        putLong("spot_id", clickedSpot.id)
                        putString("spot_name", clickedSpot.name)
                        putString("spot_desc", clickedSpot.description)
                        putDouble("spot_weight", clickedSpot.catchWeight)
                        putLong("spot_created", clickedSpot.createdAt)
                    }

                    findNavController().navigate(R.id.action_map_to_details, detailsBundle)
                    return true
                }
            }

            tapListeners.add(listener)
            placemark.addTapListener(listener)
        }
    }

    override fun onStop() {
        binding.mapView.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        viewAlive = false
        tapListeners.clear()
        _binding = null
        super.onDestroyView()
    }
}