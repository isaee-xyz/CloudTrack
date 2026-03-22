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
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            // Delay slightly to give Android time to write the latest call to the DB and save the audio file
            delay(5000) 
            fetchLatestCallAndSave(appContext)
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
                    val rawNumber = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    
                    // Dynamic Country Code Formatting
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                    val countryIso = telephonyManager.networkCountryIso?.uppercase() ?: "AE" // Default to UAE if detection fails
                    
                    val number = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        android.telephony.PhoneNumberUtils.formatNumberToE164(rawNumber, countryIso) ?: rawNumber
                    } else {
                        rawNumber
                    }
                    
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

                    // Attempt to resolve SIM name and Number
                    var simName = accountId
                    var dialedNum: String? = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                        val activeSubs = subManager.activeSubscriptionInfoList
                        val sub = activeSubs?.find { info -> info.iccId == accountId || info.subscriptionId.toString() == accountId }
                        if (sub != null) {
                            simName = sub.displayName.toString()
                            // Try to get the actual number of the SIM. 
                            // This often requires READ_PHONE_NUMBERS permission on newer androids.
                            dialedNum = try { sub.number } catch (e: Exception) { null }
                        }
                    }

                    Logger.log(context, TAG, "📞 Grabbed Metadata! Duration: $duration sec | $name")

                    val defaultRecording = getLatestDefaultCallRecording()
                    // Prioritize the default high-quality recording (.m4a) over the fallback internal PCM recording
                    val finalAudioPath = defaultRecording ?: AudioRecordingService.lastRecordedFilePath
                    if (finalAudioPath != null) {
                        Logger.log(context, TAG, "📞 Attaching Audio Recording to Call: $finalAudioPath")
                    }

                    val (finalCountryCode, finalNationalNumber) = when {
                        number.startsWith("+971") -> "+971" to number.substring(4)
                        number.startsWith("+91") -> "+91" to number.substring(3)
                        number.startsWith("+") -> {
                            // HEURISTIC: First 1-3 digits are usually the code.
                            if (number.length > 10) number.substring(0, 3) to number.substring(3)
                            else number.substring(0, 2) to number.substring(2)
                        }
                        else -> {
                            val defaultCode = if (countryIso == "AE") "+971" else if (countryIso == "IN") "+91" else null
                            defaultCode to number
                        }
                    }

                    val entity = CallDataEntity(
                        contactName = name ?: "Unknown",
                        phoneNumber = finalNationalNumber,
                        countryCode = finalCountryCode,
                        startTime = date,
                        endTime = date + (duration * 1000L),
                        durationSeconds = duration,
                        platform = "PSTN",
                        callType = typeStr,
                        simId = simName,
                        dialedNumber = dialedNum,
                        audioFilePath = finalAudioPath,
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

    private fun getLatestDefaultCallRecording(): String? {
        val recordingsDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Recordings/Call")
        if (recordingsDir.exists() && recordingsDir.isDirectory) {
            val files = recordingsDir.listFiles { file -> 
                file.isFile && (file.name.endsWith(".m4a", ignoreCase = true) || 
                                file.name.endsWith(".mp3", ignoreCase = true) || 
                                file.name.endsWith(".amr", ignoreCase = true) || 
                                file.name.endsWith(".wav", ignoreCase = true))
            }
            if (files != null && files.isNotEmpty()) {
                val latest = files.maxByOrNull { it.lastModified() }
                // Make sure it's a recent recording (e.g. updated in the last 2 minutes)
                if (latest != null && System.currentTimeMillis() - latest.lastModified() < 2 * 60 * 1000) {
                    return latest.absolutePath
                }
            }
        }
        return null
    }
}
