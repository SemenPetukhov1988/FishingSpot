package ru.semen.fishingspot.fragment

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentAddPointBinding
import ru.semen.fishingspot.utils.UserSessionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddPointFragment : Fragment() {

    private var _binding: FragmentAddPointBinding? = null
    private val binding get() = _binding!!

    private var selectedFish: String = "Не указано"
    private var selectedPhotoUri: Uri? = null
    private var tempPhotoFile: File? = null

    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedLocation: String = "С берега"
    private var selectedTackle: String = "Не указано"
    private var selectedBait: String = "Не указано"
    private var selectedVisibility: String = "private"

    // ✅ ЛАУНЧЕР ДЛЯ ГАЛЕРЕИ
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            showPhotoPreview(uri)
        }
    }

    // ✅ ЛАУНЧЕР ДЛЯ КАМЕРЫ
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoFile != null) {
            selectedPhotoUri = Uri.fromFile(tempPhotoFile)
            showPhotoPreview(selectedPhotoUri!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPointBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация даты и состояния поля
        updateDateText(selectedDate)
        updateDateFieldState()

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Клик по дате (сработает только если поле активно)
        binding.layoutFishingDate.setEndIconOnClickListener {
            if (selectedVisibility == "public") return@setEndIconOnClickListener
            showDatePicker()
        }
        binding.etFishingDate.setOnClickListener {
            if (selectedVisibility == "public") return@setOnClickListener
            showDatePicker()
        }

        // Переключатели видимости
        binding.toggleGroupVisibility.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedVisibility = when (checkedId) {
                    R.id.btnVisibilityPrivate -> "private"
                    R.id.btnVisibilityFriends -> "friends"
                    R.id.btnVisibilityPublic -> "public"
                    else -> "private"
                }

                // ✅ АВТОМАТИЧЕСКАЯ ДАТА ДЛЯ ПУБЛИЧНЫХ ТОЧЕК
                if (selectedVisibility == "public") {
                    selectedDate = System.currentTimeMillis()
                    updateDateText(selectedDate)
                }

                // Обновляем состояние поля даты
                updateDateFieldState()
            }
        }
        binding.btnVisibilityPrivate.isChecked = true

        // Остальные переключатели без изменений
        binding.toggleGroupLocation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) selectedLocation = if (checkedId == R.id.btnLocationShore) "С берега" else "С лодки"
        }
        binding.btnLocationShore.isChecked = true

        binding.chipGroupTackle.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) selectedTackle = group.findViewById<Chip>(checkedIds[0]).text.toString()
        }
        binding.chipGroupBait.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) selectedBait = group.findViewById<Chip>(checkedIds[0]).text.toString()
        }
        binding.chipGroupFish.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                selectedFish = chip.text.toString()
                binding.layoutOtherFish.visibility = if (selectedFish == "Другое") View.VISIBLE else View.GONE
                if (selectedFish != "Другое") binding.etOtherFish.text?.clear()
            }
        }

        // Логика выбора фото
        binding.cardPhoto.setOnClickListener {
            when (selectedVisibility) {
                "public" -> openCamera()
                else -> showPhotoSourceDialog()
            }
        }

        binding.btnRemovePhoto.setOnClickListener {
            selectedPhotoUri = null
            tempPhotoFile?.let { file -> if (file.exists()) file.delete() }
            tempPhotoFile = null
            binding.ivPhotoPreview.visibility = View.GONE
            binding.btnRemovePhoto.visibility = View.GONE
            binding.tvPhotoHint.visibility = View.VISIBLE
            binding.ivPhotoIcon.visibility = View.VISIBLE
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etSpotName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etSpotName.error = "Введите название места"
                return@setOnClickListener
            }

            val finalFishName = if (binding.chipOtherFish.isChecked) {
                binding.etOtherFish.text.toString().trim().ifEmpty { "Другая рыба" }
            } else selectedFish

            val weightValue = when (binding.toggleGroupWeight.checkedButtonId) {
                R.id.btnWeight1 -> 0.5; R.id.btnWeight2 -> 3.0; R.id.btnWeight3 -> 6.0; else -> 0.0
            }

            val desc = buildString {
                append(" Дата: ${formatDate(selectedDate)}\n📍 Место: $selectedLocation\n")
                append("🎣 Снасть: $selectedTackle | 🪱 Наживка: $selectedBait\n🐟 В улове: $finalFishName\n\n")
                append(binding.etDescription.text.toString().trim())
            }

            val lat = arguments?.getDouble("LAT") ?: 0.0
            val lon = arguments?.getDouble("LON") ?: 0.0
            if (lat == 0.0 && lon == 0.0) {
                Toast.makeText(context, "Ошибка координат", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }

            val authorName = UserSessionManager.getCurrentNickname(requireContext())
            val authorId = UserSessionManager.getCurrentUserId(requireContext())
            if (authorId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Ошибка сессии", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("NAME", name); putString("DESC", desc); putDouble("WEIGHT", weightValue)
                putString("VISIBILITY", selectedVisibility); putLong("CREATED_AT", selectedDate)
                putString("FISH_TYPE", finalFishName)
                putString("PHOTO_URI", selectedPhotoUri?.toString())
                putDouble("LAT", lat); putDouble("LON", lon)
                putString("AUTHOR_NAME", authorName ?: "Неизвестно"); putString("AUTHOR_ID", authorId)
            }
            findNavController().navigate(R.id.refineLocationFragment, bundle)
        }
    }

    // ✅ ОТКРЫТИЕ КАМЕРЫ
    private fun openCamera() {
        try {
            val photoFile = File.createTempFile(
                "fish_${System.currentTimeMillis()}_", ".jpg",
                requireContext().externalCacheDir
            )
            tempPhotoFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", photoFile
            )
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть камеру", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ ДИАЛОГ ВЫБОРА ИСТОЧНИКА
    private fun showPhotoSourceDialog() {
        val options = arrayOf("📷 Камера", "🖼️ Галерея")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Выберите источник фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }.show()
    }

    private fun showPhotoPreview(uri: Uri) {
        binding.ivPhotoPreview.visibility = View.VISIBLE
        binding.btnRemovePhoto.visibility = View.VISIBLE
        binding.tvPhotoHint.visibility = View.GONE
        binding.ivPhotoIcon.visibility = View.GONE
        Glide.with(this).load(uri).fitCenter().into(binding.ivPhotoPreview)
    }

    // ✅ НОВЫЙ МЕТОД: Управление состоянием поля даты
    // ✅ УПРАВЛЕНИЕ СОСТОЯНИЕМ ПОЛЯ ДАТЫ
    private fun updateDateFieldState() {
        val isPublic = selectedVisibility == "public"

        // Блокируем/разблокируем поле
        binding.etFishingDate.isEnabled = !isPublic
        binding.etFishingDate.isFocusable = !isPublic
        binding.etFishingDate.isClickable = !isPublic

        // Меняем подсказку для наглядности
        binding.layoutFishingDate.hint = if (isPublic)
            "Дата устанавливается автоматически"
        else
            "Дата рыбалки"

        // Визуально показываем блокировку серым цветом
        // Или возвращаем фирменный синий #0288D1
        val activeColor = 0xFF0288D1.toInt() // Твой основной синий
        val disabledColor = android.graphics.Color.GRAY

        if (isPublic) {
            binding.layoutFishingDate.boxStrokeColor = disabledColor
            binding.etFishingDate.setTextColor(disabledColor)
        } else {
            binding.layoutFishingDate.boxStrokeColor = activeColor
            binding.etFishingDate.setTextColor(android.graphics.Color.BLACK)
        }
    }

    // ✅ ПОКАЗ КАЛЕНДАРЯ (с защитой от вызова для публичных точек)
    private fun showDatePicker() {
        if (selectedVisibility == "public") return

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDate

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance()
                newCal.set(year, month, dayOfMonth)
                selectedDate = newCal.timeInMillis
                updateDateText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateText(ts: Long) {
        binding.etFishingDate.setText(
            SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(ts)
        )
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(ts)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}