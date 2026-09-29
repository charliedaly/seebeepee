package com.example.seebeepee.util

import kotlin.math.max

object NaismithEngine {
    /**
     * Estimates hiking time using Naismith's Rule with adjustments.
     * @param distanceMeters Total distance in meters.
     * @param climbMeters Total ascent (climb) in meters.
     * @param flatPaceMinPerKm Flat walking pace in minutes per kilometer (default e.g. 20.0).
     * @param climbPenaltyMinPer10m Climb penalty in minutes per 10m of ascent (default e.g. 1.0).
     * @return Estimated hiking time in minutes.
     */
    fun estimateHikingTime(
        distanceMeters: Double,
        climbMeters: Double,
        flatPaceMinPerKm: Double = 20.0,
        climbPenaltyMinPer10m: Double = 1.0
    ): Double {
        val km = distanceMeters / 1000.0
        val flatTime = km * flatPaceMinPerKm
        val positiveClimb = max(0.0, climbMeters)
        val climbTime = (positiveClimb / 10.0) * climbPenaltyMinPer10m
        return flatTime + climbTime
    }
}
