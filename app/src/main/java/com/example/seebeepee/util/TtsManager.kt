package com.example.seebeepee.util

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.example.seebeepee.model.RouteManager
import java.util.Locale
import kotlin.math.roundToInt

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isInitialized = false
    private val pendingUtterances = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            if (isInitialized) {
                RouteManager.logError("TtsManager: Initialized successfully.")
                speak("Welcome. Press the start button when you are ready to hike and at the first waypoint.")
                synchronized(pendingUtterances) {
                    for (text in pendingUtterances) {
                        if (AppPreferences(appContext).voiceAlertsEnabled) {
                            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                            RouteManager.logError("TTS Spoken (queued): $text")
                        }
                    }
                    pendingUtterances.clear()
                }
            } else {
                RouteManager.logError("TtsManager: Language US missing or unsupported.")
            }
        } else {
            RouteManager.logError("TtsManager: Initialization failed with status $status")
        }
    }

    fun speakLiveStatus() {
        val message = generateLiveStatusMessage(appContext)
        speak(message)
    }

    fun speakCourseCorrectionIfOffCourse(): Boolean {
        val message = generateCourseCorrectionMessage(appContext)
        if (message != null) {
            speak(message)
            return true
        }
        return false
    }

    fun speak(text: String) {
        val alertsEnabled = AppPreferences(appContext).voiceAlertsEnabled

        if (alertsEnabled) {
            if (isInitialized) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                RouteManager.logError("TTS Spoken: $text")
            } else {
                synchronized(pendingUtterances) {
                    pendingUtterances.add(text)
                }
                RouteManager.logError("TTS Queued (not initialized yet): $text")
            }
        } else {
            RouteManager.logError("TTS Blocked: voiceAlertsEnabled preference is false.")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        /**
         * Generates a course correction voice message if the angular deviation ΔB = bHiker - bTarget
         * exceeds [coneAngle] in magnitude.
         *
         * If ΔB > 0 (e.g. bHiker = 30°, bTarget = 22°, ΔB = +8°), generates "Veer left 8 degrees".
         * If ΔB < 0 (e.g. bHiker = 10°, bTarget = 22°, ΔB = -12°), generates "Veer right 12 degrees".
         * Returns null if |ΔB| <= coneAngle or if rounded degrees is 0.
         */
        fun generateCourseCorrectionMessage(
            bHiker: Double,
            bTarget: Double,
            coneAngle: Float
        ): String? {
            val deltaB = CoordinateUtils.computeAngularDeviation(bHiker, bTarget)
            val absDelta = kotlin.math.abs(deltaB)
            if (absDelta <= coneAngle) {
                return null
            }
            val degrees = absDelta.roundToInt()
            if (degrees == 0) return null
            return if (deltaB > 0) {
                "Veer left $degrees degrees"
            } else {
                "Veer right $degrees degrees"
            }
        }

        fun generateCourseCorrectionMessage(
            waypoints: List<com.example.seebeepee.model.Waypoint>,
            currentIndex: Int,
            breadcrumbs: List<com.example.seebeepee.model.Breadcrumb>,
            coneAngle: Float,
            loopbackDistance: Double = 10.0,
            lowSpeedCutoff: Double = 1.2,
            smoother: HeadingSmoother? = null
        ): String? {
            if (waypoints.isEmpty() || breadcrumbs.size < 2) return null

            // Low-speed suppression guard
            val speedKmh = CoordinateUtils.calculateRecentSpeed(breadcrumbs, loopbackDistance)
            if (speedKmh < lowSpeedCutoff) {
                return null
            }

            // Distance Loopback Window
            val rawBearing = CoordinateUtils.calculateLoopbackBearing(breadcrumbs, loopbackDistance) ?: return null

            // Vector EMA smoothing
            val bHiker = smoother?.update(rawBearing) ?: rawBearing

            val idx = currentIndex.coerceIn(0, waypoints.size - 1)
            val targetWp = if (idx < waypoints.size - 1) waypoints[idx + 1] else waypoints[idx]
            val lastBc = breadcrumbs.last()
            val bTarget = CoordinateUtils.calculateBearing(lastBc.x, lastBc.y, targetWp.x, targetWp.y)

            return generateCourseCorrectionMessage(bHiker, bTarget, coneAngle)
        }

        fun generateCourseCorrectionMessage(context: Context, smoother: HeadingSmoother? = null): String? {
            val prefs = AppPreferences(context)
            return generateCourseCorrectionMessage(
                waypoints = RouteManager.waypoints,
                currentIndex = RouteManager.currentWaypointIndex,
                breadcrumbs = RouteManager.breadcrumbs,
                coneAngle = prefs.coneAngle,
                loopbackDistance = prefs.loopbackDistance,
                lowSpeedCutoff = prefs.lowSpeedCutoff,
                smoother = smoother
            )
        }

        fun formatGridPositionForTts(position: String): String {
            // Keep only the digits (drops leading letters like 'V' or any non-numeric symbols)
            val digits = position.filter { it.isDigit() }

            return if (digits.length >= 6) {
                val group1 = digits.substring(0, 3).map { it }.joinToString(" ")
                val group2 = digits.substring(3, 6).map { it }.joinToString(" ")
                "$group1, $group2"
            } else {
                val chunks = digits.chunked(3)
                chunks.joinToString(", ") { chunk ->
                    chunk.map { it }.joinToString(" ")
                }
            }
        }
        fun generateLiveStatusMessage(
            waypoints: List<com.example.seebeepee.model.Waypoint>,
            currentIndex: Int,
            breadcrumbs: List<com.example.seebeepee.model.Breadcrumb>,
            flatPace: Double,
            climbPenalty: Double
        ): String {
            if (waypoints.isEmpty()) {
                return "No active route loaded."
            }

            val idx = currentIndex.coerceIn(0, waypoints.size - 1)
            val targetWp = if (idx < waypoints.size - 1) waypoints[idx + 1] else waypoints[idx]

            val lastBc = breadcrumbs.lastOrNull()
            val liveX = lastBc?.x ?: waypoints[idx].x
            val liveY = lastBc?.y ?: waypoints[idx].y
            val liveAlt = lastBc?.altitude ?: waypoints[idx].altitude

            val posStr = CoordinateUtils.formatGridReference6Digit(liveX, liveY)
            val dist = CoordinateUtils.calculateDistance(liveX, liveY, targetWp.x, targetWp.y)
            val rawBearing = CoordinateUtils.calculateBearing(liveX, liveY, targetWp.x, targetWp.y)
            val bearing = (rawBearing.roundToInt() % 360 + 360) % 360
            val climb = maxOf(0.0, targetWp.altitude - liveAlt)
            val timeMinutes = NaismithEngine.estimateHikingTime(dist, climb, flatPace, climbPenalty)

            return "Bearing $bearing degrees, Distance ${dist.roundToInt()} meters, Time ${timeMinutes.roundToInt()} minutes, Position ${formatGridPositionForTts(posStr)}."
        }

        fun generateLiveStatusMessage(context: Context): String {
            val prefs = AppPreferences(context)
            return generateLiveStatusMessage(
                waypoints = RouteManager.waypoints,
                currentIndex = RouteManager.currentWaypointIndex,
                breadcrumbs = RouteManager.breadcrumbs,
                flatPace = prefs.flatPace,
                climbPenalty = prefs.climbPenalty
            )
        }
    }
}