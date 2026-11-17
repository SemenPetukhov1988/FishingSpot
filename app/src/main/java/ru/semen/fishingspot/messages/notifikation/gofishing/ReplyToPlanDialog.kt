package ru.semen.fishingspot.messages.notifikation

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.databinding.DialogReplyToPlanBinding
import ru.semen.fishingspot.utils.UserSessionManager

class ReplyToPlanDialog : BottomSheetDialogFragment() {

    private var _binding: DialogReplyToPlanBinding? = null
    private val binding get() = _binding!!
    private var mapView: MapView? = null

    private var anchorId: String = ""
    private var senderId: String = ""
    private var spotLat: Double = 0.0
    private var spotLon: Double = 0.0

    private val signalsRepo = FishingSignalRepository()

    // ✅ Слушатель для ресайза под клавиатуру
    private var imeLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    companion object {
        private const val ARG_ANCHOR_ID = "anchor_id"
        private const val ARG_SENDER_ID = "sender_id"
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"

        fun newInstance(anchorId: String, senderId: String, lat: Double, lon: Double): ReplyToPlanDialog {
            return ReplyToPlanDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ANCHOR_ID, anchorId)
                    putString(ARG_SENDER_ID, senderId)
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LON, lon)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogReplyToPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        anchorId = arguments?.getString(ARG_ANCHOR_ID) ?: return
        senderId = arguments?.getString(ARG_SENDER_ID) ?: return
        spotLat = arguments?.getDouble(ARG_LAT) ?: 0.0
        spotLon = arguments?.getDouble(ARG_LON) ?: 0.0

        initMap()

        // ✅ КРЕСТИК ЗАКРЫВАЕТ ШТОРКУ
        binding.btnCloseReply.setOnClickListener { dismiss() }

        // ✅ ОТПРАВКА ОТВЕТА ЧЕРЕЗ РЕПОЗИТОРИЙ
        binding.btnSendReply.setOnClickListener {
            val replyText = binding.etReplyText.text.toString().trim()
            if (replyText.isEmpty()) {
                binding.etReplyText.error = "Напишите ответ!"
                return@setOnClickListener
            }

            val myId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@setOnClickListener
            val myName = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"

            lifecycleScope.launch {
                signalsRepo.sendReply(
                    anchorId = anchorId,
                    senderId = myId,
                    senderName = myName,
                    receiverId = senderId,
                    replyText = replyText
                ).onSuccess {
                    Toast.makeText(requireContext(), "Ответ отправлен! 🎣", Toast.LENGTH_SHORT).show()
                    dismiss()
                }.onFailure {
                    Toast.makeText(requireContext(), "Ошибка: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun initMap() {
        mapView = MapView(requireContext()).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
        binding.mapContainer.addView(mapView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val point = GeoPoint(spotLat, spotLon)
        mapView?.controller?.setCenter(point)
        mapView?.controller?.setZoom(16.0)

        val marker = Marker(mapView).apply {
            position = point
            title = "Место вопроса"
        }
        mapView?.overlays?.add(marker)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 1. Включаем современный режим обработки системных отступов (Edge-to-Edge для окна диалога)
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener

            val behavior = BottomSheetBehavior.from(sheet)

            // Задаем начальную высоту в 2/3 экрана, но позволяем шторке расти вверх при появлении клавиатуры
            val initialHeight = (resources.displayMetrics.heightPixels * 2 / 3).toInt()
            sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            sheet.minimumHeight = initialHeight
            sheet.requestLayout()

            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false

            // 2. Современный и точный слушатель клавиатуры (работает на Android 5 - Android 15+)
            ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                // Получаем точную высоту клавиатуры (IME) и системной навигационной панели
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                // Выбираем максимальный отступ снизу (клавиатура или кнопки навигации)
                val bottomPadding = maxOf(imeInsets.bottom, systemBarsInsets.bottom)

                // Применяем отступ к самой шторке — она плавно поднимется над клавиатурой
                view.updatePadding(bottom = bottomPadding)

                insets
            }
        }
        return dialog
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }

    override fun onDestroyView() {
        // ✅ Снимаем слушатель с декора диалога — иначе утечка
        imeLayoutListener?.let { listener ->
            dialog?.window?.decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
        imeLayoutListener = null

        mapView = null
        _binding = null
        super.onDestroyView()
    }
}
