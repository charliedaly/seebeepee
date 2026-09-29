package com.example.seebeepee.model

import androidx.compose.runtime.mutableStateListOf

object RouteManager {
    val waypoints = mutableStateListOf<Waypoint>()
    val breadcrumbs = mutableStateListOf<Breadcrumb>()
    val debugLogs = mutableStateListOf<String>()

    var currentWaypointIndex: Int = 0
    var currentRouteName: String = "No Route Loaded"
    var hikeState: HikeState = HikeState.IDLE

    fun loadWaypoints(routeName: String, newWaypoints: List<Waypoint>) {
        currentRouteName = routeName
        waypoints.clear()
        waypoints.addAll(newWaypoints)
        currentWaypointIndex = 0
        if (newWaypoints.isNotEmpty()) {
            hikeState = HikeState.ROUTE_LOADED
        } else {
            hikeState = HikeState.IDLE
        }
        logError("Loaded route '$routeName' with ${newWaypoints.size} waypoints.")
    }

    fun addBreadcrumb(breadcrumb: Breadcrumb) {
        breadcrumbs.add(breadcrumb)
    }

    fun clearBreadcrumbs() {
        breadcrumbs.clear()
        logError("Breadcrumbs cleared.")
    }

    fun persistUnsavedBreadcrumbs() {
        if (breadcrumbs.isNotEmpty()) {
            logError("Persisted ${breadcrumbs.size} breadcrumbs for route '$currentRouteName'.")
        }
    }

    fun logError(message: String) {
        val logEntry = "[${System.currentTimeMillis()}] $message"
        debugLogs.add(0, logEntry)
        if (debugLogs.size > 200) {
            debugLogs.removeAt(debugLogs.size - 1)
        }
    }
}
