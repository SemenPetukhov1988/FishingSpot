package ru.semen.fishingspot.data

data class AppMessage(
    val id: String = "",
    val type: String = "NEW_REVIEW",

    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",

    val spotId: String = "",
    val spotName: String = "",

    val spotLat: Double = 0.0,
    val spotLon: Double = 0.0,

    val contentText: String = "",
    val customMessage: String = "",

    // ✅ НОВЫЕ ПОЛЯ для уведомления "Друг сходил на рыбалку"
    val spotDescription: String = "",
    val spotWeight: Double = 0.0,
    val spotCreatedAt: Long = System.currentTimeMillis(),
    val spotIsVerified: Boolean = false,
    val spotPhotoPath: String? = null,

    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    NEW_REVIEW,
    SPOT_REQUEST,
    SPOT_SHARED,
    NEW_FRIEND,
    SYSTEM,
    FRIEND_SPOT // ✅ Новый тип
}