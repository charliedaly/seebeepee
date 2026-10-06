package com.example.seebeepee.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Implements Vector Exponential Moving Average (Vector EMA) smoothing for directional bearings.
 */
class HeadingSmoother(val alpha: Double = 0.3) {
    private var smoothedVx: Double? = null
    private var smoothedVy: Double? = null

    private fun normalizeAngle(rawDegrees: Double): Double {
        var angle = Math.toDegrees(rawDegrees) % 360.0
        if (angle < 0) {
            angle += 360.0
        }
        if (angle >= 360.0 || abs(angle - 360.0) < 1e-9) {
            angle = 0.0
        }
        return angle
    }

    /**
     * Updates the smoothed heading with a new raw bearing angle in degrees [0, 360).
     * Uses Vector Exponential Moving Average: v_smooth = alpha * v_new + (1 - alpha) * v_old
     * where v = (sin(radians(theta)), cos(radians(theta))).
     * Returns the smoothed bearing angle in degrees [0, 360).
     */
    fun update(newBearingDegrees: Double): Double {
        val rad = Math.toRadians(newBearingDegrees)
        val newVx = sin(rad)
        val newVy = cos(rad)

        val curVx = smoothedVx
        val curVy = smoothedVy

        val (nextVx, nextVy) = if (curVx == null || curVy == null) {
            Pair(newVx, newVy)
        } else {
            Pair(
                alpha * newVx + (1.0 - alpha) * curVx,
                alpha * newVy + (1.0 - alpha) * curVy
            )
        }

        smoothedVx = nextVx
        smoothedVy = nextVy

        return normalizeAngle(atan2(nextVx, nextVy))
    }

    val currentSmoothedBearing: Double?
        get() {
            val vx = smoothedVx ?: return null
            val vy = smoothedVy ?: return null
            return normalizeAngle(atan2(vx, vy))
        }

    fun reset() {
        smoothedVx = null
        smoothedVy = null
    }

    companion object {
        /**
         * Pure function for Vector EMA smoothing on a single step.
         */
        fun smoothBearingVector(
            newBearingDegrees: Double,
            oldBearingDegrees: Double,
            alpha: Double = 0.3
        ): Double {
            val newRad = Math.toRadians(newBearingDegrees)
            val oldRad = Math.toRadians(oldBearingDegrees)

            val newVx = sin(newRad)
            val newVy = cos(newRad)
            val oldVx = sin(oldRad)
            val oldVy = cos(oldRad)

            val smoothedVx = alpha * newVx + (1.0 - alpha) * oldVx
            val smoothedVy = alpha * newVy + (1.0 - alpha) * oldVy

            var angle = Math.toDegrees(atan2(smoothedVx, smoothedVy)) % 360.0
            if (angle < 0) {
                angle += 360.0
            }
            if (angle >= 360.0 || abs(angle - 360.0) < 1e-9) {
                angle = 0.0
            }
            return angle
        }
    }
}

/**
 * Gatekeeper enforcing that off-course deviation must persist across [requiredFixes]
 * consecutive GPS updates before triggering an alert.
 */
class PersistenceGatekeeper(val requiredFixes: Int = 2) {
    private var consecutiveOffCourseCount: Int = 0

    val currentCount: Int
        get() = consecutiveOffCourseCount

    /**
     * Updates gatekeeper state with whether current fix is off-course.
     * Returns true if off-course condition has persisted for at least [requiredFixes] consecutive updates.
     */
    fun update(isOffCourse: Boolean): Boolean {
        return if (isOffCourse) {
            consecutiveOffCourseCount++
            consecutiveOffCourseCount >= requiredFixes
        } else {
            consecutiveOffCourseCount = 0
            false
        }
    }

    fun reset() {
        consecutiveOffCourseCount = 0
    }
}
