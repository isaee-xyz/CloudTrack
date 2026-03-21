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

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(applicationContext).callDataDao()
            allCalls = dao.getAllCalls()
            withContext(Dispatchers.Main) {
                applyFilter(chipGroupFilters.checkedChipId)
            }
        }
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
