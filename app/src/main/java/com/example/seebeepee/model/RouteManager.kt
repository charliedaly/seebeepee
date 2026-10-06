package com.example.seebeepee.model

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.example.seebeepee.util.AdvancementResult
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.RouteParser
import com.example.seebeepee.util.WaypointAdvancementEngine
import com.example.seebeepee.util.saveBreadcrumbsToGpx
import java.io.File

object RouteManager {
    val waypoints = mutableStateListOf<Waypoint>()
    val breadcrumbs = mutableStateListOf<Breadcrumb>()
    val debugLogs = mutableStateListOf<String>()
    val waypointAdvancementEngine = WaypointAdvancementEngine()

    var currentWaypointIndex: Int = 0
    var currentRouteName: String = "No Route Loaded"
    var currentFilePath: String? = null
    var currentFileUri: String? = null
    var currentRouteContent: String? = null
    var hikeState: HikeState = HikeState.IDLE

    fun loadWaypoints(
        routeName: String,
        newWaypoints: List<Waypoint>,
        content: String? = null,
        filePath: String? = null,
        fileUri: String? = null
    ) {
        currentRouteName = routeName
        currentFilePath = filePath
        currentFileUri = fileUri
        currentRouteContent = content
        waypoints.clear()
        waypoints.addAll(newWaypoints)
        currentWaypointIndex = 0
        waypointAdvancementEngine.reset()
        if (newWaypoints.isNotEmpty()) {
            hikeState = HikeState.ROUTE_LOADED
        } else {
            hikeState = HikeState.IDLE
        }
        logError("Loaded route '$routeName' with ${newWaypoints.size} waypoints.")
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
                        loadWaypoints(name, parsedWaypoints, content = content, filePath = path, fileUri = uri)
                        if (prefs.isHikeActive) {
                            hikeState = HikeState.HIKING
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
                    loadWaypoints(name, parsedWaypoints, content = content, filePath = path, fileUri = uri)
                    if (prefs.isHikeActive) {
                        hikeState = HikeState.HIKING
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
        hikeState = HikeState.HIKING
        prefs.isHikeActive = true
        logError("Hike started and persisted as active.")
    }

    fun stopHike(prefs: AppPreferences) {
        hikeState = HikeState.IDLE
        prefs.isHikeActive = false
        logError("Hike stopped and persisted as inactive.")
    }

    fun finishHike(prefs: AppPreferences, save: Boolean, context: Context? = null): File? {
        val savedFile = if (save && context != null) {
            persistUnsavedBreadcrumbs(context)
        } else {
            null
        }
        clearBreadcrumbs()
        stopHike(prefs)
        return savedFile
    }

    fun restoreHikeState(prefs: AppPreferences) {
        if (prefs.isHikeActive) {
            if (waypoints.isNotEmpty()) {
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
        breadcrumbs.add(breadcrumb)
    }

    fun clearBreadcrumbs() {
        breadcrumbs.clear()
        waypointAdvancementEngine.reset()
        logError("Breadcrumbs cleared.")
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

    fun persistUnsavedBreadcrumbs(context: Context? = null): File? {
        if (breadcrumbs.isNotEmpty()) {
            logError("Persisted ${breadcrumbs.size} breadcrumbs for route '$currentRouteName'.")
            if (context != null) {
                return saveBreadcrumbsToGpx(context, breadcrumbs, currentRouteName, currentFilePath, currentFileUri)
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
