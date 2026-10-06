package com.example.seebeepee.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
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
        ttsManager.speakLiveStatus()
    }
}
