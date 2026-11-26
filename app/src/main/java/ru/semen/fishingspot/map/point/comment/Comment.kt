package ru.semen.fishingspot.map.point.comment

data class Comment(
    val id: String,
    val spotId: String,
    val parentId: String?,
    val userId: String,
    val userName: String,
    val text: String,
    val createdAt: Long
)