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
import ru.semen.fishingspot.utils.AppwriteManager

class FeedRepository {

    // ⚠️ ЗАМЕНИ НА СВОИ РЕАЛЬНЫЕ ID ИЗ APPWRITE CONSOLE
    private val DATABASE_ID = "6ad5021a0029a2c88786"
    private val COLLECTION_ID = "6ad524e3001c9a28c3e5"

    private val _spots = MutableLiveData<List<FishingSpot>>()
    val spots: LiveData<List<FishingSpot>> = _spots

    /**
     * Загружает ТОЛЬКО публичные точки из Appwrite
     */
    fun loadPublicSpots() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("FEED_REPO", "🔄 Начало загрузки публичных точек...")
                Log.d("FEED_REPO", "   DB: $DATABASE_ID | Collection: $COLLECTION_ID")

                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DATABASE_ID,
                    collectionId = COLLECTION_ID,
                    queries = listOf(
                        Query.Companion.equal("visibility", "public"), // ✅ Фильтр по видимости
                        Query.Companion.orderDesc("createdAt"),         // ✅ Новые сверху
                        Query.Companion.limit(50)                       // ✅ Пагинация
                    )
                )

                Log.d("FEED_REPO", "✅ Сервер вернул документов: ${result.documents.size}")

                // В FeedRepository.kt, внутри loadPublicSpots()

                val list = result.documents.map { doc ->
                    val data = doc.data

                    // Логируем для проверки
                    Log.d("FEED_REPO", "   📍 Точка: ${data["name"]} | postText=${data["postText"]}")

                    FishingSpot(
                        documentId = doc.id,
                        name = (data["name"] as? String) ?: "",
                        description = (data["description"] as? String) ?: "",

                        // ✅ ДОБАВЛЕНО: Чтение текста поста для ленты
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

                withContext(Dispatchers.Main) {
                    _spots.value = list
                    Log.d("FEED_REPO", " UI обновлен. Всего точек в ленте: ${list.size}")
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