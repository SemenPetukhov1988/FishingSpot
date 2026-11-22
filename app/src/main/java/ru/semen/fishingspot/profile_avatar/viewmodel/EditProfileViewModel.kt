package ru.semen.fishingspot.profile_avatar

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.appwrite.ID
import io.appwrite.models.InputFile
import kotlinx.coroutines.launch
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache
import ru.semen.fishingspot.utils.UserSessionManager
import java.io.File

class EditProfileViewModel : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _uploadResult = MutableLiveData<Result<String>>()
    val uploadResult: LiveData<Result<String>> = _uploadResult

    private val DATABASE_ID = "6ad5021a0029a2c88786"
    private val USERS_COLLECTION_ID = "6ad5022b003770d2864c"
    private val AVATAR_BUCKET_ID = "6b0353a500082a09fc15"
    private val PROJECT_ID = "6acfd94b00107a8657d8"
    private val SERVER_BASE_URL = "http://153.76.168.237:8080"

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("AVATAR_UPLOAD", "🚀 Начало загрузки аватарки")

            try {
                val userId = UserSessionManager.getCurrentUserId(context)
                    ?: throw IllegalStateException("User ID not found")

                Log.d("AVATAR_UPLOAD", "👤 UserID: $userId")

                // ✅ 1. ФИКСИРОВАННЫЙ ID: один файл на пользователя, замена вместо накопления
                val fileId = "avatar_$userId"

                // 2. Копируем URI во временный файл
                val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
                val tempFile = File(context.cacheDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("AVATAR_UPLOAD", "📁 Временный файл создан: ${tempFile.absolutePath}, размер: ${tempFile.length()} байт")

                // 3. Удаляем СТАРЫЙ файл с этим же ID (если есть) — осознанная замена
                try {
                    AppwriteManager.storage.deleteFile(
                        bucketId = AVATAR_BUCKET_ID,
                        fileId = fileId
                    )
                    Log.d("AVATAR_UPLOAD", "🗑️ Старый файл удалён")
                } catch (e: Exception) {
                    Log.d("AVATAR_UPLOAD", "ℹ️ Старого файла нет (или не удалось удалить): ${e.message}")
                    // Это нормальный путь для первой загрузки — не прерываем
                }

                // 4. Загружаем НОВЫЙ файл под тем же ID
                Log.d("AVATAR_UPLOAD", "☁️ Отправка файла в Storage...")
                val storageResponse = AppwriteManager.storage.createFile(
                    bucketId = AVATAR_BUCKET_ID,
                    fileId = fileId,
                    file = InputFile.fromFile(tempFile)
                )
                Log.d("AVATAR_UPLOAD", "✅ Файл загружен! FileID: ${storageResponse.id}")

                // 5. Собираем URL. ?v= — защита от кэша: URL прежний, картинка новая
                val avatarUrl = "$SERVER_BASE_URL/v1/storage/buckets/$AVATAR_BUCKET_ID/files/$fileId/preview?project=$PROJECT_ID&width=300&height=300&quality=80&v=${System.currentTimeMillis()}"

                // 6. Обновляем документ: теперь достаточно одного поля avatarUrl
                AppwriteManager.databases.updateDocument(
                    databaseId = DATABASE_ID,
                    collectionId = USERS_COLLECTION_ID,
                    documentId = userId,
                    data = mapOf("avatarUrl" to avatarUrl)
                )
                Log.d("AVATAR_UPLOAD", "📝 Документ обновлён")

                ProfileCache.putAvatar(userId, avatarUrl)
                _uploadResult.value = Result.success(avatarUrl)

                if (tempFile.exists()) tempFile.delete()

            } catch (e: Exception) {
                Log.e("AVATAR_UPLOAD", "❌ ОШИБКА ЗАГРУЗКИ: ${e.message}", e)
                _uploadResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
                Log.d("AVATAR_UPLOAD", "🏁 Загрузка завершена")
            }
        }
    }
}