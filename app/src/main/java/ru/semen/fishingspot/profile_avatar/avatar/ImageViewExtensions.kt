package ru.semen.fishingspot.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

import ru.semen.fishingspot.R

/**
 * Загружает аватарку пользователя по ID.
 * Автоматически проверяет ProfileCache → Glide DiskCache → Placeholder.
 */
fun ImageView.loadUserAvatar(userId: String?) {
    if (userId.isNullOrEmpty()) {
        setImageResource(R.drawable.group_24dp_000000_fill0_wght400_grad0_opsz24)
        return
    }

    val avatarUrl = ProfileCache.getAvatar(userId)

    Glide.with(this)
        .load(avatarUrl) // Если null — Glide покажет placeholder
        .transform(CircleCrop())
        .placeholder(R.drawable.bg_avatar_shape)
        .error(R.drawable.bg_avatar_shape)
        .transition(DrawableTransitionOptions.withCrossFade(300))
        .into(this)
}