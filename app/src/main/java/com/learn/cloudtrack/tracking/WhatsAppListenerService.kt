package com.learn.cloudtrack.tracking

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.learn.cloudtrack.db.AppDatabase
import com.learn.cloudtrack.db.CallDataEntity
import com.learn.cloudtrack.sync.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WhatsAppListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppListenerService"
        private const val WA_PACKAGE = "com.whatsapp"
        private const val W4B_PACKAGE = "com.whatsapp.w4b"
    }

    // Active calls memory cache
    private val activeCalls = mutableMapOf<Int, WhatsAppCallTracker>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName != WA_PACKAGE && packageName != W4B_PACKAGE) return

        val notification = sbn.notification ?: return
        if (notification.category != NotificationCompat.CATEGORY_CALL) return

        val extras = notification.extras ?: return
        val title = extras.getString(NotificationCompat.EXTRA_TITLE) ?: "Unknown"
        val showChronometer = extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)

        val id = title.hashCode()
        var tracker = activeCalls[id]

        if (tracker == null) {
            // New call detected
            tracker = WhatsAppCallTracker(
                contactName = title,
                startTime = System.currentTimeMillis(),
                isBusiness = (packageName == W4B_PACKAGE)
            )
            activeCalls[id] = tracker
            Log.d(TAG, "New WhatsApp Call Tracking Started: $title")
        }

        if (showChronometer && !tracker.isAnswered) {
            tracker.isAnswered = true
            tracker.answeredTime = System.currentTimeMillis()
            Log.d(TAG, "WhatsApp Call Answered: $title")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName != WA_PACKAGE && packageName != W4B_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getString(NotificationCompat.EXTRA_TITLE) ?: return

        val id = title.hashCode()
        val tracker = activeCalls.remove(id)

        if (tracker != null) {
            tracker.endTime = System.currentTimeMillis()
            val durationSecs = if (tracker.isAnswered && tracker.answeredTime > 0) {
                ((tracker.endTime - tracker.answeredTime) / 1000).toInt()
            } else {
                0
            }

            val callType = if (tracker.isAnswered) "ANSWERED" else "MISSED/UNANSWERED"

            Log.d(TAG, "WhatsApp Call Ended: $title | Duration: $durationSecs secs")

            val entity = CallDataEntity(
                contactName = tracker.contactName,
                phoneNumber = "WhatsApp_Call", // Phone number isn't easily accessible via notification
                startTime = tracker.startTime,
                endTime = tracker.endTime,
                durationSeconds = durationSecs,
                platform = if (tracker.isBusiness) "WhatsApp Business" else "WhatsApp",
                callType = callType,
                simId = "Data", // WhatsApp uses pure data
                audioFilePath = null, // Recording WA is complex, left null for V1
                syncStatus = "PENDING"
            )

            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getDatabase(applicationContext).callDataDao().insertCall(entity)
                
                val syncRequest = OneTimeWorkRequestBuilder<UploadWorker>().build()
                WorkManager.getInstance(applicationContext).enqueue(syncRequest)
            }
        }
    }

    data class WhatsAppCallTracker(
        val contactName: String,
        val startTime: Long,
        val isBusiness: Boolean,
        var isAnswered: Boolean = false,
        var answeredTime: Long = 0,
        var endTime: Long = 0
    )
}
