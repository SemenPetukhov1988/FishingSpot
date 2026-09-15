package ru.semen

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // ⚠️ ЗАМЕНИ НА СВОЙ КЛЮЧ!
        MapKitFactory.setApiKey("1c671ffa-11f7-4fcc-b383-f607aee8c938")
        MapKitFactory.initialize(this)
    }
}