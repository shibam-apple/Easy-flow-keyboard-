package com.easyflow.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(pad,pad*2,pad,pad); setBackgroundColor(0xffffffff.toInt()) }
        root.addView(TextView(this).apply { text="●  ●  ●\n\nEasy Flow"; textSize=32f; setTextColor(0xff18191b.toInt()) })
        root.addView(TextView(this).apply { text="A voice-first keyboard. Enable it, choose it, then tap the ruby microphone in any text field."; textSize=17f; setTextColor(0xff777980.toInt()); setPadding(0,pad/2,0,pad) })
        fun action(label:String, click:()->Unit)=Button(this).apply { text=label; setOnClickListener{click()}; root.addView(this) }
        action("1. Allow microphone") { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10) }
        action("2. Enable Easy Flow") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        action("3. Choose Easy Flow") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        root.addView(TextView(this).apply { text="Privacy: the keyboard keeps only the current draft in memory. Speech processing is provided by the speech service installed on your phone."; textSize=14f; setTextColor(0xff898b90.toInt()); setPadding(0,pad,0,0) })
        setContentView(root)
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),10)
    }
}
