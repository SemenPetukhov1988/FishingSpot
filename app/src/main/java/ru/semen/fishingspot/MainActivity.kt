package ru.semen.fishingspot

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.DataImporter

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
        // ====================================
    }
}