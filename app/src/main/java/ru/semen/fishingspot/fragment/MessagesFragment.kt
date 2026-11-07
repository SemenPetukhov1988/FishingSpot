package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.appwrite.Query
import kotlinx.coroutines.launch
import ru.semen.fishingspot.adapter.MessagesAdapter
import ru.semen.fishingspot.data.AppMessage
import ru.semen.fishingspot.databinding.FragmentMessagesBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.FriendsViewModel
import ru.semen.fishingspot.viewmodel.SpotViewModel
import java.time.Instant

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"
    private val NOTIFICATIONS_COL_ID = "6aef1b7300251aa8ce5b" // ✅ ID КОЛЛЕКЦИИ УВЕДОМЛЕНИЙ
    private val DB_ID = "6ad5021a0029a2c88786"

    private val spotViewModel: SpotViewModel by viewModels()
    private val friendsViewModel: FriendsViewModel by viewModels()

    private lateinit var adapter: MessagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        loadMessagesAndSignals() // ✅ ОБНОВЛЕННОЕ НАЗВАНИЕ МЕТОДА

        binding.fabAddMessage.setOnClickListener {
            SignalTypeBottomSheet().show(childFragmentManager, "signal_type_sheet")
        }
    }

    private fun setupAdapter() {
        adapter = MessagesAdapter(
            onSenderClick = { message ->
                Toast.makeText(requireContext(), "Профиль: ${message.senderName}", Toast.LENGTH_SHORT).show()
            },
            onMessageClick = { message ->
                Toast.makeText(requireContext(), "Открываем: ${message.spotName}", Toast.LENGTH_SHORT).show()
            },
            onDeclineRequestClick = { message ->
                deleteMessage(message.id)
                Toast.makeText(requireContext(), "Запрос отклонен", Toast.LENGTH_SHORT).show()
            },
            onTakeSpotClick = { message ->
                spotViewModel.addFullSpot(
                    lat = message.spotLat,
                    lon = message.spotLon,
                    name = message.spotName.ifEmpty { "Без названия" },
                    description = "Получено от ${message.senderName}",
                    postText = "",
                    weight = message.spotWeight,
                    visibility = "private",
                    photoPath = null,
                    photoUrl = null,
                    fishType = null,
                    isVerified = false,
                    createdAt = message.spotCreatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    authorId = message.senderId,
                    authorName = message.senderName
                )
                deleteMessage(message.id)
                Toast.makeText(requireContext(), "Точка '${message.spotName}' сохранена!", Toast.LENGTH_LONG).show()
            },
            onAcceptFriendClick = { message ->
                friendsViewModel.acceptFriendRequest(
                    friendId = message.senderId,
                    messageId = message.id
                )
                deleteMessage(message.id)
            },
            onDeclineFriendClick = { message ->
                deleteMessage(message.id)
                Toast.makeText(requireContext(), "Запрос отклонен", Toast.LENGTH_SHORT).show()
            },
            onViewFriendSpotClick = { message ->
                SpotPreviewBottomSheet(
                    spotViewModel = spotViewModel,
                    senderName = message.senderName,
                    senderId = message.senderId,
                    spotName = message.spotName,
                    spotDesc = message.spotDescription,
                    spotLat = message.spotLat,
                    spotLon = message.spotLon,
                    spotWeight = message.spotWeight,
                    spotCreatedAt = message.spotCreatedAt,
                    messageId = message.id,
                    onDeclineAndDelete = { deleteMessage(message.id) }
                ).show(childFragmentManager, "spot_preview_sheet")
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter
    }

    // ✅ ОБЪЕДИНЕННАЯ ЗАГРУЗКА СООБЩЕНИЙ И СИГНАЛОВ
    private fun loadMessagesAndSignals() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null || !isAdded) return@launch

            val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch

            try {
                // 1. Загружаем обычные сообщения
                val messagesResult = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    queries = listOf(
                        Query.equal("recipientId", currentUserId),
                        Query.orderDesc("timestamp"),
                        Query.limit(50)
                    )
                )

                // 2. ✅ Загружаем сигналы "Я на рыбалке"
                val signalsResult = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = NOTIFICATIONS_COL_ID,
                    queries = listOf(
                        Query.equal("receiverId", currentUserId),
                        Query.orderDesc("createdAt"),
                        Query.limit(50)
                    )
                )

                if (_binding == null || !isAdded) return@launch

                val allItems = mutableListOf<AppMessage>()

                // Маппим сообщения
                messagesResult.documents.forEach { doc ->
                    val data = doc.data
                    allItems.add(AppMessage(
                        id = doc.id,
                        messageId = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "Рыбак",
                        recipientId = data["recipientId"] as? String ?: "",
                        type = data["type"] as? String ?: "SYSTEM",
                        spotId = data["spotId"] as? String ?: "",
                        spotName = data["spotName"] as? String ?: "",
                        spotLat = data["spotLat"] as? Double ?: 0.0,
                        spotLon = data["spotLon"] as? Double ?: 0.0,
                        contentText = data["contentText"] as? String ?: "",
                        customMessage = data["customMessage"] as? String ?: "",
                        spotDescription = data["spotDescription"] as? String ?: "",
                        spotWeight = data["spotWeight"] as? Double ?: 0.0,
                        spotCreatedAt = (data["spotCreatedAt"] as? Number)?.toLong() ?: 0L,
                        spotIsVerified = data["spotIsVerified"] as? Boolean ?: false,
                        spotPhotoPath = data["spotPhotoPath"] as? String,
                        isRead = data["isRead"] as? Boolean ?: false,
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                    ))
                }

                // ✅ Маппим сигналы
                signalsResult.documents.forEach { doc ->
                    val data = doc.data
                    allItems.add(AppMessage(
                        id = doc.id,
                        messageId = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "Рыбак",
                        recipientId = data["receiverId"] as? String ?: "",
                        type = "on_fishing", // Явно задаем тип
                        spotLat = (data["lat"] as? Number)?.toDouble() ?: 0.0,
                        spotLon = (data["lon"] as? Number)?.toDouble() ?: 0.0,
                        timestamp = try {
                            Instant.parse(data["createdAt"] as? String ?: "").toEpochMilli()
                        } catch (e: Exception) { System.currentTimeMillis() }
                    ))
                }

                // Сортируем всё вместе по времени (новые сверху)
                allItems.sortByDescending { it.timestamp }

                if (_binding == null || !isAdded) return@launch

                adapter.submitList(allItems)

                if (allItems.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvMessages.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvMessages.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                if (_binding != null && isAdded) {
                    Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AppwriteManager.databases.deleteDocument(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    documentId = messageId
                )
                loadMessagesAndSignals()
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