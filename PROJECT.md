# PROJECT.md — Hillwalking & Hiking Navigation App Specification

## 1. Overview
An offline-first, lightweight GPS navigation and tracking application developed on Linux (Ubuntu 24.04) for Android using **Kotlin and Jetpack Compose**, specifically tailored for hillwalking and outdoor navigation in Ireland and the UK[cite: 1]. The app manages CSV/GPX routes, calculates hiking ETA using Naismith’s Rule with customizable pace and climb penalty parameters (standardized to minutes per 10m of ascent), tracks off-course deviations via a cone calculation, displays all metrics using either Lat/Lon or Irish National Grid Reference / OS Grid coordinates, and persists user state cleanly across app restarts[cite: 1]. 

It is designed for hands-free operation. It can provide status updates to a Bluetooth headphone and, in particular, provides bearing and distance to the target waypoint when the headset play button is pressed (intercepted via Android MediaSession callbacks while running in a Foreground Service)[cite: 1].

## 1.1 Terminology
* **waypoint:** A specific location, usually given by a name, grid reference, internal $X/Y$ coordinates, and altitude[cite: 1].
* **current waypoint:** The last waypoint reached by the hiker[cite: 1].
* **target waypoint:** The next waypoint that the hiker is travelling to[cite: 1].
* **hiker:** The user of the program. The intent of the hiker is to hike a route along a sequence of waypoints[cite: 1].
* **Leg stats:** The bearing, distance (m) and estimated time required to reach the target waypoint[cite: 1].
* **Status:** Current position of the hiker, and the leg stats[cite: 1].
* **stats:** The cumulative stats of the hike. These are the total distance travelled so far, the total time spent hiking, the average speed and the percentage completion of the hike[cite: 1].
* **Breadcrumbs:** The current path that the hiker travels will be recorded at regular intervals (e.g. every second). This will be displayed on the map screen. This will be useful to see how accurately the hiker follows the waypoints[cite: 1].

## 1.2 Usage
The app will frequently be used in background mode with the phone switched off[cite: 1]. However, it will still monitor and record GPS location via a Foreground Service and respond when the Bluetooth headset play button is pressed[cite: 1].

## 1.3 Startup/Shutdown behaviour
The program will maintain persistent state[cite: 1]. In particular, the waypoints will come from a file that was loaded previously[cite: 1]. The name of the route will be the name of the file without the extension[cite: 1].
The current position will be the GPS location unless no position is found in which case, it will default to the start position (first waypoint)[cite: 1]. The current leg will be the first waypoint[cite: 1].
On app startup, `MainActivity` coordinates permission checks (ensuring runtime location and Android 14+ `FOREGROUND_SERVICE_LOCATION` permissions are granted) prior to launching the tracking Foreground Service.
When the program is shutdown, the user will be asked if they want to save the breadcrumbs (if they exist), and if they want to, they will be saved as a full GPX track (including timestamps, elevation, and coordinates per trackpoint) with the name of the route followed by the date in yyyymmdd format[cite: 1].

## 1.3 Error Handling
Any errors, e.g. missing permissions need to be prominantly displayed as the user needs to know. Maybe it can be clicked away and program can continue with reduced functionality.

---

## 2. Core Settings & Hiking Algorithms

### 2.1 Coordinate Systems & Internal Representation
* **Setting:** Coordinate System (`"grid"` [Irish Grid / OS Grid] vs `"latlon"`).
* **Internal $X/Y$ Representation:** For grid-based coordinate formats, the application parses and maintains an internal metric representation using $X$ (Easting) and $Y$ (Northing) coordinate values in meters. This ensures efficient, high-precision distance and bearing calculations between waypoints.
* **Format Stability:** Irish Grid notation follows standard notation, and the parser must robustly handle varying spacings (e.g., `V 860 870`, `V860 870`, or `V860870`)[cite: 1].
* **Behavior:** All coordinates across the main screen, waypoint table, details, map gridlines, and spoken alerts strictly respect this setting[cite: 1].

### 2.2 Walk Time Calculation (Naismith's Rule with Adjustments)
* **Flat Pace Setting:** User-configurable flat walking speed (e.g., 20 min/km)[cite: 1].
* **Climb Penalty Setting:** User-configurable time penalty per height gained, standardized to **minutes per 10m of ascent** (e.g., 1 min per 10m of ascent)[cite: 1].
* **Usage:** Used to compute leg time estimates, remaining route time, and projected completion time[cite: 1].

### 2.3 Off-Course Detection
* **Off-Course Cone Angle Setting:** Configurable threshold angle in degrees (e.g., 5° or 10°)[cite: 1].
* **Behavior:** Triggers off-course visual alerts and voice warnings when the bearing between the current track vector and the target waypoint exceeds the cone angle[cite: 1].


### 2.4 Voice & Speech Synthesis (Text-To-Speech)
* **Voice Alerts Toggle:** Enable / Disable Spoken Status[cite: 1].
* **Alert Intervals:** Spoken updates on waypoint change, off-course warnings, or periodic intervals (e.g., every 15 mins or every 200m travelled)[cite: 1].
* **Spoken Metrics:** Distance to next waypoint, required leg bearing, and off-course warnings. Spoken when the play button on the Bluetooth headset is pressed[cite: 1].

### 2.5 GPX & Track Logging Control
* **Auto-Track Setting:** Track logging state controlled via Settings (e.g., Auto-Start Track on GPS Lock or manual switch in Settings), keeping the main UI clean during active navigation[cite: 1].

---

## 3. Data Schema, Persistence & File Onboarding

### 3.1 Route File Schema (CSV & GPX)
The app accepts CSV route files (or GPX tracks) containing the following fields[cite: 1]:
* **Grid Ref Column:** `grid_ref` (or `grid`, `location`) — Single uppercase letter prefix (`A`–`Z`, excluding `I`) + Easting & Northing digits[cite: 1]. Automatically mapped to internal metric $X$ and $Y$ coordinates.
* **Lat/Lon Columns:** `latitude` (or `lat`) and `longitude` (or `lon`) in Decimal Degrees[cite: 1].
* **Altitude Column:** `altitude` (or `alt`, `elevation`, `ele`) in meters (defaults to `0.0` if omitted or unparseable)[cite: 1].
* **Name Column:** `name` (or `title`) — Optional label (defaults to `Waypoint N`)[cite: 1].

### 3.2 Persistent App State (`App.config` / `JsonStore`)
* **Zero Hardcoded Waypoints:** No default coordinates or dummy waypoints embedded in source code[cite: 1].
* **Persisted Keys:** `coordinate_system`, `last_file_path`, `last_directory_used`, `flat_pace`, `climb_penalty`, `cone_angle`, `voice_alerts_enabled`[cite: 1].
* **Auto-Reload:** On app startup, if `last_file_path` exists and points to a valid CSV/GPX file, it is automatically parsed, converted into internal $X/Y$ or Lat/Lon representations, and loaded as the active route[cite: 1].

### 3.3 First-Run Onboarding Lifecycle
If no valid `last_file_path` is found on startup (e.g., first launch or deleted file)[cite: 1]:
1. **Onboarding Banner / Overlay:** Displays clear instructions explaining the required file schema[cite: 1].
2. **"Open Route File" Button:** Opens the native system file chooser to pick a CSV or GPX file[cite: 1].
3. **"Generate Sample Irish Grid CSV" Button:** Creates a starter file in the user's document folder populated with valid Irish Grid locations and altitudes[cite: 1].

---

## 4. Screen Specifications

### 4.1 Main / Navigation Screen (MainScreen)
The primary operational interface combining live navigation metrics, an embedded Waypoints table, and overall hike statistics[cite: 1].

#### Top Section: Current Leg Navigation Metrics
* **Leg Bearing, Leg Distance, Leg Time, Current Position, Current Elevation, and GPS Status**[cite: 1].

#### Center Section: Embedded Waypoints Table & Auto-Scroll
* **Embedded Table View:** Scrollable table showing all route waypoints with Name/ID, Location, Altitude, and Leg Stats[cite: 1].
The table should be in a grid format. Almost like a spreadsheet. The columns may need to have a short width to allow all the data to be presented. The bearing could have a degree symbol but distance and time should not have units (except maybe in the header). Distance could be in metres and time in minutes. Superfluous quotes should be removed. I don't think there is a need for the number of the waypoint.
The table should be presented more like a spreadsheet with compact columns, stripped superfluous quotes, units removed from data cells (retained in headers). The name field maybe be shortened or omitted if not everything can be viewed.
* **Current Waypoint Highlighting:** The Current Waypoint is visually highlighted to distinguish it the other waypoints[cite: 1].
* **Auto-Scroll Behavior:** The table automatically favors viewing and anchoring future waypoints so the hiker can inspect upcoming legs immediately. However, the current waypoint is always in view. [cite: 1].
* The final row of the table should just contain total distance and total time required.

#### Bottom Section: Overall Hike Statistics
* **Total Distance Travelled, Total Elevation Climbed, Time taken so far, and Total Remaining Time / ETA**[cite: 1].

---

### 4.2 Map Screen (MapScreen)
* **Abstract 2D Coordinate Canvas:** 1:1 aspect ratio vector layout plotting waypoints and tracking elements without relying on heavy offline map tiles for now. If there is only one waypoint, then the scale will be set so that 2km all around that one waypoint is in view. (i.e. imagine that there is a 4km square around the waypoint which is visible. Waypoints should be labelled with their name using intelligent Constraint-Based Label Placement: Waypoints are sorted by their crowding constraint (distance to neighbors) and evaluated across four directional candidates (Right, Left, Above, Below) using a collision-avoidance algorithm. This automatically selects the placement least likely to overlap with other labels or screen boundaries.[cite: 1].

* **Live Overlay Layer:** Displays route geometry, highlighted active leg line, hiker breadcrumb trail, current position dot. The live GPS breadcrumbs are dynamically projected into the same metric Irish Grid space ($X/Y$) as the route waypoints to ensure a uniform scale and accurate visual comparison.[cite: 1].
* **Speed-Graded Breadcrumbs:** Breadcrumb trail uses a gradient transition mapping values from Red (0 km/h) through Orange, Yellow, Green, Cyan, to Blue (>= 6 km/h) to reveal pace variations[cite: 1].
* **Coordinate Gridlines:** The lettered version of the Irish grid reference system (e.g. V860 870) means that two digits can be used to mark each 1km grid line. Vertical ones will be labelled with the Easting and horizontal ones with the northing. This is the system used by OS maps.The gridlines should be every kilometer. [cite: 1].


---

### 4.3 Settings Screen (SettingsScreen)
Structured into clear collapsible or categorized panels and generally numbers are implemented via direct numeric text entry fields.[cite: 1]
* **Load New Waypoints** This will allow the hiker to select a new waypoint file. Naturally, this will erase any existing breadcrumbs. If they do exist, the hiker should be asked if he wishes to save them.
* **Location & Coordinate System**[cite: 1]
* **Hiking Pace & Time Model** Default 20m per km and 1 min per 10m ascent[cite: 1]
* **Navigation & Alerts** (cone angle, voice settings)[cite: 1]
* **GPX / CSV File & Track Logging**[cite: 1]

### 4.4 Selecting screens

---

## 5. Technical Guidelines
* **Single Source of Truth:** `app.coordinate_format` dictates coordinate string generation across all UI screens, tables, map gridlines, and TTS alerts[cite: 1].
* **Android Permissions & Lifecycle:** Strict runtime permission handling (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `FOREGROUND_SERVICE_LOCATION` for Android 14+) managed via `MainActivity` results API prior to invoking `startForegroundService()`.
* **Tech Stack:** Native Android development using **Kotlin and Jetpack Compose** on Ubuntu 24.04, supported by a Foreground Service for background tracking and MediaSession integration for physical headset controls[cite: 1].

---

## 6. Nice to Have / Future Extensions
* **Choose waypoints:** Altering the active route sequence dynamically (e.g., selecting points on the map canvas)[cite: 1].
Also, maybe reversing them, reordering them, editing and inserting/deleteing waypoints.
* **Dead reckoning mode:** Position tracking based purely on time and standard speed/bearing when GPS is unavailable, requiring hiker confirmation at prominent waypoints[cite: 1].
* **Panoramic View / DEM Integration:** Generating a cartoon panoramic view via Ray Marching over Copernicus DEM data combined with a feature map to verify location[cite: 1].

---

## 7. More specific future ideas

### 7.1 Test
	Read a specific waypoint file. Use this to generate a breadcrumb file. Create a repeatable (probably using pseudorandom numbers) breadcrumb file.
		Animate it so can be visually checked.
		Use it in automatic tests for bearing/distance/ETA readings for each waypoint.
		So, should be able to accurately text the nav screen.
		Can it be used to check the Map screen? (Each time a breadcrumb added => very specific map required.

### 7.1 Settings
Specific settings have a settings rectangle toggled by a gear icon in the lower right corner. When pressed, the icon changes colour and the settings appear over a semi transparent rectangle so that the screen underneath is still partially visible. Both the map page and the nav page have settings options.

When the gear icon pressed again, it goes away. A message to that effect at the bottom of the settings.
There is no save settings option, once entered a value becomes valid and can be seen on the page underneath.

Also, a slider is probably better for numeric entry than typing a number.

### 7.1 Map Settings

There are the following options:
	Labels on/off (i.e. waypoint names)
	Waypoints on/off
	map tiles on/off (after the map tiles feature is added)
	contours on/off  (after the contours feature is added)
	breadcrumbs on/off
		breadcrumbs colour: speed absolute/relative (relative to expected speed over the route based on disatance / ETA time)
		This allows you to see the rule accuracy and maybe update it.
	Naismith's rule params (repeated on the Nav screen because relavent to both). 
		(On the map screen it can be used to check the values of
		 Naismith's rule. When the breadcrumbs colour matches the
		 colour of the leg => the values are accurate.)
		
### 7.1 Nav Settings

Just shows the standard settings. No save button. It can be cleared by clicking on the gear icon (as in the map) but also be selecting a new valid route.

### 7.1 Screen toggling

There are now only two screens. So, in the lower right, a map icon appears on the nav screen to bring you to the map and versa vice for the map sp screen.

### 7.1 Generate a route from a breadcrumb file
This will generate waypoints to create a route from line segments that approximate the breadcrumbs. The waypoints may be edited on a map or table (not really specified how yet.)

### 7.1 Mixed screen.
Another screen is mixed nav and map. The nav shows a reduced size table and the map is underneath. The waypoint table is reduced to two elements, current waypoint and target waypoint. Initially, the map size is tailored to the the current leg. And there are preset zoom buttons to make it show 1. all waypoints (and 2. assuming map tiles are available, ~10km around the map so that surrounding areas can be recognised), 3. Only the current breadcrumbs and current waypoint) and 4. Back to single waypoint. Whichever preset zoom is selected becomes grayed out when selected. The user can also pan and zoom using standard finger movements.

### 7.1 Waypoint Line segments Colour
The line segments are coloured using the same speed colours as the breadcrumbs with the waypoint speed being distance / ETA.

### 7.1 About screen
(Maybe Easter Eggy)
The settings button has an about button which brings up a screen where says developed by Charlie and Gemini and mentions:
	Navigation and Leadership: Greg, Neil, Donal, Eric, Ger, Philip, Pat, Fiona, Tadgh, 
	Hiking Friends: all of the above and Bertie, Anthony, Jenifer, Irene, Emma, Ray, Liz M, Liz M eile, Carmel, Val and John, Ber and Phil, Limien and John, Mary, Mary eile, Nuala, Sean, Niall, Elaine, Linda, Lydia, Martin Peter, Siobhan, Siobhan eile, Mags, Danielle, Tommy, Lucy and last and possibly least, John Lynch.
	
### 7.1 Dead Reckoning
When GPS is off and Data is off (completely off grid) but with a powered phone and preloaded data.
The phone correctly oriented in backpack => the phone's compass provides a bearing corresponding to the way the hiker is walking.
This implies that the program knows your bearing and can therefore give off course warning.
As for distance, it can use a timer in conjunction with the ETA. If available, it might be able to pick up step data from a smartwatch.

When practising using dead reckoning, it would be good to keep breadcrumbs but not use the gps data until analysing the route subsequently.

A good feature would be to use extra waypoints whenever there is a feature that is recognisable and add a descriptive comment.
(E.g. at this waypoint, you will notice that the slope goes from 1% to 6%. Or Carontouhil will come into view from behind Braca. Or you will arrive at the start of a fence. Or terrain becomes difficult because of Bracken/heather/ferns. Or you encounter a stream. Or the terrain is boggy.) Obviously instructions that are usable in a fog are preferred.
When you are actually on a route, you might find other instructions that you can use and save them with a route and you can use these subsequently to update your list of waypoints.

Also, it may be possible to use the android sensor to calculate the number of steps which should provide a more accurate measure of time travelled. (This would require another setting ... step length)

### 7.1 Panorama

Another screen (in addition to Nav and Map) (=> 3 options at bottom of each screen ... but they are just icons over the display =~> take up little space)

The screen shows a panorama from the current position and a particular bearing. It does this by using Ray marching with the DEM height data. Earth curvature and atmospheric effects will be catered for. The files for an area of say 50km around the hike location will be stored. A wireframe, cartoon style outline will be created for the current location (whether given by gps or dead reckoning). The current angle of view is shwon and you can swipe the screen sideways to change the angle of view and see the new view from that angle.

The surfaces can be shaded based on how far away the surfaces are from the hiker. If map tiles are pesent, then they can be stretched over the wireframe, with the 2D points of the height matching the 2D points of the wireframe. This means that water sources (rivers, seas and lakes), paths, roads, rock symbols, grotto symbols, fences, etc. will be correctly seen on the panorama. This could also be used with satelite imagery.

Also a list of features in the same format as the waypount file for the surrounding area can be used to label mountains, other obvious locations, e.g. buildings, lakes, etc.

### 7.1 Tiles

Download tiles for the area (and surroundings so can be used by panorama). They can be used for the 2d map and for the panorama. 
