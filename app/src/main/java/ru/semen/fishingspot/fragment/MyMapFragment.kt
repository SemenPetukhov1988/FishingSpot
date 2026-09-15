package ru.semen.fishingspot.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentMapBinding

class MyMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var locationManager: LocationManager? = null
    private val LOCATION_PERMISSION_CODE = 1001

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (_binding == null) return

            val point = location.position
            Log.d("MAP_DEBUG", "GPS: ${point.latitude}, ${point.longitude}")

            // ✅ Скрываем спиннер и текст, когда получили координаты
            binding.progressLocation.visibility = View.GONE
            binding.tvLoadingText.visibility = View.GONE

            binding.mapView.map.mapObjects.addPlacemark(point)
            binding.mapView.map.move(
                CameraPosition(point, 12.0f, 0.0f, 0.0f),
                Animation(Animation.Type.SMOOTH, 1.2f),
                null
            )
            stopLocationUpdates()
        }

        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Спиннер уже виден по умолчанию из XML, ничего дополнительно показывать не нужно

        binding.btnAddPoint.setOnClickListener {
            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_mainTabs_to_addPoint)
        }

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> requestLocationOnce()
            else -> ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        }
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

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        binding.mapView.onStop()
        stopLocationUpdates()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationOnce()
        }
    }
}