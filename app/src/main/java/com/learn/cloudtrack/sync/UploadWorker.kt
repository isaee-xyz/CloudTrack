package com.learn.cloudtrack.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learn.cloudtrack.db.AppDatabase
import com.learn.cloudtrack.utils.Logger

class UploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result {
        Logger.log(applicationContext, TAG, "UploadWorker Wakeup!")
        val dao = AppDatabase.getDatabase(applicationContext).callDataDao()
        
        try {
            val pendingCalls = dao.getPendingSyncCalls()
            if (pendingCalls.isEmpty()) {
                Logger.log(applicationContext, TAG, "No pending calls resting in Room DB.")
                return Result.success()
            }

            var allSuccess = true
            for (call in pendingCalls) {
                if (call.isSyncingToLSQ) {
                    Logger.log(applicationContext, TAG, "Skip: Call ${call.id} already syncing.")
                    continue
                }

                // Lock for sync
                dao.updateSyncingToLSQ(call.id, true)

                Logger.log(applicationContext, TAG, "Attempting Firebase Sync for Call ID: ${call.id}")
                val success = FirebaseManager.syncCallData(applicationContext, call)
                
                if (success) {
                    dao.updateSyncStatus(call.id, "SYNCED")
                    Logger.log(applicationContext, TAG, "SUCCESS ✅ Uploaded Call ${call.id} to Cloud!")
                } else {
                    allSuccess = false
                    dao.updateSyncingToLSQ(call.id, false) // Unlock on failure
                    Logger.log(applicationContext, TAG, "FAILED ❌ Could not upload Call ${call.id}.")
                }
            }

            return if (allSuccess) Result.success() else Result.retry()

        } catch (e: Exception) {
            Logger.log(applicationContext, TAG, "CRASH: ${e.message}")
            return Result.retry()
        }
    }
}
