package ru.semen.fishingspot.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.databinding.BottomSheetSignalTypeBinding
import ru.semen.fishingspot.utils.UserSessionManager

class SignalTypeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSignalTypeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSignalTypeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ ПОЛУЧАЕМ РЕАЛЬНОЕ ИМЯ ПОЛЬЗОВАТЕЛЯ ПРЯМО ЗДЕСЬ
        val senderName = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"
        val senderId = UserSessionManager.getCurrentUserId(requireContext()) ?: ""

        binding.btnOnFishing.setOnClickListener {
            dismiss()
            openRecipientSheet("on_fishing", senderName, senderId)
        }

        binding.btnGoingFishing.setOnClickListener {
            dismiss()
            openRecipientSheet("going_fishing", senderName, senderId)
        }

        binding.btnRequestSpot.setOnClickListener {
            dismiss()
            openRecipientSheet("request_spot", senderName, senderId)
        }
    }

    // ✅ ЗАЩИТА ОТ ДУБЛЯ ШЕЙТА ПРИ БЫСТРЫХ ТАПАХ
    private fun openRecipientSheet(signalType: String, senderName: String, senderId: String) {
        if (parentFragmentManager.findFragmentByTag("recipient_sheet") != null) return

        RecipientSelectionBottomSheet(
            signalType = signalType,
            senderName = senderName,
            senderId = senderId
        ).show(parentFragmentManager, "recipient_sheet")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}