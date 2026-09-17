package ru.semen.fishingspot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fishing_spots")
data class FishingSpot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,       // Координаты (не в интерфейсе, но важны)
    val longitude: Double,      // Координаты
    val name: String,           // Название
    val description: String = "", // Описание
    val catchWeight: Double = 0.0, // Вес улова
    val photoPath: String? = null, // Путь к файлу фото на телефоне
    val isPublic: Boolean = false, // Публичная или приватная
    val createdAt: Long = System.currentTimeMillis() // Время создания
)