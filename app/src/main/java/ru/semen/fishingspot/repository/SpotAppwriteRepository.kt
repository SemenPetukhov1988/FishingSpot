package ru.semen.fishingspot.repository

import android.content.Context
import android.util.Log
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role

import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.utils.AppwriteManager

class SpotAppwriteRepository(private val context: Context) {

    // ️ ЗАМЕНИ НА СВОИ РЕАЛЬНЫЕ ID!
    private val databaseId = "6ad5021a0029a2c88786"
    private val spotsCollectionId = "6ad524e3001c9a28c3e5"

    /**
     * Получает только публичные точки из облака
     */
    suspend fun getPublicSpots(): Result<List<FishingSpot>> {
        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                queries = listOf(
                    Query.equal("visibility", "public"), // Фильтруем только публичные
                    Query.limit(500) // Ограничение, чтобы не грузить миллион точек
                )
            )

            val spots = response.documents.map { doc ->
                FishingSpot(
                    id = 0, // ID Room сгенерируется локально при сохранении
                    latitude = (doc.data["latitude"] as? Double) ?: 0.0,
                    longitude = (doc.data["longitude"] as? Double) ?: 0.0,
                    name = (doc.data["name"] as? String) ?: "Без названия",
                    description = (doc.data["description"] as? String) ?: "",
                    catchWeight = (doc.data["catchWeight"] as? Double) ?: 0.0,
                    fishType = doc.data["fishType"] as? String,
                    photoPath = null, // В облаке храним только URL
                    photoUrl = doc.data["photoUrl"] as? String,
                    isVerified = (doc.data["isVerified"] as? Boolean) ?: false,
                    authorId = (doc.data["authorId"] as? String) ?: "unknown",
                    visibility = (doc.data["visibility"] as? String) ?: "public",
                    createdAt = (doc.data["createdAt"] as? Long) ?: System.currentTimeMillis()
                )
            }
            Result.success(spots)
        } catch (e: Exception) {
            Log.e("APPWRITE_SPOT", "❌ Ошибка получения публичных точек", e)
            Result.failure(e)
        }
    }

    /**
     * Сохраняет точку в облако (только если она НЕ приватная)
     */
    suspend fun saveSpotToCloud(spot: FishingSpot): Result<Unit> {
        // Если точка приватная, в облако её не отправляем вообще
        if (spot.visibility == "private") {
            return Result.success(Unit)
        }

        return try {
            val spotData = mapOf(
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "name" to spot.name,
                "description" to spot.description,
                "catchWeight" to spot.catchWeight,
                "fishType" to spot.fishType,
                "photoUrl" to spot.photoUrl,
                "isVerified" to spot.isVerified,
                "authorId" to spot.authorId,
                "visibility" to spot.visibility,
                "createdAt" to spot.createdAt
            )

            // Настраиваем права доступа в зависимости от видимости
            val permissions = mutableListOf(
                Permission.update(Role.user(spot.authorId)),
                Permission.delete(Role.user(spot.authorId))
            )

            when (spot.visibility) {
                "public" -> permissions.add(Permission.read(Role.any()))
                "friends" -> permissions.add(Permission.read(Role.user(spot.authorId)))
                // Пока для друзей доступ только у автора. Позже добавим список ID друзей.
            }

            AppwriteManager.databases.createDocument(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                documentId = ID.unique(),
                data = spotData,
                permissions = permissions
            )

            Log.d("APPWRITE_SPOT", "✅ Точка сохранена в облако. Visibility: ${spot.visibility}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("APPWRITE_SPOT", "❌ Ошибка сохранения точки в облако", e)
            Result.failure(e)
        }
    }
}