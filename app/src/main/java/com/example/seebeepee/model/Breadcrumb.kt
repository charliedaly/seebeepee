package com.example.seebeepee.model

import com.example.seebeepee.util.CoordinateUtils

data class Breadcrumb(
    val x: Double,
    val y: Double,
    val altitude: Double,
    val timestamp: Long
) {
    fun toLatLon(): Pair<Double, Double> {
        return CoordinateUtils.metricToLatLon(x, y)
    }

    companion object {
        fun fromGps(lat: Double, lon: Double, altitude: Double, timestamp: Long): Breadcrumb {
            val (x, y) = CoordinateUtils.latLonToMetric(lat, lon)
            return Breadcrumb(x, y, altitude, timestamp)
        }
    }
}
