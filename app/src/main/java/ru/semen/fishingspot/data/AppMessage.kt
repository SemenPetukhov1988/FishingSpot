package ru.semen.fishingspot.data

/**
 * Модель сообщения в приложении.
 * Хранится в Firebase Firestore в коллекции users/{userId}/messages
 */
data class AppMessage(
    val id: String = "",                    // ID документа в Firebase
    val type: MessageType = MessageType.NEW_REVIEW, // Тип сообщения (для будущих расширений)
    val senderId: String = "",              // ID отправителя
    val senderName: String = "",            // Имя отправителя
    val spotId: String = "",                // ID точки в Firebase (для перехода)
    val spotName: String = "",              // Название точки
    val contentText: String = "",           // Текст отзыва / запроса
    val isRead: Boolean = false,            // Прочитано ли сообщение
    val timestamp: Long = 0L                // Время создания
)

/**
 * Типы сообщений.
 * Сейчас только один, но в будущем добавим:
 * - SPOT_REQUEST (кто-то просит поделиться точкой)
 * - SPOT_SHARED (кто-то поделился с тобой точкой)
 * - NEW_FRIEND (запрос в друзья)
 * - SYSTEM (системное уведомление от приложения)
 */
enum class MessageType {
    NEW_REVIEW,          // Новый отзыв на твоей точке
    SPOT_REQUEST,        // Запрос поделиться точкой (будет позже)
    SPOT_SHARED,         // Кто-то поделился точкой (будет позже)
    NEW_FRIEND,          // Запрос в друзья (будет позже)
    SYSTEM               // Системное сообщение (будет позже)
}