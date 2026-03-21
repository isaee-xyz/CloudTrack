package com.learn.cloudtrack.tracking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import com.learn.cloudtrack.utils.Logger

class CloudTrackAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.log(this, "AccessibilityService", "👁️ Accessibility Service Connected. Granted Elevated System Privileges.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // By merely possessing an active AccessibilityService bound to the OS,
        // Android elevates the application's background priority and lowers the
        // strict AudioManager restrictions, allowing AudioRecordingService.kt to
        // successfully force-enable the Speakerphone from the background without being
        // killed or ignored by the System UI dialer.
        
        // Advanced: We could theoretically read the screen here and search for a 
        // "Speaker" UI Node to click if AudioManager fails, but the API elevation is usually enough.
    }

    override fun onInterrupt() {
        Logger.log(this, "AccessibilityService", "❌ Accessibility Service Interrupted!")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.log(this, "AccessibilityService", "❌ Accessibility Service Unbound!")
        return super.onUnbind(intent)
    }
}
