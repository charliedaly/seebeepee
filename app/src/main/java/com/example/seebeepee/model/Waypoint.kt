package com.example.seebeepee.model

import com.example.seebeepee.util.CoordinateUtils

data class Waypoint(
    val name: String,
    val x: Double,
    val y: Double,
    val altitude: Double = 0.0,
    val threshold: Double? = null
) {
    fun toLatLonString(): String {
        val (lat, lon) = CoordinateUtils.metricToLatLon(x, y)
        return String.format("%.5f, %.5f", lat, lon)
    }

    fun toGridReferenceString(): String {
        return "X: %.1f, Y: %.1f".format(x, y)
    }

    companion object {
        fun fromGridReference(name: String, gridRef: String, altitude: Double = 0.0, threshold: Double? = null): Waypoint {
            val (x, y) = CoordinateUtils.parseGridReference(gridRef)
            return Waypoint(name, x, y, altitude, threshold)
        }

        fun fromLatLon(name: String, lat: Double, lon: Double, altitude: Double = 0.0, threshold: Double? = null): Waypoint {
            val (x, y) = CoordinateUtils.latLonToMetric(lat, lon)
            return Waypoint(name, x, y, altitude, threshold)
        }
    }
}
