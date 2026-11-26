package ru.semen.fishingspot.map.point.comment

import android.content.Context
import android.util.Log
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.profile_avatar.repository.ProfileRepository
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache
import ru.semen.fishingspot.utils.UserSessionManager

class CommentRepository {

    private val DATABASE_ID = "6ad5021a0029a2c88786"
    private val COLLECTION_ID = "6adcf7ad002d061647dc"

    /**
     * Загружает комментарии и наполняет кэш аватарок
     */
    suspend fun getCommentsForSpot(spotId: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val result = AppwriteManager.databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_ID,
                queries = listOf(
                    Query.equal("spotId", spotId),
                    Query.orderAsc("createdAt"),
                    Query.limit(100)
                )
            )

            val flatList = result.documents.map { doc ->
                val data = doc.data
                Comment(
                    id = doc.id,
                    spotId = (data["spotId"] as? String) ?: "",
                    parentId = (data["parentId"] as? String)?.takeIf { it != "null" && it.isNotEmpty() },
                    userId = (data["userId"] as? String) ?: "",
                    userName = (data["userName"] as? String) ?: "Рыбак",
                    text = (data["text"] as? String) ?: "",
                    createdAt = (data["createdAt"] as? Long) ?: 0L
                )
            }

            // ✅ БАТЧ-ЗАГРУЗКА ПРОФИЛЕЙ АВТОРОВ КОММЕНТАРИЕВ
            val userIds = flatList.map { it.userId }.filter { it.isNotBlank() }.distinct()
            if (userIds.isNotEmpty()) {
                ProfileRepository.getUsersByIds(userIds).onSuccess { users ->
                    ProfileCache.putAll(users.associate { it.id to it.avatarUrl })
                }
            }

            buildThreadedComments(flatList)

        } catch (e: Exception) {
            Log.e("COMMENT_REPO", "Ошибка загрузки: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Отправляет комментарий или ответ
     */
    suspend fun addComment(context: Context, spotId: String, text: String, parentId: String?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val userId = UserSessionManager.getCurrentUserId(context) ?: throw Exception("Не авторизован")
                val userName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

                AppwriteManager.databases.createDocument(
                    databaseId = DATABASE_ID,
                    collectionId = COLLECTION_ID,
                    documentId = ID.unique(),
                    data = mapOf(
                        "spotId" to spotId,
                        "parentId" to (parentId ?: "null"),
                        "userId" to userId,
                        "userName" to userName,
                        "text" to text,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Превращает плоский список в дерево (родитель -> дети)
     */
    private fun buildThreadedComments(comments: List<Comment>): List<Comment> {
        val commentMap = comments.associateBy { it.id }
        val roots = mutableListOf<Comment>()
        val childrenMap = mutableMapOf<String, MutableList<Comment>>()

        for (comment in comments) {
            if (comment.parentId == null || !commentMap.containsKey(comment.parentId)) {
                roots.add(comment)
            } else {
                childrenMap.getOrPut(comment.parentId) { mutableListOf() }.add(comment)
            }
        }

        val result = mutableListOf<Comment>()
        fun addWithChildren(parent: Comment) {
            result.add(parent)
            childrenMap[parent.id]?.forEach { child ->
                addWithChildren(child)
            }
        }

        roots.forEach { root -> addWithChildren(root) }
        return result
    }
}