package com.easyflow.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.easyflow.keyboard.speech.WhisperModelManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var modelStatus: TextView
    private lateinit var downloadButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val models = WhisperModelManager(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad); setBackgroundColor(0xffffffff.toInt()) }
        root.addView(TextView(this).apply { text = "Easy Flow"; textSize = 34f; setTextColor(0xff18191b.toInt()) })
        root.addView(TextView(this).apply { text = "Local voice typing that turns natural speech into text ready to send."; textSize = 17f; setTextColor(0xff777980.toInt()); setPadding(0, pad / 2, 0, pad) })
        modelStatus = TextView(this).apply { textSize = 16f; setTextColor(0xff333438.toInt()) }; root.addView(modelStatus)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = ProgressBar.GONE }; root.addView(progress)
        downloadButton = Button(this).apply {
            isAllCaps = false; setOnClickListener {
                isEnabled = false; progress.visibility = ProgressBar.VISIBLE
                lifecycleScope.launch {
                    val result = models.download { percent -> runOnUiThread { progress.progress = percent; modelStatus.text = "Downloading local Whisper model… $percent%" } }
                    result.onSuccess { modelStatus.text = "Local Whisper model ready · audio stays on this phone"; text = "Model installed" }
                        .onFailure { modelStatus.text = "Download failed: ${it.message}"; text = "Try download again"; isEnabled = true }
                }
            }
        }; root.addView(downloadButton)
        fun action(label: String, click: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { click() }; root.addView(this) }
        action("1. Allow microphone") { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10) }
        action("2. Enable Easy Flow") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        action("3. Choose Easy Flow") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        root.addView(TextView(this).apply { text = "Without the model, Easy Flow uses Android’s on-device recognizer when available. No transcript history is saved."; textSize = 14f; setTextColor(0xff898b90.toInt()); setPadding(0, pad, 0, 0) })
        setContentView(root); refreshModel(models)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
    }

    private fun refreshModel(models: WhisperModelManager) {
        if (models.isInstalled) { modelStatus.text = "Local Whisper model ready · audio stays on this phone"; downloadButton.text = "Model installed"; downloadButton.isEnabled = false }
        else { modelStatus.text = "Recommended: install Whisper base.en · about 60 MB"; downloadButton.text = "Download local model" }
    }
}
