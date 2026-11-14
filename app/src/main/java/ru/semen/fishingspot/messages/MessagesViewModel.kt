package ru.semen.fishingspot.feature_messages.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import ru.semen.fishingspot.feature_messages.notifications.repository.NotificationsRepository
import ru.semen.fishingspot.messages.data.*
import ru.semen.fishingspot.messages.friends.data.AppMessage
import ru.semen.fishingspot.messages.friends.repository.FriendsActionsRepository
import ru.semen.fishingspot.messages.notifikation.FishingSignalRepository
import ru.semen.fishingspot.utils.UserSessionManager

sealed class MessagesUiState {
    object Loading : MessagesUiState()
    data class Success(val feedItems: List<FeedItem>, val signalsError: String? = null) : MessagesUiState()
    data class Error(val message: String) : MessagesUiState()
}

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationsRepo = NotificationsRepository(application.applicationContext)
    private val friendsActionsRepo = FriendsActionsRepository(application.applicationContext)
    private val signalsRepo = FishingSignalRepository()

    private val _messagesFlow = MutableStateFlow<List<AppMessage>>(emptyList())
    private val _signalsFlow = MutableStateFlow<List<FishingSignal>>(emptyList())
    private val _signalsError = MutableStateFlow<String?>(null)

    private val _acceptedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _declinedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _wishedIds = MutableStateFlow<Set<String>>(emptySet())

    // ✅ ОТДЕЛЬНЫЙ ПОТОК ДЛЯ СПИННЕРА — не зависит от эмиссий данных
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private data class ActionFlags(
        val accepted: Set<String> = emptySet(),
        val declined: Set<String> = emptySet(),
        val wished: Set<String> = emptySet()
    )

    private val flagsFlow = combine(_acceptedIds, _declinedIds, _wishedIds) { a, d, w ->
        ActionFlags(a, d, w)
    }

    val uiState: StateFlow<MessagesUiState> = combine(
        _messagesFlow,
        _signalsFlow,
        _signalsError,
        flagsFlow
    ) { messages: List<AppMessage>,
        signals: List<FishingSignal>,
        sigError: String?,
        flags: ActionFlags ->

        val filteredMessages = messages.filter { it.id !in flags.accepted && it.id !in flags.declined }
        val visibleSignals = signals.filterNot { it.type == "on_fishing" && it.id in flags.wished }

        val feedItems = buildFeedList(filteredMessages, visibleSignals)

        if (feedItems.isEmpty() && sigError != null) {
            MessagesUiState.Error(sigError)
        } else {
            MessagesUiState.Success(feedItems, sigError)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MessagesUiState.Loading)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val userId = UserSessionManager.getCurrentUserId(getApplication())
                if (userId.isNullOrEmpty()) {
                    Log.e("MESSAGES_VM", "User ID пустой, загрузка невозможна")
                    _signalsError.value = "Не удалось определить пользователя"
                    return@launch
                }

                // Загружаем сообщения
                val msgResult = notificationsRepo.getIncomingMessages()
                if (msgResult.isSuccess) {
                    _messagesFlow.value = msgResult.getOrNull() ?: emptyList()

                    // ✅ ФЛАГИ ОБРЕЗАЕМ ПОСЛЕ ПРИХОДА СВЕЖИХ ДАННЫХ:
                    // остаём флаг только для id, которые ещё существуют на сервере.
                    // Удалённые документы исчезают из списков — их флаги падают сами.
                    val freshMessageIds = _messagesFlow.value.map { it.id }.toSet()
                    _acceptedIds.value = _acceptedIds.value intersect freshMessageIds
                    _declinedIds.value = _declinedIds.value intersect freshMessageIds
                } else {
                    Log.e("MESSAGES_VM", "Ошибка загрузки сообщений: ${msgResult.exceptionOrNull()?.message}")
                }

                // Загружаем сигналы
                val sigResult = signalsRepo.getSignals(userId)
                if (sigResult.isSuccess) {
                    _signalsFlow.value = sigResult.getOrNull()?.sortedByDescending { it.createdAt } ?: emptyList()

                    // ✅ Аналогично: wished-флаг живёт, только пока сигнал существует
                    val freshSignalIds = _signalsFlow.value.map { it.id }.toSet()
                    _wishedIds.value = _wishedIds.value intersect freshSignalIds
                    _signalsError.value = null
                } else {
                    _signalsError.value = sigResult.exceptionOrNull()?.message ?: "Ошибка загрузки сигналов"
                    Log.e("MESSAGES_VM", "Ошибка загрузки сигналов: ${_signalsError.value}")
                }
            } finally {
                // ✅ СПИННЕР ГАСИТСЯ ВСЕГДА — даже при ошибке и пустом userId
                _isRefreshing.value = false
            }
        }
    }

    private fun buildFeedList(
        messages: List<AppMessage>,
        signals: List<FishingSignal>
    ): List<FeedItem> {
        val anchorsMap = mutableMapOf<String, MutableList<FishingSignal>>()
        val anchorIds = mutableSetOf<String>()

        messages.forEach { msg ->
            if (msg.type == "ANCHOR_ON_FISHING") {
                anchorsMap[msg.id] = mutableListOf()
                anchorIds.add(msg.id)
            }
        }

        signals.forEach { signal ->
            if (signal.type == "fishing_wish" && signal.anchorId.isNotEmpty()) {
                anchorsMap[signal.anchorId]?.add(signal)
            }
        }

        val feedItems = mutableListOf<FeedItem>()

        messages.filter { it.type != "ANCHOR_ON_FISHING" }.forEach { msg ->
            feedItems.add(FriendMessageItem(msg))
        }

        anchorIds.forEach { anchorId ->
            val anchorMsg = messages.find { it.id == anchorId }
            if (anchorMsg != null) {
                val replies = anchorsMap[anchorId]?.sortedBy { it.createdAt } ?: emptyList()
                feedItems.add(AnchorGroupItem(anchorMsg, replies))
            }
        }

        signals.filter { it.type == "on_fishing" }.forEach { sig ->
            feedItems.add(SignalItem(sig))
        }

        return feedItems.sortedByDescending { it.timestamp }
    }

    fun acceptFriendRequest(senderId: String, messageId: String) {
        _acceptedIds.value = _acceptedIds.value + messageId

        viewModelScope.launch {
            friendsActionsRepo.acceptFriendRequest(senderId, messageId)
                .onFailure { e ->
                    Log.e("MESSAGES_VM", "Ошибка принятия заявки $messageId: ${e.message}")
                    _acceptedIds.value = _acceptedIds.value - messageId
                }
        }
    }

    fun declineRequest(messageId: String) {
        _declinedIds.value = _declinedIds.value + messageId

        viewModelScope.launch {
            friendsActionsRepo.declineFriendRequest(messageId)
                .onFailure { e ->
                    Log.e("MESSAGES_VM", "Ошибка отклонения заявки $messageId: ${e.message}")
                    _declinedIds.value = _declinedIds.value - messageId
                }
        }
    }

    fun sendWish(anchorId: String, receiverId: String, senderId: String, senderName: String) {
        viewModelScope.launch {
            val targetSignal = _signalsFlow.value.firstOrNull {
                it.type == "on_fishing" && it.anchorId == anchorId
            }

            if (targetSignal != null) {
                _wishedIds.value = _wishedIds.value + targetSignal.id

                notificationsRepo.deleteSignal(targetSignal.id)
                    .onFailure { e ->
                        Log.e("MESSAGES_VM", "Не удалось удалить сигнал ${targetSignal.id}: ${e.message}")
                        _wishedIds.value = _wishedIds.value - targetSignal.id
                    }
            }

            signalsRepo.sendSignal(
                senderId = senderId,
                senderName = senderName,
                receiverId = receiverId,
                type = "fishing_wish",
                anchorId = anchorId,
                messageText = "Хорошей рыбалки! 🎣"
            ).onFailure { e ->
                Log.e("MESSAGES_VM", "Ошибка отправки пожелания: ${e.message}")
            }
        }
    }

    fun refreshData() {
        loadData()
    }
}
