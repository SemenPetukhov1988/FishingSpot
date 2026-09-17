package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentAddPointBinding

class AddPointFragment : Fragment() {

    private var _binding: FragmentAddPointBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPointBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Стрелка назад
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Кнопка-галочка (FAB)
        binding.fabSavePoint.setOnClickListener {
            val name = binding.etLocationName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etLocationName.error = "Введите название места"
                return@setOnClickListener
            }

            // ✅ ВАЖНО: Берем координаты из arguments (они пришли из MyMapFragment)
            // Если их нет, берем 0, но лучше проверить
            val startLat = arguments?.getDouble("LAT") ?: 0.0
            val startLon = arguments?.getDouble("LON") ?: 0.0

            if (startLat == 0.0 && startLon == 0.0) {
                // Если координат нет, можно показать ошибку или взять текущую позицию GPS
                // Пока просто предупредим
                android.widget.Toast.makeText(context, "Ошибка: нет координат старта", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("NAME", name)
                putString("DESC", binding.etDescription.text.toString())

                val weightText = binding.etCatchWeight.text.toString()
                putDouble("WEIGHT", if (weightText.isNotEmpty()) weightText.toDouble() else 0.0)

                putBoolean("IS_PUBLIC", binding.swIsPublic.isChecked)
                putString("PHOTO_PATH", null)

                // ✅ ПЕРЕДАЕМ КООРДИНАТЫ ДАЛЬШЕ
                putDouble("LAT", startLat)
                putDouble("LON", startLon)
            }

            requireActivity().findNavController(R.id.fragmentContainer)
                .navigate(R.id.action_addPoint_to_refineLocation, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}