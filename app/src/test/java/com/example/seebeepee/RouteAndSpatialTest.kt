package com.example.seebeepee

import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.model.Waypoint
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.NaismithEngine
import com.example.seebeepee.util.RouteParser
import kotlin.math.roundToInt
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
        assertEquals("V860870", CoordinateUtils.formatGridReferenceWithLetter(x, y, 6))
        assertEquals("V86008700", CoordinateUtils.formatGridReferenceWithLetter(x, y, 8))
        assertEquals("V8600087000", CoordinateUtils.formatGridReferenceWithLetter(x, y, 10))
    }

    @Test
    fun testLatLonMetricConversion() {
        val lat = 53.3498
        val lon = -6.2603
        val (x, y) = CoordinateUtils.latLonToMetric(lat, lon)
        val (backLat, backLon) = CoordinateUtils.metricToLatLon(x, y)
        assertEquals(lat, backLat, 0.0005)
        assertEquals(lon, backLon, 0.0005)
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

    @Test
    fun testRouteAutoReloadingFromContentAndFile() {
        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
        val waypoints = RouteParser.parseCsv(sampleCsv)

        RouteManager.loadWaypoints(
            routeName = "Custom Route",
            newWaypoints = waypoints,
            content = sampleCsv,
            filePath = "/tmp/fake_route.csv",
            fileUri = "content://com.android.providers.downloads.documents/document/raw%3A%2Ftmp%2Ffake_route.csv"
        )
        assertEquals("Custom Route", RouteManager.currentRouteName)
        assertEquals("/tmp/fake_route.csv", RouteManager.currentFilePath)
        assertEquals("content://com.android.providers.downloads.documents/document/raw%3A%2Ftmp%2Ffake_route.csv", RouteManager.currentFileUri)
        assertEquals(sampleCsv, RouteManager.currentRouteContent)
        assertEquals(5, RouteManager.waypoints.size)

        val tempFile = java.io.File.createTempFile("test_route", ".csv")
        tempFile.writeText(sampleCsv)

        val parsedFromFile = RouteParser.parseCsv(tempFile.readText())
        assertEquals(5, parsedFromFile.size)
        assertEquals("Start", parsedFromFile[0].name)

        tempFile.delete()
    }

    @Test
    fun testGpxRouteParsing() {
        val sampleGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="SeeBeePee Test">
              <trk>
                <name>Test GPX Track</name>
                <trkseg>
                  <trkpt lat="53.3498" lon="-6.2603">
                    <ele>100.0</ele>
                    <name>GPX Waypoint 1</name>
                  </trkpt>
                  <trkpt lat="53.3500" lon="-6.2610">
                    <ele>120.0</ele>
                    <name>GPX Waypoint 2</name>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val waypoints = RouteParser.parseGpx(sampleGpx)
        assertEquals(2, waypoints.size)
        assertEquals("GPX Waypoint 1", waypoints[0].name)
        assertEquals(100.0, waypoints[0].altitude, 0.01)
    }

    @Test
    fun testRefinedTtsAudioStatusMessage() {
        val w0 = Waypoint("Start", 88000.0, 88000.0, 100.0)
        val w1 = Waypoint("Target", 88362.0, 88486.0, 150.0)
        val waypoints = listOf(w0, w1)

        // Live breadcrumb position
        val liveBc = Breadcrumb(88025.0, 88875.0, 100.0, 100000L)
        val breadcrumbs = listOf(liveBc)

        // Concise 6-digit grid reference without letter prefix
        val pos6Digit = CoordinateUtils.formatGridReference6Digit(liveBc.x, liveBc.y)
        assertEquals("880888", pos6Digit)

        // Generate live status message
        val statusMsg = com.example.seebeepee.util.TtsManager.generateLiveStatusMessage(
            waypoints = waypoints,
            currentIndex = 0,
            breadcrumbs = breadcrumbs,
            flatPace = 20.0,
            climbPenalty = 1.0
        )

        assertTrue("Status message should start with Bearing", statusMsg.startsWith("Bearing "))
        assertTrue("Status message should contain Distance", statusMsg.contains("Distance "))
        assertTrue("Status message should contain Time", statusMsg.contains("Time "))
        assertTrue("Status message should contain Position", statusMsg.contains("Position "))
        assertTrue("Status message should end with Position 8 8 0, 8 8 8.", statusMsg.endsWith("Position 8 8 0, 8 8 8."))

        // Verify metrics are from live position (88025, 88875) to target (88362, 88486)
        val expectedDist = CoordinateUtils.calculateDistance(liveBc.x, liveBc.y, w1.x, w1.y)
        val expectedClimb = maxOf(0.0, w1.altitude - liveBc.altitude)
        val expectedTime = NaismithEngine.estimateHikingTime(expectedDist, expectedClimb, 20.0, 1.0)

        val expectedDistRounded = expectedDist.roundToInt()
        val expectedTimeRounded = expectedTime.roundToInt()

        assertTrue("Status message should contain live distance $expectedDistRounded meters.", statusMsg.contains("Distance $expectedDistRounded meters,"))
        assertTrue("Status message should contain live time $expectedTimeRounded minutes,", statusMsg.contains("Time $expectedTimeRounded minutes,"))
    }

    @Test
    fun testAngleNormalizationAndDeviation() {
        assertEquals(8.0, CoordinateUtils.normalizeAngle180(8.0), 0.001)
        assertEquals(-12.0, CoordinateUtils.normalizeAngle180(-12.0), 0.001)
        assertEquals(-10.0, CoordinateUtils.normalizeAngle180(350.0), 0.001)
        assertEquals(10.0, CoordinateUtils.normalizeAngle180(-350.0), 0.001)

        // Hiker heading 30°, Target 22° -> Deviation +8° (Veer left)
        val dev1 = CoordinateUtils.computeAngularDeviation(30.0, 22.0)
        assertEquals(8.0, dev1, 0.001)

        // Hiker heading 10°, Target 22° -> Deviation -12° (Veer right)
        val dev2 = CoordinateUtils.computeAngularDeviation(10.0, 22.0)
        assertEquals(-12.0, dev2, 0.001)
    }

    @Test
    fun testCourseCorrectionMessageGeneration() {
        // ΔB = +8°, coneAngle = 5° -> Veer left 8 degrees
        val msg1 = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(30.0, 22.0, 5.0f)
        assertEquals("Veer left 8 degrees", msg1)

        // ΔB = -12°, coneAngle = 5° -> Veer right 12 degrees
        val msg2 = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(10.0, 22.0, 5.0f)
        assertEquals("Veer right 12 degrees", msg2)

        // ΔB = +3°, coneAngle = 5° -> null (within cone)
        val msg3 = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(25.0, 22.0, 5.0f)
        assertNull(msg3)

        // Wrap around 0°/360°: Hiker 5°, Target 355° -> ΔB = +10° -> Veer left 10 degrees
        val msg4 = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(5.0, 355.0, 5.0f)
        assertEquals("Veer left 10 degrees", msg4)

        // Wrap around 0°/360°: Hiker 355°, Target 5° -> ΔB = -10° -> Veer right 10 degrees
        val msg5 = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(355.0, 5.0, 5.0f)
        assertEquals("Veer right 10 degrees", msg5)
    }

    @Test
    fun testHikerBearingCalculation() {
        val bc1 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bc2 = Breadcrumb(0.0, 10.0, 0.0, 2000L) // Moving due North (bearing 0°)
        val bearingNorth = CoordinateUtils.calculateHikerBearing(listOf(bc1, bc2))
        assertNotNull(bearingNorth)
        assertEquals(0.0, bearingNorth!!, 0.01)

        val bc3 = Breadcrumb(10.0, 0.0, 0.0, 2000L) // Moving due East (bearing 90°)
        val bearingEast = CoordinateUtils.calculateHikerBearing(listOf(bc1, bc3))
        assertNotNull(bearingEast)
        assertEquals(90.0, bearingEast!!, 0.01)

        // Single breadcrumb returns null
        assertNull(CoordinateUtils.calculateHikerBearing(listOf(bc1)))
    }

    @Test
    fun testCourseCorrectionMessageFromState() {
        val w0 = Waypoint("Start", 0.0, 0.0, 0.0)
        val w1 = Waypoint("Target", 100.0, 100.0, 0.0) // Target bearing from (0,10) is ~42.14°
        val waypoints = listOf(w0, w1)

        val bc1 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bc2 = Breadcrumb(0.0, 10.0, 0.0, 2000L) // Hiker moving North (bearing 0°)
        val breadcrumbs = listOf(bc1, bc2)

        val msg = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(
            waypoints = waypoints,
            currentIndex = 0,
            breadcrumbs = breadcrumbs,
            coneAngle = 5.0f
        )
        assertNotNull(msg)
        // bHiker = 0°, bTarget from (0,10) to (100,100) is ~48.01°, ΔB ≈ -48.01° -> "Veer right 48 degrees"
        assertEquals("Veer right 48 degrees", msg)
    }

    @Test
    fun testStabilizedHikerBearingCalculation() {
        val bc0 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bc1 = Breadcrumb(10.0, 10.0, 0.0, 2000L)
        val bc2 = Breadcrumb(20.0, 20.0, 0.0, 3000L)
        val bc3 = Breadcrumb(30.0, 30.0, 0.0, 4000L)
        val bc4 = Breadcrumb(10.0, 50.0, 0.0, 5000L) // Current position

        // 5 breadcrumbs: index 5 - 4 = 1 (bc1 at 10,10). From (10,10) to (10,50) is North (0.0°)
        val bearing = CoordinateUtils.calculateStabilizedHikerBearing(listOf(bc0, bc1, bc2, bc3, bc4))
        assertNotNull(bearing)
        assertEquals(0.0, bearing!!, 0.01)

        // Single breadcrumb returns null
        assertNull(CoordinateUtils.calculateStabilizedHikerBearing(listOf(bc0)))

        // 2 breadcrumbs: falls back to index 0
        val bearing2 = CoordinateUtils.calculateStabilizedHikerBearing(listOf(bc0, bc1))
        assertNotNull(bearing2)
        assertEquals(45.0, bearing2!!, 0.1)
    }

    @Test
    fun testVectorEmaHeadingSmoothing() {
        val smoother = com.example.seebeepee.util.HeadingSmoother(alpha = 0.5)

        // First fix at 0° (North)
        val b1 = smoother.update(0.0)
        assertEquals(0.0, b1, 0.01)

        // Second fix at 90° (East). Vector EMA with alpha=0.5:
        // v_new = (1, 0), v_old = (0, 1) -> v_smooth = (0.5, 0.5) -> angle = 45°
        val b2 = smoother.update(90.0)
        assertEquals(45.0, b2, 0.1)

        // Test pure function Vector EMA across 0°/360° boundary: 350° and 10° -> should smooth to 0°
        val smoothedBoundary = com.example.seebeepee.util.HeadingSmoother.smoothBearingVector(10.0, 350.0, alpha = 0.5)
        assertEquals(0.0, smoothedBoundary, 0.1)
    }

    @Test
    fun testPersistenceGatekeeper() {
        val gatekeeper = com.example.seebeepee.util.PersistenceGatekeeper(requiredFixes = 2)

        // Fix 1: off-course -> false (1/2)
        assertFalse(gatekeeper.update(isOffCourse = true))
        assertEquals(1, gatekeeper.currentCount)

        // Fix 2: off-course -> true (2/2)
        assertTrue(gatekeeper.update(isOffCourse = true))
        assertEquals(2, gatekeeper.currentCount)

        // Fix 3: on-course -> false, count resets to 0
        assertFalse(gatekeeper.update(isOffCourse = false))
        assertEquals(0, gatekeeper.currentCount)
    }

    @Test
    fun testDistanceLoopbackWindowBearing() {
        // Create breadcrumbs stepping 3m each North (0°)
        val bc1 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bc2 = Breadcrumb(0.0, 3.0, 0.0, 2000L)
        val bc3 = Breadcrumb(0.0, 6.0, 0.0, 3000L)
        val bc4 = Breadcrumb(0.0, 9.0, 0.0, 4000L)
        val bc5 = Breadcrumb(0.0, 12.0, 0.0, 5000L) // Total dist = 12m

        // With loopback distance 10m, lookback traverses back 12m to bc1 (0,0) -> bearing from (0,0) to (0,12) is North (0°)
        val bearing = CoordinateUtils.calculateLoopbackBearing(listOf(bc1, bc2, bc3, bc4, bc5), loopbackDistanceMeters = 10.0)
        assertNotNull(bearing)
        assertEquals(0.0, bearing!!, 0.01)
    }

    @Test
    fun testLowSpeedSuppressionGuard() {
        val w0 = Waypoint("Start", 0.0, 0.0, 0.0)
        val w1 = Waypoint("Target", 100.0, 100.0, 0.0)
        val waypoints = listOf(w0, w1)

        // Stationary breadcrumbs: moved 0.1 meter in 100 seconds = 0.0036 km/h (below 1.2 km/h cutoff)
        val bcSlow1 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bcSlow2 = Breadcrumb(0.0, 0.1, 0.0, 101000L)
        val slowBreadcrumbs = listOf(bcSlow1, bcSlow2)

        val slowSpeed = CoordinateUtils.calculateRecentSpeed(slowBreadcrumbs, windowMeters = 10.0)
        assertTrue("Slow speed should be less than 1.2 km/h", slowSpeed < 1.2)

        // Course correction message should be suppressed (return null) due to low speed
        val suppressedMsg = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(
            waypoints = waypoints,
            currentIndex = 0,
            breadcrumbs = slowBreadcrumbs,
            coneAngle = 5.0f,
            loopbackDistance = 10.0,
            lowSpeedCutoff = 1.2
        )
        assertNull("Course correction alert should be suppressed when stationary/slow", suppressedMsg)

        // Moving breadcrumbs: moved 10 meters in 5 seconds = 2 m/s = 7.2 km/h (above 1.2 km/h cutoff)
        val bcFast1 = Breadcrumb(0.0, 0.0, 0.0, 1000L)
        val bcFast2 = Breadcrumb(0.0, 10.0, 0.0, 6000L)
        val fastBreadcrumbs = listOf(bcFast1, bcFast2)

        val fastSpeed = CoordinateUtils.calculateRecentSpeed(fastBreadcrumbs, windowMeters = 10.0)
        assertTrue("Fast speed should be greater than 1.2 km/h", fastSpeed > 1.2)

        val activeMsg = com.example.seebeepee.util.TtsManager.generateCourseCorrectionMessage(
            waypoints = waypoints,
            currentIndex = 0,
            breadcrumbs = fastBreadcrumbs,
            coneAngle = 5.0f,
            loopbackDistance = 10.0,
            lowSpeedCutoff = 1.2
        )
        assertNotNull("Course correction alert should be generated when moving above low-speed cutoff", activeMsg)
    }

    @Test
    fun testHikeStatePersistenceAndRestoration() {
        RouteManager.hikeState = HikeState.IDLE
        assertEquals(HikeState.IDLE, RouteManager.hikeState)

        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
        val waypoints = RouteParser.parseCsv(sampleCsv)
        RouteManager.loadWaypoints("HikeTestRoute", waypoints)
        assertEquals(HikeState.ROUTE_LOADED, RouteManager.hikeState)

        RouteManager.hikeState = HikeState.HIKING
        assertEquals(HikeState.HIKING, RouteManager.hikeState)

        RouteManager.hikeState = HikeState.IDLE
        assertEquals(HikeState.IDLE, RouteManager.hikeState)
    }

    @Test
    fun testClearBreadcrumbsClearsBreadcrumbsAndTrackingCaches() {
        RouteManager.clearBreadcrumbs()
        assertEquals(0, RouteManager.breadcrumbs.size)

        val bc = Breadcrumb.fromGps(53.3, -6.2, 100.0, System.currentTimeMillis())
        RouteManager.addBreadcrumb(bc)
        assertEquals(1, RouteManager.breadcrumbs.size)

        RouteManager.clearBreadcrumbs()
        assertEquals(0, RouteManager.breadcrumbs.size)
        assertEquals(HikeState.IDLE, RouteManager.hikeState)
    }

    @Test
    fun testCsvParsingWithThresholdColumns() {
        val csv1 = "name,grid_ref,altitude,threshold\nStart,V860870,100,50.0"
        val parsed1 = RouteParser.parseCsv(csv1)
        assertEquals(50.0, parsed1[0].threshold!!, 0.01)

        val csv2 = "name,grid_ref,altitude,reach_threshold\nStart,V860870,100,40.0"
        val parsed2 = RouteParser.parseCsv(csv2)
        assertEquals(40.0, parsed2[0].threshold!!, 0.01)

        val csv3 = "name,grid_ref,altitude,radius\nStart,V860870,100,30.0"
        val parsed3 = RouteParser.parseCsv(csv3)
        assertEquals(30.0, parsed3[0].threshold!!, 0.01)

        val csv4 = "name,grid_ref,altitude\nStart,V860870,100"
        val parsed4 = RouteParser.parseCsv(csv4)
        assertNull(parsed4[0].threshold)
    }

    @Test
    fun testWaypointAdvancementEngineCustomThreshold() {
        val w0 = Waypoint("Start", 0.0, 0.0, 0.0)
        val w1 = Waypoint("TargetCustom", 100.0, 0.0, 0.0, threshold = 50.0)
        val w2 = Waypoint("Next", 200.0, 0.0, 0.0)
        val waypoints = listOf(w0, w1, w2)

        val engine = com.example.seebeepee.util.WaypointAdvancementEngine(arrivalThresholdMeters = 20.0)

        // Breadcrumb at x = 55.0 (distance 45m from target at 100,0)
        // Default arrival threshold is 20m (would NOT trigger arrival).
        // Custom threshold is 50m (SHOULD trigger arrival).
        val bc = Breadcrumb(55.0, 0.0, 0.0, System.currentTimeMillis())
        val result = engine.processBreadcrumb(listOf(bc), waypoints, currentWaypointIndex = 0)

        assertTrue("Should advance using custom threshold of 50m when distance is 45m", result.advanced)
        assertEquals(1, result.newIndex)
        assertEquals("TargetCustom", result.reachedWaypoint?.name)
    }

    @Test
    fun testWaypointDescriptionAndLegDescriptionFields() {
        val wpDefault = Waypoint("Start", 0.0, 0.0)
        assertEquals("", wpDefault.description)
        assertEquals("", wpDefault.legDescription)

        val wpGrid = Waypoint.fromGridReference(
            name = "GridWp",
            gridRef = "V860870",
            altitude = 120.0,
            threshold = 25.0,
            description = "Start at carpark",
            legDescription = "Follow track uphill"
        )
        assertEquals("Start at carpark", wpGrid.description)
        assertEquals("Follow track uphill", wpGrid.legDescription)

        val wpLatLon = Waypoint.fromLatLon(
            name = "LatLonWp",
            lat = 53.3498,
            lon = -6.2603,
            altitude = 50.0,
            threshold = null,
            description = "City centre point",
            legDescription = "Walk along river"
        )
        assertEquals("City centre point", wpLatLon.description)
        assertEquals("Walk along river", wpLatLon.legDescription)
    }

    @Test
    fun testCsvParsingWithDescriptionAndLegDescriptionColumns() {
        val csv1 = "name,grid_ref,altitude,description,leg_description\nStart,V860870,120,Carpark start,Steep ascent"
        val parsed1 = RouteParser.parseCsv(csv1)
        assertEquals(1, parsed1.size)
        assertEquals("Carpark start", parsed1[0].description)
        assertEquals("Steep ascent", parsed1[0].legDescription)

        val csv2 = "title,latitude,longitude,desc,leg_desc\nPoint1,53.3,-6.2,Scenic viewpoint,Forest path"
        val parsed2 = RouteParser.parseCsv(csv2)
        assertEquals(1, parsed2.size)
        assertEquals("Scenic viewpoint", parsed2[0].description)
        assertEquals("Forest path", parsed2[0].legDescription)

        val csv3 = "name,grid_ref,waypoint_desc,leg_notes\nSummit,V890895,Peak,Watch for ice"
        val parsed3 = RouteParser.parseCsv(csv3)
        assertEquals(1, parsed3.size)
        assertEquals("Peak", parsed3[0].description)
        assertEquals("Watch for ice", parsed3[0].legDescription)

        val csv4 = "name,grid_ref,notes\nGate,V905910,Close gate"
        val parsed4 = RouteParser.parseCsv(csv4)
        assertEquals(1, parsed4.size)
        assertEquals("Close gate", parsed4[0].description)
        assertEquals("", parsed4[0].legDescription)

        val csv5 = "name,grid_ref,comments\nRest,V920925,Bench available"
        val parsed5 = RouteParser.parseCsv(csv5)
        assertEquals(1, parsed5.size)
        assertEquals("Bench available", parsed5[0].description)
        assertEquals("", parsed5[0].legDescription)
    }

    @Test
    fun testForegroundServiceConstantsAndSafety() {
        assertEquals("HikingServiceChannel", com.example.seebeepee.service.HikingForegroundService.CHANNEL_ID)
        assertEquals(1001, com.example.seebeepee.service.HikingForegroundService.NOTIFICATION_ID)
        assertEquals("ACTION_STOP", com.example.seebeepee.service.HikingForegroundService.ACTION_STOP)

        RouteManager.logError("Simulated SecurityException test during startForeground")
        assertTrue(RouteManager.debugLogs.any { it.contains("Simulated SecurityException") })
    }
}

