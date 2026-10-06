// --- FILE: app/src/main/java/com/example/seebeepee/util/HikeAlertEngine.kt ---

package com.example.seebeepee.util

import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.Waypoint
import kotlin.math.roundToInt

data class ProximityAlertResult(
    val shouldAlert: Boolean,
    val message: String?,
    val targetWaypointIndex: Int
)

class HikeAlertEngine {
    private var lastProximityAlertWaypointIndex: Int = -1

    fun reset() {
        lastProximityAlertWaypointIndex = -1
    }

    fun evaluateProximity(
        waypoints: List<Waypoint>,
        currentIndex: Int,
        breadcrumbs: List<Breadcrumb>,
        proximityThreshold: Double,
        loopbackDistance: Double
    ): ProximityAlertResult {
        if (waypoints.isEmpty() || breadcrumbs.isEmpty()) {
            return ProximityAlertResult(false, null, -1)
        }

        val targetIndex = currentIndex + 1
        if (targetIndex >= waypoints.size) return ProximityAlertResult(false, null, -1)

        val targetWp = waypoints[targetIndex]
        if (lastProximityAlertWaypointIndex == targetIndex) {
            return ProximityAlertResult(false, null, targetIndex)
        }

        val lastBc = breadcrumbs.last()
        val dist = CoordinateUtils.calculateDistance(lastBc.x, lastBc.y, targetWp.x, targetWp.y)

        if (dist <= proximityThreshold) {
            val bHiker = CoordinateUtils.calculateLoopbackBearing(breadcrumbs, loopbackDistance) ?: CoordinateUtils.calculateBearing(
                if (breadcrumbs.size >= 2) breadcrumbs[breadcrumbs.size - 2].x else lastBc.x,
                if (breadcrumbs.size >= 2) breadcrumbs[breadcrumbs.size - 2].y else lastBc.y,
                lastBc.x, lastBc.y
            )
            val bTarget = CoordinateUtils.calculateBearing(lastBc.x, lastBc.y, targetWp.x, targetWp.y)
            val relBearing = CoordinateUtils.computeRelativeBearing(bHiker, bTarget)
            val relBearingStr = CoordinateUtils.formatRelativeBearing(relBearing)

            val message = TtsManager.generateProximityMessage(targetWp, dist, relBearingStr)
            lastProximityAlertWaypointIndex = targetIndex
            return ProximityAlertResult(true, message, targetIndex)
        }

        return ProximityAlertResult(false, null, targetIndex)
    }
}