package ru.semen.fishingspot.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ru.semen.fishingspot.adapter.FeedAdapter
import ru.semen.fishingspot.databinding.FragmentFeedBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.SpotViewModel

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    // ✅ Наша ViewModel для ленты (Firebase)
    private val feedViewModel: FeedViewModel by viewModels()

    // ✅ Общая ViewModel для локальной базы (Room)
    private val spotViewModel: SpotViewModel by activityViewModels()

    private lateinit var adapter: FeedAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvFeed.layoutManager = LinearLayoutManager(requireContext())

        adapter = FeedAdapter(
            onSaveClick = { spot ->
                val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: "unknown_user"

                // 1. Проверка: это моя собственная точка (созданная мной в этом аккаунте)?
                if (spot.authorId == currentUserId) {
                    Toast.makeText(requireContext(), "⚠️ Это уже ваша точка! Она в вашем дневнике.", Toast.LENGTH_SHORT).show()
                    return@FeedAdapter
                }

                // 2. Проверка: а может, я уже сохранял эту чужую точку ранее?
                // ВАЖНО: Мы НЕ сравниваем authorId, потому что при сохранении чужой точки
                // в локальную базу, она получает ТЕКУЩИЙ authorId (твой), а не оригинальный.
                val currentLocalSpots = spotViewModel.allSpots.value ?: emptyList()
                val isAlreadySaved = currentLocalSpots.any { localSpot ->
                    localSpot.name == spot.name &&
                            localSpot.latitude == spot.latitude &&
                            localSpot.longitude == spot.longitude
                }

                if (isAlreadySaved) {
                    Toast.makeText(requireContext(), "✅ Эта точка уже сохранена в вашем дневнике!", Toast.LENGTH_SHORT).show()
                    return@FeedAdapter
                }

                // 3. Если всё ок, сохраняем в Room
                spotViewModel.addFullSpot(
                    lat = spot.latitude,
                    lon = spot.longitude,
                    name = spot.name,
                    description = spot.description,
                    weight = spot.catchWeight,
                    isPublic = false,      // Только локально
                    photoPath = null,      // Пока без фото
                    isVerified = true      // Считаем проверенной
                )

                Toast.makeText(requireContext(), "✅ Точка '${spot.name}' добавлена в дневник!", Toast.LENGTH_SHORT).show()
            },
            onCommentClick = { spot ->
                Toast.makeText(requireContext(), "💬 Комментарии скоро будут", Toast.LENGTH_SHORT).show()
            },
            onConfirmClick = { spot ->
                Toast.makeText(requireContext(), "✅ Спасибо за проверку!", Toast.LENGTH_SHORT).show()
            },
            onDisputeClick = { spot ->
                Toast.makeText(requireContext(), "⚠️ Жалоба отправлена", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvFeed.adapter = adapter

        feedViewModel.spots.observe(viewLifecycleOwner) { spots ->
            if (spots.isEmpty()) {
                binding.progressFeed.visibility = View.GONE
                binding.tvEmptyFeed.visibility = View.VISIBLE
                binding.rvFeed.visibility = View.GONE
            } else {
                binding.progressFeed.visibility = View.GONE
                binding.tvEmptyFeed.visibility = View.GONE
                binding.rvFeed.visibility = View.VISIBLE
                adapter.submitList(spots)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        feedViewModel.startListening()
    }

    override fun onStop() {
        super.onStop()
        feedViewModel.stopListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}