package ru.semen.fishingspot.map.point.data

data class FishingSpot(
    val documentId: String,       // Единственный идентификатор из Appwrite
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String = "",
    val postText: String = "",
    val catchWeight: Double = 0.0,
    val fishType: String? = null,
    val photoPath: String? = null,
    val photoUrl: String? = null,
    val isVerified: Boolean = false,
    val authorId: String = "",
    val authorName: String = "Неизвестно",
    val visibility: String = "private",
    val isPublicOnMap: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)