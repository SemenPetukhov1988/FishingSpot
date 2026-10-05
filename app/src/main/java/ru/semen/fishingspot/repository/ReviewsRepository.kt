package ru.semen.fishingspot.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Репозиторий для работы с отзывами к публичным точкам.
 * Все отзывы хранятся в Firebase Firestore в подколлекции public_spots/{spotId}/reviews
 */
class ReviewsRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "REVIEWS_REPO"

    /**
     * Добавляет новый отзыв к точке.
     * @param spotId ID точки в Firebase (из коллекции public_spots)
     * @param review Объект отзыва (без id, он сгенерируется автоматически)
     */
    suspend fun addReview(spotId: String, review: Review): Result<Unit> {
        return try {
            val reviewsCollection = firestore.collection("public_spots")
                .document(spotId)
                .collection("reviews")

            // Генерируем новый документ с автоматическим ID
            val newDocRef = reviewsCollection.document()
            val reviewWithId = review.copy(id = newDocRef.id)

            newDocRef.set(reviewWithId).await()
            Log.d(TAG, "✅ Отзыв добавлен к точке $spotId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка добавления отзыва", e)
            Result.failure(e)
        }
    }

    /**
     * Получает все отзывы для точки, отсортированные по дате (новые сначала).
     * @param spotId ID точки в Firebase
     * @return Список отзывов
     */
    suspend fun getReviews(spotId: String): Result<List<Review>> {
        return try {
            val snapshot = firestore.collection("public_spots")
                .document(spotId)
                .collection("reviews")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val reviews = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Review::class.java)?.copy(id = doc.id)
            }

            Log.d(TAG, "✅ Загружено ${reviews.size} отзывов для точки $spotId")
            Result.success(reviews)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки отзывов", e)
            Result.failure(e)
        }
    }

    /**
     * Подписывается на изменения отзывов в реальном времени.
     * @param spotId ID точки в Firebase
     * @param onReviewsChanged Callback, который вызывается при каждом изменении списка отзывов
     * @return ListenerRegistration, который нужно отменить в onDestroyView
     */
    fun subscribeToReviews(
        spotId: String,
        onReviewsChanged: (List<Review>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("public_spots")
            .document(spotId)
            .collection("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Ошибка подписки на отзывы", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val reviews = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Review::class.java)?.copy(id = doc.id)
                    }
                    onReviewsChanged(reviews)
                }
            }
    }

    /**
     * Проверяет, оставлял ли пользователь уже отзыв к этой точке.
     * @param spotId ID точки в Firebase
     * @param userId ID пользователя
     * @return true, если отзыв уже есть
     */
    suspend fun hasUserReviewed(spotId: String, userId: String): Result<Boolean> {
        return try {
            val snapshot = firestore.collection("public_spots")
                .document(spotId)
                .collection("reviews")
                .whereEqualTo("authorId", userId)
                .limit(1)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки отзыва", e)
            Result.failure(e)
        }
    }
}