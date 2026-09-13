package ru.semen.fishingspot.ui.map

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
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import ru.semen.fishingspot.R

class GlobalMapFragment : Fragment() {

    private var mapView: MapView? = null
    private var locationManager: LocationManager? = null
    private val LOCATION_PERMISSION_CODE = 1002 // Уникальный код для этого фрагмента
    private var viewAlive = false

    // Слушатель для однократного определения позиции
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (!viewAlive || mapView == null) return

            val point = location.position
            Log.d("GLOBAL_MAP", "Позиция определена: ${point.latitude}, ${point.longitude}")

            // ✅ Для общей карты берем зум поменьше (11.0), чтобы видеть район целиком
            mapView?.map?.move(
                CameraPosition(point, 11.0f, 0.0f, 0.0f),
                Animation(Animation.Type.SMOOTH, 1.5f),
                null
            )

            stopLocationUpdates()
        }

        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {
            // Логируем статус только в дебаге
            Log.d("GLOBAL_MAP", "Статус геолокации: $status")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_global_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewAlive = true
        mapView = view.findViewById(R.id.mapView)

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                requestLocationOnce()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_CODE
                )
            }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationOnce()
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        stopLocationUpdates()
        mapView?.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        viewAlive = false
        mapView = null
        super.onDestroyView()
    }
}