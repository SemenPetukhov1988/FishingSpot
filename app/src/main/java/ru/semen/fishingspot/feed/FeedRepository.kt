package ru.semen.fishingspot.feed

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.appwrite.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.map.point.data.FishingSpot
import ru.semen.fishingspot.profile_avatar.repository.ProfileRepository
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache

class FeedRepository {

    private val DATABASE_ID = "6ad5021a0029a2c88786"
    private val COLLECTION_ID = "6ad524e3001c9a28c3e5"

    private val _spots = MutableLiveData<List<FishingSpot>>()
    val spots: LiveData<List<FishingSpot>> = _spots

    fun loadPublicSpots() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("FEED_REPO", "🔄 Начало загрузки публичных точек...")

                // 1. Загружаем точки
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DATABASE_ID,
                    collectionId = COLLECTION_ID,
                    queries = listOf(
                        Query.equal("visibility", "public"),
                        Query.orderDesc("createdAt"),
                        Query.limit(50)
                    )
                )

                Log.d("FEED_REPO", "✅ Сервер вернул документов: ${result.documents.size}")

                // 2. Парсим точки
                val list = result.documents.map { doc ->
                    val data = doc.data
                    FishingSpot(
                        documentId = doc.id,
                        name = (data["name"] as? String) ?: "",
                        description = (data["description"] as? String) ?: "",
                        postText = (data["postText"] as? String) ?: "",
                        latitude = (data["latitude"] as? Double) ?: 0.0,
                        longitude = (data["longitude"] as? Double) ?: 0.0,
                        photoUrl = data["photoUrl"] as? String,
                        authorId = (data["authorId"] as? String) ?: "",
                        authorName = (data["authorName"] as? String) ?: "Рыбак",
                        visibility = (data["visibility"] as? String) ?: "private",
                        catchWeight = (data["catchWeight"] as? Double) ?: 0.0,
                        fishType = (data["fishType"] as? String),
                        createdAt = (data["createdAt"] as? Long) ?: 0L
                    )
                }

                // ✅ 3. БАТЧ-ЗАГРУЗКА ПРОФИЛЕЙ АВТОРОВ
                val authorIds = list.map { it.authorId }.filter { it.isNotBlank() }.distinct()

                if (authorIds.isNotEmpty()) {
                    Log.d("FEED_REPO", "📥 Запрашиваем профили для ${authorIds.size} авторов...")
                    ProfileRepository.getUsersByIds(authorIds).onSuccess { users ->
                        // Кладем все аватарки в кэш одним махом
                        ProfileCache.putAll(users.associate { it.id to it.avatarUrl })
                        Log.d("FEED_REPO", "✅ Кэш профилей обновлен: ${users.size} записей")
                    }.onFailure { e ->
                        Log.e("FEED_REPO", "❌ Ошибка загрузки профилей: ${e.message}")
                    }
                }

                // 4. Отдаем данные в UI
                withContext(Dispatchers.Main) {
                    _spots.value = list
                    Log.d("FEED_REPO", "📤 UI обновлен. Точек: ${list.size}")
                }

            } catch (e: Exception) {
                Log.e("FEED_REPO", "❌ КРИТИЧЕСКАЯ ОШИБКА: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _spots.value = emptyList()
                }
            }
        }
    }
}