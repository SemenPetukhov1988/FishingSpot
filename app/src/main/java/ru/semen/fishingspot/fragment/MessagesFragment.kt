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

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: MessagesAdapter
    private var messagesListener: ListenerRegistration? = null

    // ✅ Подключаем обе ViewModel
    private val spotViewModel: ru.semen.fishingspot.viewmodel.SpotViewModel by viewModels()
    private val friendsViewModel: ru.semen.fishingspot.viewmodel.FriendsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Настраиваем адаптер со всеми типами кликов
        adapter = MessagesAdapter(
            onSenderClick = { message ->
                Toast.makeText(
                    requireContext(),
                    "Переход в профиль пользователя: ${message.senderName}",
                    Toast.LENGTH_SHORT
                ).show()
                // TODO: findNavController().navigate(MessagesFragmentDirections.actionMessagesToProfile(message.senderId))
            },
            onMessageClick = { message ->
                if (message.type == "SPOT_SHARED") {
                    Toast.makeText(requireContext(), "Забираем точку: ${message.spotName}", Toast.LENGTH_SHORT).show()
                    // TODO: Логика сохранения точки в Room (будет на следующем большом этапе)
                } else {
                    Toast.makeText(requireContext(), "Открываем точку: ${message.spotName}", Toast.LENGTH_SHORT).show()
                }
            },
            // ✅ Клик по кнопке "📍 Поделиться" в запросе
            // ✅ НОВОЕ: Клик по кнопке "📍 Поделиться" в запросе
            // ✅ НОВОЕ: Клик по кнопке "📍 Поделиться" в запросе
            onShareRequestClick = { message ->
                // Открываем шторку, передавая ей SpotViewModel
                SelectSpotBottomSheet(
                    spotViewModel = spotViewModel,
                    requesterName = message.senderName
                ) { selectedSpot ->
                    // ЭТОТ КОД СРАБОТАЕТ, КОГДА АНЯ НАЖМЕТ "ОТПРАВИТЬ"

                    // Вызываем метод отправки из FriendsViewModel
                    friendsViewModel.sendSharedSpot(
                        requesterId = message.senderId,
                        requesterName = message.senderName,
                        spot = selectedSpot,
                        originalMessageId = message.id
                    )
                }.show(childFragmentManager, "select_spot_sheet")
            },
            // ✅ Клик по кнопке "Отклонить" в запросе
            onDeclineRequestClick = { message ->
                Toast.makeText(requireContext(), "Запрос от ${message.senderName} отклонен", Toast.LENGTH_SHORT).show()
                deleteMessage(message.id) // Реально удаляем запрос из базы
            },
            // ✅ ФИНАЛЬНЫЙ ШАГ: Клик "Забрать в мой дневник"
            // ✅ ФИНАЛЬНЫЙ ШАГ: Клик "Забрать в мой дневник"
            onTakeSpotClick = { message ->
                // 1. Сохраняем точку в локальную базу Room
                spotViewModel.addFullSpot(
                    lat = message.spotLat,
                    lon = message.spotLon,
                    name = message.spotName,
                    description = "Получено от ${message.senderName}",
                    weight = 0.0,
                    isPublic = false, // Сохраняем в ЛИЧНЫЙ дневник
                    photoPath = null,
                    isVerified = false
                )

                // 2. Показываем успешное уведомление
                Toast.makeText(
                    requireContext(),
                    "Точка '${message.spotName}' успешно сохранена в дневник! 🎣",
                    Toast.LENGTH_LONG
                ).show()

                // 3. ✅ УДАЛЯЕМ сообщение из Firebase, чтобы карточка исчезла из списка
                deleteMessage(message.id)
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
        android.util.Log.d("DEBUG_SPOT", " Подписка на сообщения для пользователя: $currentUserId")

        messagesListener = firestore.collection("messages")
            .whereEqualTo("recipientId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DEBUG_SPOT", "❌ Ошибка подписки на сообщения", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AppMessage::class.java)?.copy(id = doc.id)
                    }

                    // ✅ Сортировка для Long (timestamp теперь Long, а не Timestamp)
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

    /**
     * Удаляет сообщение из Firebase (используется при отклонении запроса)
     */
    private fun deleteMessage(messageId: String) {
        firestore.collection("messages").document(messageId)
            .delete()
            .addOnSuccessListener {
                android.util.Log.d("DEBUG_SPOT", "✅ Сообщение удалено из Firebase")
                // Список обновится автоматически благодаря addSnapshotListener!
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DEBUG_SPOT", "❌ Ошибка удаления сообщения", e)
                Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
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