package com.easyflow.keyboard.speech

import android.content.Context

class SpeechEngineFactory(private val context: Context) {
    val models = WhisperModelManager(context)
    fun create(): SpeechEngine {
        // Prefer Android's streaming recognizer even when Whisper is installed. On modern
        // phones this is on-device, provides live partial text, and avoids repeatedly running
        // a heavy model inside an IME process. Whisper remains the fully local fallback.
        val streaming = AndroidOnDeviceSpeechEngine(context)
        return when {
            streaming.isReady -> streaming
            models.isInstalled -> WhisperSpeechEngine(context, models)
            else -> streaming
        }
    }
}
