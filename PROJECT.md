# PROJECT.md — Hillwalking & Hiking Navigation App Specification

## 1. Overview
An offline-first, lightweight GPS navigation and tracking application developed for Android using **Kotlin and Jetpack Compose**, specifically tailored for hillwalking and outdoor navigation in Ireland and the UK. The app manages CSV/GPX routes, calculates hiking ETA using Naismith’s Rule with customizable pace and climb penalty parameters, tracks off-course deviations via a cone calculation, displays all metrics using 6-digit succinct grid references or Lat/Lon coordinates, and persists user state cleanly across app restarts. 

It is designed for hands-free operation. It can provide status updates to a Bluetooth headphone and, in particular, provides bearing and distance to the target waypoint when the headset play button is pressed (intercepted via Android MediaSession callbacks while running in a Foreground Service).

## 1.1 Terminology
* **waypoint:** A specific location, usually given by a name, 6-digit grid reference, internal $X/Y$ coordinates, and altitude.
* **current waypoint:** The last waypoint reached by the hiker.
* **target waypoint:** The next waypoint that the hiker is travelling to.
* **hiker:** The user of the program. The intent of the hiker is to hike a route along a sequence of waypoints.
* **Leg stats:** The bearing, distance (m) and estimated time required to reach the target waypoint.
* **Status:** Current position of the hiker, and the leg stats.
* **stats:** The cumulative stats of the hike (Distance travelled, Altitude, Time spent hiking, and percentage completion).
* **Breadcrumbs:** The current path that the hiker travels recorded at regular intervals and displayed on the map screen with speed-graded coloring.
* **Start Button** Pressed by the hiker to start the hike. The GPS foreground service is started, details are updated on the Nav screen and the map displays the breadcrumbs.
* **Stop Button** After the Start button is pressed and the hike is in progress, the button changes to a start button. When it is pressed the hike is over and breadcrumbs are saved.

## 1.2 Usage
The app will frequently be used in background mode with the phone switched off. However, it will still monitor and record GPS location via a Foreground Service and respond when the Bluetooth headset play button is pressed to speak audio status.

## 1.3 App Startup/Shutdown behaviour
The program will maintain persistent state. In particular, the route file path, file content URI, raw content string, and route name are persisted in `AppPreferences` (`lastFilePath`, `lastFileUri`, `lastRouteContent`, `lastRouteName`) whenever a user loads a CSV/GPX file or generates a sample route. On app startup, `MainViewModel` and `RouteManager` automatically check for previously saved route data and parse/reload that active route instead of falling back to default route templates. The name of the route displayed is the name of the file without the extension (or the custom route name). When picking files or loading routes via `content://` URIs, `OpenableColumns.DISPLAY_NAME` is queried via `ContentResolver` to resolve the actual filename and strip its extension (e.g., `glounthaune.csv` -> `glounthaune`) so `RouteManager.currentRouteName` displays clean titles instead of raw document IDs (e.g., `msf:1000052086`).
The current position will be the GPS location unless no position is found in which case, it will default to the start position (first waypoint). The current leg will be the first waypoint.
On app startup, `MainActivity` coordinates permission checks prior to launching the tracking Foreground Service. It should ensure that the app has appropriate permissions and if there is a problem it will send the user to the settings option to set permissions appropriately. If the permissions still aren't granted, it will indicate to the user that it can't proceed.
When the program is shutdown or the hike is stopped via the bottom-left finish icon, the user is asked if they want to save breadcrumbs as a full GPX track (`saveBreadcrumbsToGpx`). The exported file is saved directly into the original directory containing the active route file (via SAF `DocumentFile` or direct path) or the public Downloads directory (`Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`), instead of saving to app-private `/Android/data/com.example.seebeepee/files/Documents/`. Upon saving, a toast/snackbar feedback confirms the exact public folder location (e.g. `Saved to Downloads: Carrauntoohil_20260324_143000.gpx`). The saved file is named `<RouteName>_<YYYYMMDD_HHMMSS>.gpx` (route filename minus extension concatenated with date and time) and contains valid GPX XML structure (`<gpx>`, `<trk>`, `<trkseg>`, `<trkpt lat="..." lon="..."> <ele>...</ele> <time>...</time> </trkpt>`), converting internal $(x,y)$ metric coordinates back to WGS84 Latitude and Longitude via `CoordinateUtils`.

## 1.4 The hike

When the hiker is ready he presses start button. That starts the GPS and the Nav screen Top is updated with the current position and the overall hike stats are updated. The breadcrumbs will be collected and displayed on the map screen.

When the FINISH button is pressed, then hike finishes and the breadcrumbs are saved.

## 1.5 Error Handling
Any errors, e.g. missing permissions need to be prominently displayed as the user needs to know. There is no point progressing.

---

## 2. Core Settings & Hiking Algorithms

### 2.1 Coordinate Systems & Internal Precision vs Display/Audio
* **Coordinate System Setting:** `"grid"` vs `"latlon"`.
* **Grid Reference Precision Setting:** User-configurable setting in `AppPreferences` and Settings screen to select grid reference precision with options **6, 8, and 10 digits** (defaulting to 6 digits, split evenly between Easting and Northing, e.g., 6 digits = 3 Easting + 3 Northing; 8 digits = 4 Easting + 4 Northing; 10 digits = 5 Easting + 5 Northing), preceded by the zone letter (e.g. `V860870` for 6 digits, `V86008700` for 8 digits, `V8600087000` for 10 digits).
* **Internal Precision ($X/Y$):** Full-precision metric $X$ (Easting) and $Y$ (Northing) coordinates are maintained internally for all math, tracking, distance calculations, and off-course/cone computations.
* **Display & Audio Independence:** UI screens respect the user's grid reference precision and coordinate system preferences for position displays and waypoint tables. Spoken TTS audio status messages operate independently of UI settings and strictly use the concise 6-digit live grid format without the zone letter prefix.

### 2.2 Walk Time Calculation (Naismith's Rule with Adjustments)
* **Flat Pace Setting:** User-configurable flat walking speed (e.g., 20 min/km).
* **Climb Penalty Setting:** User-configurable time penalty per height gained, standardized to minutes per 10m of ascent.
* **Usage:** Used to compute leg time estimates, remaining route time, and total estimated route time.

### 2.3 Off-Course Detection & Course Correction
* **Off-Course Cone Angle Setting:** Configurable threshold angle in degrees (`AppPreferences.coneAngle`, e.g., 5° or 10°).
* **Hybrid Heading Smoothing & Low-Speed Suppression Strategy:**
  * **Distance Loopback Window ($D_{loopback}$):** Movement bearing is calculated by looking back along recorded breadcrumbs until cumulative distance exceeds $D_{loopback}$ (configurable in `AppPreferences`, default 10 meters) to filter out localized positioning noise.
  * **Vector Exponential Moving Average (Vector EMA):** Smooths directional vectors using $\vec{v}_{smooth} = \alpha \vec{v}_{new} + (1 - \alpha) \vec{v}_{old}$ where $\vec{v} = (\sin \theta, \cos \theta)$ and $\theta$ is in radians, eliminating high-frequency angular jitter and avoiding 0°/360° phase jump artifacts.
  * **Hysteresis & Persistence Gatekeeper ($N$ fixes):** Off-course deviation must persist across $N$ consecutive GPS updates (configurable in `AppPreferences`, default 2 fixes) before triggering a spoken course correction warning.
  * **Low-Speed / Stationary Suppression Guard:** Hiker speed is checked against a minimum speed threshold (`lowSpeedCutoff`, default 1.2 km/h). If hiker speed is below this cutoff, course correction warnings are suppressed entirely to avoid false alerts while stationary or walking extremely slowly.
* **Course Correction Message Format:**
  * Computes angular deviation $\Delta B = B_{hiker} - B_{target}$ (normalized to $[-180^\circ, 180^\circ]$).
  * If $|\Delta B| > \text{coneAngle}$:
    * If $\Delta B > 0$ (e.g., $B_{hiker} = 30^\circ, B_{target} = 22^\circ, \Delta B = +8^\circ$), generates message: `"Veer left 8 degrees"`.
    * If $\Delta B < 0$ (e.g., $B_{hiker} = 10^\circ, B_{target} = 22^\circ, \Delta B = -12^\circ$), generates message: `"Veer right 12 degrees"`.
* **Debounce / Cooldown:** Includes a 15-second cooldown interval between spoken course correction alerts to ensure timely warnings without voice spamming.

### 2.4 Voice & Speech Synthesis (Text-To-Speech)
* **Voice Alerts Toggle:** Enable / Disable Spoken Status.
* **TTS Initial Welcome Message:** When `TextToSpeech` engine successfully initializes (`TextToSpeech.SUCCESS` in `OnInitListener`), `TtsManager` automatically speaks: `"Welcome. Press the start button when you are ready to hike and at the first waypoint."`
* **TTS Audio Status Message Specifications:**
  * **Live GPS to Target Metrics:** Bearing, distance, and Naismith walk time are computed dynamically from the hiker's **live current position** (GPS $X/Y$) to the target waypoint (not from the current/previous waypoint).
  * **Concise 6-Digit Grid Reference:** The live current position in the audio message is formatted as a concise 6-digit grid reference (3 digits Easting + 3 digits Northing) **without the zone letter prefix** (e.g., `880888`).
  * **Concise Spoken Message Format:** The spoken status string includes bearing, distance, time, and ends with the current position spoken a digit at a time in two groups of 3: `"Bearing 214 degrees, Distance 605 meters, Time 16 minutes, Position 8 8 0, 8 8 8."`
  * **Off-Course Voice Alerts / Course Correction:** Computed automatically on new GPS updates during active hikes using the hybrid smoothed heading when speed exceeds `lowSpeedCutoff` and $|\Delta B| > \text{coneAngle}$ persists across $N$ fixes, generating `"Veer left X degrees"` ($\Delta B > 0$) or `"Veer right X degrees"` ($\Delta B < 0$) with a 15-second debounce/cooldown.
  * **Target Proximity Speech Announcement:**
    * **Proximity Threshold Setting:** Configurable `proximityThreshold` in `AppPreferences` (default 100 meters), adjustable via a slider in the Settings panel.
    * **Proximity Speech Trigger:** When a new GPS location is received during an active hike, distance `dist` to `targetWaypoint` is computed. If `dist <= proximityThreshold` (and the proximity alert has not already been spoken for this target waypoint), TTS speech triggers: `"${target.name} is ${dist.roundToInt()} m away at ${relativeBearingStr} degrees. ${target.description}"`.
    * **Relative Bearing ($relativeBearingStr$):** Relative change in direction from hiker movement bearing $B_{hiker}$ to target bearing $B_{target}$ ($B_{target} - B_{hiker}$, normalized to $[-180^\circ, 180^\circ]$), formatted with an explicit sign if positive (e.g., `+15` or `-20`).
    * **Waypoint Description:** Appends `target.description` if present and non-blank.
    * **Tracking & Reset:** Tracked via `RouteManager.lastProximityAlertWaypointIndex`, ensuring the alert fires exactly once per target waypoint and automatically resets when advancing to the next waypoint.
  * **UI Independence:** UI screens continue to follow the user's Settings preferences for coordinate system and grid reference precision, while TTS audio strictly uses the concise 6-digit live format.
* **Alert Intervals:** Spoken updates on waypoint change, off-course warnings, or periodic intervals. Spoken when the play button on the Bluetooth headset is pressed.

### 2.5 GPX & Track Logging Control
* **Auto-Track Setting:** Track logging state controlled via Settings or start/finish button.
* **GPX Export Specifications:**
  * **Directory:** Attempt to save to the directory containing the original route CSV/GPX file (via SAF `DocumentFile` or direct path) or the public Downloads directory (`Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`), falling back to app documents directory if public access is unavailable, rather than app-private `/Android/data/...` storage.
  * **Filename:** `<RouteName>_<YYYYMMDD_HHMMSS>.gpx` (route filename minus extension concatenated with date and time).
  * **User Feedback:** Displays a toast/snackbar confirming the exact public folder location (e.g. `Saved to Downloads: Carrauntoohil_20260324_143000.gpx`).
  * **GPX Format:** Valid GPX XML (`<gpx ...>`, `<trk>`, `<trkseg>`, `<trkpt lat="..." lon="..."> <ele>...</ele> <time>...</time> </trkpt>`).
  * **Coordinate Conversion:** Converts each breadcrumb $(x, y)$ back to Lat/Lon via `CoordinateUtils.metricToLatLon` for standard GPX compatibility.

---

## 3. Data Schema, Persistence & File Onboarding

### 3.1 Route File Schema (CSV & GPX)
Accepts CSV route files or GPX tracks containing grid references / latitude/longitude, altitude, name, optional `threshold` (supporting column names `threshold`, `reach_threshold`, `radius`) specifying custom arrival distance in meters for per-waypoint arrival evaluation, optional waypoint `description` (supporting column names `description`, `desc`, `waypoint_desc`, `waypoint_description`, `notes`, `comments`), and optional `legDescription` (supporting column names `leg_description`, `leg_desc`, `leg_notes`, `leg_comments`).

### 3.2 Persistent App State & Instant Persistence (`AppPreferences`)
* **Waypoint Data Model:** Each `Waypoint` contains `name`, coordinates (`x`, `y`), `altitude`, optional `threshold`, `description` (defaulting to `""`), and `legDescription` (defaulting to `""`).
* **Instant Persistence:** All app and map settings (flat pace, climb penalty, coordinate system, cone angle, voice alerts, map labels, waypoints, breadcrumbs, name, altitude, bearing toggles, as well as route auto-reload state `lastFilePath`, `lastFileUri`, `lastRouteContent`, `lastRouteName`) persist instantly via `SharedPreferences` (`AppPreferences`) as soon as changed.
* **Per-Waypoint Arrival Logic:** When evaluating arrival/advancement for the target waypoint via `WaypointAdvancementEngine`, the engine checks if a custom `threshold` is defined for that waypoint. If provided, the custom threshold is used as the arrival distance (with near-miss window adjusted accordingly); otherwise, it falls back to the default arrival threshold (20 meters).
* **Hike Completion & Breadcrumb Cleanup:** When a hike is finished and breadcrumbs are either saved (to GPX track) or discarded, `RouteManager.clearBreadcrumbs()` is invoked to fully clear active in-memory breadcrumbs and temporary tracking caches (such as `WaypointAdvancementEngine`), and `AppPreferences.isHikeActive` is set to `false`, ensuring every new hike starts with zero breadcrumbs.
* **No Apply Buttons:** There is no need to have a button to indicate that you are finished with settings. Simply press on the gear icon again. Or indeed anywhere that is not the settings screen.

### 3.3 First-Run Onboarding Lifecycle
If no valid route file is found on startup, displays an onboarding overlay with an option to open route files or generate sample routes.

---

## 4. Screen Specifications

### 4.1 Main / Navigation Screen (MainScreen)

#### Title Section & Route Header
* **Route Filename:** Displays the loaded route filename minus extension.
* **Load New Route Icon:** A "New Route" button allowing the user to load a new route file.
* **Route Summary:** Displays `Distance X.Xkm Altitude Xm Time hh:mm` (total route distance, altitude gain, and total estimated time).
* **Bottom App Bar Toolbar:** Primary action icons (Start/Finish, Map/Globe, Settings Gear) placed in a bottom app bar toolbar anchored with `.navigationBarsPadding()` above Android system navigation/gesture bars.

#### Top Section (Current Leg)
* **Format & Succinct Grid Refs:** Uses 6-digit succinct format (`Position`, `Bearing`, `Distance`, `Time`, `Target`), supporting optional leg description (`legDescription`) display where applicable.

#### Waypoint Table
* **Compact Rows:** Uses 6-digit grid references for compact layout, displaying waypoint names, descriptions (`description`), and leg descriptions (`legDescription`).
* **Header Clarifications:** Units like `(m)` for distance and minutes for time are clearly indicated in headers.
* **Highlighting:** Highlights current waypoint and anchors upcoming legs.

#### Overall Hike Statistics
* **Two-Line Format:**
  ```text
  Distance: X.Xkm Altitude: Xm
  Time: hh:mm  XX%
  ```

---

### 4.2 Map Screen (MapScreen)
* **Title Bar:** Route filename without suffix.
* **Bottom Right Icons:** Compass/nav icon (left) and gear icon (right, transparent background).
* **Grid Markings & Dynamic Viewport Gridlines:** Two digits only (no N, E, or trailing zeros). Gridlines every 1 km (with primary labels, 2f stroke width, and 0.65f opacity so primary 1km gridlines stand out distinctly) and 100m (with 1f stroke width and 0.4f opacity matching previous 1km styling so they are clearly visible, and no labels) generated dynamically across the viewport world bounds $(X_{min}, Y_{min}, X_{max}, Y_{max})$ calculated from camera offset, zoom scale, and canvas screen size so gridlines are continuously rendered on newly exposed map areas when zooming out or panning.
* **Waypoint Label Deduplication:** Waypoints with identical coordinates have their label information printed only once.
* **Map Settings & Instant Persistence:** 
  * Name, Altitude, and Bearing toggles (persisted instantly via `AppPreferences`).
* **Waypoint Bearings Rendering:** 
  * Rendered close to the originating waypoint using `maxOf(22f, len * 0.15f)` without overlapping it.
  * Uses a consistent perpendicular offset.
  * Short segments (<55 pixels) skip bearing rendering entirely to prevent clutter.
* **Buffer Area:** Extra buffer area around map canvas.
* **Minimum Map View Size:** **150.0 meters (150m x 150m)** minimum square bounding box / scale (e.g., when there is only one waypoint or default zoom) and off-map check buffer.
* **Out-of-Bounds Indicator:** Red arrow pointing towards current position when outside the map, positioned well inset from canvas edges (40 pixels padding from borders) so it is clearly visible and never clipped, with the exact distance in meters or km (e.g., `350m` or `50km`) displayed right next to the red arrow.
* **Recording Confirmation Prompt:** If user tries to start recording before current position is on the map, prompt for confirmation ("Are you sure?").
* **Speed-Graded Breadcrumbs & Line Segments:** Consecutive breadcrumbs are connected by line segments on the map canvas using the speed-graded color gradient (Red through Yellow/Green to Blue).
* **Current Location Marker:** A small blue circle drawn on the map canvas at the hiker's current live location.
* **Stabilized Heading Arrow:** Hiker's movement bearing calculated using current location and the breadcrumb 3 points further back (index `breadcrumbs.size - 4`), rendered as a tiny direction arrow on/from the current position marker.
* **Dashed Target Line:** A dashed line connecting the hiker's current live location directly to the target waypoint.
* **Target Waypoint Map Highlighting:** Identifies both `currentWaypointIndex` (`viewModel.currentIndex`) and `targetWaypointIndex` (`minOf(currentWaypointIndex + 1, waypoints.size - 1)`), rendering the target waypoint with a distinct amber/gold marker styling and a prominent accent outline/ring (`Color(0xFFFF8F00)`) so the hiker can easily identify their next destination on the 2D map canvas.

---

### 4.3 Settings Screen (SettingsScreen)
* **Navigation & Map Settings:** Categorized panels for Naismith's rule parameters, coordinate system, voice alerts, map overlays, etc.
* **Instant Persistence:** All toggles and inputs update `AppPreferences` immediately without requiring confirmation buttons.

---

## 5. Technical Guidelines
* **Single Source of Truth:** Coordinate format dictates string generation across all UI screens, tables, map gridlines, and TTS alerts.
* **Android Permissions & Lifecycle:** Strict runtime permission handling (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`).
* **Hike Lifecycle & State Management:** Robust handling of active/inactive hike states (`AppPreferences.isHikeActive`) and guaranteed cleanup of breadcrumbs and tracking engines upon finishing a hike (whether saved or discarded).
* **Tech Stack:** Native Android development using **Kotlin and Jetpack Compose** on Ubuntu 24.04, supported by Foreground Service and MediaSession integration.

---

