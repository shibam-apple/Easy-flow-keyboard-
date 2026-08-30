package com.easyflow.keyboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import java.util.function.Consumer

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
    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var mic: ImageButton
    private lateinit var backspace: ImageButton
    private lateinit var enter: ImageButton
    private lateinit var engine: SpeechEngine
    private lateinit var pipeline: TranscriptRefinementPipeline
    private val glassSurfaces = mutableListOf<LiquidGlassDrawable>()
    private var crossWindowBlurListener: Consumer<Boolean>? = null

    private val stabilizer = TranscriptStabilizer()
    private var writingContext = WritingContext()
    private var speechState = SpeechEngine.State.IDLE
    private var processed: ProcessedText? = null
    private var expansion = 0f
    private var expanded = false
    private var geometryAnimator: ValueAnimator? = null
    private var micPulse: AnimatorSet? = null
    private var refinementJob: Job? = null
    private var refinementGeneration = 0
    private var lastVisualUpdate = 0L
    private var lastPartial = ""
    private var insertedText = ""

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun lerp(a: Int, b: Int, p: Float) = (a + (b - a) * p).toInt()

    private fun circle(fill: Int, stroke: Int = 0x66ffffff): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(fill, if (fill == coral) magenta else fill),
    ).apply {
        shape = GradientDrawable.OVAL
    }

    private fun overlayRipple(): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(0x24ffffff), null, mask)
    }

    private fun transcriptLensBackground(): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(21).toFloat()
        setColor(0x14ffffff)
    }

    private fun control(icon: Int, description: String, click: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        background = null
        foreground = overlayRipple()
        elevation = 0f
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            click()
        }
    }

    override fun onCreateInputView(): View {
        engine = SpeechEngineFactory(this).create()
        engine.warmUp()
        pipeline = TranscriptRefinementPipeline(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // On a 390 dp phone this produces the reference's 376 dp capsule.
            setPadding(dp(7), dp(4), dp(7), dp(6))
            setBackgroundColor(Color.TRANSPARENT)
        }
        glassMaterial = LiquidGlassDrawable(this, 37f).also { glassSurfaces += it }
        surface = FrameLayout(this).apply {
            background = glassMaterial
            elevation = 0f
            clipToOutline = true
            clipChildren = false
            clipToPadding = false
        }
        root.addView(surface, LinearLayout.LayoutParams(-1, dp(74)))

        transcriptLens = FrameLayout(this).apply {
            background = transcriptLensBackground()
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
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
        }
        transcriptLens.addView(transcript, FrameLayout.LayoutParams(-1, -1))
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

        mic = control(R.drawable.ic_easyflow_mic, "Start voice input") { toggleListening() }.apply {
            background = circle(0xb3eb191d.toInt())
            elevation = dp(5).toFloat()
        }
        backspace = control(R.drawable.ic_easyflow_backspace, "Backspace") { deleteOneCharacter() }.apply {
            background = LiquidGlassDrawable(this@EasyFlowImeService, 22f).also { glassSurfaces += it }
            alpha = 0f
        }
        enter = control(R.drawable.ic_easyflow_enter, "Enter") { performPrimaryAction() }.apply {
            background = LiquidGlassDrawable(this@EasyFlowImeService, 22f).also { glassSurfaces += it }
        }
        surface.addView(mic); surface.addView(backspace); surface.addView(enter)

        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGeometry(expansion) }
        surface.post {
            applyGeometry(0f)
            configureRealBackdropBlur()
        }
        scope.launch(Dispatchers.Default) { pipeline.prewarm() }
        return root
    }

    private fun configureRealBackdropBlur() {
        glassSurfaces.forEach { it.attachHost(surface) }
        val androidWindow = window?.window
        androidWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurListener?.let {
                getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(it)
            }
            crossWindowBlurListener = null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || androidWindow == null) {
            Log.i(GLASS_TAG, "Cross-window blur unavailable: SDK=${Build.VERSION.SDK_INT} window=$androidWindow")
            setBackdropActive(false)
            return
        }

        val windowManager = getSystemService(WindowManager::class.java)
        val capsuleBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(37).toFloat()
            setColor(0x08ffffff)
        }
        androidWindow.setBackgroundDrawable(
            InsetDrawable(capsuleBackground, dp(7), dp(4), dp(7), dp(6)),
        )
        androidWindow.setBackgroundBlurRadius(dp(28))

        val updateBlur = Consumer<Boolean> { enabled ->
            Log.i(
                GLASS_TAG,
                "Cross-window compositor blur: enabled=$enabled SDK=${Build.VERSION.SDK_INT} " +
                    "window=$androidWindow surface=$surface attached=${surface.isAttachedToWindow} " +
                    "hardware=${surface.isHardwareAccelerated}",
            )
            setBackdropActive(enabled)
        }
        crossWindowBlurListener = updateBlur
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, updateBlur)
        updateBlur.accept(windowManager.isCrossWindowBlurEnabled)
    }

    private fun setBackdropActive(enabled: Boolean) {
        glassSurfaces.forEach { it.backdropActive = enabled }
    }

    private fun applyGeometry(p: Float) {
        if (!::surface.isInitialized || surface.width == 0) return
        expansion = p
        glassMaterial.expansion = p
        val w = surface.width
        val control = dp(44)
        val gap = dp(11)
        val right = dp(13)
        val compactEnterX = w - right - control
        val compactMicX = compactEnterX - gap - control
        val lensX = dp(14)
        val lensRight = compactMicX - gap
        val expandedBackspaceX = w - right - control * 2 - gap

        // Compact geometry is a direct dp translation of Frame.svg.
        place(transcriptLens, lerp(lensX, dp(14), p), lerp(dp(16), dp(12), p),
            lerp((lensRight - lensX).coerceAtLeast(dp(96)), w - dp(28), p), lerp(dp(42), dp(106), p))
        place(mic, lerp(compactMicX, dp(14), p), lerp(dp(16), dp(134), p), control, control)
        place(backspace, expandedBackspaceX, dp(134), control, control)
        place(enter, lerp(compactEnterX, compactEnterX, p), lerp(dp(16), dp(134), p), control, control)
        place(status, dp(68), dp(146), (expandedBackspaceX - dp(78)).coerceAtLeast(dp(72)), dp(18))
        status.alpha = p
        backspace.alpha = p
        backspace.isEnabled = p > .8f
        transcript.maxLines = if (p > .22f) 3 else 1
        transcript.ellipsize = if (p > .22f) null else android.text.TextUtils.TruncateAt.END
        transcript.gravity = if (p > .22f) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        transcript.textSize = 15f + p
        transcript.setPadding(lerp(dp(12), dp(16), p), lerp(0, dp(10), p), lerp(dp(12), dp(16), p), lerp(0, dp(10), p))
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
                lp.height = lerp(dp(74), dp(188), p)
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
        stabilizer.reset(); processed = null; lastPartial = ""; insertedText = ""
        updatePrimaryAction()
        showTranscript("Starting local transcription…", provisional = true)
        engine.start(this)
    }

    private fun deleteOneCharacter() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun performPrimaryAction() {
        val connection = currentInputConnection ?: return
        processed?.text?.takeIf { it.isNotBlank() }?.let { value ->
            connection.commitText(value, 1)
            insertedText = value
            processed = null
            status.text = "Inserted · tap arrow to undo"
            transcript.setTextColor(ink)
            updatePrimaryAction()
            surface.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            return
        }
        if (insertedText.isNotBlank()) {
            connection.deleteSurroundingText(insertedText.length, 0)
            showTranscript(insertedText, provisional = false)
            processed = ProcessedText(insertedText, insertedText, 1f, emptyList(), false)
            insertedText = ""
            status.text = "Undone · edit or insert again"
            updatePrimaryAction()
            return
        }
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun updatePrimaryAction() {
        if (!::enter.isInitialized) return
        enter.contentDescription = when {
            processed?.text?.isNotBlank() == true -> "Insert transcript"
            insertedText.isNotBlank() -> "Undo inserted transcript"
            else -> "Enter"
        }
        enter.alpha = if (processed != null || insertedText.isNotBlank()) 1f else .82f
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
        mic.setImageResource(if (state != SpeechEngine.State.IDLE) R.drawable.ic_easyflow_stop else R.drawable.ic_easyflow_mic)
        mic.contentDescription = if (state != SpeechEngine.State.IDLE) "Stop voice input" else "Start voice input"
        if (state == SpeechEngine.State.LISTENING) startMicPulse() else stopMicPulse()
        glassMaterial.listening = state == SpeechEngine.State.LISTENING
    }

    override fun onPartial(text: String, stability: Float) = runOnMain {
        lastPartial = stabilizer.update(text)
        showTranscript(lastPartial, provisional = stability < .88f)
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
            status.text = if (result.changes.contains("Gemma local cleanup")) "Polished · tap arrow to insert" else "Ready · tap arrow to insert"
            updatePrimaryAction()
            setExpanded(result.requiresReview || result.text.length > 46)
            surface.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    override fun onError(message: String, recoverable: Boolean) = runOnMain {
        status.text = message
        if (lastPartial.isNotBlank()) {
            processed = ProcessedText(lastPartial, lastPartial, .55f, emptyList(), true)
            showTranscript(lastPartial, provisional = false)
            status.text = "Partial transcript saved · review and insert"
            updatePrimaryAction()
        } else {
            showTranscript(message, provisional = true)
        }
        if (!recoverable) setExpanded(true)
        surface.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    private fun showTranscript(value: String, provisional: Boolean) {
        if (transcript.text.toString() == value) return
        transcript.text = value
        transcript.setTextColor(if (provisional) 0xff666871.toInt() else ink)
        val now = SystemClock.uptimeMillis()
        if (now - lastVisualUpdate > 70) {
            transcript.animate().cancel()
            transcript.alpha = .84f
            transcript.translationY = dp(2).toFloat()
            transcript.animate().alpha(1f).translationY(0f).setDuration(150).start()
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
    }

    private fun stopMicPulse() {
        micPulse?.cancel(); micPulse = null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurListener?.let {
                getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(it)
            }
        }
        crossWindowBlurListener = null
        if (::engine.isInitialized) engine.cancel()
        super.onDestroy()
    }

    private companion object {
        const val GLASS_TAG = "EasyFlowGlass"
    }
}
