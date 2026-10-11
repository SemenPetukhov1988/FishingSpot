package ru.semen.fishingspot.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FriendsRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "FriendsRepository"

    // Получаем ID текущего залогиненного пользователя
    private val currentUserId: String? get() = auth.currentUser?.uid

    /**
     * Слушаем список друзей в РЕАЛЬНОМ ВРЕМЕНИ.
     * Как только в базе что-то изменится, список обновится сам.
     */
    fun getFriendsFlow(): Flow<List<Friend>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            close(IllegalStateException("Пользователь не авторизован"))
            return@callbackFlow
        }

        // Подписываемся на подколлекцию "friends" внутри документа текущего пользователя
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

        // Отписываемся, когда Flow закрывается (например, при уходе с экрана)
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Поиск пользователей по имени (префиксный поиск Firestore)
     */
    /**
     * Поиск пользователей по имени (клиентская фильтрация, без префикса, без учёта регистра)
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
                        // Пропускаем самого себя
                        if (doc.id == userId) return@mapNotNull null

                        val nickname = doc.getString("nickname") ?: return@mapNotNull null

                        // Регистронезависимый поиск по вхождению (не только префикс!)
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
     * Добавляем пользователя в друзья (сохраняем в подколлекцию)
     */
    suspend fun addFriend(friendId: String, friendName: String): Result<Boolean> =
        suspendCancellableCoroutine { cont ->

            val userId = currentUserId
            if (userId == null) {
                cont.resume(Result.failure(Exception("Не авторизован")))
                return@suspendCancellableCoroutine
            }

            val friendData = hashMapOf(
                "id" to friendId,
                "name" to friendName,
                "addedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(userId)
                .collection("friends")
                .document(friendId) // ID друга становится ID документа
                .set(friendData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Друг добавлен в базу")
                    cont.resume(Result.success(true))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка добавления", e)
                    cont.resume(Result.failure(e))
                }
        }

    suspend fun sendSpotRequest(friendId: String, friendName: String, customMessage: String): Result<Boolean> =
        suspendCancellableCoroutine { cont ->

            val userId = currentUserId
            val userName = auth.currentUser?.displayName ?: "Рыбак"

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
                "timestamp" to System.currentTimeMillis() // ✅ Сохраняем как Long!
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
        spot: ru.semen.fishingspot.data.FishingSpot,
        originalMessageId: String
    ): Result<Boolean> = suspendCancellableCoroutine { cont ->

        val userId = currentUserId
        val userName = auth.currentUser?.displayName ?: "Рыбак"

        if (userId == null) {
            cont.resume(Result.failure(Exception("Не авторизован")))
            return@suspendCancellableCoroutine
        }

        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        // 1. Создаем новое сообщение типа SPOT_SHARED
        val newMessageRef = db.collection("messages").document()
        val sharedData = hashMapOf(
            "senderId" to userId,
            "senderName" to userName,
            "recipientId" to requesterId,
            "type" to "SPOT_SHARED",
            "spotId" to spot.id.toString(), // ID точки из Room
            "spotName" to spot.name,
            "spotLat" to spot.latitude,
            "spotLon" to spot.longitude,
            "contentText" to "Держи проверенное место!",
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        batch.set(newMessageRef, sharedData)

        // 2. Удаляем старый запрос (SPOT_REQUEST), чтобы он не висел в базе
        val oldRequestRef = db.collection("messages").document(originalMessageId)
        batch.delete(oldRequestRef)

        // 3. Выполняем всё атомарно
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
}