package ru.semen.fishingspot.repository

import androidx.lifecycle.LiveData
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.FishingSpotDao

class FishingSpotRepository(private val dao: FishingSpotDao) {

    val allSpots: LiveData<List<FishingSpot>> = dao.getAllSpots()

    // ✅ Возвращаем Long, так как DAO теперь возвращает ID вставленной записи
    suspend fun insert(spot: FishingSpot): Long {
        return dao.insert(spot)
    }

    // ✅ НОВОЕ: Получение точки по ID (нужно для обновления видимости)
    suspend fun getSpotById(id: Long): FishingSpot? {
        return dao.getSpotById(id)
    }

    // ✅ НОВОЕ: Удаление точки по ID
    suspend fun deleteSpot(id: Long) {
        dao.deleteSpot(id)
    }


}