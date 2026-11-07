package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.databinding.BottomSheetSignalTypeBinding

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

        binding.btnOnFishing.setOnClickListener {
            dismiss()
            // TODO: Открываем выбор получателя для "Я на рыбалке"
            RecipientSelectionBottomSheet("on_fishing").show(parentFragmentManager, "recipient_sheet")
        }

        binding.btnGoingFishing.setOnClickListener {
            dismiss()
            // TODO: Выбор получателя для "Собираюсь на рыбалку"
            RecipientSelectionBottomSheet("going_fishing").show(parentFragmentManager, "recipient_sheet")
        }

        binding.btnRequestSpot.setOnClickListener {
            dismiss()
            // TODO: Выбор получателя для запроса точки
            RecipientSelectionBottomSheet("request_spot").show(parentFragmentManager, "recipient_sheet")
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}