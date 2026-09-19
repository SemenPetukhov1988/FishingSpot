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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentRefineLocationBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel
import kotlin.math.*

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
    private val MAX_DISTANCE_METERS = 250.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRefineLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                binding.refineMapView.map.move(
                    CameraPosition(binding.refineMapView.map.cameraPosition.target, TARGET_ZOOM, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 0.6f), null
                )
                isZoomAdjusted = true
                Toast.makeText(context, "📍 Уточните место на карте", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            checkWaterLocalAndSave(binding.refineMapView.map.cameraPosition.target)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.refineMapView.onStart()
        if (lastUserLocation != null && !isLocationAlreadyShown) {
            binding.refineMapView.map.move(CameraPosition(lastUserLocation!!, CITY_ZOOM, 0.0f, 0.0f), Animation(Animation.Type.SMOOTH, 0.0f), null)
            isLocationAlreadyShown = true
            hideLoading()
        } else if (lastUserLocation == null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        }
    }

    private fun startLocationTracking() {
        showLoading()
        if (locationManager == null) locationManager = MapKitFactory.getInstance().createLocationManager()
        binding.refineMapView.map.move(CameraPosition(DEFAULT_POINT, CITY_ZOOM, 0.0f, 0.0f), Animation(Animation.Type.SMOOTH, 0.0f), null)
        locationManager?.requestSingleUpdate(locationListener)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            if (_binding == null) return
            lastUserLocation = location.position
            if (!isLocationAlreadyShown) {
                binding.refineMapView.map.move(CameraPosition(location.position, CITY_ZOOM, 0.0f, 0.0f), Animation(Animation.Type.SMOOTH, 1.0f), null)
                isLocationAlreadyShown = true
            }
            hideLoading()
            stopLocationUpdates()
        }
        override fun onLocationStatusUpdated(status: com.yandex.mapkit.location.LocationStatus) {}
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            Toast.makeText(context, "Нет прав на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() { locationManager?.unsubscribe(locationListener) }
    private fun showLoading() { binding.progressLocation.visibility = View.VISIBLE; binding.tvLoadingText.visibility = View.VISIBLE }
    private fun hideLoading() { binding.progressLocation.visibility = View.GONE; binding.tvLoadingText.visibility = View.GONE }

    // --------------------------------------------------------------------------
    // ✅ ГЛАВНАЯ ЛОГИКА ПРОВЕРКИ И СОХРАНЕНИЯ
    // --------------------------------------------------------------------------
    /**
     * ✅ ГЛАВНАЯ ЛОГИКА ПРОВЕРКИ И СОХРАНЕНИЯ
     */
    private fun checkWaterLocalAndSave(point: Point) {
        lifecycleScope.launch {
            binding.btnDone.isEnabled = false
            binding.btnDone.text = "Проверка..."

            // СЦЕНАРИЙ 1: ЛИЧНАЯ ТОЧКА (флаг снят) -> Только Room
            if (!isPublic) {
                saveToRoomOnly(point, isPublic = false, isVerified = false)
                return@launch
            }

            // СЦЕНАРИИ ДЛЯ ПУБЛИЧНОЙ ТОЧКИ
            val isWaterInDb = spotViewModel.findNearbyWater(point.latitude, point.longitude)
            val distance = if (lastUserLocation != null)
                calculateDistance(lastUserLocation!!, point) else Double.MAX_VALUE

            when {
                // ✅ 2A. Вода есть + Рыбак рядом -> Проверенная (Room + Firebase)
                isWaterInDb && distance < MAX_DISTANCE_METERS -> {
                    Log.d(TAG, "[VERIFIED] Вода в БД + GPS совпал")
                    saveToRoomAndFirebase(point, isPublic = true, isVerified = true)
                }

                // ⚠️ 2B. Воды нет в БД, НО Рыбак рядом -> Сомнительная
                !isWaterInDb && distance < MAX_DISTANCE_METERS -> {
                    Log.w(TAG, "[SUSPICIOUS] GPS совпал, но воды нет в БД")
                    // Показываем диалог вместо мгновенного сохранения
                    showSuspiciousDialog(point)
                }

                // ❌ 2C. Рыбак далеко -> Предлагаем сохранить как личную
                else -> {
                    Log.e(TAG, "[REJECTED] Рыбак далеко (${distance.toInt()} м)")
                    showFarAwayDialog(point)
                }
            }

            binding.btnDone.isEnabled = true
            binding.btnDone.text = "Готово"
        }
    }

    /**
     * Диалог подтверждения для сомнительной точки
     */
    private fun showSuspiciousDialog(point: Point) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Водоем не найден в базе")
            .setMessage(
                "Рядом с вами нет зарегистрированного водоема. " +
                        "Возможно, это новое или маленькое место.\n\n" +
                        "Точка будет добавлена на общую карту со статусом «Сомнительная» (желтый маркер). " +
                        "Другие рыболовы смогут подтвердить наличие воды здесь."
            )
            .setPositiveButton("Сохранить как сомнительную") { dialog, _ ->
                dialog.dismiss()
                if (isAdded) {
                    saveToRoomAndFirebase(point, isPublic = true, isVerified = false)
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false) // Запрещаем закрытие по клику вне окна
            .create()
            .show()
    }

    /** Сохранение ТОЛЬКО в Room (Личные или отклоненные) */
    /** Сохранение ТОЛЬКО в Room (Личные или отклоненные) */
    // ✅ Убрали suspend, так как ViewModel сама управляет корутиной
    private fun saveToRoomOnly(point: Point, isPublic: Boolean, isVerified: Boolean) {
        spotViewModel.addFullSpot(
            lat = point.latitude, lon = point.longitude, name = spotName,
            description = spotDesc, weight = spotWeight, isPublic = isPublic,
            isVerified = isVerified, photoPath = photoPath
        )
        navigateBack()
    }

    /** Сохранение в Room + Отправка в Firebase (Публичные) */
    // ✅ Тоже убираем suspend. Внутренний launch для Firebase оставляем.
    private fun saveToRoomAndFirebase(point: Point, isPublic: Boolean, isVerified: Boolean) {
        // 1. Сначала гарантированно сохраняем локально
        spotViewModel.addFullSpot(
            lat = point.latitude, lon = point.longitude, name = spotName,
            description = spotDesc, weight = spotWeight, isPublic = isPublic,
            isVerified = isVerified, photoPath = photoPath
        )

        // 2. 🔥 ЗАГЛУШКА ДЛЯ FIREBASE (запускаем отдельную корутину)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                /*
                 * TODO: Подключить Firebase Repository
                 * val publicSpot = localSpot.toPublicSpot(currentUserId)
                 * firebaseRepository.uploadSpot(publicSpot)
                 */
                Log.d(TAG, "[FIREBASE STUB] Отправка точки verified=$isVerified...")
            } catch (e: Exception) {
                Log.e(TAG, "[FIREBASE STUB] Ошибка отправки", e)
            }
        }

        navigateBack()
    }
    /** Диалог при нарушении дистанции */
    private fun showFarAwayDialog(point: Point) {
        AlertDialog.Builder(requireContext())
            .setTitle("Вы слишком далеко!")
            .setMessage("Расстояние > 250м. Сохранить как ЛИЧНУЮ точку?")
            .setPositiveButton("Сохранить как личную") { d, _ ->
                d.dismiss()
                if (isAdded) saveToRoomOnly(point, isPublic = false, isVerified = false)
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .create().show()
    }

    private fun navigateBack() {
        try { requireActivity().findNavController(R.id.fragmentContainer).navigate(R.id.action_refine_to_tabs) }
        catch (e: Exception) { requireActivity().onBackPressedDispatcher.onBackPressed() }
    }

    private fun calculateDistance(p1: Point, p2: Point): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onStop() { binding.refineMapView.onStop(); super.onStop() }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}