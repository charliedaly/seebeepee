package com.example.seebeepee.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.gms.location.*
import com.example.seebeepee.MainActivity
import com.example.seebeepee.R
import com.example.seebeepee.model.Breadcrumb
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.util.AppPreferences
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.HeadingSmoother
import com.example.seebeepee.util.PersistenceGatekeeper
import com.example.seebeepee.util.TtsManager
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import kotlin.math.sin

class HikingForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private lateinit var ttsManager: TtsManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaSession: MediaSessionCompat? = null
    private var isRunning = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Inaudible background audio stream variables
    private var audioTrack: AudioTrack? = null
    private var audioStreamJob: Job? = null

    // Course correction variables
    private var lastCourseCorrectionTime: Long = 0L
    private val courseCorrectionCooldownMs: Long = 15000L // 15 seconds cooldown
    private lateinit var headingSmoother: HeadingSmoother
    //private val headingSmoother = HeadingSmoother(alpha = 0.3)

    private var persistenceGatekeeper: PersistenceGatekeeper? = null

    companion object {
        const val CHANNEL_ID = "HikingServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupMediaSession()
        requestAudioFocus()
        startInaudibleAudioStream()

        val prefs = AppPreferences(applicationContext)
        headingSmoother = HeadingSmoother(alpha = prefs.headingSmoothingAlpha)

        createNotificationChannel()
        setupLocationCallback()
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            mediaSession?.setActive(true)
                        }
                        else -> {}
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { _ -> },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        if (granted) {
            showToast("Audio Focus: GRANTED")
        } else {
            showToast("Audio Focus: DENIED!")
        }
        return granted
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

    private fun startInaudibleAudioStream() {
        if (audioTrack != null) return
        try {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val toneBuffer = ByteArray(bufferSize)
            val frequency = 19000.0
            for (i in toneBuffer.indices step 2) {
                val sampleIndex = i / 2
                val angle = sampleIndex * 2.0 * Math.PI * frequency / sampleRate
                val sample = (sin(angle) * 100.0).toInt().toShort()
                toneBuffer[i] = (sample.toInt() and 0xff).toByte()
                toneBuffer[i + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
            }

            audioTrack?.play()

            audioStreamJob = CoroutineScope(Dispatchers.IO + serviceJob).launch {
                while (isActive && audioTrack != null) {
                    audioTrack?.write(toneBuffer, 0, toneBuffer.size)
                    delay(300L)
                }
            }
        } catch (e: Exception) {
            RouteManager.logError("Error starting inaudible audio stream: ${e.message}")
        }
    }

    private fun stopInaudibleAudioStream() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) { }
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

                    checkWaypointAdvancement()
                    checkProximityAlert()
                    checkCourseCorrectionAlert()
                }
            }
        }
    }

    private fun checkWaypointAdvancement() {
        if (RouteManager.hikeState != HikeState.HIKING) return

        val prefs = AppPreferences(applicationContext)
        val result = RouteManager.processWaypointAdvancement(prefs)

        if (result.advanced) {
            RouteManager.currentWaypointIndex = result.newIndex
            persistenceGatekeeper?.reset()
            RouteManager.lastProximityAlertWaypointIndex = -1
            RouteManager.logError("Auto-advanced waypoint to index ${result.newIndex}: ${result.reachedWaypoint?.name}. Reason: ${result.reason}")

            result.ttsAnnouncement?.let { announcement ->
                requestAudioFocus()
                ttsManager.speak(announcement)
            }
        }
    }

    private fun checkCourseCorrectionAlert() {
        if (RouteManager.hikeState != HikeState.HIKING) return
        val hike = RouteManager.currentHike ?: return
        val prefs = AppPreferences(applicationContext)

        val result = hike.evaluateCourseCorrection(
            lowSpeedCutoff = prefs.lowSpeedCutoff,
            loopbackDistance = prefs.loopbackDistance,
            coneAngle = prefs.coneAngle,
            requiredFixes = prefs.persistenceFixes
        )

        if (result.shouldAlert && result.message != null) {
            requestAudioFocus()
            ttsManager.speak(result.message)
            RouteManager.logError("Course correction alert spoken.")
        }
    }

    private fun checkProximityAlert() {
        if (RouteManager.hikeState != HikeState.HIKING) return
        val hike = RouteManager.currentHike ?: return
        val prefs = AppPreferences(applicationContext)

        val result = hike.evaluateProximity(prefs.proximityThreshold, prefs.loopbackDistance)

        if (result.shouldAlert && result.message != null) {
            requestAudioFocus()
            ttsManager.speak(result.message)
            RouteManager.logError("Proximity alert spoken for target waypoint index ${result.targetWaypointIndex}.")
        }
    }

    private fun setupMediaSession() {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(this@HikingForegroundService, MediaButtonReceiver::class.java)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val session = MediaSessionCompat(this, "SeeBeePeeMediaSession")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setMediaButtonReceiver(pendingIntent)

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Hiking Navigation")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "SeeBeePee")
                .build()
        )

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
                .build()
        )

        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                }

                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    showToast("Key Down: ${keyEvent.keyCode}")
                    requestAudioFocus()

                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            speakCurrentLegStats()
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

        session.setActive(true)
        mediaSession = session
    }

    private fun speakCurrentLegStats() {
        requestAudioFocus()
        ttsManager.speakLiveStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            mediaSession?.let { MediaButtonReceiver.handleIntent(it, intent) }
        }

        requestAudioFocus()

        when (intent?.action) {
            ACTION_STOP -> {
                RouteManager.stopHike(AppPreferences(applicationContext))
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
                RouteManager.startHike(AppPreferences(applicationContext))
                lastCourseCorrectionTime = 0L
                headingSmoother.reset()
                persistenceGatekeeper?.reset()
                RouteManager.lastProximityAlertWaypointIndex = -1
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
                    val notification = createNotification("⚠️ Error: Location Permission Missing!")
                    startForegroundWithNotification(notification)
                    ttsManager.speak("Error. Location permissions are missing.")
                    return START_STICKY
                }

                if (!isGpsOn) {
                    val notification = createNotification("⚠️ Warning: GPS Disabled in Settings!")
                    startForegroundWithNotification(notification)
                    ttsManager.speak("Warning. GPS is disabled in settings.")
                } else {
                    val notification = createNotification("Hike Tracking Active (GPS)")
                    startForegroundWithNotification(notification)
                    mainHandler.postDelayed({
                        speakCurrentLegStats()
                    }, 800L)
                }

                startGpsTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: SecurityException) {
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
        } catch (e: Exception) {
            isRunning = false
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        RouteManager.hikeState = HikeState.IDLE
        stopLocationUpdates()
        serviceJob.cancel()
        abandonAudioFocus()
        stopInaudibleAudioStream()
        mediaSession?.release()
        ttsManager.shutdown()
    }
}