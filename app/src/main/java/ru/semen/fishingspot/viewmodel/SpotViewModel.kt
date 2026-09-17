package ru.semen.fishingspot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.repository.FishingSpotRepository


class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FishingSpotRepository
    val allSpots: LiveData<List<FishingSpot>>

    init {
        // Инициализируем репозиторий через базу данных приложения
        val dao = AppDatabase.getDatabase(application).fishingSpotDao()
        repository = FishingSpotRepository(dao)
        allSpots = repository.allSpots
    }

    /**
     * Сохраняет полноценную точку с уточненными координатами в базу данных.
     * Выполняется в корутине (в фоне), чтобы не блокировать UI.
     */
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
}