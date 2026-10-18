package ru.semen.fishingspot.repository

import android.content.Context
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role

import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class AuthRepository(private val context: Context) {

    // ЗАМЕНИ ЭТИ ЗНАЧЕНИЯ НА СВОИ ID!
    private val databaseId = "6ad5021a0029a2c88786"
    private val collectionId = "6ad5022b003770d2864c"

    suspend fun loginAnonymously(nickname: String): Result<String> {
        return try {
            // 1. Проверяем, занят ли никнейм (ищем документы с таким nickname)
            val queries = listOf(Query.equal("nickname", nickname))
            val existingUsers = AppwriteManager.databases.listDocuments(
                databaseId = databaseId,
                collectionId = collectionId,
                queries = queries
            )

            if (existingUsers.total > 0) {
                return Result.failure(Exception("Ник \"$nickname\" уже занят. Попробуйте другой."))
            }

            // 2. Создаем анонимную сессию в Appwrite
            AppwriteManager.account.createAnonymousSession()

            // 3. Получаем данные текущего пользователя, чтобы узнать его новый userId
            val user = AppwriteManager.account.get()
            val userId = user.id

            // 4. Создаем документ профиля в базе данных, используя userId как ID документа (как в Firebase)
            val userData = mapOf(
                "nickname" to nickname,
                "isProfileDeleted" to false
                // createdAt Appwrite добавит сам автоматически!
            )

            AppwriteManager.databases.createDocument(
                databaseId = databaseId,
                collectionId = collectionId,
                documentId = userId, // Важно: ID документа = ID пользователя
                data = userData,
                permissions = listOf(
                    Permission.read(Role.any()), // Читать могут все (для поиска друзей)
                    Permission.update(Role.user(userId)), // Обновлять только владелец
                    Permission.delete(Role.user(userId))  // Удалять только владелец
                )
            )

            // 5. Сохраняем сессию локально
            UserSessionManager.saveUserSession(context, userId, nickname)

            Result.success(userId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}