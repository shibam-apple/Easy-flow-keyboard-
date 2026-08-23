package com.easyflow.keyboard.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.moonshine.voice.MicTranscriber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MoonshineSpeechEngine(
    context: Context,
    private val models: MoonshineModelManager,
) : SpeechEngine {
    override val id = "Moonshine Small · local"
    override val isReady: Boolean get() = models.isInstalled

    private val appContext = context.applicationContext
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var mic: MicTranscriber? = null
    private var listener: SpeechEngine.Listener? = null
    private var session = 0
    private var finished = StringBuilder()
    private var currentLine = ""
    private var stopping = false
    private var finalized = false

    override fun start(listener: SpeechEngine.Listener) {
        cancel()
        this.listener = listener
        if (!isReady) {
            listener.onError("Install Moonshine AI in Easy Flow first", false)
            return
        }

        val token = ++session
        finished = StringBuilder()
        currentLine = ""
        stopping = false
        finalized = false
        listener.onState(SpeechEngine.State.PROCESSING)

        worker.execute {
            try {
                val transcriber = MicTranscriber(appContext)
                    .language("en")
                    .modelArch(MoonshineModelManager.MODEL_ARCH)
                    .onText { text ->
                        if (token != session || finalized) return@onText
                        currentLine = text.trim()
                        combinedText().takeIf { it.isNotBlank() }
                            ?.let { this.listener?.onPartial(it, .78f) }
                    }
                    .onLine { line ->
                        if (token != session || finalized) return@onLine
                        line.text?.trim()?.takeIf { it.isNotBlank() }?.let {
                            if (finished.isNotEmpty()) finished.append(' ')
                            finished.append(it)
                        }
                        currentLine = ""
                        val text = combinedText()
                        if (stopping) finish(token, text) else if (text.isNotBlank()) {
                            this.listener?.onPartial(text, .86f)
                        }
                    }
                    .onError { error ->
                        if (token != session || finalized) return@onError
                        finalized = true
                        this.listener?.onError(
                            error.message ?: "Moonshine transcription stopped",
                            true,
                        )
                        this.listener?.onState(SpeechEngine.State.IDLE)
                    }

                transcriber.load()
                if (token != session) {
                    transcriber.close()
                    return@execute
                }
                mic = transcriber
                transcriber.start()
                main.post {
                    if (token == session) this.listener?.onState(SpeechEngine.State.LISTENING)
                }
            } catch (error: RuntimeException) {
                main.post {
                    if (token == session) {
                        this.listener?.onError(
                            error.cause?.message ?: error.message ?: "Moonshine could not start",
                            true,
                        )
                        this.listener?.onState(SpeechEngine.State.IDLE)
                    }
                }
            }
        }
    }

    override fun stop() {
        if (stopping || finalized) return
        stopping = true
        val token = session
        listener?.onState(SpeechEngine.State.PROCESSING)
        mic?.stop()
        // stopStream normally emits LineCompleted. The timeout also handles a silent or very
        // short recording, so the IME can never remain stuck in Processing.
        main.postDelayed({ finish(token, combinedText()) }, 1_200)
    }

    private fun combinedText(): String = buildString {
        append(finished.toString().trim())
        if (currentLine.isNotBlank()) {
            if (isNotEmpty()) append(' ')
            append(currentLine.trim())
        }
    }.trim()

    private fun finish(token: Int, text: String) {
        if (token != session || finalized) return
        finalized = true
        if (text.isBlank()) listener?.onError("No speech detected · tap to retry", true)
        else listener?.onFinal(text, .88f)
        listener?.onState(SpeechEngine.State.IDLE)
    }

    override fun cancel() {
        ++session
        stopping = true
        finalized = true
        val oldMic = mic
        mic = null
        listener = null
        if (oldMic != null) worker.execute { oldMic.close() }
    }
}
