package ru.semen.fishingspot.data

/**
 * Модель друга для отображения в списке.
 * В будущем сюда можно добавить avatarUrl, lastSeen и т.д.
 */
data class Friend(
    val id: String = "",
    val name: String = "",
    val status: String = "Добавлен недавно"
)