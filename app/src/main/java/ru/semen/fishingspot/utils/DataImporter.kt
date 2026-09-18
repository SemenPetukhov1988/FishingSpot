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

object DataImporter {
    private val TAG = "IMPORT"

    suspend fun importWaterFromGeoJson(context: Context, dao: WaterBodyDao) {
        withContext(Dispatchers.IO) {
            if (dao.getCount() > 0) {
                Log.d(TAG, "База уже заполнена (${dao.getCount()} записей).")
                return@withContext
            }

            try {
                Log.d(TAG, "🚀 Начало импорта...")
                val inputStream = context.assets.open("water_bodies.json")
                // Увеличиваем буфер чтения для больших файлов
                val reader = JsonReader(InputStreamReader(inputStream, "UTF-8").buffered(8 * 1024))
                reader.isLenient = true

                val batch = mutableListOf<WaterBody>()
                var id = 1L
                var totalParsed = 0
                var skipped = 0

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

                                    // Сохраняем батчами по 1000 штук
                                    if (batch.size >= 1000) {
                                        dao.insertAll(batch)
                                        Log.d(TAG, "Промежуточное сохранение: ${batch.size} шт. (Всего: $totalParsed)")
                                        batch.clear()
                                    }
                                } else {
                                    skipped++
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка парсинга одного объекта", e)
                                // Пропускаем битый объект и идем дальше
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                reader.close()

                // Сохраняем остаток
                if (batch.isNotEmpty()) {
                    dao.insertAll(batch)
                    batch.clear()
                }

                Log.d(TAG, "✅ Импорт ЗАВЕРШЕН! Загружено: $totalParsed, Пропущено: $skipped")

            } catch (e: Exception) {
                Log.e(TAG, "❌ КРИТИЧЕСКАЯ ОШИБКА ИМПОРТА", e)
            }
        }
    }

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

        // Считаем границы
        var i = 0
        while (i + 1 < allCoords.size) {
            val lat = allCoords[i]; val lon = allCoords[i+1]
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            i += 2
        }

        // Формируем JSON контура
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