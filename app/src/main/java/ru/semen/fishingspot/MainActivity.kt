package ru.semen.fishingspot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.AppDatabase
import ru.semen.fishingspot.utils.DataImporter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            DataImporter.importWaterFromGeoJson(this@MainActivity, db.waterBodyDao())
        }
    }
}
