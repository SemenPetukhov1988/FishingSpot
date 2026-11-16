package ru.semen.fishingspot.messages.notifikation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.BottomSheetDateSelectionBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDateSelectionBinding? = null
    private val binding get() = _binding!!

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    companion object {
        const val RESULT_KEY = "fishing_date_selected"
        const val ARG_DATE_MILLIS = "date_millis"

        fun newInstance(selectedDateMillis: Long = System.currentTimeMillis()): DateSelectionBottomSheet {
            return DateSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DATE_MILLIS, selectedDateMillis)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDateSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ ИСПРАВЛЕНИЕ БАГА С 1970 ГОДОМ
        // Если аргументы не переданы или равны 0, принудительно ставим текущее время
        val initialMillis = arguments?.getLong(ARG_DATE_MILLIS)?.takeIf { it > 0 }
            ?: System.currentTimeMillis()

        calendar.timeInMillis = initialMillis
        binding.calendarView.date = initialMillis
        updateDateDisplay()

        // ✅ ОБРАБОТКА ВЫБОРА ДАТЫ НА КАЛЕНДАРЕ (БЕЗ ИЗМЕНЕНИЙ)
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            updateDateDisplay()
        }

        // Подтверждение выбора (НАВИГАЦИЯ НА КАРТУ ОСТАЛАСЬ КАК БЫЛА)
        binding.btnConfirmDate.setOnClickListener {
            val dateMillis = calendar.timeInMillis

            val signalType = arguments?.getString("signal_type") ?: return@setOnClickListener
            val senderName = arguments?.getString("sender_name") ?: return@setOnClickListener
            val senderId = arguments?.getString("sender_id") ?: return@setOnClickListener
            val receiverId = arguments?.getString("receiver_id") ?: return@setOnClickListener

            // ✅ НАВИГАЦИЯ НА СУЩЕСТВУЮЩИЙ ФРАГМЕНТ КАРТЫ
            val bundle = Bundle().apply {
                putString("signal_type", signalType)
                putString("sender_name", senderName)
                putString("sender_id", senderId)
                putString("receiver_id", receiverId)
                putLong("planned_date_millis", dateMillis) // ✅ ПЕРЕДАЕМ ДАТУ
            }

            findNavController().navigate(R.id.sendFishingSignalFragment, bundle)
            dismiss()
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