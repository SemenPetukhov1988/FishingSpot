package ru.semen.fishingspot.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ru.semen.fishingspot.viewmodel.SpotViewModel

/**
 * ViewModel ТОЛЬКО для ленты.
 * Не трогает SpotViewModel, не знает про Room, карты и т.д.
 */
class FeedViewModel : ViewModel() {

    private val repository = FeedRepository()

    val spots: LiveData<List<SpotViewModel.PublicSpot>> = repository.spots

    fun startListening() {
        repository.startListening()
    }

    fun stopListening() {
        repository.stopListening()
    }

    // В будущем здесь появятся методы:
    // fun saveSpotToMyMap(spot: PublicSpot) { ... }
    // fun confirmSpot(spotId: String) { ... }
    // fun disputeSpot(spotId: String) { ... }
}