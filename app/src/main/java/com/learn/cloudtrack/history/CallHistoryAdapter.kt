package com.learn.cloudtrack.history

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.learn.cloudtrack.R
import com.learn.cloudtrack.db.CallDataEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHistoryAdapter(private var callList: List<CallDataEntity>) :
    RecyclerView.Adapter<CallHistoryAdapter.CallViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition = -1
    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarRunnable: Runnable? = null

    class CallViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCallType: ImageView = view.findViewById(R.id.ivCallType)
        val tvContactName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhoneNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvSimInfo: TextView = view.findViewById(R.id.tvSimInfo)
        
        val layoutAudioPlayer: LinearLayout = view.findViewById(R.id.layoutAudioPlayer)
        val btnPlayPause: ImageButton = view.findViewById(R.id.btnPlayPause)
        val audioSeekBar: SeekBar = view.findViewById(R.id.audioSeekBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallViewHolder(view)
    }

    override fun getItemCount(): Int = callList.size

    fun updateList(newList: List<CallDataEntity>) {
        callList = newList
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        val call = callList[position]

        holder.tvContactName.text = call.contactName ?: "Unknown"
        val displayPhone = if (call.countryCode != null) "${call.countryCode} ${call.phoneNumber}" else call.phoneNumber ?: "Unknown Number"
        holder.tvPhoneNumber.text = displayPhone
        holder.tvDuration.text = "${call.durationSeconds}s"

        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(call.startTime))

        holder.ivCallType.setImageResource(
            if (call.callType == "INCOMING") android.R.drawable.sym_call_incoming
            else if (call.callType == "OUTGOING") android.R.drawable.sym_call_outgoing
            else android.R.drawable.sym_call_missed
        )

        val simLabel = call.simId ?: "SIM Unknown"
        val dialedText = if (call.dialNumber != null) " (${call.dialNumber})" else ""
        holder.tvSimInfo.text = "Source: $simLabel$dialedText"

        val audioUrl = call.audioFilePath // This now contains the Firestore URL
        val hasAudio = audioUrl != null && (audioUrl.startsWith("http") || File(audioUrl).exists())

        if (hasAudio) {
            holder.layoutAudioPlayer.visibility = View.VISIBLE
            
            val isPlayingThis = (currentlyPlayingPosition == position)
            var isPlaying = false
            try {
                if (mediaPlayer != null) {
                    isPlaying = mediaPlayer!!.isPlaying
                }
            } catch (e: Exception) {
                // Player might be in an invalid state
            }
            
            holder.btnPlayPause.setImageResource(
                if (isPlayingThis && isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            
            if (isPlayingThis) {
                // Update tracker if already playing
            } else {
                holder.audioSeekBar.progress = 0
            }

            holder.btnPlayPause.setOnClickListener {
                if (isPlayingThis && mediaPlayer?.isPlaying == true) {
                    pauseAudio()
                    holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else if (isPlayingThis && mediaPlayer != null) {
                    resumeAudio()
                    holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startSeekBarUpdate(holder.audioSeekBar)
                } else {
                    playNewAudio(position, audioUrl!!, holder)
                }
            }
            
            holder.audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && currentlyPlayingPosition == position && mediaPlayer != null) {
                        mediaPlayer?.seekTo(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

        } else {
            holder.layoutAudioPlayer.visibility = View.GONE
        }
    }

    private fun playNewAudio(position: Int, filePath: String, holder: CallViewHolder) {
        stopAudio()
        try {
            val player = MediaPlayer()
            player.setDataSource(filePath)
            player.prepare()
            player.start()
            
            mediaPlayer = player
            currentlyPlayingPosition = position
            
            holder.audioSeekBar.max = player.duration
            holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            startSeekBarUpdate(holder.audioSeekBar)
        } catch (e: Exception) {
            e.printStackTrace()
            currentlyPlayingPosition = -1
            mediaPlayer?.release()
            mediaPlayer = null
        }
        notifyDataSetChanged()
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        stopSeekBarUpdate()
    }

    private fun resumeAudio() {
        mediaPlayer?.start()
    }

    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        stopSeekBarUpdate()
        val oldPos = currentlyPlayingPosition
        currentlyPlayingPosition = -1
        if (oldPos != -1) notifyItemChanged(oldPos)
    }

    private fun startSeekBarUpdate(seekBar: SeekBar) {
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            seekBar.progress = it.currentPosition
                            handler.postDelayed(this, 100)
                        } else {
                            // Finished playing
                            seekBar.progress = seekBar.max
                            val previousPos = currentlyPlayingPosition
                            currentlyPlayingPosition = -1
                            stopAudio()
                            if (previousPos != -1) notifyItemChanged(previousPos)
                        }
                    }
                } catch (e: Exception) {
                    stopAudio()
                }
            }
        }
        handler.post(updateSeekBarRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarRunnable?.let { handler.removeCallbacks(it) }
    }
}
