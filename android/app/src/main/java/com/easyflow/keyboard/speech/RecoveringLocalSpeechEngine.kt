package com.easyflow.keyboard.speech

import android.os.Handler
import android.os.Looper

/** Restarts a stalled Moonshine native session once without leaving the device. */
class RecoveringLocalSpeechEngine(
    private val createEngine: () -> SpeechEngine,
) : SpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var active = createEngine()
    private var client: SpeechEngine.Listener? = null
    private var contextHint = ""
    private var generation = 0
    private var attempt = 0
    private var receivedSpeech = false

    override val id: String get() = "${active.id} · local recovery"
    override val isReady: Boolean get() = active.isReady

    override fun setContext(hint: String) {
        contextHint = hint
        active.setContext(hint)
    }

    override fun start(listener: SpeechEngine.Listener) {
        cancel()
        client = listener
        attempt = 0
        receivedSpeech = false
        val token = ++generation
        startAttempt(token)
    }

    private fun startAttempt(token: Int) {
        if (token != generation) return
        runCatching { active.cancel() }
        active = createEngine().also { it.setContext(contextHint) }
        val attemptToken = attempt
        active.start(object : SpeechEngine.Listener {
            private fun current() = token == generation && attemptToken == attempt

            override fun onState(state: SpeechEngine.State) {
                if (current()) client?.onState(state)
            }

            override fun onPartial(text: String, stability: Float) {
                if (!current() || text.isBlank()) return
                receivedSpeech = true
                client?.onPartial(text, stability)
            }

            override fun onFinal(text: String, confidence: Float) {
                if (!current() || text.isBlank()) return
                receivedSpeech = true
                client?.onFinal(text, confidence)
            }

            override fun onError(message: String, recoverable: Boolean) {
                if (!current()) return
                if (recoverable && attempt == 0) retry(token)
                else client?.onError(message, recoverable)
            }
        })

        main.postDelayed({
            if (token != generation || attemptToken != attempt || receivedSpeech) return@postDelayed
            if (attempt == 0) retry(token)
            else {
                active.cancel()
                client?.onError("Local model is not responding · repair Moonshine in Easy Flow", false)
                client?.onState(SpeechEngine.State.IDLE)
            }
        }, 10_000)
    }

    private fun retry(token: Int) {
        if (token != generation || attempt != 0) return
        attempt = 1
        client?.onState(SpeechEngine.State.PROCESSING)
        main.postDelayed({ startAttempt(token) }, 180)
    }

    override fun stop() = active.stop()

    override fun cancel() {
        ++generation
        runCatching { active.cancel() }
        client = null
    }
}
