package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentRefineLocationBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class RefineLocationFragment : Fragment() {

    private var _binding: FragmentRefineLocationBinding? = null
    private val binding get() = _binding!!

    private var spotName: String = ""
    private var spotDesc: String = ""
    private var spotWeight: Double = 0.0
    private var isPublic: Boolean = false
    private var photoPath: String? = null

    private val spotViewModel: SpotViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRefineLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Получаем данные
        spotName = arguments?.getString("NAME") ?: "Без названия"
        spotDesc = arguments?.getString("DESC") ?: ""
        spotWeight = arguments?.getDouble("WEIGHT") ?: 0.0
        isPublic = arguments?.getBoolean("IS_PUBLIC") ?: false
        photoPath = arguments?.getString("PHOTO_PATH")

        val lat = arguments?.getDouble("LAT") ?: 0.0
        val lon = arguments?.getDouble("LON") ?: 0.0

        Log.d("REFINE_DEBUG", "Получены координаты: $lat, $lon")

        if (lat == 0.0 && lon == 0.0) {
            Toast.makeText(context, "Ошибка координат", Toast.LENGTH_SHORT).show()
            return
        }

        MapKitFactory.getInstance().onStart()
        binding.refineMapView.onStart()

        // ✅ Приближение к точке
        val initialPoint = Point(lat, lon)
        binding.refineMapView.map.move(
            CameraPosition(initialPoint, 15.0f, 0.0f, 0.0f),
            com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
            null
        )

        // 2. Кнопка Готово
        binding.btnDone.setOnClickListener {
            val centerPoint = binding.refineMapView.map.cameraPosition.target
            Log.d("REFINE_DEBUG", "Сохраняем точку: ${spotName}, Коорд: ${centerPoint.latitude}, ${centerPoint.longitude}")

            spotViewModel.addFullSpot(
                lat = centerPoint.latitude,
                lon = centerPoint.longitude,
                name = spotName,
                description = spotDesc,
                weight = spotWeight,
                isPublic = isPublic,
                photoPath = photoPath
            )

            // Навигация
            try {
                requireActivity().findNavController(R.id.fragmentContainer)
                    .navigate(R.id.action_refine_to_tabs)
            } catch (e: Exception) {
                Log.e("NAV_ERROR", "Ошибка навигации", e)
                // Если экшен не найден, пробуем просто назад
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        _binding?.refineMapView?.onStart()
    }

    override fun onStop() {
        _binding?.refineMapView?.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}