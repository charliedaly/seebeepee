package com.example.seebeepee.util

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("seebeepee_prefs", Context.MODE_PRIVATE)

    var coordinateSystem: String
        get() = prefs.getString("coordinate_system", "grid") ?: "grid"
        set(value) = prefs.edit().putString("coordinate_system", value).apply()

    var lastFilePath: String?
        get() = prefs.getString("last_file_path", null)
        set(value) = prefs.edit().putString("last_file_path", value).apply()

    var flatPace: Double
        get() = prefs.getFloat("flat_pace", 20.0f).toDouble()
        set(value) = prefs.edit().putFloat("flat_pace", value.toFloat()).apply()

    var climbPenalty: Double
        get() = prefs.getFloat("climb_penalty", 1.0f).toDouble()
        set(value) = prefs.edit().putFloat("climb_penalty", value.toFloat()).apply()

    var coneAngle: Float
        get() = prefs.getFloat("cone_angle", 10.0f)
        set(value) = prefs.edit().putFloat("cone_angle", value).apply()

    var voiceAlertsEnabled: Boolean
        get() = prefs.getBoolean("voice_alerts_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_alerts_enabled", value).apply()

    var autoTrack: Boolean
        get() = prefs.getBoolean("auto_track", true)
        set(value) = prefs.edit().putBoolean("auto_track", value).apply()

    var showWaypointName: Boolean
        get() = prefs.getBoolean("show_waypoint_name", true)
        set(value) = prefs.edit().putBoolean("show_waypoint_name", value).apply()

    var showWaypointAltitude: Boolean
        get() = prefs.getBoolean("show_waypoint_altitude", true)
        set(value) = prefs.edit().putBoolean("show_waypoint_altitude", value).apply()

    var showWaypointBearing: Boolean
        get() = prefs.getBoolean("show_waypoint_bearing", false)
        set(value) = prefs.edit().putBoolean("show_waypoint_bearing", value).apply()

    var showWaypoints: Boolean
        get() = prefs.getBoolean("show_waypoints", true)
        set(value) = prefs.edit().putBoolean("show_waypoints", value).apply()

    var showBreadcrumbs: Boolean
        get() = prefs.getBoolean("show_breadcrumbs", true)
        set(value) = prefs.edit().putBoolean("show_breadcrumbs", value).apply()

    var showGridlines: Boolean
        get() = prefs.getBoolean("show_gridlines", true)
        set(value) = prefs.edit().putBoolean("show_gridlines", value).apply()

    var useFullGridReference: Boolean
        get() = prefs.getBoolean("use_full_grid_reference", false)
        set(value) = prefs.edit().putBoolean("use_full_grid_reference", value).apply()

    var gridReferencePrecision: Int
        get() = prefs.getInt("grid_reference_precision", 6)
        set(value) = prefs.edit().putInt("grid_reference_precision", value).apply()
}
