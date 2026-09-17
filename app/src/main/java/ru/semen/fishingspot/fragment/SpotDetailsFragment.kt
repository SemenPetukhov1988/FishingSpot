package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentSpotDetailsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpotDetailsFragment : Fragment() {

    private var _binding: FragmentSpotDetailsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_SPOT_ID = "spot_id"
        private const val ARG_SPOT_NAME = "spot_name"
        private const val ARG_SPOT_DESC = "spot_desc"
        private const val ARG_SPOT_WEIGHT = "spot_weight"
        private const val ARG_SPOT_CREATED = "spot_created"

        fun newInstance(spot: FishingSpot): SpotDetailsFragment {
            return SpotDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SPOT_ID, spot.id)
                    putString(ARG_SPOT_NAME, spot.name)
                    putString(ARG_SPOT_DESC, spot.description)
                    putDouble(ARG_SPOT_WEIGHT, spot.catchWeight)
                    putLong(ARG_SPOT_CREATED, spot.createdAt)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_SPOT_NAME) ?: ""
        val desc = arguments?.getString(ARG_SPOT_DESC) ?: ""
        val weight = arguments?.getDouble(ARG_SPOT_WEIGHT) ?: 0.0
        val created = arguments?.getLong(ARG_SPOT_CREATED) ?: 0L

        binding.tvSpotName.text = name
        binding.tvDescription.text = desc.ifEmpty { "Нет описания" }

        binding.tvCatchWeight.text = if (weight > 0) {
            "🐟 Улов: ${weight} кг"
        } else {
            "🐟 Улов не указан"
        }

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        binding.tvCreatedAt.text = "📅 Создано: ${dateFormat.format(Date(created))}"

        // ✅ ИСПРАВЛЕННАЯ КНОПКА НАЗАД
        binding.btnBack.setOnClickListener {
            // Используем NavController для правильного возврата в стек
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}