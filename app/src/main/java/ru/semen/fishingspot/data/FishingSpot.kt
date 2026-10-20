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

    val fishType: String? = null,
    val photoPath: String? = null,
    val photoUrl: String? = null,

    val isVerified: Boolean = false,
    val authorId: String = "",
    val authorName: String = "Рыбак", // ✅ НОВОЕ ПОЛЕ для отображения ника
    val visibility: String = "private",
    val isPublicOnMap: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)