package ru.semen.fishingspot.messages.notifikation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ru.semen.fishingspot.databinding.BottomSheetRecommendDateBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RecommendSpotDateBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecommendDateBinding? = null
    private val binding get() = _binding!!

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    private val signalsRepo = FishingSignalRepository()

    companion object {
        const val RESULT_KEY = "spot_request_created"

        fun newInstance(
            signalType: String,
            senderName: String,
            senderId: String,
            receiverId: String
        ): RecommendSpotDateBottomSheet {
            return RecommendSpotDateBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("signal_type", signalType)
                    putString("sender_name", senderName)
                    putString("sender_id", senderId)
                    putString("receiver_id", receiverId)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetRecommendDateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendar.timeInMillis = System.currentTimeMillis()
        binding.calendarView.date = System.currentTimeMillis()
        updateDateDisplay()

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            updateDateDisplay()
        }

        binding.btnSendRequest.setOnClickListener {
            val comment = binding.etSpotRequestComment.text.toString().trim()

            if (comment.isEmpty()) {
                binding.etSpotRequestComment.error = "Напиши, что именно ищешь!"
                binding.etSpotRequestComment.requestFocus()
                return@setOnClickListener
            }

            val signalType = arguments?.getString("signal_type") ?: return@setOnClickListener
            val senderName = arguments?.getString("sender_name") ?: return@setOnClickListener
            val senderId = arguments?.getString("sender_id") ?: return@setOnClickListener
            val receiverId = arguments?.getString("receiver_id") ?: return@setOnClickListener

            val dateMillis = calendar.timeInMillis

            lifecycleScope.launch {
                signalsRepo.sendSignal(
                    senderId = senderId,
                    senderName = senderName,
                    receiverId = receiverId,
                    type = signalType,
                    plannedDate = dateMillis,
                    messageText = comment,      // ✅ ОБЯЗАТЕЛЬНО: текст для карточки получателя
                    requestComment = comment     // ✅ Для якоря отправителя
                ).onSuccess { (count, _) ->
                    Toast.makeText(requireContext(), "Запрос отправлен $count друзьям! 🎣", Toast.LENGTH_SHORT).show()

                    // ✅ ПРОСТОЕ УВЕДОМЛЕНИЕ БЕЗ ЛИШНИХ ФЛАГОВ
                    parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle.EMPTY)

                    dismiss()

                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateDateDisplay() {
        binding.tvSelectedDateDisplay.text = dateFormat.format(calendar.time)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}