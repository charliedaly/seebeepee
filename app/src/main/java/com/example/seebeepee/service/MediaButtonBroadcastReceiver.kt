package com.example.seebeepee.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.TtsManager

class MediaButtonBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }

            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val isActiveHike = RouteManager.waypoints.isNotEmpty() && RouteManager.hikeState != HikeState.IDLE
                if (isActiveHike) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            abortBroadcast()
                            speakNavigationStatus(context)
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            abortBroadcast()
                            if (RouteManager.waypoints.isNotEmpty()) {
                                RouteManager.currentWaypointIndex = (RouteManager.currentWaypointIndex + 1) % RouteManager.waypoints.size
                                speakNavigationStatus(context)
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            abortBroadcast()
                            if (RouteManager.waypoints.isNotEmpty()) {
                                RouteManager.currentWaypointIndex = if (RouteManager.currentWaypointIndex > 0) RouteManager.currentWaypointIndex - 1 else RouteManager.waypoints.size - 1
                                speakNavigationStatus(context)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun speakNavigationStatus(context: Context) {
        val ttsManager = TtsManager(context.applicationContext)
        val waypoints = RouteManager.waypoints
        val idx = RouteManager.currentWaypointIndex
        val precision = AppPreferences(context).gridReferencePrecision
        if (waypoints.isNotEmpty() && idx < waypoints.size) {
            val current = waypoints[idx]
            val next = if (idx < waypoints.size - 1) waypoints[idx + 1] else null
            if (next != null) {
                val dist = CoordinateUtils.calculateDistance(current.x, current.y, next.x, next.y)
                val bearing = CoordinateUtils.calculateBearing(current.x, current.y, next.x, next.y)
                val gridRef = CoordinateUtils.formatGridReferenceWithLetter(next.x, next.y, precision)
                val text = "Leg to ${next.name}, grid reference $gridRef. Bearing ${bearing.toInt()} degrees. Distance ${dist.toInt()} meters."
                ttsManager.speak(text)
            } else {
                val gridRef = CoordinateUtils.formatGridReferenceWithLetter(current.x, current.y, precision)
                ttsManager.speak("Currently at final waypoint ${current.name}, grid reference $gridRef.")
            }
        } else {
            ttsManager.speak("No active route loaded.")
        }
    }
}
