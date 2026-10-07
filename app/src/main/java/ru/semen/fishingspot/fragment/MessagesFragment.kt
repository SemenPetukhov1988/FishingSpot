package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

import ru.semen.fishingspot.adapter.MessagesAdapter
import ru.semen.fishingspot.data.AppMessage
import ru.semen.fishingspot.databinding.FragmentMessagesBinding
import ru.semen.fishingspot.utils.UserSessionManager

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: MessagesAdapter
    private var messagesListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Настраиваем адаптер с простой и понятной логикой кликов
        adapter = MessagesAdapter(
            onSenderClick = { message ->
                // ✅ ВРЕМЕННАЯ ЗАГЛУШКА (Пока не настроен nav_graph для профиля)
                // Когда добавим ProfileFragment, здесь будет:
                // findNavController().navigate(MessagesFragmentDirections.actionMessagesToProfile(message.senderId))
                Toast.makeText(
                    requireContext(),
                    "Переход в профиль пользователя: ${message.senderName}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onMessageClick = { message ->
                // ✅ ПРОСТОЙ ОТКЛИК
                Toast.makeText(
                    requireContext(),
                    "Открываем точку: ${message.spotName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter

        // 2. Подписываемся на сообщения
        subscribeToMessages()
    }

    /**
     * Подписка на коллекцию сообщений в реальном времени
     */
    private fun subscribeToMessages() {
        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return
        android.util.Log.d("DEBUG_SPOT", "👂 Подписка на сообщения для пользователя: $currentUserId")

        messagesListener = firestore.collection("messages")
            .whereEqualTo("recipientId", currentUserId)
            // ✅ УБРАЛИ orderBy — он требует индекс в Firebase Console
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DEBUG_SPOT", "❌ Ошибка подписки на сообщения", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AppMessage::class.java)?.copy(id = doc.id)
                    }

                    // ✅ Сортируем на клиенте (от новых к старым)
                    val sortedMessages = messages.sortedByDescending { it.timestamp }

                    android.util.Log.d("DEBUG_SPOT", "📬 Получено сообщений из Firebase: ${sortedMessages.size}")
                    android.util.Log.d("DEBUG_SPOT", "📋 Список: $sortedMessages")

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

    override fun onDestroyView() {
        // Чистая уборка: отписка от Firebase
        messagesListener?.remove()
        messagesListener = null
        _binding = null
        super.onDestroyView()
    }
}