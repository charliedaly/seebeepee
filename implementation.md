# Hiking App Implementation Blueprint (implementation.md)

## 1. Core Architecture & Coordinate System
- **Local Metric Space:** All spatial layout, calculations, and canvas drawing use a flat Cartesian coordinate system (`x` for Easting in meters, `y` for Northing in meters), scoped within a local 100km x 100km area. This ensures simple calculations for bearing, distance, speed, and 2D canvas mapping without global scale distortion.
- **Single Source of Truth:** `RouteManager` holds all active runtime state (waypoints, breadcrumbs, current index, and application logs).
- **Strict Separation of Concerns:** UI screens only render state and capture gestures. Math, parsing, and state management live exclusively in dedicated helper classes.

## 2. Core Data Models & Factories
- **`Waypoint`**
  - **Fields:** `name` (String), `x` (Double), `y` (Double), `altitude` (Double).
  - **Responsibility:** Represents a static route stop in local metric space.
  - **Creation/Parsing:** Includes factory methods/constructors to build a Waypoint directly from a raw Grid Reference string or Lat/Lon coordinates by delegating to `CoordinateUtils`.
- **`Breadcrumb`**
  - **Fields:** `x` (Double), `y` (Double), `altitude` (Double), `timestamp` (Long).
  - **Responsibility:** Represents a historical GPS track point stored in local metric space for seamless canvas rendering and speed/coloring computations. Converted back to lat/lon when saved or exported.
  - **Creation/Parsing:** Includes a factory method to initialize from raw GPS latitude, longitude, altitude, and timestamp by converting them instantly into metric `x, y` via `CoordinateUtils`.

## 3. Spatial Utilities & Bidirectional Transformations
- **`CoordinateUtils`**
  - **Responsibility:** Centralized utility functions handling **bidirectional** coordinate transformations:
    - Lat/Lon $\leftrightarrow$ Local Metric `x, y` (Meters)
    - Grid Reference $\leftrightarrow$ Local Metric `x, y` (Meters)
  - Provides mathematical functions for distance, bearing, and speed tracking ($\Delta \text{distance} / \Delta \text{time}$).

## 4. Parsers & Engines
- **`CsvRouteParser` / Grid Parsers**
  - **Responsibility:** Translates raw files or bulk text into structured lists of `Waypoint` objects using the waypoint factory/utility methods.
- **`NaismithEngine`**
  - **Responsibility:** Pure utility functions for calculating hiking times using distance, climb, flat pace, and climb penalty parameters (`minsPer10Metres`).

## 5. State Management, Logging & Breadcrumb Lifecycle (`RouteManager`)
- Manages active runtime lists: `waypoints`, `breadcrumbs`, and `debugLogs`.
- **Centralized Error Routing:** Any subsystem error, parser failure, or background exception calls `RouteManager.logError(msg)` so it automatically routes to the debug screen.
- **Breadcrumb Lifecycle Rules:**
  - **Start Hike:** When a user initiates a new hike, existing breadcrumbs are cleared.
  - **Route Switch:** If the user loads a new route, any unsaved breadcrumbs from the current session are automatically saved.
  - **Shutdown:** Upon application close or teardown, any active unsaved breadcrumbs are persisted to disk.

## 6. UI Layer Specifications
- **`NavigationScreen`**: Displays the active route waypoint table, current leg progress, estimated Naismith times, and navigation controls.
- **`MapScreen`**: Consumes `RouteManager` data directly. Renders waypoints, active legs, and breadcrumb trails using a single, unified canvas scale factor.
- **`DebugScreen`**: Displays live `RouteManager.debugLogs` for error tracking and diagnostics.
- **Rendering Contract:** UI code must never perform raw coordinate transformations inline; it relies entirely on pre-calculated metric `x, y` values.

## 7. Persistence Layer
- **User Settings:** Lightweight configurations (flat pace, climb penalty, voice alerts, etc.) are saved and retrieved via Android `SharedPreferences`.
- **Route & Trail Data:** Completed routes and recorded breadcrumb sessions are serialized to local JSON files in internal storage upon trigger events (shutdown, route change, manual save).

## 8. Android-Specific Services & Hardware
To insulate core logic and UI from Android lifecycle volatility, hardware hooks are encapsulated into dedicated manager structures:
- **Location & GPS:** Managed via a dedicated `HikingForegroundService` that feeds local metric coordinates into `RouteManager`.
- **Permissions:** Requested gracefully at startup using modern Compose permission flows, with safe fallback handling if denied.
- **Text-to-Speech (TTS):** Wrapped in a clean utility interface for issuing verbal navigation and waypoint alerts.
- **Bluetooth Media Controls:** Intercepts hardware media button inputs (like headset remotes) to trigger actions such as logging waypoints or requesting voice updates.

## 9. Testing Strategy
- **Unit Tests (JVM-based):** Fast execution tests covering bidirectional coordinate transformations, metric distance/speed formulas, Naismith hiking time calculations, and string parsing logic.
- **Instrumentation Tests (Device/Emulator):** Integration tests verifying `SharedPreferences` persistence, JSON file export integrity, and core UI lifecycle navigation.

## 10. Anti-Drift & Preservation Rule
- **No Silent Stripping:** Any future code modifications or AI-assisted patches must preserve all existing fields, methods, and UI components defined in this blueprint. Changes must be surgical and verified against these rules.

---

## 11. Class Diagram & Signatures (Lightweight UML)

```kotlin
// Data Models & Factories
class Waypoint(val name: String, val x: Double, val y: Double, val altitude: Double) {
    fun toLatLonString(): String
    fun toGridReferenceString(): String

    companion object {
        fun fromGridReference(name: String, gridRef: String, altitude: Double): Waypoint
        fun fromLatLon(name: String, lat: Double, lon: Double, altitude: Double): Waypoint
    }
}

class Breadcrumb(val x: Double, val y: Double, val altitude: Double, val timestamp: Long) {
    fun toLatLon(): Pair<Double, Double>

    companion object {
        fun fromGps(lat: Double, lon: Double, altitude: Double, timestamp: Long): Breadcrumb
    }
}

// Utilities & Engines
object CoordinateUtils {
    fun latLonToMetric(lat: Double, lon: Double): Pair<Double, Double>
    fun metricToLatLon(x: Double, y: Double): Pair<Double, Double>
    fun parseGridReference(ref: String): Pair<Double, Double>
    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double
    fun calculateSpeed(b1: Breadcrumb, b2: Breadcrumb): Double
}

object NaismithEngine {
    fun estimateHikingTime(distanceMeters: Double, climbMeters: Double, flatPaceMinPerKm: Double, climbPenaltyMinPer10m: Double): Double
}

// State & Persistence
enum class HikeState { IDLE, ROUTE_LOADED, HIKING, PAUSED }

object RouteManager {
    val waypoints = mutableStateListOf<Waypoint>()
    val breadcrumbs = mutableStateListOf<Breadcrumb>()
    val debugLogs = mutableStateListOf<String>()
    var currentWaypointIndex: Int = 0
    var currentRouteName: String = "No Route Loaded"
    var hikeState: HikeState = HikeState.IDLE

    fun loadWaypoints(routeName: String, newWaypoints: List<Waypoint>)
    fun addBreadcrumb(breadcrumb: Breadcrumb)
    fun clearBreadcrumbs()
    fun persistUnsavedBreadcrumbs()
    fun logError(message: String)
}

class AppPreferences(context: Context) {
    var coordinateSystem: String
    var lastFilePath: String?
    var flatPace: Float
    var climbPenalty: Float
    var coneAngle: Float
    var voiceAlertsEnabled: Boolean
}

// UI & Services
@Composable fun NavigationScreen(modifier: Modifier = Modifier)
@Composable fun MapScreen(modifier: Modifier = Modifier)
@Composable fun DebugScreen(modifier: Modifier = Modifier)

class HikingForegroundService : Service() {
    // Listens to GPS, converts via CoordinateUtils, and feeds RouteManager
}


