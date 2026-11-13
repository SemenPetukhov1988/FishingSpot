package ru.semen.fishingspot.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ru.semen.fishingspot.map.point.data.FishingSpot

class FeedViewModel : ViewModel() {

    private val repository = FeedRepository()

    val spots: LiveData<List<FishingSpot>> = repository.spots

    fun loadFeed() {
        repository.loadPublicSpots()
    }
}