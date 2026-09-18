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

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FishingSpotRepository
    private val database: AppDatabase
    private val waterChecker = WaterChecker() // ✅ Инициализируем checker
    val allSpots: LiveData<List<FishingSpot>>

    init {
        database = AppDatabase.getDatabase(application)
        val dao = database.fishingSpotDao()
        repository = FishingSpotRepository(dao)
        allSpots = repository.allSpots
    }

    fun addFullSpot(
        lat: Double,
        lon: Double,
        name: String,
        description: String,
        weight: Double,
        isPublic: Boolean,
        photoPath: String?
    ) {
        viewModelScope.launch {
            val spot = FishingSpot(
                latitude = lat,
                longitude = lon,
                name = name,
                description = description,
                catchWeight = weight,
                isPublic = isPublic,
                photoPath = photoPath
            )
            repository.insert(spot)
        }
    }

    /**
     * ✅ Двухэтапная проверка: сначала грубый поиск в базе, потом точная геометрия
     */
    suspend fun findNearbyWater(lat: Double, lon: Double): Boolean {
        // Шаг 1: Берем кандидатов из расширенного квадрата (~500м)
        val candidates = database.waterBodyDao().getNearbyWaterBodies(lat, lon)

        // Шаг 2: Проверяем точное расстояние до контура
        return waterChecker.isNearWater(lat, lon, candidates)
    }
}