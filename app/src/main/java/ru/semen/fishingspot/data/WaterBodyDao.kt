package ru.semen.fishingspot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WaterBodyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(waterBodies: List<WaterBody>)

    @Query("SELECT COUNT(*) FROM water_bodies")
    suspend fun getCount(): Int

    @Query("DELETE FROM water_bodies")
    suspend fun clearAll()

    // ✅ Расширенный поиск: берем запас 0.005 градуса (~500м) вокруг точки
    @Query("""
        SELECT * FROM water_bodies 
        WHERE minLat <= :lat + 0.005 
        AND maxLat >= :lat - 0.005 
        AND minLon <= :lon + 0.005 
        AND maxLon >= :lon - 0.005
    """)
    suspend fun getNearbyWaterBodies(lat: Double, lon: Double): List<WaterBody>
}