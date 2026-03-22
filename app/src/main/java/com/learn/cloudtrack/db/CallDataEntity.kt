package com.learn.cloudtrack.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactName: String?,
    val phoneNumber: String?,
    val countryCode: String?,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val platform: String, // "PSTN" or "WhatsApp"
    val callType: String, // "INCOMING", "OUTGOING", "MISSED"
    val simId: String?, // Only applies to PSTN
    val dialedNumber: String?, // The local SIM number from which the call was made
    val audioFilePath: String?,
    val syncStatus: String // "PENDING", "SYNCED", "FAILED"
)
