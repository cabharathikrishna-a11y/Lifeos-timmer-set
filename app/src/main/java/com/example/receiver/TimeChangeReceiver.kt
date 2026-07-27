package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.FocusTimerManager

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("TimeChangeReceiver", "Received broadcast: $action")
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            Log.i("TimeChangeReceiver", "System clock jump detected! Rescheduling exact alarms against absolute NTP time.")
            try {
                FocusTimerManager.recoverAndResumeActiveSession(context.applicationContext)
            } catch (e: Exception) {
                Log.e("TimeChangeReceiver", "Failed to recover active session: ${e.message}", e)
            }
        }
    }
}
