package ru.semen.fishingspot.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogSpotMapPreviewBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class SpotMapPreviewDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpotMapPreviewBinding? = null
    private val binding get() = _binding!!

    // ✅ ПОЛУЧАЕМ ОБЩИЙ VIEWMODEL ДЛЯ СОХРАНЕНИЯ
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var mapView: MapView? = null
    private var currentSpotName: String = ""

    companion object {
        private const val ARG_LATITUDE = "latitude"
        private const val ARG_LONGITUDE = "longitude"
        private const val ARG_SPOT_NAME = "spot_name"

        fun newInstance(lat: Double, lon: Double, spotName: String): SpotMapPreviewDialog {
            return SpotMapPreviewDialog().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_LATITUDE, lat)
                    putDouble(ARG_LONGITUDE, lon)
                    putString(ARG_SPOT_NAME, spotName)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSpotMapPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val latitude = arguments?.getDouble(ARG_LATITUDE) ?: 0.0
        val longitude = arguments?.getDouble(ARG_LONGITUDE) ?: 0.0
        currentSpotName = arguments?.getString(ARG_SPOT_NAME) ?: "Точка"

        binding.tvMapDialogTitle.text = "📍 $currentSpotName"

        // Инициализация карты
        mapView = MapView(requireContext()).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBackgroundColor(Color.parseColor("#EAEFF5"))
            controller.setCenter(GeoPoint(latitude, longitude))
            controller.setZoom(15.0)
        }

        binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // Маркер точки
        val marker = Marker(mapView).apply {
            position = GeoPoint(latitude, longitude)
            setIcon(resources.getDrawable(R.drawable.metka4567, null))
            title = currentSpotName
        }
        mapView?.overlays?.add(marker)

        // Кнопка "Закрыть"
        binding.btnCancelMapPreview.setOnClickListener { dismiss() }

        // ✅ РЕАЛЬНАЯ ЛОГИКА СОХРАНЕНИЯ
        binding.btnConfirmSaveFromMap.setOnClickListener {
            // Создаем временный объект FishingSpot для передачи в ViewModel
            // В будущем можно передавать весь объект через Parcelable/Serializable
            val tempSpot = ru.semen.fishingspot.data.FishingSpot(
                documentId = "", // ID будет сгенерирован или найден при сохранении
                name = currentSpotName,
                latitude = latitude,
                longitude = longitude,
                authorId = "",
                authorName = "",
                visibility = "public",
                createdAt = System.currentTimeMillis()
            )

            spotViewModel.saveToFavorites(tempSpot)

            Toast.makeText(requireContext(), "✅ Точка '$currentSpotName' сохранена!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
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