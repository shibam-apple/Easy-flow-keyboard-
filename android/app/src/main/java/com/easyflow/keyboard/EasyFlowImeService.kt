package com.easyflow.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.inputmethodservice.InputMethodService
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.easyflow.keyboard.speech.SpeechEngine
import com.easyflow.keyboard.speech.SpeechEngineFactory
import com.easyflow.keyboard.text.FlowTextProcessor
import com.easyflow.keyboard.text.ProcessedText
import com.easyflow.keyboard.text.TranscriptStabilizer

class EasyFlowImeService : InputMethodService(), SpeechEngine.Listener {
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var detail: TextView
    private lateinit var mic: Button
    private lateinit var engine: SpeechEngine
    private val processor = FlowTextProcessor()
    private val stabilizer = TranscriptStabilizer()
    private var processed: ProcessedText? = null
    private var lastInserted = ""
    private var state = SpeechEngine.State.IDLE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }
    private fun label(value: String, size: Float, color: Int = 0xff18191b.toInt()) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun action(value: String, click: () -> Unit) = Button(this).apply { text = value; isAllCaps = false; setTextColor(0xff242529.toInt()); background = background(0x00ffffff, 24); setOnClickListener { click() } }

    override fun onCreateInputView(): View {
        engine = SpeechEngineFactory(this).create()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(18), dp(12), dp(18), dp(14)); background = background(0xfffbfcfe.toInt(), 28, 0x22707988) }
        root.addView(View(this).apply { background = background(0xffd7d9dd.toInt(), 3) }, LinearLayout.LayoutParams(dp(46), dp(5)).apply { bottomMargin = dp(12) })
        transcript = label("Tap the microphone and speak naturally.", 21f).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(4)) }
        root.addView(transcript, LinearLayout.LayoutParams(-1, dp(78)))
        detail = label(engine.id, 12f, 0xff85878d.toInt()).apply { gravity = Gravity.CENTER }
        root.addView(detail, LinearLayout.LayoutParams(-1, dp(28)))
        status = label("Ready", 15f, 0xff66686d.toInt()).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(36)))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = background(0xddffffff.toInt(), 30, 0x22707988); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        controls.addView(action("↶") { undoInsert() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        mic = action("●") { toggleListening() }.apply { textSize = 24f; setTextColor(Color.WHITE); background = background(0xffd51c32.toInt(), 28, 0xffeef5ff.toInt()) }
        controls.addView(mic, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(action("⌫") { clearDraft() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(action("Insert") { insertDraft() }.apply { setTextColor(0xffc9142d.toInt()); textSize = 16f }, LinearLayout.LayoutParams(0, dp(56), 1.35f))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(68)))
        return root
    }

    private fun toggleListening() {
        if (state == SpeechEngine.State.LISTENING) { engine.stop(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { status.text = "Open Easy Flow to allow microphone"; return }
        stabilizer.reset(); processed = null; engine.start(this)
    }

    private fun insertDraft() {
        val value = processed?.text?.takeIf { it.isNotBlank() } ?: return
        currentInputConnection?.commitText(value, 1); lastInserted = value; status.text = "Inserted"; detail.text = "Tap undo to remove"
    }

    private fun undoInsert() {
        if (lastInserted.isNotBlank()) { currentInputConnection?.deleteSurroundingText(lastInserted.length, 0); status.text = "Insert undone"; lastInserted = "" }
        else status.text = "Nothing to undo"
    }

    private fun clearDraft() { engine.cancel(); processed = null; transcript.text = "Tap the microphone and speak naturally."; detail.text = engine.id; status.text = "Ready"; state = SpeechEngine.State.IDLE }

    override fun onState(state: SpeechEngine.State) {
        runOnMain {
            this.state = state
            status.text = when (state) { SpeechEngine.State.IDLE -> if (processed == null) "Ready" else "Ready to insert"; SpeechEngine.State.LISTENING -> "Listening… tap again to finish"; SpeechEngine.State.PROCESSING -> "Cleaning your words…" }
            mic.text = if (state == SpeechEngine.State.LISTENING) "■" else "●"
        }
    }

    override fun onPartial(text: String, stability: Float) = runOnMain {
        val stable = stabilizer.update(text); transcript.text = stable
        detail.text = "${engine.id} · live"
    }

    override fun onFinal(text: String, confidence: Float) = runOnMain {
        processed = processor.process(text, confidence); transcript.text = processed!!.text
        detail.text = when { processed!!.requiresReview -> "Review suggested · ${processed!!.changes.joinToString()}"; processed!!.changes.isNotEmpty() -> processed!!.changes.joinToString(" · "); else -> "High-confidence transcript" }
        status.text = "Ready to insert"
    }

    override fun onError(message: String, recoverable: Boolean) = runOnMain { status.text = message; if (!recoverable) detail.text = "Open Easy Flow settings" }
    private fun runOnMain(action: () -> Unit) { status.post { action() } }
    override fun onFinishInput() { engine.cancel(); super.onFinishInput() }
    override fun onDestroy() { if (::engine.isInitialized) engine.cancel(); super.onDestroy() }
}
