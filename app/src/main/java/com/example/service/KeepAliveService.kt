package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.util.FocusTimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.example.ui.FocusRecord
import com.example.api.BellSignal
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

class KeepAliveService : Service() {

    private val serviceJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("KeepAliveService", "Uncaught exception in background service scope: ${exception.message}", exception)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)
    private var combinedMonitoringJob: Job? = null
    private var friendsFocusJob: Job? = null
    private var waterReminderJob: Job? = null
    private var databaseAlignmentCheckJob: Job? = null
    private var lastKnownForegroundPackage: String? = null
    private var wasUsingInstaOnBreak = false
    private val lastFocusStatusMap = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val latestFetchedPeerStates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val debounceJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var usersValueEventListener: ValueEventListener? = null
    private var usersDatabaseReference: DatabaseReference? = null
    private var bellsValueEventListener: ValueEventListener? = null
    private var bellsDatabaseReference: DatabaseReference? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LifeOS::KeepAliveWakeLock")?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let { lock ->
            if (!lock.isHeld) {
                try {
                    // Specify a maximum safety ceiling of 4 hours to prevent permanent zombie locks
                    lock.acquire(4 * 60 * 60 * 1000L)
                    Log.d("KeepAliveService", "WakeLock acquired with 4-hour safety timeout")
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Failed to acquire WakeLock", e)
                }
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d("KeepAliveService", "WakeLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to release WakeLock safely", e)
        } finally {
            wakeLock = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // 1. INSTANTLY satisfy the Android OS requirement
        createNotificationChannel()
        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Initializing background daemon...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
            
        try {
            startForegroundSafe(NOTIFICATION_ID, initialNotification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to start service in foreground in onCreate: ${e.message}", e)
        }

        // Dynamically manage WakeLock: only hold it when a timer or stopwatch is actively running
        serviceScope.launch {
            com.example.util.FocusTimerManager.isTimerRunning.collect { isTimerRunning ->
                val isStopwatchRunning = com.example.util.FocusTimerManager.isStopwatchActive.value
                if (isTimerRunning || isStopwatchRunning) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }
        serviceScope.launch {
            com.example.util.FocusTimerManager.isStopwatchActive.collect { isStopwatchRunning ->
                val isTimerRunning = com.example.util.FocusTimerManager.isTimerRunning.value
                if (isTimerRunning || isStopwatchRunning) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }

        // Dynamically update notification to keep it perfectly in sync with the live timer
        serviceScope.launch {
            combine(
                com.example.util.FocusTimerManager.isTimerRunning,
                com.example.util.FocusTimerManager.isStopwatchActive,
                com.example.util.FocusTimerManager.isPaused,
                com.example.util.FocusTimerManager.isTabFocusTimerSelected,
                com.example.util.FocusTimerManager.isFocusPhase,
                com.example.util.FocusTimerManager.attachedTask
            ) { _ ->
                Unit
            }.collect {
                updateNotificationDirectly()
                com.example.widget.WidgetUpdater.updateAllWidgets(applicationContext)
            }
        }

        // Live sync Friends Focus Widget when peer statuses change
        serviceScope.launch {
            com.example.api.PeerLiveSphereManager.peerLiveStates.collect {
                com.example.widget.WidgetUpdater.updateFriendsFocusWidget(applicationContext)
            }
        }

        // 2. NOW it is safe to do heavy initialization
        com.example.util.StableTime.init()
        com.example.api.Firebase.appContext = applicationContext
        com.example.util.FocusTimerManager.init(applicationContext)
        com.example.util.AppBlockHelper.initializeStrictAppsIfNeeded(applicationContext)
        
        startCombinedMonitoring()
        startFriendsFocusMonitoring()
        startWaterReminderMonitoring()
        startHourlyDatabaseAlignmentCheck()
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
            Log.e("KeepAliveService", "Failed to start FGS with type specialUse, falling back to dataSync...", e)
            try {
                ServiceCompat.startForeground(
                    this,
                    notificationId,
                    notification,
                    if (Build.VERSION.SDK_INT >= 34) typeDataSync else 0
                )
            } catch (e2: Exception) {
                Log.e("KeepAliveService", "Failed to start FGS with type dataSync, falling back to generic...", e2)
                try {
                    ServiceCompat.startForeground(this, notificationId, notification, 0)
                } catch (e3: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e3 is android.app.ForegroundServiceStartNotAllowedException) {
                        Log.w("KeepAliveService", "Foreground service start not allowed from background", e3)
                    } else {
                        Log.e("KeepAliveService", "Failed to start foreground service", e3)
                    }
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Guarantee that startForeground is called instantly using a safe, up-to-date notification
            createNotificationChannel()

            val action = intent?.action
            Log.d("KeepAliveService", "KeepAliveService started with action: $action")
            
            when (action) {
                ACTION_PAUSE_TIMER -> {
                    FocusTimerManager.pauseTimer(this)
                }
                ACTION_RESUME_TIMER -> {
                    FocusTimerManager.startTimer(this, isResuming = true)
                }
                ACTION_RESET_TIMER -> {
                    FocusTimerManager.resetTimer(this)
                }
                ACTION_PAUSE_STOPWATCH -> {
                    FocusTimerManager.pauseStopwatch(this)
                }
                ACTION_RESUME_STOPWATCH -> {
                    FocusTimerManager.startStopwatch(this, isResuming = true)
                }
                ACTION_RESET_STOPWATCH -> {
                    FocusTimerManager.resetStopwatch(this)
                }
            }

            // Immediately build the actual up-to-date notification and set it as foreground
            val notification = buildKeepAliveNotification()
            startForegroundSafe(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error in onStartCommand: ${e.message}", e)
            try {
                val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("LifeOS Active System")
                    .setContentText("Ensuring scheduler accuracy")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .build()
                startForegroundSafe(NOTIFICATION_ID, fallbackNotification)
            } catch (inner: Exception) {
                Log.e("KeepAliveService", "Fallback startForeground failed: ${inner.message}", inner)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateNotificationDirectly() {
        try {
            val notification = buildKeepAliveNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to update notification directly: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LifeOS Core Daemon",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps LifeOS background app monitoring & focus timer active"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildFallbackNotification(): Notification {
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            9999,
            launchIntent,
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Ensuring accurate backgrounds & task scheduling")
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun getActionPendingIntent(action: String): android.app.PendingIntent {
        val intent = Intent(this, KeepAliveService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        return android.app.PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            flags
        )
    }

    private fun buildKeepAliveNotification(): Notification {
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            9999,
            launchIntent,
            flags
        )

        val isTimerOn = FocusTimerManager.isTimerRunning.value
        val hasProgress = FocusTimerManager.timerSecondsLeft.value < FocusTimerManager.timerDurationMinutes.value * 60
        val isPaused = !isTimerOn && hasProgress

        val isStopwatchOn = FocusTimerManager.isStopwatchActive.value
        val hasStopwatchProgress = FocusTimerManager.stopwatchSeconds.value > 0
        val isStopwatchPaused = !isStopwatchOn && hasStopwatchProgress

        val isTimerSelected = FocusTimerManager.isTabFocusTimerSelected.value

        if (isTimerSelected) {
            if (isTimerOn || isPaused) {
                val totalSecs = FocusTimerManager.timerSecondsLeft.value
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                val timeStr = if (hours > 0) {
                    String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format(java.util.Locale.US, "%02d:%02d", totalSecs / 60, secs)
                }
                val phase = if (FocusTimerManager.isFocusPhase.value) "FOCUSING 🎯" else "BREAK ☕"
                val taskName = FocusTimerManager.attachedTask.value?.title ?: "Focus Session"

                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)

                if (isTimerOn) {
                    builder.setContentTitle("Focus Timer ($phase)")
                    builder.setContentText("Active - $taskName")
                    builder.setWhen(System.currentTimeMillis() + (totalSecs * 1000L))
                    builder.setUsesChronometer(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        builder.setChronometerCountDown(true)
                    }
                } else {
                    builder.setContentTitle("Focus Timer: $timeStr ($phase)")
                    builder.setContentText(if (isPaused) "Paused - $taskName" else "Active - $taskName")
                    builder.setUsesChronometer(false)
                }

                if (isTimerOn) {
                    builder.addAction(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        getActionPendingIntent(ACTION_PAUSE_TIMER)
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "End",
                        getActionPendingIntent(ACTION_RESET_TIMER)
                    )
                } else {
                    builder.addAction(
                        android.R.drawable.ic_media_play,
                        "Resume",
                        getActionPendingIntent(ACTION_RESUME_TIMER)
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "End",
                        getActionPendingIntent(ACTION_RESET_TIMER)
                    )
                }

                return builder.build()
            }
        } else {
            if (isStopwatchOn || isStopwatchPaused) {
                val totalSecs = FocusTimerManager.stopwatchSeconds.value
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                val timeStr = if (hours > 0) {
                    String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
                }

                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)

                if (isStopwatchOn) {
                    builder.setContentTitle("Stopwatch (Focusing 🎯)")
                    builder.setContentText("Focus session in progress")
                    builder.setWhen(System.currentTimeMillis() - (totalSecs * 1000L))
                    builder.setUsesChronometer(true)
                } else {
                    builder.setContentTitle("Stopwatch: $timeStr")
                    builder.setContentText("Paused stopwatch")
                    builder.setUsesChronometer(false)
                }

                if (isStopwatchOn) {
                    builder.addAction(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        getActionPendingIntent(ACTION_PAUSE_STOPWATCH)
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "End",
                        getActionPendingIntent(ACTION_RESET_STOPWATCH)
                    )
                } else {
                    builder.addAction(
                        android.R.drawable.ic_media_play,
                        "Resume",
                        getActionPendingIntent(ACTION_RESUME_STOPWATCH)
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "End",
                        getActionPendingIntent(ACTION_RESET_STOPWATCH)
                    )
                }

                return builder.build()
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Ensuring accurate backgrounds & task scheduling")
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "Start Stopwatch",
                getActionPendingIntent(ACTION_RESUME_STOPWATCH)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "Start Timer",
                getActionPendingIntent(ACTION_RESUME_TIMER)
            )
            .build()
    }

    private fun startCombinedMonitoring() {
        if (combinedMonitoringJob != null) return
        combinedMonitoringJob = serviceScope.launch {
            var lastCheckTime = android.os.SystemClock.elapsedRealtime()
            var prevPackage: String? = null
            var lastWasFocusing = false
            while (true) {
                // Adaptive delay: 1 second if active timer or strict mode/monitored apps are present, 5 seconds if idle
                val isTimerActive = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value
                val isFocusPhase = FocusTimerManager.isFocusPhase.value
                val isCurrentlyFocusing = isTimerActive && isFocusPhase

                if (lastWasFocusing && !isCurrentlyFocusing) {
                    Log.d("KeepAliveService", "Focusing ended/paused/break. Releasing blocked notifications.")
                    com.example.util.AppBlockHelper.releaseBlockedNotifications(applicationContext)
                }
                lastWasFocusing = isCurrentlyFocusing

                val strictPrefs = getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
                val strictEnabled = strictPrefs.getBoolean("strict_mode_enabled", true)
                val monitoredAppsCount = com.example.util.AppBlockHelper.getBlockedApps(applicationContext).size

                val delayMs = if (isTimerActive || strictEnabled || monitoredAppsCount > 0) 1000L else 5000L
                delay(delayMs)

                val now = android.os.SystemClock.elapsedRealtime()
                val actualElapsedMs = now - lastCheckTime
                lastCheckTime = now

                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isScreenOn = powerManager?.isInteractive ?: true
                    if (!isScreenOn) continue

                    val foregroundPackage = getForegroundPackageName() ?: continue

                    // Detect if foreground app changed to clear active screen limit bypass session
                    if (prevPackage != null && foregroundPackage != prevPackage) {
                        val monitoredApps = com.example.util.AppBlockHelper.getBlockedApps(applicationContext)
                        if (monitoredApps.contains(prevPackage)) {
                            val isLimitOver = com.example.util.AppBlockHelper.isDailyLimitExceeded(applicationContext, prevPackage)
                            if (isLimitOver) {
                                com.example.util.AppBlockHelper.clearSessionForPackage(applicationContext, prevPackage)
                                Log.d("KeepAliveService", "Cleared session for $prevPackage because user left the app (switched to $foregroundPackage)")
                            }
                        }
                    }
                    prevPackage = foregroundPackage

                    if (foregroundPackage == packageName) continue

                    val isBreakActive = !FocusTimerManager.isFocusPhase.value

                    // --- 1. STRICT MODE MONITORING ---
                    if (isTimerActive) {
                        // Track if using Instagram or Snapchat during break timer
                        if (isBreakActive && (foregroundPackage == "com.instagram.android" || foregroundPackage == "com.snapchat.android")) {
                            wasUsingInstaOnBreak = true
                        }

                        // Close immediately if break ended
                        if (wasUsingInstaOnBreak && !isBreakActive) {
                            wasUsingInstaOnBreak = false
                            if (foregroundPackage == "com.instagram.android" || foregroundPackage == "com.snapchat.android") {
                                Log.d("KeepAliveService", "Instagram/Snapchat break over! Returning to Focus Timer.")
                                launch(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Break is over! Returning to Focus Timer... 🎯", Toast.LENGTH_LONG).show()
                                }
                                val launchIntent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("SHOW_FULL_SCREEN_TIMER", true)
                                }
                                startActivity(launchIntent)
                                continue
                            }
                        }
                    } else {
                        wasUsingInstaOnBreak = false
                    }

                    var intercepted = false
                    val focusGuardDisabled = true
                    if (!focusGuardDisabled && strictEnabled && !isBreakActive && isTimerActive) {
                        if (com.example.util.AppBlockHelper.isPackageBlockedInStrictMode(applicationContext, foregroundPackage)) {
                            Log.d("KeepAliveService", "Strict Mode Intercept triggered for package: $foregroundPackage")
                            
                            val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                putExtra("IS_STRICT_MODE_INTERCEPT", true)
                                putExtra("IS_LIMIT_BLOCK", false)
                            }
                            startActivity(launchIntent)
                            intercepted = true
                        }
                    }

                    // --- 2. SCREEN LIMITS & APP BLOCKS MONITORING ---
                    if (!focusGuardDisabled && !intercepted) {
                        val monitoredApps = com.example.util.AppBlockHelper.getBlockedApps(applicationContext)
                        
                        // Check if the foreground app is a user-launchable app on the device
                        val isLaunchableApp = try {
                            foregroundPackage != applicationContext.packageName && 
                            applicationContext.packageManager.getLaunchIntentForPackage(foregroundPackage) != null
                        } catch (e: Exception) {
                            false
                        }

                        if (isLaunchableApp) {
                            com.example.util.AppBlockHelper.checkAndResetDailyUsageIfNeeded(applicationContext)
                            
                            // 1. Increment active usage counter by actual elapsed seconds (e.g. 1s or 5s)
                            val incrementSecs = (actualElapsedMs / 1000).toInt()
                            if (incrementSecs > 0) {
                                com.example.util.AppBlockHelper.incrementDailyUsageSeconds(applicationContext, foregroundPackage, incrementSecs)
                            }
                        }

                        if (monitoredApps.contains(foregroundPackage)) {
                            val blockedApps = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                            val isAppInAllowList = !blockedApps.contains(foregroundPackage)
                            val areLimitsActive = !isTimerActive || !strictEnabled || isAppInAllowList

                            if (areLimitsActive) {
                                val hasSession = com.example.util.AppBlockHelper.isSessionActive(applicationContext, foregroundPackage)
                                val isLimitOver = com.example.util.AppBlockHelper.isDailyLimitExceeded(applicationContext, foregroundPackage)
                                val isLimitBypassed = com.example.util.AppBlockHelper.isDailyBypassed(applicationContext, foregroundPackage)
                                
                                if (isLimitOver && !isLimitBypassed) {
                                    if (!hasSession) {
                                        Log.d("KeepAliveService", "Daily limit exceeded for $foregroundPackage! Redirecting to block countdown...")
                                        val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            putExtra("IS_LIMIT_BLOCK", true)
                                            putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                        }
                                        startActivity(launchIntent)
                                    }
                                    continue
                                }

                                // 3. If limit not over, check temporary session
                                if (!hasSession) {
                                    Log.d("KeepAliveService", "No active temporary session for $foregroundPackage. Pointing to picker.")
                                    val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra("IS_LIMIT_BLOCK", false)
                                        putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                    }
                                    startActivity(launchIntent)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Failed to run combined monitoring: ${e.message}", e)
                }
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        
        // If we don't have a last known package, do a wider query to initialize it
        val queryWindowMs = if (lastKnownForegroundPackage == null) {
            30 * 60 * 1000L // 30 minutes
        } else {
            15 * 1000L // 15 seconds
        }
        
        val eventsStartTime = endTime - queryWindowMs
        val events = usageStatsManager.queryEvents(eventsStartTime, endTime)
        
        val event = android.app.usage.UsageEvents.Event()
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                lastKnownForegroundPackage = event.packageName
            }
        }
        
        // Fallback: If still null, query daily usage stats for the last 24 hours
        if (lastKnownForegroundPackage == null) {
            val statsStartTime = endTime - (24 * 3600 * 1000L)
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                statsStartTime,
                endTime
            )
            if (stats != null && stats.isNotEmpty()) {
                var lastUsage: android.app.usage.UsageStats? = null
                for (stat in stats) {
                    if (lastUsage == null || stat.lastTimeUsed > lastUsage.lastTimeUsed) {
                        lastUsage = stat
                    }
                }
                if (lastUsage != null && (endTime - lastUsage.lastTimeUsed) < 15000) {
                    lastKnownForegroundPackage = lastUsage.packageName
                }
            }
        }
        
        return lastKnownForegroundPackage
    }

    private fun startFriendsFocusMonitoring() {
        if (friendsFocusJob != null) return
        
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val currentUsername = prefs.getString("current_username", null)

        if (isLoggedIn && currentUsername != null) {
            try {
                // 1. Dynamic background polling of registered users bypassed since UserRemote was removed
                /*
                serviceScope.launch {
                    while (isActive) {
                        try {
                            val response = com.example.api.Firebase.api.getUsers()
                            if (response.isSuccessful) {
                                val allUsersMap = response.body() ?: emptyMap()
                                val realFriends = allUsersMap.keys.filter { username ->
                                    username != "admin" &&
                                    username != currentUsername &&
                                    username != "madhavan" &&
                                    username != "shalini" &&
                                    username != "subash" &&
                                    username != "maddy"
                                }
                                Log.d("KeepAliveService", "Dynamic friends list loaded: $realFriends")
                                com.example.api.Firebase.listenToFriends(applicationContext, realFriends)
                                com.example.api.Firebase.listenToFriendsFocusTimers(applicationContext, realFriends)
                            }
                        } catch (e: Exception) {
                            Log.e("KeepAliveService", "Failed to fetch dynamic friends list: ${e.message}", e)
                        }
                        delay(120000) // Refresh friends list every 2 minutes
                    }
                }
                */
                
                // 3. Friends flow observation removed safely
                
                // 4. Setup real-time listener and profile configurations update (bypassed in offline-first mode)
                com.example.api.Firebase.listenToMyFocusTimer(applicationContext, currentUsername)
                com.example.api.Firebase.listenToFirestoreCommands(applicationContext, currentUsername)

            } catch (e: Exception) {
                Log.e("KeepAliveService", "Error configuring Real-time Firebase listeners: ${e.message}", e)
            }
        }

        // Keep a non-aggressive background sync loop for inactivity notifications and database alignments
        friendsFocusJob = serviceScope.launch {
            while (true) {
                checkInactivityAndNotify()
                try {
                    com.example.util.FocusTimerManager.syncStateToFirebase(applicationContext)
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Background periodic syncStateToFirebase failed: ${e.message}", e)
                }
                delay(60000) // Poll sync every 60 seconds (no REST users querying!)
            }
        }
    }

    private fun checkInactivityAndNotify() {
        try {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            if (!isLoggedIn) return

            val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
            val now = System.currentTimeMillis()

            if (isLocalFocusing) {
                // User is focusing! Update last activity timestamp and reset notification flag
                prefs.edit()
                    .putLong("last_focus_activity_timestamp_ms", now)
                    .putBoolean("inactivity_6hr_notified", false)
                    .apply()
            } else if (com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
                // Sleep hours: continuously advance the last active timestamp so inactivity timer starts fresh when waking up
                prefs.edit()
                    .putLong("last_focus_activity_timestamp_ms", now)
                    .putBoolean("inactivity_6hr_notified", false)
                    .apply()
            } else {
                var lastTime = prefs.getLong("last_focus_activity_timestamp_ms", 0L)
                if (lastTime == 0L) {
                    // Initialize if never set
                    lastTime = now
                    prefs.edit().putLong("last_focus_activity_timestamp_ms", now).apply()
                }

                val elapsedMs = now - lastTime
                val sixHoursMs = 6 * 60 * 60 * 1000L // 6 hours straight
                
                if (elapsedMs >= sixHoursMs) {
                    val alreadyNotified = prefs.getBoolean("inactivity_6hr_notified", false)
                    if (!alreadyNotified) {
                        sendInactivityEncouragementNotification()
                        prefs.edit().putBoolean("inactivity_6hr_notified", true).apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error in checkInactivityAndNotify: ${e.message}", e)
        }
    }

    private fun sendInactivityEncouragementNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "inactivity_encouragement_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Study Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to start studying if you have been inactive"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 10010, launchIntent, flags)
        
        val messages = listOf(
            "It's been 6 hours since your last session. Let's start studying and make some progress! ��",
            "Ready to unlock your full potential? Click here to start your focus timer! 🎯",
            "A small step today leads to big achievements tomorrow. Let's do a quick focus session! 🚀",
            "Consistency is key! You haven't focused in a while. Let's study together now! 👨‍💻",
            "Your goals are waiting. Start a focus session now and cross off your tasks! 🏆"
        )
        val message = messages.random()
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Let's Start Studying! 📚")
            .setContentText(message)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
            
        notificationManager.notify(10010, notification)
    }

    private fun processFriendsFocusStateAndNotify(users: Map<String, Any>) {
        // Bypassed
    }


    private fun startWaterReminderMonitoring() {
        if (waterReminderJob != null) return
        waterReminderJob = serviceScope.launch {
            while (true) {
                delay(30000) // check every 30 seconds
                try {
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val enabled = prefs.getBoolean("water_reminder_enabled", false)
                    if (enabled) {
                        val intervalMins = prefs.getFloat("water_reminder_interval_mins", 60f)
                        val startTimeStr = prefs.getString("water_reminder_start_time", "08:00") ?: "08:00"
                        val endTimeStr = prefs.getString("water_reminder_end_time", "22:00") ?: "22:00"
                        
                        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                        
                        var isWithinWakeupWindow = false
                        if (startTimeStr <= endTimeStr) {
                            isWithinWakeupWindow = currentTime >= startTimeStr && currentTime <= endTimeStr
                        } else {
                            // Overnight window e.g. 22:00 to 08:00
                            isWithinWakeupWindow = currentTime >= startTimeStr || currentTime <= endTimeStr
                        }
                        
                        if (isWithinWakeupWindow) {
                            val lastMs = prefs.getLong("last_water_reminder_time_ms", 0L)
                            val currentMs = System.currentTimeMillis()
                            val intervalMs = (intervalMins * 60 * 1000L).toLong()
                            
                            if (lastMs == 0L) {
                                prefs.edit().putLong("last_water_reminder_time_ms", currentMs).apply()
                            } else if (currentMs - lastMs >= intervalMs) {
                                triggerWaterReminderNotification()
                                prefs.edit().putLong("last_water_reminder_time_ms", currentMs).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Failed to check water reminder: ${e.message}", e)
                }
            }
        }
    }

    private fun triggerWaterReminderNotification() {
        if (com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
            Log.i("KeepAliveService", "Muting water reminder during sleep hours.")
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "water_reminder_channel",
                "Water Drinking Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to stay hydrated"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 10002, launchIntent, flags)
        
        val notification = NotificationCompat.Builder(this, "water_reminder_channel")
            .setContentTitle("Time to drink water! 💧")
            .setContentText("Keep focused and hydrated! Drink a glass of water now.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
            
        manager.notify(10002, notification)
    }


    private fun startHourlyDatabaseAlignmentCheck() {
        if (databaseAlignmentCheckJob != null) return
        databaseAlignmentCheckJob = serviceScope.launch {
            while (true) {
                try {
                    com.example.util.FocusTimerManager.addSystemLog(
                        applicationContext,
                        "Periodic 30m State Sync Started",
                        "FIREBASE_SYNC",
                        "Initiating automated 1-hour local and online database integrity verification"
                    )
                    com.example.util.FocusTimerManager.syncStateToFirebase(applicationContext)
                    com.example.util.FocusTimerManager.performCloudAlignmentCheck(applicationContext)
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Hourly alignment check failed: ${e.message}", e)
                    com.example.util.FocusTimerManager.addSystemLog(
                        applicationContext,
                        "Periodic State Sync Error",
                        "FIREBASE_SYNC",
                        "Verification error: ${e.message}"
                    )
                }
                delay(1800000L) // 1 hour delay
            }
        }
    }

    private fun cleanName(rawName: String?): String {
        if (rawName == null) return "Friend"
        var clean = rawName
            .replace(Regex("""\[?photo:[^\]]+\]?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""photo:\S*""", RegexOption.IGNORE_CASE), "")
            .trim()
        
        val emojiRegex = Regex(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+|" +
            "[\\u2600-\\u27BF]+|" +
            "[\\uE000-\\uF8FF]+|" +
            "[\\uFE0F]+"
        )
        clean = emojiRegex.replace(clean, "").trim()
        
        if (clean.isBlank()) {
            return "Friend"
        }
        return clean
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("KeepAliveService", "Foreground service timed out (fgsType: $fgsType, startId: $startId). Stopping self to prevent crash.")
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("KeepAliveService", "KeepAliveService destroyed")
        try {
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
                Log.e("KeepAliveService", "Error stopping foreground/notification in onDestroy: ${e.message}")
            }
            try {
                com.example.util.FocusTimerManager.closeOverlay()
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to close overlay in onDestroy: ${e.message}")
            }
            try {
                com.example.api.Firebase.stopListening(applicationContext)
                com.example.api.Firebase.stopListeningToFirestoreCommands()
                usersValueEventListener?.let {
                    usersDatabaseReference?.removeEventListener(it)
                }
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Error removing Firebase listeners in onDestroy: ${e.message}")
            }
            serviceJob.cancel()
        } finally {
            releaseWakeLock()
            super.onDestroy()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isRunning = com.example.util.FocusTimerManager.isTimerRunning.value || com.example.util.FocusTimerManager.isStopwatchActive.value
        if (!isRunning) {
            Log.d("KeepAliveService", "onTaskRemoved - Timer idle, closing OSD")
            try {
                com.example.util.FocusTimerManager.closeOverlay()
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to close overlay in onTaskRemoved: ${e.message}")
            }
        } else {
            Log.d("KeepAliveService", "onTaskRemoved - Timer active! Keeping OSD and foreground service alive.")
        }
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val CHANNEL_ID = "lifeos_keepalive_service_channel"
        const val NOTIFICATION_ID = 10001

        const val ACTION_PAUSE_TIMER = "com.example.service.ACTION_PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.example.service.ACTION_RESUME_TIMER"
        const val ACTION_RESET_TIMER = "com.example.service.ACTION_RESET_TIMER"

        const val ACTION_PAUSE_STOPWATCH = "com.example.service.ACTION_PAUSE_STOPWATCH"
        const val ACTION_RESUME_STOPWATCH = "com.example.service.ACTION_RESUME_STOPWATCH"
        const val ACTION_RESET_STOPWATCH = "com.example.service.ACTION_RESET_STOPWATCH"
        
        fun start(context: Context) {
            try {
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("enable_background_activity", true)) {
                    Log.d("KeepAliveService", "Aborting service start - background activity disabled by user")
                    return
                }
                if (prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false)) {
                    Log.d("KeepAliveService", "Aborting service start - Background Services Silent Mode is active and app is closed")
                    return
                }
                val intent = Intent(context.applicationContext, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                Log.d("KeepAliveService", "KeepAliveService start command triggered successfully")
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to invoke startForegroundService: ${e.message}")
            }
        }

        fun updateNotification(context: Context) {
            try {
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("enable_background_activity", true)) {
                    return
                }
                if (prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false)) {
                    return
                }
                val isTimerOn = FocusTimerManager.isTimerRunning.value
                val isStopwatchOn = FocusTimerManager.isStopwatchActive.value
                if (!prefs.getBoolean("keep_notification_enabled", true) && !isTimerOn && !isStopwatchOn) {
                    return
                }

                val intent = Intent(context.applicationContext, KeepAliveService::class.java).apply {
                    action = "com.example.service.UPDATE_NOTIFICATION"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to update notification service: ${e.message}", e)
            }
        }
    }
}
