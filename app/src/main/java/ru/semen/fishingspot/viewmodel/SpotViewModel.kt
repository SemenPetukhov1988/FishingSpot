package ru.semen.fishingspot.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.WaterChecker
import ru.semen.fishingspot.repository.FishingSpotRepository
import ru.semen.fishingspot.utils.UserSessionManager

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FishingSpotRepository
    private val database: AppDatabase
    private val waterChecker = WaterChecker()

    val allSpots: LiveData<List<FishingSpot>>

    private val currentUserId: String =
        UserSessionManager.getCurrentUserId(application.applicationContext) ?: "unknown_user"

    // =========================================================================
    // МОДЕЛЬ И LIVE DATA ДЛЯ ПУБЛИЧНЫХ ТОЧЕК ИЗ FIREBASE
    // =========================================================================
    data class PublicSpot(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val description: String,
        val catchWeight: Double,
        val isVerified: Boolean,
        val authorId: String,
        val createdAt: Long
    )

    private val _publicSpots = MutableLiveData<List<PublicSpot>>()
    val publicSpots: LiveData<List<PublicSpot>> = _publicSpots
    private var publicSpotsListener: ListenerRegistration? = null

    // =========================================================================
    // УПРАВЛЕНИЕ ВЫБРАННОЙ ВКЛАДКОЙ (ДЛЯ СИНХРОНИЗАЦИИ НАВИГАЦИИ)
    // =========================================================================
    private val _selectedTabId = MutableLiveData<Int>()
    val selectedTabId: LiveData<Int> = _selectedTabId

    fun setSelectedTab(tabId: Int) {
        _selectedTabId.value = tabId
    }

    init {
        database = AppDatabase.getDatabase(application)
        val dao = database.fishingSpotDao()
        repository = FishingSpotRepository(dao)
        allSpots = repository.allSpots
    }

    fun addFullSpot(
        lat: Double, lon: Double, name: String, description: String, weight: Double,
        isPublic: Boolean, photoPath: String?, isVerified: Boolean = false
    ) {
        viewModelScope.launch {
            val spot = FishingSpot(
                latitude = lat, longitude = lon, name = name, description = description,
                catchWeight = weight, isPublic = isPublic, photoPath = photoPath,
                isVerified = isVerified, authorId = currentUserId
            )
            repository.insert(spot)
            if (isPublic) {
                uploadSpotToFirebase(spot)
            }
        }
    }

    private suspend fun uploadSpotToFirebase(spot: FishingSpot) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("public_spots").document()

            val spotData = hashMapOf(
                "serverId" to docRef.id,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "name" to spot.name,
                "description" to spot.description,
                "catchWeight" to spot.catchWeight,
                "photoPath" to spot.photoPath,
                "isVerified" to spot.isVerified,
                "authorId" to spot.authorId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(spotData).await()
            Log.d("FIREBASE_DEBUG", "✅ Точка отправлена. Verified: ${spot.isVerified}")
        } catch (e: Exception) {
            Log.e("FIREBASE_DEBUG", "❌ Ошибка отправки", e)
        }
    }

    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)
        return waterChecker.isNearWater(lat, lon, candidates)
    }

    fun getDao() = database.fishingSpotDao()

    fun startListeningPublicSpots() {
        publicSpotsListener = FirebaseFirestore.getInstance()
            .collection("public_spots")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FIREBASE_DEBUG", "Listen failed.", error)
                    return@addSnapshotListener
                }
                val spots = mutableListOf<PublicSpot>()
                for (doc in snapshot!!) {
                    val data = doc.data
                    val timestamp = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                    spots.add(PublicSpot(
                        id = doc.id,
                        latitude = (data["latitude"] as? Double) ?: 0.0,
                        longitude = (data["longitude"] as? Double) ?: 0.0,
                        name = (data["name"] as? String) ?: "Без названия",
                        description = (data["description"] as? String) ?: "",
                        catchWeight = (data["catchWeight"] as? Double) ?: 0.0,
                        isVerified = (data["isVerified"] as? Boolean) ?: false,
                        authorId = (data["authorId"] as? String) ?: "unknown",
                        createdAt = timestamp
                    ))
                }
                _publicSpots.value = spots
            }
    }

    fun stopListeningPublicSpots() {
        publicSpotsListener?.remove()
    }
}