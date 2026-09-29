package com.example.seebeepee.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.model.Waypoint
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.NaismithEngine
import com.example.seebeepee.util.RouteParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

data class LegMetric(
    val fromWaypoint: Waypoint,
    val toWaypoint: Waypoint?,
    val distanceMeters: Double,
    val climbMeters: Double,
    val bearing: Double,
    val timeMinutes: Double
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)

    var flatPace by mutableDoubleStateOf(prefs.flatPace)
        private set
    var climbPenalty by mutableDoubleStateOf(prefs.climbPenalty)
        private set
    var coordinateSystem by mutableStateOf(prefs.coordinateSystem)
        private set
    var voiceAlertsEnabled by mutableStateOf(prefs.voiceAlertsEnabled)
        private set
    var coneAngle by mutableFloatStateOf(prefs.coneAngle)
        private set

    var showSettings by mutableStateOf(false)
    var gpsStatus by mutableStateOf("GPS Active (Simulated)")

    var elapsedTimeSeconds by mutableLongStateOf(0L)
        private set

    init {
        if (RouteManager.waypoints.isEmpty()) {
            val sample = RouteParser.parseCsv(RouteParser.generateSampleIrishGridCsv())
            RouteManager.loadWaypoints("Sample Hike (Irish Grid)", sample)
        }

        viewModelScope.launch {
            while (true) {
                delay(1000L)
                elapsedTimeSeconds++
            }
        }
    }

    fun updateFlatPace(pace: Double) {
        flatPace = pace
        prefs.flatPace = pace
    }

    fun updateClimbPenalty(penalty: Double) {
        climbPenalty = penalty
        prefs.climbPenalty = penalty
    }

    fun updateCoordinateSystem(system: String) {
        coordinateSystem = system
        prefs.coordinateSystem = system
    }

    fun updateVoiceAlerts(enabled: Boolean) {
        voiceAlertsEnabled = enabled
        prefs.voiceAlertsEnabled = enabled
    }

    fun updateConeAngle(angle: Float) {
        coneAngle = angle
        prefs.coneAngle = angle
    }

    fun loadRoute(name: String, waypoints: List<Waypoint>) {
        RouteManager.loadWaypoints(name, waypoints)
        elapsedTimeSeconds = 0L
    }

    val legMetrics: List<LegMetric>
        get() {
            val list = mutableListOf<LegMetric>()
            val waypoints = RouteManager.waypoints
            for (i in waypoints.indices) {
                val current = waypoints[i]
                val next = if (i < waypoints.size - 1) waypoints[i + 1] else null
                if (next != null) {
                    val dist = CoordinateUtils.calculateDistance(current.x, current.y, next.x, next.y)
                    val climb = max(0.0, next.altitude - current.altitude)
                    val bearing = CoordinateUtils.calculateBearing(current.x, current.y, next.x, next.y)
                    val time = NaismithEngine.estimateHikingTime(dist, climb, flatPace, climbPenalty)
                    list.add(LegMetric(current, next, dist, climb, bearing, time))
                } else {
                    list.add(LegMetric(current, null, 0.0, 0.0, 0.0, 0.0))
                }
            }
            return list
        }

    val currentIndex: Int
        get() = RouteManager.currentWaypointIndex.coerceIn(0, max(0, RouteManager.waypoints.size - 1))

    fun setCurrentWaypointIndex(index: Int) {
        if (RouteManager.waypoints.isNotEmpty()) {
            RouteManager.currentWaypointIndex = index.coerceIn(0, RouteManager.waypoints.size - 1)
        }
    }

    val currentLegMetric: LegMetric?
        get() {
            val metrics = legMetrics
            val idx = currentIndex
            return if (metrics.isNotEmpty() && idx < metrics.size) metrics[idx] else null
        }

    val currentPositionString: String
        get() {
            val lastBc = RouteManager.breadcrumbs.lastOrNull()
            val x: Double
            val y: Double
            if (lastBc != null) {
                x = lastBc.x
                y = lastBc.y
            } else {
                val waypoints = RouteManager.waypoints
                val idx = currentIndex
                if (waypoints.isNotEmpty() && idx < waypoints.size) {
                    x = waypoints[idx].x
                    y = waypoints[idx].y
                } else {
                    return "N/A"
                }
            }

            return if (coordinateSystem == "grid") {
                // Format pseudo Irish grid representation
                val easting = x % 100000.0
                val northing = y % 100000.0
                String.format("V%.0f %.0f", easting, northing)
            } else {
                val (lat, lon) = CoordinateUtils.metricToLatLon(x, y)
                String.format("%.4f, %.4f", lat, lon)
            }
        }

    val currentElevation: Double
        get() {
            val lastBc = RouteManager.breadcrumbs.lastOrNull()
            if (lastBc != null) return lastBc.altitude
            val waypoints = RouteManager.waypoints
            val idx = currentIndex
            if (waypoints.isNotEmpty() && idx < waypoints.size) {
                return waypoints[idx].altitude
            }
            return 0.0
        }

    val totalDistance: Double
        get() = legMetrics.sumOf { it.distanceMeters }

    val totalClimb: Double
        get() = legMetrics.sumOf { it.climbMeters }

    val totalTimeRequired: Double
        get() = legMetrics.sumOf { it.timeMinutes }

    val remainingTime: Double
        get() {
            val metrics = legMetrics
            val idx = currentIndex
            var sum = 0.0
            for (i in idx until metrics.size) {
                sum += metrics[i].timeMinutes
            }
            return sum
        }

    val distanceTravelledSoFar: Double
        get() {
            val metrics = legMetrics
            val idx = currentIndex
            var sum = 0.0
            for (i in 0 until idx) {
                if (i < metrics.size) sum += metrics[i].distanceMeters
            }
            return sum
        }

    val elevationClimbedSoFar: Double
        get() {
            val metrics = legMetrics
            val idx = currentIndex
            var sum = 0.0
            for (i in 0 until idx) {
                if (i < metrics.size) sum += metrics[i].climbMeters
            }
            return sum
        }
}
