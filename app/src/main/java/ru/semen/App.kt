package ru.semen

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        // ✅ ПРАВИЛЬНЫЙ ПУТЬ ДЛЯ СОВРЕМЕННЫХ ANDROID (API 29+)
        // getExternalFilesDir(null) возвращает папку /Android/data/ru.semen.fishingspot/files
        // Если она вдруг недоступна (эмулятор), используем внутренний кэш (cacheDir)
        val externalDir = getExternalFilesDir(null) ?: cacheDir
        val osmdroidBasePath = File(externalDir, "osmdroid")

        if (!osmdroidBasePath.exists()) {
            osmdroidBasePath.mkdirs()
        }

        Configuration.getInstance().osmdroidBasePath = osmdroidBasePath
        Configuration.getInstance().osmdroidTileCache = File(osmdroidBasePath, "tiles")

        // Ограничиваем кэш 300 МБ (хватит на несколько районов Архангельской области)
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 300L * 1024L * 1024L
    }
}