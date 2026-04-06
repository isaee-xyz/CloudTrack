package com.il.Callytics.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.il.Callytics.db.CallDataEntity
import com.il.Callytics.utils.Logger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    // Your Firebase Console shows a database explicitly named "callytics".
    // We must specify this ID, otherwise the app hangs looking for "(default)".
    private val firestore = FirebaseFirestore.getInstance("callytics")
    private val storage = FirebaseStorage.getInstance().reference

    /** IST timezone formatter — produces "2024-04-07T00:21:27+05:30" */
    private val istFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    private fun toIST(epochMs: Long): String = istFormatter.format(Date(epochMs)) + "+05:30"

    suspend fun syncCallData(context: Context, call: CallDataEntity): Boolean {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Logger.log(context, TAG, "❌ Sync Aborted: No authenticated user found. UID is null.")
            return false
        }
        Logger.log(context, TAG, "👤 Authenticated as: ${user.email} (${user.uid})")

        return try {
            val callMap = hashMapOf(
                "userId"              to user.uid,
                "userEmail"           to user.email,
                "contactName"         to call.contactName,
                "phoneNumber"         to call.phoneNumber,
                "countryCode"         to call.countryCode,
                "startTime"           to call.startTime,
                "endTime"             to call.endTime,
                "startTimeIST"        to toIST(call.startTime),   // ✅ Human-readable IST
                "endTimeIST"          to toIST(call.endTime),     // ✅ Human-readable IST
                "durationSeconds"     to call.durationSeconds,
                "platform"            to call.platform,
                "callType"            to call.callType,
                "userCountryCode"     to call.userCountryCode,
                "userNumber"          to call.userNumber,
                "customerCountryCode" to call.customerCountryCode,
                "customerNumber"      to call.customerNumber,
                "dialCountryCode"     to call.dialCountryCode,
                "dialNumber"          to call.dialNumber,
                "simId"               to call.simId,
                "lsqSyncStatus"       to call.lsqSyncStatus       // ✅ LSQ sync tracking
            )

            // Step 1: Upload Audio if exists
            if (call.audioFilePath != null) {
                val file = File(call.audioFilePath)
                if (file.exists()) {
                    try {
                        Logger.log(context, TAG, "📦 File: ${file.name} | Size: ${file.length()} bytes")
                        Logger.log(context, TAG, "🚀 Uploading to Storage: call_recordings/${file.name}")

                        val audioRef = storage.child("call_recordings/${file.name}")
                        val stream = java.io.FileInputStream(file)
                        audioRef.putStream(stream).await()

                        Logger.log(context, TAG, "✅ Storage Upload Done. Getting URL...")
                        val downloadUrl = audioRef.downloadUrl.await()
                        callMap["audioDownloadUrl"] = downloadUrl.toString()
                        Logger.log(context, TAG, "🔗 Audio URL: $downloadUrl")
                    } catch (e: Exception) {
                        Logger.log(context, TAG, "❌ Audio Upload Error: ${e.message}")
                        return false
                    }
                } else {
                    Logger.log(context, TAG, "⚠️ Audio path exists but file not found: ${call.audioFilePath}")
                }
            }

            // Step 2: Upload Metadata to Firestore
            Logger.log(context, TAG, "📤 Sending to Firestore [db: callytics | coll: call_logs]...")

            val documentReference = withTimeout(15000) {
                firestore.collection("call_logs").add(callMap).await()
            }

            Logger.log(context, TAG, "✅ Firestore Sync Success! DocID: ${documentReference.id}")

            true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.log(context, TAG, "🔥 Firestore Timeout! Check Firestore DB creation or Security Rules.")
            false
        } catch (e: Throwable) {
            Logger.log(context, TAG, "🔥 Firebase Crash: ${e.toString()}")
            false
        }
    }

    /**
     * Purges Firestore call_logs docs where userNumber does NOT match the current owner.
     * Call this after login or on app start to clean up stale/mismatched data.
     */
    suspend fun purgeStaleLeads(context: Context, ownerNumber: String) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        Logger.log(context, TAG, "🧹 Purging stale leads for owner: $ownerNumber")

        try {
            // Query all docs for this userId
            val snapshot = withTimeout(15000) {
                firestore.collection("call_logs")
                    .whereEqualTo("userId", user.uid)
                    .get()
                    .await()
            }

            var purgeCount = 0
            for (doc in snapshot.documents) {
                val docUserNumber = doc.getString("userNumber")
                // Delete if userNumber doesn't match the owner's number
                if (docUserNumber != null && docUserNumber != ownerNumber) {
                    doc.reference.delete().await()
                    purgeCount++
                    Logger.log(context, TAG, "🗑️ Purged stale doc ${doc.id} (stored: $docUserNumber ≠ owner: $ownerNumber)")
                }
            }
            Logger.log(context, TAG, "✅ Purge complete. Removed $purgeCount stale docs.")
        } catch (e: Exception) {
            Logger.log(context, TAG, "❌ Purge failed: ${e.message}")
        }
    }
}
