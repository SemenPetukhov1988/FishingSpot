package ru.semen.fishingspot.fragment

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

class AddPointFragment : Fragment() {

    private var _binding: FragmentAddPointBinding? = null
    private val binding get() = _binding!!

    // Переменная для хранения выбранной рыбы
    private var selectedFish: String = "Не указано"
    // Переменная для пути к фото (пока null, позже подключим ImagePicker)
    private var currentPhotoPath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPointBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 1. КНОПКА НАЗАД
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ✅ 2. ЛОГИКА ВЫБОРА РЫБЫ (ЧИПЫ)
        binding.chipGroupFish.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedChip = group.findViewById<Chip>(checkedIds[0])
                selectedFish = checkedChip.text.toString()

                // Если выбрали "Другое", показываем поле для ввода
                if (selectedFish == "Другое") {
                    binding.layoutOtherFish.visibility = View.VISIBLE
                    binding.etOtherFish.requestFocus()
                } else {
                    binding.layoutOtherFish.visibility = View.GONE
                    binding.etOtherFish.text?.clear() // Очищаем, если передумали
                }
            }
        }

        // ✅ 3. ЗАГЛУШКА ДЛЯ ФОТО (ПОДГОТОВКА ПОД IMAGE PICKER)
        binding.cardPhoto.setOnClickListener {
            // TODO: Здесь будет вызов ImagePicker (только камера)
            Toast.makeText(context, "📸 Скоро здесь откроется камера", Toast.LENGTH_SHORT).show()
        }

        binding.btnRemovePhoto.setOnClickListener {
            currentPhotoPath = null
            binding.ivPhotoPreview.visibility = View.GONE
            binding.btnRemovePhoto.visibility = View.GONE
            binding.tvPhotoHint.visibility = View.VISIBLE
            // TODO: Вернуть иконку камеры на место
        }

        // ✅ 4. КНОПКА СОХРАНИТЬ И ПЕРЕХОД К УТОЧНЕНИЮ
        binding.btnSave.setOnClickListener {
            val name = binding.etSpotName.text.toString().trim()

            // Валидация названия
            if (name.isEmpty()) {
                binding.etSpotName.error = "Введите название места"
                binding.etSpotName.requestFocus()
                return@setOnClickListener
            }

            // Определяем итоговое название рыбы
            val finalFishName = if (binding.chipOther.isChecked) {
                binding.etOtherFish.text.toString().trim().ifEmpty { "Другая рыба" }
            } else {
                selectedFish
            }

            // Определяем вес (ВРЕМЕННЫЙ МАППИНГ ПОД СТАРУЮ МОДЕЛЬ)
            // Позже, когда обновим модель, будем передавать строку или Enum
            val weightValue = when (binding.toggleGroupWeight.checkedButtonId) {
                R.id.btnWeight1 -> 0.5  // До 1 кг
                R.id.btnWeight2 -> 3.0  // 1-5 кг (среднее)
                R.id.btnWeight3 -> 6.0  // Больше 5 кг
                else -> 0.0
            }

            // Собираем описание. Добавим туда рыбу, пока не обновим модель данных
            val baseDescription = binding.etDescription.text.toString().trim()
            val finalDescription = if (baseDescription.isNotEmpty()) {
                "🐟 В улове: $finalFishName.\n\n$baseDescription"
            } else {
                "🐟 В улове: $finalFishName."
            }

            // Берем координаты, которые передал MyMapFragment или GlobalMapFragment
            val startLat = arguments?.getDouble("LAT") ?: 0.0
            val startLon = arguments?.getDouble("LON") ?: 0.0

            if (startLat == 0.0 && startLon == 0.0) {
                Toast.makeText(context, "⚠️ Ошибка: не удалось получить координаты", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Формируем Bundle для RefineLocationFragment
            val bundle = Bundle().apply {
                putString("NAME", name)
                putString("DESC", finalDescription) // Передаем рыбу внутри описания (временно)
                putDouble("WEIGHT", weightValue)
                putBoolean("IS_PUBLIC", binding.switchPublic.isChecked)
                putString("PHOTO_PATH", currentPhotoPath)
                putDouble("LAT", startLat)
                putDouble("LON", startLon)
            }

            // Переход на экран уточнения точки на карте
            findNavController().navigate(R.id.action_addPoint_to_refineLocation, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}