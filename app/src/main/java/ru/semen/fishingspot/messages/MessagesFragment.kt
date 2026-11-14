package ru.semen.fishingspot.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.semen.fishingspot.databinding.FragmentMessagesBinding
import ru.semen.fishingspot.feature_messages.viewmodel.MessagesViewModel
import ru.semen.fishingspot.feature_messages.viewmodel.MessagesUiState
import ru.semen.fishingspot.messages.adapter.MessagesAdapter
import ru.semen.fishingspot.messages.data.AnchorGroupItem
import ru.semen.fishingspot.messages.data.FriendMessageItem
import ru.semen.fishingspot.messages.notifikation.onfishing.NotificationMapPreviewDialog
import ru.semen.fishingspot.utils.UserSessionManager

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private val messagesViewModel: MessagesViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter
    private var isMapDialogShowing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupSwipeToDelete()
        observeState()

        binding.fabAddMessage.setOnClickListener {
            SignalTypeBottomSheet().show(childFragmentManager, "signal_type_sheet")
        }

        binding.swipeRefresh.setOnRefreshListener {
            messagesViewModel.refreshData()
        }
    }

    private fun setupAdapter() {
        adapter = MessagesAdapter(
            onSenderClick = { item ->
                val name = when (item) {
                    is FriendMessageItem -> item.message.senderName
                    else -> "Неизвестный отправитель"
                }
                Toast.makeText(requireContext(), "Профиль: $name", Toast.LENGTH_SHORT).show()
            },
            onShowMapClick = { item ->
                if (isMapDialogShowing) return@MessagesAdapter
                // Логика карты для сигналов при необходимости
            },
            onWishGoodFishingClick = { anchorId, receiverId ->
                val userId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@MessagesAdapter
                val nickname = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"
                messagesViewModel.sendWish(anchorId, receiverId, userId, nickname)
                Toast.makeText(requireContext(), "Пожелание отправлено! 🎣", Toast.LENGTH_SHORT).show()
            },
            onAcceptFriendClick = { senderId, messageId ->
                messagesViewModel.acceptFriendRequest(senderId, messageId)
            },
            onDeleteMessageClick = { messageId ->
                messagesViewModel.declineRequest(messageId)
            }
        )
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter
    }

    // ✅ СВАЙП ЧЕРЕЗ ПУБЛИЧНЫЙ МЕТОД АДАПТЕРА
    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // ✅ ИСПОЛЬЗУЕМ getItemAt ВМЕСТО ЗАЩИЩЕННОГО getItem
                    val item = adapter.getItemAt(position)

                    val messageId = when (item) {
                        is FriendMessageItem -> item.message.id
                        is AnchorGroupItem -> item.anchorMessage.id
                        else -> null
                    }

                    messageId?.let { messagesViewModel.swipeToDelete(it) }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvMessages)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    messagesViewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefresh.isRefreshing = refreshing
                    }
                }
                launch {
                    messagesViewModel.uiState.collect { state ->
                        when (state) {
                            is MessagesUiState.Success -> {
                                adapter.submitList(state.feedItems)
                                binding.layoutEmptyState.visibility =
                                    if (state.feedItems.isEmpty()) View.VISIBLE else View.GONE
                                binding.rvMessages.visibility =
                                    if (state.feedItems.isEmpty()) View.GONE else View.VISIBLE
                                state.signalsError?.let {
                                    Toast.makeText(requireContext(), "Сигналы: $it", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is MessagesUiState.Error -> {
                                Toast.makeText(requireContext(), "Ошибка: ${state.message}", Toast.LENGTH_LONG).show()
                            }
                            is MessagesUiState.Loading -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}