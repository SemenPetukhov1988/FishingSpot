package ru.semen.fishingspot.data

/**
 * Модель пользователя для поиска и добавления в друзья.
 * Отличается от Friend тем, что это ещё не друг, а просто пользователь системы.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val isAlreadyFriend: Boolean = false // Чтобы не добавлять повторно
)