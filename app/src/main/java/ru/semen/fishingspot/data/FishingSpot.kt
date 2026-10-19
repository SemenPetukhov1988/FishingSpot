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

    // ✅ НОВЫЕ ПОЛЯ
    val fishType: String? = null,       // Вид рыбы (окунь, щука и т.д.)
    val photoPath: String? = null,      // Локальный путь к фото (для оффлайна)
    val photoUrl: String? = null,       // Ссылка на фото в Appwrite Storage
    val isVerified: Boolean = false,
    val authorId: String = "",
    val visibility: String = "private", // "private", "friends", "public"
    val createdAt: Long = System.currentTimeMillis()
) {
    // Удобное свойство для быстрой проверки
    val isPublic: Boolean get() = visibility == "public"
}