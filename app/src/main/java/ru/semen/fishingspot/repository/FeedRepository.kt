package ru.semen.fishingspot.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

import ru.semen.fishingspot.viewmodel.SpotViewModel

/**
 * Отвечает ТОЛЬКО за получение публичных точек из Firebase для ленты.
 * Ничего не знает про Room, карты и прочее.
 */
class FeedRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("public_spots")

    private val _spots = MutableLiveData<List<SpotViewModel.PublicSpot>>()
    val spots: LiveData<List<SpotViewModel.PublicSpot>> = _spots

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
                    // Временно можно убрать .orderBy() и сортировать вручную.
                    return@addSnapshotListener
                }

                val list = mutableListOf<SpotViewModel.PublicSpot>()
                for (doc in snapshot!!) {
                    val data = doc.data
                    val timestamp = (data["createdAt"] as? Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()

                    list.add(
                        SpotViewModel.PublicSpot(
                            id = doc.id,
                            latitude = (data["latitude"] as? Double) ?: 0.0,
                            longitude = (data["longitude"] as? Double) ?: 0.0,
                            name = (data["name"] as? String) ?: "Без названия",
                            description = (data["description"] as? String) ?: "",
                            catchWeight = (data["catchWeight"] as? Double) ?: 0.0,
                            isVerified = (data["isVerified"] as? Boolean) ?: false,
                            authorId = (data["authorId"] as? String) ?: "unknown",
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