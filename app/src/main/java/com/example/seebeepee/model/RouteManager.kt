// --- FILE: app/src/main/java/com/example/seebeepee/model/RouteManager.kt ---

package com.example.seebeepee.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.AdvancementResult
import com.example.seebeepee.util.RouteParser
import com.example.seebeepee.util.WaypointAdvancementEngine
import com.example.seebeepee.util.saveBreadcrumbsToGpx
import java.io.File

object RouteManager {
    var currentRoute: Route? by mutableStateOf(null)
    var currentHike: Hike? by mutableStateOf(null)
    val debugLogs = mutableStateListOf<String>()
    val waypointAdvancementEngine = WaypointAdvancementEngine()

    var currentFilePath: String? = null
    var currentFileUri: String? = null
    var currentRouteContent: String? = null
    var hikeState: HikeState = HikeState.IDLE

    // State delegation for proximity and advancement tracking
    var lastProximityAlertWaypointIndex: Int = -1

    // Convenience accessors for other files referencing RouteManager fields
    val waypoints: List<Waypoint>
        get() = currentRoute?.waypoints ?: emptyList()

    val currentRouteName: String
        get() = currentRoute?.name ?: "No Route Loaded"

    var currentWaypointIndex: Int
        get() = currentHike?.currentIndex ?: 0
        set(value) {
            if (currentHike != null) {
                currentHike?.currentIndex = value
            }
        }

    val breadcrumbs: List<Breadcrumb>
        get() = currentHike?.breadcrumbs ?: emptyList()

    fun loadRoute(
        routeName: String,
        newWaypoints: List<Waypoint>,
        content: String? = null,
        filePath: String? = null,
        fileUri: String? = null
    ) {
        currentFilePath = filePath
        currentFileUri = fileUri
        currentRouteContent = content
        currentRoute = Route(name = routeName, waypoints = newWaypoints)
        currentHike = null
        lastProximityAlertWaypointIndex = -1
        waypointAdvancementEngine.reset()

        if (newWaypoints.isNotEmpty()) {
            hikeState = HikeState.ROUTE_LOADED
        } else {
            hikeState = HikeState.IDLE
        }
        logError("Loaded route '$routeName' with ${newWaypoints.size} waypoints.")
    }

    // Backwards compatibility alias
    fun loadWaypoints(
        routeName: String,
        newWaypoints: List<Waypoint>,
        content: String? = null,
        filePath: String? = null,
        fileUri: String? = null
    ) {
        loadRoute(routeName, newWaypoints, content, filePath, fileUri)
    }

    fun processWaypointAdvancement(prefs: AppPreferences): AdvancementResult {
        return waypointAdvancementEngine.processBreadcrumb(
            breadcrumbs = breadcrumbs,
            waypoints = waypoints,
            currentWaypointIndex = currentWaypointIndex,
            arrivalThreshold = prefs.arrivalThreshold,
            nearMissThreshold = prefs.nearMissThreshold
        )
    }

    fun loadSavedRoute(prefs: AppPreferences, context: Context? = null): Boolean {
        val path = prefs.lastFilePath
        val uri = prefs.lastFileUri
        var name = prefs.lastRouteName ?: "Saved Route"

        if (context != null && !uri.isNullOrEmpty() && uri.startsWith("content://")) {
            name = RouteParser.extractRouteNameFromUri(context, uri, fallbackName = name)
        } else {
            name = name.substringBeforeLast(".")
        }

        if (!path.isNullOrEmpty()) {
            try {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    val content = file.readText()
                    val parsedWaypoints = parseRouteContent(path, content)
                    if (parsedWaypoints.isNotEmpty()) {
                        loadRoute(name, parsedWaypoints, content = content, filePath = path, fileUri = uri)
                        if (prefs.isHikeActive) {
                            startHike(prefs)
                        }
                        return true
                    }
                }
            } catch (e: Exception) {
                logError("Failed to auto-reload route from path '$path': ${e.message}")
            }
        }

        val content = prefs.lastRouteContent
        if (!content.isNullOrEmpty()) {
            try {
                val parsedWaypoints = parseRouteContent(name, content)
                if (parsedWaypoints.isNotEmpty()) {
                    loadRoute(name, parsedWaypoints, content = content, filePath = path, fileUri = uri)
                    if (prefs.isHikeActive) {
                        startHike(prefs)
                    }
                    return true
                }
            } catch (e: Exception) {
                logError("Failed to auto-reload route from saved content: ${e.message}")
            }
        }

        return false
    }

    fun startHike(prefs: AppPreferences) {
        val route = currentRoute
        if (route != null && route.waypoints.isNotEmpty()) {
            currentHike = Hike(route).apply {
                currentIndex = prefs.currentHikeIndex.coerceIn(0, route.waypoints.size - 1)
            }
            hikeState = HikeState.HIKING
            prefs.isHikeActive = true
            logError("Hike started at index ${currentHike?.currentIndex} and persisted.")
        }
    }

    fun stopHike(prefs: AppPreferences) {
        currentHike?.let {
            prefs.currentHikeIndex = it.currentIndex
        }
        hikeState = if (currentRoute != null) HikeState.ROUTE_LOADED else HikeState.IDLE
        prefs.isHikeActive = false
        logError("Hike stopped and progress index (${prefs.currentHikeIndex}) saved.")
    }

    fun finishHike(prefs: AppPreferences, save: Boolean, context: Context? = null): File? {
        val savedFile = if (save && context != null) {
            persistUnsavedBreadcrumbs(context)
        } else {
            null
        }
        currentHike = null
        stopHike(prefs)
        return savedFile
    }

    fun restoreHikeState(prefs: AppPreferences) {
        if (prefs.isHikeActive) {
            if (currentRoute != null && currentRoute!!.waypoints.isNotEmpty()) {
                if (currentHike == null) {
                    currentHike = Hike(currentRoute!!)
                }
                hikeState = HikeState.HIKING
                logError("Restored active hike state (HIKING).")
            } else {
                if (loadSavedRoute(prefs)) {
                    hikeState = HikeState.HIKING
                    logError("Loaded saved route and restored active hike state (HIKING).")
                }
            }
        }
    }

    private fun parseRouteContent(fileNameOrPath: String, content: String): List<Waypoint> {
        return if (fileNameOrPath.endsWith(".gpx", ignoreCase = true) || content.contains("<gpx", ignoreCase = true)) {
            RouteParser.parseGpx(content)
        } else {
            RouteParser.parseCsv(content)
        }
    }

    fun addBreadcrumb(breadcrumb: Breadcrumb) {
        currentHike?.addBreadcrumb(breadcrumb)
    }

    fun clearBreadcrumbs() {
        currentHike = null
        waypointAdvancementEngine.reset()
        lastProximityAlertWaypointIndex = -1
        logError("Hike and breadcrumbs cleared.")
    }

    fun persistUnsavedBreadcrumbs(context: Context? = null): File? {
        val bc = breadcrumbs
        if (bc.isNotEmpty()) {
            logError("Persisted ${bc.size} breadcrumbs for route '$currentRouteName'.")
            if (context != null) {
                return saveBreadcrumbsToGpx(context, bc, currentRouteName, currentFilePath, currentFileUri)
            }
        }
        return null
    }

    fun logError(message: String) {
        val logEntry = "[${System.currentTimeMillis()}] $message"
        debugLogs.add(0, logEntry)
        if (debugLogs.size > 200) {
            debugLogs.removeAt(debugLogs.size - 1)
        }
    }
}