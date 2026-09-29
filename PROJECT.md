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
The program will maintain persistent state. In particular, the waypoints will come from a file that was loaded previously. The name of the route will be the name of the file without the extension.
The current position will be the GPS location unless no position is found in which case, it will default to the start position (first waypoint). The current leg will be the first waypoint.
On app startup, `MainActivity` coordinates permission checks prior to launching the tracking Foreground Service. It should ensure that the app has appropriate permissions and if there is a problem it will send the user to the settings option to set permissions appropriately. If the permissions still aren't granted, it will indicate to the user that it can't proceed.
When the program is shutdown or the hike is stopped via the bottom-left finish icon, the user is asked if they want to save breadcrumbs as a full GPX track (including timestamps, elevation, and coordinates per trackpoint) with the name of the route followed by the date in yyyymmdd format.

## 1.4 The hike

When the hiker is ready he presses start button. That starts the GPS and the Nav screen Top is updated with the current position and the overall hike stats are updated. The breadcrumbs will be collected and displayed on the map screen.

When the FINISH button is pressed, then hike finishes and the breadcrumbs are saved.

## 1.5 Error Handling
Any errors, e.g. missing permissions need to be prominently displayed as the user needs to know. There is no point progressing.

---

## 2. Core Settings & Hiking Algorithms

### 2.1 Coordinate Systems & Internal Precision vs Display/Audio
* **Coordinate System Setting:** `"grid"` vs `"latlon"`.
* **Internal Precision ($X/Y$):** Full-precision metric $X$ (Easting) and $Y$ (Northing) coordinates are maintained internally for all math, tracking, distance calculations, and off-course/cone computations.
* **Display & Audio (6-Digit Grid References):** 6-digit grid references (3 digits Easting, 3 digits Northing, omitting letter prefix e.g., `880888`) are used strictly for UI display and TTS audio announcements.

### 2.2 Walk Time Calculation (Naismith's Rule with Adjustments)
* **Flat Pace Setting:** User-configurable flat walking speed (e.g., 20 min/km).
* **Climb Penalty Setting:** User-configurable time penalty per height gained, standardized to minutes per 10m of ascent.
* **Usage:** Used to compute leg time estimates, remaining route time, and total estimated route time.

### 2.3 Off-Course Detection
* **Off-Course Cone Angle Setting:** Configurable threshold angle in degrees.
* **Behavior:** Triggers off-course visual alerts and voice warnings when the bearing between the current track vector and the target waypoint exceeds the cone angle.

### 2.4 Voice & Speech Synthesis (Text-To-Speech)
* **Voice Alerts Toggle:** Enable / Disable Spoken Status.
* **Alert Intervals:** Spoken updates on waypoint change, off-course warnings, or periodic intervals. Spoken when the play button on the Bluetooth headset is pressed using 6-digit grid references.

### 2.5 GPX & Track Logging Control
* **Auto-Track Setting:** Track logging state controlled via Settings or start/finish button.

---

## 3. Data Schema, Persistence & File Onboarding

### 3.1 Route File Schema (CSV & GPX)
Accepts CSV route files or GPX tracks containing grid references / latitude/longitude, altitude, and name columns.

### 3.2 Persistent App State & Instant Persistence (`AppPreferences`)
* **Instant Persistence:** All app and map settings (flat pace, climb penalty, coordinate system, cone angle, voice alerts, map labels, waypoints, breadcrumbs, name, altitude, bearing toggles) persist instantly via `SharedPreferences` (`AppPreferences`) as soon as changed.
* **No Apply Buttons:** There is no need to have a button to indicate that you are finished with settings. Simply press on the gear icon again. Or indeed anywhere that is not the settings screen.

### 3.3 First-Run Onboarding Lifecycle
If no valid route file is found on startup, displays an onboarding overlay with an option to open route files or generate sample routes.

---

## 4. Screen Specifications

### 4.1 Main / Navigation Screen (MainScreen)

#### Title Section & Route Header
* **Route Filename:** Displays the loaded route filename minus extension.
* **Load New Route Icon:** A "New Route" buuton allowing the user to load a new route file.
* **Route Summary:** Displays `Distance X.Xkm Altitude Xm Time hh:mm` (total route distance, altitude gain, and total estimated time).
* **Bottom App Bar Toolbar:** Primary action icons (Start/Finish, Map/Globe, Settings Gear) placed in a bottom app bar toolbar anchored with `.navigationBarsPadding()` above Android system navigation/gesture bars.

#### Top Section (Current Leg)
* **Format & Succinct Grid Refs:** Uses 6-digit succinct format (`Position`, `Bearing`, `Distance`, `Time`, `Target`).

#### Waypoint Table
* **Compact Rows:** Uses 6-digit grid references for compact layout.
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
* **Grid Markings:** Two digits only (no N, E, or trailing zeros). Gridlines every 1 km.
* **Waypoint Label Deduplication:** Waypoints with identical coordinates have their label information printed only once.
* **Map Settings & Instant Persistence:** 
  * Name, Altitude, and Bearing toggles (persisted instantly via `AppPreferences`).
* **Waypoint Bearings Rendering:** 
  * Rendered close to the originating waypoint using `maxOf(22f, len * 0.15f)` without overlapping it.
  * Uses a consistent perpendicular offset.
  * Short segments (<55 pixels) skip bearing rendering entirely to prevent clutter.
* **Buffer Area:** Extra buffer area around map canvas.
* **Minimum Map View Size:** **0.2km (200 meters)** minimum square bounding box / scale (e.g., when there is only one waypoint or default zoom).
* **Out-of-Bounds Indicator:** Red arrow pointing from current waypoint towards current position when outside the map, with distance in meters (or km if >= 1 km).
* **Recording Confirmation Prompt:** If user tries to start recording before current position is on the map, prompt for confirmation ("Are you sure?").
* **Speed-Graded Breadcrumbs:** Breadcrumbs graded by speed (Red to Blue).

---

### 4.3 Settings Screen (SettingsScreen)
* **Navigation & Map Settings:** Categorized panels for Naismith's rule parameters, coordinate system, voice alerts, map overlays, etc.
* **Instant Persistence:** All toggles and inputs update `AppPreferences` immediately without requiring confirmation buttons.

---

## 5. Technical Guidelines
* **Single Source of Truth:** Coordinate format dictates string generation across all UI screens, tables, map gridlines, and TTS alerts.
* **Android Permissions & Lifecycle:** Strict runtime permission handling (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`).
* **Tech Stack:** Native Android development using **Kotlin and Jetpack Compose** on Ubuntu 24.04, supported by Foreground Service and MediaSession integration.

---

