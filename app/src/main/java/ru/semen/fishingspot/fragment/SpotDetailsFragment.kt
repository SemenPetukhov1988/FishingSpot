package ru.semen.fishingspot.fragment

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

    companion object {
        private const val ARG_SPOT_ID = "spot_id"
        private const val ARG_SPOT_NAME = "spot_name"
        private const val ARG_SPOT_DESC = "spot_desc"
        private const val ARG_SPOT_WEIGHT = "spot_weight"
        private const val ARG_SPOT_CREATED = "spot_created"

        // ✅ Новые поля для передачи
        private const val ARG_SPOT_LAT = "spot_lat"
        private const val ARG_SPOT_LON = "spot_lon"
        private const val ARG_SPOT_AUTHOR = "spot_author"
        private const val ARG_SPOT_IS_VERIFIED = "spot_is_verified"
        private const val ARG_SOURCE_TAB = "source_tab"
        private const val ARG_PHOTO_PATH = "photo_path"

        fun newInstance(spot: FishingSpot, sourceTab: Int = R.id.nav_global_map): SpotDetailsFragment {
            return SpotDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SPOT_ID, spot.id)
                    putString(ARG_SPOT_NAME, spot.name)
                    putString(ARG_SPOT_DESC, spot.description)
                    putDouble(ARG_SPOT_WEIGHT, spot.catchWeight)
                    putLong(ARG_SPOT_CREATED, spot.createdAt)

                    // ✅ Передаем новые данные
                    putDouble(ARG_SPOT_LAT, spot.latitude)
                    putDouble(ARG_SPOT_LON, spot.longitude)
                    putString(ARG_SPOT_AUTHOR, spot.authorId)
                    putBoolean(ARG_SPOT_IS_VERIFIED, spot.isVerified)
                    putString(ARG_PHOTO_PATH, spot.photoPath)
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

        // 1. Получаем все данные из Bundle
        val name = arguments?.getString(ARG_SPOT_NAME) ?: ""
        val desc = arguments?.getString(ARG_SPOT_DESC) ?: ""
        val weight = arguments?.getDouble(ARG_SPOT_WEIGHT) ?: 0.0
        val created = arguments?.getLong(ARG_SPOT_CREATED) ?: 0L

        val lat = arguments?.getDouble(ARG_SPOT_LAT) ?: 0.0
        val lon = arguments?.getDouble(ARG_SPOT_LON) ?: 0.0
        val authorId = arguments?.getString(ARG_SPOT_AUTHOR) ?: "неизвестно"
        val isVerified = arguments?.getBoolean(ARG_SPOT_IS_VERIFIED) ?: false
        val sourceTab = arguments?.getInt(ARG_SOURCE_TAB, R.id.nav_global_map) ?: R.id.nav_global_map

        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: ""
        val isAuthor = currentUserId.isNotEmpty() && currentUserId == authorId

        // 2. Заполняем UI (старая логика сохранена)
        binding.tvSpotName.text = name
        binding.tvDescription.text = desc.ifEmpty { "Нет описания" }
        binding.tvCatchWeight.text = getWeightText(weight)

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        binding.tvCreatedAt.text = "📅 Создано: ${dateFormat.format(Date(created))}"

        // ✅ Заполняем новые поля
        binding.tvCoordinates.text = "📍 Широта: %.4f, Долгота: %.4f".format(lat, lon)
        binding.tvAuthor.text = "👤 Автор: $authorId"

        // ✅ Логика карточки-предупреждения
        binding.cardSuspiciousWarning.visibility = if (isVerified) View.GONE else View.VISIBLE

        // =====================================================================
        // 3. НАСТРОЙКА КНОПОК (Заглушки с Toast, чтобы ничего не сломать)
        // =====================================================================

        binding.btnShare.setOnClickListener {
            if (!isAuthor) {
                Toast.makeText(requireContext(), "🔒 Поделиться может только автор точки", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "📤 Функция 'Поделиться' в разработке", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnConfirmSpot.setOnClickListener {
            if (isAuthor) {
                Toast.makeText(requireContext(), "ℹ️ Это ваша точка, подтверждение не требуется", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "✅ Функция 'Подтвердить' в разработке", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDisputeSpot.setOnClickListener {
            if (isAuthor) {
                Toast.makeText(requireContext(), "ℹ️ Это ваша точка", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "⚠️ Функция 'Опровергнуть' в разработке", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNavigate.setOnClickListener {
            Toast.makeText(requireContext(), "🗺️ Открываем Яндекс.Навигатор (в разработке)", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveToLocal.setOnClickListener {
            Toast.makeText(requireContext(), "💾 Сохранение на локальную карту (в разработке)", Toast.LENGTH_SHORT).show()
        }

        binding.btnComments.setOnClickListener {
            Toast.makeText(requireContext(), "💬 Комментарии скоро будут добавлены", Toast.LENGTH_SHORT).show()
        }

        // 4. Кнопка НАЗАД (возвращает именно туда, откуда пришли, благодаря sourceTab)
        binding.btnBack.setOnClickListener {
            spotViewModel.setSelectedTab(sourceTab)
            findNavController().navigateUp()
        }
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