package ru.semen.fishingspot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.repository.FeedRepository

class FeedViewModel : ViewModel() {

    private val repository = FeedRepository()

    val spots: LiveData<List<FishingSpot>> = repository.spots

    fun loadFeed() {
        repository.loadPublicSpots()
    }
}