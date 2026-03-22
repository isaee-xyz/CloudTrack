package com.learn.cloudtrack.history

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.learn.cloudtrack.R
import com.learn.cloudtrack.db.AppDatabase
import com.learn.cloudtrack.db.CallDataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CallHistoryActivity : AppCompatActivity() {

    private lateinit var rvCallHistory: RecyclerView
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var adapter: CallHistoryAdapter
    private var allCalls = listOf<CallDataEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_call_history)

        rvCallHistory = findViewById(R.id.rvCallHistory)
        chipGroupFilters = findViewById(R.id.chipGroupFilters)

        adapter = CallHistoryAdapter(emptyList())
        rvCallHistory.layoutManager = LinearLayoutManager(this)
        rvCallHistory.adapter = adapter

        chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            applyFilter(checkedIds[0])
        }

        setupFirestoreListener()
    }

    private fun setupFirestoreListener() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance("cloudtrack")

        firestore.collection("call_logs")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("CallHistory", "Firestore Listen Failed", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val calls = mutableListOf<CallDataEntity>()
                    for (doc in snapshots) {
                        try {
                            // Map Firestore doc to Entity
                            val entity = CallDataEntity(
                                id = 0, 
                                contactName = doc.getString("contactName"),
                                phoneNumber = doc.getString("phoneNumber"),
                                countryCode = doc.getString("countryCode"),
                                startTime = doc.getLong("startTime") ?: 0L,
                                endTime = doc.getLong("endTime") ?: 0L,
                                durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                                platform = doc.getString("platform") ?: "PSTN",
                                callType = doc.getString("callType") ?: "UNKNOWN",
                                userCountryCode = doc.getString("userCountryCode"),
                                userNumber = doc.getString("userNumber"),
                                customerCountryCode = doc.getString("customerCountryCode"),
                                customerNumber = doc.getString("customerNumber"),
                                dialCountryCode = doc.getString("dialCountryCode"),
                                dialNumber = doc.getString("dialNumber"),
                                simId = doc.getString("simId"),
                                audioFilePath = doc.getString("audioDownloadUrl"),
                                syncStatus = "SYNCED"
                            )
                            calls.add(entity)
                        } catch (ex: Exception) {
                            android.util.Log.e("CallHistory", "Error mapping doc: ${ex.message}")
                        }
                    }
                    allCalls = calls.sortedByDescending { it.startTime }
                    applyFilter(chipGroupFilters.checkedChipId)
                }
            }
    }

    private fun loadData() {
        // No longer needed, as setupFirestoreListener handles real-time sync
    }

    private fun applyFilter(chipId: Int) {
        val filtered = when (chipId) {
            R.id.chipIncoming -> allCalls.filter { it.callType == "INCOMING" }
            R.id.chipOutgoing -> allCalls.filter { it.callType == "OUTGOING" }
            R.id.chipRecorded -> allCalls.filter { 
                it.audioFilePath != null && File(it.audioFilePath).exists() && File(it.audioFilePath).length() > 0 
            }
            else -> allCalls // chipAll
        }
        adapter.updateList(filtered)
    }

    override fun onPause() {
        super.onPause()
        adapter.stopAudio()
    }
}
