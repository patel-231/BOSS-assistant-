package com.baputime

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent

class VoiceService : Service() {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var listener: HotwordListener

    override fun onCreate() {
        super.onCreate()

        startForegroundNotification()

        listener = HotwordListener(this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        recognizer.startListening(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            "bapu_time",
            "Bapu Time Listener",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, "bapu_time")
            .setContentTitle("Bapu Time Active")
            .setContentText("Listening for 'bapu time'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}