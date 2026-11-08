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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentSendFishingSignalBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class SendFishingSignalFragment : Fragment() {

    private var _binding: FragmentSendFishingSignalBinding? = null
    private val binding get() = _binding!!

    private val TAG = "SIGNAL_MAP"
    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433)
    private val CITY_ZOOM = 11.0
    private val TARGET_ZOOM = 17.0

    private val NOTIFICATIONS_COL_ID = "6aef1b7300251aa8ce5b"
    private val DB_ID = "6ad5021a0029a2c88786"
    private val USERS_COL_ID = "6ad5022b003770d2864c" // ✅ ID коллекции пользователей

    private var isZoomAdjusted = false
    private var locationOverlay: MyLocationNewOverlay? = null

    private lateinit var signalType: String
    private lateinit var receiverId: String
    private var senderName: String = "Рыбак"
    private var senderId: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startLocationTracking()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendFishingSignalBinding.inflate(inflater, container, false)
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid_prefs", 0)
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        signalType = arguments?.getString("signal_type") ?: "on_fishing"
        receiverId = arguments?.getString("receiver_id") ?: run {
            findNavController().popBackStack()
            return
        }

        senderName = arguments?.getString("sender_name") ?: UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"
        senderId = arguments?.getString("sender_id") ?: UserSessionManager.getCurrentUserId(requireContext()) ?: ""

        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        val navigateBackToMessages = Runnable {
            val bundle = Bundle().apply {
                putInt("active_tab", R.id.nav_stats)
            }
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mainTabsFragment, inclusive = false)
                .setEnterAnim(R.anim.slide_in_left)
                .setExitAnim(R.anim.slide_out_right)
                .build()
            findNavController().navigate(R.id.mainTabsFragment, bundle, navOptions)
        }

        binding.btnConfirm.setOnClickListener {
            val currentZoom = mapView.zoomLevel

            if (!isZoomAdjusted && currentZoom < TARGET_ZOOM - 1.0) {
                mapView.controller.setZoom(TARGET_ZOOM)
                isZoomAdjusted = true
                Toast.makeText(requireContext(), "📍 Уточните место на карте", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val lat = mapView.mapCenter.latitude
            val lon = mapView.mapCenter.longitude
            val zoom = mapView.zoomLevelDouble

            // ✅ ПЕРЕДАЕМ КООРДИНАТЫ В МЕТОД ОТПРАВКИ
            sendSignalToAppwrite(signalType, receiverId, lat, lon, zoom, navigateBackToMessages)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBackToMessages.run()
                }
            }
        )
    }

    // ✅ ОБНОВЛЕННЫЙ МЕТОД С ПОДДЕРЖКОЙ МАССОВОЙ ОТПРАВКИ
    // ✅ ОБНОВЛЕННЫЙ МЕТОД С БЕЗОПАСНОЙ МАССОВОЙ ОТПРАВКОЙ
    private fun sendSignalToAppwrite(
        type: String,
        receiverId: String,
        lat: Double,
        lon: Double,
        zoom: Double,
        onNavigateBack: Runnable
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null || !isAdded) return@launch

            try {
                val finalSenderId = senderId.ifEmpty {
                    UserSessionManager.getCurrentUserId(requireContext())
                        ?: throw Exception("Пользователь не авторизован")
                }

                // ✅ ЕСЛИ ВЫБРАНЫ "ВСЕ ДРУЗЬЯ" — ДЕЛАЕМ ПОСЛЕДОВАТЕЛЬНУЮ ОТПРАВКУ
                if (receiverId == "all") {
                    val userDoc = AppwriteManager.databases.getDocument(
                        databaseId = DB_ID,
                        collectionId = USERS_COL_ID,
                        documentId = finalSenderId
                    )
                    val friendIds = (userDoc.data["friends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                    if (friendIds.isEmpty()) {
                        Toast.makeText(requireContext(), "У вас пока нет друзей", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    var successCount = 0
                    // Отправляем каждому другу по отдельности (гарантированная совместимость)
                    for (friendId in friendIds) {
                        try {
                            AppwriteManager.databases.createDocument(
                                databaseId = DB_ID,
                                collectionId = NOTIFICATIONS_COL_ID,
                                documentId = ID.unique(),
                                data = mapOf(
                                    "senderId" to finalSenderId,
                                    "senderName" to senderName,
                                    "receiverId" to friendId,
                                    "type" to type,
                                    "lat" to lat,
                                    "lon" to lon,
                                    "zoom" to zoom,
                                    "isRead" to false,
                                    "createdAt" to java.time.Instant.now().toString()
                                )
                            )
                            successCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Не удалось отправить другу $friendId: ${e.message}")
                        }
                    }

                    Log.d(TAG, "📤 Массовая отправка: $type от $senderName -> $successCount/${friendIds.size} друзьям")
                    Toast.makeText(requireContext(), "Сигнал отправлен $successCount друзьям! 🎣", Toast.LENGTH_SHORT).show()

                } else {
                    // ✅ ОДИНОЧНАЯ ОТПРАВКА (СТАРЫЙ ПУТЬ)
                    AppwriteManager.databases.createDocument(
                        databaseId = DB_ID,
                        collectionId = NOTIFICATIONS_COL_ID,
                        documentId = ID.unique(),
                        data = mapOf(
                            "senderId" to finalSenderId,
                            "senderName" to senderName,
                            "receiverId" to receiverId,
                            "type" to type,
                            "lat" to lat,
                            "lon" to lon,
                            "zoom" to zoom,
                            "isRead" to false,
                            "createdAt" to java.time.Instant.now().toString()
                        )
                    )

                    Log.d(TAG, "📤 Отправка сигнала: $type от $senderName -> $receiverId")
                    Toast.makeText(requireContext(), "Сигнал отправлен! 🎣", Toast.LENGTH_SHORT).show()
                }

                onNavigateBack.run()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Критическая ошибка отправки: ${e.message}", e)
                Toast.makeText(requireContext(), "Не удалось отправить: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun startLocationTracking() {
        if (_binding == null) return
        locationOverlay?.enableMyLocation()
        binding.mapView.controller.setCenter(DEFAULT_POINT)
        binding.mapView.controller.setZoom(CITY_ZOOM)

        val timeoutRunnable = Runnable {
            if (_binding != null) Log.d(TAG, "⏱️ GPS таймаут")
        }
        binding.mapView.postDelayed(timeoutRunnable, 6000)

        locationOverlay?.runOnFirstFix {
            val location = locationOverlay?.myLocation
            if (location != null) {
                val newLocation = GeoPoint(location.latitude, location.longitude)
                requireActivity().runOnUiThread {
                    if (_binding != null) {
                        binding.mapView.removeCallbacks(timeoutRunnable)
                        binding.mapView.controller.animateTo(newLocation)
                        binding.mapView.controller.setZoom(CITY_ZOOM)
                        Log.d(TAG, "✅ GPS получен: $newLocation")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        _binding?.mapView?.onPause()
        locationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        _binding?.mapView?.onDetach()
        locationOverlay?.disableMyLocation()
        locationOverlay = null
        _binding = null
        super.onDestroyView()
    }
}