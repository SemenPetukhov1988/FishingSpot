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

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    // ⚠️ ЗАМЕНИ НА СВОЙ ID КОЛЛЕКЦИИ MESSAGES ИЗ APPWRITE CONSOLE
    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"
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
        loadMessages()

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
            // ✅ УБРАН onShareRequestClick, так как sendSharedSpot удален из ViewModel
            // Если эта фича нужна позже - вернем через отдельный метод
            onDeclineRequestClick = { message ->
                deleteMessage(message.id)
                Toast.makeText(requireContext(), "Запрос отклонен", Toast.LENGTH_SHORT).show()
            },
            onTakeSpotClick = { message ->
                spotViewModel.addFullSpot(
                    lat = message.spotLat ?: 0.0,
                    lon = message.spotLon ?: 0.0,
                    name = message.spotName ?: "Без названия",
                    description = "Получено от ${message.senderName}",
                    weight = message.spotWeight ?: 0.0,
                    visibility = "private",
                    photoPath = null,
                    photoUrl = null,
                    fishType = null,
                    isVerified = false,
                    createdAt = message.spotCreatedAt ?: System.currentTimeMillis(),
                    authorId = message.senderId,
                    authorName = message.senderName
                )
                deleteMessage(message.id)
                Toast.makeText(requireContext(), "Точка '${message.spotName}' сохранена!", Toast.LENGTH_LONG).show()
            },
            onAcceptFriendClick = { message ->
                // ✅ ИСПРАВЛЕНО: Передаем только 2 параметра, как требует новый ViewModel
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
                    senderName = message.senderName ?: "",
                    senderId = message.senderId,
                    spotName = message.spotName ?: "",
                    spotDesc = message.spotDescription ?: "",
                    spotLat = message.spotLat ?: 0.0,
                    spotLon = message.spotLon ?: 0.0,
                    spotWeight = message.spotWeight ?: 0.0,
                    spotCreatedAt = message.spotCreatedAt,
                    messageId = message.id,
                    onDeclineAndDelete = { deleteMessage(message.id) }
                ).show(childFragmentManager, "spot_preview_sheet")
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter
    }

    private fun loadMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            // ✅ ПРОВЕРКА 1: Если фрагмент уже уничтожен или не добавлен — выходим сразу
            if (_binding == null || !isAdded) return@launch

            val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch

            try {
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    queries = listOf(
                        Query.equal("recipientId", currentUserId),
                        Query.orderDesc("timestamp"),
                        Query.limit(50)
                    )
                )

                // ✅ ПРОВЕРКА 2: После сетевого запроса снова проверяем состояние фрагмента
                if (_binding == null || !isAdded) return@launch

                val messages = result.documents.mapNotNull { doc ->
                    val data = doc.data
                    AppMessage(
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
                    )
                }

                // ✅ ПРОВЕРКА 3: Перед обновлением UI убеждаемся, что binding жив
                if (_binding == null || !isAdded) return@launch

                adapter.submitList(messages)

                if (messages.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvMessages.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvMessages.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                // ✅ БЕЗОПАСНЫЙ ТОСТ: только если фрагмент всё ещё активен
                if (_binding != null && isAdded) {
                    Toast.makeText(requireContext(), "Ошибка загрузки сообщений: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Перезагружаем список после удаления
                loadMessages()
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