package ru.semen.fishingspot.utils

import android.content.Context
import android.util.Log
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import ru.semen.fishingspot.data.WaterBody
import ru.semen.fishingspot.data.WaterBodyDao
import java.io.InputStreamReader
import java.io.Reader
import java.util.zip.ZipInputStream

object DataImporter {
    private val TAG = "IMPORT"

    suspend fun importWaterSafe(context: Context, dao: WaterBodyDao) {
        withContext(Dispatchers.IO) {
            if (dao.getCount() > 0) {
                Log.d(TAG, "База уже заполнена (${dao.getCount()} записей). Пропускаем импорт.")
                return@withContext
            }

            // 1️⃣ Пытаемся прочитать ZIP
            try {
                context.assets.open("water_bodies.zip").use { zipStream ->
                    Log.d(TAG, "📦 Найден water_bodies.zip. Запускаем быстрый импорт...")
                    val zis = ZipInputStream(zipStream)
                    var entry = zis.nextEntry

                    while (entry != null) {
                        if (entry.name.endsWith(".json")) {
                            Log.d(TAG, "Распаковываем: ${entry.name}")
                            // Для ZIP буфер не так критичен, т.к. он уже сжат,
                            // но InputStreamReader обязателен
                            processJsonStream(InputStreamReader(zis, "UTF-8"), dao)
                            break
                        }
                        entry = zis.nextEntry
                    }
                    zis.close()
                    return@withContext
                }
            } catch (e: java.io.FileNotFoundException) {
                Log.w(TAG, "⚠️ water_bodies.zip не найден. Переключаюсь на legacy JSON...")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка чтения ZIP, аварийное переключение на JSON", e)
            }

            // 2️⃣ Fallback: Читаем старый JSON с буфером
            try {
                context.assets.open("water_bodies.json").use { jsonStream ->
                    Log.d(TAG, "📄 Читаю legacy water_bodies.json...")
                    // ✅ Создаем буферизированный InputStreamReader здесь
                    val bufferedReader = InputStreamReader(jsonStream, "UTF-8").buffered(8 * 1024)
                    processJsonStream(bufferedReader, dao)
                }
            } catch (e: Exception) {
                Log.e(TAG, "💀 КРИТИЧЕСКАЯ ОШИБКА: Нет ни ZIP, ни JSON!", e)
            }
        }
    }

    /**
     * Универсальный процессор. Принимает Reader (поддерживает и InputStreamReader, и BufferedReader)
     */
    private suspend fun processJsonStream(readerSource: Reader, dao: WaterBodyDao) {
        val reader = JsonReader(readerSource)
        reader.isLenient = true

        val batch = mutableListOf<WaterBody>()
        var id = 1L
        var totalParsed = 0
        var skipped = 0

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "features") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        try {
                            val result = parseSingleFeature(reader)
                            if (result != null) {
                                batch.add(WaterBody(
                                    id = id++,
                                    name = result.name,
                                    type = result.type,
                                    minLat = result.minLat, maxLat = result.maxLat,
                                    minLon = result.minLon, maxLon = result.maxLon,
                                    coordinatesJson = result.coordsJson
                                ))
                                totalParsed++

                                if (batch.size >= 1000) {
                                    dao.insertAll(batch)
                                    Log.d(TAG, "Сохранено: ${batch.size} шт. (Всего: $totalParsed)")
                                    batch.clear()
                                }
                            } else {
                                skipped++
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка парсинга объекта", e)
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        } finally {
            reader.close()
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            batch.clear()
        }

        Log.d(TAG, "✅ Импорт ЗАВЕРШЕН! Загружено: $totalParsed, Пропущено: $skipped")
    }

    // --- Вспомогательные классы и функции (без изменений) ---
    private data class ParsedWater(
        val name: String, val type: String,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
        val coordsJson: String
    )

    private fun parseSingleFeature(reader: JsonReader): ParsedWater? {
        var name: String? = null
        var type = "lake"
        var isWater = false
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        val allCoords = mutableListOf<Double>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "properties" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "name" -> name = reader.nextString()
                            "natural" -> if (reader.nextString() == "water") isWater = true
                            "waterway" -> { reader.nextString(); isWater = true; type = "river" }
                            "landuse" -> if (reader.nextString() == "reservoir") isWater = true
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "geometry" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "coordinates") {
                            readCoordsDeep(reader, allCoords)
                        } else reader.skipValue()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!isWater || allCoords.isEmpty()) return null

        var i = 0
        while (i + 1 < allCoords.size) {
            val lat = allCoords[i]; val lon = allCoords[i+1]
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            i += 2
        }

        val coordsArray = JSONArray()
        i = 0
        while (i + 1 < allCoords.size) {
            val pair = JSONArray().apply { put(allCoords[i]); put(allCoords[i+1]) }
            coordsArray.put(pair)
            i += 2
        }

        return ParsedWater(name ?: "Водоем", type, minLat, maxLat, minLon, maxLon, coordsArray.toString())
    }

    private fun readCoordsDeep(reader: JsonReader, allCoords: MutableList<Double>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return }
        reader.beginArray()
        if (reader.peek() == JsonToken.NUMBER) {
            val lon = reader.nextDouble()
            val lat = reader.nextDouble()
            allCoords.add(lat); allCoords.add(lon)
            while (reader.peek() == JsonToken.NUMBER) reader.nextDouble()
        } else {
            while (reader.hasNext()) readCoordsDeep(reader, allCoords)
        }
        reader.endArray()
    }
}