package com.learn.cloudtrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CallDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallDataEntity): Long

    @Update
    suspend fun updateCall(call: CallDataEntity)

    @Query("SELECT * FROM call_logs ORDER BY startTime DESC")
    suspend fun getAllCalls(): List<CallDataEntity>

    @Query("SELECT * FROM call_logs WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncCalls(): List<CallDataEntity>

    @Query("UPDATE call_logs SET syncStatus = :status WHERE id = :callId")
    suspend fun updateSyncStatus(callId: Int, status: String)
}
