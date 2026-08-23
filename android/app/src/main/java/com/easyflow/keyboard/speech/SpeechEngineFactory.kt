package com.easyflow.keyboard.speech

import android.content.Context

class SpeechEngineFactory(private val context: Context) {
    val models = MoonshineModelManager(context)
    fun create(): SpeechEngine {
        // Never block a keyboard on a first-run model download. Moonshine becomes the primary
        // engine after setup; Android speech remains the instant, zero-setup fallback.
        val streaming = AndroidOnDeviceSpeechEngine(context)
        return when {
            models.isInstalled -> MoonshineSpeechEngine(context, models)
            streaming.isReady -> streaming
            else -> streaming
        }
    }
}
