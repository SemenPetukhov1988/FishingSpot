package ru.semen.fishingspot.profile_avatar.repository

import android.util.Log
import io.appwrite.Query
import ru.semen.fishingspot.friends.data.User
import ru.semen.fishingspot.utils.AppwriteManager

object ProfileRepository {

    private const val TAG = "PROFILE_REPO"

    // ✅ Замени на свои реальные ID!
    private const val DATABASE_ID = "6ad5021a0029a2c88786"
    private const val USERS_COLLECTION_ID = "6ad5022b003770d2864c"

    suspend fun getUsersByIds(ids: List<String>): Result<List<User>> {
        if (ids.isEmpty()) return Result.success(emptyList())

        return try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = USERS_COLLECTION_ID,
                queries = listOf(
                    Query.equal("\$id", ids),
                    Query.limit(100)
                )
            )

            val users = response.documents.mapNotNull { doc ->
                // ✅ Данные документа живут в поле data, а не в операторе []
                User(
                    id = doc.id,
                    name = (doc.data["nickname"] as? String) ?: "Рыбак",
                    avatarUrl = doc.data["avatarUrl"] as? String,
                    isAlreadyFriend = false
                )
            }

            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Batch load failed: ${e.message}")
            Result.failure(e)
        }
    }
}
