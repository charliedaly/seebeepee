package com.example.seebeepee.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.seebeepee.MainActivity
import com.example.seebeepee.R
import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.TtsManager
import kotlinx.coroutines.*

class HikingForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var ttsManager: TtsManager
    private var mediaSession: MediaSessionCompat? = null
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "HikingServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(applicationContext)
        setupMediaSession()
        createNotificationChannel()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SeeBeePeeMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    speakCurrentLegStats()
                }
                override fun onPause() {
                    super.onPause()
                    ttsManager.speak("Hike tracking paused.")
                }
                override fun onSkipToNext() {
                    super.onSkipToNext()
                    if (RouteManager.waypoints.isNotEmpty()) {
                        RouteManager.currentWaypointIndex = (RouteManager.currentWaypointIndex + 1) % RouteManager.waypoints.size
                        speakCurrentLegStats()
                    }
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                    .build()
            )
            isActive = true
        }
    }

    private fun speakCurrentLegStats() {
        val waypoints = RouteManager.waypoints
        val idx = RouteManager.currentWaypointIndex
        if (waypoints.isNotEmpty() && idx < waypoints.size) {
            val current = waypoints[idx]
            val next = if (idx < waypoints.size - 1) waypoints[idx + 1] else null
            if (next != null) {
                val dist = CoordinateUtils.calculateDistance(current.x, current.y, next.x, next.y)
                val bearing = CoordinateUtils.calculateBearing(current.x, current.y, next.x, next.y)
                val text = "Leg to ${next.name}. Bearing ${bearing.toInt()} degrees. Distance ${dist.toInt()} meters."
                ttsManager.speak(text)
            } else {
                ttsManager.speak("Currently at final waypoint ${current.name}.")
            }
        } else {
            ttsManager.speak("No active route loaded.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = createNotification("Hike Tracking Active")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startSimulationOrGpsTracking()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hiking Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SeeBeePee Hike Navigator")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startSimulationOrGpsTracking() {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            var simStep = 0
            while (isRunning) {
                delay(4000L)
                val waypoints = RouteManager.waypoints
                if (waypoints.isNotEmpty()) {
                    val idx = RouteManager.currentWaypointIndex.coerceIn(0, waypoints.size - 1)
                    val currentWp = waypoints[idx]
                    val offsetX = currentWp.x + (Math.random() - 0.5) * 20.0
                    val offsetY = currentWp.y + (Math.random() - 0.5) * 20.0
                    val alt = currentWp.altitude + (Math.random() - 0.5) * 5.0

                    val breadcrumb = Breadcrumb(offsetX, offsetY, alt, System.currentTimeMillis())
                    val lastBc = RouteManager.breadcrumbs.lastOrNull()
                    RouteManager.addBreadcrumb(breadcrumb)

                    if (lastBc != null) {
                        val prefs = AppPreferences(applicationContext)
                        if (prefs.voiceAlertsEnabled && simStep % 6 == 0) {
                            speakCurrentLegStats()
                        }
                    }
                    simStep++
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        mediaSession?.release()
        ttsManager.shutdown()
    }
}
