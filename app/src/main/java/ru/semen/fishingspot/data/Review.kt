package ru.semen.fishingspot.data

/**
 * Модель отзыва к публичной точке.
 * Хранится в Firebase Firestore в подколлекции reviews/{spotId}
 */
data class Review(
    val id: String = "",              // ID документа в Firebase
    val authorId: String = "",        // ID пользователя, который оставил отзыв
    val authorName: String = "",      // Имя автора (для отображения)
    val text: String = "",            // Текст отзыва
    val createdAt: Long = 0L          // Время создания (timestamp)
)