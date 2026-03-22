package com.learn.cloudtrack.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.learn.cloudtrack.utils.Logger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordingService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var currentFilePath: String? = null

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        private const val CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "AudioRecordingService"
        private const val SAMPLE_RATE = 44100
        var lastRecordedFilePath: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_RECORDING -> {
                startForegroundNotification()
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CloudTrack Call Recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        // Try each source in order: best quality first, then fallbacks
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,        // Both sides — only works on privileged/rooted devices
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // Caller side — works with Accessibility elevation
            MediaRecorder.AudioSource.MIC                // Fallback — caller voice through microphone
        )

        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    val sourceName = when (source) {
                        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL (both sides)"
                        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION (caller)"
                        else -> "MIC (fallback)"
                    }
                    Logger.log(this, TAG, "🎙️ AudioRecord ONLINE with: $sourceName")
                    break
                } else {
                    record.release()
                    Logger.log(this, TAG, "⚠️ Source $source state=ERROR, trying next...")
                }
            } catch (e: Exception) {
                Logger.log(this, TAG, "⚠️ Source $source threw: ${e.message}")
            }
        }

        if (audioRecord == null) {
            Logger.log(this, TAG, "❌ All audio sources blocked. Device has full hardware lock.")
            return
        }

        val fileName = "Call_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pcm"
        val file = File(cacheDir, fileName)
        currentFilePath = file.absolutePath
        lastRecordedFilePath = currentFilePath

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            val fos = FileOutputStream(file)
            var totalBytes = 0L
            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                        }
                        fos.write(byteBuffer)
                        totalBytes += byteBuffer.size
                    }
                }
            } finally {
                fos.flush()
                fos.close()
                Logger.log(this, TAG, "⏹️ Done. File: ${file.name} | Size: ${totalBytes / 1024}KB")
                if (totalBytes < 2048) {
                    Logger.log(this, TAG, "⚠️ TINY FILE (${totalBytes}B): Mic is still being blocked by the kernel.")
                } else {
                    Logger.log(this, TAG, "✅ Audio captured successfully!")
                }
            }
        }.also { it.start() }

        Logger.log(this, TAG, "🎙️ Recording loop started → $fileName")
    }

    private fun stopRecording() {
        isRecording = false
        recordingThread?.join(3000)
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        currentFilePath = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
