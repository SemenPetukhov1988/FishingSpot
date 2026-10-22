package ru.semen.fishingspot.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FishingSpotDao {

    @Query("SELECT * FROM fishing_spots ORDER BY createdAt DESC")
    fun getAllSpots(): LiveData<List<FishingSpot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spot: FishingSpot): Long

    @Query("SELECT * FROM fishing_spots WHERE id = :id LIMIT 1")
    suspend fun getSpotById(id: Long): FishingSpot?

    // ✅ НОВОЕ: Ищем точку по серверному ID
    @Query("SELECT * FROM fishing_spots WHERE documentId = :documentId LIMIT 1")
    suspend fun getSpotByDocumentId(documentId: String): FishingSpot?

    // ✅ НОВОЕ: Удаляем точку по серверному ID (это гарантирует удаление всех дубликатов)


    // ✅ НОВОЕ: Очистка всей таблицы (для тестов)
    @Query("DELETE FROM fishing_spots")
    suspend fun clearAllSpots()

    // Удаляет все записи с таким documentId (защищает от дубликатов)
    @Query("DELETE FROM fishing_spots WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String): Int

    // Страховочный метод, если documentId вдруг пуст
    @Query("DELETE FROM fishing_spots WHERE id = :id")
    suspend fun deleteSpot(id: Long): Int
}