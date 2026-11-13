package ru.semen.fishingspot.messages.repository

import android.util.Log
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.semen.fishingspot.messages.data.FishingSignal
import ru.semen.fishingspot.utils.AppwriteManager
import java.time.Instant

class FishingSignalRepository {

    companion object {
        private const val DB_ID = "6ad5021a0029a2c88786"
        private const val NOTIFICATIONS_COL_ID = "6aef1b7300251aa8ce5b"
        private const val USERS_COL_ID = "6ad5022b003770d2864c"
        private const val MESSAGES_COL_ID = "6aeb4c1600142e707a69"
    }

    suspend fun sendSignal(
        senderId: String,
        senderName: String,
        receiverId: String,
        type: String,
        lat: Double = 0.0,
        lon: Double = 0.0,
        zoom: Double = 0.0,
        messageText: String? = null,
        anchorId: String = "" // Этот параметр используется только для ответов (fishing_wish)
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var finalAnchorId = anchorId

            // ✅ СОЗДАЕМ ЯКОРЬ ТОЛЬКО ДЛЯ on_fishing И ПОЛУЧАЕМ ЕГО ID
            if (type == "on_fishing") {
                try {
                    Log.d("ANCHOR_DEBUG", "🔨 Создаём якорь для $senderId")

                    val anchorData = mapOf(
                        "senderId" to senderId,
                        "senderName" to senderName,
                        "recipientId" to senderId,
                        "type" to "ANCHOR_ON_FISHING",
                        "contentText" to "Вы отправили сообщение ${if (receiverId == "all") "друзьям" else "другу"} что Вы на рыбалке",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to true
                    )

                    val anchorDoc = AppwriteManager.databases.createDocument(
                        databaseId = DB_ID,
                        collectionId = MESSAGES_COL_ID,
                        documentId = ID.Companion.unique(),
                        data = anchorData
                    )

                    // ✅ ЯВНО ПРИСВАИВАЕМ ID ИЗ ОТВЕТА СЕРВЕРА
                    finalAnchorId = anchorDoc.id
                    Log.d("ANCHOR_DEBUG", "✅ Якорь создан. ID=$finalAnchorId")

                } catch (e: Exception) {
                    Log.e("ANCHOR_DEBUG", "❌ Ошибка якоря: ${e.message}")
                    // Если якорь не создался, finalAnchorId останется пустым или переданным
                }
            }

            // ✅ ФОРМИРУЕМ ДАННЫЕ СИГНАЛА СТРОГО ПОСЛЕ СОЗДАНИЯ ЯКОРЯ
            val baseData = mutableMapOf<String, Any>(
                "senderId" to senderId,
                "senderName" to senderName,
                "receiverId" to receiverId,
                "type" to type,
                "lat" to lat,
                "lon" to lon,
                "zoom" to zoom,
                "isRead" to false,
                "createdAt" to Instant.now().toString(),
                "anchorId" to finalAnchorId // ✅ ГАРАНТИРОВАННО ПРАВИЛЬНЫЙ ID
            )

            if (!messageText.isNullOrEmpty()) {
                baseData["messageText"] = messageText
            }

            var successCount = 0

            if (receiverId == "all") {
                val userDoc = AppwriteManager.databases.getDocument(
                    databaseId = DB_ID,
                    collectionId = USERS_COL_ID,
                    documentId = senderId
                )
                val friendIds =
                    (userDoc.data["friends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                for (friendId in friendIds) {
                    try {
                        AppwriteManager.databases.createDocument(
                            databaseId = DB_ID,
                            collectionId = NOTIFICATIONS_COL_ID,
                            documentId = ID.Companion.unique(),
                            data = baseData + ("receiverId" to friendId)
                        )
                        successCount++
                    } catch (_: Exception) { /* Пропускаем ошибки */
                    }
                }
            } else {
                AppwriteManager.databases.createDocument(
                    databaseId = DB_ID,
                    collectionId = NOTIFICATIONS_COL_ID,
                    documentId = ID.Companion.unique(),
                    data = baseData
                )
                successCount = 1
            }

            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSignals(receiverId: String): Result<List<FishingSignal>> =
        withContext(Dispatchers.IO) {
            Log.d("REPO_SIGNALS", "📤 Запрос к Appwrite: receiverId='$receiverId'")

            try {
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = NOTIFICATIONS_COL_ID,
                    queries = listOf(
                        Query.Companion.equal("receiverId", receiverId),
                        Query.Companion.orderDesc("createdAt"),
                        Query.Companion.limit(100)
                    )
                )

                Log.d("REPO_SIGNALS", "📥 Ответ от Appwrite: ${result.documents.size} документов")

                val signalTypes =
                    setOf("on_fishing", "going_fishing", "fishing_wish", "fishing_reply")

                val signals = result.documents.mapNotNull { doc ->
                    val data = doc.data
                    val type = data["type"] as? String ?: return@mapNotNull null

                    if (type !in signalTypes) return@mapNotNull null

                    FishingSignal(
                        id = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "Рыбак",
                        receiverId = data["receiverId"] as? String ?: "",
                        type = type,
                        lat = (data["lat"] as? Number)?.toDouble() ?: 0.0,
                        lon = (data["lon"] as? Number)?.toDouble() ?: 0.0,
                        zoom = (data["zoom"] as? Number)?.toDouble() ?: 0.0,
                        messageText = data["messageText"] as? String,
                        anchorId = data["anchorId"] as? String ?: "",
                        isRead = data["isRead"] as? Boolean ?: false,
                        createdAt = parseTime(data["createdAt"])
                    )
                }

                Log.d("REPO_SIGNALS", "✅ Маппинг завершён: ${signals.size} FishingSignal объектов")
                Result.success(signals)

            } catch (e: Exception) {
                Log.e("REPO_SIGNALS", "❌ Исключение в репозитории: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun parseTime(ts: Any?): Long {
        return when (ts) {
            is Number -> ts.toLong()
            is String -> try { Instant.parse(ts).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
            else -> System.currentTimeMillis()
        }
    }
}