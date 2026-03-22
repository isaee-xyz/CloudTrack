package com.learn.cloudtrack.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.learn.cloudtrack.utils.Logger

class CallStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.extras?.getString(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (stateStr != null && stateStr != lastState) {
            lastState = stateStr
            Logger.log(context, TAG, "📡 State Changed: $stateStr | Target: $incomingNumber")

            val serviceIntent = Intent(context, AudioRecordingService::class.java)
            
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // DISABLED: We now rely purely on the Samsung dialer's automatic recordings.
                    // Starting a Microphone FGS from the background crashes on Android 14+.
                    /* 
                    serviceIntent.action = AudioRecordingService.ACTION_START_RECORDING
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    */
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Stop Recording Foreground Service
                    // DISABLED: Companion to the above fix.
                    /* 
                    serviceIntent.action = AudioRecordingService.ACTION_STOP_RECORDING
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    */

                    // Trigger CallLog observer to save metadata after short delay
                    // (Android takes a second to write to CallLog DB after disconnect)
                    CallLogObserver.scheduleCallLogSync(context)
                }
            }
        }
    }
}
