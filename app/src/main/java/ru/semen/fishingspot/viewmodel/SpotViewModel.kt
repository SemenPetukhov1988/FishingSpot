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
import ru.semen.fishingspot.repository.SavedSpotsRepository
import ru.semen.fishingspot.repository.SpotAppwriteRepository
import ru.semen.fishingspot.utils.NetworkMonitor
import ru.semen.fishingspot.utils.UserSessionManager

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val server = SpotAppwriteRepository(application)
    private val favorites = SavedSpotsRepository(application)

    // Room только для справочника водоемов. Точки отсюда НЕ читаются и НЕ пишутся.
    private val database = AppDatabase.getDatabase(application)
    private val waterChecker = WaterChecker()

    private val networkMonitor = NetworkMonitor.getInstance(application)
    private val userId = UserSessionManager.getCurrentUserId(application) ?: ""
    private val userName = UserSessionManager.getCurrentNickname(application) ?: "Неизвестно"

    enum class MapMode { MY_SPOTS, PUBLIC_SPOTS }

    private val _mapMode = MutableLiveData(MapMode.MY_SPOTS)
    val mapMode: LiveData<MapMode> = _mapMode

    private val _selectedTabId = MutableLiveData<Int>()
    val selectedTabId: LiveData<Int> = _selectedTabId
    fun setSelectedTab(tabId: Int) { _selectedTabId.value = tabId }

    // Единственные списки точек в приложении — серверные, в памяти
    private val _mySpots = MutableLiveData<List<FishingSpot>>(emptyList())
    val mySpots: LiveData<List<FishingSpot>> = _mySpots

    // Совместимость со старыми вызовами (FeedFragment и др.)
    val localSpots: LiveData<List<FishingSpot>> = _mySpots

    private val _publicSpots = MutableLiveData<List<FishingSpot>>(emptyList())
    val publicSpots: LiveData<List<FishingSpot>> = _publicSpots

    val currentSpots: LiveData<List<FishingSpot>> = _mapMode.switchMap { mode ->
        if (mode == MapMode.MY_SPOTS) _mySpots else _publicSpots
    }

    private val _currentSpotDetails = MutableLiveData<FishingSpot?>()
    val currentSpotDetails: LiveData<FishingSpot?> = _currentSpotDetails

    private val _isSpotSaved = MutableLiveData(false)
    val isSpotSaved: LiveData<Boolean> = _isSpotSaved

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadMySpots()
    }

    private fun hasNetwork(): Boolean {
        if (networkMonitor.value == true) return true
        _errorMessage.value = "Нет подключения к интернету"
        _isLoading.value = false
        return false
    }

    // ================= ЗАГРУЗКА =================

    fun switchToMySpots() {
        _mapMode.value = MapMode.MY_SPOTS
        loadMySpots()
    }

    fun switchToPublicSpots() {
        _mapMode.value = MapMode.PUBLIC_SPOTS
        loadPublicSpots()
    }

    fun refreshMap() {
        if (_mapMode.value == MapMode.PUBLIC_SPOTS) loadPublicSpots() else loadMySpots()
    }

    fun loadMySpots() {
        viewModelScope.launch {
            if (userId.isEmpty()) {
                _errorMessage.value = "Пользователь не авторизован"
                return@launch
            }
            if (!hasNetwork()) return@launch
            _isLoading.value = true
            val result = server.getMySpots(userId)
            _isLoading.value = false
            if (result.isSuccess) {
                _mySpots.value = result.getOrNull() ?: emptyList()
                _errorMessage.value = null
            } else {
                _mySpots.value = emptyList()
                _errorMessage.value = "Не удалось загрузить точки: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun loadPublicSpots() {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch
            _isLoading.value = true
            val result = server.getPublicSpots()
            _isLoading.value = false
            if (result.isSuccess) {
                _publicSpots.value = result.getOrNull() ?: emptyList()
                _errorMessage.value = null
            } else {
                _publicSpots.value = emptyList()
                _errorMessage.value = "Не удалось загрузить публичные точки"
            }
        }
    }

    fun loadSpotDetails(documentId: String) {
        viewModelScope.launch {
            if (documentId.isBlank()) {
                _errorMessage.value = "Не передан ID точки"
                return@launch
            }
            if (!hasNetwork()) return@launch
            _isLoading.value = true
            val result = server.getSpotByDocumentId(documentId)
            _isLoading.value = false
            if (result.isSuccess) {
                val spot = result.getOrNull() ?: return@launch
                _currentSpotDetails.value = spot
                _errorMessage.value = null
                upsertInLists(spot)
                checkIfSpotSaved(spot.documentId)
            } else {
                _errorMessage.value = "Точка не найдена на сервере"
            }
        }
    }

    // ================= ОБНОВЛЕНИЕ СПИСКОВ В ПАМЯТИ =================
    // Это не кэш: просто состояние UI, чтобы карта перерисовалась сразу.

    private fun upsertInLists(spot: FishingSpot) {
        val my = (_mySpots.value ?: emptyList()).filter { it.documentId != spot.documentId }
        _mySpots.value = listOf(spot) + my

        val pub = (_publicSpots.value ?: emptyList()).filter { it.documentId != spot.documentId }
        _publicSpots.value = if (spot.visibility == "public") listOf(spot) + pub else pub
    }

    private fun removeFromLists(documentId: String) {
        _mySpots.value = (_mySpots.value ?: emptyList()).filter { it.documentId != documentId }
        _publicSpots.value = (_publicSpots.value ?: emptyList()).filter { it.documentId != documentId }
    }

    // ================= СОЗДАНИЕ =================

    fun addFullSpot(
        lat: Double, lon: Double, name: String, description: String, weight: Double,
        visibility: String, photoPath: String?, photoUrl: String?, fishType: String? = null,
        isVerified: Boolean = false, createdAt: Long = System.currentTimeMillis(),
        authorId: String? = null, authorName: String? = null
    ) {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch

            val finalAuthorId = authorId?.takeIf { it.isNotEmpty() && it != "unknown_user" } ?: userId
            val finalAuthorName = if (finalAuthorId == userId) {
                userName
            } else {
                authorName?.takeIf { it.isNotEmpty() && it != "Рыбак" } ?: "Неизвестно"
            }

            val spot = FishingSpot(
                id = 0,
                documentId = "",
                latitude = lat,
                longitude = lon,
                name = name,
                description = description,
                catchWeight = weight,
                fishType = fishType,
                photoPath = photoPath,
                photoUrl = photoUrl,
                isVerified = isVerified,
                visibility = visibility,
                isPublicOnMap = visibility == "public",
                createdAt = createdAt,
                authorId = finalAuthorId,
                authorName = finalAuthorName
            )

            val result = server.createSpot(spot)
            if (result.isSuccess) {
                val created = result.getOrNull() ?: return@launch
                upsertInLists(created)
                _errorMessage.value = null
                Log.d("SPOT_SERVER", "✅ создана и добавлена в списки: ${created.documentId}")
            } else {
                _errorMessage.value = "Ошибка сохранения: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // ================= ВИДИМОСТЬ =================

    fun updateSpotVisibility(documentId: String, newVisibility: String) {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch
            val result = server.updateSpotVisibility(documentId, newVisibility)
            if (result.isSuccess) {
                val current = _currentSpotDetails.value
                if (current != null && current.documentId == documentId) {
                    val updated = current.copy(
                        visibility = newVisibility,
                        isPublicOnMap = newVisibility == "public"
                    )
                    _currentSpotDetails.value = updated
                    upsertInLists(updated)
                } else {
                    refreshMap()
                }
                _errorMessage.value = null
                Log.d("SPOT_SERVER", "✅ видимость изменена: $documentId -> $newVisibility")
            } else {
                _errorMessage.value = "Ошибка изменения видимости: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // ================= УДАЛЕНИЕ =================

    fun deleteSpot(documentId: String) {
        viewModelScope.launch {
            Log.d("SPOT_SERVER", "🗑️ удаление documentId=$documentId")
            if (!hasNetwork()) return@launch

            val result = server.deleteSpotByDocumentId(documentId)
            if (result.isSuccess) {
                removeFromLists(documentId)
                _errorMessage.value = null
                Log.d("SPOT_SERVER", "✅ удалена с сервера и убрана из списков")
            } else {
                _errorMessage.value = "Ошибка удаления: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // ================= ИЗБРАННОЕ (тоже сервер) =================

    fun checkIfSpotSaved(documentId: String) {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch
            val result = favorites.isSpotSaved(userId, documentId)
            _isSpotSaved.value = result.getOrNull() ?: false
        }
    }

    fun saveToFavorites(spot: FishingSpot) {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch
            if (spot.documentId.isEmpty()) {
                _errorMessage.value = "У точки нет серверного ID"
                return@launch
            }

            // ✅ ТЕПЕРЬ ПЕРЕДАЕМ ВЕСЬ ОБЪЕКТ SPOT
            val result = favorites.saveSpot(userId = userId, spot = spot)

            if (result.isSuccess) {
                _isSpotSaved.value = true
                _errorMessage.value = null
                Log.d("SPOT_SERVER", "✅ Добавлено в избранное: ${spot.documentId}")
            } else {
                _errorMessage.value = "Ошибка добавления в избранное: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun unsaveFromFavorites(spot: FishingSpot) {
        viewModelScope.launch {
            if (!hasNetwork()) return@launch
            val result = favorites.unsaveSpot(userId, spot.documentId)
            if (result.isSuccess) {
                _isSpotSaved.value = false
                _errorMessage.value = null
            } else {
                _errorMessage.value = "Ошибка удаления из избранного"
            }
        }
    }

    // ================= ВОДОЕМЫ (Room, отдельная задача) =================

    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)
        return waterChecker.isNearWater(lat, lon, candidates)
    }
}