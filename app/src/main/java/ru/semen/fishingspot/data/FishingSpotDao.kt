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

    // ✅ Возвращаем Long (ID), чтобы потом связать с Firebase/Appwrite
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spot: FishingSpot): Long

    // ✅ НОВОЕ: Получить точку по ID для обновления её параметров
    @Query("SELECT * FROM fishing_spots WHERE id = :id LIMIT 1")
    suspend fun getSpotById(id: Long): FishingSpot?

    // ✅ НОВОЕ: Удалить точку по ID
    @Query("DELETE FROM fishing_spots WHERE id = :id")
    suspend fun deleteSpot(id: Long)
}