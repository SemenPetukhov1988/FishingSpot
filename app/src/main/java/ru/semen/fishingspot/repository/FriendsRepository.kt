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
}