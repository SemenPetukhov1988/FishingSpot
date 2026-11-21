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

// ✅ ШАГ 1: ТИПЫ ВКЛАДОК
enum class TabType { INCOMING, OUTGOING }

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationsRepo = NotificationsRepository(application.applicationContext)
    private val friendsActionsRepo = FriendsActionsRepository(application.applicationContext)
    private val signalsRepo = FishingSignalRepository()

    private val _messagesFlow = MutableStateFlow<List<AppMessage>>(emptyList())
    private val _signalsFlow = MutableStateFlow<List<FishingSignal>>(emptyList())
    private val _signalsError = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    // ✅ ШАГ 1: ТЕКУЩАЯ АКТИВНАЯ ВКЛАДКА
    private val _currentTab = MutableStateFlow<TabType>(TabType.INCOMING)
    val currentTab: StateFlow<TabType> = _currentTab.asStateFlow()

    private val _acceptedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _declinedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _wishedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _swipedIds = MutableStateFlow<Set<String>>(emptySet())

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private data class ActionFlags(
        val accepted: Set<String> = emptySet(),
        val declined: Set<String> = emptySet(),
        val wished: Set<String> = emptySet(),
        val swiped: Set<String> = emptySet()
    )

    private val flagsFlow = combine(_acceptedIds, _declinedIds, _wishedIds, _swipedIds) { a, d, w, s ->
        ActionFlags(a, d, w, s)
    }

    // ✅ ШАГ 1: ДОБАВЛЯЕМ _currentTab В КОМБАЙН И ФИЛЬТРУЕМ ДАННЫЕ
    val uiState: StateFlow<MessagesUiState> = combine(
        _messagesFlow, _signalsFlow, _signalsError, flagsFlow, _currentTab
    ) { messages, signals, sigError, flags, tab ->

        // Базовая логика формирования ленты (без изменений)
        val filteredMessages = messages.filter {
            it.id !in flags.accepted && it.id !in flags.declined && it.id !in flags.swiped
        }
        val visibleSignals = signals.filterNot {
            it.type == "on_fishing" && it.id in flags.wished
        }
        val allFeedItems = buildFeedList(filteredMessages, visibleSignals)

        // ✅ ШАГ 1: ФИЛЬТРАЦИЯ ПО ВКЛАДКЕ
        val feedItems = when (tab) {
            TabType.INCOMING -> allFeedItems.filter {
                it is FriendMessageItem ||
                        it is AnchorGroupItem ||
                        it is GoingFishingAnchorItem
            }
            TabType.OUTGOING -> allFeedItems.filter {
                it is SignalItem
            }
        }

        if (feedItems.isEmpty() && sigError != null) MessagesUiState.Error(sigError)
        else MessagesUiState.Success(feedItems, sigError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MessagesUiState.Loading)

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val userId = UserSessionManager.getCurrentUserId(getApplication())
                if (userId.isNullOrEmpty()) {
                    Log.e("MESSAGES_VM", "User ID пустой")
                    _signalsError.value = "Не удалось определить пользователя"
                    return@launch
                }

                val msgResult = notificationsRepo.getIncomingMessages()
                val sigResult = signalsRepo.getSignals(userId)

                if (msgResult.isSuccess) {
                    val freshMessages = msgResult.getOrNull() ?: emptyList()
                    _messagesFlow.value = freshMessages
                    val freshMsgIds = freshMessages.map { it.id }.toSet()
                    _acceptedIds.value = _acceptedIds.value intersect freshMsgIds
                    _declinedIds.value = _declinedIds.value intersect freshMsgIds
                    _swipedIds.value = _swipedIds.value intersect freshMsgIds
                }

                if (sigResult.isSuccess) {
                    val freshSignals = sigResult.getOrNull()?.sortedByDescending { it.createdAt } ?: emptyList()
                    _signalsFlow.value = freshSignals
                    val freshSigIds = freshSignals.map { it.id }.toSet()
                    _wishedIds.value = _wishedIds.value intersect freshSigIds
                    _signalsError.value = null
                } else {
                    _signalsError.value = sigResult.exceptionOrNull()?.message ?: "Ошибка загрузки сигналов"
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // Метод для переключения вкладок из фрагмента
    fun switchTab(tab: TabType) {
        _currentTab.value = tab
    }

    private fun buildFeedList(messages: List<AppMessage>, signals: List<FishingSignal>): List<FeedItem> {
        val anchorsMap = mutableMapOf<String, MutableList<FishingSignal>>()
        val anchorIds = mutableSetOf<String>()

        messages.forEach { msg ->
            if (msg.type == "ANCHOR_ON_FISHING" || msg.type == "ANCHOR_GOING_FISHING" || msg.type == "ANCHOR_SPOT_REQUEST") {
                anchorsMap[msg.id] = mutableListOf()
                anchorIds.add(msg.id)
            }
        }

        signals.forEach { signal ->
            // ✅ ДОБАВЛЕН spot_recommendation В УСЛОВИЕ ГРУППИРОВКИ ОТВЕТОВ
            if ((signal.type == "fishing_wish" ||
                        signal.type == "fishing_reply" ||
                        signal.type == "spot_recommendation") && signal.anchorId.isNotEmpty()) {
                anchorsMap[signal.anchorId]?.add(signal)
            }
        }

        val feedItems = mutableListOf<FeedItem>()

        messages.filter {
            it.type != "ANCHOR_ON_FISHING" &&
                    it.type != "ANCHOR_GOING_FISHING" &&
                    it.type != "ANCHOR_SPOT_REQUEST"
        }.forEach { msg ->
            feedItems.add(FriendMessageItem(msg))
        }

        anchorIds.forEach { anchorId ->
            val anchorMsg = messages.find { it.id == anchorId } ?: return@forEach
            val replies = anchorsMap[anchorId]?.sortedBy { it.createdAt } ?: emptyList()

            when (anchorMsg.type) {
                "ANCHOR_GOING_FISHING" -> {
                    feedItems.add(GoingFishingAnchorItem(
                        id = anchorMsg.id,
                        contentText = anchorMsg.contentText,
                        spotLat = anchorMsg.spotLat,
                        spotLon = anchorMsg.spotLon,
                        plannedDate = anchorMsg.plannedDate,
                        timestamp = anchorMsg.timestamp,
                        replies = replies
                    ))
                }
                "ANCHOR_SPOT_REQUEST" -> {
                    feedItems.add(AnchorGroupItem(anchorMsg, replies))
                }
                else -> {
                    feedItems.add(AnchorGroupItem(anchorMsg, replies))
                }
            }
        }

        signals.filter {
            it.type == "on_fishing" ||
                    it.type == "going_fishing" ||
                    it.type == "request_spot"
        }.forEach { sig ->
            feedItems.add(SignalItem(sig))
        }

        return feedItems.sortedByDescending { it.timestamp }
    }

    fun swipeToDelete(item: FeedItem) {
        val id = when (item) {
            is FriendMessageItem -> item.message.id
            is AnchorGroupItem -> item.anchorMessage.id
            is GoingFishingAnchorItem -> item.id
            is SignalItem -> item.signal.id
            else -> null
        } ?: return

        viewModelScope.launch {
            val result = when (item) {
                is SignalItem -> signalsRepo.deleteSignal(id)
                else -> notificationsRepo.deleteMessage(id)
            }

            result.onFailure { loadData() }
        }
    }

    fun acceptFriendRequest(senderId: String, messageId: String) {
        _acceptedIds.value = _acceptedIds.value + messageId
        viewModelScope.launch {
            friendsActionsRepo.acceptFriendRequest(senderId, messageId)
                .onFailure { _acceptedIds.value = _acceptedIds.value - messageId }
        }
    }

    fun declineRequest(messageId: String) {
        _declinedIds.value = _declinedIds.value + messageId
        viewModelScope.launch {
            friendsActionsRepo.declineFriendRequest(messageId)
                .onFailure { _declinedIds.value = _declinedIds.value - messageId }
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
                    .onFailure { _wishedIds.value = _wishedIds.value - targetSignal.id }
            }
            signalsRepo.sendSignal(
                senderId = senderId, senderName = senderName, receiverId = receiverId,
                type = "fishing_wish", anchorId = anchorId, messageText = "Хорошей рыбалки! 🎣"
            )
        }
    }

    // ✅ НОВЫЙ МЕТОД ДЛЯ УДАЛЕНИЯ ИЗ ИСТОЧНИКА ДАННЫХ
    fun removeMessageFromSource(messageId: String) {
        _messagesFlow.value = _messagesFlow.value.filterNot { it.id == messageId }
    }

    fun removeSignalFromSource(signalId: String) {
        _signalsFlow.value = _signalsFlow.value.filterNot { it.id == signalId }
    }
    fun forceRefreshFeed() {
        loadData()
    }

    fun refreshData() { loadData() }
}