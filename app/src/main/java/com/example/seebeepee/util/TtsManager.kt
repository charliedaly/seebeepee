package com.example.seebeepee.util

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.example.seebeepee.model.RouteManager
import java.util.Locale

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
                synchronized(pendingUtterances) {
                    for (text in pendingUtterances) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        RouteManager.logError("TTS Spoken (queued): $text")
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

    fun speak(text: String) {
        // Temporarily bypass preference check to test if sound works.
        // Once working, you can change this back to: if (AppPreferences(appContext).voiceAlertsEnabled)
        val alertsEnabled = true

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
}