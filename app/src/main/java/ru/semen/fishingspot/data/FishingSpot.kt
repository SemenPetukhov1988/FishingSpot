package ru.semen.fishingspot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fishing_spots")
data class FishingSpot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String = "",
    val catchWeight: Double = 0.0,
    val photoPath: String? = null,
    val isPublic: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // ✅ Новое поле для разделения проверенных и сомнительных точек
    // По умолчанию false (сомнительная), при успешной проверке воды ставим true
    val isVerified: Boolean = false
)