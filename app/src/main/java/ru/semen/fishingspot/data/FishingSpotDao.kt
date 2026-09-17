package ru.semen.fishingspot.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FishingSpotDao {

    // Получаем все точки, сортируя по времени создания (новые сверху)
    @Query("SELECT * FROM fishing_spots ORDER BY createdAt DESC")
    fun getAllSpots(): LiveData<List<FishingSpot>>

    // Вставляем новую точку
    @Insert
    suspend fun insert(spot: FishingSpot)
}