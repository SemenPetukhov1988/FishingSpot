package ru.semen.fishingspot.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.semen.fishingspot.utils.UserSessionManager // ✅ Добавлен импорт
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ✅ Добавили Context в конструктор
class FriendsRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "FriendsRepository"

    // Получаем ID текущего залогиненного пользователя
    private val currentUserId: String? get() = auth.currentUser?.uid

    /**
     * Слушаем список друзей в РЕАЛЬНОМ ВРЕМЕНИ.
     */
    fun getFriendsFlow(): Flow<List<Friend>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            close(IllegalStateException("Пользователь не авторизован"))
            return@callbackFlow
        }

        val listenerRegistration = db.collection("users")
            .document(userId)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val friendsList = snapshot?.documents?.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val id = doc.id
                    Friend(id = id, name = name, status = "В друзьях")
                } ?: emptyList()

                trySend(friendsList)
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Поиск пользователей по имени (клиентская фильтрация)
     */
    suspend fun searchUsers(query: String): List<User> = suspendCancellableCoroutine { cont ->
        if (query.isBlank()) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val userId = currentUserId

        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                val queryLower = query.lowercase()

                val users = snapshot.documents
                    .mapNotNull { doc ->
                        if (doc.id == userId) return@mapNotNull null

                        val nickname = doc.getString("nickname") ?: return@mapNotNull null

                        if (nickname.lowercase().contains(queryLower)) {
                            User(id = doc.id, name = nickname, isAlreadyFriend = false)
                        } else null
                    }

                cont.resume(users)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Ошибка поиска", e)
                cont.resumeWithException(e)
            }
    }

    /**
     * Отправляет запрос в друзья (берём имя из UserSessionManager)
     */
    /**
     * Отправляет запрос в друзья (берём имя из UserSessionManager)
     */
    suspend fun sendFriendRequest(friendId: String, friendName: String): Result<Boolean> =
        suspendCancellableCoroutine { cont ->

            val userId = currentUserId
            // ✅ Берём имя и проверяем на пустоту
            val myName = UserSessionManager.getCurrentNickname(context)
            val finalName = if (myName.isNullOrBlank()) "Рыбак" else myName

            if (userId == null) {
                cont.resume(Result.failure(Exception("Не авторизован")))
                return@suspendCancellableCoroutine
            }

            val requestData = hashMapOf(
                "senderId" to userId,
                "senderName" to finalName, // ✅ Теперь точно не будет пустым!
                "recipientId" to friendId,
                "type" to "NEW_FRIEND",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )

            db.collection("messages")
                .add(requestData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Запрос в друзья отправлен")
                    cont.resume(Result.success(true))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка отправки запроса в друзья", e)
                    cont.resume(Result.failure(e))
                }
        }

    suspend fun sendSpotRequest(friendId: String, friendName: String, customMessage: String): Result<Boolean> =
        suspendCancellableCoroutine { cont ->

            val userId = currentUserId
            val userName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак" // ✅ Тоже поправил здесь для консистентности

            if (userId == null) {
                cont.resume(Result.failure(Exception("Не авторизован")))
                return@suspendCancellableCoroutine
            }

            val requestData = hashMapOf(
                "senderId" to userId,
                "senderName" to userName,
                "recipientId" to friendId,
                "type" to "SPOT_REQUEST",
                "customMessage" to customMessage,
                "contentText" to "",
                "isRead" to false,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("messages")
                .add(requestData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Запрос точки отправлен")
                    cont.resume(Result.success(true))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка отправки запроса", e)
                    cont.resume(Result.failure(e))
                }
        }

    /**
     * Отправляет точку другу и удаляет его исходный запрос (Batch operation)
     */
    suspend fun sendSharedSpot(
        requesterId: String,
        requesterName: String,
        spot: FishingSpot,
        originalMessageId: String
    ): Result<Boolean> = suspendCancellableCoroutine { cont ->

        val userId = currentUserId
        val userName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак" // ✅ И здесь тоже

        if (userId == null) {
            cont.resume(Result.failure(Exception("Не авторизован")))
            return@suspendCancellableCoroutine
        }

        val batch = db.batch()

        val newMessageRef = db.collection("messages").document()
        val sharedData = hashMapOf(
            "senderId" to userId,
            "senderName" to userName,
            "recipientId" to requesterId,
            "type" to "SPOT_SHARED",
            "spotId" to spot.id.toString(),
            "spotName" to spot.name,
            "spotLat" to spot.latitude,
            "spotLon" to spot.longitude,
            "contentText" to "Держи проверенное место!",
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        batch.set(newMessageRef, sharedData)

        val oldRequestRef = db.collection("messages").document(originalMessageId)
        batch.delete(oldRequestRef)

        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Точка отправлена, запрос удален")
                cont.resume(Result.success(true))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка отправки точки", e)
                cont.resume(Result.failure(e))
            }
    }

    /**
     * Принимает запрос в друзья: добавляет в список друзей и удаляет запрос из сообщений
     */
    /**
     * Принимает запрос в друзья: добавляет друзей ВЗАИМНО и удаляет запрос из сообщений
     */
    suspend fun acceptFriendRequest(friendId: String, friendName: String, messageId: String): Result<Boolean> =
        suspendCancellableCoroutine { cont ->

            val userId = currentUserId
            if (userId == null) {
                cont.resume(Result.failure(Exception("Не авторизован")))
                return@suspendCancellableCoroutine
            }

            // Получаем имя того, кто принимает заявку (Аннушка), чтобы записать его Семенушке
            val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

            val batch = db.batch()

            // 1. Добавляем отправителя (Семенушку) в друзья принимающему (Аннушке)
            val friendDataForMe = hashMapOf(
                "id" to friendId,
                "name" to friendName,
                "addedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val myFriendRef = db.collection("users").document(userId).collection("friends").document(friendId)
            batch.set(myFriendRef, friendDataForMe)

            // 2. ✅ НОВОЕ: Добавляем принимающего (Аннушку) в друзья отправителю (Семенушке)
            val friendDataForHim = hashMapOf(
                "id" to userId,
                "name" to myName,
                "addedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val hisFriendRef = db.collection("users").document(friendId).collection("friends").document(userId)
            batch.set(hisFriendRef, friendDataForHim)

            // 3. Удаляем сообщение-запрос
            val messageRef = db.collection("messages").document(messageId)
            batch.delete(messageRef)

            // 4. Выполняем всё атомарно (или всё получится, или ничего)
            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Друзья добавлены взаимно, запрос удален")
                    cont.resume(Result.success(true))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка принятия друга", e)
                    cont.resume(Result.failure(e))
                }
        }
}