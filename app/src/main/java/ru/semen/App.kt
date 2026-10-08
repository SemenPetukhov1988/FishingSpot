package ru.semen

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ ИНИЦИАЛИЗАЦИЯ OSMDROID
        // 1. Загружаем конфигурацию (включает кэширование тайлов на устройство)
        // Используем getSharedPreferences, так как мы в классе Application
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )

        // 2. Обязательно задаем User-Agent.
        // Серверы OpenStreetMap требуют это, иначе они заблокируют запросы на получение карты.
        Configuration.getInstance().userAgentValue = packageName
    }
}