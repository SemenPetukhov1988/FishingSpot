package ru.semen.fishingspot.repository

import android.content.Context
import android.util.Log
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.utils.AppwriteManager

class SavedSpotsRepository(private val context: Context) {

    private val databaseId = "6ad5021a0029a2c88786"
    private val savedSpotsCollectionId = "6ada41db002ea68773d9"

    companion object {
        private const val TAG = "SAVED_SPOTS_REPO"
    }

    /**
     * Сохранить точку в избранное (создать запись в saved_spots)
     */
    suspend fun saveSpot(userId: String, spot: FishingSpot): Result<Unit> {
        return try {
            val data = mapOf(
                "userId" to userId,
                "spotId" to spot.documentId,
                "authorId" to spot.authorId,
                "authorName" to spot.authorName,
                "spotName" to spot.name,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                // ✅ ИСПРАВЛЕНО: передаём как Long (число), а не как String
                "createdAt" to spot.createdAt
            )

            AppwriteManager.databases.createDocument(
                databaseId = databaseId,
                collectionId = savedSpotsCollectionId,
                documentId = ID.unique(),
                data = data,
                permissions = listOf(
                    Permission.read(Role.user(userId)),
                    Permission.update(Role.user(userId)),
                    Permission.delete(Role.user(userId))
                )
            )

            Log.d(TAG, "✅ Точка ${spot.documentId} сохранена в избранное для $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения в избранное", e)
            Result.failure(e)
        }
    }

    /**
     * Убрать точку из избранного (удалить запись из saved_spots)
     */
    suspend fun unsaveSpot(userId: String, spotDocumentId: String): Result<Unit> {
        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = savedSpotsCollectionId,
                queries = listOf(
                    Query.equal("userId", userId),
                    Query.equal("spotId", spotDocumentId),
                    Query.limit(1)
                )
            )

            if (response.documents.isEmpty()) {
                Log.w(TAG, "⚠️ Запись не найдена для удаления: userId=$userId, spotId=$spotDocumentId")
                return Result.success(Unit)
            }

            val documentId = response.documents[0].id
            AppwriteManager.databases.deleteDocument(
                databaseId = databaseId,
                collectionId = savedSpotsCollectionId,
                documentId = documentId
            )

            Log.d(TAG, "✅ Точка $spotDocumentId убрана из избранного (удалён документ $documentId)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления из избранного", e)
            Result.failure(e)
        }
    }

    /**
     * Получить все сохранённые точки пользователя
     */
    suspend fun getSavedSpots(userId: String): Result<List<FishingSpot>> {
        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = savedSpotsCollectionId,
                queries = listOf(
                    Query.equal("userId", userId),
                    Query.limit(500)
                )
            )

            val savedSpots = response.documents.map { doc ->
                val data = doc.data
                FishingSpot(
                    id = 0,
                    documentId = data["spotId"] as? String ?: "",
                    latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                    name = data["spotName"] as? String ?: "Без названия",
                    description = "Сохранено в избранное",
                    catchWeight = 0.0,
                    fishType = null,
                    photoPath = null,
                    photoUrl = null,
                    isVerified = false,
                    authorId = data["authorId"] as? String ?: "",
                    authorName = data["authorName"] as? String ?: "Неизвестно",
                    visibility = "private",
                    isPublicOnMap = false,
                    // ✅ ИСПРАВЛЕНО: читаем как Number и конвертируем в Long
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }

            Log.d(TAG, "✅ Загружено сохранённых точек: ${savedSpots.size}")
            Result.success(savedSpots)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения сохранённых точек", e)
            Result.failure(e)
        }
    }

    /**
     * Проверить, сохранена ли точка пользователем
     */
    suspend fun isSpotSaved(userId: String, spotDocumentId: String): Result<Boolean> {
        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = savedSpotsCollectionId,
                queries = listOf(
                    Query.equal("userId", userId),
                    Query.equal("spotId", spotDocumentId),
                    Query.limit(1)
                )
            )

            val isSaved = response.documents.isNotEmpty()
            Log.d(TAG, "🔍 Точка $spotDocumentId сохранена пользователем $userId: $isSaved")
            Result.success(isSaved)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки сохранения", e)
            Result.failure(e)
        }
    }
}