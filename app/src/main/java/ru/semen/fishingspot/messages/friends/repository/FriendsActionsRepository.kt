package ru.semen.fishingspot.messages.friends.repository

import android.content.Context
import io.appwrite.ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class FriendsActionsRepository(private val context: Context) {

    private val DB_ID = "6ad5021a0029a2c88786"
    private val USERS_COL_ID = "6ad5022b003770d2864c"
    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"

    /**
     * Принять заявку в друзья.
     * Логика: Взаимное добавление + Уведомление отправителю + Удаление запроса
     */
    suspend fun acceptFriendRequest(friendId: String, messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val myId = UserSessionManager.getCurrentUserId(context)
                ?: return@withContext Result.failure(Exception("Не авторизован"))
            val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

            try {
                // 1. Получаем текущие списки друзей обоих пользователей
                val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
                val friendDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, friendId)

                val myFriends =
                    (myDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()
                val friendFriends =
                    (friendDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()

                // 2. Добавляем друг друга взаимно (если еще не друзья)
                if (!myFriends.contains(friendId)) myFriends.add(friendId)
                if (!friendFriends.contains(myId)) friendFriends.add(myId)

                // 3. Обновляем документы пользователей
                AppwriteManager.databases.updateDocument(
                    databaseId = DB_ID, collectionId = USERS_COL_ID, documentId = myId,
                    data = mapOf("friends" to myFriends)
                )
                AppwriteManager.databases.updateDocument(
                    databaseId = DB_ID, collectionId = USERS_COL_ID, documentId = friendId,
                    data = mapOf("friends" to friendFriends)
                )

                // 4. Создаем уведомление для отправителя заявки
                val notificationData = mapOf(
                    "senderId" to myId,
                    "senderName" to myName,
                    "recipientId" to friendId,
                    "type" to "FRIEND_ACCEPTED",
                    "contentText" to "$myName принял(а) вашу заявку в друзья!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )

                AppwriteManager.databases.createDocument(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    documentId = ID.Companion.unique(),
                    data = notificationData
                )

                // 5. Удаляем исходный запрос из ленты сообщений
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

    /**
     * Отклонить заявку (просто удаляем сообщение-запрос)
     */
    suspend fun declineFriendRequest(messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
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
}