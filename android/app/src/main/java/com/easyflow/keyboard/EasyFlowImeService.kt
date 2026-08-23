package com.easyflow.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodService
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class EasyFlowImeService : InputMethodService(), RecognitionListener {
    private lateinit var status: TextView
    private lateinit var before: TextView
    private lateinit var after: TextView
    private var recognizer: SpeechRecognizer? = null
    private var draft = ""
    private var previous = ""

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun bg(color:Int, radius:Int, stroke:Int=0)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); if(stroke>0)setStroke(dp(1),stroke) }
    private fun text(value:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply { text=value; textSize=size; setTextColor(color) }
    private fun button(label:String, click:()->Unit)=Button(this).apply { text=label; isAllCaps=false; setTextColor(0xff242529.toInt()); background=bg(0xccffffff.toInt(),24,0x22707988); setOnClickListener{click()} }

    override fun onCreateInputView(): View {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(16),dp(12),dp(16),dp(16)); background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xfffbfdff.toInt(),0xfff1f5fa.toInt())).apply{cornerRadii=floatArrayOf(dp(28f),dp(28f),dp(28f),dp(28f),0f,0f,0f,0f)} }
        root.addView(View(this).apply{background=bg(0xffd8dadd.toInt(),3)},LinearLayout.LayoutParams(dp(48),dp(5)).apply{bottomMargin=dp(14)})
        val preview=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=bg(0xccffffff.toInt(),18,0x22707988)}
        before=text("Speak naturally",13f,0xff85878d.toInt()).also{preview.addView(it,LinearLayout.LayoutParams(0,dp(64),1f).apply{setMargins(dp(12),dp(8),dp(6),dp(6))})}
        preview.addView(text("→",20f,0xff85878d.toInt()))
        after=text("Easy Flow cleans it",13f,0xff18191b.toInt()).also{preview.addView(it,LinearLayout.LayoutParams(0,dp(64),1f).apply{setMargins(dp(6),dp(8),dp(12),dp(6))})}
        root.addView(preview,LinearLayout.LayoutParams(-1,dp(80)))
        status=text("Tap to speak",16f,0xff85878d.toInt()).also{it.gravity=Gravity.CENTER;root.addView(it,LinearLayout.LayoutParams(-1,dp(50)))}
        val mic=button("●  Speak") { startListening() }.apply{ textSize=20f;setTextColor(Color.WHITE); background=bg(0xffc9142d.toInt(),34,0xffedf5ff.toInt()) }
        root.addView(mic,LinearLayout.LayoutParams(dp(150),dp(68)))
        val tools=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        tools.addView(button("↶") { val temp=draft;draft=previous;previous=temp;after.text=draft;status.text="Undone" },LinearLayout.LayoutParams(0,dp(48),1f))
        tools.addView(button("◎  English (India)") { status.text="English (India)" },LinearLayout.LayoutParams(0,dp(48),2.5f).apply{setMargins(dp(8),0,dp(8),0)})
        tools.addView(button("⌫") { previous=draft;draft="";before.text="—";after.text="—";status.text="Cleared" },LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(tools,LinearLayout.LayoutParams(-1,dp(64)).apply{topMargin=dp(12)})
        root.addView(button("Insert   ↑") { if(draft.isNotBlank()){currentInputConnection.commitText(draft,1);status.text="Inserted"} }.apply{setTextColor(0xffc9142d.toInt());background=bg(0xccffffff.toInt(),28,0x22707988);textSize=18f},LinearLayout.LayoutParams(-1,dp(56)))
        return root
    }

    private fun startListening(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ status.text="Open Easy Flow to allow microphone"; return }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status.text="Speech recognition unavailable";return}
        recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this).also{it.setRecognitionListener(this)}
        status.text="Listening…"
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)})
    }
    private fun clean(raw:String):String { var s=raw.trim(); val fixes=mapOf(" tonite" to " tonight"," pls" to " please"," snd" to " send"," teh" to " the"," bfr" to " before"," mtng" to " meeting"); fixes.forEach{(a,b)->s=s.replace(a,b,true)}; if(s.isNotEmpty())s=s.replaceFirstChar{if(it.isLowerCase())it.titlecase(Locale.getDefault()) else it.toString()}; if(s.isNotEmpty()&&!s.endsWith('.')&&!s.endsWith('!')&&!s.endsWith('?'))s+=".";return s }
    private fun update(raw:String){before.text=raw;previous=draft;draft=clean(raw);after.text=draft;status.text="Ready to insert"}
    override fun onResults(results:Bundle?){results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::update)}
    override fun onPartialResults(results:Bundle?){results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{before.text=it;after.text=clean(it)}}
    override fun onError(error:Int){status.text="Tap to try again"}
    override fun onReadyForSpeech(params:Bundle?){status.text="Listening…"};override fun onBeginningOfSpeech(){};override fun onRmsChanged(rmsdB:Float){};override fun onBufferReceived(buffer:ByteArray?){};override fun onEndOfSpeech(){status.text="Cleaning…"};override fun onEvent(eventType:Int,params:Bundle?){}
    override fun onDestroy(){recognizer?.destroy();super.onDestroy()}
    private fun dp(v:Float)=(v*resources.displayMetrics.density)
}
