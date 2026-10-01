package com.example.seebeepee.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.gms.location.*
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
    private lateinit var ttsManager: TtsManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaSession: MediaSessionCompat? = null
    private var mediaButtonPendingIntent: PendingIntent? = null
    private var isRunning = false
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        const val CHANNEL_ID = "HikingServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestAudioFocus()
        setupMediaSession()
        createNotificationChannel()
        setupLocationCallback()
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { _ -> }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { _ -> },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val (x, y) = CoordinateUtils.latLonToMetric(location.latitude, location.longitude)
                    val alt = if (location.hasAltitude()) location.altitude else 0.0
                    val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()

                    val breadcrumb = Breadcrumb(x, y, alt, timestamp)
                    RouteManager.addBreadcrumb(breadcrumb)
                }
            }
        }
    }

    private fun setupMediaSession() {
        val pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this@HikingForegroundService,
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        )
        mediaButtonPendingIntent = pendingIntent

        mediaSession = MediaSessionCompat(this, "SeeBeePeeMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setMediaButtonReceiver(pendingIntent)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    requestAudioFocus()
                    speakCurrentLegStats()
                }
                override fun onPause() {
                    super.onPause()
                    requestAudioFocus()
                    ttsManager.speak("Hike tracking paused.")
                }
                override fun onSkipToNext() {
                    super.onSkipToNext()
                    requestAudioFocus()
                    if (RouteManager.waypoints.isNotEmpty()) {
                        RouteManager.currentWaypointIndex = (RouteManager.currentWaypointIndex + 1) % RouteManager.waypoints.size
                        speakCurrentLegStats()
                    }
                }
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                    }
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        requestAudioFocus()
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                speakCurrentLegStats()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                ttsManager.speak("Hike tracking paused.")
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                if (RouteManager.waypoints.isNotEmpty()) {
                                    RouteManager.currentWaypointIndex = (RouteManager.currentWaypointIndex + 1) % RouteManager.waypoints.size
                                    speakCurrentLegStats()
                                }
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
            )
            isActive = true
        }
    }

    private fun speakCurrentLegStats() {
        requestAudioFocus()
        val waypoints = RouteManager.waypoints
        val idx = RouteManager.currentWaypointIndex
        val precision = AppPreferences(applicationContext).gridReferencePrecision
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        requestAudioFocus()
        mediaSession?.setActive(true)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (intent?.action != ACTION_STOP) {
            mediaButtonPendingIntent?.let {
                audioManager.registerMediaButtonEventReceiver(it)
            }
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    RouteManager.logError("Error stopping foreground service: ${e.message}")
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val hasFine = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                val isGpsOn = locationManager?.let {
                    it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                } ?: false

                if (!hasFine && !hasCoarse) {
                    RouteManager.logError("Error: Location permissions (Fine/Coarse) are missing when starting HikingForegroundService.")
                    val notification = createNotification("⚠️ Error: Location Permission Missing!")
                    startForegroundWithNotification(notification)
                    return START_STICKY
                }

                if (!isGpsOn) {
                    RouteManager.logError("Warning: GPS/Location provider is disabled.")
                    val notification = createNotification("⚠️ Warning: GPS Disabled in Settings!")
                    startForegroundWithNotification(notification)
                } else {
                    val notification = createNotification("Hike Tracking Active (GPS)")
                    startForegroundWithNotification(notification)
                }

                startGpsTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: SecurityException) {
                RouteManager.logError("SecurityException starting foreground service with location/mediaPlayback type: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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

    private fun startGpsTracking() {
        if (isRunning) return
        isRunning = true

        val hasFine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            RouteManager.logError("Cannot start GPS tracking: Location permission missing.")
            isRunning = false
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).apply {
            setMinUpdateIntervalMillis(1000L)
            setMaxUpdateDelayMillis(2000L)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            RouteManager.logError("High-accuracy GPS tracking requested (1000ms interval).")
        } catch (e: SecurityException) {
            RouteManager.logError("SecurityException starting GPS location updates: ${e.message}")
            isRunning = false
        } catch (e: Exception) {
            RouteManager.logError("Error starting GPS location updates: ${e.message}")
            isRunning = false
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            RouteManager.logError("Error removing location updates: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopLocationUpdates()
        serviceJob.cancel()
        abandonAudioFocus()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaButtonPendingIntent?.let {
            audioManager.unregisterMediaButtonEventReceiver(it)
        }
        mediaSession?.release()
        ttsManager.shutdown()
    }
}
