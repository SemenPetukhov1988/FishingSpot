package ru.semen.fishingspot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {

    /**
     * Сжимает изображение по URI до заданного размера и качества
     * @return ByteArray готовый к отправке в Appwrite
     */
    fun compressImage(context: Context, uri: Uri?, maxSizePx: Int = 1024, quality: Int = 80): ByteArray? {
        if (uri == null) return null

        return try {
            // 1. Декодируем только размеры (без загрузки всего битмапа в память)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // 2. Вычисляем коэффициент сжатия
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxSizePx || options.outHeight / sampleSize > maxSizePx) {
                sampleSize *= 2
            }

            // 3. Декодируем уже сжатым
            val bitmapOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bitmapOptions)
            } ?: return null

            // 4. Конвертируем в JPEG с нужным качеством
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}