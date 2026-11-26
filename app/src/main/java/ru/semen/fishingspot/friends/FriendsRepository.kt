package ru.semen.fishingspot.friends

import android.content.Context
import android.util.Log
import io.appwrite.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.friends.data.Friend
import ru.semen.fishingspot.friends.data.User
import ru.semen.fishingspot.profile_avatar.repository.ProfileRepository
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache
import ru.semen.fishingspot.utils.UserSessionManager

class FriendsRepository(private val context: Context) {

    private val DB_ID = "6ad5021a0029a2c88786"
    private val USERS_COL_ID = "6ad5022b003770d2864c"
    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"

    private val currentUserId: String?
        get() = UserSessionManager.getCurrentUserId(context)

    /**
     * Получаем список друзей с батч-загрузкой профилей для кэша
     */
    suspend fun getFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: return@withContext emptyList()

        try {
            // 1. Получаем список ID друзей из документа текущего пользователя
            val doc = AppwriteManager.databases.getDocument(
                databaseId = DB_ID,
                collectionId = USERS_COL_ID,
                documentId = userId
            )

            val friendIds = doc.data["friends"] as? List<String> ?: emptyList()

            if (friendIds.isEmpty()) return@withContext emptyList()

            // ✅ 2. БАТЧ-ЗАГРУЗКА ПРОФИЛЕЙ ВСЕХ ДРУЗЕЙ ОДНИМ ЗАПРОСОМ
            // Это наполнит ProfileCache, и адаптер возьмет URL оттуда мгновенно
            Log.d("FRIENDS_REPO", "📥 Запрашиваем профили для ${friendIds.size} друзей...")
            ProfileRepository.getUsersByIds(friendIds).onSuccess { users ->
                ProfileCache.putAll(users.associate { it.id to it.avatarUrl })
                Log.d("FRIENDS_REPO", "✅ Кэш профилей друзей обновлен")
            }

            // 3. Формируем список для UI
            val friendsList = mutableListOf<Friend>()

            // Проходимся по ID и берем данные.
            // Если ProfileRepository вернул не всех (например, кто-то удалился),
            // мы обработаем это gracefully.
            usersMap@ for (id in friendIds) {
                try {
                    // Пробуем взять имя из кэша или документа, если он есть в ответе batch-запроса
                    // Для простоты пока оставим локальный запрос имени, но аватарка уже в кэше!
                    val friendDoc = AppwriteManager.databases.getDocument(
                        databaseId = DB_ID,
                        collectionId = USERS_COL_ID,
                        documentId = id
                    )
                    val name = friendDoc.data["nickname"] as? String ?: "Рыбак"
                    friendsList.add(Friend(id = id, name = name, status = "В друзьях"))
                } catch (e: Exception) {
                    // Если документ друга не найден (удалился), просто пропускаем его
                    Log.w("FRIENDS_REPO", "Друг с ID $id не найден или удален")
                }
            }

            friendsList
        } catch (e: Exception) {
            Log.e("FRIENDS_REPO", "Ошибка загрузки списка друзей", e)
            emptyList()
        }
    }

    /**
     * Поиск пользователей по никнейму
     */
    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val myId = currentUserId ?: return@withContext emptyList()

        try {
            val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
            val myFriends = (myDoc.data["friends"] as? List<String>)?.toSet() ?: emptySet()

            val result = AppwriteManager.databases.listDocuments(
                databaseId = DB_ID,
                collectionId = USERS_COL_ID,
                queries = listOf(Query.limit(100))
            )

            val queryLower = query.lowercase()
            result.documents.mapNotNull { doc ->
                if (doc.id == myId) return@mapNotNull null

                val nickname = doc.data["nickname"] as? String ?: return@mapNotNull null
                val isDeleted = doc.data["isProfileDeleted"] as? Boolean ?: false

                if (!isDeleted && nickname.lowercase().contains(queryLower)) {
                    User(
                        id = doc.id,
                        name = nickname,
                        isAlreadyFriend = myFriends.contains(doc.id)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("SEARCH_DEBUG", "❌ Ошибка поиска", e)
            emptyList()
        }
    }

    suspend fun sendFriendRequest(friendId: String, friendName: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))
            val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

            try {
                val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
                val myFriends = (myDoc.data["friends"] as? List<String>) ?: emptyList()

                if (myFriends.contains(friendId)) {
                    return@withContext Result.failure(Exception("Вы уже друзья!"))
                }

                val messageData = mapOf(
                    "senderId" to myId,
                    "senderName" to myName,
                    "recipientId" to friendId,
                    "type" to "NEW_FRIEND",
                    "contentText" to "",
                    "spotId" to "",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )

                AppwriteManager.databases.createDocument(
                    databaseId = DB_ID,
                    collectionId = MESSAGES_COL_ID,
                    documentId = "unique()",
                    data = messageData
                )

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun acceptFriendRequest(friendId: String, messageId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))
            val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

            try {
                val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
                val friendDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, friendId)

                val myFriends = (myDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()
                val friendFriends = (friendDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()

                if (!myFriends.contains(friendId)) myFriends.add(friendId)
                if (!friendFriends.contains(myId)) friendFriends.add(myId)

                AppwriteManager.databases.updateDocument(DB_ID, USERS_COL_ID, myId, mapOf("friends" to myFriends))
                AppwriteManager.databases.updateDocument(DB_ID, USERS_COL_ID, friendId, mapOf("friends" to friendFriends))

                val notificationData = mapOf(
                    "senderId" to myId,
                    "senderName" to myName,
                    "recipientId" to friendId,
                    "type" to "FRIEND_ACCEPTED",
                    "contentText" to "$myName принял(а) вашу заявку в друзья!",
                    "spotId" to "",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )

                AppwriteManager.databases.createDocument(DB_ID, MESSAGES_COL_ID, "unique()", data = notificationData)
                AppwriteManager.databases.deleteDocument(DB_ID, MESSAGES_COL_ID, messageId)

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun removeFriend(friendId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))

        try {
            val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
            val friendDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, friendId)

            var myFriends = (myDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()
            var friendFriends = (friendDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()

            myFriends.remove(friendId)
            friendFriends.remove(myId)

            AppwriteManager.databases.updateDocument(DB_ID, USERS_COL_ID, myId, mapOf("friends" to myFriends))
            AppwriteManager.databases.updateDocument(DB_ID, USERS_COL_ID, friendId, mapOf("friends" to friendFriends))

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}