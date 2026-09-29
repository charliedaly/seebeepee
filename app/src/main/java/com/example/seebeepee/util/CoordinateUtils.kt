package com.example.seebeepee.util

import com.example.seebeepee.model.Breadcrumb
import kotlin.math.*

object CoordinateUtils {
    private const val REF_LAT = 53.3498
    private const val REF_LON = -6.2603
    private const val METERS_PER_DEG_LAT = 111132.0
    private const val GRID_LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

    fun latLonToMetric(lat: Double, lon: Double): Pair<Double, Double> {
        val dLat = Math.toRadians(lat - REF_LAT)
        val dLon = Math.toRadians(lon - REF_LON)
        val y = dLat * METERS_PER_DEG_LAT + 200000.0
        val x = dLon * METERS_PER_DEG_LAT * cos(Math.toRadians(REF_LAT)) + 300000.0
        return Pair(x, y)
    }

    fun metricToLatLon(x: Double, y: Double): Pair<Double, Double> {
        val dY = y - 200000.0
        val dX = x - 300000.0
        val dLat = dY / METERS_PER_DEG_LAT
        val dLon = dX / (METERS_PER_DEG_LAT * cos(Math.toRadians(REF_LAT)))
        val lat = REF_LAT + Math.toDegrees(dLat)
        val lon = REF_LON + Math.toDegrees(dLon)
        return Pair(lat, lon)
    }

    /**
     * Parses grid references like "V 860 870", "V860 870", or "V860870".
     */
    fun parseGridReference(ref: String): Pair<Double, Double> {
        val cleaned = ref.trim().uppercase()
        if (cleaned.isEmpty()) return Pair(0.0, 0.0)

        val letter = cleaned[0]
        val digits = cleaned.substring(1).replace(Regex("\\s+"), "")

        val index = GRID_LETTERS.indexOf(letter)
        val safeIndex = if (index >= 0) index else 20 // default to V if not found
        val col = safeIndex % 5
        val row = safeIndex / 5

        val baseEasting = col * 100000.0
        val baseNorthing = (4 - row) * 100000.0

        if (digits.isEmpty()) {
            return Pair(baseEasting, baseNorthing)
        }

        val halfLen = digits.length / 2
        val eastingStr = digits.substring(0, halfLen)
        val northingStr = digits.substring(halfLen)

        val eastingVal = eastingStr.toDoubleOrNull() ?: 0.0
        val northingVal = northingStr.toDoubleOrNull() ?: 0.0

        val multiplier = 100000.0 / 10.0.pow(halfLen.toDouble())

        val x = baseEasting + (eastingVal * multiplier)
        val y = baseNorthing + (northingVal * multiplier)

        return Pair(x, y)
    }

    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return hypot(x2 - x1, y2 - y1)
    }

    fun calculateBearing(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        var angle = Math.toDegrees(atan2(dx, dy))
        if (angle < 0) {
            angle += 360.0
        }
        return angle
    }

    fun calculateSpeed(b1: Breadcrumb, b2: Breadcrumb): Double {
        val distMeters = calculateDistance(b1.x, b1.y, b2.x, b2.y)
        val timeSec = (b2.timestamp - b1.timestamp) / 1000.0
        if (timeSec <= 0.0) return 0.0
        val speedMps = distMeters / timeSec
        return speedMps * 3.6 // km/h
    }
}
