package com.easyflow.keyboard.speech

import android.content.Context

class SpeechEngineFactory(private val context: Context) {
    val models = WhisperModelManager(context)
    fun create(): SpeechEngine = if (models.isInstalled) WhisperSpeechEngine(context, models) else AndroidOnDeviceSpeechEngine(context)
}
