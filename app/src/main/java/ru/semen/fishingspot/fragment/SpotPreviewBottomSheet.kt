package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.databinding.BottomSheetSpotPreviewBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpotPreviewBottomSheet(
    private val spotViewModel: SpotViewModel,
    private val senderName: String,
    private val senderId: String,
    private val spotName: String,
    private val spotDesc: String,
    private val spotLat: Double,
    private val spotLon: Double,
    private val spotWeight: Double,
    private val spotCreatedAt: Long,
    private val messageId: String,          // ✅ ID сообщения для удаления
    private val onDeclineAndDelete: () -> Unit // ✅ Функция обратного вызова
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSpotPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSpotPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPreviewName.text = spotName
        binding.tvPreviewDesc.text = spotDesc.ifEmpty { "Без описания" }
        binding.tvPreviewCoords.text = "%.4f, %.4f".format(spotLat, spotLon)
        binding.tvPreviewWeight.text = getWeightText(spotWeight)
        binding.tvPreviewAuthor.text = "Добавил $senderName • ${formatDate(spotCreatedAt)}"

        // Кнопка "Сохранить"
        binding.btnPreviewSave.setOnClickListener {
            spotViewModel.addFullSpot(
                lat = spotLat,
                lon = spotLon,
                name = spotName,
                description = "Получено от $senderName: $spotDesc",
                weight = spotWeight,
                visibility = "private", // ✅ ИСПРАВЛЕНО: было isPublic = false
                photoPath = null,
                fishType = null,        // ✅ ДОБАВЛЕНО
                isVerified = false,
                createdAt = spotCreatedAt ?: System.currentTimeMillis(), // ✅ ДОБАВЛЕНО: сохраняем оригинальную дату
                authorId = senderId
            )
            // Обычно здесь идет dismiss() или закрытие шторки
            dismiss()
        }

        // ✅ Кнопка "Отклонить" (вместо "Позже")
        binding.btnPreviewCancel.setOnClickListener {
            Toast.makeText(requireContext(), "Сообщение удалено", Toast.LENGTH_SHORT).show()
            onDeclineAndDelete() // Вызываем удаление из Firebase
            dismiss()            // Закрываем шторку
        }
    }

    private fun getWeightText(weight: Double): String {
        return when {
            weight <= 0.0 -> "Не указан"
            weight <= 1.0 -> "До 1 кг"
            weight <= 5.0 -> "%.1f кг".format(weight)
            else -> "%.1f кг".format(weight)
        }
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        } catch (e: Exception) {
            "недавно"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}