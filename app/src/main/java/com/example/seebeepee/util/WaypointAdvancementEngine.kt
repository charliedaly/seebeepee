package com.example.seebeepee.util

import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.Waypoint
import kotlin.math.min

sealed class AdvancementReason {
    object DirectArrival : AdvancementReason()
    data class NearMissAndMovingOnward(
        val minDistance: Double,
        val consecutiveFixes: Int
    ) : AdvancementReason()
}

data class AdvancementResult(
    val advanced: Boolean,
    val reachedWaypoint: Waypoint? = null,
    val nextTargetWaypoint: Waypoint? = null,
    val newIndex: Int = -1,
    val reason: AdvancementReason? = null,
    val ttsAnnouncement: String? = null
)

class WaypointAdvancementEngine(
    var arrivalThresholdMeters: Double = 20.0,
    var nearMissThresholdMeters: Double = 40.0,
    var requiredConsecutiveMovingOnwardFixes: Int = 10
) {
    private var lastTrackedTargetIndex: Int = -1
    private var minDistanceToTarget: Double = Double.MAX_VALUE
    private var consecutiveMovingOnwardCount: Int = 0
    private var previousDistanceToTarget: Double? = null
    private var previousDistanceToNext: Double? = null

    fun reset() {
        lastTrackedTargetIndex = -1
        minDistanceToTarget = Double.MAX_VALUE
        consecutiveMovingOnwardCount = 0
        previousDistanceToTarget = null
        previousDistanceToNext = null
    }

    /**
     * Evaluates live GPS position (breadcrumbs) against current target waypoint.
     *
     * In SeeBeePee's route model, when currentWaypointIndex = i,
     * origin is waypoints[i], target W_target is waypoints[i + 1],
     * and next target W_next is waypoints[i + 2].
     *
     * Rules:
     * - Condition A (Direct Arrival): dist(GPS, W_target) <= arrivalThreshold (default 20m).
     * - Condition B (Near-Miss & Moving Onward):
     *   - B1: arrivalThreshold < minDistanceToTarget <= nearMissThreshold (20m < minDist <= 40m).
     *   - B2: For at least 10 consecutive breadcrumbs, dist to W_target is increasing
     *         AND dist to W_next (waypoints[i + 2]) is decreasing.
     *
     * On advancement:
     * - Advances currentWaypointIndex to i + 1.
     * - Triggers TTS announcement: "Reached [TargetName]. Next target is [NextTargetName]."
     */
    fun processBreadcrumb(
        breadcrumbs: List<Breadcrumb>,
        waypoints: List<Waypoint>,
        currentWaypointIndex: Int,
        arrivalThreshold: Double = arrivalThresholdMeters,
        nearMissThreshold: Double = nearMissThresholdMeters
    ): AdvancementResult {
        if (waypoints.isEmpty() || breadcrumbs.isEmpty()) {
            return AdvancementResult(advanced = false)
        }

        val targetIndex = currentWaypointIndex + 1
        if (targetIndex >= waypoints.size) {
            return AdvancementResult(advanced = false)
        }

        // If target waypoint index changed, reset tracking state for the new target
        if (lastTrackedTargetIndex != targetIndex) {
            lastTrackedTargetIndex = targetIndex
            minDistanceToTarget = Double.MAX_VALUE
            consecutiveMovingOnwardCount = 0
            previousDistanceToTarget = null
            previousDistanceToNext = null
        }

        val lastBc = breadcrumbs.last()
        val targetWp = waypoints[targetIndex]
        val nextTargetWp = if (targetIndex + 1 < waypoints.size) waypoints[targetIndex + 1] else null

        val effectiveArrivalThreshold = targetWp.threshold ?: arrivalThreshold
        val thresholdDelta = nearMissThreshold - arrivalThreshold
        val effectiveNearMissThreshold = targetWp.threshold?.let { it + thresholdDelta } ?: nearMissThreshold

        val currentDistToTarget = CoordinateUtils.calculateDistance(lastBc.x, lastBc.y, targetWp.x, targetWp.y)
        minDistanceToTarget = min(minDistanceToTarget, currentDistToTarget)

        // Condition A: Direct Arrival
        if (currentDistToTarget <= effectiveArrivalThreshold) {
            val newIndex = currentWaypointIndex + 1
            val announcement = generateAnnouncement(targetWp, nextTargetWp)
            reset()
            return AdvancementResult(
                advanced = true,
                reachedWaypoint = targetWp,
                nextTargetWaypoint = nextTargetWp,
                newIndex = newIndex,
                reason = AdvancementReason.DirectArrival,
                ttsAnnouncement = announcement
            )
        }

        // Condition B: Near-Miss & Moving Onward
        // B1: Passed within near-miss window (effectiveArrivalThreshold < minDistanceToTarget <= effectiveNearMissThreshold)
        val isNearMissWindow = minDistanceToTarget > effectiveArrivalThreshold && minDistanceToTarget <= effectiveNearMissThreshold

        // B2: 10 consecutive breadcrumbs distance to W_target increasing AND distance to W_next decreasing
        if (nextTargetWp != null) {
            val currentDistToNext = CoordinateUtils.calculateDistance(lastBc.x, lastBc.y, nextTargetWp.x, nextTargetWp.y)

            val prevDistTarget = previousDistanceToTarget
            val prevDistNext = previousDistanceToNext

            if (prevDistTarget != null && prevDistNext != null) {
                val isDistTargetIncreasing = currentDistToTarget > prevDistTarget
                val isDistNextDecreasing = currentDistToNext < prevDistNext

                if (isDistTargetIncreasing && isDistNextDecreasing) {
                    consecutiveMovingOnwardCount++
                } else {
                    consecutiveMovingOnwardCount = 0
                }
            } else {
                consecutiveMovingOnwardCount = 0
            }

            previousDistanceToNext = currentDistToNext

            if (isNearMissWindow && consecutiveMovingOnwardCount >= requiredConsecutiveMovingOnwardFixes) {
                val newIndex = currentWaypointIndex + 1
                val announcement = generateAnnouncement(targetWp, nextTargetWp)
                val reason = AdvancementReason.NearMissAndMovingOnward(
                    minDistance = minDistanceToTarget,
                    consecutiveFixes = consecutiveMovingOnwardCount
                )
                reset()
                return AdvancementResult(
                    advanced = true,
                    reachedWaypoint = targetWp,
                    nextTargetWaypoint = nextTargetWp,
                    newIndex = newIndex,
                    reason = reason,
                    ttsAnnouncement = announcement
                )
            }
        }

        previousDistanceToTarget = currentDistToTarget

        return AdvancementResult(advanced = false)
    }

    private fun generateAnnouncement(reached: Waypoint, next: Waypoint?): String {
        return if (next != null) {
            "Reached ${reached.name}. Next target is ${next.name}."
        } else {
            "Reached ${reached.name}. Route completed."
        }
    }
}
