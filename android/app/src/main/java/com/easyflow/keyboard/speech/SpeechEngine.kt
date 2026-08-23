package com.easyflow.keyboard.speech

interface SpeechEngine {
    val id: String
    val isReady: Boolean
    fun start(listener: Listener)
    fun stop()
    fun cancel()

    interface Listener {
        fun onState(state: State)
        fun onPartial(text: String, stability: Float)
        fun onFinal(text: String, confidence: Float)
        fun onError(message: String, recoverable: Boolean)
    }

    enum class State { IDLE, LISTENING, PROCESSING }
}
