package ru.semen.fishingspot.messages.notifikation

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
        anchorId: String = "",
        plannedDate: Long = 0,
        requestComment: String? = null
    ): Result<Pair<Int, String?>> = withContext(Dispatchers.IO) {
        Log.d("SEND_SIGNAL_DEBUG", "📥 Получены параметры: type='$type', sender='$senderName', receiver='$receiverId'")
        try {
            var finalAnchorId = anchorId

            if (type == "on_fishing" || type == "going_fishing" || type == "request_spot") {
                try {
                    val anchorType = when (type) {
                        "on_fishing" -> "ANCHOR_ON_FISHING"
                        "going_fishing" -> "ANCHOR_GOING_FISHING"
                        else -> "ANCHOR_SPOT_REQUEST"
                    }

                    val anchorText = when (type) {
                        "on_fishing" -> "Вы отправили сообщение ${if (receiverId == "all") "друзьям" else "другу"}, что Вы на рыбалке"
                        "going_fishing" -> "Запрос отправлен ${if (receiverId == "all") "друзьям" else "другу"}: стоит ли ехать на рыбалку?"
                        else -> "Просьба порекомендовать место для рыбалки отправлена ${if (receiverId == "all") "друзьям" else "другу"}"
                    }

                    Log.d("ANCHOR_DEBUG", "🔨 Создаём якорь [$anchorType] для $senderId")

                    val anchorData = mapOf(
                        "senderId" to senderId,
                        "senderName" to senderName,
                        "recipientId" to senderId,
                        "type" to anchorType,
                        "contentText" to anchorText,
                        "spotLat" to lat,
                        "spotLon" to lon,
                        "plannedDate" to plannedDate,
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to true,
                        "requestComment" to (if (type == "request_spot") requestComment.orEmpty() else "")
                    )

                    val anchorDoc = AppwriteManager.databases.createDocument(
                        databaseId = DB_ID,
                        collectionId = MESSAGES_COL_ID,
                        documentId = ID.unique(),
                        data = anchorData
                    )

                    finalAnchorId = anchorDoc.id
                    Log.d("ANCHOR_DEBUG", "✅ Якорь создан. ID=$finalAnchorId")

                } catch (e: Exception) {
                    Log.e("ANCHOR_DEBUG", "❌ Ошибка якоря: ${e.message}")
                }
            }

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
                "anchorId" to finalAnchorId,
                "plannedDate" to plannedDate
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
                val friendIds = (userDoc.data["friends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                for (friendId in friendIds) {
                    try {
                        AppwriteManager.databases.createDocument(
                            databaseId = DB_ID,
                            collectionId = NOTIFICATIONS_COL_ID,
                            documentId = ID.unique(),
                            data = baseData + ("receiverId" to friendId)
                        )
                        successCount++
                    } catch (_: Exception) { /* Пропускаем ошибки */ }
                }
            } else {
                AppwriteManager.databases.createDocument(
                    databaseId = DB_ID,
                    collectionId = NOTIFICATIONS_COL_ID,
                    documentId = ID.unique(),
                    data = baseData
                )
                successCount = 1
            }

            Result.success(Pair(successCount, finalAnchorId))
        } catch (e: Exception) {
            Log.e("SEND_SIGNAL_DEBUG", "❌ Критическая ошибка: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSignals(receiverId: String): Result<List<FishingSignal>> = withContext(Dispatchers.IO) {
        Log.d("SIGNALS_DEBUG", "📤 Запрос к Appwrite: receiverId='$receiverId'")
        try {
            val result = AppwriteManager.databases.listDocuments(
                databaseId = DB_ID,
                collectionId = NOTIFICATIONS_COL_ID,
                queries = listOf(
                    Query.equal("receiverId", receiverId),
                    Query.orderDesc("createdAt"),
                    Query.limit(100)
                )
            )

            // ✅ ВРЕМЕННЫЙ ЛОГ ДЛЯ ДИАГНОСТИКИ ПРИХОДЯЩИХ ДОКУМЕНТОВ
            Log.d("SIGNALS_DEBUG", "📥 Получено ${result.documents.size} документов от сервера")
            result.documents.forEach { doc ->
                Log.d("SIGNALS_DEBUG", "   - type='${doc.data["type"]}', sender='${doc.data["senderName"]}'")
            }

            // ✅ ИСПРАВЛЕНИЕ: ДОБАВЛЕН request_spot В РАЗРЕШЕННЫЕ ТИПЫ
            val signalTypes = setOf("on_fishing", "going_fishing", "fishing_wish", "fishing_reply", "request_spot")

            val signals = result.documents.mapNotNull { doc ->
                val data = doc.data
                val type = data["type"] as? String ?: return@mapNotNull null

                // Если тип не входит в разрешенный список, документ пропускается
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
                    createdAt = parseTime(data["createdAt"]),
                    plannedDate = (data["plannedDate"] as? Number)?.toLong() ?: 0L
                )
            }

            Log.d("SIGNALS_DEBUG", "✅ После фильтрации осталось ${signals.size} сигналов")
            Result.success(signals)
        } catch (e: Exception) {
            Log.e("SIGNALS_DEBUG", "❌ Исключение в репозитории: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendReply(
        anchorId: String,
        senderId: String,
        senderName: String,
        receiverId: String,
        replyText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (anchorId.isEmpty()) {
                return@withContext Result.failure(Exception("Ошибка привязки к запросу"))
            }

            val data = mapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "receiverId" to receiverId,
                "type" to "fishing_reply",
                "anchorId" to anchorId,
                "messageText" to replyText,
                "isRead" to false,
                "createdAt" to java.time.Instant.now().toString()
            )

            AppwriteManager.databases.createDocument(
                databaseId = DB_ID,
                collectionId = NOTIFICATIONS_COL_ID,
                documentId = ID.unique(),
                data = data
            )
            Result.success(Unit)
        } catch (e: Exception) {
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

    suspend fun deleteSignal(signalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppwriteManager.databases.deleteDocument(
                databaseId = DB_ID,
                collectionId = NOTIFICATIONS_COL_ID,
                documentId = signalId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}