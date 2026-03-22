package com.learn.cloudtrack.utils

import android.os.Build
import android.os.Environment
import java.io.File

object OEMFolderHelper {
    private val manufacturer = Build.MANUFACTURER.lowercase()

    /**
     * Returns a prioritized list of absolute paths where different OEMs 
     * are known to store their native call recordings.
     */
    fun getPossibleRecordingDirs(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val paths = mutableListOf<String>()

        // OEM Specific Paths based on common research
        when {
            manufacturer.contains("samsung") -> {
                paths.add("Recordings/Call")
                paths.add("Call")
                paths.add("Sounds/Call Recordings")
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                paths.add("MIUI/sound_recorder/call_rec")
                paths.add("MIUI/debug_log/common/call_rec")
                paths.add("MIUI/call_rec")
                paths.add("Recordings/Call") // Some newer Xiaomi/Poco use this
            }
            manufacturer.contains("oneplus") -> {
                paths.add("Record/PhoneRecord")
                paths.add("Music/Record/Call")
                paths.add("Android/data/com.oneplus.communication.data/files/Record/PhoneRecord")
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                paths.add("Music/Recordings/Call Recordings")
                paths.add("Recordings/Call Recording")
                paths.add("Recordings")
            }
            manufacturer.contains("vivo") -> {
                paths.add("Record/Call")
                paths.add("recording/call recording")
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                paths.add("Sounds/Call Record")
                paths.add("Record")
            }
        }

        // Generic Fallbacks for any device
        paths.add("Recordings")
        paths.add("Recordings/Call")
        paths.add("Recordings/Voice")
        paths.add("Call")
        paths.add("Music/Recordings")
        paths.add("Sounds/CallRecord")

        return paths.distinct().map { File(root, it) }
    }
}
