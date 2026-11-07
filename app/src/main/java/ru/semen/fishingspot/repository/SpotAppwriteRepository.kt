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

    private val databaseId = "6ad5021a0029a2c88786"
    private val spotsCollectionId = "6ad524e3001c9a28c3e5"

    companion object {
        private const val TAG = "SPOT_SERVER"
    }

    private fun asDouble(value: Any?): Double = (value as? Number)?.toDouble() ?: 0.0
    private fun asLong(value: Any?): Long = (value as? Number)?.toLong() ?: 0L

    // ✅ ИСПРАВЛЕНИЕ: Добавлено чтение postText
    private fun toSpot(documentId: String, data: Map<String, Any?>): FishingSpot {
        val rawPost = data["postText"]
        Log.d("SPOT_REPO", "🔍 Маппинг $documentId: postText=$rawPost")
        return FishingSpot(
            id = 0,
            documentId = documentId,
            latitude = asDouble(data["latitude"]),
            longitude = asDouble(data["longitude"]),
            name = data["name"] as? String ?: "Без названия",
            description = data["description"] as? String ?: "",
            catchWeight = asDouble(data["catchWeight"]),
            fishType = data["fishType"] as? String,
            photoPath = null,
            photoUrl = data["photoUrl"] as? String,
            isVerified = data["isVerified"] as? Boolean ?: false,
            authorId = data["authorId"] as? String ?: "",
            authorName = data["authorName"] as? String ?: "Неизвестно",
            visibility = data["visibility"] as? String ?: "private",
            isPublicOnMap = data["isPublicOnMap"] as? Boolean ?: false,
            createdAt = asLong(data["createdAt"]),
            postText = data["postText"] as? String ?: ""
        )
    }

    suspend fun getPublicSpots(): Result<List<FishingSpot>> {
        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                queries = listOf(
                    Query.equal("visibility", "public"),
                    Query.limit(500)
                )
            )
            val spots = response.documents.map { toSpot(it.id, it.data) }
            Log.d(TAG, "✅ публичных с сервера: ${spots.size}")
            Result.success(spots)
        } catch (e: Exception) {
            Log.e(TAG, "❌ getPublicSpots", e)
            Result.failure(e)
        }
    }

    suspend fun getMySpots(userId: String): Result<List<FishingSpot>> {
        return try {
            val savedResponse = AppwriteManager.databases.listDocuments(
                databaseId = "6ad5021a0029a2c88786",
                collectionId = "6ada41db002ea68773d9",
                queries = listOf(
                    Query.equal("userId", userId),
                    Query.limit(500)
                )
            )
            val savedSpotIds = savedResponse.documents.mapNotNull { doc ->
                doc.data["spotId"] as? String
            }
            Log.d(TAG, "📊 Найдено избранных точек: ${savedSpotIds.size}")

            val myResponse = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                queries = listOf(
                    Query.equal("authorId", userId),
                    Query.limit(500)
                )
            )
            val mySpots = myResponse.documents.map { toSpot(it.id, it.data) }
            Log.d(TAG, "📊 Найдено моих созданных точек: ${mySpots.size}")

            val savedSpots = if (savedSpotIds.isNotEmpty()) {
                val savedResponse2 = AppwriteManager.databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = spotsCollectionId,
                    queries = listOf(
                        Query.equal("\$id", savedSpotIds),
                        Query.limit(500)
                    )
                )
                savedResponse2.documents.map { toSpot(it.id, it.data) }
            } else {
                emptyList()
            }
            Log.d(TAG, "📊 Загружено избранных точек: ${savedSpots.size}")

            val allSpots = (mySpots + savedSpots).distinctBy { it.documentId }
            Log.d(TAG, "✅ моих с сервера (созданные + избранные): ${allSpots.size}")
            Result.success(allSpots)
        } catch (e: Exception) {
            Log.e(TAG, "❌ getMySpots", e)
            Result.failure(e)
        }
    }

    suspend fun getSpotByDocumentId(documentId: String): Result<FishingSpot> {
        return try {
            val doc = AppwriteManager.databases.getDocument(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                documentId = documentId
            )
            Result.success(toSpot(doc.id, doc.data))
        } catch (e: Exception) {
            Log.e(TAG, "❌ getSpotByDocumentId: $documentId", e)
            Result.failure(e)
        }
    }

    suspend fun createSpot(spot: FishingSpot): Result<FishingSpot> {
        return try {
            val data = mapOf(
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "name" to spot.name.take(100),
                "description" to spot.description.take(1000),
                "catchWeight" to spot.catchWeight,
                "fishType" to spot.fishType,
                "photoUrl" to spot.photoUrl,
                "postText" to spot.postText, // ✅ Тут уже было верно
                "isVerified" to spot.isVerified,
                "authorId" to spot.authorId,
                "authorName" to spot.authorName,
                "visibility" to spot.visibility,
                "isPublicOnMap" to (spot.visibility == "public"),
                "createdAt" to spot.createdAt
            )

            val permissions = mutableListOf(
                Permission.read(Role.user(spot.authorId)),
                Permission.update(Role.user(spot.authorId)),
                Permission.delete(Role.user(spot.authorId))
            )
            if (spot.visibility == "public") {
                permissions.add(Permission.read(Role.any()))
            }

            val doc = AppwriteManager.databases.createDocument(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                documentId = ID.unique(),
                data = data,
                permissions = permissions
            )

            Log.d(TAG, "✅ создан документ: ${doc.id}")
            Result.success(toSpot(doc.id, doc.data))
        } catch (e: Exception) {
            Log.e(TAG, "❌ createSpot", e)
            Result.failure(e)
        }
    }

    suspend fun updateSpotVisibility(documentId: String, newVisibility: String): Result<Unit> {
        return try {
            AppwriteManager.databases.updateDocument(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                documentId = documentId,
                data = mapOf(
                    "visibility" to newVisibility,
                    "isPublicOnMap" to (newVisibility == "public")
                )
            )
            Log.d(TAG, "✅ обновлена видимость $documentId -> $newVisibility")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ updateSpotVisibility", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSpotByDocumentId(documentId: String): Result<Unit> {
        return try {
            AppwriteManager.databases.deleteDocument(
                databaseId = databaseId,
                collectionId = spotsCollectionId,
                documentId = documentId
            )
            Log.d(TAG, "✅ удалён документ $documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ deleteSpotByDocumentId", e)
            Result.failure(e)
        }
    }
}