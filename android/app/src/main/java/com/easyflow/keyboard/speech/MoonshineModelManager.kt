package com.easyflow.keyboard.speech

import android.content.Context
import ai.moonshine.voice.JNI
import ai.moonshine.voice.MicTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MoonshineModelManager(context: Context) {
    companion object {
        const val MEDIUM_ARCH = JNI.MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING
        const val SMALL_ARCH = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
        private const val PREFS = "moonshine_model"
        private const val MEDIUM_READY = "medium_streaming_ready"
        private const val SMALL_READY = "small_streaming_ready"
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isMediumInstalled: Boolean get() = preferences.getBoolean(MEDIUM_READY, false)
    val isSmallInstalled: Boolean get() = preferences.getBoolean(SMALL_READY, false)
    val isInstalled: Boolean get() = isMediumInstalled || isSmallInstalled
    val activeArch: Int get() = if (isMediumInstalled) MEDIUM_ARCH else SMALL_ARCH
    val activeName: String get() = if (isMediumInstalled) "Moonshine Medium" else "Moonshine Small"

    fun markActiveUnhealthy() {
        preferences.edit().putBoolean(if (isMediumInstalled) MEDIUM_READY else SMALL_READY, false).apply()
    }

    suspend fun download(onProgress: (Int, String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transcriber = MicTranscriber(appContext)
                    .language("en")
                    .modelArch(MEDIUM_ARCH)
                    .onProgress { fraction, file ->
                        onProgress((fraction * 100).toInt().coerceIn(0, 100), file)
                    }
                try {
                    // The library resumes .part files and validates that every required model
                    // asset exists before load() succeeds.
                    transcriber.load()
                    preferences.edit().putBoolean(MEDIUM_READY, true).apply()
                } finally {
                    transcriber.close()
                }
            }.onFailure { markActiveUnhealthy() }
        }
}
