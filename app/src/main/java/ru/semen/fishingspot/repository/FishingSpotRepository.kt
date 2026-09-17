package ru.semen.fishingspot.repository

import androidx.lifecycle.LiveData
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.FishingSpotDao

class FishingSpotRepository(private val dao: FishingSpotDao) {

    val allSpots: LiveData<List<FishingSpot>> = dao.getAllSpots()

    suspend fun insert(spot: FishingSpot) {
        dao.insert(spot)
    }
}