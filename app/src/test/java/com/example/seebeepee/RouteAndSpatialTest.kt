package com.example.seebeepee

import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.model.Waypoint
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.NaismithEngine
import com.example.seebeepee.util.RouteParser
import org.junit.Assert.*
import org.junit.Test

class RouteAndSpatialTest {

    @Test
    fun testGridReferenceParsing() {
        val (x, y) = CoordinateUtils.parseGridReference("V860870")
        assertEquals(86000.0, x, 0.01)
        assertEquals(87000.0, y, 0.01)

        val (x2, y2) = CoordinateUtils.parseGridReference("V 860 870")
        assertEquals(86000.0, x2, 0.01)
        assertEquals(87000.0, y2, 0.01)

        val (x3, y3) = CoordinateUtils.parseGridReference("V860870")
        assertEquals(86000.0, x3, 0.01)
    }

    @Test
    fun testGridReferenceFormatting() {
        val x = 86000.0
        val y = 87000.0
        assertEquals("860870", CoordinateUtils.formatGridReference6Digit(x, y))
        assertEquals("V860870", CoordinateUtils.formatGridReferenceWithLetter(x, y))
    }

    @Test
    fun testLatLonMetricConversion() {
        val lat = 53.3498
        val lon = -6.2603
        val (x, y) = CoordinateUtils.latLonToMetric(lat, lon)
        val (backLat, backLon) = CoordinateUtils.metricToLatLon(x, y)
        assertEquals(lat, backLat, 0.0001)
        assertEquals(lon, backLon, 0.0001)
    }

    @Test
    fun testSpatialMath() {
        val dist = CoordinateUtils.calculateDistance(0.0, 0.0, 300.0, 400.0)
        assertEquals(500.0, dist, 0.01)

        val bearing = CoordinateUtils.calculateBearing(0.0, 0.0, 0.0, 100.0)
        assertEquals(0.0, bearing, 0.1) // North

        val b1 = Breadcrumb(0.0, 0.0, 10.0, 1000L)
        val b2 = Breadcrumb(10.0, 0.0, 10.0, 2000L) // 10 meters in 1 second = 10 m/s = 36 km/h
        val speed = CoordinateUtils.calculateSpeed(b1, b2)
        assertEquals(36.0, speed, 0.1)
    }

    @Test
    fun testNaismithEngine() {
        // 5 km, 200m climb. Flat pace 20 min/km = 100 min. Climb penalty 1 min per 10m (200m / 10 = 20 min) = 120 min.
        val time = NaismithEngine.estimateHikingTime(5000.0, 200.0, 20.0, 1.0)
        assertEquals(120.0, time, 0.01)
    }

    @Test
    fun testRouteParserAndSampleCsv() {
        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
        assertNotNull(sampleCsv)
        assertTrue(sampleCsv.contains("V860870"))

        val waypoints = RouteParser.parseCsv(sampleCsv)
        assertEquals(5, waypoints.size)
        assertEquals("Start", waypoints[0].name)
        assertEquals(86000.0, waypoints[0].x, 0.01)
    }

    @Test
    fun testRouteManager() {
        RouteManager.clearBreadcrumbs()
        assertEquals(0, RouteManager.breadcrumbs.size)

        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
        val waypoints = RouteParser.parseCsv(sampleCsv)
        RouteManager.loadWaypoints("TestRoute", waypoints)

        assertEquals("TestRoute", RouteManager.currentRouteName)
        assertEquals(5, RouteManager.waypoints.size)
        assertEquals(HikeState.ROUTE_LOADED, RouteManager.hikeState)

        val bc = Breadcrumb.fromGps(53.3, -6.2, 100.0, System.currentTimeMillis())
        RouteManager.addBreadcrumb(bc)
        assertEquals(1, RouteManager.breadcrumbs.size)

        RouteManager.logError("Test error log")
        assertTrue(RouteManager.debugLogs.isNotEmpty())
    }

    @Test
    fun testNaismithLegCalculations() {
        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
        val waypoints = RouteParser.parseCsv(sampleCsv)
        RouteManager.loadWaypoints("NaismithTestRoute", waypoints)

        val w1 = waypoints[0]
        val w2 = waypoints[1]
        val dist = CoordinateUtils.calculateDistance(w1.x, w1.y, w2.x, w2.y)
        val climb = maxOf(0.0, w2.altitude - w1.altitude)
        val time = NaismithEngine.estimateHikingTime(dist, climb, 20.0, 1.0)
        assertTrue(time > 0.0)
    }
}
