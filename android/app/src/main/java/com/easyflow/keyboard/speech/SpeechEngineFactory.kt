package com.easyflow.keyboard.speech

import android.content.Context

class SpeechEngineFactory(private val context: Context) {
    val models = MoonshineModelManager(context)
    fun create(): SpeechEngine {
        // Never block a keyboard on a first-run model download. Moonshine becomes the primary
        // engine after setup; Android speech remains the instant, zero-setup fallback.
        val streaming = AndroidOnDeviceSpeechEngine(context)
        return when {
            models.isInstalled -> RecoveringLocalSpeechEngine {
                MoonshineSpeechEngine(context, models)
            }
            streaming.isGuaranteedOnDevice -> streaming
            else -> LocalModelRequiredSpeechEngine
        }
    }
}

private object LocalModelRequiredSpeechEngine : SpeechEngine {
    override val id = "Moonshine required"
    override val isReady = false
    override fun start(listener: SpeechEngine.Listener) {
        listener.onError("Download Moonshine in Easy Flow to transcribe privately", false)
        listener.onState(SpeechEngine.State.IDLE)
    }
    override fun stop() = Unit
    override fun cancel() = Unit
}
