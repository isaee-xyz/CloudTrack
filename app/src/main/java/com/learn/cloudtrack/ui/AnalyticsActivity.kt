package com.learn.cloudtrack.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.learn.cloudtrack.R
import com.learn.cloudtrack.databinding.ActivityAnalyticsBinding
import java.io.File
import java.io.FileOutputStream
import java.util.*

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private var currentListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadAnalytics(getTimeframeStart(R.id.chipToday))
    }

    private fun setupUI() {
        // Setup Metric Headers (Using Azure Palette)
        binding.metricTotal.tvMetricLabel.text = "Total Calls"
        binding.metricTotal.iconMetric.setImageResource(android.R.drawable.ic_menu_call)
        binding.metricTotal.iconMetric.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.blue_primary))

        binding.metricConnected.tvMetricLabel.text = "Connected Calls"
        binding.metricConnected.iconMetric.setImageResource(android.R.drawable.stat_sys_phone_call)
        binding.metricConnected.iconMetric.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))

        binding.metricTalkTime.tvMetricLabel.text = "Total Talk Time"
        binding.metricTalkTime.iconMetric.setImageResource(android.R.drawable.ic_menu_recent_history)
        binding.metricTalkTime.iconMetric.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.blue_secondary))

        binding.chipGroupTimeframe.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val startTime = getTimeframeStart(checkedIds.first())
                loadAnalytics(startTime)
            }
        }

        binding.btnShareWhatsApp.setOnClickListener { shareToWhatsApp() }
    }

    private fun getTimeframeStart(chipId: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (chipId) {
            R.id.chipWeek -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            R.id.chipMonth -> calendar.add(Calendar.DAY_OF_YEAR, -30)
        }
        return calendar.timeInMillis
    }

    private fun loadAnalytics(startTime: Long) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance("cloudtrack")

        currentListener?.remove()

        currentListener = firestore.collection("call_logs")
            .whereEqualTo("userId", user.uid)
            .whereGreaterThanOrEqualTo("startTime", startTime)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("Analytics", "Firestore Listen Failed", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    processSnapshots(snapshots)
                }
            }
    }

    private fun processSnapshots(snapshots: QuerySnapshot) {
        var total = 0
        var connected = 0
        var totalSec = 0
        var incoming = 0
        var outgoing = 0
        
        val leadStats = mutableMapOf<String, Pair<Int, Int>>()

        for (doc in snapshots) {
            total++
            val duration = doc.getLong("durationSeconds")?.toInt() ?: 0
            val type = doc.getString("callType") ?: "OUTGOING"
            val phone = doc.getString("phoneNumber") ?: "Unknown"
            val name = doc.getString("contactName") ?: phone

            if (type.equals("INCOMING", true)) incoming++ else outgoing++

            if (duration > 0) {
                connected++
                totalSec += duration
            }

            val currentFlag = leadStats[name] ?: Pair(0, 0)
            leadStats[name] = Pair(currentFlag.first + 1, currentFlag.second + duration)
        }

        updateUI(total, connected, totalSec, incoming, outgoing, leadStats)
    }

    private fun updateUI(total: Int, connected: Int, totalSec: Int, incoming: Int, outgoing: Int, leadStats: Map<String, Pair<Int, Int>>) {
        binding.metricTotal.tvMetricValue.text = total.toString()
        binding.metricConnected.tvMetricValue.text = connected.toString()
        binding.metricTalkTime.tvMetricValue.text = formatSeconds(totalSec)

        if (total > 0) {
            val incomingWeight = (incoming.toFloat() / total.toFloat()) * 10
            val outgoingWeight = (outgoing.toFloat() / total.toFloat()) * 10
            
            val paramsIn = binding.viewIncomingPart.layoutParams as android.widget.LinearLayout.LayoutParams
            paramsIn.weight = incomingWeight
            binding.viewIncomingPart.layoutParams = paramsIn

            val paramsOut = binding.viewOutgoingPart.layoutParams as android.widget.LinearLayout.LayoutParams
            paramsOut.weight = outgoingWeight
            binding.viewOutgoingPart.layoutParams = paramsOut
        }
        
        binding.tvIncomingLegend.text = "● Incoming: $incoming"
        binding.tvOutgoingLegend.text = "● Outgoing: $outgoing"

        val topLead = leadStats.maxByOrNull { it.value.second }
        if (topLead != null && total > 0) {
            binding.tvTopLeadName.text = topLead.key
            binding.tvTopLeadStats.text = "${topLead.value.first} calls • ${formatSeconds(topLead.value.second)} talk time"
            binding.cardTopLead.visibility = View.VISIBLE
        } else {
            binding.cardTopLead.visibility = View.GONE
        }
    }

    private fun formatSeconds(seconds: Int?): String {
        if (seconds == null || seconds == 0) return "00:00:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun shareToWhatsApp() {
        val bitmap = captureScreen(binding.root)
        val uri = saveBitmapToCache(bitmap)
        
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(shareIntent)
            } catch (e: Exception) {
                val genericShareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(genericShareIntent, "Share Analytics"))
            }
        }
    }

    private fun captureScreen(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(externalCacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "analytics_screenshot.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            FileProvider.getUriForFile(this, "$packageName.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
