package ru.semen.fishingspot // ✅ Проверь, что пакет совпадает с твоим

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.ActivityMainBinding
import ru.semen.fishingspot.fragment.MainTabsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ViewBinding для активити
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Запускаем наш MainTabsFragment только при первом запуске
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MainTabsFragment())
                .commit()
        }
    }
}