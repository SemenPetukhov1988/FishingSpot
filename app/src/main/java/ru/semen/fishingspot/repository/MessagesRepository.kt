package ru.semen.fishingspot.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class MessagesRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "MESSAGES_REPO"

    /**
     * Отправляет уведомление автору точки о новом отзыве.
     * Вызывается только если отзыв оставил НЕ сам автор точки.
     */
    suspend fun sendReviewNotification(
        recipientId: String,
        senderId: String,
        senderName: String,
        spotId: String,
        spotName: String,
        reviewText: String
    ): Result<Unit> {
        return try {
            val messageData = mapOf(
                "recipientId" to recipientId,
                "type" to MessageType.NEW_REVIEW.name,
                "senderId" to senderId,
                "senderName" to senderName,
                "spotId" to spotId,
                "spotName" to spotName,
                "contentText" to reviewText,
                "isRead" to false,
                "timestamp" to System.currentTimeMillis()
            )

            android.util.Log.d("DEBUG_SPOT", "📦 Данные для отправки в messages: $messageData")

            firestore.collection("messages")
                .document()
                .set(messageData)
                .await()

            android.util.Log.d("DEBUG_SPOT", "✅ Уведомление успешно записано в Firestore для пользователя $recipientId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("DEBUG_SPOT", "❌ Ошибка записи в Firestore (messages)", e)
            Result.failure(e)
        }
    }
}