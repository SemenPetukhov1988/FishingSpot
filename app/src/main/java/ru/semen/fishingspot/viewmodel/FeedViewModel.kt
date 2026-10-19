package ru.semen.fishingspot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ru.semen.fishingspot.data.FishingSpot // ✅ Импортируем единую модель
import ru.semen.fishingspot.repository.FeedRepository

/**
 * ViewModel ТОЛЬКО для ленты.
 * Не трогает SpotViewModel, не знает про Room, карты и т.д.
 */
class FeedViewModel : ViewModel() {

    private val repository = FeedRepository()

    // ✅ ИСПРАВЛЕНО: используем FishingSpot вместо SpotViewModel.PublicSpot
    val spots: LiveData<List<FishingSpot>> = repository.spots

    fun startListening() {
        repository.startListening()
    }

    fun stopListening() {
        repository.stopListening()
    }

    // В будущем здесь появятся методы:
    // fun saveSpotToMyMap(spot: FishingSpot) { ... }
    // fun confirmSpot(spotId: String) { ... }
    // fun disputeSpot(spotId: String) { ... }
}