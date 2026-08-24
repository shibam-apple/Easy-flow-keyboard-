package com.easyflow.keyboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import com.easyflow.keyboard.speech.SpeechEngine
import com.easyflow.keyboard.speech.SpeechEngineFactory
import com.easyflow.keyboard.text.ProcessedText
import com.easyflow.keyboard.text.TranscriptRefinementPipeline
import com.easyflow.keyboard.text.TranscriptStabilizer
import com.easyflow.keyboard.text.WritingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EasyFlowImeService : InputMethodService(), SpeechEngine.Listener {
    private val coral = 0xffff4f67.toInt()
    private val magenta = 0xffff2e9c.toInt()
    private val ink = 0xff202126.toInt()
    private val graphite = 0xff4b4d55.toInt()
    private val mint = 0xff10a66a.toInt()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var surface: FrameLayout
    private lateinit var glassMaterial: LiquidGlassDrawable
    private lateinit var transcriptLens: FrameLayout
    private lateinit var compactTranscriptScroll: HorizontalScrollView
    private lateinit var transcript: TextView
    private lateinit var expandedTranscriptScroll: ScrollView
    private lateinit var expandedTranscript: TextView
    private lateinit var status: TextView
    private lateinit var mic: Button
    private lateinit var backspace: Button
    private lateinit var enter: Button
    private lateinit var collapse: Button
    private lateinit var engine: SpeechEngine
    private lateinit var pipeline: TranscriptRefinementPipeline

    private val stabilizer = TranscriptStabilizer()
    private var writingContext = WritingContext()
    private var speechState = SpeechEngine.State.IDLE
    private var processed: ProcessedText? = null
    private var expansion = 0f
    private var expanded = false
    private var geometryAnimator: ValueAnimator? = null
    private var micPulse: AnimatorSet? = null
    private var glassAnimator: ValueAnimator? = null
    private var refinementJob: Job? = null
    private var refinementGeneration = 0
    private var lastVisualUpdate = 0L

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun lerp(a: Int, b: Int, p: Float) = (a + (b - a) * p).toInt()

    private fun circle(fill: Int, stroke: Int = 0x66ffffff): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(fill, if (fill == coral) magenta else fill),
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(1), stroke)
    }

    private fun overlayRipple(): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(0x24ffffff), null, mask)
    }

    private fun control(symbol: String, description: String, color: Int, click: () -> Unit) = Button(this).apply {
        text = symbol
        contentDescription = description
        isAllCaps = false
        textSize = 22f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setTextColor(color)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        background = overlayRipple()
        elevation = 0f
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            click()
        }
    }

    override fun onCreateInputView(): View {
        engine = SpeechEngineFactory(this).create()
        pipeline = TranscriptRefinementPipeline(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(9), dp(5), dp(9), dp(7))
            setBackgroundColor(Color.TRANSPARENT)
        }
        glassMaterial = LiquidGlassDrawable(this, 28f)
        surface = FrameLayout(this).apply {
            background = glassMaterial
            elevation = dp(13).toFloat()
            clipChildren = false
            clipToPadding = false
        }
        root.addView(surface, LinearLayout.LayoutParams(-1, dp(62)))

        transcriptLens = FrameLayout(this).apply {
            background = null
            elevation = 0f
            clipChildren = true
            isClickable = true
            isFocusable = true
            contentDescription = "Expand live transcript"
            setOnClickListener { setExpanded(!expanded) }
        }
        transcript = TextView(this).apply {
            text = "Tap the microphone and speak"
            setTextColor(ink)
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            isSingleLine = true
            setPadding(dp(13), 0, dp(13), 0)
        }
        compactTranscriptScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(transcript, FrameLayout.LayoutParams(-2, -1))
            setOnClickListener { setExpanded(true) }
        }
        transcriptLens.addView(compactTranscriptScroll, FrameLayout.LayoutParams(-1, -1))

        expandedTranscript = TextView(this).apply {
            text = transcript.text
            setTextColor(ink)
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.BOTTOM
            setLineSpacing(0f, 1.14f)
            setPadding(dp(17), dp(12), dp(17), dp(12))
        }
        expandedTranscriptScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            alpha = 0f
            addView(expandedTranscript, FrameLayout.LayoutParams(-1, -2))
            setOnClickListener { setExpanded(false) }
        }
        transcriptLens.addView(expandedTranscriptScroll, FrameLayout.LayoutParams(-1, -1))
        surface.addView(transcriptLens)

        status = TextView(this).apply {
            text = "Ready"
            textSize = 12f
            setTextColor(0xff777981.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            alpha = 0f
        }
        surface.addView(status)

        mic = control("≋", "Start voice input", Color.WHITE) { toggleListening() }.apply {
            background = circle(coral)
            textSize = 24f
            elevation = dp(7).toFloat()
        }
        backspace = control("⌫", "Backspace", ink) { deleteOneCharacter() }
        enter = control("↵", "Enter", mint) { pressEnter() }
        collapse = control("⌄", "Collapse transcript", graphite) { setExpanded(false) }.apply { alpha = 0f }
        surface.addView(mic); surface.addView(backspace); surface.addView(enter); surface.addView(collapse)

        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGeometry(expansion) }
        surface.post { applyGeometry(0f) }
        scope.launch(Dispatchers.Default) { pipeline.prewarm() }
        return root
    }

    private fun applyGeometry(p: Float) {
        if (!::surface.isInitialized || surface.width == 0) return
        expansion = p
        val w = surface.width
        val compact = dp(40)
        val gap = dp(5)
        val right = dp(8)
        val controlsWidth = compact * 3 + gap * 2
        val compactMicX = w - right - controlsWidth
        val lensRight = compactMicX - dp(6)

        place(transcriptLens, lerp(dp(8), dp(14), p), lerp(dp(10), dp(14), p),
            lerp((lensRight - dp(8)).coerceAtLeast(dp(80)), w - dp(28), p), lerp(dp(42), dp(104), p))
        place(mic, lerp(compactMicX, w / 2 - dp(32), p), lerp(dp(11), dp(146), p), lerp(compact, dp(64), p), lerp(compact, dp(64), p))
        place(backspace, lerp(compactMicX + compact + gap, w / 2 - dp(106), p), lerp(dp(11), dp(154), p), lerp(compact, dp(48), p), lerp(compact, dp(48), p))
        place(enter, lerp(compactMicX + (compact + gap) * 2, w / 2 + dp(57), p), lerp(dp(11), dp(154), p), lerp(compact, dp(48), p), lerp(compact, dp(48), p))
        place(status, dp(12), dp(122), w - dp(24), dp(20))
        place(collapse, w - dp(50), dp(158), dp(38), dp(38))
        status.alpha = p
        collapse.alpha = p
        collapse.isEnabled = p > .8f
        compactTranscriptScroll.alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
        expandedTranscriptScroll.alpha = ((p - .22f) * 1.7f).coerceIn(0f, 1f)
        compactTranscriptScroll.isEnabled = p < .45f
        expandedTranscriptScroll.isEnabled = p > .55f
    }

    private fun place(view: View, x: Int, y: Int, width: Int, height: Int) {
        val lp = (view.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(width, height)
        if (lp.width != width || lp.height != height || lp.leftMargin != x || lp.topMargin != y) {
            lp.width = width; lp.height = height; lp.leftMargin = x; lp.topMargin = y
            view.layoutParams = lp
        }
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value && geometryAnimator == null) return
        expanded = value
        geometryAnimator?.cancel()
        val from = expansion
        val to = if (value) 1f else 0f
        geometryAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 430
            interpolator = PathInterpolator(.18f, .88f, .22f, 1f)
            addUpdateListener {
                val p = it.animatedValue as Float
                val lp = surface.layoutParams
                lp.height = lerp(dp(62), dp(218), p)
                surface.layoutParams = lp
                applyGeometry(p)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    geometryAnimator = null
                    transcriptLens.contentDescription = if (expanded) "Collapse live transcript" else "Expand live transcript"
                }
            })
            start()
        }
        surface.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun toggleListening() {
        if (speechState != SpeechEngine.State.IDLE) { engine.stop(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow microphone in Easy Flow"
            setExpanded(true)
            return
        }
        refinementGeneration++
        refinementJob?.cancel()
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(600, 0)?.toString().orEmpty()
        writingContext = WritingContext(beforeCursor, currentInputEditorInfo?.packageName.orEmpty())
        engine.setContext("App: ${writingContext.appPackage}\nText before cursor: $beforeCursor")
        stabilizer.reset(); processed = null
        showTranscript("Starting local transcription…", provisional = true)
        engine.start(this)
    }

    private fun deleteOneCharacter() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun pressEnter() {
        val connection = currentInputConnection ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onState(state: SpeechEngine.State) = runOnMain {
        speechState = state
        status.text = when (state) {
            SpeechEngine.State.IDLE -> if (processed == null) "Ready" else "Inserted"
            SpeechEngine.State.LISTENING -> "Listening"
            SpeechEngine.State.PROCESSING -> "Finishing…"
        }
        if (state == SpeechEngine.State.LISTENING && transcript.text.toString().startsWith("Starting")) {
            showTranscript("Listening · speak now", provisional = true)
        }
        mic.text = if (state != SpeechEngine.State.IDLE) "■" else "≋"
        mic.contentDescription = if (state != SpeechEngine.State.IDLE) "Stop voice input" else "Start voice input"
        if (state == SpeechEngine.State.LISTENING) startMicPulse() else stopMicPulse()
    }

    override fun onPartial(text: String, stability: Float) = runOnMain {
        showTranscript(stabilizer.update(text), provisional = stability < .88f)
        status.text = "Listening"
    }

    override fun onFinal(text: String, confidence: Float) = runOnMain {
        val token = ++refinementGeneration
        showTranscript(text, provisional = true)
        status.text = if (pipeline.hasLocalLlm) "Polishing locally…" else "Formatting…"
        refinementJob?.cancel()
        refinementJob = scope.launch {
            val result = pipeline.refine(text, confidence, writingContext)
            if (token != refinementGeneration) return@launch
            processed = result
            showTranscript(result.text, provisional = false)
            currentInputConnection?.commitText(result.text, 1)
            status.text = if (result.changes.contains("Gemma local cleanup")) "Polished and inserted" else "Inserted"
            surface.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    override fun onError(message: String, recoverable: Boolean) = runOnMain {
        status.text = message
        showTranscript(message, provisional = true)
        if (!recoverable) setExpanded(true)
        surface.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    private fun showTranscript(value: String, provisional: Boolean) {
        if (transcript.text.toString() == value) return
        transcript.text = value
        expandedTranscript.text = value
        transcript.setTextColor(if (provisional) 0xff666871.toInt() else ink)
        expandedTranscript.setTextColor(if (provisional) 0xff666871.toInt() else ink)
        compactTranscriptScroll.post {
            val target = (transcript.width - compactTranscriptScroll.width).coerceAtLeast(0)
            compactTranscriptScroll.smoothScrollTo(target, 0)
        }
        expandedTranscriptScroll.post {
            val target = (expandedTranscript.height - expandedTranscriptScroll.height).coerceAtLeast(0)
            expandedTranscriptScroll.smoothScrollTo(0, target)
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastVisualUpdate > 70) {
            expandedTranscript.animate().cancel()
            expandedTranscript.alpha = .82f
            expandedTranscript.translationY = dp(3).toFloat()
            expandedTranscript.animate().alpha(1f).translationY(0f).setDuration(170).start()
            lastVisualUpdate = now
        }
    }

    private fun startMicPulse() {
        if (micPulse?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(mic, View.SCALE_X, 1f, 1.055f)
        val scaleY = ObjectAnimator.ofFloat(mic, View.SCALE_Y, 1f, 1.055f)
        listOf(scaleX, scaleY).forEach {
            it.duration = 720; it.repeatCount = ValueAnimator.INFINITE; it.repeatMode = ValueAnimator.REVERSE
        }
        micPulse = AnimatorSet().apply { playTogether(scaleX, scaleY); start() }
        glassAnimator?.cancel()
        glassAnimator = ValueAnimator.ofFloat(.12f, .88f).apply {
            duration = 2100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = PathInterpolator(.32f, 0f, .2f, 1f)
            addUpdateListener { glassMaterial.highlightPhase = it.animatedValue as Float }
            start()
        }
    }

    private fun stopMicPulse() {
        micPulse?.cancel(); micPulse = null
        glassAnimator?.cancel(); glassAnimator = null
        if (::glassMaterial.isInitialized) glassMaterial.highlightPhase = .18f
        if (::mic.isInitialized) mic.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    private fun runOnMain(action: () -> Unit) {
        if (::surface.isInitialized) surface.post { action() }
    }

    override fun onFinishInput() {
        refinementGeneration++; refinementJob?.cancel()
        if (::engine.isInitialized) engine.cancel()
        super.onFinishInput()
    }

    override fun onDestroy() {
        geometryAnimator?.cancel(); stopMicPulse(); refinementJob?.cancel(); scope.cancel()
        if (::engine.isInitialized) engine.cancel()
        super.onDestroy()
    }
}
