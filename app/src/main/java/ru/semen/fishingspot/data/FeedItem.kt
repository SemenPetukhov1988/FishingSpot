package ru.semen.fishingspot.data

sealed interface FeedItem {
    val timestamp: Long
}

data class FriendMessageItem(val message: AppMessage) : FeedItem {
    override val timestamp = message.timestamp
}

/**
 * Группа: Якорь (сообщение отправителя) + его ответы (пожелания)
 */
data class AnchorGroupItem(
    val anchorMessage: AppMessage,        // Сообщение "Вы отправили..."
    val replies: List<FishingSignal> = emptyList(), // Пожелания fishing_wish
    override val timestamp: Long = anchorMessage.timestamp
) : FeedItem

/**
 * Обычный сигнал on_fishing для получателя (без ответов внутри)
 */
data class SignalItem(val signal: FishingSignal) : FeedItem {
    override val timestamp = signal.createdAt
}