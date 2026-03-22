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
import android.os.Environment
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(this, "Please enable 'Allow access to manage all files'", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
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


        val prefs = getSharedPreferences("CloudTrackPrefs", MODE_PRIVATE)
        val savedNumber = prefs.getString("owner_phoneNumber", "")
        binding.etOwnerNumber.setText(savedNumber)

        binding.btnSaveProfile.setOnClickListener {
            val number = binding.etOwnerNumber.text.toString().trim()
            if (number.isNotEmpty()) {
                prefs.edit().putString("owner_phoneNumber", number).apply()
                Toast.makeText(this, "Profile Saved: $number", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, com.learn.cloudtrack.history.CallHistoryActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            // Sign out from Firebase
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            
            // Sign out from Google to allow account switcher next time
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .build()
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                // Return to Login Screen and clear activity stack
                val intent = Intent(this, com.learn.cloudtrack.ui.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkSpecialPermissions()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName, ignoreCase = true)
    }

    private fun checkSpecialPermissions() {
        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.btnGrantNotificationAccess.isEnabled = !hasNotificationAccess
        binding.btnGrantNotificationAccess.text = if (hasNotificationAccess)
            "✅ WhatsApp Tracking: ACTIVE"
        else
            "Grant Notification Access (Required for WhatsApp)"

        val hasAccessibility = isAccessibilityEnabled()
        binding.btnGrantAccessibility.isEnabled = !hasAccessibility
        binding.btnGrantAccessibility.text = if (hasAccessibility)
            "✅ Microphone Elevation: ACTIVE"
        else
            "⚠️ Enable Accessibility Elevation (Required for Audio)"

        val allGranted = requiredPermissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        binding.btnGrantPermissions.isEnabled = !allGranted
        binding.btnGrantPermissions.text = if (allGranted) "✅ Core Tracking: ACTIVE" else "Enable Core Tracking"
    }
}