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
            Logger.log(context, TAG, "❌ Sync Aborted: No authenticated user found. UID is null.")
            return false
        }
        Logger.log(context, TAG, "👤 Authenticated as: ${user.email} (${user.uid})")

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
                "userCountryCode" to call.userCountryCode,
                "userNumber" to call.userNumber,
                "customerCountryCode" to call.customerCountryCode,
                "customerNumber" to call.customerNumber,
                "dialCountryCode" to call.dialCountryCode,
                "dialNumber" to call.dialNumber,
                "simId" to call.simId
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
            Logger.log(context, TAG, "📤 Sending to Firestore [db: cloudtrack | coll: call_logs]...")
            
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
}
