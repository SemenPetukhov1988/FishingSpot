package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.databinding.BottomSheetRequestSpotBinding
import ru.semen.fishingspot.viewmodel.FriendsViewModel

class RequestSpotBottomSheet(
    private val friendId: String,
    private val friendName: String
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRequestSpotBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRequestSpotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvFriendName.text = "Для: $friendName"

        binding.btnSendRequest.setOnClickListener {
            val messageText = binding.etRequestMessage.text.toString().trim()

            if (messageText.isEmpty()) {
                Toast.makeText(requireContext(), "Напиши хоть пару слов 😊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Вызываем метод ViewModel для отправки
            viewModel.sendSpotRequest(friendId, friendName, messageText)

            Toast.makeText(requireContext(), "Запрос отправлен!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}