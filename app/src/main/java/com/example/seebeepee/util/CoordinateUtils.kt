package com.example.seebeepee.util

import android.content.Context
import android.util.Log
import com.example.seebeepee.model.Breadcrumb
import org.json.JSONObject
import java.util.Locale
import kotlin.math.*

// --- Public Data Classes ---

/**
 * Represents a geographical coordinate point using WGS84 latitude and longitude.
 *
 * @property latitude Latitude in decimal degrees (e.g., 53.3498).
 * @property longitude Longitude in decimal degrees (e.g., -6.2603).
 */
data class LatLon(val latitude: Double, val longitude: Double)

/**
 * Represents an official Irish National Grid Reference coordinate.
 *
 * @property letter The 100km grid square letter (e.g., 'O' for Dublin region).
 * @property easting The local easting within the 100km square in meters (0.0 to 99999.9).
 * @property northing The local northing within the 100km square in meters (0.0 to 99999.9).
 */
data class IrishGridReference(
    val letter: Char,
    val easting: Double,
    val northing: Double
) {
    /**
     * Formats the grid reference into standard string notation (e.g., "O 16342 34211").
     */
    override fun toString(): String {
        val e = easting.toInt().toString().padStart(5, '0')
        val n = northing.toInt().toString().padStart(5, '0')
        return "$letter $e$n"
    }
}

/**
 * Represents absolute full cartesian coordinates (Easting and Northing) in meters.
 */
data class FullCoordinates(val x: Double, val y: Double)

/**
 * Represents local surveying offset corrections in meters.
 */
data class GridOffset(val eastingOffset: Double, val northingOffset: Double)

// --- Internal Configuration Maps ---

private val letterCorrectionMap = mapOf(
    'A' to GridOffset(70.07, 146.81), 'B' to GridOffset(86.22, 147.32),
    'C' to GridOffset(106.15, 148.39), 'D' to GridOffset(121.62, 149.54),
    'F' to GridOffset(70.73, 125.83), 'G' to GridOffset(87.12, 126.65),
    'H' to GridOffset(107.14, 127.72), 'J' to GridOffset(122.88, 128.59),
    'L' to GridOffset(71.34, 102.25), 'M' to GridOffset(88.16, 103.47),
    'N' to GridOffset(108.22, 104.51), 'O' to GridOffset(124.39, 105.09),
    'Q' to GridOffset(72.04, 78.80), 'R' to GridOffset(89.28, 80.07),
    'S' to GridOffset(109.23, 81.13), 'T' to GridOffset(125.81, 81.69),
    'V' to GridOffset(72.71, 57.07), 'W' to GridOffset(90.45, 58.27),
    'X' to GridOffset(110.09, 59.33), 'Y' to GridOffset(127.15, 59.99)
)

private val dynamic10kmOffsetMap = mutableMapOf<String, GridOffset>()

// --- Public Initialization API ---

/**
 * Loads the 10km grid offset JSON file from the Android assets directory.
 */
fun loadIrishGridOffsets(context: Context, fileName: String = "irish_grid_offsets_10km.json") {
    try {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)

        dynamic10kmOffsetMap.clear()
        for (key in jsonObject.keys()) {
            val array = jsonObject.getJSONArray(key)
            dynamic10kmOffsetMap[key] = GridOffset(array.getDouble(0), array.getDouble(1))
        }
        Log.d("CoordinateUtils", "Loaded ${dynamic10kmOffsetMap.size} 10km grid offsets.")
    } catch (e: Exception) {
        Log.e("CoordinateUtils", "Failed to load 10km offsets: ${e.message}. Using 100km fallbacks.")
    }
}

// --- Main Public Conversion Object ---

object IrishGridConverter {
    private const val A = 6377340.189
    private const val B = 6356034.446
    private const val F0 = 1.000035

    private const val LAT0 = 53.5
    private const val LON0 = -8.0
    private const val N0 = 250000.0
    private const val E0 = 200000.0

    private const val WGS84_A = 6378137.0
    private const val WGS84_B = 6356752.3142

    private val gridLetters = arrayOf(
        arrayOf('A', 'B', 'C', 'D', 'E'),
        arrayOf('F', 'G', 'H', 'J', 'K'),
        arrayOf('L', 'M', 'N', 'O', 'P'),
        arrayOf('Q', 'R', 'S', 'T', 'U'),
        arrayOf('V', 'W', 'X', 'Y', 'Z')
    )

    fun Wgs84ToIrishGrid(latLon: LatLon): IrishGridReference {
        val irl75 = wgs84ToIrl75Internal(latLon)
        val rawRef = projectToGridInternal(irl75)

        val correction = getBestOffsetInternal(rawRef.letter, rawRef.easting, rawRef.northing)
        return IrishGridReference(
            letter = rawRef.letter,
            easting = rawRef.easting + correction.eastingOffset,
            northing = rawRef.northing + correction.northingOffset
        )
    }

    fun IrishGridToWgs84(gridRefStr: String): LatLon {
        val clean = gridRefStr.replace(Regex("[^A-Z0-9]"), "").uppercase()
        require(clean.length >= 2) { "Invalid grid reference: $gridRefStr" }
        val letter = clean[0]

        val rawXY = parseGridReferenceToXYInternal(clean)
        val roughLocalE = rawXY.x - getGridLetterEastingOffsetInternal(letter)
        val roughLocalN = rawXY.y % 100000.0

        val correction = getBestOffsetInternal(letter, roughLocalE, roughLocalN)
        val unadjustedX = rawXY.x - correction.eastingOffset
        val unadjustedY = rawXY.y - correction.northingOffset

        val irl75 = inverseProjectToLatLonInternal(unadjustedX, unadjustedY)
        return irl75ToWgs84Internal(irl75)
    }

    private fun getBestOffsetInternal(letter: Char, localEasting: Double, localNorthing: Double): GridOffset {
        val eastBlock = (localEasting / 10000).toInt().coerceIn(0, 9)
        val northBlock = (localNorthing / 10000).toInt().coerceIn(0, 9)
        val cellKey = "$letter-$eastBlock-$northBlock"

        return dynamic10kmOffsetMap[cellKey] ?: letterCorrectionMap[letter] ?: GridOffset(0.0, 0.0)
    }

    private fun getGridLetterEastingOffsetInternal(letter: Char): Double {
        val columns = mapOf(
            'A' to 0, 'F' to 0, 'L' to 0, 'Q' to 0, 'V' to 0,
            'B' to 1, 'G' to 1, 'M' to 1, 'R' to 1, 'W' to 1,
            'C' to 2, 'H' to 2, 'N' to 2, 'S' to 2, 'X' to 2,
            'D' to 3, 'J' to 3, 'O' to 3, 'T' to 3, 'Y' to 3,
            'E' to 4, 'K' to 4, 'P' to 4, 'U' to 4, 'Z' to 4
        )
        return ((columns[letter.uppercaseChar()] ?: 0) * 100000).toDouble()
    }

    private fun parseGridReferenceToXYInternal(cleanRef: String): FullCoordinates {
        val letter = cleanRef[0]
        val numericPart = cleanRef.substring(1)
        val halfLen = numericPart.length / 2

        val localE = numericPart.substring(0, halfLen).padEnd(5, '0').take(5).toDouble()
        val localN = numericPart.substring(halfLen).padEnd(5, '0').take(5).toDouble()

        var baseE = 0.0
        var baseN = 0.0
        outer@ for (r in gridLetters.indices) {
            for (c in gridLetters[r].indices) {
                if (gridLetters[r][c] == letter) {
                    baseE = c * 100000.0
                    baseN = (4 - r) * 100000.0
                    break@outer
                }
            }
        }
        return FullCoordinates(baseE + localE, baseN + localN)
    }

    private fun wgs84ToIrl75Internal(latLon: LatLon): LatLon {
        val lat1 = Math.toRadians(latLon.latitude)
        val lon1 = Math.toRadians(latLon.longitude)
        val eSq1 = (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_A * WGS84_A)
        val nu1 = WGS84_A / sqrt(1.0 - eSq1 * sin(lat1) * sin(lat1))
        val x1 = nu1 * cos(lat1) * cos(lon1)
        val y1 = nu1 * cos(lat1) * sin(lon1)
        val z1 = nu1 * (1.0 - eSq1) * sin(lat1)

        val x2 = (x1 - (-482.530)) / (1.0 - 8.15e-6) + Math.toRadians(0.631 / 3600.0) * y1 - Math.toRadians(0.214 / 3600.0) * z1
        val y2 = (y1 - 130.596) / (1.0 - 8.15e-6) - Math.toRadians(0.631 / 3600.0) * x1 + Math.toRadians(1.042 / 3600.0) * z1
        val z2 = (z1 - (-564.557)) / (1.0 - 8.15e-6) + Math.toRadians(0.214 / 3600.0) * x1 - Math.toRadians(1.042 / 3600.0) * y1

        val eSq2 = (A * A - B * B) / (A * A)
        val p = sqrt(x2 * x2 + y2 * y2)
        var lat2 = atan2(z2, p * (1.0 - eSq2))
        var lon2 = atan2(y2, x2)
        for (i in 0..4) {
            val nu2 = A / sqrt(1.0 - eSq2 * sin(lat2) * sin(lat2))
            lat2 = atan2(z2 + eSq2 * nu2 * sin(lat2), p)
        }
        return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun irl75ToWgs84Internal(latLon: LatLon): LatLon {
        val lat1 = Math.toRadians(latLon.latitude)
        val lon1 = Math.toRadians(latLon.longitude)
        val eSq1 = (A * A - B * B) / (A * A)
        val nu1 = A / sqrt(1.0 - eSq1 * sin(lat1) * sin(lat1))
        val x1 = nu1 * cos(lat1) * cos(lon1)
        val y1 = nu1 * cos(lat1) * sin(lon1)
        val z1 = nu1 * (1.0 - eSq1) * sin(lat1)

        val x2 = -482.530 + (1.0 + 8.15e-6) * x1 - Math.toRadians(-0.631 / 3600.0) * y1 + Math.toRadians(-0.214 / 3600.0) * z1
        val y2 = 130.596 + Math.toRadians(-0.631 / 3600.0) * x1 + (1.0 + 8.15e-6) * y1 - Math.toRadians(-1.042 / 3600.0) * z1
        val z2 = -564.557 - Math.toRadians(-0.214 / 3600.0) * x1 + Math.toRadians(-1.042 / 3600.0) * y1 + (1.0 + 8.15e-6) * z1

        val eSq2 = (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_A * WGS84_A)
        val p = sqrt(x2 * x2 + y2 * y2)
        var lat2 = atan2(z2, p * (1.0 - eSq2))
        var lon2 = atan2(y2, x2)
        for (i in 0..4) {
            val nu2 = WGS84_A / sqrt(1.0 - eSq2 * sin(lat2) * sin(lat2))
            lat2 = atan2(z2 + eSq2 * nu2 * sin(lat2), p)
        }
        return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun computeMeridionalArc(lat: Double, lat0: Double, b: Double, n: Double): Double {
        val n2 = n * n
        val n3 = n * n * n
        val ma = (1.0 + n + (5.0 / 4.0) * n2 + (5.0 / 4.0) * n3) * (lat - lat0)
        val mb = (3.0 * n + 3.0 * n2 + (21.0 / 8.0) * n3) * sin(lat - lat0) * cos(lat + lat0)
        val mc = ((15.0 / 8.0) * n2 + (15.0 / 8.0) * n3) * sin(2.0 * (lat - lat0)) * cos(2.0 * (lat + lat0))
        val md = (35.0 / 24.0) * n3 * sin(3.0 * (lat - lat0)) * cos(3.0 * (lat + lat0))
        return b * (ma - mb + mc - md)
    }

    private fun projectToGridInternal(latLon: LatLon): IrishGridReference {
        val latRad = Math.toRadians(latLon.latitude)
        val lonRad = Math.toRadians(latLon.longitude)
        val phi0 = Math.toRadians(LAT0)
        val lambda0 = Math.toRadians(LON0)

        val e2 = (A * A - B * B) / (A * A)
        val n = (A - B) / (A + B)
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val tanLat = tan(latRad)

        val nu = A * F0 / sqrt(1.0 - e2 * sinLat * sinLat)
        val rho = A * F0 * (1.0 - e2) / (1.0 - e2 * sinLat * sinLat).pow(1.5)
        val m = computeMeridionalArc(latRad, phi0, B, n)
        val dLambda = lonRad - lambda0

        val term1 = nu * cosLat * dLambda
        val term2 = nu * cosLat.pow(3) * (nu / rho - tanLat * tanLat) * dLambda.pow(3) / 6.0
        val term3 = nu * cosLat.pow(5) * (5.0 - 18.0 * tanLat * tanLat + tanLat.pow(4)) * dLambda.pow(5) / 120.0
        val eastingTotal = E0 + F0 * (term1 + term2 + term3)

        val nTerm1 = m
        val nTerm2 = nu * sinLat * cosLat * dLambda.pow(2) / 2.0
        val nTerm3 = nu * sinLat * cosLat.pow(3) * (5.0 - tanLat * tanLat + 9.0 * (nu / rho - 1.0)) * dLambda.pow(4) / 24.0
        val northingTotal = N0 + F0 * (nTerm1 + nTerm2 + nTerm3)

        val col = (eastingTotal / 100000.0).toInt().coerceIn(0, 4)
        val row = (4 - (northingTotal / 100000.0).toInt()).coerceIn(0, 4)

        return IrishGridReference(gridLetters[row][col], eastingTotal % 100000.0, northingTotal % 100000.0)
    }

    private fun inverseProjectToLatLonInternal(x: Double, y: Double): LatLon {
        val phi0 = Math.toRadians(LAT0)
        val lambda0 = Math.toRadians(LON0)
        val e2 = (A * A - B * B) / (A * A)
        val n = (A - B) / (A + B)

        var phiPrime = phi0 + (y - N0) / (A * F0)
        var m: Double
        do {
            m = computeMeridionalArc(phiPrime, phi0, B, n)
            phiPrime = (y - N0 - (m * F0)) / (A * F0) + phiPrime
        } while (abs(y - N0 - (m * F0)) >= 0.00001)

        val sinPhi = sin(phiPrime)
        val cosPhi = cos(phiPrime)
        val tanPhi = tan(phiPrime)
        val nuPrime = A * F0 / sqrt(1.0 - e2 * sinPhi * sinPhi)
        val rhoPrime = A * F0 * (1.0 - e2) / (1.0 - e2 * sinPhi * sinPhi).pow(1.5)
        val etaSqPrime = nuPrime / rhoPrime - 1.0

        val xRel = x - E0
        val secPhi = 1.0 / cosPhi
        val vii = tanPhi / (2.0 * rhoPrime * nuPrime * F0 * F0)
        val viii = tanPhi / (24.0 * rhoPrime * nuPrime.pow(3) * F0.pow(4)) * (5.0 + 3.0 * tanPhi * tanPhi + etaSqPrime)

        val lat = phiPrime - vii * xRel.pow(2) + viii * xRel.pow(4)
        val xXi = secPhi / (nuPrime * F0)
        val xXii = secPhi / (6.0 * nuPrime.pow(3) * F0.pow(3)) * (nuPrime / rhoPrime + 2.0 * tanPhi * tanPhi)

        val lon = lambda0 + xXi * xRel - xXii * xRel.pow(3)

        return LatLon(Math.toDegrees(lat), Math.toDegrees(lon))
    }
}

// --- CoordinateUtils Object integrating IrishGridConverter ---

object CoordinateUtils {

    /**
     * Converts WGS84 latitude and longitude into Irish National Grid X and Y coordinates (meters).
     */
    fun latLonToMetric(lat: Double, lon: Double): Pair<Double, Double> {
        val gridRef = IrishGridConverter.Wgs84ToIrishGrid(LatLon(lat, lon))
        var baseE = 0.0
        var baseN = 0.0
        val gridLetters = arrayOf(
            arrayOf('A', 'B', 'C', 'D', 'E'),
            arrayOf('F', 'G', 'H', 'J', 'K'),
            arrayOf('L', 'M', 'N', 'O', 'P'),
            arrayOf('Q', 'R', 'S', 'T', 'U'),
            arrayOf('V', 'W', 'X', 'Y', 'Z')
        )
        outer@ for (r in gridLetters.indices) {
            for (c in gridLetters[r].indices) {
                if (gridLetters[r][c] == gridRef.letter) {
                    baseE = (c * 100000.0)
                    baseN = ((4 - r) * 100000.0)
                    break@outer
                }
            }
        }
        val x = baseE + gridRef.easting
        val y = baseN + gridRef.northing
        return Pair(x, y)
    }

    /**
     * Converts Irish National Grid X and Y coordinates (meters) into WGS84 latitude and longitude.
     */
    fun metricToLatLon(x: Double, y: Double): Pair<Double, Double> {
        val col = (x / 100000.0).toInt().coerceIn(0, 4)
        val row = (4 - (y / 100000.0).toInt()).coerceIn(0, 4)
        val gridLetters = arrayOf(
            arrayOf('A', 'B', 'C', 'D', 'E'),
            arrayOf('F', 'G', 'H', 'J', 'K'),
            arrayOf('L', 'M', 'N', 'O', 'P'),
            arrayOf('Q', 'R', 'S', 'T', 'U'),
            arrayOf('V', 'W', 'X', 'Y', 'Z')
        )
        val letter = gridLetters[row][col]
        val localE = x % 100000.0
        val localN = y % 100000.0
        val gridRefStr = String.format(Locale.US, "%c%05d%05d", letter, localE.toInt(), localN.toInt())
        val latLon = IrishGridConverter.IrishGridToWgs84(gridRefStr)
        return Pair(latLon.latitude, latLon.longitude)
    }

    /**
     * Parses grid references like "V 860 870", "V860 870", or "V860870".
     */
    fun parseGridReference(ref: String): Pair<Double, Double> {
        val cleaned = ref.trim().uppercase()
        if (cleaned.isEmpty()) return Pair(0.0, 0.0)

        val hasLetter = cleaned[0] in 'A'..'Z'
        val letter = if (hasLetter) cleaned[0] else 'V'
        val numericPart = if (hasLetter) cleaned.substring(1).replace(Regex("\\s+"), "") else cleaned.replace(Regex("\\s+"), "")

        val halfLen = numericPart.length / 2
        val eastingStr = if (halfLen > 0) numericPart.substring(0, halfLen) else "0"
        val northingStr = if (halfLen > 0) numericPart.substring(halfLen) else numericPart

        val localE = eastingStr.padEnd(5, '0').take(5).toDoubleOrNull() ?: 0.0
        val localN = northingStr.padEnd(5, '0').take(5).toDoubleOrNull() ?: 0.0

        val gridLetters = arrayOf(
            arrayOf('A', 'B', 'C', 'D', 'E'),
            arrayOf('F', 'G', 'H', 'J', 'K'),
            arrayOf('L', 'M', 'N', 'O', 'P'),
            arrayOf('Q', 'R', 'S', 'T', 'U'),
            arrayOf('V', 'W', 'X', 'Y', 'Z')
        )
        var baseE = 0.0
        var baseN = 0.0
        outer@ for (r in gridLetters.indices) {
            for (c in gridLetters[r].indices) {
                if (gridLetters[r][c] == letter) {
                    baseE = (c * 100000.0)
                    baseN = ((4 - r) * 100000.0)
                    break@outer
                }
            }
        }

        return Pair(baseE + localE, baseN + localN)
    }

    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return hypot(x2 - x1, y2 - y1)
    }

    fun calculateBearing(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        var angle = Math.toDegrees(atan2(dx, dy))
        if (angle < 0) {
            angle += 360.0
        }
        return angle
    }

    /**
     * Normalizes an angle difference to the range [-180.0, 180.0] degrees.
     */
    fun normalizeAngle180(angle: Double): Double {
        var a = (angle + 180.0) % 360.0
        if (a < 0) a += 360.0
        return a - 180.0
    }

    /**
     * Computes the angular deviation ΔB = bHiker - bTarget normalized to [-180.0, 180.0] degrees.
     */
    fun computeAngularDeviation(bHiker: Double, bTarget: Double): Double {
        return normalizeAngle180(bHiker - bTarget)
    }

    /**
     * Computes relative bearing from hiker movement bearing bHiker to target bearing bTarget
     * (bTarget - bHiker, normalized to [-180.0, 180.0] degrees).
     */
    fun computeRelativeBearing(bHiker: Double, bTarget: Double): Double {
        return normalizeAngle180(bTarget - bHiker)
    }

    /**
     * Formats relative bearing with an explicit sign if positive (e.g., "+15", "-20", "0").
     */
    fun formatRelativeBearing(relativeBearing: Double): String {
        val rounded = relativeBearing.roundToInt()
        return if (rounded > 0) "+$rounded" else rounded.toString()
    }

    /**
     * Computes movement bearing by looking back along recorded breadcrumbs until cumulative distance
     * exceeds [loopbackDistanceMeters] (default 10 meters).
     * Returns null if fewer than 2 breadcrumbs exist or if movement distance is zero.
     */
    fun calculateLoopbackBearing(breadcrumbs: List<Breadcrumb>, loopbackDistanceMeters: Double = 10.0): Double? {
        if (breadcrumbs.size < 2) return null
        val curr = breadcrumbs.last()
        var accumulatedDist = 0.0
        var lookbackBc = breadcrumbs[breadcrumbs.size - 2]

        for (i in breadcrumbs.size - 2 downTo 0) {
            val p1 = breadcrumbs[i]
            val p2 = breadcrumbs[i + 1]
            val segDist = calculateDistance(p1.x, p1.y, p2.x, p2.y)
            accumulatedDist += segDist
            lookbackBc = p1
            if (accumulatedDist >= loopbackDistanceMeters) {
                break
            }
        }

        val directDist = calculateDistance(lookbackBc.x, lookbackBc.y, curr.x, curr.y)
        if (directDist > 0.0) {
            return calculateBearing(lookbackBc.x, lookbackBc.y, curr.x, curr.y)
        }
        return null
    }

    /**
     * Calculates the hiker's current movement bearing from recent breadcrumbs.
     * Searches backwards from the latest breadcrumb to find a previous breadcrumb at least [minDistanceMeters] away.
     * Returns null if fewer than 2 breadcrumbs exist or if movement distance is negligible.
     */
    fun calculateHikerBearing(breadcrumbs: List<Breadcrumb>, minDistanceMeters: Double = 1.0): Double? {
        return calculateLoopbackBearing(breadcrumbs, minDistanceMeters)
    }

    /**
     * Calculates the hiker's movement bearing using the current location (latest breadcrumb)
     * and the breadcrumb point 3 points further back (index breadcrumbs.size - 4).
     * If fewer than 4 breadcrumbs exist, falls back to breadcrumbs.first() if size >= 2.
     * Returns null if fewer than 2 breadcrumbs exist or if movement distance is zero.
     */
    fun calculateStabilizedHikerBearing(breadcrumbs: List<Breadcrumb>): Double? {
        if (breadcrumbs.size < 2) return null
        val curr = breadcrumbs.last()
        val prevIndex = if (breadcrumbs.size >= 4) breadcrumbs.size - 4 else 0
        val prev = breadcrumbs[prevIndex]
        val dist = calculateDistance(prev.x, prev.y, curr.x, curr.y)
        if (dist > 0.0) {
            return calculateBearing(prev.x, prev.y, curr.x, curr.y)
        }
        return null
    }

    fun calculateSpeed(b1: Breadcrumb, b2: Breadcrumb): Double {
        val distMeters = calculateDistance(b1.x, b1.y, b2.x, b2.y)
        val timeSec = (b2.timestamp - b1.timestamp) / 1000.0
        if (timeSec <= 0.0) return 0.0
        val speedMps = distMeters / timeSec
        return speedMps * 3.6 // km/h
    }

    /**
     * Calculates recent hiker speed (km/h) over recent breadcrumbs within [windowMeters] lookback window.
     * Returns 0.0 if fewer than 2 breadcrumbs exist or time elapsed is <= 0.
     */
    fun calculateRecentSpeed(breadcrumbs: List<Breadcrumb>, windowMeters: Double = 10.0): Double {
        if (breadcrumbs.size < 2) return 0.0
        val curr = breadcrumbs.last()
        var accumulatedDist = 0.0
        var lookbackBc = breadcrumbs[breadcrumbs.size - 2]

        for (i in breadcrumbs.size - 2 downTo 0) {
            val p1 = breadcrumbs[i]
            val p2 = breadcrumbs[i + 1]
            val segDist = calculateDistance(p1.x, p1.y, p2.x, p2.y)
            accumulatedDist += segDist
            lookbackBc = p1
            if (accumulatedDist >= windowMeters) {
                break
            }
        }

        val timeSec = (curr.timestamp - lookbackBc.timestamp) / 1000.0
        if (timeSec <= 0.0) return 0.0
        val distMeters = calculateDistance(lookbackBc.x, lookbackBc.y, curr.x, curr.y)
        val speedMps = distMeters / timeSec
        return speedMps * 3.6 // km/h
    }

    /**
     * Formats metric X and Y coordinates as a concise 6-digit grid reference (3 digits Easting + 3 digits Northing)
     * without the zone letter prefix (e.g., "880888"). Used specifically for TTS audio status messages.
     */
    fun formatGridReference6Digit(x: Double, y: Double): String {
        val east = (((x % 100000.0) + 100000.0) % 100000.0 / 100.0).toInt().coerceIn(0, 999)
        val north = (((y % 100000.0) + 100000.0) % 100000.0 / 100.0).toInt().coerceIn(0, 999)
        return String.format(Locale.US, "%03d%03d", east, north)
    }

    fun formatGridReference(x: Double, y: Double, precision: Int = 6): String {
        val col = (x / 100000.0).toInt().coerceIn(0, 4)
        val row = (4 - (y / 100000.0).toInt()).coerceIn(0, 4)
        val gridLetters = arrayOf(
            arrayOf('A', 'B', 'C', 'D', 'E'),
            arrayOf('F', 'G', 'H', 'J', 'K'),
            arrayOf('L', 'M', 'N', 'O', 'P'),
            arrayOf('Q', 'R', 'S', 'T', 'U'),
            arrayOf('V', 'W', 'X', 'Y', 'Z')
        )
        val letter = gridLetters[row][col]
        val localE = ((x % 100000.0) + 100000.0) % 100000.0
        val localN = ((y % 100000.0) + 100000.0) % 100000.0

        val d = when (precision) {
            10 -> 5
            8 -> 4
            6 -> 3
            else -> 3
        }

        val divisor = when (d) {
            5 -> 1.0
            4 -> 10.0
            3 -> 100.0
            2 -> 1000.0
            1 -> 10000.0
            else -> 100.0
        }

        val maxVal = 10.0.pow(d).toInt() - 1
        val east = (localE / divisor).toInt().coerceIn(0, maxVal)
        val north = (localN / divisor).toInt().coerceIn(0, maxVal)

        val formatStr = "%c%0${d}d%0${d}d"
        return String.format(Locale.US, formatStr, letter, east, north)
    }

    fun formatGridReferenceWithLetter(x: Double, y: Double, precision: Int = 6): String {
        return formatGridReference(x, y, precision)
    }
}
