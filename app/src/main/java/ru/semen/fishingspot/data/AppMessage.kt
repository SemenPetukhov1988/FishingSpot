package ru.semen.fishingspot.data

data class AppMessage(
    val id: String = "",
    val messageId: String = "",
    val type: String = "SYSTEM",

    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",

    // Данные точки (для SPOT_SHARED, FRIEND_SPOT, ON_FISHING)
    val spotId: String = "",
    val spotName: String = "",
    val spotLat: Double = 0.0,
    val spotLon: Double = 0.0,
    val spotDescription: String = "",
    val spotWeight: Double = 0.0,
    val spotCreatedAt: Long = 0L,
    val spotIsVerified: Boolean = false,
    val spotPhotoPath: String? = null,

    // Текстовое содержимое
    val contentText: String = "",
    val customMessage: String = "",

    val isRead: Boolean = false,
    val timestamp: Long = 0L
)

enum class MessageType(val value: String) {
    NEW_REVIEW("NEW_REVIEW"),
    SPOT_REQUEST("SPOT_REQUEST"),
    SPOT_SHARED("SPOT_SHARED"),
    NEW_FRIEND("NEW_FRIEND"),
    SYSTEM("SYSTEM"),
    FRIEND_SPOT("FRIEND_SPOT"),
    ON_FISHING("on_fishing"); // ✅ ДОБАВЛЕН ТИП ДЛЯ СИГНАЛА

    companion object {
        fun fromString(value: String): MessageType {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}