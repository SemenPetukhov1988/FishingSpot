package ru.semen.fishingspot.repository // ⚠️ Проверь, что пакет совпадает с реальной папкой файла!

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import ru.semen.fishingspot.data.FishingSpot // ✅ Импортируем нашу единую модель

/**
 * Отвечает за получение публичных точек для ленты.
 * Временно использует Firebase. Позже мы переключим его на Appwrite.
 */
class FeedRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("public_spots")

    // ✅ Используем FishingSpot вместо PublicSpot
    private val _spots = MutableLiveData<List<FishingSpot>>()
    val spots: LiveData<List<FishingSpot>> = _spots

    private var listener: ListenerRegistration? = null

    /**
     * Подписываемся на обновления коллекции в реальном времени.
     * Сортировка: новые сверху (по createdAt DESC).
     */
    fun startListening() {
        if (listener != null) return // защита от дублирования подписки

        listener = collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Если Firebase просит создать индекс — в логе будет ссылка.
                    return@addSnapshotListener
                }

                val list = mutableListOf<FishingSpot>()
                for (doc in snapshot!!) {
                    val data = doc.data
                    val timestamp = (data["createdAt"] as? Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()

                    list.add(
                        FishingSpot(
                            id = 0, // ID для Room (если точку сохранят локально, Room сгенерирует свой)
                            latitude = (data["latitude"] as? Double) ?: 0.0,
                            longitude = (data["longitude"] as? Double) ?: 0.0,
                            name = (data["name"] as? String) ?: "Без названия",
                            description = (data["description"] as? String) ?: "",
                            catchWeight = (data["catchWeight"] as? Double) ?: 0.0,
                            isVerified = (data["isVerified"] as? Boolean) ?: false,
                            authorId = (data["authorId"] as? String) ?: "unknown",
                            visibility = "public", // ✅ Явно указываем, что это публичная точка
                            createdAt = timestamp
                        )
                    )
                }
                _spots.value = list
            }
    }

    /**
     * Отписываемся — экономим батарею и трафик.
     */
    fun stopListening() {
        listener?.remove()
        listener = null
    }
}