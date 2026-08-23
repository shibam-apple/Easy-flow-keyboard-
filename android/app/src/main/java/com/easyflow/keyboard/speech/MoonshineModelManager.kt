package com.easyflow.keyboard.speech

import android.content.Context
import ai.moonshine.voice.JNI
import ai.moonshine.voice.MicTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MoonshineModelManager(context: Context) {
    companion object {
        const val MODEL_ARCH = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
        private const val PREFS = "moonshine_model"
        private const val READY = "small_streaming_ready"
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isInstalled: Boolean
        get() = preferences.getBoolean(READY, false)

    suspend fun download(onProgress: (Int, String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transcriber = MicTranscriber(appContext)
                    .language("en")
                    .modelArch(MODEL_ARCH)
                    .onProgress { fraction, file ->
                        onProgress((fraction * 100).toInt().coerceIn(0, 100), file)
                    }
                try {
                    // The library resumes .part files and validates that every required model
                    // asset exists before load() succeeds.
                    transcriber.load()
                    preferences.edit().putBoolean(READY, true).apply()
                } finally {
                    transcriber.close()
                }
            }
        }
}
