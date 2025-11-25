package com.example.bossassistant

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import android.speech.tts.TextToSpeech
import java.util.*

class CommandProcessor(private val ctx: Context, private val tts: TextToSpeech) {
    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val compName = ComponentName(ctx, MyDeviceAdminReceiver::class.java)

    fun processCommand(command: String) {
        when {
            command.contains("lock") -> {
                speak("Locking the phone")
                if (dpm.isAdminActive(compName)) {
                    dpm.lockNow()
                } else {
                    speak("Device admin not enabled. I cannot lock the phone.")
                    // you could open admin intent
                }
            }

            command.contains("unlock") || command.contains("turn on screen") -> {
                // unlocking is intentionally restricted. we can turn on screen using a wakelock or show a UI.
                speak("I cannot fully unlock for security reasons. I can open the lock screen.")
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            }

            command.contains("torch") || command.contains("flash") -> {
                val enable = command.contains("on") || command.contains("enable")
                toggleTorch(enable)
            }

            command.contains("brightness") || command.contains("dim") || command.contains("bright") -> {
                adjustBrightness(command)
            }

            command.contains("send message to") || command.contains("send sms to") -> {
                handleSendSMS(command)
            }

            command.contains("call ") -> {
                handleCall(command)
            }

            command.contains("answer call") || command.contains("pick up") -> {
                speak("Answering calls programmatically may be restricted on your device.")
                // Provide guidance; see README. Attempting with TelecomManager/Accessibility is device-dependent.
            }

            else -> {
                speak("I did not understand: $command")
            }
        }
    }

    private fun handleCall(command: String) {
        // crude parse: "call +91 98765 43210" or "call mom"
        val tokens = command.split(" ")
        val idx = tokens.indexOf("call")
        if (idx >= 0 && tokens.size > idx + 1) {
            val target = tokens.subList(idx + 1, tokens.size).joinToString(" ")
            // If target looks like phone number:
            val phone = target.filter { it.isDigit() || it == '+' }
            if (phone.length >= 6) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    ctx.startActivity(intent)
                } catch (e: SecurityException) {
                    speak("Call permission not granted.")
                }
            } else {
                speak("Calling $target. If $target is a contact name, I will open dialer for you.")
                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                dial.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(dial)
            }
        }
    }

    private fun handleSendSMS(command: String) {
        // parse pattern "send message to <number> say <message>"
        speak("Preparing to send SMS.")
        // Very basic parser; improve in production
        val parts = command.split(" to ", limit = 2)
        if (parts.size < 2) {
            speak("Please say the recipient and message.")
            return
        }
        val afterTo = parts[1]
        val sayParts = afterTo.split(" say ", limit = 2)
        val recipient = sayParts[0].trim().filter { it.isDigit() || it == '+' }
        val message = if (sayParts.size > 1) sayParts[1].trim() else "Hi"

        try {
            val sms = SmsManager.getDefault()
            sms.sendTextMessage(recipient, null, message, null, null)
            speak("Message sent to $recipient")
        } catch (e: Exception) {
            speak("Failed to send message: ${e.message}")
        }
    }

    private fun toggleTorch(on: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            // Choose first camera id with flashlight
            val ids = cm.cameraIdList
            for (id in ids) {
                val info = cm.getCameraCharacteristics(id)
                val hasFlash = info.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    cm.setTorchMode(id, on)
                    speak("Torch ${if (on) "on" else "off"}")
                    return
                }
            }
            speak("No flashlight available")
        } catch (e: Exception) {
            speak("Torch control failed: ${e.message}")
        }
    }

    private fun adjustBrightness(command: String) {
        // Very simple: "set brightness to 50" or "increase brightness"
        val numRegex = Regex("(\\d{1,3})")
        val m = numRegex.find(command)
        if (m != null) {
            val value = m.groupValues[1].toInt().coerceIn(0, 255)
            if (Settings.System.canWrite(ctx)) {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                speak("Brightness set to $value")
            } else {
                speak("Please grant write settings permission for brightness control.")
            }
        } else if (command.contains("increase")) {
            speak("Increasing brightness")
            // naive: read current and add
            try {
                val current = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                val next = (current + 30).coerceAtMost(255)
                if (Settings.System.canWrite(ctx)) {
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
                    speak("Brightness increased")
                } else speak("Please grant write settings permission.")
            } catch (e: Exception) {
                speak("Cannot change brightness: ${e.message}")
            }
        } else {
            speak("Brightness command not understood.")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}
