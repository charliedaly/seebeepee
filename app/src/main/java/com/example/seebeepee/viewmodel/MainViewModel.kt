package com.example.seebeepee.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

    var showWaypointName by mutableStateOf(prefs.showWaypointName)
        private set
    var showWaypointAltitude by mutableStateOf(prefs.showWaypointAltitude)
        private set
    var showWaypointBearing by mutableStateOf(prefs.showWaypointBearing)
        private set
    var showWaypoints by mutableStateOf(prefs.showWaypoints)
        private set
    var showBreadcrumbs by mutableStateOf(prefs.showBreadcrumbs)
        private set
    var showGridlines by mutableStateOf(prefs.showGridlines)
        private set
    var useFullGridReference by mutableStateOf(prefs.useFullGridReference)
        private set
    var gridReferencePrecision by mutableIntStateOf(prefs.gridReferencePrecision)
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

    fun updateShowWaypointName(enabled: Boolean) {
        showWaypointName = enabled
        prefs.showWaypointName = enabled
    }

    fun updateShowWaypointAltitude(enabled: Boolean) {
        showWaypointAltitude = enabled
        prefs.showWaypointAltitude = enabled
    }

    fun updateShowWaypointBearing(enabled: Boolean) {
        showWaypointBearing = enabled
        prefs.showWaypointBearing = enabled
    }

    fun updateShowWaypoints(enabled: Boolean) {
        showWaypoints = enabled
        prefs.showWaypoints = enabled
    }

    fun updateShowBreadcrumbs(enabled: Boolean) {
        showBreadcrumbs = enabled
        prefs.showBreadcrumbs = enabled
    }

    fun updateShowGridlines(enabled: Boolean) {
        showGridlines = enabled
        prefs.showGridlines = enabled
    }

    fun updateUseFullGridReference(enabled: Boolean) {
        useFullGridReference = enabled
        prefs.useFullGridReference = enabled
    }

    fun updateGridReferencePrecision(precision: Int) {
        gridReferencePrecision = precision
        prefs.gridReferencePrecision = precision
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
                CoordinateUtils.formatGridReferenceWithLetter(x, y, gridReferencePrecision)
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

    fun getCurrentPositionMetric(): Pair<Double, Double> {
        val lastBc = RouteManager.breadcrumbs.lastOrNull()
        if (lastBc != null) return Pair(lastBc.x, lastBc.y)
        val waypoints = RouteManager.waypoints
        val idx = currentIndex
        if (waypoints.isNotEmpty() && idx < waypoints.size) {
            return Pair(waypoints[idx].x, waypoints[idx].y)
        }
        return Pair(0.0, 0.0)
    }

    fun isCurrentPositionOnMap(): Boolean {
        val waypoints = RouteManager.waypoints
        if (waypoints.isEmpty()) return true
        val (cx, cy) = getCurrentPositionMetric()
        val minX = waypoints.minOf { it.x } - 200.0
        val maxX = waypoints.maxOf { it.x } + 200.0
        val minY = waypoints.minOf { it.y } - 200.0
        val maxY = waypoints.maxOf { it.y } + 200.0
        return cx in minX..maxX && cy in minY..maxY
    }
}
