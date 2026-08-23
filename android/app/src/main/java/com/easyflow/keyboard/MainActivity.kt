package com.easyflow.keyboard

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.easyflow.keyboard.speech.MoonshineModelManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val coral = 0xffff4f59.toInt()
    private val ink = 0xff171719.toInt()
    private val secondary = 0xff838388.toInt()
    private val hairline = 0xffe5e5e9.toInt()
    private lateinit var modelStatus: TextView
    private lateinit var modelDetail: TextView
    private lateinit var downloadButton: Button
    private lateinit var progress: ProgressBar

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun panel(radius: Int = 24, fill: Int = 0xf7ffffff.toInt(), stroke: Int = hairline) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun text(value: CharSequence, size: Float, color: Int = ink, weight: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", weight)
            includeFontPadding = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xfffbfbfd.toInt()
        window.navigationBarColor = 0xfffbfbfd.toInt()
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        val models = MoonshineModelManager(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(52), dp(24), dp(32))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xfffbfbfd.toInt())
            addView(page, FrameLayout.LayoutParams(-1, -2))
        }

        val title = SpannableString("Easy Flow").apply {
            setSpan(ForegroundColorSpan(coral), 5, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        page.addView(text(title, 40f).apply { gravity = Gravity.CENTER })
        page.addView(text("Private AI voice keyboard", 17f, secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, dp(34))
        })

        val modelCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(12), dp(15))
            background = panel(22)
            elevation = dp(3).toFloat()
        }
        modelCard.addView(text("●", 22f, coral).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), dp(42)))
        val modelCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        modelStatus = text("", 16f, ink, Typeface.BOLD)
        modelDetail = text("", 12f, secondary).apply { setPadding(0, dp(4), 0, 0) }
        modelCopy.addView(modelStatus)
        modelCopy.addView(modelDetail)
        modelCard.addView(modelCopy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        downloadButton = Button(this).apply {
            isAllCaps = false
            textSize = 13f
            setTextColor(coral)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panel(16, 0xfffff5f5.toInt(), 0x22ff4f59)
            setOnClickListener { downloadModel(models) }
        }
        modelCard.addView(downloadButton, LinearLayout.LayoutParams(-2, -2))
        page.addView(modelCard, LinearLayout.LayoutParams(-1, -2))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(coral)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xffffe1e3.toInt())
            visibility = View.GONE
        }
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply {
            marginStart = dp(18); marginEnd = dp(18); topMargin = dp(8)
        })

        page.addView(text("▣   Transcription stays on this phone", 14f, secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(28))
        })

        val divider = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        divider.addView(View(this).apply { setBackgroundColor(hairline) }, LinearLayout.LayoutParams(0, dp(1), 1f))
        divider.addView(text("•", 18f, 0xffc6c6cb.toInt()).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), dp(22)))
        divider.addView(View(this).apply { setBackgroundColor(hairline) }, LinearLayout.LayoutParams(0, dp(1), 1f))
        page.addView(divider, LinearLayout.LayoutParams(-1, dp(24)))

        page.addView(text("SET UP EASY FLOW", 12f, secondary, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(dp(4), dp(24), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))

        val setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel(25)
            elevation = dp(2).toFloat()
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        setup.addView(actionRow("●", "Allow microphone", "Required only while listening") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        })
        setup.addView(separator())
        setup.addView(actionRow("⌨", "Enable Keyboard", "Turn on Easy Flow in Android") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setup.addView(separator())
        setup.addView(actionRow("✓", "Choose Easy Flow", "Make it your active keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        page.addView(setup, LinearLayout.LayoutParams(-1, -2))

        page.addView(text("Private by design", 13f, secondary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(6))
        })
        page.addView(text("Moonshine streams words locally while you speak.\nAndroid speech stays available until the AI model is installed.", 13f, secondary).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
        })

        setContentView(scroll)
        refreshModel(models)
    }

    private fun actionRow(symbol: String, title: String, detail: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(13), dp(10), dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        row.addView(text(symbol, 18f, coral, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = panel(18, 0xfffff7f7.toInt(), 0x20ff4f59)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 16f, ink, Typeface.BOLD))
        labels.addView(text(detail, 12f, secondary).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        row.addView(text("›", 31f, 0xffaaaab0.toInt()).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), dp(44)))
        return row
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(hairline)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(68); marginEnd = dp(12) }
    }

    private fun downloadModel(models: MoonshineModelManager) {
        downloadButton.isEnabled = false
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = models.download { percent, file ->
                runOnUiThread {
                    progress.progress = percent
                    modelStatus.text = "Installing Moonshine AI… $percent%"
                    modelDetail.text = if (file.isBlank()) "Preparing local model" else "Keep Easy Flow open · download resumes"
                }
            }
            result.onSuccess {
                modelStatus.text = "Moonshine AI ready"
                modelDetail.text = "Small Streaming · fast, accurate and local"
                downloadButton.text = "Ready"
                progress.visibility = View.GONE
            }.onFailure {
                modelStatus.text = "Download interrupted"
                modelDetail.text = "Your progress was saved · tap Resume"
                downloadButton.text = "Resume"
                downloadButton.isEnabled = true
            }
        }
    }

    private fun refreshModel(models: MoonshineModelManager) {
        if (models.isInstalled) {
            modelStatus.text = "Moonshine AI ready"
            modelDetail.text = "Small Streaming · private on-device AI"
            downloadButton.text = "Ready"
            downloadButton.isEnabled = false
        } else {
            modelStatus.text = "Upgrade transcription"
            modelDetail.text = "Install faster, more accurate local AI"
            downloadButton.text = "Install AI"
            downloadButton.isEnabled = true
        }
    }

}
