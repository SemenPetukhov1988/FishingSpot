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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentSendFishingSignalBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.FishingSignalsViewModel
import ru.semen.fishingspot.viewmodel.SendSignalState
import ru.semen.fishingspot.viewmodel.SignalsUiState

class SendFishingSignalFragment : Fragment() {

    private var _binding: FragmentSendFishingSignalBinding? = null
    private val binding get() = _binding!!

    private val TAG = "SIGNAL_MAP"
    private val DEFAULT_POINT = GeoPoint(64.5401, 40.5433)
    private val CITY_ZOOM = 11.0
    private val TARGET_ZOOM = 17.0

    // ✅ ИСПОЛЬЗУЕМ VIEWMODEL ВМЕСТО ПРЯМОГО ВЫЗОВА APPWRITE
    private val signalsViewModel: FishingSignalsViewModel by activityViewModels()

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

        senderName = arguments?.getString("sender_name")
            ?: UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"
        senderId = arguments?.getString("sender_id")
            ?: UserSessionManager.getCurrentUserId(requireContext()) ?: ""

        Log.d(TAG, "🚀 Запуск фрагмента. Тип: $signalType, Получатель: $receiverId")

        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView.overlays.add(locationOverlay)

        checkLocationPermission()

        // ✅ НАБЛЮДЕНИЕ ЗА РЕЗУЛЬТАТОМ ОТПРАВКИ
        observeSendResult()

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

            // ✅ ОТПРАВКА ЧЕРЕЗ VIEWMODEL
            signalsViewModel.sendSignal(
                senderId = senderId,
                senderName = senderName,
                receiverId = receiverId,
                type = signalType,
                lat = lat,
                lon = lon,
                zoom = zoom
            )

            // Навигация произойдет автоматически при успехе в observeSendResult()
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

    // ✅ РЕАКТИВНАЯ ОБРАБОТКА РЕЗУЛЬТАТА ОТПРАВКИ
    // ✅ НАБЛЮДЕНИЕ ЗА СТАТУСОМ ОТПРАВКИ (НЕ ЗА ЛЕНТОЙ!)
    private fun observeSendResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                signalsViewModel.sendState.collect { state ->
                    when (state) {
                        is SendSignalState.Success -> {
                            Toast.makeText(requireContext(), "Сигнал отправлен! 🎣", Toast.LENGTH_SHORT).show()

                            // Сбрасываем состояние перед навигацией
                            signalsViewModel.resetSendState()

                            // Навигируем назад
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
                        is SendSignalState.Error -> {
                            Toast.makeText(requireContext(), "Ошибка: ${state.message}", Toast.LENGTH_LONG).show()
                            signalsViewModel.resetSendState()
                        }
                        else -> {}
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