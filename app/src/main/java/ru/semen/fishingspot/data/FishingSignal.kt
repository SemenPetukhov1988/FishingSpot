package ru.semen.fishingspot.data

/**
 * Модель данных ТОЛЬКО для сигналов рыбалки (on_fishing, going_fishing, fishing_wish, fishing_reply)
 * Не путать с AppMessage (друзья, точки, отзывы)
 */
data class FishingSignal(
    val id: String,                    // documentId из Appwrite
    val senderId: String,              // Кто отправил
    val senderName: String,            // Имя отправителя
    val receiverId: String,            // Кому отправлено
    val type: String,                  // "on_fishing", "going_fishing", "fishing_wish", "fishing_reply"

    // Геоданные (для on_fishing / going_fishing)
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val zoom: Double = 0.0,

    // Текст (для going_fishing - вопрос, для fishing_wish/reply - ответ/пожелание)
    val messageText: String? = null,

    // Связь с якорем (для fishing_wish / fishing_reply)
    val anchorId: String = "",

    // Метаданные
    val isRead: Boolean = false,
    val createdAt: Long                // timestamp в миллисекундах
)