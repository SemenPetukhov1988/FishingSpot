package ru.semen.fishingspot.messages

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogNotificationMapPreviewBinding

class NotificationMapPreviewDialog : BottomSheetDialogFragment() {

    private var _binding: DialogNotificationMapPreviewBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var signalLat: Double = 0.0
    private var signalLon: Double = 0.0
    private var senderName: String = "Рыбак"

    companion object {
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_SENDER_NAME = "sender_name"

        fun newInstance(lat: Double, lon: Double, name: String): NotificationMapPreviewDialog {
            return NotificationMapPreviewDialog().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LON, lon)
                    putString(ARG_SENDER_NAME, name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // ✅ ИСПОЛЬЗУЕМ НОВУЮ РАЗМЕТКУ
        _binding = DialogNotificationMapPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        signalLat = arguments?.getDouble(ARG_LAT) ?: 0.0
        signalLon = arguments?.getDouble(ARG_LON) ?: 0.0
        senderName = arguments?.getString(ARG_SENDER_NAME) ?: "Рыбак"

        initMapAndUI()
    }

    private fun initMapAndUI() {
        if (mapView == null) {
            mapView = MapView(requireContext()).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBackgroundColor(Color.parseColor("#EAEFF5"))
            }
            binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // Кнопка закрытия
            binding.btnCancelMapPreview.setOnClickListener { dismiss() }

            // ✅ ПОКАЗЫВАЕМ КНОПКУ НАВИГАЦИИ И ВЕШАЕМ ИНТЕНТ
            binding.btnNavigateToSpot.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val uri = Uri.parse("geo:${signalLat},${signalLon}?q=${signalLat},${signalLon}(${senderName})")
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Обновляем UI
        binding.tvMapDialogTitle.text = "📍 $senderName сейчас здесь"

        mapView?.controller?.setCenter(GeoPoint(signalLat, signalLon))
        mapView?.controller?.setZoom(16.0)

        mapView?.overlays?.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = GeoPoint(signalLat, signalLon)
            setIcon(resources.getDrawable(R.drawable.metka4567, null))
            title = senderName
        }
        mapView?.overlays?.add(marker)
        mapView?.invalidate()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)

                // Высота 2/3 экрана
                val displayMetrics = resources.displayMetrics
                val twoThirdsHeight = (displayMetrics.heightPixels * 2 / 3).toInt()
                sheet.layoutParams.height = twoThirdsHeight
                sheet.requestLayout()

                // Блокируем свайп
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
            }
        }
        return dialog
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView = null
        _binding = null
        super.onDestroyView()
    }
}