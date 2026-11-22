package ru.semen.fishingspot

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.launch
import ru.semen.fishingspot.utils.water.AppDatabase
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.utils.water.DataImporter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Твой существующий код импорта данных
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            DataImporter.importWaterSafe(this@MainActivity, db.waterBodyDao())
        }

        // === ТЕСТ ПОДКЛЮЧЕНИЯ К APPWRITE ===
        lifecycleScope.launch {
            try {
                val user = AppwriteManager.account.get()
                Log.d("APPWRITE_PING", "✅ УРА! Подключение работает. Пользователь: ${user.name}")
            } catch (e: AppwriteException) {
                if (e.code == 401) {
                    Log.d("APPWRITE_PING", "✅ СЕРВЕР ОТВЕЧАЕТ! (Код 401: Связь есть, просто мы еще не вошли в аккаунт)")
                } else {
                    Log.e("APPWRITE_PING", "❌ Ошибка Appwrite: ${e.message} (Код: ${e.code})")
                }
            } catch (e: Exception) {
                Log.e("APPWRITE_PING", " Ошибка сети или другая: ${e.message}")
            }
        }

        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.getCurrentUserId(this@MainActivity)
                if (!userId.isNullOrEmpty()) {
                    val userDoc = AppwriteManager.databases.getDocument(
                        databaseId = "6ad5021a0029a2c88786",
                        collectionId = "6ad5022b003770d2864c",
                        documentId = userId
                    )

                    // ✅ Данные документа — в поле data
                    val avatarUrl = userDoc.data["avatarUrl"] as? String
                    if (!avatarUrl.isNullOrEmpty()) {
                        ProfileCache.putAvatar(userId, avatarUrl)
                        Log.d("MAIN_ACTIVITY", "✅ Профиль загружен в кэш при старте")
                    } else {
                        Log.d("MAIN_ACTIVITY", "ℹ️ У пользователя нет аватарки")
                    }
                }
            } catch (e: Exception) {
                Log.e("MAIN_ACTIVITY", "❌ Ошибка загрузки профиля: ${e.message}")
            }
        }


    }
}