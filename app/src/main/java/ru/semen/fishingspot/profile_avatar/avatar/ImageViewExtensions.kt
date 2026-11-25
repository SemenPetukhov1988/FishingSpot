package ru.semen.fishingspot.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import ru.semen.fishingspot.R

fun ImageView.loadUserAvatar(userId: String?) {
    // ✅ Сначала проверяем, есть ли вообще ID
    if (userId.isNullOrEmpty()) {
        setImageResource(R.drawable.bg_avatar_shape)
        return
    }

    // ✅ Теперь userId гарантированно не null, можно смело передавать в кэш
    val url = ProfileCache.getAvatar(userId)

    if (url.isNullOrEmpty()) {
        setImageResource(R.drawable.bg_avatar_shape)
        return
    }

    Glide.with(this)
        .load(url)
        .transform(CircleCrop())
        .placeholder(R.drawable.bg_avatar_shape)
        .error(R.drawable.bg_avatar_shape)
        .into(this)
}