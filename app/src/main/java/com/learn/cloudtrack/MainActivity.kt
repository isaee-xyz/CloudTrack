package com.learn.cloudtrack

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.learn.cloudtrack.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val logUpdater = object : Runnable {
        override fun run() {
            binding.tvDebugConsole.text = com.learn.cloudtrack.utils.Logger.getLogs(this@MainActivity)
            binding.scrollViewDebug.post {
                binding.scrollViewDebug.fullScroll(android.view.View.FOCUS_DOWN)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val missing = mutableListOf<String>()
        permissions.entries.forEach {
            if (!it.value) missing.add(it.key.substringAfterLast("."))
        }
        if (missing.isEmpty()) {
            checkSpecialPermissions()
            Toast.makeText(this, "Standard Permissions Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Missing: ${missing.joinToString()}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener {
            requestPermissionLauncher.launch(requiredPermissions)
        }
        
        binding.btnGrantNotificationAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, com.learn.cloudtrack.history.CallHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        checkSpecialPermissions()
        handler.post(logUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(logUpdater)
    }

    private fun checkSpecialPermissions() {
        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.btnGrantNotificationAccess.isEnabled = !hasNotificationAccess
        binding.btnGrantNotificationAccess.text = if (hasNotificationAccess) "Notification Access: GRANTED" else "Grant Notification Access (Required for WhatsApp)"

        val allGranted = requiredPermissions.all { 
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
        }
        binding.btnGrantPermissions.isEnabled = !allGranted
        binding.btnGrantPermissions.text = if (allGranted) "Core Tracking: GRANTED" else "Enable Core Tracking"
    }
}