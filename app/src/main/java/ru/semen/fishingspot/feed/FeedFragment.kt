package ru.semen.fishingspot.feed

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.JsonObject
import io.appwrite.models.RealtimeSubscription
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

import ru.semen.fishingspot.databinding.FragmentFeedBinding
import ru.semen.fishingspot.feed.SpotMapPreviewDialog
import ru.semen.fishingspot.map.point.comment.CommentsBottomSheetDialogFragment
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private val feedViewModel: FeedViewModel by viewModels()
    private lateinit var adapter: FeedAdapter

    // ✅ Активная подписка Realtime
    private var realtimeSubscription: RealtimeSubscription? = null

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
            onMapClick = { documentId ->
                SpotMapPreviewDialog.newInstance(documentId)
                    .show(childFragmentManager, "map_preview")
            },
            onCommentsClick = { spotId ->
                CommentsBottomSheetDialogFragment.newInstance(spotId)
                    .show(childFragmentManager, "comments")
            }
        )

        binding.rvFeed.adapter = adapter

        // ✅ Реактивное обновление: кэш → адаптер
        // (срабатывает и на putAvatar из Realtime, и после смены своей аватарки)
        ProfileCache.avatarUpdated.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }

        feedViewModel.loadFeed()

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

    override fun onResume() {
        super.onResume()
        startRealtimeAvatarUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeAvatarUpdates()
    }

    private fun startRealtimeAvatarUpdates() {
        realtimeSubscription?.close()

        // ✅ Подписка на изменения коллекции пользователей
        realtimeSubscription = AppwriteManager.realtime.subscribe(
            channels = arrayOf(
                "databases.6ad5021a0029a2c88786.collections.6ad5022b003770d2864c.documents"
            )
        ) { message ->
            Log.d("REALTIME_DEBUG", "ЛЮБОЕ СОБЫТИЕ: ${message.events}")

            val data = message.payload as? Map<*, *>
            Log.d("REALTIME_DEBUG", "payload = ${message.payload}")

            val userId = data?.get("\$id") as? String
            val avatarUrl = data?.get("avatarUrl") as? String

            if (!userId.isNullOrEmpty() && !avatarUrl.isNullOrEmpty()) {
                Log.d("REALTIME_DEBUG", "putAvatar($userId, $avatarUrl)")
                requireActivity().runOnUiThread {
                    ProfileCache.putAvatar(userId, avatarUrl)
                }
            }
        }


    }

    private fun stopRealtimeAvatarUpdates() {
        realtimeSubscription?.close()
        realtimeSubscription = null
    }

    override fun onDestroyView() {
        stopRealtimeAvatarUpdates()
        super.onDestroyView()
        _binding = null
    }
}
