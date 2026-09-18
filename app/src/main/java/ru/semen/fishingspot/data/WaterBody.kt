package ru.semen.fishingspot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_bodies")
data class WaterBody(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val coordinatesJson: String // ✅ Храним контур для точной проверки
)