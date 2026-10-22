package ru.semen.fishingspot.repository

import androidx.lifecycle.LiveData
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.data.FishingSpotDao

class FishingSpotRepository(private val dao: FishingSpotDao) {

    val allSpots: LiveData<List<FishingSpot>> = dao.getAllSpots()

    // ✅ УМНАЯ ВСТАВКА: предотвращает дубликаты
    suspend fun insertOrUpdate(spot: FishingSpot): Long {
        if (spot.documentId.isNotEmpty()) {
            // Если точка с таким documentId уже есть, удаляем её перед вставкой новой
            dao.deleteByDocumentId(spot.documentId)
        }
        return dao.insert(spot)
    }

    suspend fun getSpotById(id: Long): FishingSpot? = dao.getSpotById(id)

    // ✅ УДАЛЕНИЕ ПО СЕРВЕРНОМУ ID (чище и надежнее)
    suspend fun deleteSpotByDocumentId(documentId: String): Int {
        return dao.deleteByDocumentId(documentId)
    }

    suspend fun clearAllSpots() = dao.clearAllSpots()


    suspend fun deleteSpotById(id: Long): Int {
        return dao.deleteSpot(id)
    }
}