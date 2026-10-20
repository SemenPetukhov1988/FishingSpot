package ru.semen.fishingspot.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentSpotDetailsBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.SpotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpotDetailsFragment : Fragment() {

    private var _binding: FragmentSpotDetailsBinding? = null
    private val binding get() = _binding!!
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var currentSpotId: Long = 0
    private var currentVisibility: String = "private"
    private var isCurrentlyPublicOnMap: Boolean = false

    companion object {
        private const val ARG_SPOT_ID = "spot_id"
        private const val ARG_SPOT_NAME = "spot_name"
        private const val ARG_SPOT_DESC = "spot_desc"
        private const val ARG_SPOT_WEIGHT = "spot_weight"
        private const val ARG_SPOT_CREATED = "spot_created"
        private const val ARG_SPOT_LAT = "spot_lat"
        private const val ARG_SPOT_LON = "spot_lon"
        private const val ARG_SPOT_AUTHOR_ID = "spot_author_id"       // ✅ Переименовали для ясности
        private const val ARG_SPOT_AUTHOR_NAME = "spot_author_name"   // ✅ Новое поле для ника
        private const val ARG_SPOT_IS_VERIFIED = "spot_is_verified"
        private const val ARG_SOURCE_TAB = "source_tab"
        private const val ARG_PHOTO_PATH = "photo_path"
        private const val ARG_VISIBILITY = "visibility"
        private const val ARG_IS_PUBLIC_ON_MAP = "is_public_on_map"

        fun newInstance(spot: FishingSpot, sourceTab: Int = R.id.nav_my_places): SpotDetailsFragment {
            return SpotDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SPOT_ID, spot.id)
                    putString(ARG_SPOT_NAME, spot.name)
                    putString(ARG_SPOT_DESC, spot.description)
                    putDouble(ARG_SPOT_WEIGHT, spot.catchWeight)
                    putLong(ARG_SPOT_CREATED, spot.createdAt)
                    putDouble(ARG_SPOT_LAT, spot.latitude)
                    putDouble(ARG_SPOT_LON, spot.longitude)
                    putString(ARG_SPOT_AUTHOR_ID, spot.authorId)
                    putString(ARG_SPOT_AUTHOR_NAME, spot.authorName) // ✅ Передаем имя автора
                    putBoolean(ARG_SPOT_IS_VERIFIED, spot.isVerified)
                    putString(ARG_PHOTO_PATH, spot.photoPath)
                    putString(ARG_VISIBILITY, spot.visibility)
                    putBoolean(ARG_IS_PUBLIC_ON_MAP, spot.isPublicOnMap)
                    putInt(ARG_SOURCE_TAB, sourceTab)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpotDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_SPOT_NAME) ?: ""
        val desc = arguments?.getString(ARG_SPOT_DESC) ?: ""
        val weight = arguments?.getDouble(ARG_SPOT_WEIGHT) ?: 0.0
        val created = arguments?.getLong(ARG_SPOT_CREATED) ?: System.currentTimeMillis()
        val lat = arguments?.getDouble(ARG_SPOT_LAT) ?: 0.0
        val lon = arguments?.getDouble(ARG_SPOT_LON) ?: 0.0

        // ✅ Читаем оригинальные данные автора
        val originalAuthorId = arguments?.getString(ARG_SPOT_AUTHOR_ID) ?: "unknown"
        val originalAuthorName = arguments?.getString(ARG_SPOT_AUTHOR_NAME) ?: "Рыбак"

        currentVisibility = arguments?.getString(ARG_VISIBILITY) ?: "private"
        isCurrentlyPublicOnMap = arguments?.getBoolean(ARG_IS_PUBLIC_ON_MAP) ?: false
        currentSpotId = arguments?.getLong(ARG_SPOT_ID) ?: 0L
        val sourceTab = arguments?.getInt(ARG_SOURCE_TAB, R.id.nav_my_places) ?: R.id.nav_my_places

        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: ""

        // ✅ isAuthor будет true ТОЛЬКО если текущий пользователь совпадает с ОРИГИНАЛЬНЫМ автором
        val isAuthor = currentUserId.isNotEmpty() && currentUserId == originalAuthorId

        // 1. Заполнение UI
        binding.tvSpotName.text = name
        binding.tvDescription.text = desc.ifEmpty { "Нет описания" }
        binding.tvCatchWeight.text = getWeightText(weight)

        // ✅ Показываем ник, а не ID!
        binding.tvAuthorName.text = if (isAuthor) "Вы (Автор)" else originalAuthorName

        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
        binding.tvFishingDate.text = "📅 Рыбалка: ${dateFormat.format(Date(created))}"

        // =====================================================================
        // 2. ЛОГИКА КНОПОК
        // =====================================================================

        // ✅ КНОПКА 1: Добраться (ВСЕГДА видна)
        binding.btnNavigate.visibility = View.VISIBLE
        binding.btnNavigate.setOnClickListener {
            if (lat == 0.0 && lon == 0.0) {
                Toast.makeText(requireContext(), "⚠️ Координаты не найдены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
            }
        }

        if (isAuthor) {
            // ✅ КНОПКА 2: Управление видимостью для друзей (ТОЛЬКО ОРИГИНАЛЬНЫЙ АВТОР)
            binding.btnToggleFriendsVisibility.visibility = View.VISIBLE
            updateFriendsButtonUI()
            binding.btnToggleFriendsVisibility.setOnClickListener {
                if (currentVisibility == "friends") {
                    showConfirmationDialog("Скрыть от друзей?", "Эта точка станет видимой только для вас.", "Скрыть") {
                        currentVisibility = "private"
                        spotViewModel.updateSpotVisibility(currentSpotId, "private")
                        updateFriendsButtonUI()
                        Toast.makeText(requireContext(), "Точка скрыта от друзей", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showConfirmationDialog("Показать друзьям?", "Эта точка станет видимой для всех ваших друзей.", "Показать") {
                        currentVisibility = "friends"
                        spotViewModel.updateSpotVisibility(currentSpotId, "friends")
                        updateFriendsButtonUI()
                        Toast.makeText(requireContext(), "Точка теперь видна друзьям", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ✅ КНОПКА 3: Управление публичной картой (ТОЛЬКО ОРИГИНАЛЬНЫЙ АВТОР)
            binding.btnTogglePublicMap.visibility = View.VISIBLE
            updatePublicMapButtonUI()
            binding.btnTogglePublicMap.setOnClickListener {
                if (!isCurrentlyPublicOnMap) {
                    showConfirmationDialog("Показать на публичной карте?", "Эта точка станет видна всем пользователям приложения.", "Показать") {
                        isCurrentlyPublicOnMap = true
                        spotViewModel.updatePublicMapVisibility(currentSpotId, true)
                        updatePublicMapButtonUI()
                        Toast.makeText(requireContext(), "Точка на публичной карте", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showConfirmationDialog("Скрыть с публичной карты?", "Эта точка исчезнет с публичной карты, но останется в вашем дневнике.", "Скрыть") {
                        isCurrentlyPublicOnMap = false
                        spotViewModel.updatePublicMapVisibility(currentSpotId, false)
                        updatePublicMapButtonUI()
                        Toast.makeText(requireContext(), "Точка скрыта с публичной карты", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ✅ КНОПКА 4: Удалить точку (ТОЛЬКО ОРИГИНАЛЬНЫЙ АВТОР - удаляет везде)
            binding.btnDeleteSpot.visibility = View.VISIBLE
            binding.btnDeleteSpot.setOnClickListener {
                showConfirmationDialog("Удалить точку?", "Точка будет удалена везде: из вашего дневника и с публичной карты. Это действие нельзя отменить.", "Удалить", true) {
                    spotViewModel.deleteSpot(currentSpotId)
                    Toast.makeText(requireContext(), "Точка удалена", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }

            // Кнопка "Сохранить" скрыта для автора
            binding.btnSaveToLocal.visibility = View.GONE

        } else {
            // =====================================================================
            // ЛОГИКА ДЛЯ НЕ-АВТОРА (Тот, кто просматривает чужую точку)
            // =====================================================================
            binding.btnToggleFriendsVisibility.visibility = View.GONE
            binding.btnTogglePublicMap.visibility = View.GONE

            // ✅ КНОПКА: Удалить (Удаляет только мою локальную копию)
            binding.btnDeleteSpot.visibility = View.VISIBLE
            binding.btnDeleteSpot.setOnClickListener {
                showConfirmationDialog("Удалить из дневника?", "Эта точка будет удалена только из вашего дневника. Оригинал у автора останется.", "Удалить", true) {
                    spotViewModel.deleteSpot(currentSpotId)
                    Toast.makeText(requireContext(), "Точка удалена из дневника", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }

            // ✅ КНОПКА: Сохранить (Если мы еще не сохранили её)
            binding.btnSaveToLocal.visibility = View.VISIBLE
            binding.btnSaveToLocal.setOnClickListener {
                val currentLocalSpots = spotViewModel.localSpots.value ?: emptyList()
                val isAlreadySaved = currentLocalSpots.any { it.name == name && it.latitude == lat && it.longitude == lon }

                if (isAlreadySaved) {
                    Toast.makeText(requireContext(), "✅ Эта точка уже в вашем дневнике!", Toast.LENGTH_SHORT).show()
                } else {
                    // ✅ ГЛАВНОЕ ИСПРАВЛЕНИЕ: передаем ОРИГИНАЛЬНЫЕ authorId и authorName!
                    spotViewModel.addFullSpot(
                        lat = lat, lon = lon, name = name, description = desc, weight = weight,
                        visibility = "private", photoPath = arguments?.getString(ARG_PHOTO_PATH),
                        fishType = null, isVerified = false, createdAt = created,
                        authorId = originalAuthorId,      // <-- Оставляем оригинального автора!
                        authorName = originalAuthorName   // <-- Оставляем оригинальный ник!
                    )
                    Toast.makeText(requireContext(), "✅ Точка '$name' добавлена в дневник!", Toast.LENGTH_SHORT).show()
                    // После сохранения кнопка исчезает, так как точка теперь "в дневнике"
                    binding.btnSaveToLocal.visibility = View.GONE
                }
            }
        }

        binding.btnBack.setOnClickListener {
            spotViewModel.setSelectedTab(sourceTab)
            findNavController().navigateUp()
        }
    }

    // --- Вспомогательные методы ---

    private fun updateFriendsButtonUI() {
        binding.btnToggleFriendsVisibility.text = if (currentVisibility == "friends") "🙈 Скрыть от друзей" else "👥 Показать точку друзьям"
    }

    private fun updatePublicMapButtonUI() {
        binding.btnTogglePublicMap.text = if (isCurrentlyPublicOnMap) "🌍 Скрыть с публичной карты" else "🌍 Показать на публичной карте"
    }

    private fun showConfirmationDialog(title: String, message: String, positiveText: String, isDestructive: Boolean = false, onConfirm: () -> Unit) {
        if (!isAdded || _binding == null) return
        val builder = AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ -> dialog.dismiss(); onConfirm() }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
        val dialog = builder.create()
        dialog.show()
        if (isDestructive) dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
    }

    private fun getWeightText(weight: Double): String {
        return when {
            weight <= 0.0 -> "🐟 Улов не указан"
            weight <= 1.0 -> "⚖️ Вес улова: До 1 кг"
            weight <= 5.0 -> "⚖️ Вес улова: 1–5 кг"
            else -> "⚖️ Вес улова: Больше 5 кг"
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}