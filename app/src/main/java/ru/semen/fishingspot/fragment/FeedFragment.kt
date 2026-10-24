package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ru.semen.fishingspot.adapter.FeedAdapter
import ru.semen.fishingspot.databinding.FragmentFeedBinding
import ru.semen.fishingspot.viewmodel.FeedViewModel

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private val feedViewModel: FeedViewModel by viewModels()
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

        // Настраиваем RecyclerView
        binding.rvFeed.layoutManager = LinearLayoutManager(requireContext())

        // ✅ СОЗДАЁМ АДАПТЕР ТОЛЬКО С ОДНИМ CALLBACK'ОМ - ОТКРЫТИЕ КАРТЫ
        // В onViewCreated, при создании адаптера:
        adapter = FeedAdapter(
            onMapClick = { spot ->
                SpotMapPreviewDialog.newInstance(
                    lat = spot.latitude,
                    lon = spot.longitude,
                    spotName = spot.name
                ).show(childFragmentManager, "map_preview")
            },
            // ✅ НОВАЯ ЛОГИКА ОТКРЫТИЯ КОММЕНТАРИЕВ
            onCommentsClick = { spotId ->
                CommentsBottomSheetDialogFragment.newInstance(spotId)
                    .show(childFragmentManager, "comments")
            }
        )

        binding.rvFeed.adapter = adapter

        // ✅ ЗАГРУЖАЕМ ПУБЛИЧНЫЕ ТОЧКИ ИЗ APPWRITE (ВМЕСТО FIREBASE LISTENING)
        feedViewModel.loadFeed()

        // Наблюдаем за данными ленты
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

    // ✅ УДАЛЕНЫ onStart() И onStop() — ОНИ БОЛЬШЕ НЕ НУЖНЫ ДЛЯ APPWRITE
    // Загрузка происходит один раз при открытии фрагмента через loadFeed()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}