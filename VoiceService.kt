package com.example.bossassistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.*

class VoiceService : Service(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var srIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var cmdProcessor: CommandProcessor
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        cmdProcessor = CommandProcessor(this, tts)

        // Create & start foreground notification
        startForeground(1, createNotification())

        // Setup SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // restart listening
                    restartListen()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.let { processMatches(it) }
                    restartListen()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    // Optionally use partials to detect wakeword
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partial?.let { processPartial(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            srIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            startListening()
        } else {
            // no STT available
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "boss_service_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "BOSS Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(chan)
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BOSS")
            .setContentText("Listening for hotword...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        return builder.build()
    }

    private fun startListening() {
        if (!isListening) {
            try {
                speechRecognizer.startListening(srIntent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
            }
        }
    }

    private fun restartListen() {
        isListening = false
        startListening()
    }

    private fun processPartial(partials: List<String>) {
        // Simple wakeword check in partial results:
        val combined = partials.joinToString(" ").lowercase(Locale.getDefault())
        if (combined.contains("boss") || combined.contains("hey boss")) {
            // we detected wakeword, so stop partial listening and wait for full command
            tts.speak("Yes boss", TextToSpeech.QUEUE_FLUSH, null, "wake")
            // After wake, start a new listen request to capture next command
            restartListen()
        }
    }

    private fun processMatches(matches: List<String>) {
        val text = matches.joinToString(" ").lowercase(Locale.getDefault())
        // If wakeword present, strip it and run command; otherwise try process anyway.
        val cmd = text.replace("hey boss", "").replace("boss", "").trim()
        if (cmd.isNotEmpty()) {
            cmdProcessor.processCommand(cmd)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        // tts ready
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }
}
