package ru.semen.fishingspot.feature_messages.notifications.repository

import android.content.Context
import io.appwrite.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.messages.friends.data.AppMessage
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class NotificationsRepository(private val context: Context) {

    private val DB_ID = "6ad5021a0029a2c88786"
    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"
    private val NOTIFICATIONS_COL_ID = "6aef1b7300251aa8ce5b"

    suspend fun getIncomingMessages(): Result<List<AppMessage>> = withContext(Dispatchers.IO) {
        val currentUserId = UserSessionManager.getCurrentUserId(context)
            ?: return@withContext Result.failure(Exception("Пользователь не авторизован"))

        try {
            val response = AppwriteManager.databases.listDocuments(
                databaseId = DB_ID,
                collectionId = MESSAGES_COL_ID,
                queries = listOf(
                    Query.equal("recipientId", currentUserId),
                    Query.orderDesc("timestamp"),
                    Query.limit(50)
                )
            )

            val messages = response.documents.map { doc ->
                val data = doc.data
                AppMessage(
                    id = doc.id,
                    messageId = doc.id,
                    senderId = data["senderId"] as? String ?: "",
                    senderName = data["senderName"] as? String ?: "Рыбак",
                    recipientId = data["recipientId"] as? String ?: "",
                    type = data["type"] as? String ?: "SYSTEM",
                    spotName = data["spotName"] as? String ?: "",
                    spotLat = (data["spotLat"] as? Number)?.toDouble() ?: 0.0,
                    spotLon = (data["spotLon"] as? Number)?.toDouble() ?: 0.0,
                    contentText = data["contentText"] as? String ?: "",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                    plannedDate = (data["plannedDate"] as? Number)?.toLong() ?: 0L
                )
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ УНИВЕРСАЛЬНОЕ УДАЛЕНИЕ СООБЩЕНИЙ И ЯКОРЕЙ
    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppwriteManager.databases.deleteDocument(
                databaseId = DB_ID,
                collectionId = MESSAGES_COL_ID,
                documentId = messageId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ УДАЛЕНИЕ СИГНАЛОВ (ДЛЯ ПОЖЕЛАНИЙ)
    suspend fun deleteSignal(documentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppwriteManager.databases.deleteDocument(
                databaseId = DB_ID,
                collectionId = NOTIFICATIONS_COL_ID,
                documentId = documentId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}