package ru.semen.fishingspot.messages.notifikation

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogRecommendSpotMapBinding
import ru.semen.fishingspot.map.point.SpotViewModel
import ru.semen.fishingspot.map.point.data.FishingSpot
import ru.semen.fishingspot.utils.UserSessionManager

class RecommendSpotMapDialog : BottomSheetDialogFragment() {

    private var _binding: DialogRecommendSpotMapBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private val spotViewModel: SpotViewModel by activityViewModels()
    private val signalsRepo = FishingSignalRepository()
    private var locationOverlay: MyLocationNewOverlay? = null

    private var isLocationAlreadyShown = false
    private var lastUserLocation: GeoPoint? = null

    // ✅ СОСТОЯНИЕ ВЫБОРА ТОЧКИ
    private var selectedSpotId: String? = null
    private var isNewSpot: Boolean = false
    private var newSpotCoordinates: GeoPoint? = null

    // ✅ НОВАЯ ПЕРЕМЕННАЯ ДЛЯ КООРДИНАТ СУЩЕСТВУЮЩЕЙ ТОЧКИ
    private var existingSpotCoordinates: GeoPoint? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startLocationTracking()
    }

    companion object {
        private const val ARG_ANCHOR_ID = "anchor_id"
        private const val ARG_SENDER_ID = "sender_id"
        private const val DEFAULT_ZOOM = 16.0

        fun newInstance(anchorId: String, senderId: String): RecommendSpotMapDialog {
            return RecommendSpotMapDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ANCHOR_ID, anchorId)
                    putString(ARG_SENDER_ID, senderId)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogRecommendSpotMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initMap()

        spotViewModel.currentSpots.observe(viewLifecycleOwner) { spots ->
            drawMarkers(spots)

            if (!isLocationAlreadyShown && spots.isNotEmpty()) {
                val firstSpot = spots.first()
                mapView?.controller?.setCenter(GeoPoint(firstSpot.latitude, firstSpot.longitude))
                mapView?.controller?.setZoom(DEFAULT_ZOOM)
                isLocationAlreadyShown = true
            } else if (!isLocationAlreadyShown && spots.isEmpty()) {
                checkLocationPermission()
            }
        }

        binding.btnCloseDialog.setOnClickListener { dismiss() }

        // ✅ ИСПРАВЛЕННАЯ ЛОГИКА ОТПРАВКИ
        binding.btnSendSpot.setOnClickListener {
            if (selectedSpotId == null && !isNewSpot) {
                showQuickFeedback("Выберите точку на карте!")
                return@setOnClickListener
            }

            val anchorId = arguments?.getString(ARG_ANCHOR_ID) ?: return@setOnClickListener
            val senderId = arguments?.getString(ARG_SENDER_ID) ?: return@setOnClickListener
            val myId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@setOnClickListener
            val myName = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"

            // ✅ БЕРЕМ ТЕКСТ ИЗ ПОЛЯ ВВОДА
            val comment = binding.etSpotComment.text.toString().trim()

            // ✅ ПРАВИЛЬНОЕ ОПРЕДЕЛЕНИЕ КООРДИНАТ
            val recLat: Double
            val recLon: Double

            if (isNewSpot) {
                recLat = newSpotCoordinates?.latitude ?: 0.0
                recLon = newSpotCoordinates?.longitude ?: 0.0
            } else {
                recLat = existingSpotCoordinates?.latitude ?: 0.0
                recLon = existingSpotCoordinates?.longitude ?: 0.0
            }

            val recSpotId = if (!isNewSpot) selectedSpotId.orEmpty() else ""

            Log.d("SEND_REC_DEBUG", "📤 Отправка: anchor=$anchorId, type=spot_recommendation")
            Log.d("SEND_REC_DEBUG", "   Координаты: lat=$recLat, lon=$recLon, spotId=$recSpotId")
            Log.d("SEND_REC_DEBUG", "   Комментарий: '$comment'")

            lifecycleScope.launch {
                try {
                    val result = signalsRepo.sendReply(
                        anchorId = anchorId,
                        senderId = myId,
                        senderName = myName,
                        receiverId = senderId,
                        replyText = comment,
                        lat = recLat,
                        lon = recLon,
                        spotId = recSpotId,
                        type = "spot_recommendation"
                    )

                    if (result.isSuccess) {
                        Log.d("SEND_REC_DEBUG", "✅ Успешно отправлено!")
                        showQuickFeedback("Рекомендация отправлена! 🎣")
                        binding.root.postDelayed({ dismiss() }, 600)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                        Log.e("SEND_REC_DEBUG", "❌ Ошибка: $error")
                        showQuickFeedback("Ошибка: $error")
                    }
                } catch (e: Exception) {
                    Log.e("SEND_REC_DEBUG", "💥 Критический сбой: ${e.message}", e)
                    showQuickFeedback("Критическая ошибка: ${e.message}")
                }
            }
        }
    }

    // ✅ БЕЗОПАСНАЯ АНИМАЦИЯ УВЕДОМЛЕНИЯ
    private fun showQuickFeedback(message: String) {
        val currentBinding = _binding ?: return

        currentBinding.tvMapFeedback.text = message
        currentBinding.tvMapFeedback.visibility = View.VISIBLE

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 200
            fillAfter = true
        }
        currentBinding.tvMapFeedback.startAnimation(fadeIn)

        currentBinding.tvMapFeedback.postDelayed({
            val safeBinding = _binding ?: return@postDelayed

            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 300
                fillAfter = true
            }
            fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    if (_binding != null) {
                        safeBinding.tvMapFeedback.visibility = View.GONE
                    }
                }
            })
            safeBinding.tvMapFeedback.startAnimation(fadeOut)
        }, 800)
    }

    private fun initMap() {
        mapView = MapView(requireContext()).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBackgroundColor(Color.parseColor("#EAEFF5"))
        }

        binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        locationOverlay = MyLocationNewOverlay(mapView)
        locationOverlay?.setDrawAccuracyEnabled(true)
        mapView?.overlays?.add(locationOverlay)

        addSimpleMapClickListener()
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
        locationOverlay?.enableMyLocation()
        locationOverlay?.runOnFirstFix {
            val loc = locationOverlay?.myLocation
            if (loc != null) {
                requireActivity().runOnUiThread {
                    if (!isLocationAlreadyShown) {
                        lastUserLocation = GeoPoint(loc.latitude, loc.longitude)
                        mapView?.controller?.animateTo(lastUserLocation)
                        mapView?.controller?.setZoom(DEFAULT_ZOOM)
                        isLocationAlreadyShown = true
                    }
                }
            }
        }
    }

    private fun drawMarkers(spots: List<FishingSpot>) {
        val map = mapView ?: return
        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.metka4567)

        map.overlays.removeAll { it is SpotSelectionOverlay }

        spots.forEach { spot ->
            val overlay = SpotSelectionOverlay(
                spot = spot,
                iconDrawable = icon,
                isSelected = spot.documentId == selectedSpotId,
                onClick = {
                    selectExistingSpot(spot.documentId, GeoPoint(spot.latitude, spot.longitude), spot.authorName)
                }
            )
            map.overlays.add(overlay)
        }
        map.invalidate()
    }

    private fun addSimpleMapClickListener() {
        val map = mapView ?: return
        map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView): Boolean {
                val tapX = e?.x?.toInt() ?: 0
                val tapY = e?.y?.toInt() ?: 0
                val tappedGeoPoint = mapView.projection.fromPixels(tapX, tapY) as GeoPoint

                val isClickOnExistingSpot = spotViewModel.currentSpots.value?.any { spot ->
                    val dist = tappedGeoPoint.distanceToAsDouble(GeoPoint(spot.latitude, spot.longitude))
                    dist < 50.0
                } ?: false

                if (!isClickOnExistingSpot) {
                    showNewSpotConfirmation(tappedGeoPoint)
                }

                return true
            }
        })
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД ВЫБОРА СУЩЕСТВУЮЩЕЙ ТОЧКИ
    // ✅ ИСПРАВЛЕННЫЙ МЕТОД ВЫБОРА СУЩЕСТВУЮЩЕЙ ТОЧКИ (без очистки текста)
    // ✅ ИСПРАВЛЕННЫЙ МЕТОД ВЫБОРА СУЩЕСТВУЮЩЕЙ ТОЧКИ (без очистки текста)
    private fun selectExistingSpot(id: String, point: GeoPoint, authorName: String) {
        selectedSpotId = id
        isNewSpot = false
        newSpotCoordinates = null

        // ✅ СОХРАНЯЕМ КООРДИНАТЫ ВЫБРАННОЙ ТОЧКИ
        existingSpotCoordinates = point

        mapView?.overlays?.removeAll { it is NewSpotOverlay }
        drawMarkers(spotViewModel.currentSpots.value ?: emptyList())

        binding.btnSendSpot.isEnabled = true
        // ❌ УБРАНО: binding.etSpotComment.setText("")
        // Текст остается, пользователь может дописать комментарий к уже выбранной точке

        showQuickFeedback("Выбрана существующая точка")
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД НОВОЙ ТОЧКИ (без очистки текста)
    private fun showNewSpotConfirmation(point: GeoPoint) {
        newSpotCoordinates = point
        isNewSpot = true
        selectedSpotId = null
        existingSpotCoordinates = null // Сбрасываем координаты старой точки

        val map = mapView ?: return
        map.overlays.removeAll { it is NewSpotOverlay }
        map.overlays.add(NewSpotOverlay(point))

        drawMarkers(spotViewModel.currentSpots.value ?: emptyList())

        binding.btnSendSpot.isEnabled = true
        // ❌ УБРАНО: binding.etSpotComment.setText("")
        // Комментарий сохраняется, даже если пользователь случайно ткнул не туда

        showQuickFeedback("Выбрана новая точка")
    }

    inner class SpotSelectionOverlay(
        private val spot: FishingSpot,
        private val iconDrawable: Drawable?,
        private val isSelected: Boolean,
        private val onClick: () -> Unit
    ) : Overlay() {

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
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
                val scale = if (isSelected) 1.5f else 1.0f
                val scaledW = (w * scale).toInt()
                val scaledH = (h * scale).toInt()

                drawable.setBounds(
                    screenPos.x - scaledW / 2,
                    screenPos.y - scaledH,
                    screenPos.x + scaledW / 2,
                    screenPos.y
                )
                drawable.draw(canvas)

                canvas.drawText(
                    spot.authorName.ifEmpty { "Неизвестно" },
                    screenPos.x.toFloat(),
                    screenPos.y + 20f,
                    textPaint
                )
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView): Boolean {
            val tapX = e?.x?.toInt() ?: 0
            val tapY = e?.y?.toInt() ?: 0
            val markerScreen = Point()
            mapView.projection.toPixels(GeoPoint(spot.latitude, spot.longitude), markerScreen)

            if (Math.abs(tapX - markerScreen.x) < 50 && Math.abs(tapY - markerScreen.y) < 80) {
                onClick()
                return true
            }
            return false
        }
    }

    inner class NewSpotOverlay(private val point: GeoPoint) : Overlay() {
        private val paint = android.graphics.Paint().apply {
            color = Color.RED
            style = android.graphics.Paint.Style.FILL
        }
        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val screenPos = Point()
            mapView.projection.toPixels(point, screenPos)
            canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), 15f, paint)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(sheet)
            val initialHeight = (resources.displayMetrics.heightPixels * 2 / 3).toInt()
            sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            sheet.minimumHeight = initialHeight
            sheet.requestLayout()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false

            ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(bottom = maxOf(imeInsets.bottom, systemBarsInsets.bottom))
                insets
            }
        }
        return dialog
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        spotViewModel.refreshMap()
    }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onDestroyView() {
        locationOverlay?.disableMyLocation()
        mapView = null; _binding = null; super.onDestroyView()
    }
}