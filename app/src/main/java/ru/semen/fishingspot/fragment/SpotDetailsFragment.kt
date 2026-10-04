package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentSpotDetailsBinding
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
        private const val ARG_SOURCE_TAB = "source_tab" // ✅ Новое поле

        fun newInstance(spot: FishingSpot, sourceTab: Int = R.id.nav_my_places): SpotDetailsFragment {
            return SpotDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SPOT_ID, spot.id)
                    putString(ARG_SPOT_NAME, spot.name)
                    putString(ARG_SPOT_DESC, spot.description)
                    putDouble(ARG_SPOT_WEIGHT, spot.catchWeight)
                    putLong(ARG_SPOT_CREATED, spot.createdAt)
                    putInt(ARG_SOURCE_TAB, sourceTab) // ✅ Сохраняем, откуда пришли
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
        val created = arguments?.getLong(ARG_SPOT_CREATED) ?: 0L
        val sourceTab = arguments?.getInt(ARG_SOURCE_TAB, R.id.nav_my_places) ?: R.id.nav_my_places // ✅ Читаем вкладку

        binding.tvSpotName.text = name
        binding.tvDescription.text = desc.ifEmpty { "Нет описания" }
        binding.tvCatchWeight.text = if (weight > 0) "🐟 Улов: ${weight} кг" else "🐟 Улов не указан"

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        binding.tvCreatedAt.text = "📅 Создано: ${dateFormat.format(Date(created))}"

        // ✅ ИСПРАВЛЕННАЯ КНОПКА НАЗАД
        binding.btnBack.setOnClickListener {
            spotViewModel.setSelectedTab(sourceTab) // ✅ Говорим MainTabs, какую вкладку подсветить
            findNavController().navigateUp()         // ✅ Возвращаемся назад
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}