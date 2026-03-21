package com.learn.cloudtrack.utils

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    fun log(context: Context, tag: String, message: String) {
        Log.d(tag, message)

        val prefs = context.getSharedPreferences("cloudtrack_logs", Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val currentLogs = prefs.getString("logs", "") ?: ""
        
        val newLogLine = "[$timestamp] $tag: $message"
        val updatedLogs = "$currentLogs\n$newLogLine".split("\n").takeLast(50).joinToString("\n")
        
        prefs.edit().putString("logs", updatedLogs).apply()
    }

    fun getLogs(context: Context): String {
        return context.getSharedPreferences("cloudtrack_logs", Context.MODE_PRIVATE)
            .getString("logs", "System Booted. Awaiting Events...") ?: "System Booted. Awaiting Events..."
    }
}
