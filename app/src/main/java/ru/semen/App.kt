package ru.semen

import android.app.Application
import org.osmdroid.config.Configuration
import ru.semen.fishingspot.utils.AppwriteManager // <-- Не забудь этот импорт!
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Инициализация OSMDroid (твой отличный код)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        val externalDir = getExternalFilesDir(null) ?: cacheDir
        val osmdroidBasePath = File(externalDir, "osmdroid")

        if (!osmdroidBasePath.exists()) {
            osmdroidBasePath.mkdirs()
        }

        Configuration.getInstance().osmdroidBasePath = osmdroidBasePath
        Configuration.getInstance().osmdroidTileCache = File(osmdroidBasePath, "tiles")
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 300L * 1024L * 1024L

        // 2. Инициализация Appwrite (добавляем сюда)
        AppwriteManager.init(this)
    }
}