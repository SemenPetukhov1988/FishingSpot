package ru.semen.fishingspot.utils

object ProfileCache {
    // Хранилище: userId -> avatarUrl
    private val cache = mutableMapOf<String, String>()

    // Получить аватарку (null если нет)
    fun getAvatar(userId: String): String? = cache[userId]

    // Сохранить одну аватарку
    fun putAvatar(userId: String, url: String?) {
        if (!url.isNullOrEmpty()) {
            cache[userId] = url
        } else {
            cache.remove(userId)
        }
    }

    // Заполнить кэш пачкой данных (из batch-загрузки)
    fun putAll(profiles: Map<String, String?>) {
        profiles.forEach { (id, url) ->
            if (!url.isNullOrEmpty()) cache[id] = url
        }
    }

    // Очистить кэш (например, при выходе из аккаунта)
    fun clear() = cache.clear()
}