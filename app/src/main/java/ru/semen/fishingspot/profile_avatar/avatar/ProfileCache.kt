package ru.semen.fishingspot.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object ProfileCache {
    private val cache = mutableMapOf<String, String>()
    // ✅ Защита от двойных запросов
    private val inFlight = mutableSetOf<String>()

    private val _avatarUpdated = MutableLiveData<String?>()
    val avatarUpdated: LiveData<String?> = _avatarUpdated

    fun getAvatar(userId: String): String? = cache[userId]

    fun isBeingFetched(userId: String): Boolean = userId in inFlight
    fun markFetching(userId: String) { inFlight.add(userId) }
    fun unmarkFetching(userId: String) { inFlight.remove(userId) }

    fun putAvatar(userId: String, url: String?) {
        if (!url.isNullOrEmpty()) cache[userId] = url else cache.remove(userId)
        _avatarUpdated.value = userId
    }

    // ✅ Батч-наполнение без лишних сигналов
    fun putAll(profiles: Map<String, String?>) {
        profiles.forEach { (id, url) -> if (!url.isNullOrEmpty()) cache[id] = url }
    }

    fun clear() {
        cache.clear()
        inFlight.clear()
        _avatarUpdated.value = null
    }
}