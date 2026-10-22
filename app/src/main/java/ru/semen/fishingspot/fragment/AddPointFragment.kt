package ru.semen.fishingspot.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentAddPointBinding
import ru.semen.fishingspot.utils.UserSessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddPointFragment : Fragment() {

    private var _binding: FragmentAddPointBinding? = null
    private val binding get() = _binding!!

    private var selectedFish: String = "Не указано"
    private var currentPhotoPath: String? = null

    // ✅ Переменные для расширенной информации
    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedLocation: String = "С берега"
    private var selectedTackle: String = "Не указано"
    private var selectedBait: String = "Не указано"
    private var selectedVisibility: String = "private" // По умолчанию личная

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPointBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Установка текущей даты по умолчанию
        updateDateText(selectedDate)

        // ✅ 1. КНОПКА НАЗАД
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ✅ 2. ВЫБОР ДАТЫ РЫБАЛКИ
        binding.layoutFishingDate.setEndIconOnClickListener {
            showDatePicker()
        }

        // ✅ 3. ПЕРЕКЛЮЧАТЕЛЬ ВИДИМОСТИ (Личная / Друзья / Публичная)
        binding.toggleGroupVisibility.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedVisibility = when (checkedId) {
                    R.id.btnVisibilityPrivate -> "private"
                    R.id.btnVisibilityFriends -> "friends"
                    R.id.btnVisibilityPublic -> "public"
                    else -> "private"
                }
            }
        }
        // Установим "Личная" как выбранную по умолчанию
        binding.btnVisibilityPrivate.isChecked = true

        // ✅ 4. МЕСТО ЛОВЛИ
        binding.toggleGroupLocation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedLocation = if (checkedId == R.id.btnLocationShore) "С берега" else "С лодки"
            }
        }
        binding.btnLocationShore.isChecked = true

        // ✅ 5. СНАСТИ
        binding.chipGroupTackle.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedTackle = group.findViewById<Chip>(checkedIds[0]).text.toString()
            }
        }

        // ✅ 6. НАЖИВКА
        binding.chipGroupBait.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedBait = group.findViewById<Chip>(checkedIds[0]).text.toString()
            }
        }

        // ✅ 7. ВИД РЫБЫ
        binding.chipGroupFish.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedChip = group.findViewById<Chip>(checkedIds[0])
                selectedFish = checkedChip.text.toString()

                if (selectedFish == "Другое") {
                    binding.layoutOtherFish.visibility = View.VISIBLE
                    binding.etOtherFish.requestFocus()
                } else {
                    binding.layoutOtherFish.visibility = View.GONE
                    binding.etOtherFish.text?.clear()
                }
            }
        }

        // ✅ 8. ФОТО (Заглушка под ImagePicker)
        binding.cardPhoto.setOnClickListener {
            Toast.makeText(context, "📸 Здесь будет выбор: Камера или Галерея", Toast.LENGTH_SHORT).show()
        }

        binding.btnRemovePhoto.setOnClickListener {
            currentPhotoPath = null
            binding.ivPhotoPreview.visibility = View.GONE
            binding.btnRemovePhoto.visibility = View.GONE
            binding.tvPhotoHint.visibility = View.VISIBLE
            binding.ivPhotoIcon.visibility = View.VISIBLE
        }

        // ✅ 9. КНОПКА СОХРАНИТЬ И ПЕРЕХОД К УТОЧНЕНИЮ
        binding.btnSave.setOnClickListener {
            val name = binding.etSpotName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etSpotName.error = "Введите название места"
                binding.etSpotName.requestFocus()
                return@setOnClickListener
            }

            val finalFishName = if (binding.chipOtherFish.isChecked) {
                binding.etOtherFish.text.toString().trim().ifEmpty { "Другая рыба" }
            } else {
                selectedFish
            }

            val weightValue = when (binding.toggleGroupWeight.checkedButtonId) {
                R.id.btnWeight1 -> 0.5
                R.id.btnWeight2 -> 3.0
                R.id.btnWeight3 -> 6.0
                else -> 0.0
            }

            // Формируем красивое описание из новых полей
            val baseDescription = binding.etDescription.text.toString().trim()
            val finalDescription = buildString {
                append("📅 Дата: ${formatDate(selectedDate)}\n")
                append("📍 Место: $selectedLocation\n")
                append("🎣 Снасть: $selectedTackle | 🪱 Наживка: $selectedBait\n")
                append("🐟 В улове: $finalFishName\n\n")
                if (baseDescription.isNotEmpty()) {
                    append(baseDescription)
                }
            }

            val startLat = arguments?.getDouble("LAT") ?: 0.0
            val startLon = arguments?.getDouble("LON") ?: 0.0

            if (startLat == 0.0 && startLon == 0.0) {
                Toast.makeText(context, "⚠️ Ошибка координат", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ ПОЛУЧАЕМ ДАННЫЕ АВТОРА
            val currentAuthorName = UserSessionManager.getCurrentNickname(requireContext())
            val currentAuthorId = UserSessionManager.getCurrentUserId(requireContext())

            // ✅ ЗАЩИТА: Если ID нет, значит сессия слетела. Не даем создать точку.
            if (currentAuthorId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "⚠️ Ошибка сессии. Пожалуйста, перезайдите в приложение.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Лог для отладки (оставляем, он полезен)
            android.util.Log.d("DEBUG_ADD_POINT", "👤 ПЕРЕД СОХРАНЕНИЕМ -> ID: '$currentAuthorId', Ник: '$currentAuthorName'")

            val bundle = Bundle().apply {
                putString("NAME", name)
                putString("DESC", finalDescription)
                putDouble("WEIGHT", weightValue)
                putString("VISIBILITY", selectedVisibility)
                putLong("CREATED_AT", selectedDate)
                putString("FISH_TYPE", finalFishName)
                putString("PHOTO_PATH", currentPhotoPath)
                putDouble("LAT", startLat)
                putDouble("LON", startLon)

                // ✅ ИСПРАВЛЕНО: Убрали "Рыбак". Теперь либо реальный ник, либо "Неизвестно"
                putString("AUTHOR_NAME", currentAuthorName ?: "Неизвестно")
                putString("AUTHOR_ID", currentAuthorId)
            }

            findNavController().navigate(R.id.refineLocationFragment, bundle)
        }
    }

    // --- Вспомогательные методы ---

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDate

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(year, month, dayOfMonth)
                selectedDate = newCalendar.timeInMillis
                updateDateText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateDateText(timestamp: Long) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
        binding.etFishingDate.setText(sdf.format(timestamp))
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
        return sdf.format(timestamp)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}