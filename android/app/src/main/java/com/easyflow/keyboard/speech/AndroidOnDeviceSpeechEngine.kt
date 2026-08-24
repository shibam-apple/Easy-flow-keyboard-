package com.easyflow.keyboard.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidOnDeviceSpeechEngine(private val context: Context) : SpeechEngine, RecognitionListener {
    private val hasOnDeviceService
        get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    val isGuaranteedOnDevice: Boolean get() = hasOnDeviceService
    override val id: String
        get() = if (hasOnDeviceService) "Fast on-device · live" else "Android speech · live"
    override val isReady: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)
    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null

    override fun start(listener: SpeechEngine.Listener) {
        cancel()
        this.listener = listener
        recognizer = if (hasOnDeviceService) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(this)
        listener.onState(SpeechEngine.State.LISTENING)
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
    }

    override fun stop() { recognizer?.stopListening(); listener?.onState(SpeechEngine.State.PROCESSING) }
    override fun cancel() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null; listener = null }
    private fun best(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    override fun onPartialResults(results: Bundle?) { best(results)?.let { listener?.onPartial(it, .55f) } }
    override fun onResults(results: Bundle?) {
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()?.coerceIn(0f, 1f) ?: .72f
        best(results)?.let { listener?.onFinal(it, confidence) } ?: listener?.onError("No speech detected", true)
        listener?.onState(SpeechEngine.State.IDLE)
    }
    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error · tap to retry"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service unavailable offline"
            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that · tap to retry"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy · tap again"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard · tap to retry"
            else -> "Speech service unavailable · error $error"
        }
        listener?.onError(message, true)
        listener?.onState(SpeechEngine.State.IDLE)
    }
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { listener?.onState(SpeechEngine.State.PROCESSING) }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
