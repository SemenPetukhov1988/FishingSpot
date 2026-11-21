package ru.semen.fishingspot.messages.notifikation

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogSpotRecommendationPreviewBinding

class SpotRecommendationMapDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpotRecommendationPreviewBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var lat: Double = 0.0
    private var lon: Double = 0.0
    private var authorName: String = "Рыбак"

    companion object {
        fun newInstance(lat: Double, lon: Double, name: String): SpotRecommendationMapDialog {
            return SpotRecommendationMapDialog().apply {
                arguments = Bundle().apply {
                    putDouble("lat", lat)
                    putDouble("lon", lon)
                    putString("name", name)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpotRecommendationPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lat = arguments?.getDouble("lat") ?: 0.0
        lon = arguments?.getDouble("lon") ?: 0.0
        authorName = arguments?.getString("name") ?: "Рыбак"

        // Обновляем заголовок именем автора
        binding.tvMapDialogTitle.text = "📍 Точка от $authorName"

        initMap()

        // ✅ КНОПКИ ПРИВЯЗАНЫ К НОВОЙ РАЗМЕТКЕ
        binding.btnCancelMapPreview.setOnClickListener { dismiss() }

        binding.btnNavigateToSpot.setOnClickListener {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($authorName)")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
            }
        }
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

        mapView?.controller?.setCenter(GeoPoint(lat, lon))
        mapView?.controller?.setZoom(16.0)

        mapView?.overlays?.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            title = "Точка от $authorName"
            setIcon(resources.getDrawable(R.drawable.metka4567, null))
        }
        mapView?.overlays?.add(marker)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener

            val behavior = BottomSheetBehavior.from(sheet)
            val height = (resources.displayMetrics.heightPixels * 2 / 3).toInt()

            sheet.layoutParams.height = height
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