package com.baputime

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class TimeSpeaker(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    fun speakTime() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)

            val msg = "The time is $hour $minute"

            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "bapuTime")
        }
    }
}