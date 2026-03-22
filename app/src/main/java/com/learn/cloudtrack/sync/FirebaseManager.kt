package com.learn.cloudtrack.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.learn.cloudtrack.db.CallDataEntity
import com.learn.cloudtrack.utils.Logger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    // Your Firebase Console shows a database explicitly named "cloudtrack".
    // We must specify this ID, otherwise the app hangs looking for "(default)".
    private val firestore = FirebaseFirestore.getInstance("cloudtrack")
    private val storage = FirebaseStorage.getInstance().reference

    suspend fun syncCallData(context: Context, call: CallDataEntity): Boolean {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Logger.log(context, TAG, "❌ Sync Aborted: No authenticated user found.")
            return false
        }

        return try {
            val callMap = hashMapOf(
                "userId" to user.uid,
                "userEmail" to user.email,
                "contactName" to call.contactName,
                "phoneNumber" to call.phoneNumber,
                "countryCode" to call.countryCode,
                "startTime" to call.startTime,
                "endTime" to call.endTime,
                "durationSeconds" to call.durationSeconds,
                "platform" to call.platform,
                "callType" to call.callType,
                "simId" to call.simId,
                "dialedNumber" to call.dialedNumber
            )

            // Step 1: Upload Audio if exists
            if (call.audioFilePath != null) {
                val file = File(call.audioFilePath)
                if (file.exists()) {
                    try {
                        Logger.log(context, TAG, "Found local audio: ${file.name}. Uploading to Cloud Storage...")
                        val audioRef = storage.child("call_recordings/${file.name}")
                        
                        // Use putStream to bypass strict 'file://' URI restrictions on Android 11+
                        val stream = java.io.FileInputStream(file)
                        audioRef.putStream(stream).await()
                        
                        val downloadUrl = audioRef.downloadUrl.await()
                        callMap["audioDownloadUrl"] = downloadUrl.toString()
                        Logger.log(context, TAG, "Audio Storage Upload Complete!")
                    } catch (e: Exception) {
                        Logger.log(context, TAG, "Audio Upload Failed: ${e.message}. Triggering Worker Retry...")
                        return false // Fail the sync so WorkManager retries instead of swallowing the error
                    }
                }
            }

            // Step 2: Upload Metadata to Firestore
            Logger.log(context, TAG, "Uploading metadata to Firestore...")
            
            val documentReference = withTimeout(10000) {
                firestore.collection("call_logs").add(callMap).await()
            }
            
            Logger.log(context, TAG, "Firestore Sync Complete: ${documentReference.id}")
            
            true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.log(context, TAG, "🔥 Firestore Timeout! Check Firestore DB creation or Security Rules.")
            false
        } catch (e: Throwable) {
            Logger.log(context, TAG, "🔥 Firebase Crash: ${e.toString()}")
            false
        }
    }
}
