Changes.md

## 1. Nav screen

### 1.1 Title section
You don't need to add the (Irish Grid). Also, the play icon should move to the bottom of the screen, left hand side. Replacing that, should be an icon representing a new course route which brings the user to the new route screen (i.e. loading a new route file). When you load the route, the title should change to the filename (minus the extension).
Also, the total length of the route and estimated time required should appear here and removed from the way points.
Distance in km and time in the format hh:mm

### 1.2 Top section
When a file is loaded, a message appears on the NAV screen that the file loaded. That isn't needed as the waypoints are visible. Maybe a message should appear if there are no waypoints.

The layout needs to be clearer. Basically this top section is just saying "Here we are, here's where we're going and here's how we get there". Firstly, the grid ref should be made more succinct. The context means that we don't need the first letter and three digits for Easting and Northing will be sufficient. So, if the location changes to just the six digits that would be perfect.
The top then should change to e.g.:
Position  Bearing Distance Time  Target
880888    214     605      16.1  Start of path

This would make the top section much shorter and allow more room for the waypoints.

### 1.3 Waypoint table

It would be nicer if more waypoints were visible. Making the top section shorter will help.

In addition, each row should be a little shorter. By replacing the current location values with the shorter 6 digit versions. The header rows are fine, except maybe the (min) could change to (m) as the fact that is time should make it clear that the unit is minutes rather than metres.

### 1.4
I think this screen would be better as:
Overall Hike statistics
Distance: 2.8km Altitude: 75m Time: hh:mm % dd%

(i.e. just two lines.)

### 1.5 bottom icons
The hike stats are not visible under the nav buttons. Maybe make these buttons smaller . E.g. is it possble to have a gear icon with no background? And then the icons could have a small row at the bottom of the screen.

For consistency, I think the settings option, i.e. the gear icon should always be on the lower right of the screen. Then to the left of that, a small globe icon representing the map screen. On the bottom left could be the icon representing start. I'm not sure wether this should be a play button or a pair of hiking boots or what. The three icons on the bottom of the screen should all be the same side.
When the hiker starts, the play icon changes to a finish icon. When pressed, the GPS is turned off (assuming nothing else needs it) and the hiker is asked if he wants to save his breadcrumbs. (As a gpx track.)

### 1.6 Navigation settings
Since loading a new route option will be set in the title bar, there is no need to also have it in the settings. The generate sample might be better just to have in a help option attached the load new route.


## 2. The map

Grid markings should only be two digits. Remove the N, the E and all the zeros.
The title bar could just be the name of the route file without the suffix. There should be two small icons at the bottom right. The gear icon is rightmost and just to the left is the nav icon. (E.g. a compass). The doument ref text should disappear.

On the map, there are some duplicate waypoints. You can see this as they have the same coordinates. When this occurs, you should only print the label information once.

There should be extra options in the settings. In addition to the name, there will be the altitude. If both are selected, they will be separated by a space.

There is also a bearing setting. When this is selected, the bearing will appear parallel to the waypoint leg and just underneath it. It will be at the end of the line segment closest to the originating waypoint.

There should also be a little more buffer area around the map.

If the current position is outside the map, then a red arrow points tfrom the current waypoint towards the current position and the distance between them (in metres, unless it is more than a km in which case it will be in km).

If someone tries to start recording before their current position is on the map, ask if they are sure.

I think the information about the number of waypoints, breadcrumbs and zoom level is not needed. Maybe keep the breadcrumbs until we are sure that hey work.
