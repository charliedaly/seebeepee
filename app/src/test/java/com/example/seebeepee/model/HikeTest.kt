// --- FILE: app/src/test/java/com/example/seebeepee/model/HikeTest.kt ---

package com.example.seebeepee.model

import org.junit.Assert.*
import org.junit.Test

class HikeTest {

    private val sampleWaypoints = listOf(
        Waypoint("Start", 0.0, 0.0, 0.0),
        Waypoint("WP1", 0.0, 100.0, 10.0), // North along Y axis
        Waypoint("WP2", 100.0, 100.0, 20.0) // East along X axis
    )

    @Test
    fun testHikeInitialization() {
        val route = Route("Test Route", sampleWaypoints)
        val hike = Hike(route)

        assertEquals(0, hike.currentIndex)
        assertFalse(hike.isFinished())
        assertEquals("WP1", hike.getCurrentTargetWaypoint()?.name)
        assertEquals("WP2", hike.getNextTargetWaypoint()?.name)
    }

    @Test
    fun testWaypointAdvancementAndFlagsReset() {
        val route = Route("Test Route", sampleWaypoints)
        val hike = Hike(route)

        hike.descriptionAnnounced = true
        hike.approachAnnouncementMade = true
        hike.lastProximityAlertWaypointIndex = 1

        val advanced = hike.advanceWaypoint()

        assertTrue(advanced)
        assertEquals(1, hike.currentIndex)
        assertFalse(hike.descriptionAnnounced)
        assertFalse(hike.approachAnnouncementMade)
        assertEquals(-1, hike.lastProximityAlertWaypointIndex)
        assertEquals("WP2", hike.getCurrentTargetWaypoint()?.name)
        assertFalse(hike.isFinished())

        // Advance to final waypoint
        val advancedAgain = hike.advanceWaypoint()
        assertTrue(advancedAgain)
        assertTrue(hike.isFinished())
        assertNull(hike.getCurrentTargetWaypoint())
    }

    @Test
    fun testProximityAlertTriggersCorrectly() {
        val route = Route("Test Route", sampleWaypoints)
        val hike = Hike(route)

        val t0 = System.currentTimeMillis()
        // Including x, y, elevation, and timestamp
        hike.addBreadcrumb(Breadcrumb(0.0, 0.0, 0.0, t0 - 5000))
        hike.addBreadcrumb(Breadcrumb(0.0, 95.0, 0.0, t0)) // 5 meters away from WP1

        val result = hike.evaluateProximity(proximityThreshold = 10.0, loopbackDistance = 10.0)

        assertTrue(result.shouldAlert)
        assertNotNull(result.message)
        assertEquals(1, result.targetWaypointIndex)

        // Subsequent check should not re-alert for the same target waypoint
        val secondResult = hike.evaluateProximity(proximityThreshold = 10.0, loopbackDistance = 10.0)
        assertFalse(secondResult.shouldAlert)
    }

    @Test
    fun testCourseCorrectionLowSpeedSuppression() {
        val route = Route("Test Route", sampleWaypoints)
        val hike = Hike(route)

        val t0 = System.currentTimeMillis()
        hike.addBreadcrumb(Breadcrumb(0.0, 0.0, 0.0, t0 - 2000))
        hike.addBreadcrumb(Breadcrumb(0.0, 0.1, 0.0, t0)) // minimal movement

        val result = hike.evaluateCourseCorrection(
            lowSpeedCutoff = 0.5,
            loopbackDistance = 10.0,
            coneAngle = 45f,
            requiredFixes = 1
        )

        assertFalse(result.shouldAlert)
        assertNull(result.message)
    }

    @Test
    fun testCourseCorrectionTriggersWhenOffCourse() {
        val route = Route("Test Route", sampleWaypoints)
        val hike = Hike(route)

        val t0 = System.currentTimeMillis()
        // Simulating moving East instead of North towards WP1 with proper timestamps and elevation
        hike.addBreadcrumb(Breadcrumb(0.0, 0.0, 0.0, t0 - 5000))
        hike.addBreadcrumb(Breadcrumb(0.0, 10.0, 0.0, t0 - 4000))
        hike.addBreadcrumb(Breadcrumb(50.0, 10.0, 0.0, t0))

        val result = hike.evaluateCourseCorrection(
            lowSpeedCutoff = 0.1,
            loopbackDistance = 5.0,
            coneAngle = 30f,
            requiredFixes = 1
        )

        assertTrue(result.shouldAlert)
        assertNotNull(result.message)
        //assertTrue("Ooops", false)
    }
}