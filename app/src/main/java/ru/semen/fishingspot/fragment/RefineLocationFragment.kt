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
import kotlinx.coroutines.launch
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentRefineLocationBinding
import ru.semen.fishingspot.utils.NetworkMonitor
import ru.semen.fishingspot.viewmodel.SpotViewModel
import kotlin.math.*

class RefineLocationFragment : Fragment() {

    private var _binding: FragmentRefineLocationBinding? = null
    private val binding get() = _binding!!

    // ✅ Данные точки
    private var spotName: String = ""
    private var spotDesc: String = ""
    private var spotWeight: Double = 0.0
    private var spotVisibility: String = "private"
    private var spotCreatedAt: Long = System.currentTimeMillis()
    private var fishType: String? = null
    private var photoPath: String? = null

    // ✅ Данные автора
    private var authorName: String = "Рыбак"
    private var authorId: String = "unknown_user"

    private val spotViewModel: SpotViewModel by activityViewModels()
    private lateinit var networkMonitor: NetworkMonitor
    private val TAG = "FISHING_DEBUG"

    private var lastUserLocation: IGeoPoint? = null
    private var isLocationAlreadyShown = false
    private var isZoomAdjusted = false

    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433)
    private val CITY_ZOOM = 15.0
    private val TARGET_ZOOM = 17.5
    private val MAX_DISTANCE_METERS = 250.0

    private var locationOverlay: MyLocationNewOverlay? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRefineLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        networkMonitor = NetworkMonitor.getInstance(requireContext())

        // ✅ Читаем все данные из Bundle
        spotName = arguments?.getString("NAME") ?: "Без названия"
        spotDesc = arguments?.getString("DESC") ?: ""
        spotWeight = arguments?.getDouble("WEIGHT") ?: 0.0
        spotVisibility = arguments?.getString("VISIBILITY") ?: "private"
        spotCreatedAt = arguments?.getLong("CREATED_AT") ?: System.currentTimeMillis()
        fishType = arguments?.getString("FISH_TYPE")
        photoPath = arguments?.getString("PHOTO_PATH")

        authorName = arguments?.getString("AUTHOR_NAME") ?: "Рыбак"
        authorId = arguments?.getString("AUTHOR_ID") ?: "unknown_user"

        val mapView = binding.refineMapView
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val copyrightOverlay = CopyrightOverlay(requireContext())
        mapView.overlays.add(copyrightOverlay)

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.enableMyLocation()
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        binding.btnDone.setOnClickListener {
            val currentZoom = mapView.zoomLevel
            if (!isZoomAdjusted && currentZoom < 16.0) {
                mapView.controller.setZoom(TARGET_ZOOM)
                isZoomAdjusted = true
                Toast.makeText(context, "📍 Уточните место на карте", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            checkWaterLocalAndSave(mapView.mapCenter)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.refineMapView.onResume()
        if (lastUserLocation != null && !isLocationAlreadyShown) {
            binding.refineMapView.controller.setCenter(lastUserLocation)
            binding.refineMapView.controller.setZoom(CITY_ZOOM)
            isLocationAlreadyShown = true
            hideLoading()
        } else if (lastUserLocation == null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.refineMapView.onPause()
    }

    private fun startLocationTracking() {
        if (_binding == null) return
        showLoading()
        binding.refineMapView.controller.setCenter(DEFAULT_POINT)
        binding.refineMapView.controller.setZoom(CITY_ZOOM)

        locationOverlay?.runOnFirstFix {
            val location = locationOverlay?.myLocation
            if (location != null) {
                val newLocation = GeoPoint(location.latitude, location.longitude)
                requireActivity().runOnUiThread {
                    if (_binding != null) {
                        lastUserLocation = newLocation
                        if (!isLocationAlreadyShown) {
                            binding.refineMapView.controller.setCenter(lastUserLocation)
                            binding.refineMapView.controller.setZoom(CITY_ZOOM)
                            isLocationAlreadyShown = true
                        }
                        hideLoading()
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            Toast.makeText(context, "Нет прав на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading() {
        if (_binding != null) {
            binding.progressLocation.visibility = View.VISIBLE
            binding.tvLoadingText.visibility = View.VISIBLE
        }
    }

    private fun hideLoading() {
        if (_binding != null) {
            binding.progressLocation.visibility = View.GONE
            binding.tvLoadingText.visibility = View.GONE
        }
    }

    // =========================================================================
    // ✅ ЛОГИКА МОДЕРАЦИИ И СОХРАНЕНИЯ
    // =========================================================================
    private fun checkWaterLocalAndSave(point: IGeoPoint) {
        lifecycleScope.launch {
            if (_binding == null) return@launch

            // ✅ БЕЗОПАСНАЯ ПРОВЕРКА: явно сравниваем с true
            val isOnline = networkMonitor.value == true
            Log.d("DEBUG_REFINE", "🌐 Проверка сети: isOnline = $isOnline (сырое значение: ${networkMonitor.value})")

            if (!isOnline) {
                Toast.makeText(requireContext(), "⚠️ Для создания точки требуется интернет (статус: ${networkMonitor.value})", Toast.LENGTH_LONG).show()
                binding.btnDone.isEnabled = true
                binding.btnDone.text = "Готово"
                return@launch
            }

            binding.btnDone.isEnabled = false
            binding.btnDone.text = "Проверка..."

            // Если точка не публичная, сохраняем сразу
            if (spotVisibility != "public") {
                saveSpot(point, isVerified = false)
                return@launch
            }

            val isWaterInDb = spotViewModel.findNearbyWater(point.latitude, point.longitude)
            val distance = if (lastUserLocation != null)
                calculateDistance(lastUserLocation!!, point) else Double.MAX_VALUE

            when {
                isWaterInDb && distance < MAX_DISTANCE_METERS -> {
                    Log.d(TAG, "[VERIFIED] Вода в БД + GPS совпал")
                    saveSpot(point, isVerified = true)
                }
                !isWaterInDb && distance < MAX_DISTANCE_METERS -> {
                    Log.w(TAG, "[SUSPICIOUS] GPS совпал, но воды нет в БД")
                    showSuspiciousDialog(point)
                }
                else -> {
                    Log.e(TAG, "[REJECTED] Рыбак далеко (${distance.toInt()} м)")
                    showFarAwayDialog(point)
                }
            }

            if (_binding != null) {
                binding.btnDone.isEnabled = true
                binding.btnDone.text = "Готово"
            }
        }
    }

    private fun showSuspiciousDialog(point: IGeoPoint) {
        if (!isAdded || _binding == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Водоем не найден в базе")
            .setMessage("Рядом с вами нет зарегистрированного водоема. Точка будет добавлена со статусом «Сомнительная».")
            .setPositiveButton("Сохранить как сомнительную") { dialog, _ ->
                dialog.dismiss()
                if (isAdded && _binding != null) {
                    saveSpot(point, isVerified = false)
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                if (_binding != null) {
                    binding.btnDone.isEnabled = true
                    binding.btnDone.text = "Готово"
                }
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun showFarAwayDialog(point: IGeoPoint) {
        if (!isAdded || _binding == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Вы слишком далеко!")
            .setMessage("Расстояние > 250м. Сохранить как ЛИЧНУЮ точку?")
            .setPositiveButton("Сохранить как личную") { d, _ ->
                d.dismiss()
                if (isAdded && _binding != null) {
                    spotVisibility = "private" // Принудительно делаем личным
                    saveSpot(point, isVerified = false)
                }
            }
            .setNegativeButton("Отмена") { d, _ ->
                d.dismiss()
                if (_binding != null) {
                    binding.btnDone.isEnabled = true
                    binding.btnDone.text = "Готово"
                }
            }
            .create().show()
    }

    // ✅ ЕДИНЫЙ МЕТОД СОХРАНЕНИЯ (ViewModel сама решит: Appwrite -> Room)
    private fun saveSpot(point: IGeoPoint, isVerified: Boolean) {
        android.util.Log.d("DEBUG_REFINE", "💾 Отправка точки на сохранение. Автор: $authorName, ID: $authorId")

        spotViewModel.addFullSpot(
            lat = point.latitude,
            lon = point.longitude,
            name = spotName,
            description = spotDesc,
            weight = spotWeight,
            visibility = spotVisibility,
            photoPath = photoPath,
            photoUrl = null, // ✅ ДОБАВЛЕНО: соответствие новой сигнатуре
            fishType = fishType,
            isVerified = isVerified,
            createdAt = spotCreatedAt,
            authorId = authorId,
            authorName = authorName
        )
        navigateBack()
    }

    private fun navigateBack() {
        try {
            requireActivity().findNavController(R.id.fragmentContainer).navigate(R.id.action_refine_to_tabs)
        } catch (e: Exception) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun calculateDistance(p1: IGeoPoint, p2: IGeoPoint): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onDestroyView() {
        locationOverlay?.disableMyLocation()
        _binding = null
        super.onDestroyView()
    }
}