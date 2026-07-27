package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.ui.AppViewModel
import com.example.util.FocusTimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FocusForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "FocusForegroundService"
        private const val CHANNEL_ID = "FocusSessionChannel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"

        @Volatile
        private var instance: FocusForegroundService? = null

        fun updateProgress(taskTitle: String, contentText: String, isRunning: Boolean) {
            val service = instance ?: return
            val notification = service.buildNotification(taskTitle, contentText, isRunning)
            val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun startForegroundSafe(notificationId: Int, notification: Notification) {
        val typeSpecialUse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val typeDataSync = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                if (Build.VERSION.SDK_INT >= 34) typeSpecialUse else 0
            )
        } catch (e: Exception) {
            Log.e("FocusForegroundService", "Failed to start FGS with type specialUse, falling back to dataSync...", e)
            try {
                ServiceCompat.startForeground(
                    this,
                    notificationId,
                    notification,
                    if (Build.VERSION.SDK_INT >= 34) typeDataSync else 0
                )
            } catch (e2: Exception) {
                Log.e("FocusForegroundService", "Failed to start FGS with type dataSync, falling back to generic...", e2)
                try {
                    ServiceCompat.startForeground(this, notificationId, notification, 0)
                } catch (e3: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e3 is android.app.ForegroundServiceStartNotAllowedException) {
                        Log.w("FocusForegroundService", "Foreground service start not allowed from background", e3)
                    } else {
                        Log.e("FocusForegroundService", "Failed to start foreground service", e3)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_PAUSE || action == ACTION_RESUME) {
            handleNotificationAction(action)
            return START_STICKY
        }

        startForegroundSafe(NOTIFICATION_ID, buildNotification("Focus Session", "Initializing...", false))

        serviceScope.launch {
            combine(
                FocusTimerManager.isTimerRunning,
                FocusTimerManager.isStopwatchActive,
                FocusTimerManager.timerSecondsLeft,
                FocusTimerManager.stopwatchSeconds,
                FocusTimerManager.isPaused
            ) { isTimerRunning, isStopwatchActive, timerSecondsLeft, stopwatchSeconds, isPaused ->
                val isFocusPhase = FocusTimerManager.isFocusPhase.value
                val seconds = if (isStopwatchActive) stopwatchSeconds else timerSecondsLeft
                val formattedTime = formatTime(seconds)
                
                val title = if (!isFocusPhase) {
                    "Break Session Active ☕"
                } else if (isStopwatchActive) {
                    "Stopwatch Study Running ⏱️"
                } else {
                    "Pomodoro Focus Session Running 🎯"
                }
                
                val taskTitle = FocusTimerManager.attachedTask.value?.title
                val subText = if (taskTitle != null && isFocusPhase) " - Task: $taskTitle" else ""
                val statusText = if (isPaused) "Paused - $formattedTime$subText" else "Ticking - $formattedTime$subText"
                
                Triple(title, statusText, isPaused)
            }.collect { (title, statusText, isPaused) ->
                val notification = buildNotification(title, statusText, !isPaused)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }

        return START_STICKY
    }

    private fun handleNotificationAction(action: String) {
        val mode = com.example.api.DynamicCommandManager.currentTimerModeFlow.value
        val model = AppViewModel.instance
        Log.d(TAG, "Notification action received: $action, Mode: $mode, ViewModel ready: ${model != null}")
        
        if (model != null) {
            if (action == ACTION_PAUSE) {
                if (mode == "stopwatch") model.pauseStopwatch() else model.pauseTimer()
            } else if (action == ACTION_RESUME) {
                if (mode == "stopwatch") model.startStopwatch(isResuming = true) else model.startTimer(isResuming = true)
            }
        } else {
            if (action == ACTION_PAUSE) {
                if (mode == "stopwatch") {
                    FocusTimerManager.pauseStopwatch(applicationContext)
                } else {
                    FocusTimerManager.pauseTimer(applicationContext)
                }
            } else if (action == ACTION_RESUME) {
                if (mode == "stopwatch") {
                    FocusTimerManager.startStopwatch(applicationContext, isResuming = true)
                } else {
                    FocusTimerManager.startTimer(applicationContext, isResuming = true)
                }
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    private fun buildNotification(taskTitle: String, contentText: String, isCurrentlyRunning: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, FocusForegroundService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, FocusForegroundService::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getService(
            this, 2, resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(taskTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)
            .setOnlyAlertOnce(true)

        if (isCurrentlyRunning) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Focus Session",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps long study sessions running unkilled in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timed out (fgsType: $fgsType, startId: $startId). Stopping self to prevent crash.")
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground in onDestroy: ${e.message}")
        }
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
