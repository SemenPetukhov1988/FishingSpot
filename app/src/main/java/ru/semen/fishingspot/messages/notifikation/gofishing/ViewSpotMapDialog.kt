package ru.semen.fishingspot.messages.notifikation.gofishing

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogViewSpotMapBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewSpotMapDialog : BottomSheetDialogFragment() {

    private var _binding: DialogViewSpotMapBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var spotLat: Double = 0.0
    private var spotLon: Double = 0.0
    private var plannedDateMillis: Long = 0L

    companion object {
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_DATE = "date"

        fun newInstance(lat: Double, lon: Double, dateMillis: Long? = null): ViewSpotMapDialog {
            return ViewSpotMapDialog().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LON, lon)
                    dateMillis?.let { putLong(ARG_DATE, it) }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogViewSpotMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spotLat = arguments?.getDouble(ARG_LAT) ?: 0.0
        spotLon = arguments?.getDouble(ARG_LON) ?: 0.0
        plannedDateMillis = arguments?.getLong(ARG_DATE) ?: 0L

        // Отображаем дату в заголовке
        if (plannedDateMillis > 0) {
            val formatted = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(plannedDateMillis))
            binding.tvSpotDate.text = "📅 Планируемая дата: $formatted"
        } else {
            binding.tvSpotDate.text = "📍 Выбранное место"
        }

        initMap()

        // ✅ ТОЛЬКО КНОПКА ЗАКРЫТИЯ
        binding.btnCloseMap.setOnClickListener { dismiss() }
    }

    private fun initMap() {
        if (mapView == null) {
            mapView = MapView(requireContext()).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBackgroundColor(Color.parseColor("#EAEFF5"))
            }
            binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val point = GeoPoint(spotLat, spotLon)
        mapView?.controller?.setCenter(point)
        mapView?.controller?.setZoom(16.0)

        // Очищаем старые маркеры и ставим новый
        mapView?.overlays?.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = point
            title = "Место рыбалки"
            setIcon(resources.getDrawable(R.drawable.metka4567, null))
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

                // ✅ ВЫСОТА 2/3 ЭКРАНА
                val displayMetrics = resources.displayMetrics
                val twoThirdsHeight = (displayMetrics.heightPixels * 2 / 3).toInt()
                sheet.layoutParams.height = twoThirdsHeight
                sheet.requestLayout()

                // ✅ БЛОКИРУЕМ СВАЙП И РАСКРЫТИЕ
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