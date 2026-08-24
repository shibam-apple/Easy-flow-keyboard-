package com.easyflow.keyboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.easyflow.keyboard.speech.SpeechEngine
import com.easyflow.keyboard.speech.SpeechEngineFactory
import com.easyflow.keyboard.text.FlowTextProcessor
import com.easyflow.keyboard.text.ProcessedText
import com.easyflow.keyboard.text.TranscriptStabilizer
import com.easyflow.keyboard.text.WritingContext

class EasyFlowImeService : InputMethodService(), SpeechEngine.Listener {
    private val coral = 0xffff4f59.toInt()
    private val ink = 0xff19191b.toInt()
    private val secondary = 0xff85858b.toInt()
    private val hairline = 0xffe3e3e8.toInt()
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var transcriptScroller: ScrollView
    private lateinit var detail: TextView
    private lateinit var mic: Button
    private lateinit var engine: SpeechEngine
    private val processor = FlowTextProcessor()
    private val stabilizer = TranscriptStabilizer()
    private var processed: ProcessedText? = null
    private var lastInserted = ""
    private var state = SpeechEngine.State.IDLE
    private var writingContext = WritingContext()
    private var micPulse: AnimatorSet? = null

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
            setPadding(dp(10), dp(6), dp(10), dp(9))
            background = panel(24, 0xfff9f9fb.toInt(), 0xffdedee4.toInt())
            elevation = dp(12).toFloat()
        }

        root.addView(View(this).apply { background = panel(3, 0xffc9c9cf.toInt(), Color.TRANSPARENT) },
            LinearLayout.LayoutParams(dp(38), dp(4)).apply { bottomMargin = dp(6) })

        val transcriptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = panel(18, 0xfaffffff.toInt())
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status = label("Ready", 11f, coral, Typeface.BOLD)
        meta.addView(status, LinearLayout.LayoutParams(0, dp(16), 1f))
        detail = label(engine.id, 11f, secondary).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        meta.addView(detail, LinearLayout.LayoutParams(0, dp(16), 1.4f))
        transcriptCard.addView(meta, LinearLayout.LayoutParams(-1, dp(15)))
        transcript = label("Tap the microphone and speak.", 16f, ink, Typeface.BOLD).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            gravity = Gravity.BOTTOM
            minHeight = dp(40)
        }
        transcriptScroller = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(12))
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(transcript, FrameLayout.LayoutParams(-1, -2))
        }
        transcriptCard.addView(transcriptScroller, LinearLayout.LayoutParams(-1, dp(43)))
        root.addView(transcriptCard, LinearLayout.LayoutParams(-1, dp(70)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        controls.addView(control("↶", "Undo last insert") { undoInsert() }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(7) })
        mic = Button(this).apply {
            text = "●"
            contentDescription = "Start voice input"
            isAllCaps = false
            textSize = 24f
            setTextColor(Color.WHITE)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = panel(27, coral, 0x33ffffff)
            elevation = dp(6).toFloat()
            setOnClickListener { toggleListening() }
        }
        controls.addView(mic, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(7) })
        controls.addView(control("×", "Clear transcript") { clearDraft() }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(7) })

        val insert = Button(this).apply {
            text = "Insert"
            contentDescription = "Insert transcript"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(coral)
            minHeight = 0; minimumHeight = 0
            minWidth = 0; minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            background = panel(18, 0xf9ffffff.toInt())
            setOnClickListener { insertDraft() }
        }
        controls.addView(insert, LinearLayout.LayoutParams(dp(78), dp(46)))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(60)))
        return root
    }

    private fun toggleListening() {
        if (state == SpeechEngine.State.LISTENING) { engine.stop(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Open Easy Flow to allow microphone"
            return
        }
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(600, 0)?.toString().orEmpty()
        val appPackage = currentInputEditorInfo?.packageName.orEmpty()
        writingContext = WritingContext(beforeCursor, appPackage)
        engine.setContext("App: $appPackage\nText before cursor: $beforeCursor")
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
        showTranscript("Tap the microphone and speak naturally.", animate = false)
        detail.text = engine.id
        status.text = "Ready"
        state = SpeechEngine.State.IDLE
    }

    override fun onState(state: SpeechEngine.State) = runOnMain {
        this.state = state
        status.text = when (state) {
            SpeechEngine.State.IDLE -> if (processed == null) "Ready" else "Ready to insert"
            SpeechEngine.State.LISTENING -> "Listening"
            SpeechEngine.State.PROCESSING -> "Preparing transcript…"
        }
        mic.text = if (state == SpeechEngine.State.LISTENING) "■" else "●"
        mic.contentDescription = if (state == SpeechEngine.State.LISTENING) "Stop voice input" else "Start voice input"
        if (state == SpeechEngine.State.LISTENING) startMicPulse() else stopMicPulse()
    }

    override fun onPartial(text: String, stability: Float) = runOnMain {
        showTranscript(stabilizer.update(text))
        detail.text = "${engine.id} · live"
    }

    override fun onFinal(text: String, confidence: Float) = runOnMain {
        processed = processor.process(text, confidence, writingContext)
        showTranscript(processed!!.text)
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

    private fun showTranscript(value: String, animate: Boolean = true) {
        if (transcript.text.toString() == value) return
        transcript.text = value
        transcriptScroller.post {
            val target = (transcript.height - transcriptScroller.height + dp(3)).coerceAtLeast(0)
            if (animate) transcriptScroller.smoothScrollTo(0, target)
            else transcriptScroller.scrollTo(0, target)
        }
    }

    private fun startMicPulse() {
        if (micPulse?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(mic, View.SCALE_X, 1f, 1.055f)
        val scaleY = ObjectAnimator.ofFloat(mic, View.SCALE_Y, 1f, 1.055f)
        listOf(scaleX, scaleY).forEach {
            it.duration = 720
            it.repeatCount = ValueAnimator.INFINITE
            it.repeatMode = ValueAnimator.REVERSE
        }
        micPulse = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun stopMicPulse() {
        micPulse?.cancel()
        micPulse = null
        if (::mic.isInitialized) {
            mic.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
    }

    private fun runOnMain(action: () -> Unit) { status.post { action() } }
    override fun onFinishInput() { engine.cancel(); super.onFinishInput() }
    override fun onDestroy() {
        stopMicPulse()
        if (::engine.isInitialized) engine.cancel()
        super.onDestroy()
    }
}
