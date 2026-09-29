# Project Plan

Hillwalking & Hiking Navigation App (SeeBeePee) specification based on PROJECT.md and implementation.md:
An offline-first, lightweight GPS navigation and tracking Android app built with Kotlin and Jetpack Compose, tailored for hillwalking and outdoor navigation in Ireland and the UK.
Features include:
1. CSV/GPX Route loading and waypoint management with Irish National Grid / OS Grid or Lat/Lon coordinate support.
2. Naismith’s Rule ETA hiking time calculation with customizable flat pace and climb penalty parameters (minutes per 10m ascent).
3. Main Navigation Screen with live leg stats (bearing, distance, estimated time), compact spreadsheet-like Waypoints table with auto-scroll and current waypoint highlighting, and overall hike statistics.
4. Interactive 2D Map Screen with abstract metric coordinate canvas, waypoint crowding-aware constraint-based label placement, speed-graded breadcrumb trail (Red to Blue), and 1km coordinate gridlines.
5. Settings Screen for configuring coordinate system, Naismith parameters, cone angle off-course detection, and voice alerts.
6. Persistent state across app restarts, Foreground Service for GPS tracking, and Bluetooth MediaSession headset button integration for hands-free status updates and bearing/distance spoken alerts.
7. First-run onboarding lifecycle with file chooser and sample Irish Grid CSV generator.

## Project Brief

# Project Brief: SeeBeePee (Hillwalking & Hiking Navigation App)

## Overview
**SeeBeePee** is an offline-first, lightweight GPS navigation and tracking Android application built with Kotlin and Jetpack Compose, tailored specifically for hillwalking and outdoor navigation in Ireland and the UK. It provides robust offline routing, precise metric coordinate calculations, Naismith's Rule ETA estimates, live map rendering, and hands-free Bluetooth headset integration.

## Features
1. **CSV/GPX Route Loading & Waypoint Management**: Import routes via CSV or GPX with support for Irish National Grid, OS Grid, or Lat/Lon coordinates, automatically mapped to a local metric ($X/Y$) coordinate space.
2. **Naismith’s Rule ETA Calculation**: Dynamic hiking time estimation factoring in customizable flat pace (min/km) and climb penalty (minutes per 10m of ascent).
3. **Main Navigation Screen & Table**: Real-time leg statistics (bearing, distance, time), compact spreadsheet-like waypoints table with auto-scroll and current waypoint highlighting, and cumulative hike statistics.
4. **Interactive 2D Map Screen**: Abstract metric coordinate canvas displaying route geometry, intelligent constraint-based waypoint label placement, speed-graded breadcrumb trails (Red to Blue), and 1km coordinate gridlines.
5. **Background Tracking & Bluetooth Headset Integration**: Foreground Service for continuous GPS location monitoring and Bluetooth MediaSession headset button integration for hands-free spoken status updates (bearing and distance).

## High-Level Technical Stack
- **Programming Language**: Kotlin
- **UI Toolkit**: Jetpack Compose
- **Navigation & Adaptive Strategy**: Jetpack Navigation 3 (State-driven navigation) and Compose Material Adaptive Library for all adaptive layouts.
- **Concurrency**: Kotlin Coroutines & StateFlow
- **Hardware & System Services**: Android Location Services, Foreground Service, Android MediaSession (Bluetooth remote control), Text-to-Speech (TTS)
- **Persistence**: SharedPreferences (app configurations) and local JSON/CSV serialization (routes and breadcrumbs; no relational database required).

## Implementation Steps

### Task_1_CoreDataAndParsers: Implement core data models, CSV and GPX route loading parsers, waypoint management, and coordinate system conversion (Irish Grid, OS Grid, Lat/Lon to local metric X/Y coordinates).
- **Status:** COMPLETED
- **Updates:** Successfully implemented Waypoint, Breadcrumb, CoordinateUtils (Irish/OS Grid & LatLon metric conversion), NaismithEngine, RouteParser (CSV & GPX), and RouteManager. Verified with unit tests and gradle build.
- **Acceptance Criteria:**
  - CSV and GPX parsing implemented
  - Coordinate conversion supports Irish Grid, OS Grid, and Lat/Lon to metric X/Y
  - project builds successfully

### Task_2_NaismithAndMainScreen: Implement Naismith’s Rule ETA hiking time calculation with customizable flat pace & climb penalty, and the Main Navigation Screen featuring live leg stats, compact spreadsheet waypoint table with auto-scroll & current waypoint highlighting, and cumulative hike stats.
- **Status:** COMPLETED
- **Updates:** Successfully implemented MainScreen with live leg navigation metrics, spreadsheet-style auto-scrolling waypoints table with current waypoint highlighting, overall hike statistics, Naismith engine integration via ViewModel, Navigation 3 screen switching, and Settings overlay panel. Verified with build and unit tests.
- **Acceptance Criteria:**
  - Naismith ETA calculation functioning correctly with custom parameters
  - Main Navigation Screen displays live leg stats and auto-scrolling waypoint table
  - project builds successfully

### Task_3_MapAndTrackingService: Build the Interactive 2D Map Screen with abstract metric coordinate canvas, waypoint label placement, speed-graded breadcrumb trail (Red to Blue), 1km coordinate gridlines, and Foreground Service for continuous GPS tracking with Bluetooth MediaSession voice alerts.
- **Status:** COMPLETED
- **Updates:** Successfully implemented interactive 2D MapScreen with constraint-based label placement, speed-graded breadcrumbs (Red to Blue), 1km gridlines, and zoom/pan gestures. Implemented HikingForegroundService for continuous GPS tracking with Android 14+ FGS location compliance. Implemented TtsManager and Bluetooth MediaSession callbacks for headset remote triggers. Added first-run onboarding file picker and sample CSV generator. Verified with gradle build and unit tests.
- **Acceptance Criteria:**
  - 2D Map Screen renders metric canvas, breadcrumbs, and gridlines
  - Foreground Service tracks GPS and integrates Bluetooth MediaSession for voice updates
  - project builds successfully

### Task_4_SettingsOnboardingAndVerify: Implement Settings Screen for coordinate systems, Naismith parameters, cone angle off-course detection & voice alerts, first-run onboarding lifecycle with sample Irish Grid CSV generator and file picker. Run and verify application stability, ensure no crashes, alignment with user requirements, and pass all existing tests.
- **Status:** COMPLETED
- **Updates:** Successfully completed Task 4. Verified project build (`:app:assembleDebug`) and unit test suite (`:app:testDebugUnitTest`), confirming 100% passing tests and zero compilation issues. All core features (CSV/GPX routing, Naismith ETA, Main Navigation Screen with auto-scroll table, 2D Map Screen with constraint-based label placement and speed-graded breadcrumbs, Foreground Service tracking, Bluetooth MediaSession voice alerts, Settings, and Onboarding CSV generator) are fully implemented and robustly verified.
- **Acceptance Criteria:**
  - Settings Screen and Onboarding with sample CSV generator implemented
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - critic_agent verifies application stability and alignment with requirements

### Task_5_RefineMainNavigationScreen: Refine Main Navigation Screen UI and features according to changes.md: title bar with file load icon, route filename minus extension, and total route length (km) + estimated time (hh:mm) without '(Irish Grid)'; succinct 6-digit grid references (Position, Bearing, Distance, Time, Target) in top section without file loaded toast message; waypoint table using 6-digit coordinates and compact layout without final row totals; overall hike statistics in clean two-line format; bottom icon bar with start/finish GPS toggle, globe icon for map screen, and transparent gear icon for settings.
- **Status:** COMPLETED
- **Updates:** Successfully refined Main Navigation Screen according to changes.md: updated title bar with route filename minus extension, total route length, estimated time, and file loader icon; succinct 6-digit grid references in top section; compact waypoint table without totals; two-line overall hike statistics; and uniform bottom icon bar with start/finish GPS toggle, map globe icon, and transparent settings gear icon. Verified with build and unit tests.
- **Acceptance Criteria:**
  - Main Navigation Screen UI and features match changes.md refinements
  - project builds successfully

### Task_6_RefineMapScreenAndSettings: Refine Map Screen, Settings, and HUD features according to changes.md: title bar showing route filename with bottom-right compass/nav and gear icons; 2-digit grid markings; waypoint label deduplication for identical coordinates; map settings for Name, Altitude, and Bearing parallel to leg near originating waypoint; extra buffer area around map canvas; out-of-bounds red arrow indicator pointing from current waypoint to current position with distance; recording confirmation prompt if current position is outside map; clean HUD removing waypoint count, breadcrumb count, and zoom text.
- **Status:** COMPLETED
- **Updates:** Successfully implemented Task 6: Refined Map Screen with route filename title, gear and nav bottom-right icons, 2-digit grid markings, waypoint label deduplication for identical coordinates, map settings for Name, Altitude, and Bearing parallel to legs, extra map buffer area, out-of-bounds red arrow position indicator, recording confirmation prompt for off-map positions, and clean HUD. Verified with successful gradle build and passing unit tests.
- **Acceptance Criteria:**
  - Map Screen, Settings, and HUD match changes.md refinements
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** N/A

