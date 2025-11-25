package com.example.bossassistant

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.ActivityManager
import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(androidx.appcompat.R.layout.abc_action_bar_title_item) // replace with your layout

        // Request runtime perms
        ActivityCompat.requestPermissions(this, requiredPermissions, 101)

        // Start the foreground voice service
        val startBtn = Button(this).apply { text = "Start BOSS (Foreground)"; setOnClickListener { startVoiceService() } }
        val stopBtn = Button(this).apply { text = "Stop BOSS"; setOnClickListener { stopVoiceService() } }

        // Add to layout (simple)
        setContentView(startBtn)

        // Ask user to enable write settings if needed
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Ask user to enable device admin
        ensureDeviceAdmin()
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "BOSS started", Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceService() {
        val intent = Intent(this, VoiceService::class.java)
        stopService(intent)
        Toast.makeText(this, "BOSS stopped", Toast.LENGTH_SHORT).show()
    }

    private fun ensureDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "BOSS needs admin to lock device.")
            startActivity(intent)
        }
    }
}
