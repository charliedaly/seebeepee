// --- FILE: app/src/main/java/com/example/seebeepee/model/Hike.kt ---

package com.example.seebeepee.model

import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.HeadingSmoother
import com.example.seebeepee.util.PersistenceGatekeeper
import com.example.seebeepee.util.TtsManager
import kotlin.math.abs

data class ProximityAlertResult(
    val shouldAlert: Boolean,
    val message: String?,
    val targetWaypointIndex: Int
)

data class CourseCorrectionResult(
    val shouldAlert: Boolean,
    val message: String?
)

class Hike(val route: Route) {
    var currentIndex: Int = 0
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val startTime: Long = System.currentTimeMillis()

    var descriptionAnnounced: Boolean = false
    var approachAnnouncementMade: Boolean = false
    var lastProximityAlertWaypointIndex: Int = -1

    val headingSmoother = HeadingSmoother()
    var persistenceGatekeeper: PersistenceGatekeeper? = null
    var lastCourseCorrectionTime: Long = 0L
    private val courseCorrectionCooldownMs = 30000L // 30 seconds cooldown

    fun addBreadcrumb(crumb: Breadcrumb) {
        breadcrumbs.add(crumb)
    }

    fun advanceWaypoint(): Boolean {
        if (currentIndex < route.waypoints.size - 1) {
            currentIndex++
            descriptionAnnounced = false
            approachAnnouncementMade = false
            lastProximityAlertWaypointIndex = -1
            persistenceGatekeeper?.reset()
            return true
        }
        return false
    }

    fun getCurrentTargetWaypoint(): Waypoint? {
        val targetIndex = currentIndex + 1
        return route.waypoints.getOrNull(targetIndex)
    }

    fun getNextTargetWaypoint(): Waypoint? {
        val nextIndex = currentIndex + 2
        return route.waypoints.getOrNull(nextIndex)
    }

    fun isFinished(): Boolean {
        return currentIndex >= route.waypoints.size - 1
    }

    fun evaluateProximity(proximityThreshold: Double, loopbackDistance: Double): ProximityAlertResult {
        val waypoints = route.waypoints
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

    fun evaluateCourseCorrection(
        lowSpeedCutoff: Double,
        loopbackDistance: Double,
        coneAngle: Float,
        requiredFixes: Int
    ): CourseCorrectionResult {
        val waypoints = route.waypoints
        if (waypoints.isEmpty() || breadcrumbs.size < 2) {
            return CourseCorrectionResult(false, null)
        }

        if (persistenceGatekeeper == null || persistenceGatekeeper?.requiredFixes != requiredFixes) {
            persistenceGatekeeper = PersistenceGatekeeper(requiredFixes)
        }

        val currentSpeed = CoordinateUtils.calculateRecentSpeed(breadcrumbs, loopbackDistance)
        if (currentSpeed < lowSpeedCutoff) {
            persistenceGatekeeper?.reset()
            return CourseCorrectionResult(false, null)
        }

        val rawBearing = CoordinateUtils.calculateLoopbackBearing(breadcrumbs, loopbackDistance) ?: return CourseCorrectionResult(false, null)
        val smoothedBearing = headingSmoother.update(rawBearing)

        val idx = currentIndex.coerceIn(0, waypoints.size - 1)
        val targetWp = if (idx < waypoints.size - 1) waypoints[idx + 1] else waypoints[idx]
        val lastBc = breadcrumbs.last()
        val targetBearing = CoordinateUtils.calculateBearing(lastBc.x, lastBc.y, targetWp.x, targetWp.y)

        val deltaB = CoordinateUtils.computeAngularDeviation(smoothedBearing, targetBearing)
        val isOffCourse = abs(deltaB) > coneAngle

        val alertTriggered = persistenceGatekeeper?.update(isOffCourse) ?: false
        if (!alertTriggered) return CourseCorrectionResult(false, null)

        val now = System.currentTimeMillis()
        if (now - lastCourseCorrectionTime < courseCorrectionCooldownMs) {
            return CourseCorrectionResult(false, null)
        }

        val message = TtsManager.generateCourseCorrectionMessage(smoothedBearing, targetBearing, coneAngle)
        if (message != null) {
            lastCourseCorrectionTime = now
            persistenceGatekeeper?.reset()
            return CourseCorrectionResult(true, message)
        }

        return CourseCorrectionResult(false, null)
    }
}