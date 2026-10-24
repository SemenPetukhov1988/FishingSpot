package ru.semen.fishingspot.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.DialogSpotMapPreviewBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class SpotMapPreviewDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpotMapPreviewBinding? = null
    private val binding get() = _binding!!

    // ✅ ИСПОЛЬЗУЕМ ТОТ ЖЕ VIEWMODEL, ЧТО И ВЕСЬ ОСТАЛЬНОЙ APP
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var mapView: MapView? = null
    private var currentSpot: FishingSpot? = null

    companion object {
        private const val ARG_DOCUMENT_ID = "document_id"

        /**
         * ✅ БЕЗОПАСНО: передаём ТОЛЬКО ID
         */
        fun newInstance(documentId: String): SpotMapPreviewDialog {
            return SpotMapPreviewDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOCUMENT_ID, documentId)
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

        val documentId = arguments?.getString(ARG_DOCUMENT_ID) ?: run {
            dismiss()
            return
        }

        // ✅ ЗАПУСКАЕМ СУЩЕСТВУЮЩИЙ МЕТОД ЗАГРУЗКИ ИЗ VIEWMODEL
        spotViewModel.loadSpotDetails(documentId)

        // ✅ НАБЛЮДАЕМ ЗА РЕЗУЛЬТАТОМ (как в SpotDetailsFragment)
        spotViewModel.currentSpotDetails.observe(viewLifecycleOwner, Observer { spot ->
            if (spot != null && spot.documentId == documentId) {
                currentSpot = spot
                initMapAndUI(spot)
            }
        })

        // Обработка ошибок загрузки
        spotViewModel.errorMessage.observe(viewLifecycleOwner, Observer { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                // Не закрываем диалог сразу, даём пользователю прочитать ошибку
            }
        })
    }

    private fun initMapAndUI(spot: FishingSpot) {
        // Если карта уже создана - обновляем данные, иначе создаём
        if (mapView == null) {
            mapView = MapView(requireContext()).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBackgroundColor(Color.parseColor("#EAEFF5"))
            }
            binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // Кнопки создаём один раз
            binding.btnCancelMapPreview.setOnClickListener { dismiss() }

            binding.btnConfirmSaveFromMap.setOnClickListener {
                currentSpot?.let {
                    spotViewModel.saveToFavorites(it) // ✅ ИСПОЛЬЗУЕМ ГОТОВЫЙ МЕТОД
                    Toast.makeText(requireContext(), "✅ '${it.name}' сохранена!", Toast.LENGTH_SHORT).show()
                }
                dismiss()
            }
        }

        // Обновляем состояние карты
        binding.tvMapDialogTitle.text = "📍 ${spot.name}"
        mapView?.controller?.setCenter(GeoPoint(spot.latitude, spot.longitude))
        mapView?.controller?.setZoom(15.0)

        // Очищаем старые маркеры и ставим новый
        mapView?.overlays?.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = GeoPoint(spot.latitude, spot.longitude)
            setIcon(resources.getDrawable(R.drawable.metka4567, null))
            title = spot.name
        }
        mapView?.overlays?.add(marker)
        mapView?.invalidate()
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