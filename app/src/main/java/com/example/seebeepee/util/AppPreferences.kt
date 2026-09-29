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
}
