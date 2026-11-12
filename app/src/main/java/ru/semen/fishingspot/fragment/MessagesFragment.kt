package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import io.appwrite.Query
import kotlinx.coroutines.launch
import ru.semen.fishingspot.adapter.MessagesAdapter
import ru.semen.fishingspot.data.AnchorGroupItem
import ru.semen.fishingspot.data.AppMessage
import ru.semen.fishingspot.data.FeedItem
import ru.semen.fishingspot.data.FishingSignal
import ru.semen.fishingspot.data.FriendMessageItem
import ru.semen.fishingspot.data.SignalItem
import ru.semen.fishingspot.databinding.FragmentMessagesBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.FishingSignalsViewModel
import ru.semen.fishingspot.viewmodel.FriendsViewModel
import ru.semen.fishingspot.viewmodel.SignalsUiState
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MessagesFragment : Fragment() {

    companion object {
        private const val MESSAGES_COL_ID = "6aeb4c1600142e707a69"
        private const val NOTIFICATIONS_COL_ID = "6aef1b7300251aa8ce5b"
        private const val DB_ID = "6ad5021a0029a2c88786"
    }

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val signalsViewModel: FishingSignalsViewModel by activityViewModels()
    private val spotViewModel: SpotViewModel by viewModels()
    private val friendsViewModel: FriendsViewModel by viewModels()

    private lateinit var adapter: MessagesAdapter
    private var friendMessages: List<AppMessage> = emptyList()
    private var fishingSignals: List<FishingSignal> = emptyList()
    private var isMapDialogShowing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        observeSignalsState()

        // ✅ Запускаем ТОЛЬКО загрузку сообщений. Сигналы загрузятся ПОСЛЕ них.
        loadFriendMessages()

        // ❌ УБРАНО: loadSignalsIfNeeded() - чтобы избежать гонки данных при старте

        binding.fabAddMessage.setOnClickListener {
            SignalTypeBottomSheet().show(childFragmentManager, "signal_type_sheet")
        }
    }

    override fun onResume() {
        super.onResume()
        loadSignalsIfNeeded()
    }

    private fun loadSignalsIfNeeded() {
        val userId = UserSessionManager.getCurrentUserId(requireContext())
        if (!userId.isNullOrEmpty()) {
            signalsViewModel.loadSignals(userId)
        }
    }

    private fun setupAdapter() {
        adapter = MessagesAdapter(
            onSenderClick = { item ->
                val name = when (item) {
                    is FriendMessageItem -> item.message.senderName
                    is SignalItem -> item.signal.senderName
                    is AnchorGroupItem -> item.anchorMessage.senderName
                    else -> "Неизвестный отправитель"
                }
                Toast.makeText(requireContext(), "Профиль: $name", Toast.LENGTH_SHORT).show()
            },
            onMessageClick = { /* Логика клика при необходимости */ },

            onShowMapClick = { item ->
                if (isMapDialogShowing) return@MessagesAdapter

                if (item is SignalItem) {
                    val lat = item.signal.lat
                    val lon = item.signal.lon

                    if (lat == 0.0 && lon == 0.0) {
                        Toast.makeText(requireContext(), "Координаты недоступны", Toast.LENGTH_SHORT).show()
                        return@MessagesAdapter
                    }

                    isMapDialogShowing = true
                    try {
                        NotificationMapPreviewDialog.newInstance(
                            lat = lat,
                            lon = lon,
                            name = item.signal.senderName
                        ).show(childFragmentManager, "notif_map_preview")

                        view?.postDelayed({ isMapDialogShowing = false }, 500)
                    } catch (e: Exception) {
                        isMapDialogShowing = false
                        Toast.makeText(requireContext(), "Ошибка открытия карты", Toast.LENGTH_SHORT).show()
                    }
                }
            },

            onWishGoodFishingClick = { anchorId, receiverId ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch
                        val currentNickname = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"

                        val signalToDelete = fishingSignals.find {
                            it.anchorId == anchorId &&
                                    it.senderId == receiverId &&
                                    it.type == "on_fishing"
                        }

                        if (signalToDelete != null) {
                            AppwriteManager.databases.deleteDocument(
                                databaseId = DB_ID,
                                collectionId = NOTIFICATIONS_COL_ID,
                                documentId = signalToDelete.id
                            )
                            android.util.Log.d("FRAGMENT_WISH", "🗑️ Сигнал ${signalToDelete.id} удалён из базы")
                        } else {
                            android.util.Log.e("FRAGMENT_WISH", "❌ Не найден сигнал для удаления: anchorId=$anchorId")
                        }

                        signalsViewModel.sendSignal(
                            senderId = currentUserId,
                            senderName = currentNickname,
                            receiverId = receiverId,
                            type = "fishing_wish",
                            anchorId = anchorId,
                            messageText = "Хорошей рыбалки! 🎣"
                        )

                        Toast.makeText(requireContext(), "Пожелание отправлено! 🎣", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        android.util.Log.e("FRAGMENT_WISH", "❌ Ошибка: ${e.message}")
                    }
                }
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter
    }

    private fun observeSignalsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                signalsViewModel.uiState.collect { state ->
                    when (state) {
                        is SignalsUiState.Loading -> {
                            android.util.Log.d("SIGNAL_LOAD", "⏳ Загрузка сигналов...")
                        }
                        is SignalsUiState.Success -> {
                            android.util.Log.d("SIGNAL_LOAD", "✅ Получено ${state.signals.size} сигналов")

                            fishingSignals = state.signals
                            updateCombinedList()
                        }
                        is SignalsUiState.Error -> {
                            android.util.Log.e("SIGNAL_LOAD", "❌ Ошибка: ${state.message}")
                            Toast.makeText(requireContext(), "Ошибка сигналов: ${state.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadFriendMessagesSync() {
        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return

        try {
            val messagesResult = AppwriteManager.databases.listDocuments(
                databaseId = DB_ID,
                collectionId = MESSAGES_COL_ID,
                queries = listOf(
                    Query.equal("recipientId", currentUserId),
                    Query.orderDesc("timestamp"),
                    Query.limit(50)
                )
            )

            val items = mutableListOf<AppMessage>()
            messagesResult.documents.forEach { doc ->
                val data = doc.data
                items.add(AppMessage(
                    id = doc.id, messageId = doc.id,
                    senderId = data["senderId"] as? String ?: "",
                    senderName = data["senderName"] as? String ?: "Рыбак",
                    recipientId = data["recipientId"] as? String ?: "",
                    type = data["type"] as? String ?: "SYSTEM",
                    spotName = data["spotName"] as? String ?: "",
                    spotLat = data["spotLat"] as? Double ?: 0.0,
                    spotLon = data["spotLon"] as? Double ?: 0.0,
                    contentText = data["contentText"] as? String ?: "",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                ))
            }

            friendMessages = items
            android.util.Log.d("FRAGMENT_LIST", "🔄 Сообщения перезагружены: ${items.size} шт.")

        } catch (e: Exception) {
            android.util.Log.e("FRAGMENT_LIST", "❌ Ошибка перезагрузки сообщений: ${e.message}")
        }
    }

    private fun loadFriendMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null || !isAdded) return@launch
            val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch

            try {
                val messagesResult = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    queries = listOf(
                        Query.equal("recipientId", currentUserId),
                        Query.orderDesc("timestamp"),
                        Query.limit(50)
                    )
                )

                if (_binding == null || !isAdded) return@launch

                val items = mutableListOf<AppMessage>()
                messagesResult.documents.forEach { doc ->
                    val data = doc.data
                    items.add(AppMessage(
                        id = doc.id, messageId = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "Рыбак",
                        recipientId = data["recipientId"] as? String ?: "",
                        type = data["type"] as? String ?: "SYSTEM",
                        spotName = data["spotName"] as? String ?: "",
                        spotLat = data["spotLat"] as? Double ?: 0.0,
                        spotLon = data["spotLon"] as? Double ?: 0.0,
                        contentText = data["contentText"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                    ))
                }

                friendMessages = items
                updateCombinedList()

                // ✅ ТЕПЕРЬ ГРУЗИМ СИГНАЛЫ ТОЛЬКО КОГДА ЯКОРЯ УЖЕ НА МЕСТЕ
                signalsViewModel.loadSignals(currentUserId)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка загрузки друзей: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCombinedList() {
        android.util.Log.d("FRAGMENT_LIST", "🔄 Группировка: сообщений=${friendMessages.size}, сигналов=${fishingSignals.size}")

        val anchorsMap = mutableMapOf<String, MutableList<FishingSignal>>()
        val anchorIds = mutableSetOf<String>()

        friendMessages.forEach { msg ->
            if (msg.type == "ANCHOR_ON_FISHING") {
                anchorsMap[msg.id] = mutableListOf()
                anchorIds.add(msg.id)
                android.util.Log.d("FRAGMENT_LIST", "   🔑 Найден якорь ID: ${msg.id}")
            }
        }

        fishingSignals.forEach { signal ->
            if (signal.type == "fishing_wish" && signal.anchorId.isNotEmpty()) {
                android.util.Log.d("FRAGMENT_LIST", "   📨 Пожелание anchorId='${signal.anchorId}'")

                if (anchorsMap.containsKey(signal.anchorId)) {
                    anchorsMap[signal.anchorId]?.add(signal)
                    android.util.Log.d("FRAGMENT_LIST", "      ✅ Привязано к якорю!")
                } else {
                    android.util.Log.d("FRAGMENT_LIST", "      ❌ Якорь НЕ НАЙДЕН!")
                    android.util.Log.d("FRAGMENT_LIST", "      Доступные якоря: $anchorIds")

                    val similarAnchor = friendMessages.find {
                        it.id == signal.anchorId && it.type != "ANCHOR_ON_FISHING"
                    }
                    if (similarAnchor != null) {
                        android.util.Log.e("FRAGMENT_LIST", "      ⚠️ НАЙДЕН ДОКУМЕНТ С ТАКИМ ID, НО ТИП '${similarAnchor.type}'!")
                    }
                }
            }
        }

        val feedItems = mutableListOf<FeedItem>()

        friendMessages.filter { it.type != "ANCHOR_ON_FISHING" }.forEach { msg ->
            feedItems.add(FriendMessageItem(msg))
        }

        anchorIds.forEach { anchorId ->
            val anchorMsg = friendMessages.find { it.id == anchorId }
            if (anchorMsg != null) {
                val replies = anchorsMap[anchorId]?.sortedBy { it.createdAt } ?: emptyList()
                feedItems.add(AnchorGroupItem(anchorMsg, replies))
                android.util.Log.d("FRAGMENT_LIST", "   📦 Создана группа: якорь + ${replies.size} ответов")
            }
        }

        fishingSignals.filter { it.type == "on_fishing" }.forEach { sig ->
            feedItems.add(SignalItem(sig))
        }

        feedItems.sortByDescending { it.timestamp }

        adapter.submitList(feedItems)
        binding.layoutEmptyState.visibility = if (feedItems.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMessages.visibility = if (feedItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteMessage(messageId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AppwriteManager.databases.deleteDocument(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    documentId = messageId
                )
                loadFriendMessages()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}