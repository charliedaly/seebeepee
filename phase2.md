## 1. Phase 2

### 1.1 Settings
It would be good if the settings screen background was slightly transparent so that we could see the Navigation Screen and the Map screen.

### 1.2
The map screen also has the same status window at the top of the map window

### 1.3 Waypoint Line segments Colour
The line segments are coloured using the same speed colours as the breadcrumbs with the waypoint speed being distance / ETA.

### 1.4 About screen

The settings button has an about button. Pressing it brings up a page with
a picture of the program icon.
Underneath it is CorkBackPackers

developed by Charlie, Android Studio and Gemini
Special mentions go to Greg, Neil, Donal, Eric, Ger, Philip, Pat, Fiona, Tadgh for 	Navigation and Leadership.
And to Hiking Friends: all of the above and Bertie, Anthony, Jenifer, Irene, Emma, Ray, Liz M, Liz M eile, Carmel, Val and John, Ber and Phil, Ger and Niamh, Limien and John, Mary, Mary eile, Barry, Nuala, Sean, Niall, Elaine, Linda, Lydia, Martin Peter, Siobhan, Siobhan eile, Mags, Danielle, Tommy, Lucy and last and possibly least, John Lynch.
	
### 1.5 Dead Reckoning
In the navigation settings, there is a dead reckoning option. This doesn't use android location services for navigation. Instead, it uses the android compass for the bearing and the android step detection to estimate distance travelled.

Altough GPS is not used for navigation, it may still be used to record the breadcrumbs.

The use case is as follows:

When GPS is off and Data is off (completely off grid) but with a powered phone and preloaded data.

The phone is correctly oriented in the hikers backpackbackpack => the phone's compass provides a bearing corresponding to the way the hiker is facing and therefore walking.
Thus the app knows your bearing and can therefore inform the hiker of any off course using the bluetooth headphone.

When practising using dead reckoning, it would be good to keep breadcrumbs but not use the gps data until analysing the route subsequently.

Naturally, the step length would need to be in the settings. In fact, it would probably have three settings, level step, climb penalty and descent penalty. These could be worked out by comparing step count with breadcrumbs. Indeed, one of the map options should be to add the step count to the waypoint label.

### 1.6

The CSV file could have an extra optional string which would be the waypoint description.

A waypoint might have a recognisable feature which can help the hiker know when he has arrived. This will be spoken to the hiker as the waypoint is reached to help him identify the waypoint.
A good feature would be to use extra waypoints whenever there is a feature that is recognisable and add a descriptive comment.
(E.g. at this waypoint, you will notice that the slope goes from 1% to 6%. Or Carontouhil will come into view from behind Braca. Or you will arrive at the start of a fence. Or terrain becomes difficult because of Bracken/heather/ferns. Or you encounter a stream. Or the terrain is boggy.) Obviously instructions that are usable in a fog are preferred).
[While the hiker is enroute he might find new descriptions that can be used that could be used to update the waypoints. Indeed any interesting feature could be an excuse to create a new waypoint. Or maybe have a separate features file.]

### 7.1 Map Tiles

OSM Map Tiles should be predownloaded.

These would then be used in the 2D map so that rivers, roads and other featuers would be visible.

The tiles would be drawn first and then the other map features would be drawn onto them.

### 7.1 Panorama

Another screen (in addition to Nav and Map) (=> 3 options at bottom of each screen ... but they are just icons over the display =~> take up little space)

The screen shows a panorama from the current position and a particular bearing. It does this by using Ray marching with the DEM height data. Earth curvature and atmospheric effects will be catered for. The files for an area of say 50km around the hike location will be stored. A wireframe, cartoon style outline will be created for the current location (whether given by gps or dead reckoning). The current angle of view is shwon and you can swipe the screen sideways to change the angle of view and see the new view from that angle.

The surfaces can be shaded based on how far away the surfaces are from the hiker. If map tiles are pesent, then they can be stretched over the wireframe, with the 2D points of the height matching the 2D points of the wireframe. This means that water sources (rivers, seas and lakes), paths, roads, rock symbols, grotto symbols, fences, etc. will be correctly seen on the panorama. This could also be used with satelite imagery.

Also a list of features in the same format as the waypount file for the surrounding area can be used to label mountains, other obvious locations, e.g. buildings, lakes, etc.



### 7.1 Editing the waypoints.
A pencil icon appears on the navigation settings page allowing you to edit the waypoints. You can reverse them (in case the hiker wants to retrace his steps for any reason, or you can mark them to be skipped. This doesn't delete them but does remove them from the navigation list.


## 8. Next phase

### 8.1 Test
	Read a specific waypoint file. Use this to generate a breadcrumb file. Create a repeatable (probably using pseudorandom numbers) breadcrumb file.
		Animate it so can be visually checked.
		Use it in automatic tests for bearing/distance/ETA readings for each waypoint.
		So, should be able to accurately text the nav screen.
		Can it be used to check the Map screen? (Each time a breadcrumb added => very specific map required.

		
## 7.1 Map Settings (already partially implemented).

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
		 
		 
### 1.2 The New Route dialog will allow you to create a route from a breadcrumb file
This will generate waypoints to create a route from line segments that approximate the breadcrumbs. The waypoints may be edited on a map or table (not really specified how yet.)

### 7.1 Mixed screen.
Another screen is mixed nav and map. The nav shows a reduced size table and the map is underneath. The waypoint table is reduced to two elements, current waypoint and target waypoint. Initially, the map size is tailored to the the current leg. And there are preset zoom buttons to make it show 1. all waypoints (and 2. assuming map tiles are available, ~10km around the map so that surrounding areas can be recognised), 3. Only the current breadcrumbs and current waypoint) and 4. Back to single waypoint. Whichever preset zoom is selected becomes grayed out when selected. The user can also pan and zoom using standard finger movements.


### 1.7 Leg description
This is defined by two waypoints and so would be in a separate file. Each file would have three columns, waypoint 1, waypoint 2 and a description. This would be a description of the leg between the two waypoints. Any legs which correspond to the leg 


