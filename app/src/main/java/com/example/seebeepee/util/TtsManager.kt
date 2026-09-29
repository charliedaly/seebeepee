package com.example.seebeepee.util

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.seebeepee.model.RouteManager
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    fun speak(text: String) {
        if (isInitialized && AppPreferences(context).voiceAlertsEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            RouteManager.logError("TTS Spoken: $text")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
