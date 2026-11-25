package ru.semen.fishingspot.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object ProfileCache {
    private val cache = mutableMapOf<String, String>()

    // ✅ Сигнал обновления: хранит ID пользователя, чья аватарка изменилась
    private val _avatarUpdated = MutableLiveData<String?>()
    val avatarUpdated: LiveData<String?> = _avatarUpdated

    fun getAvatar(userId: String): String? = cache[userId]

    fun putAvatar(userId: String, url: String?) {
        if (!url.isNullOrEmpty()) cache[userId] = url else cache.remove(userId)
        // ✅ Уведомляем всех подписчиков сразу после изменения
        _avatarUpdated.value = userId
    }

    fun putAll(profiles: Map<String, String?>) {
        profiles.forEach { (id, url) -> if (!url.isNullOrEmpty()) cache[id] = url }
    }

    fun clear() {
        cache.clear()
        _avatarUpdated.value = null
    }
}