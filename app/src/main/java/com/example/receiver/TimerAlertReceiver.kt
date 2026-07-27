package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.FocusTimerManager
import com.example.util.AlarmScheduler

class TimerAlertReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "timer_alert_channel"
    private val CHANNEL_NAME = "Timer Completed Alert"

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("master_silent_mode", false) || 
            (prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false))) {
            Log.d("TimerAlertReceiver", "Silent/Sleep mode is active and app is closed. Suppressing timer alarm alert.")
            return
        }
        val action = intent.action
        Log.d("TimerAlertReceiver", "Timer end alarm received with action: $action")

        if (action == "DISMISS_NOTIFICATION") {
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", 1003)
            com.example.util.UrgentNotificationHelper.cancelNotification(context, notificationId)
            return
        }

        if (action == "com.example.action.TIMER_FINISHED") {
            val isFocusPhase = intent.getBooleanExtra("is_focus_phase", true)

            // SAFETY CHECK: Suppress stale Timer alarm if user is actively in Stopwatch focus mode
            if (isFocusPhase && FocusTimerManager.isStopwatchActive.value) {
                Log.w("TimerAlertReceiver", "Suppressed timer finished alarm: Stopwatch is currently active.")
                AlarmScheduler.cancelTimerEndAlarm(context)
                return
            }

            val isLeader = kotlinx.coroutines.runBlocking {
                try {
                    val db = com.example.data.AppDatabase.getInstance(context.applicationContext)
                    val session = db.localActiveSessionDao().getActiveSession()
                    session == null || session.is_current_leader == 1
                } catch (e: Exception) {
                    true
                }
            }

            if (isLeader) {
                // 1. Play the strong bell sound with vibration
                try {
                    FocusTimerManager.playStrongBellSoundWithVibration(context)
                } catch (e: Exception) {
                    Log.e("TimerAlertReceiver", "Failed to play bell sound: ${e.message}", e)
                }
            } else {
                Log.d("TimerAlertReceiver", "Suppressed hardware speaker/bell audio: current device is not the leader.")
            }

            // 2. Show a notification to alert the user
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (isLeader) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    importance
                ).apply {
                    description = "Urgent alerts when focus or break sessions finish"
                    enableLights(true)
                    enableVibration(isLeader)
                    setBypassDnd(isLeader)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Open application on click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                AlarmScheduler.TIMER_ALARM_REQUEST_CODE,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationTitle = if (isFocusPhase) {
                "LifeOS: Pomodoro Focus Timer Ended! 🎯"
            } else {
                "LifeOS: Break Timer Ended! ☕"
            }

            val notificationText = if (isFocusPhase) {
                "Your Pomodoro focus session has completed. Time to rest!"
            } else {
                "Your break has ended. Let's get back to studying!"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)

            if (isLeader) {
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVibrate(longArrayOf(0, 500, 200, 500))
            } else {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            }

            notificationManager.notify(AlarmScheduler.TIMER_ALARM_REQUEST_CODE, builder.build())
        }
    }
}
