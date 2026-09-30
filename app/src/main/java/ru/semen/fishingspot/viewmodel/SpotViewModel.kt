package ru.semen.fishingspot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.WaterChecker
import ru.semen.fishingspot.repository.FishingSpotRepository
import ru.semen.fishingspot.utils.UserSessionManager // ✅ Добавлен импорт

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FishingSpotRepository
    private val database: AppDatabase
    private val waterChecker = WaterChecker()

    val allSpots: LiveData<List<FishingSpot>>

    // ✅ Получаем текущий UID пользователя один раз при создании ViewModel
    private val currentUserId: String =
        UserSessionManager.getCurrentUserId(application.applicationContext) ?: "unknown"

    init {
        database = AppDatabase.getDatabase(application)
        val dao = database.fishingSpotDao()
        repository = FishingSpotRepository(dao)
        // ✅ Запрос остался прежним! Локальная карта показывает ВСЕ точки, как и раньше
        allSpots = repository.allSpots
    }

    /**
     * ✅ Обновленный метод сохранения с поддержкой флага isVerified и authorId
     */
    fun addFullSpot(
        lat: Double,
        lon: Double,
        name: String,
        description: String,
        weight: Double,
        isPublic: Boolean,
        photoPath: String?,
        isVerified: Boolean = false
    ) {
        viewModelScope.launch {
            val spot = FishingSpot(
                latitude = lat,
                longitude = lon,
                name = name,
                description = description,
                catchWeight = weight,
                isPublic = isPublic,
                photoPath = photoPath,
                isVerified = isVerified,
                authorId = currentUserId // ✅ Автоматически привязываем UID создателя
            )
            repository.insert(spot)
        }
    }

    /**
     * Двухэтапная проверка воды
     */
    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)
        return waterChecker.isNearWater(lat, lon, candidates)
    }

    // Вспомогательный метод для доступа к DAO (если понадобится в фрагменте)
    fun getDao() = database.fishingSpotDao()
}