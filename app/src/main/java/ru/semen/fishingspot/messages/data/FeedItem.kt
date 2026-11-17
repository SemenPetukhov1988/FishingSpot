package ru.semen.fishingspot.messages.data

import ru.semen.fishingspot.messages.friends.data.AppMessage

sealed interface FeedItem {
    val timestamp: Long
}

data class FriendMessageItem(val message: AppMessage) : FeedItem {
    override val timestamp = message.timestamp
}

/**
 * Группа: Якорь "Я на рыбалке" (AppMessage) + ответы
 */
data class AnchorGroupItem(
    val anchorMessage: AppMessage,
    val replies: List<FishingSignal> = emptyList(),
    override val timestamp: Long = anchorMessage.timestamp
) : FeedItem

/**
 * ✅ СПЕЦИАЛЬНЫЙ ЭЛЕМЕНТ ДЛЯ ЯКОРЯ "СТОИТ ЛИ ЕХАТЬ?"
 * Исправлено: убрано дублирование timestamp
 */
data class GoingFishingAnchorItem(
    val id: String,
    val contentText: String,
    val spotLat: Double,
    val spotLon: Double,
    val plannedDate: Long,
    override val timestamp: Long, // ✅ ИСПОЛЬЗУЕМ OVERRIDE ПРЯМО В КОНСТРУКТОРЕ
    val replies: List<FishingSignal> = emptyList()
) : FeedItem

data class SignalItem(val signal: FishingSignal) : FeedItem {
    override val timestamp = signal.createdAt
}