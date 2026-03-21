package com.learn.cloudtrack.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.learn.cloudtrack.db.AppDatabase
import com.learn.cloudtrack.db.CallDataEntity
import com.learn.cloudtrack.sync.UploadWorker
import com.learn.cloudtrack.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CallLogObserver {
    private const val TAG = "CallLogObserver"

    fun scheduleCallLogSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            // Delay slightly to give Android time to write the latest call to the DB
            delay(2000) 
            fetchLatestCallAndSave(context)
        }
    }

    private suspend fun fetchLatestCallAndSave(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Logger.log(context, TAG, "❌ Missing READ_CALL_LOG perm! Cannot grab metadata.")
            return
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    val name = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                    val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val duration = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                    val typeInt = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val accountId = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID))

                    val typeStr = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "OTHER"
                    }

                    // Attempt to resolve SIM name
                    var simName = accountId
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                        val activeSubs = subManager.activeSubscriptionInfoList
                        val sub = activeSubs?.find { info -> info.iccId == accountId || info.subscriptionId.toString() == accountId }
                        if (sub != null) {
                            simName = sub.displayName.toString()
                        }
                    }

                    Logger.log(context, TAG, "📞 Grabbed Metadata! Duration: $duration sec | $name")

                    val entity = CallDataEntity(
                        contactName = name ?: "Unknown",
                        phoneNumber = number,
                        startTime = date,
                        endTime = date + (duration * 1000L),
                        durationSeconds = duration,
                        platform = "PSTN",
                        callType = typeStr,
                        simId = simName,
                        audioFilePath = AudioRecordingService.lastRecordedFilePath,
                        syncStatus = "PENDING"
                    )

                    // Insert into Room
                    AppDatabase.getDatabase(context).callDataDao().insertCall(entity)

                    // Clear audio cache reference so it isn't assigned to next call incorrectly
                    AudioRecordingService.lastRecordedFilePath = null

                    // Schedule WorkManager Sync
                    Logger.log(context, TAG, "⚡ Enqueueing UploadWorker to sync this call...")
                    val syncRequest = OneTimeWorkRequestBuilder<UploadWorker>().build()
                    WorkManager.getInstance(context).enqueue(syncRequest)
                }
            }
        } catch (e: Exception) {
            Logger.log(context, TAG, "❌ Crash fetching Call Log: ${e.message}")
        }
    }
}
