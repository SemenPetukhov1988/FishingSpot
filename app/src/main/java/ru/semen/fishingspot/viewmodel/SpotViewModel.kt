package ru.semen.fishingspot.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap // ✅ Магический импорт
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.FriendsRepository
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

    // Инициализируем пустым списком, чтобы НИКОГДА не было null
    private val _publicSpots = MutableLiveData<List<FishingSpot>>(emptyList())

    val localSpots: LiveData<List<FishingSpot>> = repository.allSpots

    // ✅ МАГИЯ switchMap: автоматически переключает источник и берет его ТЕКУЩЕЕ значение!
    val currentSpots: LiveData<List<FishingSpot>> = _mapMode.switchMap { mode ->
        if (mode == MapMode.MY_SPOTS) {
            repository.allSpots
        } else {
            _publicSpots
        }
    }

    init {
        switchToMySpots()
    }

    fun switchToMySpots() {
        _mapMode.value = MapMode.MY_SPOTS
    }

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
        isPublic: Boolean, photoPath: String?, fishType: String? = null, isVerified: Boolean = false,
        authorId: String? = null
    ) {
        viewModelScope.launch {
            val visibility = if (isPublic) "public" else "private"

            val spot = FishingSpot(
                latitude = lat, longitude = lon, name = name, description = description,
                catchWeight = weight, fishType = fishType, photoPath = photoPath,
                isVerified = isVerified, visibility = visibility,
                authorId = authorId ?: currentUserId
            )

            // 1. Сохраняем в Room (это автоматически обновит repository.allSpots,
            // и switchMap мгновенно передаст новое значение во фрагмент, если мы в режиме MY_SPOTS)
            repository.insert(spot)

            // 2. Уведомляем друзей
            val context = getApplication<Application>().applicationContext
            val myName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"
            val friendsRepo = FriendsRepository(context)
            friendsRepo.notifyFriendsAboutNewSpot(spot, myName)

            // 3. Если точка публичная, отправляем в облако
            if (visibility == "public") {
                val result = appwriteRepository.saveSpotToCloud(spot)
                if (result.isFailure) {
                    Log.e("VIEWMODEL_SPOT", "❌ Ошибка отправки в Appwrite", result.exceptionOrNull())
                }
            }
        }
    }

    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)
        return waterChecker.isNearWater(lat, lon, candidates)
    }

    fun getDao() = database.fishingSpotDao()
}