package com.easyflow.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.easyflow.keyboard.speech.SpeechEngine
import com.easyflow.keyboard.speech.SpeechEngineFactory
import com.easyflow.keyboard.text.FlowTextProcessor
import com.easyflow.keyboard.text.ProcessedText
import com.easyflow.keyboard.text.TranscriptStabilizer

class EasyFlowImeService : InputMethodService(), SpeechEngine.Listener {
    private val coral = 0xffff4f59.toInt()
    private val ink = 0xff19191b.toInt()
    private val secondary = 0xff85858b.toInt()
    private val hairline = 0xffe3e3e8.toInt()
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun panel(radius: Int, fill: Int = 0xf7ffffff.toInt(), stroke: Int = hairline) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int = ink, weight: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", weight)
            includeFontPadding = false
        }

    private fun control(value: String, contentDescription: String, click: () -> Unit) = Button(this).apply {
        text = value
        this.contentDescription = contentDescription
        isAllCaps = false
        textSize = 22f
        setTextColor(secondary)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        background = panel(18, 0xf5ffffff.toInt())
        setOnClickListener { click() }
    }

    override fun onCreateInputView(): View {
        engine = SpeechEngineFactory(this).create()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(9), dp(16), dp(12))
            background = panel(27, 0xfff9f9fb.toInt(), 0xffdedee4.toInt())
            elevation = dp(12).toFloat()
        }

        root.addView(View(this).apply { background = panel(3, 0xffc9c9cf.toInt(), Color.TRANSPARENT) },
            LinearLayout.LayoutParams(dp(42), dp(5)).apply { bottomMargin = dp(7) })

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("···", 20f, secondary).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(34)))
        status = label("Ready", 14f, secondary, Typeface.BOLD).apply { gravity = Gravity.CENTER }
        header.addView(status, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(label("⌄", 21f, secondary).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(34)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(34)))

        val transcriptCard = FrameLayout(this).apply {
            background = panel(21, 0xfaffffff.toInt())
            elevation = dp(2).toFloat()
        }
        transcript = label("Tap the microphone and speak naturally.", 20f, ink).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(27))
            setLineSpacing(0f, 1.08f)
        }
        transcriptCard.addView(transcript, FrameLayout.LayoutParams(-1, -1))
        detail = label(engine.id, 11f, secondary).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setPadding(dp(18), 0, dp(18), dp(10))
        }
        transcriptCard.addView(detail, FrameLayout.LayoutParams(-1, -1))
        root.addView(transcriptCard, LinearLayout.LayoutParams(-1, dp(105)).apply { topMargin = dp(3) })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(8))
        }
        controls.addView(control("↶", "Undo last insert") { undoInsert() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(18) })
        mic = Button(this).apply {
            text = "●"
            contentDescription = "Start voice input"
            isAllCaps = false
            textSize = 24f
            setTextColor(Color.WHITE)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = panel(30, coral, 0x33ffffff)
            elevation = dp(6).toFloat()
            setOnClickListener { toggleListening() }
        }
        controls.addView(mic, LinearLayout.LayoutParams(dp(64), dp(64)))
        controls.addView(control("×", "Clear transcript") { clearDraft() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(18) })
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(82)))

        val insert = Button(this).apply {
            text = "Insert"
            contentDescription = "Insert transcript"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(coral)
            minHeight = 0; minimumHeight = 0
            background = panel(19, 0xf9ffffff.toInt())
            setOnClickListener { insertDraft() }
        }
        root.addView(insert, LinearLayout.LayoutParams(-1, dp(50)))
        return root
    }

    private fun toggleListening() {
        if (state == SpeechEngine.State.LISTENING) { engine.stop(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Open Easy Flow to allow microphone"
            return
        }
        stabilizer.reset(); processed = null; engine.start(this)
    }

    private fun insertDraft() {
        val value = processed?.text?.takeIf { it.isNotBlank() } ?: return
        currentInputConnection?.commitText(value, 1)
        lastInserted = value
        status.text = "Inserted"
        detail.text = "Tap undo to remove"
    }

    private fun undoInsert() {
        if (lastInserted.isNotBlank()) {
            currentInputConnection?.deleteSurroundingText(lastInserted.length, 0)
            status.text = "Insert undone"
            lastInserted = ""
        } else status.text = "Nothing to undo"
    }

    private fun clearDraft() {
        engine.cancel(); processed = null
        transcript.text = "Tap the microphone and speak naturally."
        detail.text = engine.id
        status.text = "Ready"
        state = SpeechEngine.State.IDLE
    }

    override fun onState(state: SpeechEngine.State) = runOnMain {
        this.state = state
        status.text = when (state) {
            SpeechEngine.State.IDLE -> if (processed == null) "Ready" else "Ready to insert"
            SpeechEngine.State.LISTENING -> "Listening"
            SpeechEngine.State.PROCESSING -> "Cleaning your words…"
        }
        mic.text = if (state == SpeechEngine.State.LISTENING) "■" else "●"
        mic.contentDescription = if (state == SpeechEngine.State.LISTENING) "Stop voice input" else "Start voice input"
    }

    override fun onPartial(text: String, stability: Float) = runOnMain {
        transcript.text = stabilizer.update(text)
        detail.text = "${engine.id} · live"
    }

    override fun onFinal(text: String, confidence: Float) = runOnMain {
        processed = processor.process(text, confidence)
        transcript.text = processed!!.text
        detail.text = when {
            processed!!.requiresReview -> "Review suggested · ${processed!!.changes.joinToString()}"
            processed!!.changes.isNotEmpty() -> processed!!.changes.joinToString(" · ")
            else -> "High-confidence transcript"
        }
        status.text = "Ready to insert"
    }

    override fun onError(message: String, recoverable: Boolean) = runOnMain {
        status.text = message
        if (!recoverable) detail.text = "Open Easy Flow settings"
    }

    private fun runOnMain(action: () -> Unit) { status.post { action() } }
    override fun onFinishInput() { engine.cancel(); super.onFinishInput() }
    override fun onDestroy() { if (::engine.isInitialized) engine.cancel(); super.onDestroy() }
}
