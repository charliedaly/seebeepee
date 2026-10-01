## 1. Core functionality
When the app starts the status message says that the GPS activity is simulated. When I press the start button it says GPS activity active. But there is no evidence that it is active. The status stays the same. The position reads the same.

The main purpose of the app is to direct the hiker when the start button is pressed and to record breadcrumbs. Neither of those things are happening

Also, if I turn off the app's location permission, there is no notification. It just sits there reporting the wrong location.

The first priority is to make GPS work as a foreground service and the second priority is that if there is a problem, whether with permissions or otherwise that the user is notified.

2. When the app starts the status message says that the GPS activity is simulated. When I press the start button it says GPS activity active. But there is no evidence that it is active. The status stays the same. The position reads the same.
The main purpose of the app is to direct the hiker when the start button is pressed and to record breadcrumbs. Neither of those things are happening
Also, if I turn off the app's location permission, there is no notification. It just sits there reporting the wrong location.
The first priority is to make GPS work as a foreground service and the second priority is that if there is a problem, whether with permissions or otherwise that the user is notified.

Good, now at least a value is being being displayed for current position. However it is the wrong value. Earlier I wanted the grid reference to just show six digits but it is hard to debug when it is only showing six digits, so could you change the dispaly to show the full grid reference. In fact, it would be good to have a settings value that toggles between full grid reference and the 6 digit grid reference. The map shows an arrow to where the current position is, but ithe arrow is too near the edge and so is not clearly visible. And I would like the distance to be displayed. (E.g. it would be nice to know if the GPS thought that the location was 50km away)
