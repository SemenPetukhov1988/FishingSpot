package ru.semen.fishingspot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FishingSpot::class, WaterBody::class], version = 3) // Версия 3!
abstract class AppDatabase : RoomDatabase() {
    abstract fun fishingSpotDao(): FishingSpotDao
    abstract fun waterBodyDao(): WaterBodyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fishing_database"
                )
                    .fallbackToDestructiveMigration() // ✅ Сбрасывает базу при изменении структуры
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}