package ru.semen.fishingspot.fragment

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
            // ✅ ПЕРЕДАЕМ ИМЯ И ID В СЛЕДУЮЩИЙ ШЕЙТ
            RecipientSelectionBottomSheet(
                signalType = "on_fishing",
                senderName = senderName,
                senderId = senderId
            ).show(parentFragmentManager, "recipient_sheet")
        }

        binding.btnGoingFishing.setOnClickListener {
            dismiss()
            RecipientSelectionBottomSheet(
                signalType = "going_fishing",
                senderName = senderName,
                senderId = senderId
            ).show(parentFragmentManager, "recipient_sheet")
        }

        binding.btnRequestSpot.setOnClickListener {
            dismiss()
            RecipientSelectionBottomSheet(
                signalType = "request_spot",
                senderName = senderName,
                senderId = senderId
            ).show(parentFragmentManager, "recipient_sheet")
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}