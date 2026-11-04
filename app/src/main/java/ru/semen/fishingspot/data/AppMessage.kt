package ru.semen.fishingspot.data

/**
 * Модель сообщения для ленты уведомлений и запросов.
 * Используется как для Firebase (legacy), так и для Appwrite.
 */
data class AppMessage(
    val id: String = "",          // Document ID из базы
    val messageId: String = "",   // ✅ ДОБАВЛЕНО: Для операций удаления/принятия (дублирует id для совместимости)

    val type: String = "SYSTEM",  // Тип сообщения (NEW_FRIEND, SPOT_REQUEST и т.д.)

    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",

    // Данные точки (для SPOT_SHARED, FRIEND_SPOT)
    val spotId: String = "",
    val spotName: String = "",
    val spotLat: Double = 0.0,
    val spotLon: Double = 0.0,
    val spotDescription: String = "",
    val spotWeight: Double = 0.0,
    val spotCreatedAt: Long = 0L,     // ✅ Убрано System.currentTimeMillis()
    val spotIsVerified: Boolean = false,
    val spotPhotoPath: String? = null,

    // Текстовое содержимое
    val contentText: String = "",
    val customMessage: String = "",

    val isRead: Boolean = false,
    val timestamp: Long = 0L          // ✅ Убрано System.currentTimeMillis()
)

enum class MessageType(val value: String) {
    NEW_REVIEW("NEW_REVIEW"),
    SPOT_REQUEST("SPOT_REQUEST"),
    SPOT_SHARED("SPOT_SHARED"),
    NEW_FRIEND("NEW_FRIEND"),
    SYSTEM("SYSTEM"),
    FRIEND_SPOT("FRIEND_SPOT");

    companion object {
        fun fromString(value: String): MessageType {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}