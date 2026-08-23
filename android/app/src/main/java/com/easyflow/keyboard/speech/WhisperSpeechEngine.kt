package com.easyflow.keyboard.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.*

class WhisperSpeechEngine(private val context: Context, private val models: WhisperModelManager) : SpeechEngine {
    override val id = "Whisper base.en · local"
    override val isReady: Boolean get() = models.isInstalled
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null
    private var listener: SpeechEngine.Listener? = null
    private var samples = ArrayList<Float>(16000 * 20)
    private var whisper: WhisperContext? = null
    @Volatile private var running = false

    override fun start(listener: SpeechEngine.Listener) {
        this.listener = listener
        if (!isReady) { listener.onError("Download the local Whisper model first", false); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Microphone permission is required", false); return
        }
        cancel(); samples = ArrayList(); running = true
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        recorder?.startRecording(); listener.onState(SpeechEngine.State.LISTENING)
        captureJob = scope.launch {
            if (whisper == null) whisper = WhisperContext.createContextFromFile(models.modelFile.absolutePath)
            val buffer = ShortArray(min / 2)
            while (running) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (count > 0) for (i in 0 until count) samples.add(buffer[i] / 32768f)
            }
        }
    }

    private suspend fun transcribe(final: Boolean) {
        val audio = synchronized(samples) { samples.toFloatArray() }
        if (audio.size < 8000) return
        val text = whisper?.transcribeData(audio, printTimestamp = false)?.trim().orEmpty()
        if (text.isNotBlank()) withContext(Dispatchers.Main) {
            if (final) listener?.onFinal(text, .78f) else listener?.onPartial(text, .68f)
        }
    }

    override fun stop() {
        if (!running) return
        running = false; recorder?.stop(); recorder?.release(); recorder = null
        listener?.onState(SpeechEngine.State.PROCESSING)
        scope.launch { captureJob?.join(); transcribe(final = true); withContext(Dispatchers.Main) { listener?.onState(SpeechEngine.State.IDLE) } }
    }
    override fun cancel() { running = false; captureJob?.cancel(); recorder?.runCatching { stop(); release() }; recorder = null }
}
