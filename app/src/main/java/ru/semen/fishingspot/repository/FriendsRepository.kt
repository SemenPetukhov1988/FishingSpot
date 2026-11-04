package ru.semen.fishingspot.data

import android.content.Context
import io.appwrite.Query

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class FriendsRepository(private val context: Context) {

    // ⚠️ ЗАМЕНИ НА СВОИ РЕАЛЬНЫЕ ID ИЗ КОНСОЛИ APPWRITE
    private val DB_ID = "6ad5021a0029a2c88786"
    private val USERS_COL_ID = "6ad5022b003770d2864c"
    private val MESSAGES_COL_ID = "6aeb4c1600142e707a69"

    private val currentUserId: String?
        get() = UserSessionManager.getCurrentUserId(context)

    /**
     * Получаем список друзей из массива friends текущего пользователя
     */
    suspend fun getFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: return@withContext emptyList()

        try {
            val doc = AppwriteManager.databases.getDocument(
                databaseId = DB_ID,
                collectionId = USERS_COL_ID,
                documentId = userId
            )

            // friends хранится как List<String> (ID друзей)
            val friendIds = doc.data["friends"] as? List<String> ?: emptyList()

            // Чтобы показать имена, нам нужно загрузить профили друзей
            // Для MVP делаем простой маппинг. В будущем можно оптимизировать.
            // Пока вернем заглушки или попробуем достать имена, если они есть в кэше/локально.
            // Но лучше всего — загрузить документы друзей.

            // ⚡ БЫСТРЫЙ ВАРИАНТ ДЛЯ MVP:
            // Просто возвращаем ID, а имена подтянем позже или храним локально.
            // Но чтобы адаптер работал красиво, давай попробуем загрузить их.

            val friendsList = mutableListOf<Friend>()
            for (id in friendIds) {
                try {
                    val friendDoc = AppwriteManager.databases.getDocument(
                        databaseId = DB_ID,
                        collectionId = USERS_COL_ID,
                        documentId = id
                    )
                    val name = friendDoc.data["nickname"] as? String ?: "Рыбак"
                    friendsList.add(Friend(id = id, name = name, status = "В друзьях"))
                } catch (e: Exception) {
                    // Если друг удалил профиль или ошибка сети - пропускаем
                }
            }

            friendsList
        } catch (e: Exception) {
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
            // 1. Сначала получаем ТВОЙ список друзей
            val myDoc = AppwriteManager.databases.getDocument(
                databaseId = DB_ID,
                collectionId = USERS_COL_ID,
                documentId = myId
            )
            val myFriends = (myDoc.data["friends"] as? List<String>)?.toSet() ?: emptySet()

            android.util.Log.d("SEARCH_DEBUG", "🔍 Мои друзья (${myFriends.size}): $myFriends")

            // 2. Загружаем всех пользователей для локального поиска
            val result = AppwriteManager.databases.listDocuments(
                databaseId = DB_ID,
                collectionId = USERS_COL_ID,
                queries = listOf(Query.limit(100))
            )

            val queryLower = query.lowercase()
            val users = result.documents.mapNotNull { doc ->
                if (doc.id == myId) return@mapNotNull null

                val nickname = doc.data["nickname"] as? String ?: return@mapNotNull null
                val isDeleted = doc.data["isProfileDeleted"] as? Boolean ?: false

                if (!isDeleted && nickname.lowercase().contains(queryLower)) {
                    // ✅ ПРОВЕРЯЕМ: есть ли этот user.id в МОЁМ списке друзей
                    val isFriend = myFriends.contains(doc.id)

                    User(
                        id = doc.id,
                        name = nickname,
                        isAlreadyFriend = isFriend // ← Вот здесь магия!
                    )
                } else null
            }

            android.util.Log.d("SEARCH_DEBUG", "🎯 Найдено: ${users.size}, из них друзей: ${users.count { it.isAlreadyFriend }}")
            users

        } catch (e: Exception) {
            android.util.Log.e("SEARCH_DEBUG", "❌ Ошибка поиска", e)
            emptyList()
        }
    }
    /**
     * Отправка запроса в друзья
     * Логика: Создаем сообщение в messages + Добавляем себя в pending_friends (опционально)
     */
    suspend fun sendFriendRequest(friendId: String, friendName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))
        val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

        try {
            // ✅ ПРОВЕРКА: Не является ли он уже другом?
            val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
            val myFriends = (myDoc.data["friends"] as? List<String>) ?: emptyList()

            if (myFriends.contains(friendId)) {
                return@withContext Result.failure(Exception("Вы уже друзья!"))
            }

            // ✅ ПРОВЕРКА: Не отправляли ли мы уже запрос?
            // Можно проверить по коллекции messages, но для MVP достаточно проверки friends
            // Если хочешь проверять pending requests - нужен отдельный запрос к messages

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
    /**
     * Принятие заявки в друзья
     * Логика: Добавляем друг друга в массив friends + Удаляем сообщение-запрос
     */
    suspend fun acceptFriendRequest(friendId: String, messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))
        val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

        try {
            // 1. Получаем текущие списки друзей обоих пользователей
            val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
            val friendDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, friendId)

            val myFriends = (myDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()
            val friendFriends = (friendDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()

            // 2. Добавляем друг друга взаимно
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

            // 4. ✅ СОЗДАЁМ УВЕДОМЛЕНИЕ ДЛЯ ОТПРАВИТЕЛЯ ЗАЯВКИ
            val notificationData = mapOf(
                "senderId" to myId,           // Отправитель уведомления - тот, кто принял
                "senderName" to myName,
                "recipientId" to friendId,    // Получатель - тот, кто отправлял заявку
                "type" to "FRIEND_ACCEPTED",  // Новый тип сообщения
                "contentText" to "$myName принял(а) вашу заявку в друзья!",
                "spotId" to "",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )

            AppwriteManager.databases.createDocument(
                databaseId = DB_ID,
                collectionId = MESSAGES_COL_ID,
                documentId = "unique()",
                data = notificationData
            )

            // 5. Удаляем исходный запрос
            AppwriteManager.databases.deleteDocument(
                databaseId = DB_ID,
                collectionId = MESSAGES_COL_ID,
                documentId = messageId
            )

            android.util.Log.d("FRIENDS_REPO", "✅ Друзья добавлены взаимно, уведомление отправлено")
            Result.success(true)

        } catch (e: Exception) {
            android.util.Log.e("FRIENDS_REPO", "❌ Ошибка принятия заявки", e)
            Result.failure(e)
        }
    }
    /**
     * Удаляет пользователя из списка друзей текущего пользователя
     */
    suspend fun removeFriend(friendId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val myId = currentUserId ?: return@withContext Result.failure(Exception("Не авторизован"))

        try {
            android.util.Log.d("REMOVE_DEBUG", "🗑️ Начало удаления: myId=$myId, friendId=$friendId")

            // Получаем текущие списки
            val myDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, myId)
            val friendDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, friendId)

            var myFriends = (myDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()
            var friendFriends = (friendDoc.data["friends"] as? List<String>)?.toMutableList() ?: mutableListOf()

            android.util.Log.d("REMOVE_DEBUG", "📋 До удаления: Мои друзья=${myFriends.size}, Друзья друга=${friendFriends.size}")

            // Удаляем взаимно
            val removedFromMe = myFriends.remove(friendId)
            val removedFromHim = friendFriends.remove(myId)

            android.util.Log.d("REMOVE_DEBUG", "✅ Удалено из моего списка: $removedFromMe, из списка друга: $removedFromHim")

            if (!removedFromMe && !removedFromHim) {
                return@withContext Result.failure(Exception("Пользователь не найден в списках друзей"))
            }

            // Обновляем ОБА документа
            AppwriteManager.databases.updateDocument(
                databaseId = DB_ID, collectionId = USERS_COL_ID, documentId = myId,
                data = mapOf("friends" to myFriends)
            )
            android.util.Log.d("REMOVE_DEBUG", "💾 Мой документ обновлён. Новый размер: ${myFriends.size}")

            AppwriteManager.databases.updateDocument(
                databaseId = DB_ID, collectionId = USERS_COL_ID, documentId = friendId,
                data = mapOf("friends" to friendFriends)
            )
            android.util.Log.d("REMOVE_DEBUG", "💾 Документ друга обновлён. Новый размер: ${friendFriends.size}")

            Result.success(true)

        } catch (e: Exception) {
            android.util.Log.e("REMOVE_DEBUG", "❌ КРИТИЧЕСКАЯ ОШИБКА УДАЛЕНИЯ", e)
            Result.failure(e)
        }
    }
}