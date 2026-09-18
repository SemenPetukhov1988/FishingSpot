package ru.semen.fishingspot.data

import org.json.JSONArray
import kotlin.math.*

class WaterChecker {
    private val CHECK_RADIUS_METERS = 50.0

    fun isNearWater(lat: Double, lon: Double, candidates: List<WaterBody>): Boolean {
        for (water in candidates) {
            val coords = parseCoordinates(water.coordinatesJson)
            if (coords.isEmpty()) continue

            // 1. Точка внутри полигона?
            if (coords.size >= 3 && isPointInPolygon(lat, lon, coords)) return true

            // 2. Расстояние до ближайшей линии берега
            if (minDistanceToContour(lat, lon, coords) <= CHECK_RADIUS_METERS) return true
        }
        return false
    }

    private fun parseCoordinates(json: String): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val pair = arr.getJSONArray(i)
                list.add(Pair(pair.getDouble(0), pair.getDouble(1))) // Lat, Lon
            }
        } catch (e: Exception) {}
        return list
    }

    private fun minDistanceToContour(lat: Double, lon: Double, coords: List<Pair<Double, Double>>): Double {
        var minDist = Double.MAX_VALUE
        for (i in 0 until coords.size - 1) {
            val d = distanceToSegment(lat, lon, coords[i].first, coords[i].second, coords[i+1].first, coords[i+1].second)
            if (d < minDist) minDist = d
        }
        // Замыкаем контур
        if (coords.size > 2) {
            val d = distanceToSegment(lat, lon, coords.last().first, coords.last().second, coords.first().first, coords.first().second)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return haversine(px, py, ax, ay)
        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        return haversine(px, py, ax + clampedT * dx, ay + clampedT * dy)
    }

    private fun isPointInPolygon(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (latI, lonI) = polygon[i]; val (latJ, lonJ) = polygon[j]
            if (((lonI > lon) != (lonJ > lon)) && (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}