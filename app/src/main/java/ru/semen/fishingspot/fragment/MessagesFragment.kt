package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import ru.semen.fishingspot.adapter.MessagesAdapter
import ru.semen.fishingspot.data.AppMessage
import ru.semen.fishingspot.databinding.FragmentMessagesBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.FriendsViewModel
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: MessagesAdapter
    private var messagesListener: ListenerRegistration? = null

    private val spotViewModel: SpotViewModel by viewModels()
    private val friendsViewModel: FriendsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MessagesAdapter(
            onSenderClick = { message ->
                Toast.makeText(requireContext(), "Профиль: ${message.senderName}", Toast.LENGTH_SHORT).show()
            },
            onMessageClick = { message ->
                Toast.makeText(requireContext(), "Открываем: ${message.spotName}", Toast.LENGTH_SHORT).show()
            },
            onShareRequestClick = { message ->
                val myUserId = UserSessionManager.getCurrentUserId(requireContext())
                if (myUserId == null) {
                    Toast.makeText(requireContext(), "Ошибка: не авторизован", Toast.LENGTH_SHORT).show()
                    return@MessagesAdapter
                }
                SelectSpotBottomSheet(
                    spotViewModel = spotViewModel,
                    requesterName = message.senderName,
                    myUserId = myUserId
                ) { selectedSpot ->
                    friendsViewModel.sendSharedSpot(
                        requesterId = message.senderId,
                        requesterName = message.senderName,
                        spot = selectedSpot,
                        originalMessageId = message.id
                    )
                }.show(childFragmentManager, "select_spot_sheet")
            },
            onDeclineRequestClick = { message ->
                Toast.makeText(requireContext(), "Запрос отклонен", Toast.LENGTH_SHORT).show()
                deleteMessage(message.id)
            },
            onTakeSpotClick = { message ->
                // ✅ ИСПРАВЛЕНО: Добавлены параметры photoUrl и authorName для соответствия новой сигнатуре
                spotViewModel.addFullSpot(
                    lat = message.spotLat,
                    lon = message.spotLon,
                    name = message.spotName,
                    description = "Получено от ${message.senderName}",
                    weight = 0.0,
                    visibility = "private",
                    photoPath = null,
                    photoUrl = null,          // ✅ ДОБАВЛЕНО
                    fishType = null,
                    isVerified = false,
                    createdAt = message.spotCreatedAt ?: System.currentTimeMillis(),
                    authorId = message.senderId,
                    authorName = message.senderName // ✅ ДОБАВЛЕНО для корректного отображения
                )
                Toast.makeText(requireContext(), "Точка '${message.spotName}' сохранена в дневник!", Toast.LENGTH_LONG).show()
                deleteMessage(message.id)
            },
            onAcceptFriendClick = { message ->
                friendsViewModel.acceptFriendRequest(
                    friendId = message.senderId,
                    friendName = message.senderName,
                    messageId = message.id
                )
            },
            onDeclineFriendClick = { message ->
                Toast.makeText(requireContext(), "Запрос отклонен", Toast.LENGTH_SHORT).show()
                deleteMessage(message.id)
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
                    onDeclineAndDelete = {
                        deleteMessage(message.id)
                    }
                ).show(childFragmentManager, "spot_preview_sheet")
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter

        subscribeToMessages()
    }

    private fun subscribeToMessages() {
        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return
        android.util.Log.d("DEBUG_SPOT", "👂 Подписка на сообщения для: $currentUserId")

        messagesListener = firestore.collection("messages")
            .whereEqualTo("recipientId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DEBUG_SPOT", "❌ Ошибка подписки", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AppMessage::class.java)?.copy(id = doc.id)
                    }
                    val sortedMessages = messages.sortedByDescending { it.timestamp }

                    adapter.submitList(sortedMessages)

                    if (sortedMessages.isEmpty()) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.rvMessages.visibility = View.GONE
                    } else {
                        binding.layoutEmptyState.visibility = View.GONE
                        binding.rvMessages.visibility = View.VISIBLE
                    }
                }
            }
    }

    private fun deleteMessage(messageId: String) {
        firestore.collection("messages").document(messageId)
            .delete()
            .addOnSuccessListener {
                android.util.Log.d("DEBUG_SPOT", "✅ Сообщение удалено")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DEBUG_SPOT", "❌ Ошибка удаления", e)
            }
    }

    override fun onDestroyView() {
        messagesListener?.remove()
        messagesListener = null
        _binding = null
        super.onDestroyView()
    }
}