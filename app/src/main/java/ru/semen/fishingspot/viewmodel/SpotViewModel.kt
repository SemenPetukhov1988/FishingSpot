package ru.semen.fishingspot.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.WaterChecker
import ru.semen.fishingspot.repository.FishingSpotRepository
import ru.semen.fishingspot.repository.SpotAppwriteRepository
import ru.semen.fishingspot.utils.UserSessionManager

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val repository: FishingSpotRepository = FishingSpotRepository(database.fishingSpotDao())
    private val appwriteRepository: SpotAppwriteRepository = SpotAppwriteRepository(application)
    private val waterChecker = WaterChecker()

    private val currentUserId: String =
        UserSessionManager.getCurrentUserId(application.applicationContext) ?: "unknown_user"

    private val _selectedTabId = MutableLiveData<Int>()
    val selectedTabId: LiveData<Int> = _selectedTabId

    fun setSelectedTab(tabId: Int) {
        _selectedTabId.value = tabId
    }

    enum class MapMode { MY_SPOTS, PUBLIC_SPOTS }

    private val _mapMode = MutableLiveData(MapMode.MY_SPOTS)
    val mapMode: LiveData<MapMode> = _mapMode

    private val _publicSpots = MutableLiveData<List<FishingSpot>>(emptyList())
    val localSpots: LiveData<List<FishingSpot>> = repository.allSpots

    val currentSpots: LiveData<List<FishingSpot>> = _mapMode.switchMap { mode ->
        if (mode == MapMode.MY_SPOTS) repository.allSpots else _publicSpots
    }

    init { switchToMySpots() }

    fun switchToMySpots() { _mapMode.value = MapMode.MY_SPOTS }

    fun switchToPublicSpots() {
        _mapMode.value = MapMode.PUBLIC_SPOTS
        loadPublicSpots()
    }

    private fun loadPublicSpots() {
        viewModelScope.launch {
            val result = appwriteRepository.getPublicSpots()
            _publicSpots.value = result.getOrNull() ?: emptyList()
            Log.d("VIEWMODEL_SPOT", "✅ Загружено публичных точек: ${_publicSpots.value?.size}")
        }
    }

    fun addFullSpot(
        lat: Double, lon: Double, name: String, description: String, weight: Double,
        visibility: String, photoPath: String?, fishType: String? = null, isVerified: Boolean = false,
        createdAt: Long = System.currentTimeMillis(), authorId: String? = null, authorName: String = "Рыбак"
    ) {
        viewModelScope.launch {
            val spot = FishingSpot(
                latitude = lat, longitude = lon, name = name, description = description,
                catchWeight = weight, fishType = fishType, photoPath = photoPath,
                isVerified = isVerified, visibility = visibility, isPublicOnMap = (visibility == "public"),
                createdAt = createdAt,
                authorId = authorId ?: currentUserId, // Если authorId передан (чужая точка), берем его. Иначе - текущий пользователь.
                authorName = authorName // ✅ Сохраняем оригинальное имя
            )

            repository.insert(spot)

            // Уведомляем друзей только если точку создал текущий пользователь (а не скопировал)
            if (authorId == null || authorId == currentUserId) {
                val context = getApplication<Application>().applicationContext
                val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"
                val friendsRepo = ru.semen.fishingspot.data.FriendsRepository(context)
                friendsRepo.notifyFriendsAboutNewSpot(spot, myName)
            }

            if (visibility != "private" || spot.isPublicOnMap) {
                val result = appwriteRepository.saveSpotToCloud(spot)
                if (result.isFailure) Log.e("VIEWMODEL_SPOT", "❌ Ошибка отправки в Appwrite", result.exceptionOrNull())
            }
        }
    }

    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)
        return waterChecker.isNearWater(lat, lon, candidates)
    }

    fun getDao() = database.fishingSpotDao()

    // =========================================================================
    // ✅ УПРАВЛЕНИЕ СУЩЕСТВУЮЩИМИ ТОЧКАМИ
    // =========================================================================

    fun updateSpotVisibility(spotId: Long, newVisibility: String) {
        viewModelScope.launch {
            val spot = repository.getSpotById(spotId) ?: return@launch
            val updatedSpot = spot.copy(visibility = newVisibility)
            repository.insert(updatedSpot)
            Log.d("VIEWMODEL_SPOT", "✅ Видимость точки $spotId обновлена локально на: $newVisibility")

            if (newVisibility != "private" || updatedSpot.isPublicOnMap) {
                appwriteRepository.saveSpotToCloud(updatedSpot)
            }
        }
    }

    fun updatePublicMapVisibility(spotId: Long, isPublicOnMap: Boolean) {
        viewModelScope.launch {
            val spot = repository.getSpotById(spotId) ?: return@launch
            val updatedSpot = spot.copy(isPublicOnMap = isPublicOnMap)
            repository.insert(updatedSpot)
            Log.d("VIEWMODEL_SPOT", "✅ Флаг публичной карты обновлен: $isPublicOnMap")

            if (isPublicOnMap) {
                appwriteRepository.saveSpotToCloud(updatedSpot)
            } else {
                appwriteRepository.deleteSpotFromCloud(updatedSpot.authorId, updatedSpot.createdAt)
            }
        }
    }

    fun deleteSpot(spotId: Long) {
        viewModelScope.launch {
            val spot = repository.getSpotById(spotId) ?: return@launch

            if (spot.visibility != "private" || spot.isPublicOnMap) {
                appwriteRepository.deleteSpotFromCloud(spot.authorId, spot.createdAt)
            }
            repository.deleteSpot(spotId)
            Log.d("VIEWMODEL_SPOT", "✅ Точка $spotId полностью удалена")
        }
    }
}