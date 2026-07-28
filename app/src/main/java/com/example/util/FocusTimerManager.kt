package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.room.withTransaction
import com.example.MainActivity
import com.example.api.Firebase
import com.example.api.OutboxDrainer
import com.example.data.*
import com.example.data.AppDatabase
import com.example.data.LocalHistoryVault
import com.example.data.OutboxQueue
import com.example.data.Task
import com.example.service.KeepAliveService
import com.example.ui.FocusRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun android.content.SharedPreferences.getSafeLong(key: String, defValue: Long): Long {
    return try {
        val value = this.all[key]
        if (value == null) {
            defValue
        } else {
            when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Float -> value.toLong()
                is Double -> value.toLong()
                is String -> value.toLongOrNull() ?: defValue
                else -> defValue
            }
        }
    } catch (e: Exception) {
        defValue
    }
}

object FocusTimerManager {
    private val firebaseSyncMutex = Mutex()
    private val logLock = Any()
    private val recordLock = Any()
    private val initLock = Any()
    private val ONE_HOUR_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(1)
    private val TWELVE_HOURS_SECONDS = java.util.concurrent.TimeUnit.HOURS.toSeconds(12).toInt()

    // System Audit Log definitions
    data class SystemLogEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val event: String,
        val category: String, // e.g. "BUTTON_PRESS", "AUTO_SAVE", "FIREBASE_SYNC", "STATE_RESTORE", "CALCULATION", "ALARM"
        val details: String
    )

    val systemLogs = MutableStateFlow<List<SystemLogEntry>>(emptyList())

    fun addSystemLog(context: Context?, event: String, category: String, details: String) {
        val log = SystemLogEntry(
            event = event,
            category = category,
            details = details
        )
        systemLogs.update { current ->
            val updated = current.toMutableList()
            updated.add(0, log)
            if (updated.size > 100) updated.take(100) else updated
        }

        context?.let { ctx ->
            scope.launch(Dispatchers.IO) {
                synchronized(logLock) {
                    try {
                        val prefs = ctx.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val serialized = systemLogs.value.joinToString("\n") { entry ->
                            val encodedEvent = android.util.Base64.encodeToString(entry.event.toByteArray(), android.util.Base64.NO_WRAP)
                            val encodedDetails = android.util.Base64.encodeToString(entry.details.toByteArray(), android.util.Base64.NO_WRAP)
                            "${entry.id}|${entry.timestamp}|${encodedEvent}|${entry.category}|${encodedDetails}"
                        }
                        prefs.edit().putString("system_logs_serialized2", serialized).apply()
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to save system logs", e)
                    }
                }
            }
        }
    }

    fun clearSystemLogs(context: Context) {
        systemLogs.value = emptyList()
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("system_logs_serialized2").apply()
    }

    fun loadSystemLogs(context: Context): List<SystemLogEntry> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("system_logs_serialized2", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val id = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                    val event = try {
                        String(android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Event]" }
                    val category = parts[3]
                    val details = try {
                        String(android.util.Base64.decode(parts[4], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Details]" }
                    SystemLogEntry(id, timestamp, event, category, details)
                } else null
            }
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Failed to load system logs", e)
            emptyList()
        }
    }

    fun getSanitizedEmail(context: Context): String? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null) ?: return null
        val email = prefs.getString("user_email_${currentUsername}", null)
        val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(appContext)
        val emailToUse = if (!email.isNullOrBlank()) email else googleAccount?.email
        if (emailToUse.isNullOrBlank()) {
            return com.example.api.DevicePresenceManager.sanitizeEmail(currentUsername)
        }
        return com.example.api.DevicePresenceManager.sanitizeEmail(emailToUse)
    }

    fun syncStateToFirebase(context: Context) {
        // Purged: Legacy continuous active-timer state sync to RTDB is completely removed.
    }

    fun appendTimelineEvent(context: Context, command: String) {
        val appContext = context.applicationContext
        val email = com.example.api.DynamicCommandManager.activeEmail
        
        // If we are online and this is one of the control events mapped by executeMidSessionCommand,
        // we skip appending here to prevent double logging (one small, one big).
        // Standard "END" is kept here since resetTimer does not call executeMidSessionCommand.
        if (email.isNotEmpty() && command != "END" && command != "session_end") {
            Log.d("FocusTimerManager", "Skipping appendTimelineEvent for $command because executeMidSessionCommand handles it.")
            return
        }

        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        
        val nowMs = System.currentTimeMillis()
        val timelineKey = "session_timeline_json"
        
        val currentArray = if (command == "START") {
            org.json.JSONArray()
        } else {
            val existing = prefs.getString(timelineKey, "[]") ?: "[]"
            try {
                org.json.JSONArray(existing)
            } catch (e: Exception) {
                org.json.JSONArray()
            }
        }
        
        try {
            val eventObj = org.json.JSONObject().apply {
                put("command", command)
                put("timestamp", nowMs)
            }
            currentArray.put(eventObj)
            val updatedJson = currentArray.toString()
            prefs.edit().putString(timelineKey, updatedJson).apply()

            // Keep DynamicCommandManager.currentTimelineFlow in perfect sync
            val list = if (command == "START") {
                mutableListOf()
            } else {
                com.example.api.DynamicCommandManager.currentTimelineFlow.value.toMutableList()
            }
            list.add(com.example.api.TimelineEvent(deviceId = android.os.Build.MODEL, event = command, timestamp = nowMs))
            com.example.api.DynamicCommandManager.currentTimelineFlow.value = list
            
            Log.d("FocusTimerManager", "Appended timeline event: $command at $nowMs. Total events: ${currentArray.length()}")
            
            // Sync to legacy focusTimer/timelineJson is removed permanently.
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Error appending timeline event: $command", e)
        }
    }

    fun reportActionToFirebase(context: Context, buttonName: String) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("current_username", null) ?: return
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        if (!isLoggedIn || isAdmin) return

        // Writing device starting a focus session: publish settings if correct before writing focus timer action
        if (buttonName == "start_timer" || buttonName == "start_stopwatch") {
            Log.d("FocusTimerManager", "Writing device starting a focus session ($buttonName). Publishing settings config if correct.")
            com.example.api.Firebase.publishTimerConfigAndSignal(context.applicationContext, username)
        }

        val isCommandDevice = prefs.getBoolean("is_command_device", true)
        Log.d("FocusTimerManager", "Button click detected. Current sync mode: isCommandDevice=$isCommandDevice")

        val ts = com.example.util.StableTime.currentTimeMillis()
        prefs.edit().putLong("last_processed_button_clicked_timestamp", ts).apply()
    }

    fun performCloudAlignmentCheck(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        
        if (!isLoggedIn || isAdmin || currentUsername == null) {
            addSystemLog(context, "Alignment Check Skipped", "FIREBASE_SYNC", "User is not logged in or is admin. Check skipped.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                addSystemLog(context, "Alignment Querying", "FIREBASE_SYNC", "Triggering secure 3-phase alignment via Firestore...")
                com.example.util.FocusReconciliationEngine.runReconciliation(context, currentUsername)
                addSystemLog(context, "Alignment Confirmed", "FIREBASE_SYNC", "Firestore-based 3-phase alignment executed successfully.")
            } catch (e: Exception) {
                addSystemLog(context, "Alignment Query Error", "FIREBASE_SYNC", "Error running Firestore-based alignment: ${e.message}")
            }
        }
    }

    // High-precision tracking variable for Doze-mode immune relative elapsed time
    private var lastResumeElapsedRealtime: Long? = null

    // Unbreakable anchors for hardware uptime, matching the requested new FocusTimerManager format
    var activeSessionStartRealtimeMs: Long
        get() = lastResumeElapsedRealtime ?: 0L
        set(value) {
            lastResumeElapsedRealtime = if (value == 0L) null else value
        }

    var baseAccumulatedSeconds: Int
        get() = (_accumulatedSessionTimeMs.value / 1000).toInt()
        set(value) {
            _accumulatedSessionTimeMs.value = value * 1000L
        }

    private var uiTickJob: android.os.AsyncTask<Void, Void, Void>? = null // Dummy or keep Job
    private var actualUiTickJob: kotlinx.coroutines.Job? = null
    private val stateMutex = kotlinx.coroutines.sync.Mutex()

    // Current Active States (Encapsulated backing fields)
    private val _accumulatedSessionTimeMs = MutableStateFlow(0L)
    val accumulatedSessionTimeMs: StateFlow<Long> = _accumulatedSessionTimeMs.asStateFlow()

    private val _lastResumeTimeMs = MutableStateFlow<Long?>(null)
    val lastResumeTimeMs: StateFlow<Long?> = _lastResumeTimeMs.asStateFlow()

    private val _timerSecondsLeft = MutableStateFlow(25 * 60)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft.asStateFlow()

    private val _timerDurationMinutes = MutableStateFlow(25)
    val timerDurationMinutes: StateFlow<Int> = _timerDurationMinutes.asStateFlow()
    
    private val _pendingFocusReview = MutableStateFlow<FocusRecord?>(null)
    val pendingFocusReview: StateFlow<FocusRecord?> = _pendingFocusReview.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    val currentStatus: String
        get() = if ((_isTimerRunning.value && _isFocusPhase.value) || _isStopwatchActive.value) "FOCUSING" else "IDLE"

    private val _isFocusPhase = MutableStateFlow(true)
    val isFocusPhase: StateFlow<Boolean> = _isFocusPhase.asStateFlow()

    private val _attachedTask = MutableStateFlow<Task?>(null)
    val attachedTask: StateFlow<Task?> = _attachedTask.asStateFlow()

    private val _attachedTag = MutableStateFlow<String>("")
    val attachedTag: StateFlow<String> = _attachedTag.asStateFlow()

    private val _focusTags = MutableStateFlow<List<String>>(emptyList())
    val focusTags: StateFlow<List<String>> = _focusTags.asStateFlow()

    private val _cumulativeSessionFocusSeconds = MutableStateFlow(0)
    val cumulativeSessionFocusSeconds: StateFlow<Int> = _cumulativeSessionFocusSeconds.asStateFlow()

    // Global verification/completion dialog states
    private val _showGlobalVerificationDialog = MutableStateFlow(false)
    val showGlobalVerificationDialog: StateFlow<Boolean> = _showGlobalVerificationDialog.asStateFlow()

    private val _globalVerificationFocusedTimeSeconds = MutableStateFlow(0)
    val globalVerificationFocusedTimeSeconds: StateFlow<Int> = _globalVerificationFocusedTimeSeconds.asStateFlow()

    private val _globalVerificationRevisedTotalMinutes = MutableStateFlow(0)
    val globalVerificationRevisedTotalMinutes: StateFlow<Int> = _globalVerificationRevisedTotalMinutes.asStateFlow()

    private val _globalVerificationRevisedTotalSeconds = MutableStateFlow(0)
    val globalVerificationRevisedTotalSeconds: StateFlow<Int> = _globalVerificationRevisedTotalSeconds.asStateFlow()

    // Session Verification & Break tracking variables
    private val _currentSessionStartMs = MutableStateFlow<Long?>(null)
    val currentSessionStartMs: StateFlow<Long?> = _currentSessionStartMs.asStateFlow()

    private val _currentSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    val currentSessionPauseRanges: StateFlow<List<Pair<Long, Long>>> = _currentSessionPauseRanges.asStateFlow()

    var tempPauseStartMs: Long? = null

    private val _verifiedSessionStartMs = MutableStateFlow<Long?>(null)
    val verifiedSessionStartMs: StateFlow<Long?> = _verifiedSessionStartMs.asStateFlow()

    private val _verifiedSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    val verifiedSessionPauseRanges: StateFlow<List<Pair<Long, Long>>> = _verifiedSessionPauseRanges.asStateFlow()

    fun recordSessionStart() {
        if (_currentSessionStartMs.value == null) {
            _currentSessionStartMs.value = StableTime.currentTimeMillis()
        }
        if (tempPauseStartMs != null) {
            val pauseStart = tempPauseStartMs!!
            val pauseEnd = StableTime.currentTimeMillis()
            _currentSessionPauseRanges.value = _currentSessionPauseRanges.value + Pair(pauseStart, pauseEnd)
            tempPauseStartMs = null
        }
    }

    fun recordSessionPause() {
        if (tempPauseStartMs == null) {
            tempPauseStartMs = StableTime.currentTimeMillis()
        }
    }

    fun recordSessionCompleteOrReset(isSaving: Boolean) {
        if (isSaving) {
            _verifiedSessionStartMs.value = _currentSessionStartMs.value
            if (tempPauseStartMs != null) {
                val finalPauseRange = Pair(tempPauseStartMs!!, StableTime.currentTimeMillis())
                _verifiedSessionPauseRanges.value = _currentSessionPauseRanges.value + finalPauseRange
            } else {
                _verifiedSessionPauseRanges.value = _currentSessionPauseRanges.value
            }
        }
        // Always reset current session tracking after transferring (or if not saving)
        _currentSessionStartMs.value = null
        _currentSessionPauseRanges.value = emptyList()
        tempPauseStartMs = null
    }

    // Stopwatch Active States (Encapsulated)
    private val _lastLocalInteractionTimestamp = MutableStateFlow(0L)
    val lastLocalInteractionTimestamp: StateFlow<Long> = _lastLocalInteractionTimestamp.asStateFlow()

    fun updateLocalInteractionTimestamp() {
        _lastLocalInteractionTimestamp.value = StableTime.currentTimeMillis()
    }

    private val _stopwatchSeconds = MutableStateFlow(0)
    val stopwatchSeconds: StateFlow<Int> = _stopwatchSeconds.asStateFlow()

    private val _isStopwatchActive = MutableStateFlow(false)
    val isStopwatchActive: StateFlow<Boolean> = _isStopwatchActive.asStateFlow()

    private val _stopwatchLimitReached = MutableStateFlow(false)
    val stopwatchLimitReached: StateFlow<Boolean> = _stopwatchLimitReached.asStateFlow()

    private val _isTabFocusTimerSelected = MutableStateFlow(false)
    val isTabFocusTimerSelected: StateFlow<Boolean> = _isTabFocusTimerSelected.asStateFlow()

    private val _stopwatchBreakDurationMinutes = MutableStateFlow(5)
    val stopwatchBreakDurationMinutes: StateFlow<Int> = _stopwatchBreakDurationMinutes.asStateFlow()

    private val _autoStartStopwatchAfterBreak = MutableStateFlow(true)
    val autoStartStopwatchAfterBreak: StateFlow<Boolean> = _autoStartStopwatchAfterBreak.asStateFlow()

    private val _wasStartedFromStopwatch = MutableStateFlow(false)
    val wasStartedFromStopwatch: StateFlow<Boolean> = _wasStartedFromStopwatch.asStateFlow()

    // Tracks if the user explicitly clicked "Pause"
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // CRITICAL: Preserves stopwatch count-up time when switching to break mode
    private var savedStopwatchSeconds: Int = 0

    // User Stats States (Encapsulated)
    private val _todayPomosCount = MutableStateFlow(0)
    val todayPomosCount: StateFlow<Int> = _todayPomosCount.asStateFlow()

    private val _totalFocusMinutes = MutableStateFlow(0)
    val totalFocusMinutes: StateFlow<Int> = _totalFocusMinutes.asStateFlow()

    private val _optimisticTodayFocusSeconds = MutableStateFlow<Long?>(null)
    val optimisticTodayFocusSeconds: StateFlow<Long?> = _optimisticTodayFocusSeconds.asStateFlow()

    private val _focusRecords = MutableStateFlow<List<FocusRecord>>(emptyList())
    val focusRecords: StateFlow<List<FocusRecord>> = _focusRecords.asStateFlow()

    private val _adoptedTodayMs = MutableStateFlow(0L)
    val adoptedTodayMs: StateFlow<Long> = _adoptedTodayMs.asStateFlow()

    fun setAdoptedTodayMs(value: Long) {
        _adoptedTodayMs.value = value
    }

    // Option toggles (Encapsulated)
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _isBellSilentModeEnabled = MutableStateFlow(false)
    val isBellSilentModeEnabled: StateFlow<Boolean> = _isBellSilentModeEnabled.asStateFlow()

    private val _autoStartBreak = MutableStateFlow(true)
    val autoStartBreak: StateFlow<Boolean> = _autoStartBreak.asStateFlow()

    private val _autoStartPomo = MutableStateFlow(true)
    val autoStartPomo: StateFlow<Boolean> = _autoStartPomo.asStateFlow()

    // Controlled Mutator Functions for Encapsulation
    fun setAccumulatedSessionTimeMs(value: Long) {
        _accumulatedSessionTimeMs.value = value
    }

    fun setLastResumeTimeMs(value: Long?) {
        _lastResumeTimeMs.value = value
        if (value != null) {
            val diff = StableTime.currentTimeMillis() - value
            lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime() - diff
        } else {
            lastResumeElapsedRealtime = null
        }
    }

    fun getCurrentChunkMs(): Long {
        return lastResumeElapsedRealtime?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
    }

    fun setTimerSecondsLeft(value: Int) {
        _timerSecondsLeft.value = value
    }

    fun setTimerDurationMinutes(value: Int) {
        _timerDurationMinutes.value = value
    }

    fun setPendingFocusReview(value: FocusRecord?) {
        _pendingFocusReview.value = value
    }

    fun setFocusPhase(value: Boolean) {
        _isFocusPhase.value = value
    }

    fun setAttachedTask(value: Task?) {
        _attachedTask.value = value
    }

    fun setAttachedTag(value: String) {
        _attachedTag.value = value
    }

    fun setFocusTags(value: List<String>) {
        _focusTags.value = value
    }

    fun setCumulativeSessionFocusSeconds(value: Int) {
        _cumulativeSessionFocusSeconds.value = value
    }

    fun setShowGlobalVerificationDialog(value: Boolean) {
        _showGlobalVerificationDialog.value = value
    }

    fun setGlobalVerificationFocusedTimeSeconds(value: Int) {
        _globalVerificationFocusedTimeSeconds.value = value
    }

    fun setGlobalVerificationRevisedTotalMinutes(value: Int) {
        _globalVerificationRevisedTotalMinutes.value = value
    }

    fun setGlobalVerificationRevisedTotalSeconds(value: Int) {
        _globalVerificationRevisedTotalSeconds.value = value
    }

    fun setCurrentSessionStartMs(value: Long?) {
        _currentSessionStartMs.value = value
    }

    fun setCurrentSessionPauseRanges(value: List<Pair<Long, Long>>) {
        _currentSessionPauseRanges.value = value
    }

    fun setVerifiedSessionStartMs(value: Long?) {
        _verifiedSessionStartMs.value = value
    }

    fun setVerifiedSessionPauseRanges(value: List<Pair<Long, Long>>) {
        _verifiedSessionPauseRanges.value = value
    }

    fun setStopwatchSeconds(value: Int) {
        _stopwatchSeconds.value = value
    }

    fun setStopwatchActive(value: Boolean) {
        _isStopwatchActive.value = value
    }

    fun setStopwatchLimitReached(value: Boolean) {
        _stopwatchLimitReached.value = value
    }

    fun setTabFocusTimerSelected(value: Boolean) {
        _isTabFocusTimerSelected.value = value
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("timer_is_tab_focus_selected", value).apply()
        }
    }

    fun setStopwatchBreakDurationMinutes(value: Int) {
        _stopwatchBreakDurationMinutes.value = value
    }

    fun setAutoStartStopwatchAfterBreak(value: Boolean) {
        _autoStartStopwatchAfterBreak.value = value
    }

    fun setWasStartedFromStopwatch(value: Boolean) {
        _wasStartedFromStopwatch.value = value
    }

    fun setTodayPomosCount(value: Int) {
        _todayPomosCount.value = value
    }

    fun setTotalFocusMinutes(value: Int) {
        _totalFocusMinutes.value = value
    }

    fun setOptimisticTodayFocusSeconds(value: Long?) {
        _optimisticTodayFocusSeconds.value = value
    }

    fun setFocusRecords(value: List<FocusRecord>) {
        val mergedRes = com.example.util.IntervalMerger.mergeOverlappingFocusRecords(value)
        _focusRecords.value = mergedRes.merged
    }

    fun setSoundEnabled(value: Boolean) {
        _soundEnabled.value = value
    }

    fun setIsBellSilentModeEnabled(value: Boolean) {
        _isBellSilentModeEnabled.value = value
    }

    fun setAutoStartBreak(value: Boolean) {
        _autoStartBreak.value = value
    }

    fun setAutoStartPomo(value: Boolean) {
        _autoStartPomo.value = value
    }

    // UI context flags
    var isTimerScreenActive = false
    var appIsBackgrounded = false

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("FocusTimerManager", "Uncaught exception in timer scope: ${exception.message}", exception)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    private var timerJob: Job? = null
    private var stopwatchJob: Job? = null
    private var alarmJob: Job? = null

    // Window overlay objects
    private var overlayView: View? = null
    private var tvTimerText: TextView? = null
    private var tvCollapsedArrow: TextView? = null
    private var tvEndBtn: TextView? = null
    private var tvPauseBtn: TextView? = null
    private var tvCloseBtn: TextView? = null
    private var tvExpandBtn: TextView? = null
    private var tvTopRow: View? = null
    private var tvBottomRow: View? = null
    private var windowManager: WindowManager? = null

    private var isOverlayCollapsed = false
    private var overlayCollapsedSide = "none" // "none", "left", "right"
    private var areOverlayControlsVisible = false
    private var overlayAutoHideJob: Job? = null
    private var lastOverlayX = 150
    private var lastOverlayY = 150

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null

    fun saveActiveSessionState(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_is_running", isTimerRunning.value)
            .putLong("accumulated_time_ms", accumulatedSessionTimeMs.value)
            .putLong("last_resume_time_ms", lastResumeTimeMs.value ?: -1L)
            .putLong("timer_session_start_ms", currentSessionStartMs.value ?: -1L)
            .putInt("timer_cumulative_seconds", cumulativeSessionFocusSeconds.value)
            .putBoolean("timer_is_focus_phase", isFocusPhase.value)
            .putBoolean("timer_is_stopwatch_active", isStopwatchActive.value)
            .putBoolean("timer_was_started_from_stopwatch", wasStartedFromStopwatch.value)
            .putInt("timer_attached_task_id", attachedTask.value?.id ?: -1)
            .putString("timer_attached_tag", attachedTag.value)
            .putLong("timer_last_active_timestamp", StableTime.currentTimeMillis())
            .putInt("timer_seconds_left", timerSecondsLeft.value)
            .putBoolean("timer_is_tab_focus_selected", isTabFocusTimerSelected.value)
            .putBoolean("is_paused", isPaused.value)
            .putInt("saved_stopwatch_seconds", savedStopwatchSeconds)
            .apply()
    }

    fun persistStateToDisk(context: Context) {
        saveActiveSessionState(context)
    }

    fun restoreStateFromDisk(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _isTimerRunning.value = prefs.getBoolean("timer_is_running", false)
        _isStopwatchActive.value = prefs.getBoolean("timer_is_stopwatch_active", false)
        lastResumeElapsedRealtime = prefs.getSafeLong("last_resume_time_ms", -1L).let { if (it == -1L) null else it }
        _accumulatedSessionTimeMs.value = prefs.getSafeLong("accumulated_time_ms", 0L)
        _isFocusPhase.value = prefs.getBoolean("timer_is_focus_phase", true)
        _isTabFocusTimerSelected.value = prefs.getBoolean("timer_is_tab_focus_selected", true)
        _isPaused.value = prefs.getBoolean("is_paused", false)
        savedStopwatchSeconds = prefs.getInt("saved_stopwatch_seconds", 0)
    }

    fun recoverAndResumeActiveSession(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val db = com.example.data.AppDatabase.getInstance(appContext)
                val session = db.localActiveSessionDao().getActiveSession()
                if (session != null && session.status != "IDLE") {
                    val isRunning = session.status == "FOCUSING"
                    val isStopwatch = session.mode == "STOPWATCH"
                    val isBreaking = session.status == "BREAKING"
                    
                    val isFocusPhaseFromTimeline = if (session.status == "PAUSED") {
                        prefs.getBoolean("timer_is_focus_phase", true)
                    } else {
                        session.status == "FOCUSING" || session.status == "IDLE"
                    }
                    
                    val editor = prefs.edit()
                    editor.putBoolean("timer_is_running", isRunning || isBreaking)
                    editor.putBoolean("timer_is_stopwatch_active", isStopwatch && isRunning)
                    editor.putBoolean("timer_was_started_from_stopwatch", isStopwatch)
                    editor.putBoolean("timer_is_focus_phase", isFocusPhaseFromTimeline)
                    editor.putBoolean("timer_is_tab_focus_selected", !isStopwatch)
                    editor.putLong("accumulated_time_ms", session.base_focus_time_ms)
                    editor.putInt("timer_cumulative_seconds", (session.base_focus_time_ms / 1000).toInt())
                    editor.putInt("saved_stopwatch_seconds", (session.base_focus_time_ms / 1000).toInt())
                    editor.putLong("last_resume_time_ms", if (isRunning) session.last_event_ts_ms else -1L)
                    editor.putString("timer_attached_tag", session.tag)
                    editor.putBoolean("is_paused", session.status == "PAUSED")
                    editor.apply()
                    Log.d("FocusTimerManager", "recoverAndResumeActiveSession: Synchronized SharedPreferences with local_active_session DB: id=${session.session_id}, status=${session.status}, baseFocusTimeMs=${session.base_focus_time_ms}")
                } else {
                    val editor = prefs.edit()
                    editor.putBoolean("timer_is_running", false)
                    editor.putBoolean("timer_is_stopwatch_active", false)
                    editor.putBoolean("timer_was_started_from_stopwatch", false)
                    editor.putLong("accumulated_time_ms", 0L)
                    editor.putLong("last_resume_time_ms", -1L)
                    editor.putLong("timer_session_start_ms", -1L)
                    editor.putInt("timer_cumulative_seconds", 0)
                    editor.putBoolean("is_paused", false)
                    editor.putInt("saved_stopwatch_seconds", 0)
                    editor.apply()
                }
            }
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Error synchronizing SharedPreferences with local_active_session in recoverAndResumeActiveSession", e)
        }
        
        val savedIsRunning = prefs.getBoolean("timer_is_running", false)
        val savedIsFocusPhase = prefs.getBoolean("timer_is_focus_phase", true)
        val savedIsStopwatchActive = prefs.getBoolean("timer_is_stopwatch_active", false)
        val savedWasStartedFromStopwatch = prefs.getBoolean("timer_was_started_from_stopwatch", false)
        val savedIsPaused = prefs.getBoolean("is_paused", false)
        _isPaused.value = savedIsPaused
        savedStopwatchSeconds = prefs.getInt("saved_stopwatch_seconds", 0)
        val savedAttachedTaskId = prefs.getInt("timer_attached_task_id", -1)
        _attachedTag.value = prefs.getString("timer_attached_tag", "") ?: ""
        val savedIsTabFocusTimerSelected = prefs.getBoolean("timer_is_tab_focus_selected", true)
        _isTabFocusTimerSelected.value = savedIsTabFocusTimerSelected
        
        val savedAccumulated = if (!savedIsRunning && !savedIsStopwatchActive && !savedIsPaused) 0L else prefs.getSafeLong("accumulated_time_ms", 0L)
        val savedLastResume = prefs.getSafeLong("last_resume_time_ms", -1L)
        val savedSessionStart = prefs.getSafeLong("timer_session_start_ms", -1L)
        val savedLastActiveTimestamp = prefs.getSafeLong("timer_last_active_timestamp", -1L)
        
        _accumulatedSessionTimeMs.value = savedAccumulated
        val restoredSecs = (savedAccumulated / 1000).toInt()
        if (restoredSecs > 0) {
            if (_cumulativeSessionFocusSeconds.value < restoredSecs) {
                _cumulativeSessionFocusSeconds.value = restoredSecs
            }
            if (_stopwatchSeconds.value < restoredSecs) {
                _stopwatchSeconds.value = restoredSecs
            }
        }
        setLastResumeTimeMs(if (savedLastResume != -1L) savedLastResume else null)
        _currentSessionStartMs.value = if (savedSessionStart != -1L) savedSessionStart else null
        
        _isFocusPhase.value = savedIsFocusPhase
        _wasStartedFromStopwatch.value = savedWasStartedFromStopwatch
        
        if (savedIsRunning) {
            if (savedIsFocusPhase) {
                // Pomodoro Focus Phase Recovery
                if (savedLastResume != -1L) {
                    val elapsedBackgroundMs = StableTime.currentTimeMillis() - savedLastResume
                    val totalElapsedMs = savedAccumulated + elapsedBackgroundMs
                    val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L
                    
                    if (totalElapsedMs >= totalDurationMs) {
                        // Completed in background
                        _accumulatedSessionTimeMs.value = totalDurationMs
                        _timerSecondsLeft.value = 0
                        _isTimerRunning.value = false
                        handlePhaseCompletion(appContext, completedFocusPhase = true)
                    } else {
                        // Still running
                        _accumulatedSessionTimeMs.value = totalElapsedMs
                        _timerSecondsLeft.value = ((totalDurationMs - totalElapsedMs) / 1000).toInt()
                        _isTimerRunning.value = false
                        // Set last resume to now so current chunk starts from 0 again
                        setLastResumeTimeMs(StableTime.currentTimeMillis())
                        startTimer(appContext, stopActiveAlarm = false)
                    }
                } else {
                    _isTimerRunning.value = false
                    startTimer(appContext, stopActiveAlarm = false)
                }
            } else {
                // Pomodoro Break Phase Recovery
                val savedSecondsLeft = prefs.getInt("timer_seconds_left", -1)
                val elapsedSeconds = if (savedLastActiveTimestamp != -1L) {
                    ((StableTime.currentTimeMillis() - savedLastActiveTimestamp) / 1000).toInt()
                } else {
                    0
                }
                val actualSecondsLeft = if (savedSecondsLeft != -1) {
                    maxOf(0, savedSecondsLeft - elapsedSeconds)
                } else {
                    val bMins = prefs.getInt("break_duration", 5)
                    maxOf(0, (bMins * 60) - elapsedSeconds)
                }
                
                if (actualSecondsLeft <= 0) {
                    _timerSecondsLeft.value = 0
                    _isTimerRunning.value = false
                    handlePhaseCompletion(appContext, completedFocusPhase = false)
                } else {
                    _timerSecondsLeft.value = actualSecondsLeft
                    _isTimerRunning.value = false
                    startTimer(appContext, stopActiveAlarm = false)
                }
            }
        } else {
            _isTimerRunning.value = false
            if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
                val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L
                _timerSecondsLeft.value = maxOf(0, ((totalDurationMs - _accumulatedSessionTimeMs.value) / 1000).toInt())
            } else if (!_isFocusPhase.value) {
                val savedSecondsLeft = prefs.getInt("timer_seconds_left", -1)
                if (savedSecondsLeft != -1) {
                    _timerSecondsLeft.value = savedSecondsLeft
                } else {
                    val bMins = prefs.getInt("break_duration", 5)
                    _timerSecondsLeft.value = bMins * 60
                }
            }
        }
        
        if (savedIsStopwatchActive) {
            if (savedLastResume != -1L) {
                val elapsedBackgroundMs = StableTime.currentTimeMillis() - savedLastResume
                _accumulatedSessionTimeMs.value = savedAccumulated + elapsedBackgroundMs
                _stopwatchSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
                _isStopwatchActive.value = false
                // Set last resume to now so current chunk starts from 0 again
                setLastResumeTimeMs(StableTime.currentTimeMillis())
                startStopwatch(appContext, stopActiveAlarm = false)
            } else {
                _isStopwatchActive.value = false
                startStopwatch(appContext, stopActiveAlarm = false)
            }
        } else {
            _isStopwatchActive.value = false
            _stopwatchSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
        }
        
        if (savedAttachedTaskId != -1) {
            scope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(appContext)
                    val task = db.taskDao().getTaskById(savedAttachedTaskId)
                    launch(Dispatchers.Main) {
                        _attachedTask.value = task
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            _attachedTask.value = null
        }
        
        addSystemLog(appContext, "Session State Recovered Dynamically", "STATE_RESTORE", "TimerRunning=$savedIsRunning, StopwatchActive=$savedIsStopwatchActive, AccumulatedTimeMs=${accumulatedSessionTimeMs.value}")
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun reloadTimerSettingsFromPrefs(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _timerDurationMinutes.value = prefs.getInt("timer_duration", 25)
        _soundEnabled.value = prefs.getBoolean("timer_sound_enabled", true)
        _isBellSilentModeEnabled.value = prefs.getBoolean("bell_silent_mode_enabled", false)
        _autoStartBreak.value = prefs.getBoolean("timer_autostart_break", prefs.getBoolean("auto_start_break_after_focus", true))
        _autoStartPomo.value = prefs.getBoolean("timer_autostart_pomo", prefs.getBoolean("auto_start_focus_after_break", true))
    }

    fun init(context: Context) {
        if (isInitialized) {
            if (appContext == null) appContext = context.applicationContext
            return
        }
        synchronized(initLock) {
            if (isInitialized) {
                if (appContext == null) appContext = context.applicationContext
                return
            }
            isInitialized = true
            appContext = context.applicationContext
            systemLogs.value = loadSystemLogs(context)
            addSystemLog(context, "System Core Initialized", "SYSTEM", "Loaded ${systemLogs.value.size} persisted audit logs")
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            _timerDurationMinutes.value = prefs.getInt("timer_duration", 25)
            
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val lastResetDate = prefs.getString("last_midnight_reset_date", "")
            if (lastResetDate != todayStr) {
                _todayPomosCount.value = 0
                prefs.edit()
                    .putInt("today_pomos_count", 0)
                    .putString("last_midnight_reset_date", todayStr)
                    .putBoolean("needs_firebase_midnight_reset", true)
                    .apply()
            } else {
                _todayPomosCount.value = prefs.getInt("today_pomos_count", 0)
            }
            _totalFocusMinutes.value = prefs.getInt("total_focus_minutes", 0)
            val sanitizedEmail = getSanitizedEmail(context)
            if (sanitizedEmail != null) {
                val savedAdoptedDate = prefs.getString("adopted_today_date_${sanitizedEmail}", "")
                if (savedAdoptedDate == todayStr) {
                    _adoptedTodayMs.value = prefs.getSafeLong("adopted_today_ms_${sanitizedEmail}", 0L)
                } else {
                    _adoptedTodayMs.value = 0L
                    prefs.edit()
                        .putLong("adopted_today_ms_${sanitizedEmail}", 0L)
                        .putString("adopted_today_date_${sanitizedEmail}", todayStr)
                        .apply()
                }
            }
            _focusRecords.value = loadFocusRecords(context)
            reloadFocusRecordsFromDb(context)
            _soundEnabled.value = prefs.getBoolean("timer_sound_enabled", true)
            _isBellSilentModeEnabled.value = prefs.getBoolean("bell_silent_mode_enabled", false)
            _autoStartBreak.value = prefs.getBoolean("timer_autostart_break", prefs.getBoolean("auto_start_break_after_focus", true))
            _autoStartPomo.value = prefs.getBoolean("timer_autostart_pomo", prefs.getBoolean("auto_start_focus_after_break", true))
            _stopwatchBreakDurationMinutes.value = prefs.getInt("stopwatch_break_duration", 5)
            _autoStartStopwatchAfterBreak.value = prefs.getBoolean("stopwatch_autostart_after_break", true)
            
            // Recover Active Session State Dynamically
            _focusTags.value = loadFocusTags(context)
            recoverAndResumeActiveSession(context)

        // Hourly Google Drive Sync Job
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    // Wait for 1 hour
                    delay(ONE_HOUR_MS)
                    
                    if (GoogleDriveSyncManager.hasDrivePermission(context)) {
                        Log.d("FocusTimerManager", "Starting hourly automatic Google Drive sync...")
                        val (success, msg) = GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Hourly Google Drive backup outcome: success=$success, msg=$msg")
                        addSystemLog(context, "Hourly Google Drive Sync", "AUTO_SAVE", "Outcome: success=$success, msg=$msg")
                    } else {
                        Log.d("FocusTimerManager", "Skipping hourly Google Drive sync: Permission not granted yet.")
                    }
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Error in hourly Google Drive sync job: ${e.message}", e)
                }
            }
        }
        startUiTickLoop()
    }
}

    @Volatile
    var isPassiveCalibrationInProgress: Boolean = false

    fun claimCommandDevice(context: Context) {
        if (isPassiveCalibrationInProgress) return
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null) ?: return
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        if (!isLoggedIn || isAdmin) return

        val isCommandDevice = prefs.getBoolean("is_command_device", true)
        Log.d("FocusTimerManager", "claimCommandDevice check: isCommandDevice=$isCommandDevice")
    }

    private fun startUiTickLoop() {
        // No-op to prevent low-precision ticking from fighting with high-precision timer/stopwatch jobs.
    }

    fun setStopwatchBreakDuration(context: Context, mins: Int) {
        init(context)
        _stopwatchBreakDurationMinutes.value = mins
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("stopwatch_break_duration", mins).apply()
    }

    fun setAutoStartStopwatchAfterBreak(context: Context, enabled: Boolean) {
        init(context)
        _autoStartStopwatchAfterBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("stopwatch_autostart_after_break", enabled).apply()
    }

    private suspend fun isCurrentDeviceLeader(context: Context): Boolean {
        return try {
            val db = com.example.data.AppDatabase.getInstance(context.applicationContext)
            val session = db.localActiveSessionDao().getActiveSession()
            if (session == null) {
                true
            } else {
                session.is_current_leader == 1
            }
        } catch (e: Exception) {
            true
        }
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
    }

    fun playStrongBellSoundWithVibration(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            if (!isCurrentDeviceLeader(context)) {
                Log.d("FocusTimerManager", "Timer zero reached! (Audio suppressed: Device is a silent follower).")
                return@launch
            }
            
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val isOnCall = try {
                telephonyManager != null && telephonyManager.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
            } catch (e: Exception) {
                false
            }

            val endTime = System.currentTimeMillis() + 10000L // 10 seconds duration
            
            // Check if any bluetooth devices are connected to keep volume nominal (safe)
            val isBT = isBluetoothAudioConnected(context)
            val streamType = if (isBT) android.media.AudioManager.STREAM_MUSIC else android.media.AudioManager.STREAM_ALARM
            val volume = if (isBT) 15 else 100 // Safe, gentle volume of 15 when bluetooth connected, else 100
            
            val tg = if (isOnCall) {
                Log.i("FocusTimerManager", "User is on an active call! Suppressing acoustic bell; using vibration only.")
                null
            } else {
                try {
                    android.media.ToneGenerator(streamType, volume)
                } catch (e: Exception) {
                    null
                }
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // Distinct, strong bell-like alarm tone
                        if (tg != null && !isOnCall) {
                            tg.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 600)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(500)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(1200L) // Wait a bit before repeating
                }
            } finally {
                tg?.release()
            }
        }
    }

    fun playFriendReminderBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            if (!isCurrentDeviceLeader(context)) {
                Log.d("FocusTimerManager", "Timer zero reached! (Audio suppressed: Device is a silent follower).")
                return@launch
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val isOnCall = try {
                telephonyManager != null && telephonyManager.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
            } catch (e: Exception) {
                false
            }

            val endTime = System.currentTimeMillis() + 5000L // 5 seconds duration
            
            val isBT = isBluetoothAudioConnected(context)
            val streamType = if (isBT) android.media.AudioManager.STREAM_MUSIC else android.media.AudioManager.STREAM_ALARM
            val volume = if (isBT) 15 else 100 // Safe, gentle volume of 15 when bluetooth connected, else 100
            
            val tg = if (isOnCall) {
                Log.i("FocusTimerManager", "User is on an active call! Suppressing friend reminder bell; using vibration only.")
                null
            } else {
                try {
                    android.media.ToneGenerator(streamType, volume)
                } catch (e: Exception) {
                    null
                }
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // Quick distinct ringing bell tone (TONE_CDMA_ALERT_CALL_GUARD or TONE_CDMA_HIGH_L)
                        if (tg != null && !isOnCall) {
                            tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(300)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(800L) // Wait a bit before repeating
                }
            } finally {
                tg?.release()
            }
        }
    }

    fun playStopwatchBreakEndBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            if (!isCurrentDeviceLeader(context)) {
                Log.d("FocusTimerManager", "Timer zero reached! (Audio suppressed: Device is a silent follower).")
                return@launch
            }
            val endTime = System.currentTimeMillis() + 3000L // 3 seconds duration
            
            val isBT = isBluetoothAudioConnected(context)
            val streamType = if (isBT) android.media.AudioManager.STREAM_MUSIC else android.media.AudioManager.STREAM_ALARM
            val volume = if (isBT) 15 else 100
            
            val tg = try {
                android.media.ToneGenerator(streamType, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        tg?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 500)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(400)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(1000L) // Repeat every 1 second for 3 seconds
                }
            } finally {
                tg?.release()
            }
        }
    }

    private fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (i in devices.indices) {
                    val device = devices[i]
                    val type = device.type
                    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        return true
                    }
                }
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openAppWithTimerPageInFront(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.MainActivity::class.java)
            intent.apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("SHOW_TIMER_PAGE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        _soundEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_sound_enabled", enabled).apply()
    }

    fun setBellSilentModeEnabled(context: Context, enabled: Boolean) {
        init(context)
        _isBellSilentModeEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bell_silent_mode_enabled", enabled).apply()
    }

    fun setAutoStartBreak(context: Context, enabled: Boolean) {
        _autoStartBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_autostart_break", enabled)
            .putBoolean("auto_start_break_after_focus", enabled)
            .apply()
    }

    fun setAutoStartPomo(context: Context, enabled: Boolean) {
        _autoStartPomo.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_autostart_pomo", enabled)
            .putBoolean("auto_start_focus_after_break", enabled)
            .apply()
    }

    fun setTimerDuration(context: Context, mins: Int) {
        init(context)
        _timerDurationMinutes.value = mins
        if (!_isTimerRunning.value) {
            _timerSecondsLeft.value = mins * 60
        }
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("timer_duration", mins)
            .putInt("pomodoro_focus_duration_mins", mins)
            .apply()
    }

    fun setBreakDuration(context: Context, mins: Int) {
        init(context)
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("break_duration", mins)
            .putInt("pomodoro_break_duration_mins", mins)
            .apply()
    }

    fun attachTaskToTimer(context: Context, task: Task?) {
        init(context)
        _attachedTask.value = task
    }

    fun startTimer(context: Context, stopActiveAlarm: Boolean = true, isResuming: Boolean = false) {
        init(context)
        claimCommandDevice(context)
        if (_isStopwatchActive.value) {
            pauseStopwatch(context)
        }
        stopwatchJob?.cancel()
        stopwatchJob = null
        _isStopwatchActive.value = false
        if (_isFocusPhase.value && !isResuming) {
            _wasStartedFromStopwatch.value = false
        }
        val actualResuming = isResuming || _isPaused.value || _accumulatedSessionTimeMs.value > 0L || (_isFocusPhase.value && _timerSecondsLeft.value < _timerDurationMinutes.value * 60)
        if (!actualResuming) {
            _isPaused.value = false
            _accumulatedSessionTimeMs.value = 0L
            _cumulativeSessionFocusSeconds.value = 0
            if (_isFocusPhase.value) {
                _timerSecondsLeft.value = _timerDurationMinutes.value * 60
            } else {
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val bMins = prefs.getInt("break_duration", 5)
                if (_timerSecondsLeft.value <= 0) {
                    _timerSecondsLeft.value = bMins * 60
                }
            }
            setLastResumeTimeMs(null)
            _currentSessionStartMs.value = StableTime.currentTimeMillis()
        } else {
            _isPaused.value = false
        }
        if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            setTabFocusTimerSelected(true)
        }
        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        if (_isTimerRunning.value) return
        val appContext = context.applicationContext

        if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            val completedTodaySecs = getTodayFocusSeconds()
            if (completedTodaySecs >= 72000) {
                Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Take a break.", Toast.LENGTH_LONG).show()
                return
            }

            // Only reset to full duration if we are NOT resuming from a pause
            if (!actualResuming || _timerSecondsLeft.value <= 0) {
                _timerSecondsLeft.value = _timerDurationMinutes.value * 60
                _accumulatedSessionTimeMs.value = 0L
                _cumulativeSessionFocusSeconds.value = 0
            }

            _isTimerRunning.value = true
            saveActiveSessionState(appContext)
            // --- POMODORO FOCUS MODE (Timestamp Engine) ---
            val isResumingSession = actualResuming || _accumulatedSessionTimeMs.value > 0L || (_timerSecondsLeft.value < _timerDurationMinutes.value * 60)
            val email = com.example.api.DynamicCommandManager.activeEmail
            if (email.isNotEmpty()) {
                val action = if (isResumingSession) "resumed" else "start"
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val task = _attachedTask.value?.title ?: "Focus Session"
                val tag = _attachedTag.value.ifEmpty { "Study" }
                com.example.api.DynamicCommandManager.executeMidSessionCommand(action, timeline, "pomodoro", task, tag)
            }
            if (isResumingSession) {
                com.example.util.FocusSessionDbHelper.handleResumeFocus(appContext)
            } else {
                com.example.util.FocusSessionDbHelper.handleStartFocus(
                    appContext,
                    tag = _attachedTag.value.ifEmpty { "Study" },
                    taskTitle = _attachedTask.value?.title ?: "General Focus",
                    mode = "POMODORO"
                )
            }
            if (_isFocusPhase.value) {
                appendTimelineEvent(appContext, if (isResumingSession) "RESUME" else "START")
            }
            recordSessionStart()
            addSystemLog(appContext, "Start Timer", "BUTTON_PRESS", "Duration=${_timerDurationMinutes.value}m")
            performCloudAlignmentCheck(appContext)
            runBackgroundAuditAndHealing(appContext)
            
            KeepAliveService.start(appContext)
            FocusDriftDetector.startMonitoring(appContext, com.example.api.DynamicCommandManager.activeEmail)

            _lastResumeTimeMs.value = StableTime.currentTimeMillis()
            lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime()
            saveActiveSessionState(appContext)
            reportActionToFirebase(appContext, "start_timer")

            val baseAccumulatedMs = _accumulatedSessionTimeMs.value
            timerJob?.cancel()
            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L

                while (_isTimerRunning.value && _isFocusPhase.value) {
                    delay(200) // UI refresh rate
                    val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                    val currentStatus = if (_isPaused.value) "Paused" else "Focusing"
                    val totalElapsedMs = if (timeline.isNotEmpty()) {
                        com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(timeline, currentStatus)
                    } else {
                        val currentChunkMs = getCurrentChunkMs()
                        baseAccumulatedMs + currentChunkMs
                    }
                    _accumulatedSessionTimeMs.value = totalElapsedMs
                    
                    val remainingMs = totalDurationMs - totalElapsedMs
                    val remainingSecs = maxOf(0, (remainingMs / 1000).toInt())
                    _timerSecondsLeft.value = remainingSecs

                    val currentSessionSecs = (totalElapsedMs / 1000).toInt()
                    _cumulativeSessionFocusSeconds.value = currentSessionSecs
                    if (currentSessionSecs >= 21600) { // 6 hours
                        launch(Dispatchers.Main) {
                            Toast.makeText(appContext, "⚠️ Session focus limit of 6 hours reached! Timer paused.", Toast.LENGTH_LONG).show()
                        }
                        pauseTimer(appContext)
                        break
                    }

                    val todayTotalSecs = getTodayFocusSeconds() + currentSessionSecs
                    if (todayTotalSecs >= 72000) { // 20 hours
                        launch(Dispatchers.Main) {
                            Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Timer paused.", Toast.LENGTH_LONG).show()
                        }
                        pauseTimer(appContext)
                        break
                    }
                    
                    updateOverlayTextAndState()
                    
                    if (remainingMs <= 0) break // Phase finished
                }

                if (_timerSecondsLeft.value <= 0) {
                    scope.launch(Dispatchers.Main) {
                        handlePhaseCompletion(appContext, completedFocusPhase = true)
                    }
                }
            }
        } else {
            _isTimerRunning.value = true
            // --- BREAK MODE (Simple Countdown) ---
            addSystemLog(appContext, "Start Break", "BUTTON_PRESS", "Left=${_timerSecondsLeft.value}s")
            KeepAliveService.start(appContext)
            
            saveActiveSessionState(appContext)
            reportActionToFirebase(appContext, "take_break_pomo")

            timerJob?.cancel()
            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                var lastTickTime = android.os.SystemClock.elapsedRealtime()
                while (_isTimerRunning.value && !_isFocusPhase.value && _timerSecondsLeft.value > 0) {
                    delay(1000) // Simple 1-second tick for breaks
                    val now = android.os.SystemClock.elapsedRealtime()
                    val actualElapsedSecs = ((now - lastTickTime) / 1000).toInt()
                    lastTickTime = now

                    if (actualElapsedSecs > 0) {
                        _timerSecondsLeft.value = maxOf(0, _timerSecondsLeft.value - actualElapsedSecs)
                        updateOverlayTextAndState()
                    }
                }

                if (_timerSecondsLeft.value <= 0) {
                    scope.launch(Dispatchers.Main) {
                        handlePhaseCompletion(appContext, completedFocusPhase = false)
                    }
                }
            }
        }
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
        AlarmScheduler.scheduleTimerEndAlarm(appContext, _timerSecondsLeft.value, _isFocusPhase.value)
    }

    private fun handlePhaseCompletion(context: Context, completedFocusPhase: Boolean) {
        val appContext = context.applicationContext

        // SAFETY CHECK: If the user is currently actively focusing in Stopwatch mode,
        // a stale Timer completion event MUST NOT interrupt, play bell sound, or reset/wipe the active stopwatch session!
        if (completedFocusPhase && (_isStopwatchActive.value || (_wasStartedFromStopwatch.value && _isFocusPhase.value))) {
            Log.w("FocusTimerManager", "Suppressed handlePhaseCompletion: User is currently actively focusing in Stopwatch mode.")
            _isTimerRunning.value = false
            timerJob?.cancel()
            timerJob = null
            AlarmScheduler.cancelTimerEndAlarm(appContext)
            return
        }

        _isTimerRunning.value = false
        val previousTimerJob = timerJob
        timerJob = null
        if (previousTimerJob != null && previousTimerJob.isActive) {
            previousTimerJob.cancel()
        }
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        saveActiveSessionState(appContext)

        // Sound prompt alerting phase change (10s ring bell sound with vibration)
        if (_soundEnabled.value) {
            playStrongBellSoundWithVibration(appContext)
        }

        if (completedFocusPhase && !_wasStartedFromStopwatch.value) {
            val duration = _timerDurationMinutes.value

            // Save focus records history item -> Instead of saving directly, we queue a pending review
            val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
            val startStr = formatter.format(java.util.Date(System.currentTimeMillis() - duration * 60 * 1000L))
            val endStr = formatter.format(java.util.Date())
            val taskName = _attachedTask.value?.title ?: "Focus Session"
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Trigger immediate, robust data persistence before resetting the active session tracking values
            val elapsedSecs = duration * 60
            val savedRecord = persistFocusSession(appContext, elapsedSecs, isTimer = true)

            // Do not request confirmation dialog after pomodoro finishes automatically
            _pendingFocusReview.value = null
            _cumulativeSessionFocusSeconds.value = 0
            _accumulatedSessionTimeMs.value = 0L
            setLastResumeTimeMs(null)

            // Switch to Break Mode
            _isFocusPhase.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val bMins = prefs.getInt("break_duration", 5)
            _timerSecondsLeft.value = bMins * 60

            saveActiveSessionState(appContext)
            KeepAliveService.updateNotification(appContext)
            syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            // Auto-start break depends on autoStartBreak preference
            val autoStartBreakPref = _autoStartBreak.value
            if (autoStartBreakPref) {
                startTimer(appContext, stopActiveAlarm = false)
            } else {
                _isPaused.value = true
            }
        } else {
            // Break Finished!
            openAppWithTimerPageInFront(appContext)

            if (_wasStartedFromStopwatch.value) {
                // Play bell sound for 3 seconds after stopwatch break is over
                if (_soundEnabled.value) {
                    playStopwatchBreakEndBellSound(appContext)
                }

                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val autoStartSw = _autoStartStopwatchAfterBreak.value || prefs.getBoolean("stopwatch_autostart_after_break", true)
                if (autoStartSw) {
                    _isFocusPhase.value = true
                    _wasStartedFromStopwatch.value = true
                    setTabFocusTimerSelected(false)
                    prefs.edit().putBoolean("was_started_from_stopwatch", true).apply()

                    // Reset Timer back to pomo duration
                    _timerSecondsLeft.value = _timerDurationMinutes.value * 60

                    saveActiveSessionState(appContext)
                    KeepAliveService.updateNotification(appContext)
                    syncStateToFirebase(appContext)
                    updateOverlayVisibility(appContext)

                    startStopwatch(appContext, stopActiveAlarm = false, resumeFromBreak = true)
                } else {
                    _isFocusPhase.value = false // Keep as break
                    _timerSecondsLeft.value = 0
                    
                    saveActiveSessionState(appContext)
                    KeepAliveService.updateNotification(appContext)
                    syncStateToFirebase(appContext)
                    updateOverlayVisibility(appContext)

                    pauseStopwatch(appContext, stopActiveAlarm = false)
                }
            } else {
                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val autoStartPomoPref = _autoStartPomo.value
                if (autoStartPomoPref) {
                    // Normal Pomo Break End: Reset to Work Phase
                    _isFocusPhase.value = true
                    _timerSecondsLeft.value = _timerDurationMinutes.value * 60

                    _accumulatedSessionTimeMs.value = 0L
                    setLastResumeTimeMs(null)
                    _cumulativeSessionFocusSeconds.value = 0

                    saveActiveSessionState(appContext)
                    KeepAliveService.updateNotification(appContext)
                    syncStateToFirebase(appContext)
                    updateOverlayVisibility(appContext)

                    startTimer(appContext, stopActiveAlarm = false)
                } else {
                    _isFocusPhase.value = true // Switch to Focus phase!
                    _timerSecondsLeft.value = _timerDurationMinutes.value * 60 // Reset to 25 mins!

                    _accumulatedSessionTimeMs.value = 0L
                    setLastResumeTimeMs(null)
                    _cumulativeSessionFocusSeconds.value = 0

                    _isPaused.value = false

                    saveActiveSessionState(appContext)
                    KeepAliveService.updateNotification(appContext)
                    syncStateToFirebase(appContext)
                    updateOverlayVisibility(appContext)
                }
            }
        }
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun pauseTimer(context: Context) {
        init(context)
        claimCommandDevice(context)
        
        val appContext = context.applicationContext
        val email = com.example.api.DynamicCommandManager.activeEmail
        val timelineBeforePause = com.example.api.DynamicCommandManager.currentTimelineFlow.value

        val chunkMs = getCurrentChunkMs()
        setLastResumeTimeMs(null) // Wipes out active live-tracking

        if (email.isNotEmpty()) {
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand("paused", timelineBeforePause, "pomodoro", task, tag)
        }

        if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            val timelineAfterPause = com.example.api.DynamicCommandManager.currentTimelineFlow.value
            val totalMs = if (timelineAfterPause.isNotEmpty()) {
                val calcMs = com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(timelineAfterPause, "Paused")
                if (calcMs > 0L) calcMs else (_accumulatedSessionTimeMs.value + chunkMs)
            } else {
                _accumulatedSessionTimeMs.value + chunkMs
            }
            _accumulatedSessionTimeMs.value = totalMs
            _cumulativeSessionFocusSeconds.value = (totalMs / 1000).toInt()
        }

        com.example.util.FocusSessionDbHelper.handlePauseFocus(appContext)
 
        updateLocalInteractionTimestamp()
        stopAlarm()
        FocusDriftDetector.stopMonitoring()
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
        _isPaused.value = true // Triggers the 2-button UI!
        recordSessionPause()
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        addSystemLog(appContext, "Pause Timer", "BUTTON_PRESS", "SecondsLeft=${_timerSecondsLeft.value}s")
        saveActiveSessionState(appContext)
        reportActionToFirebase(appContext, "pause_timer")
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun extractSessionStartMs(
        sessionId: String?,
        lastEventTsMs: Long? = null,
        baseFocusMs: Long = 0L,
        baseBreakMs: Long = 0L,
        timeline: List<com.example.api.TimelineEvent>? = null
    ): Long {
        val trueNow = com.example.util.TimeEngine.getUniversalTimeMs()
        
        if (!timeline.isNullOrEmpty()) {
            val startEvent = timeline.firstOrNull { 
                val act = it.event.lowercase().trim()
                act == "start"
            }
            if (startEvent != null && startEvent.timestamp > 0L && startEvent.timestamp <= trueNow) {
                return startEvent.timestamp
            }
        }

        if (!sessionId.isNullOrEmpty()) {
            val raw = sessionId.substringAfter("sess_")
            val digitsOnly = raw.takeWhile { it.isDigit() }
            val parsed = digitsOnly.toLongOrNull()
            if (parsed != null && parsed > 0L && parsed <= trueNow) {
                return parsed
            }
        }

        if (lastEventTsMs != null && lastEventTsMs > 0L) {
            val estimatedStart = lastEventTsMs - baseFocusMs - baseBreakMs
            if (estimatedStart > 0L && estimatedStart <= trueNow) {
                return estimatedStart
            }
            return lastEventTsMs
        }

        return trueNow - maxOf(0L, baseFocusMs)
    }

    fun persistFocusSession(context: Context, elapsedSecs: Int, isTimer: Boolean): FocusRecord? {
        if (elapsedSecs <= 0) return null
        
        val elapsedFocusMs = elapsedSecs * 1000L
        val appContext = context.applicationContext
        if (elapsedFocusMs <= 0L) {
            // Short-circuit: abort archival sequence, clear the SQLite scratchpad, and push an IDLE reset payload to the outbox.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = com.example.data.AppDatabase.getInstance(appContext)
                    val currentSession = db.localActiveSessionDao().getActiveSession()
                    val sessionId = currentSession?.session_id ?: "sess_${System.currentTimeMillis()}"
                    
                    db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                    
                    val nowMs = System.currentTimeMillis()
                    val endMutationId = "end_" + java.util.UUID.randomUUID().toString()
                    appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit().putString("last_local_end_mutation_id", endMutationId).apply()
                    com.example.api.Firebase.didThisDeviceInitiateEnd = true
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(appContext, "Session duration is 0. Aborted.", Toast.LENGTH_SHORT).show()
                    }
                    Log.d("FocusTimerManager", "0-second guard triggered. Aborted persistFocusSession.")
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Error in 0-second guard for persistFocusSession", e)
                }
            }
            return null
        }
        
        val currentSession = try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.AppDatabase.getInstance(appContext).localActiveSessionDao().getActiveSession()
            }
        } catch (e: Exception) {
            null
        }

        com.example.util.FocusSessionDbHelper.handleEndSession(appContext, onWiped = {
            Log.d("FocusTimerManager", "Active session wiped due to short-circuit guard.")
            performCloudAlignmentCheck(appContext)
            runBackgroundAuditAndHealing(appContext)
        }, onArchived = { record ->
            Log.d("FocusTimerManager", "Active session archived: recordId = ${record.record_id}")
            performCloudAlignmentCheck(appContext)
            runBackgroundAuditAndHealing(appContext)
        })
        
        // Automated multi-stage audit & alignment pipeline (runs immediately and within 10s in background)
        scope.launch(Dispatchers.IO) {
            try {
                delay(2000)
                performCloudAlignmentCheck(appContext)
                runBackgroundAuditAndHealing(appContext)
                delay(6000)
                performCloudAlignmentCheck(appContext)
                runBackgroundAuditAndHealing(appContext)
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error in post-session audit/sync pipeline", e)
            }
        }
        
        recordSessionCompleteOrReset(true)
        val sessionStart = extractSessionStartMs(
            sessionId = currentSession?.session_id,
            lastEventTsMs = currentSession?.last_event_ts_ms,
            baseFocusMs = currentSession?.base_focus_time_ms ?: (elapsedSecs * 1000L),
            baseBreakMs = currentSession?.base_break_time_ms ?: 0L
        )

        if (_verifiedSessionStartMs.value == null) {
            _verifiedSessionStartMs.value = sessionStart
        }
        
        val finalMinutes = com.example.util.TimeEngine.roundSecondsToMinutes(elapsedSecs)
        val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val startStr = formatter.format(java.util.Date(sessionStart))
        val endStr = formatter.format(java.util.Date())
        val taskName = _attachedTask.value?.title ?: "Focus Session"
        val tagValue = _attachedTag.value
        val modeNotes = if (isTimer) "TIMER_SESSION" else "STOPWATCH_SESSION"
        val sessionId = currentSession?.session_id ?: com.example.api.DynamicCommandManager.activeSessionId.ifEmpty { "sess_${com.example.util.StableTime.currentTimeMillis()}" }
        val sessionBreakMs = currentSession?.base_break_time_ms ?: 0L
        
        // 1. Save Focus Record locally
        val record = addFocusRecord(context, startStr, endStr, taskName, finalMinutes, modeNotes, elapsedSecs, tagValue, id = sessionId, totalBreakMs = sessionBreakMs)
        if (record == null) {
            Log.w("FocusTimerManager", "persistFocusSession aborted due to Causality Guard.")
            return null
        }

        // 2. Update Stats (Pomos count and total focus minutes)
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val focusTimerDurationMins = prefs.getInt("timer_duration", 25)
        if (isTimer && finalMinutes >= focusTimerDurationMins && focusTimerDurationMins > 0) {
            val currentPomos = _todayPomosCount.value
            _todayPomosCount.value = currentPomos + 1
            prefs.edit().putInt("today_pomos_count", currentPomos + 1).apply()
        }

        val currentMins = _totalFocusMinutes.value
        _totalFocusMinutes.value = currentMins + finalMinutes
        prefs.edit().putInt("total_focus_minutes", currentMins + finalMinutes).apply()

        // Disable global verification dialog for background/immediate completion and auto-saves
        _globalVerificationFocusedTimeSeconds.value = elapsedSecs
        _globalVerificationRevisedTotalMinutes.value = getTodayFocusMinutes()
        _globalVerificationRevisedTotalSeconds.value = getTodayFocusSeconds()
        _showGlobalVerificationDialog.value = false

        // 3. Update task progress in database
        _attachedTask.value?.let { task ->
            val updatedTask = task.copy(actualMinutes = task.actualMinutes + finalMinutes)
            updateTaskInDatabase(context, updatedTask)
            _attachedTask.value = updatedTask
        }
        
        // 4. Remote Firebase Synchronization (Legacy focusTimer sync removed permanently)

        // 5. Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    try {
                        GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                    }
                }
            }
        }

        return record
    }

    fun resetTimer(context: Context, saveSession: Boolean = true) {
        val appContext = context.applicationContext
        if (!isPassiveCalibrationInProgress) {
            appendTimelineEvent(appContext, "END")
            claimCommandDevice(context)
        }
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        AlarmScheduler.cancelTimerEndAlarm(context.applicationContext)
        timerJob?.cancel()
        timerJob = null
        FocusDriftDetector.stopMonitoring()
        _isTimerRunning.value = false

        val chunkMs = if (_isTimerRunning.value) getCurrentChunkMs() else 0L
        val totalMs = _accumulatedSessionTimeMs.value + chunkMs
        val calculatedSecs = (totalMs / 1000).toInt()
        var elapsedSecs = maxOf(_cumulativeSessionFocusSeconds.value, calculatedSecs)

        if (saveSession && elapsedSecs <= 0) {
            try {
                val dbSession = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.AppDatabase.getInstance(appContext).localActiveSessionDao().getActiveSession()
                }
                if (dbSession != null && dbSession.base_focus_time_ms > 0) {
                    elapsedSecs = (dbSession.base_focus_time_ms / 1000).toInt()
                }
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error reading DB active session in resetTimer", e)
            }
        }

        val logCat = if (isPassiveCalibrationInProgress || !saveSession) "CALIBRATION" else "BUTTON_PRESS"
        addSystemLog(appContext, "Reset Timer", logCat, "SaveSession=$saveSession, ElapsedSecs=${elapsedSecs}s")
        
        val shouldSave = saveSession && !isPassiveCalibrationInProgress && elapsedSecs > 0 && _isFocusPhase.value && !_wasStartedFromStopwatch.value

        if (shouldSave) {
            val rtdbEmail = com.example.api.DynamicCommandManager.activeEmail
            if (rtdbEmail.isNotEmpty()) {
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val mode = com.example.api.DynamicCommandManager.currentTimerModeFlow.value
                val task = _attachedTask.value?.title ?: "Focus Session"
                val tag = _attachedTag.value.ifEmpty { "Study" }
                val sessionId = com.example.api.DynamicCommandManager.activeSessionId
                scope.launch(Dispatchers.IO) {
                    try {
                        com.example.api.SessionTerminator.executeSessionTermination(
                            context = appContext,
                            email = rtdbEmail,
                            currentTimeline = timeline,
                            timerMode = mode,
                            currentTask = task,
                            currentTag = tag,
                            originalSessionId = sessionId
                        )
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Error executing session termination in resetTimer", e)
                    }
                }
            }
            persistFocusSession(context, elapsedSecs, isTimer = true)
        } else if (!isPassiveCalibrationInProgress) {
            val rtdbEmail = com.example.api.DynamicCommandManager.activeEmail
            if (rtdbEmail.isNotEmpty()) {
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val mode = com.example.api.DynamicCommandManager.currentTimerModeFlow.value
                val task = _attachedTask.value?.title ?: "Focus Session"
                val tag = _attachedTag.value.ifEmpty { "Study" }
                com.example.api.DynamicCommandManager.executeMidSessionCommand("END", timeline, mode, task, tag)
            }
            scope.launch(Dispatchers.IO) {
                FocusSessionDbHelper.dbMutex.withLock {
                    try {
                        val db = com.example.data.AppDatabase.getInstance(appContext)
                        val currentSession = db.localActiveSessionDao().getActiveSession()
                        val sessionId = currentSession?.session_id ?: "sess_${StableTime.currentTimeMillis()}"
                        
                        db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                        
                        val nowMs = StableTime.currentTimeMillis()
                        val endMutationId = "end_" + java.util.UUID.randomUUID().toString()
                        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit().putString("last_local_end_mutation_id", endMutationId).apply()
                        com.example.api.Firebase.didThisDeviceInitiateEnd = true
                        
                        val wipePayload = org.json.JSONObject().apply {
                            put("sessionId", sessionId)
                            put("status", "IDLE")
                            put("lastMutationId", endMutationId)
                        }.toString()

                        val outboxWipe = com.example.data.OutboxQueue(
                            mutation_id = "mut_wipe_${nowMs}_android",
                            created_at_ms = nowMs,
                            routing_target = "RTDB_LIVE_SYNC",
                            action_type = "WIPE",
                            payload_json = wipePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Zero out active timer memory state
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        _stopwatchSeconds.value = 0
        _isPaused.value = false
        setLastResumeTimeMs(null)
        setOptimisticTodayFocusSeconds(null)
        _currentSessionStartMs.value = null
        savedStopwatchSeconds = 0

        if (!shouldSave) {
            try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    val db = com.example.data.AppDatabase.getInstance(appContext)
                    db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                }
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error clearing local_active_session synchronously in resetTimer", e)
            }
        }

        _isFocusPhase.value = true
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        setLastResumeTimeMs(null)
        _wasStartedFromStopwatch.value = false
        _isPaused.value = false
        savedStopwatchSeconds = 0
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("was_started_from_stopwatch", false)
            .putBoolean("is_paused", false)
            .putBoolean("timer_is_running", false)
            .putBoolean("timer_is_stopwatch_active", false)
            .putInt("saved_stopwatch_seconds", 0)
            .putLong("accumulated_time_ms", 0L)
            .apply()
        _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        com.example.api.DynamicCommandManager.resetToIdle()
        saveActiveSessionState(appContext)
        reportActionToFirebase(appContext, "reset_timer")
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun takeBreakFromStopwatch(context: Context) {
        init(context)
        claimCommandDevice(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        
        val email = com.example.api.DynamicCommandManager.activeEmail
        if (email.isNotEmpty()) {
            val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand("break_started", timeline, "stopwatch", task, tag)
        }
        
        pauseStopwatch(context)
        
        // CRITICAL: Save exact stopwatch elapsed time after pausing and committing the chunk!
        savedStopwatchSeconds = _stopwatchSeconds.value
        
        _isPaused.value = false
        _isFocusPhase.value = false
        _wasStartedFromStopwatch.value = true
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("was_started_from_stopwatch", true)
            .putInt("saved_stopwatch_seconds", savedStopwatchSeconds)
            .apply()
        
        _timerSecondsLeft.value = _stopwatchBreakDurationMinutes.value * 60
        
        KeepAliveService.updateNotification(context)
        appendTimelineEvent(context, "BREAK START")
        startTimer(context)
        reportActionToFirebase(context, "take_break_stopwatch")
        com.example.widget.WidgetUpdater.updateAllWidgets(context)
    }

    fun takeBreakFromPomodoro(context: Context) {
        init(context)
        claimCommandDevice(context)
        updateLocalInteractionTimestamp()
        stopAlarm()

        val email = com.example.api.DynamicCommandManager.activeEmail
        if (email.isNotEmpty()) {
            val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand("break_started", timeline, "pomodoro", task, tag)
        }

        pauseTimer(context)
        
        val elapsedSecs = _cumulativeSessionFocusSeconds.value
        if (elapsedSecs > 0) {
            persistFocusSession(context, elapsedSecs, isTimer = true)
            _cumulativeSessionFocusSeconds.value = 0
            _accumulatedSessionTimeMs.value = 0L
            setLastResumeTimeMs(null)
        }
        
        _isPaused.value = false
        _isFocusPhase.value = false
        _wasStartedFromStopwatch.value = false
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        
        val bMins = prefs.getInt("break_duration", 5)
        _timerSecondsLeft.value = bMins * 60
        
        KeepAliveService.updateNotification(context)
        appendTimelineEvent(context, "BREAK START")
        startTimer(context)
        reportActionToFirebase(context, "take_break_pomo")
        com.example.widget.WidgetUpdater.updateAllWidgets(context)
    }

    fun skipOrEndBreak(context: Context, isUserManualEnd: Boolean = false) {
        init(context)
        claimCommandDevice(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
        _isPaused.value = false

        val appContext = context.applicationContext
        val email = com.example.api.DynamicCommandManager.activeEmail
        if (email.isNotEmpty()) {
            val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
            val mode = if (_wasStartedFromStopwatch.value) "stopwatch" else "pomodoro"
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand("break_ended", timeline, mode, task, tag)
        }

        appendTimelineEvent(appContext, "BREAK END")
        if (_wasStartedFromStopwatch.value) {
            _isFocusPhase.value = true
            _wasStartedFromStopwatch.value = true
            setTabFocusTimerSelected(false)
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("was_started_from_stopwatch", true).apply()

            KeepAliveService.updateNotification(appContext)
            syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            if (!isUserManualEnd && _autoStartStopwatchAfterBreak.value) {
                startStopwatch(appContext, resumeFromBreak = true)
            } else {
                _accumulatedSessionTimeMs.value = savedStopwatchSeconds * 1000L
                _stopwatchSeconds.value = savedStopwatchSeconds
            }
        } else {
            _isFocusPhase.value = true
            _timerSecondsLeft.value = _timerDurationMinutes.value * 60
            _accumulatedSessionTimeMs.value = 0L
            _cumulativeSessionFocusSeconds.value = 0
            KeepAliveService.updateNotification(appContext)
            syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            if (!isUserManualEnd && _autoStartPomo.value) {
                startTimer(appContext)
            }
        }
        reportActionToFirebase(appContext, "skip_or_end_break")
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun startStopwatch(context: Context, stopActiveAlarm: Boolean = true, isResuming: Boolean = false, resumeFromBreak: Boolean = false) {
        init(context)
        claimCommandDevice(context)
        val appContext = context.applicationContext
        
        if (_isTimerRunning.value) {
            pauseTimer(context)
        }
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
        AlarmScheduler.cancelTimerEndAlarm(appContext)

        val actualResuming = isResuming || resumeFromBreak || _isPaused.value || _stopwatchSeconds.value > 0 || _accumulatedSessionTimeMs.value > 0L
        setTabFocusTimerSelected(false)
        _wasStartedFromStopwatch.value = true
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", true).apply()

        if (stopActiveAlarm) {
            stopAlarm()
        }

        // CRITICAL: If returning from a break, restore the preserved count-up time!
        if (resumeFromBreak) {
            _accumulatedSessionTimeMs.value = savedStopwatchSeconds * 1000L
            _stopwatchSeconds.value = savedStopwatchSeconds
            _isPaused.value = false
        } else if (!actualResuming) {
            _accumulatedSessionTimeMs.value = 0L
            _stopwatchSeconds.value = 0
            savedStopwatchSeconds = 0
            _isPaused.value = false
            setLastResumeTimeMs(null)
            _currentSessionStartMs.value = StableTime.currentTimeMillis()
        } else {
            _isPaused.value = false
        }

        val completedTodaySecs = getTodayFocusSeconds()
        if (completedTodaySecs >= 72000) {
            Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Take a break.", Toast.LENGTH_LONG).show()
            return
        }

        // If we are currently in break mode, stop the break timer and go back to stopwatch mode
        if (!_isFocusPhase.value) {
            timerJob?.cancel()
            _isTimerRunning.value = false
            _isFocusPhase.value = true
            _wasStartedFromStopwatch.value = true
            setTabFocusTimerSelected(false)
            val p = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            p.edit().putBoolean("was_started_from_stopwatch", true).apply()
            
            // Reset break timer seconds left back to pomo duration for clean state
            _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        }

        if (_isStopwatchActive.value) return
        updateLocalInteractionTimestamp()
        _isStopwatchActive.value = true
        saveActiveSessionState(appContext)
        val isResumingSession = actualResuming || _accumulatedSessionTimeMs.value > 0L || _stopwatchSeconds.value > 0
        val email = com.example.api.DynamicCommandManager.activeEmail
        if (email.isNotEmpty()) {
            val action = if (resumeFromBreak) "break_ended" else if (isResumingSession) "resumed" else "start"
            val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand(action, timeline, "stopwatch", task, tag)
        }
        if (isResumingSession) {
            com.example.util.FocusSessionDbHelper.handleResumeFocus(appContext)
        } else {
            com.example.util.FocusSessionDbHelper.handleStartFocus(
                appContext,
                tag = _attachedTag.value.ifEmpty { "Study" },
                taskTitle = _attachedTask.value?.title ?: "General Focus",
                mode = "STOPWATCH"
            )
        }
        if (_isFocusPhase.value) {
            appendTimelineEvent(appContext, if (isResumingSession) "RESUME" else "START")
        }
        recordSessionStart()
        addSystemLog(appContext, "Start Stopwatch", "BUTTON_PRESS", "Seconds=${_stopwatchSeconds.value}s")
        performCloudAlignmentCheck(appContext)
        runBackgroundAuditAndHealing(appContext)
        
        KeepAliveService.start(appContext)
        FocusDriftDetector.startMonitoring(appContext, com.example.api.DynamicCommandManager.activeEmail)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)

        _lastResumeTimeMs.value = StableTime.currentTimeMillis()
        lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        saveActiveSessionState(appContext)
        reportActionToFirebase(appContext, "start_stopwatch")

        val baseAccumulatedMs = _accumulatedSessionTimeMs.value
        stopwatchJob?.cancel()
        stopwatchJob = scope.launch {
            KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
            while (_isStopwatchActive.value) {
                delay(200) // UI refresh rate
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val currentStatus = if (_isPaused.value) "Paused" else "Focusing"
                val totalMs = if (timeline.isNotEmpty()) {
                    com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(timeline, currentStatus)
                } else {
                    val currentChunkMs = getCurrentChunkMs()
                    baseAccumulatedMs + currentChunkMs
                }
                _accumulatedSessionTimeMs.value = totalMs

                val elapsedSecs = (totalMs / 1000).toInt()
                _stopwatchSeconds.value = elapsedSecs
                _cumulativeSessionFocusSeconds.value = elapsedSecs

                val currentSessionSecs = elapsedSecs
                if (currentSessionSecs >= 21600) { // 6 hours limit
                    launch(Dispatchers.Main) {
                        Toast.makeText(appContext, "⚠️ Session focus limit of 6 hours reached! Stopwatch paused.", Toast.LENGTH_LONG).show()
                    }
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                    break
                }

                val todayTotalSecs = getTodayFocusSeconds() + currentSessionSecs
                if (todayTotalSecs >= 72000) { // 20 hours limit
                    launch(Dispatchers.Main) {
                        Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Stopwatch paused.", Toast.LENGTH_LONG).show()
                    }
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                    break
                }
                
                updateOverlayTextAndState()
            }
        }
    }

    fun pauseStopwatch(context: Context, stopActiveAlarm: Boolean = true) {
        init(context)
        claimCommandDevice(context)
        
        val appContext = context.applicationContext
        val email = com.example.api.DynamicCommandManager.activeEmail
        val timelineBeforePause = com.example.api.DynamicCommandManager.currentTimelineFlow.value

        val chunkMs = getCurrentChunkMs()
        setLastResumeTimeMs(null) // Wipes out active live-tracking

        if (email.isNotEmpty()) {
            val task = _attachedTask.value?.title ?: "Focus Session"
            val tag = _attachedTag.value.ifEmpty { "Study" }
            com.example.api.DynamicCommandManager.executeMidSessionCommand("paused", timelineBeforePause, "stopwatch", task, tag)
        }

        val timelineAfterPause = com.example.api.DynamicCommandManager.currentTimelineFlow.value
        val totalMs = if (timelineAfterPause.isNotEmpty()) {
            val calcMs = com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(timelineAfterPause, "Paused")
            if (calcMs > 0L) calcMs else (_accumulatedSessionTimeMs.value + chunkMs)
        } else {
            _accumulatedSessionTimeMs.value + chunkMs
        }
        _accumulatedSessionTimeMs.value = totalMs

        val totalSecs = (totalMs / 1000).toInt()
        _stopwatchSeconds.value = totalSecs
        _cumulativeSessionFocusSeconds.value = totalSecs

        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        stopwatchJob?.cancel()
        stopwatchJob = null
        FocusDriftDetector.stopMonitoring()
        _isStopwatchActive.value = false
        _isPaused.value = true // Triggers the 2-button UI!
        recordSessionPause()
        appendTimelineEvent(appContext, "PAUSE")
        com.example.util.FocusSessionDbHelper.handlePauseFocus(appContext)
        addSystemLog(appContext, "Pause Stopwatch", "BUTTON_PRESS", "Seconds=${_stopwatchSeconds.value}s")
        saveActiveSessionState(appContext)
        reportActionToFirebase(appContext, "pause_stopwatch")
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun resetStopwatch(context: Context, saveSession: Boolean = true) {
        val appContext = context.applicationContext
        if (!isPassiveCalibrationInProgress) {
            appendTimelineEvent(appContext, "END")
            claimCommandDevice(context)
        }
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        stopwatchJob?.cancel()
        stopwatchJob = null
        FocusDriftDetector.stopMonitoring()
        _isStopwatchActive.value = false

        val chunkMs = if (_isStopwatchActive.value) getCurrentChunkMs() else 0L
        val totalMs = _accumulatedSessionTimeMs.value + chunkMs
        val calculatedSecs = (totalMs / 1000).toInt()
        var elapsedSecs = maxOf(_stopwatchSeconds.value, _cumulativeSessionFocusSeconds.value, calculatedSecs)

        if (saveSession && elapsedSecs <= 0) {
            try {
                val dbSession = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.AppDatabase.getInstance(appContext).localActiveSessionDao().getActiveSession()
                }
                if (dbSession != null && dbSession.base_focus_time_ms > 0) {
                    elapsedSecs = (dbSession.base_focus_time_ms / 1000).toInt()
                }
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error reading DB active session in resetStopwatch", e)
            }
        }

        val logCat = if (isPassiveCalibrationInProgress || !saveSession) "CALIBRATION" else "BUTTON_PRESS"
        addSystemLog(appContext, "Reset Stopwatch", logCat, "SaveSession=$saveSession, Seconds=${elapsedSecs}s")
        
        val shouldSave = saveSession && !isPassiveCalibrationInProgress && elapsedSecs > 0

        if (shouldSave) {
            val rtdbEmail = com.example.api.DynamicCommandManager.activeEmail
            if (rtdbEmail.isNotEmpty()) {
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val mode = "stopwatch"
                val task = _attachedTask.value?.title ?: "Focus Session"
                val tag = _attachedTag.value.ifEmpty { "Study" }
                val sessionId = com.example.api.DynamicCommandManager.activeSessionId.ifEmpty { "sess_${StableTime.currentTimeMillis()}" }
                scope.launch(Dispatchers.IO) {
                    try {
                        com.example.api.SessionTerminator.executeSessionTermination(
                            context = appContext,
                            email = rtdbEmail,
                            currentTimeline = timeline,
                            timerMode = mode,
                            currentTask = task,
                            currentTag = tag,
                            originalSessionId = sessionId
                        )
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Error executing session termination in resetStopwatch", e)
                    }
                }
            }
            persistFocusSession(context, elapsedSecs, isTimer = false)
        } else if (!isPassiveCalibrationInProgress) {
            val rtdbEmail = com.example.api.DynamicCommandManager.activeEmail
            if (rtdbEmail.isNotEmpty()) {
                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val mode = "stopwatch"
                val task = _attachedTask.value?.title ?: "Focus Session"
                val tag = _attachedTag.value.ifEmpty { "Study" }
                com.example.api.DynamicCommandManager.executeMidSessionCommand("END", timeline, mode, task, tag)
            }
            scope.launch(Dispatchers.IO) {
                FocusSessionDbHelper.dbMutex.withLock {
                    try {
                        val db = com.example.data.AppDatabase.getInstance(appContext)
                        val currentSession = db.localActiveSessionDao().getActiveSession()
                        val sessionId = currentSession?.session_id ?: "sess_${StableTime.currentTimeMillis()}"
                        
                        db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                        
                        val nowMs = StableTime.currentTimeMillis()
                        val endMutationId = "end_" + java.util.UUID.randomUUID().toString()
                        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit().putString("last_local_end_mutation_id", endMutationId).apply()
                        com.example.api.Firebase.didThisDeviceInitiateEnd = true
                        
                        val wipePayload = org.json.JSONObject().apply {
                            put("sessionId", sessionId)
                            put("status", "IDLE")
                            put("lastMutationId", endMutationId)
                        }.toString()

                        val outboxWipe = com.example.data.OutboxQueue(
                            mutation_id = "mut_wipe_${nowMs}_android",
                            created_at_ms = nowMs,
                            routing_target = "RTDB_LIVE_SYNC",
                            action_type = "WIPE",
                            payload_json = wipePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Zero out active stopwatch memory state
        _stopwatchSeconds.value = 0
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        setLastResumeTimeMs(null)
        _isPaused.value = false
        savedStopwatchSeconds = 0

        if (!shouldSave) {
            try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    val db = com.example.data.AppDatabase.getInstance(appContext)
                    db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                }
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error clearing local_active_session synchronously in resetStopwatch", e)
            }
        }

        // Reset phase and wasStartedFromStopwatch flags so they don't get stuck in break mode
        _isFocusPhase.value = true
        _wasStartedFromStopwatch.value = false
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("was_started_from_stopwatch", false)
            .putBoolean("is_paused", false)
            .putBoolean("timer_is_running", false)
            .putBoolean("timer_is_stopwatch_active", false)
            .putInt("saved_stopwatch_seconds", 0)
            .putLong("accumulated_time_ms", 0L)
            .apply()
        _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        com.example.api.DynamicCommandManager.resetToIdle()
        saveActiveSessionState(appContext)
        reportActionToFirebase(appContext, "reset_stopwatch")
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun alignStateWithRemoteEnd(context: Context) {
        val appContext = context.applicationContext
        _isTimerRunning.value = false
        _isStopwatchActive.value = false
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        setLastResumeTimeMs(null)
        _wasStartedFromStopwatch.value = false
        _isFocusPhase.value = true
        
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_is_running", false)
            .putBoolean("timer_is_stopwatch_active", false)
            .putLong("accumulated_time_ms", 0L)
            .putLong("last_resume_time_ms", -1L)
            .putLong("timer_session_start_ms", -1L)
            .putInt("timer_cumulative_seconds", 0)
            .putBoolean("timer_is_focus_phase", true)
            .putBoolean("timer_was_started_from_stopwatch", false)
            .putInt("timer_attached_task_id", -1)
            .putString("timer_attached_tag", "")
            .putLong("timer_last_active_timestamp", StableTime.currentTimeMillis())
            .putInt("timer_seconds_left", _timerDurationMinutes.value * 60)
            .apply()

        _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        _stopwatchSeconds.value = 0
        
        timerJob?.cancel()
        timerJob = null
        stopwatchJob?.cancel()
        stopwatchJob = null
        
        stopAlarm()
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        
        KeepAliveService.updateNotification(appContext)
        updateOverlayVisibility(appContext)
        com.example.widget.WidgetUpdater.updateAllWidgets(appContext)
    }

    fun setAppBackgroundedState(context: Context, backgrounded: Boolean) {
        init(context)
        appIsBackgrounded = backgrounded
        
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("app_is_backgrounded", backgrounded).apply()
        
        val batterySaverEnabled = prefs.getBoolean("battery_saver_mode", false)
        val backgroundServicesSilentMode = prefs.getBoolean("background_services_silent_mode", false)
        
        if (backgroundServicesSilentMode) {
            val appContext = context.applicationContext
            if (backgrounded) {
                Log.d("FocusTimerManager", "Background Services Silent Mode: App backgrounded, disabling background loops and saving state.")
                
                // Save state to disk first so recovery is flawless
                saveActiveSessionState(appContext)
                
                // Stop KeepAliveService completely
                try {
                    val serviceIntent = Intent(appContext, KeepAliveService::class.java)
                    appContext.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Cancel active ticking jobs
                timerJob?.cancel()
                timerJob = null
                stopwatchJob?.cancel()
                stopwatchJob = null
                actualUiTickJob?.cancel()
                actualUiTickJob = null
                
                // Silent mode means no active alarms/notifications trigger in background
                AlarmScheduler.cancelTimerEndAlarm(appContext)
            } else {
                Log.d("FocusTimerManager", "Background Services Silent Mode: App foregrounded, recovering and resuming state.")
                // Reconnect & recalibrate within seconds
                recoverAndResumeActiveSession(appContext)
                
                // Start KeepAliveService to restore foreground state if background activity is enabled
                try {
                    KeepAliveService.start(appContext)
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Failed to start KeepAliveService on foreground restore: ${e.message}")
                }
            }
        } else if (batterySaverEnabled) {
            val appContext = context.applicationContext
            if (backgrounded) {
                Log.d("FocusTimerManager", "Battery Saver Mode: App backgrounded, disabling background loops and saving state.")
                
                // Save state to disk first so recovery is flawless
                saveActiveSessionState(appContext)
                
                // Stop KeepAliveService completely
                try {
                    val serviceIntent = Intent(appContext, KeepAliveService::class.java)
                    appContext.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Cancel active ticking jobs
                timerJob?.cancel()
                timerJob = null
                stopwatchJob?.cancel()
                stopwatchJob = null
                actualUiTickJob?.cancel()
                actualUiTickJob = null
                
                // Make sure exact system alarm is active so Doze mode wakes us up at 00:00!
                if (_isTimerRunning.value && _timerSecondsLeft.value > 0) {
                    AlarmScheduler.scheduleTimerEndAlarm(appContext, _timerSecondsLeft.value, _isFocusPhase.value)
                } else {
                    AlarmScheduler.cancelTimerEndAlarm(appContext)
                }
            } else {
                Log.d("FocusTimerManager", "Battery Saver Mode: App foregrounded, recovering and resuming state.")
                recoverAndResumeActiveSession(appContext)
            }
        }
        
        updateOverlayVisibility(context.applicationContext)
    }

    fun setTimerScreenActiveState(context: Context, active: Boolean) {
        init(context)
        isTimerScreenActive = active
        updateOverlayVisibility(context.applicationContext)
    }

    private fun updateOverlayVisibility(context: Context) {
        scope.launch(Dispatchers.Main) {
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val showOverlayPref = prefs.getBoolean("show_overlay_on_exit", true)

            val isPromoSessionActive = (isTimerRunning.value || isPaused.value) && (!isFocusPhase.value || timerSecondsLeft.value < timerDurationMinutes.value * 60)
            val isStopwatchSessionActive = (isStopwatchActive.value || isPaused.value) && stopwatchSeconds.value > 0
            val hasAnySession = isTimerRunning.value || isStopwatchActive.value || isPaused.value || isPromoSessionActive || isStopwatchSessionActive

            val backgroundServicesSilentMode = prefs.getBoolean("background_services_silent_mode", false)
            val shouldShow = hasAnySession && (!isTimerScreenActive || appIsBackgrounded) && showOverlayPref && !(backgroundServicesSilentMode && appIsBackgrounded)
            if (shouldShow) {
                showOverlay(context)
            } else {
                hideOverlay()
            }
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
        }
    }

    fun recreateOverlayIfExists(context: Context) {
        scope.launch(Dispatchers.Main) {
            if (overlayView != null) {
                hideOverlay()
                showOverlay(context)
            }
        }
    }

    private fun showOverlay(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.d("FocusTimerManager", "Overlay permission not granted")
            return
        }

        // Keep app always on by keeping the foreground service running while OSD is active
        try {
            com.example.service.KeepAliveService.start(context)
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Failed to auto-start KeepAliveService for OSD: ${e.message}")
        }

        if (overlayView != null) {
            updateOverlayTextAndState()
            return
        }

        try {
            val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("floating_timer_size", "large") ?: "large"

            val textSizeVal: Float
            val topBtnSizeDp: Float
            val bottomBtnSizeDp: Float
            val expandedWidthDp: Float
            val compactWidthDp: Float
            when (sizePref) {
                "small" -> {
                    textSizeVal = 18f
                    topBtnSizeDp = 20f
                    bottomBtnSizeDp = 24f
                    expandedWidthDp = 140f
                    compactWidthDp = 80f
                }
                "medium" -> {
                    textSizeVal = 22f
                    topBtnSizeDp = 22f
                    bottomBtnSizeDp = 28f
                    expandedWidthDp = 170f
                    compactWidthDp = 100f
                }
                else -> {
                    textSizeVal = 26f
                    topBtnSizeDp = 24f
                    bottomBtnSizeDp = 32f
                    expandedWidthDp = 200f
                    compactWidthDp = 120f
                }
            }

            val initialWidthDp = if (isOverlayCollapsed) 32f else (if (areOverlayControlsVisible) expandedWidthDp else compactWidthDp)
            
            // Query screen metrics to clamp coordinates dynamically (surviving rotation/foldables)
            val initialDisplayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(initialDisplayMetrics)
            val initialScreenWidth = initialDisplayMetrics.widthPixels
            val initialScreenHeight = initialDisplayMetrics.heightPixels
            val estContainerWidth = dpToPx(context, initialWidthDp)
            val estContainerHeight = dpToPx(context, 80f)

            lastOverlayX = lastOverlayX.coerceIn(0, maxOf(0, initialScreenWidth - estContainerWidth))
            lastOverlayY = lastOverlayY.coerceIn(0, maxOf(0, initialScreenHeight - estContainerHeight))

            val wmLayoutParams = WindowManager.LayoutParams(
                dpToPx(context, initialWidthDp),
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                     @Suppress("DEPRECATION")
                     WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = lastOverlayX
                y = lastOverlayY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipToOutline = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF000000.toInt())
                    cornerRadius = dpToPx(context, 16f).toFloat()
                    setStroke(dpToPx(context, 1f), 0xFF222222.toInt())
                }
                setPadding(dpToPx(context, 6f), dpToPx(context, 4f), dpToPx(context, 6f), dpToPx(context, 4f))
            }

            // Top Row for Close ('✕') and Expand ('⤢')
            val topRow = RelativeLayout(context).apply {
                visibility = if (areOverlayControlsVisible && !isOverlayCollapsed) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(context, 2f)
                }
            }
            tvTopRow = topRow

            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x40FFFFFF.toInt())
                }
                val btnPx = dpToPx(context, topBtnSizeDp)
                layoutParams = RelativeLayout.LayoutParams(btnPx, btnPx).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
                setOnClickListener {
                    closeOverlay()
                }
            }
            tvCloseBtn = closeBtn
            topRow.addView(closeBtn)

            val expandBtn = TextView(context).apply {
                text = "⤢"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x40FFFFFF.toInt())
                }
                val btnPx = dpToPx(context, topBtnSizeDp)
                layoutParams = RelativeLayout.LayoutParams(btnPx, btnPx).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
                setOnClickListener {
                    val isPomoActive = _isTimerRunning.value || (_isPaused.value && !_wasStartedFromStopwatch.value)
                    val isSwActive = _isStopwatchActive.value || (_isPaused.value && _wasStartedFromStopwatch.value)
                    if (isPomoActive) {
                        _isTabFocusTimerSelected.value = true
                    } else if (isSwActive) {
                        _isTabFocusTimerSelected.value = false
                    }

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("SHOW_TIMER_PAGE", true)
                    }
                    context.startActivity(intent)
                }
            }
            tvExpandBtn = expandBtn
            topRow.addView(expandBtn)

            container.addView(topRow)

            // Timer display text view (middle)
            val textView = TextView(context).apply {
                val displaySecs = if (!isFocusPhase.value) {
                    timerSecondsLeft.value
                } else if (isStopwatchActive.value) {
                    stopwatchSeconds.value
                } else if (isTimerRunning.value) {
                    timerSecondsLeft.value
                } else if (!isTabFocusTimerSelected.value) {
                    stopwatchSeconds.value
                } else {
                    timerSecondsLeft.value
                }
                text = formatTime(displaySecs)
                setTextColor(android.graphics.Color.WHITE)
                textSize = textSizeVal
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.BOLD)
                setPadding(dpToPx(context, 4f), dpToPx(context, 2f), dpToPx(context, 4f), dpToPx(context, 2f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }
            tvTimerText = textView
            container.addView(textView)

            // Bottom Row for Pause/Resume and End Session
            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                visibility = if (areOverlayControlsVisible && !isOverlayCollapsed) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(context, 4f)
                }
            }
            tvBottomRow = bottomRow

            val pauseBtn = TextView(context).apply {
                val isRunning = isTimerRunning.value || isStopwatchActive.value
                text = if (isRunning) "❚❚" else "▶"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xFF1565C0.toInt())
                }
                val btnPx = dpToPx(context, bottomBtnSizeDp)
                layoutParams = LinearLayout.LayoutParams(btnPx, btnPx).apply {
                    rightMargin = dpToPx(context, 10f)
                }
                setOnClickListener {
                    val isRunningBefore = isTimerRunning.value || isStopwatchActive.value
                    if (isRunningBefore) {
                        if (isStopwatchActive.value) {
                            pauseStopwatch(context)
                        } else if (isTimerRunning.value) {
                            pauseTimer(context)
                        }
                    } else {
                        if (isTabFocusTimerSelected.value) {
                            startTimer(context)
                        } else {
                            startStopwatch(context)
                        }
                    }
                    updateOverlayTextAndState()
                    showOverlayControls(context)
                }
            }
            tvPauseBtn = pauseBtn
            bottomRow.addView(pauseBtn)

            val endBtn = TextView(context).apply {
                text = "■"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xFF262626.toInt())
                    setStroke(dpToPx(context, 1f), 0xFFFF5252.toInt())
                }
                val btnPx = dpToPx(context, bottomBtnSizeDp)
                layoutParams = LinearLayout.LayoutParams(btnPx, btnPx)
                setOnClickListener {
                    hideOverlayControls(context)

                    val isStopwatch = stopwatchSeconds.value > 0 || isStopwatchActive.value
                    val elapsedSecs = if (isStopwatch) stopwatchSeconds.value else cumulativeSessionFocusSeconds.value

                    if (elapsedSecs > 0) {
                        if (isStopwatch) {
                            resetStopwatch(context, saveSession = true)
                        } else {
                            resetTimer(context, saveSession = true)
                        }
                        Toast.makeText(context, "Session saved successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        if (isStopwatch) {
                            resetStopwatch(context, saveSession = false)
                        } else {
                            resetTimer(context, saveSession = false)
                        }
                        Toast.makeText(context, "Session cancelled. No focus time to save.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            tvEndBtn = endBtn
            bottomRow.addView(endBtn)

            container.addView(bottomRow)

            // Handle collapsed arrow layout
            val arrowText = TextView(context).apply {
                text = "❯"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                visibility = View.GONE
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            tvCollapsedArrow = arrowText
            container.addView(arrowText)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val isPomoActive = _isTimerRunning.value || (_isPaused.value && !_wasStartedFromStopwatch.value)
                    val isSwActive = _isStopwatchActive.value || (_isPaused.value && _wasStartedFromStopwatch.value)
                    if (isPomoActive) {
                        _isTabFocusTimerSelected.value = true
                    } else if (isSwActive) {
                        _isTabFocusTimerSelected.value = false
                    }

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("SHOW_TIMER_PAGE", true)
                    }
                    context.startActivity(intent)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (isOverlayCollapsed) {
                        expandOverlay(context)
                        return true
                    } else {
                        showOverlayControls(context)
                        return true
                    }
                }
            })

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            val onTouchHandler = View.OnTouchListener { _, event ->
                if (gestureDetector.onTouchEvent(event)) {
                    return@OnTouchListener true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = wmLayoutParams.x
                        initialY = wmLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        showOverlayControls(context)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val displayMetrics = android.util.DisplayMetrics()
                        @Suppress("DEPRECATION")
                        wm.defaultDisplay.getMetrics(displayMetrics)
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        val containerWidth = container.width
                        val containerHeight = container.height

                        val rawXVal = initialX + (event.rawX - initialTouchX).toInt()
                        val rawYVal = initialY + (event.rawY - initialTouchY).toInt()

                        wmLayoutParams.x = rawXVal.coerceIn(0, maxOf(0, screenWidth - containerWidth))
                        wmLayoutParams.y = rawYVal.coerceIn(0, maxOf(0, screenHeight - containerHeight))

                        lastOverlayX = wmLayoutParams.x
                        lastOverlayY = wmLayoutParams.y
                        try {
                            wm.updateViewLayout(container, wmLayoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showOverlayControls(context)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)
                        
                        // Dock to edge collapse checks only if dragged
                        if (dx > 10 || dy > 10) {
                            val displayMetrics = android.util.DisplayMetrics()
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getMetrics(displayMetrics)
                            val screenWidth = displayMetrics.widthPixels
                            val containerWidth = container.width

                            val triggerThreshold = 0 // only minimize if forcefully pushed to edge

                            if (wmLayoutParams.x <= triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "left"
                                wmLayoutParams.x = 0
                                updateCollapsedStateViews(context)
                            } else if (wmLayoutParams.x >= screenWidth - containerWidth - triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "right"
                                wmLayoutParams.x = screenWidth - dpToPx(context, 32f) // keep mini handle visible
                                updateCollapsedStateViews(context)
                            } else {
                                isOverlayCollapsed = false
                                overlayCollapsedSide = "none"
                                showOverlayControls(context)
                            }
                        } else {
                            showOverlayControls(context)
                        }
                        lastOverlayX = wmLayoutParams.x
                        lastOverlayY = wmLayoutParams.y

                        try {
                            wm.updateViewLayout(container, wmLayoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                    else -> true
                }
            }

            container.setOnTouchListener(onTouchHandler)
            textView.setOnTouchListener(onTouchHandler)
            arrowText.setOnTouchListener(onTouchHandler)

            wm.addView(container, wmLayoutParams)
            overlayView = container
            updateOverlayTextAndState()
            updateCollapsedStateViews(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverlay3StepVerification(context: Context, elapsedSeconds: Int, isTimer: Boolean) {
        // Disabled: System verification popup suppressed on OSD end session
    }

    private fun expandOverlay(context: Context) {
        isOverlayCollapsed = false
        overlayCollapsedSide = "none"

        val wm = windowManager ?: return
        val container = overlayView as? LinearLayout ?: return

        val displayMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return

        if (lp.x < screenWidth / 2) {
            lp.x = 40
        } else {
            lp.x = screenWidth - container.width - 40
        }
        
        lastOverlayX = lp.x
        lastOverlayY = lp.y

        showOverlayControls(context)

        try {
            wm.updateViewLayout(container, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCollapsedStateViews(context: Context) {
        val timerText = tvTimerText ?: return
        val arrowText = tvCollapsedArrow ?: return
        val container = overlayView as? LinearLayout ?: return
        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return
        val wm = windowManager ?: return

        scope.launch(Dispatchers.Main) {
            val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("floating_timer_size", "large") ?: "large"

            val expandedWidthDp = when (sizePref) {
                "small" -> 140f
                "medium" -> 170f
                else -> 200f
            }
            val compactWidthDp = when (sizePref) {
                "small" -> 80f
                "medium" -> 100f
                else -> 120f
            }

            if (isOverlayCollapsed) {
                timerText.visibility = View.GONE
                tvTopRow?.visibility = View.GONE
                tvBottomRow?.visibility = View.GONE
                arrowText.visibility = View.VISIBLE
                if (overlayCollapsedSide == "left") {
                    arrowText.text = "❯"
                    arrowText.setPadding(dpToPx(context, 10f), dpToPx(context, 12f), dpToPx(context, 6f), dpToPx(context, 12f))
                } else {
                    arrowText.text = "❮"
                    arrowText.setPadding(dpToPx(context, 6f), dpToPx(context, 12f), dpToPx(context, 10f), dpToPx(context, 12f))
                }
                lp.width = dpToPx(context, 32f)
            } else {
                timerText.visibility = View.VISIBLE
                arrowText.visibility = View.GONE

                if (areOverlayControlsVisible) {
                    tvTopRow?.visibility = View.VISIBLE
                    tvBottomRow?.visibility = View.VISIBLE
                    lp.width = dpToPx(context, expandedWidthDp)
                } else {
                    tvTopRow?.visibility = View.GONE
                    tvBottomRow?.visibility = View.GONE
                    lp.width = dpToPx(context, compactWidthDp)
                }
            }
            try {
                wm.updateViewLayout(container, lp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            updateBlinkingAnimations()
        }
    }

    private fun showOverlayControls(context: Context) {
        if (isOverlayCollapsed) return
        areOverlayControlsVisible = true
        updateCollapsedStateViews(context)
        
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = scope.launch(Dispatchers.Main) {
            delay(3000)
            hideOverlayControls(context)
        }
    }

    private fun hideOverlayControls(context: Context) {
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = null
        areOverlayControlsVisible = false
        updateCollapsedStateViews(context)
    }

    private fun hideOverlay() {
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = null
        areOverlayControlsVisible = false
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            tvTimerText = null
            tvCollapsedArrow = null
            tvEndBtn = null
            tvPauseBtn = null
            tvCloseBtn = null
            tvExpandBtn = null
            tvTopRow = null
            tvBottomRow = null
            windowManager = null
        }
    }

    fun closeOverlay() {
        scope.launch(Dispatchers.Main) {
            hideOverlay()
        }
    }

    private fun updateOverlayTextAndState() {
        scope.launch(Dispatchers.Main) {
            overlayView?.let { view ->
                val wm = windowManager
                val lp = view.layoutParams as? WindowManager.LayoutParams
                if (wm != null && lp != null) {
                    val isRunning = isTimerRunning.value || isStopwatchActive.value
                    if (isRunning) {
                        // Continuous sine wave pixel shifting (period = 5 mins, amplitude = 3 pixels)
                        val angle = (2 * Math.PI * (System.currentTimeMillis() % 300000)) / 300000.0
                        val shiftX = Math.round(Math.sin(angle) * 3.0).toInt()
                        val shiftY = Math.round(Math.cos(angle) * 3.0).toInt()
                        lp.x = lastOverlayX + shiftX
                        lp.y = lastOverlayY + shiftY
                    } else {
                        lp.x = lastOverlayX
                        lp.y = lastOverlayY
                    }
                    try {
                        wm.updateViewLayout(view, lp)
                    } catch (e: Exception) {
                        // Ignore view layout updates if view is detached/removed
                    }
                }
            }

            val displaySeconds = if (!isFocusPhase.value) {
                timerSecondsLeft.value // Show break countdown (both Pomodoro and Stopwatch break)
            } else if (isStopwatchActive.value) {
                stopwatchSeconds.value
            } else if (isTimerRunning.value) {
                timerSecondsLeft.value
            } else if (!isTabFocusTimerSelected.value) {
                stopwatchSeconds.value
            } else {
                timerSecondsLeft.value
            }
            tvTimerText?.let { textView ->
                textView.text = formatTime(displaySeconds)
            }
            updateBlinkingAnimations()
            tvPauseBtn?.let { btn ->
                val isRunning = isTimerRunning.value || isStopwatchActive.value
                btn.text = if (isRunning) "❙❙" else "▶"
            }
        }
    }

    private fun updateBlinkingAnimations() {
        val timerText = tvTimerText
        val arrowText = tvCollapsedArrow
        val isBreakActive = !isFocusPhase.value
        val isPausedVal = isPaused.value
        val shouldBlink = isBreakActive || isPausedVal

        // Timer Text blinking
        if (timerText != null) {
            val hasAnimation = timerText.animation != null
            if (shouldBlink) {
                if (!hasAnimation) {
                    val anim = android.view.animation.AlphaAnimation(1.0f, 0.15f).apply {
                        duration = 600
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                    }
                    timerText.startAnimation(anim)
                }
            } else {
                if (hasAnimation) {
                    timerText.clearAnimation()
                }
            }
        }

        // Arrow blinking
        if (arrowText != null) {
            val hasAnimation = arrowText.animation != null
            if (isOverlayCollapsed && shouldBlink) {
                if (!hasAnimation) {
                    val anim = android.view.animation.AlphaAnimation(1.0f, 0.15f).apply {
                        duration = 600
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                    }
                    arrowText.startAnimation(anim)
                }
            } else {
                if (hasAnimation) {
                    arrowText.clearAnimation()
                }
            }
        }
    }

    private fun updateTaskInDatabase(context: Context, task: Task) {
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.taskDao().updateTask(task)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun incrementTodayPomos(context: Context) {
        val next = _todayPomosCount.value + 1
        _todayPomosCount.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("today_pomos_count", next).apply()
    }

    fun decrementTodayPomos(context: Context) {
        val next = maxOf(0, _todayPomosCount.value - 1)
        _todayPomosCount.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("today_pomos_count", next).apply()
    }

    fun addFocusMinutes(context: Context, mins: Int) {
        val next = _totalFocusMinutes.value + mins
        _totalFocusMinutes.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_focus_minutes", next).apply()
    }

    fun clearPendingFocusReview() {
        _pendingFocusReview.value = null
    }

    fun addFocusRecord(context: Context, startTime: String, endTime: String, taskTitle: String, durationMinutes: Int, notes: String = "", durationSeconds: Int = durationMinutes * 60, tag: String = "", id: String = java.util.UUID.randomUUID().toString(), totalBreakMs: Long = 0L): FocusRecord? {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val calcSeconds = if (durationSeconds > 0) durationSeconds else (durationMinutes * 60)
        if (calcSeconds <= 0 && durationMinutes <= 0) {
            Log.w("FocusTimerManager", "Rejecting 0-second focus record addition.")
            return null
        }
        val cappedSeconds = if (calcSeconds > 21600) 21600 else calcSeconds
        val cappedMinutes = if (cappedSeconds > 0) cappedSeconds / 60 else (if (durationMinutes > 360) 360 else durationMinutes)
        val currentEmail = com.example.api.DevicePresenceManager.sanitizeEmail(com.example.api.DynamicCommandManager.activeEmail)
        val record = FocusRecord(startTime, endTime, taskTitle, cappedMinutes, todayStr, notes, cappedSeconds, tag, id = id, totalBreakMs = totalBreakMs, userEmail = currentEmail)
        
        // --- DYNAMIC CAUSALITY GUARD ---
        val sessionStart = verifiedSessionStartMs.value ?: (com.example.util.StableTime.currentTimeMillis() - cappedSeconds * 1000L)
        val maxAllowed = com.example.util.StableTime.currentTimeMillis() - sessionStart
        val newSessionDuration = cappedSeconds * 1000L
        if (newSessionDuration > maxAllowed + 5000L) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                Toast.makeText(context, "You cannot record more focus time than has elapsed since start.", Toast.LENGTH_LONG).show()
            }
            return null
        }

        var updatedList: List<FocusRecord> = emptyList()
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            currentList.add(0, record)
            updatedList = sanitizeRecordsList(currentList)
            updatedList
        }
        saveFocusRecords(context, updatedList)

        // Save to Room Database safely using NonCancellable block
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val db = com.example.data.AppDatabase.getInstance(context)
                    val newDbRecord = com.example.data.FocusRecordEntity(
                        taskTitle = taskTitle,
                        tag = tag,
                        notes = notes,
                        durationSeconds = cappedSeconds,
                        durationMinutes = cappedMinutes,
                        dateString = todayStr,
                        startTime = startTime,
                        endTime = endTime,
                        timestamp = System.currentTimeMillis(),
                        userEmail = currentEmail
                    )
                    db.focusRecordDao().insertRecord(newDbRecord)

                    val sessionStart = verifiedSessionStartMs.value ?: (com.example.util.StableTime.currentTimeMillis() - cappedSeconds * 1000L)
                    val vaultRecord = com.example.data.LocalHistoryVault(
                        record_id = id,
                        userEmail = currentEmail,
                        subject = tag.ifEmpty { "Study" },
                        task_title = taskTitle,
                        start_time_formatted = startTime,
                        end_time_formatted = endTime,
                        duration_formatted = com.example.util.TimeEngine.formatDuration(cappedSeconds * 1000L),
                        start_time_ms = sessionStart,
                        end_time_ms = sessionStart + cappedSeconds * 1000L,
                        total_focus_ms = cappedSeconds * 1000L,
                        date_string = todayStr,
                        is_synced_to_firestore = 0
                    )
                    db.localHistoryVaultDao().insertRecord(vaultRecord)
                    Log.d("FocusTimerManager", "Session safely locked into Room Database and LocalHistoryVault.")

                    val activeEmail = com.example.api.DynamicCommandManager.activeEmail.ifEmpty { currentEmail }
                    if (activeEmail.isNotEmpty()) {
                        com.example.api.WeeklyStatsUpdater.updateWeeklyStats(context, activeEmail, cappedSeconds * 1000L, tag)
                        com.example.api.DevicePresenceManager.updateDeviceFocusStats(context, activeEmail)
                        com.example.api.ArenaLeaderboardEngine.recomputeLeaderboard(activeEmail)
                    }
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Critical failure saving session to Room", e)
                }
            }
        }

        return record
    }

    fun updateFocusRecordById(context: Context, id: String, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 360) 360 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 21600) 21600 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = sanitizeRecordsList(currentList)
                updatedList!!
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun upsertFocusRecordById(context: Context, id: String, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            val cappedMinutes = if (updatedRecord.durationMinutes > 360) 360 else updatedRecord.durationMinutes
            val cappedSeconds = if (updatedRecord.durationSeconds > 21600) 21600 else updatedRecord.durationSeconds
            val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
            if (index != -1) {
                currentList[index] = record
            } else {
                currentList.add(0, record)
            }
            updatedList = sanitizeRecordsList(currentList)
            updatedList!!
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun deleteFocusRecordById(context: Context, id: String) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                currentList.removeAt(index)
                updatedList = sanitizeRecordsList(currentList)
                updatedList!!
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun updateFocusRecord(context: Context, index: Int, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 720) 720 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 43200) 43200 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun deleteFocusRecord(context: Context, index: Int) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                currentList.removeAt(index)
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun loadPeerFocusRecords(context: Context, username: String): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val sanitized = com.example.api.DevicePresenceManager.sanitizeEmail(username)
        val prefix = if (username.contains("@")) username.substringBefore("@") else username
        val lowerKey = username.lowercase().trim()

        val serialized = prefs.getString("peer_focus_records_$username", null)
            ?: prefs.getString("peer_focus_records_$sanitized", null)
            ?: prefs.getString("peer_focus_records_$prefix", null)
            ?: prefs.getString("peer_focus_records_$lowerKey", null)
            ?: return emptyList()

        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    val breakMsValue = if (parts.size >= 10) parts[9].toLongOrNull() ?: 0L else 0L
                    FocusRecord(parts[0], parts[1], parts[2], originalMins, dateValue, notesValue, originalSecs, tagValue, idValue, totalBreakMs = breakMsValue)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePeerFocusRecords(context: Context, username: String, list: List<FocusRecord>) {
        val serialized = list.joinToString("\n") { 
            val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
            "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}" 
        }
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val sanitized = com.example.api.DevicePresenceManager.sanitizeEmail(username)
        val prefix = if (username.contains("@")) username.substringBefore("@") else username
        val lowerKey = username.lowercase().trim()

        val editor = prefs.edit()
        editor.putString("peer_focus_records_$username", serialized)
        if (sanitized.isNotEmpty()) editor.putString("peer_focus_records_$sanitized", serialized)
        if (prefix.isNotEmpty()) editor.putString("peer_focus_records_$prefix", serialized)
        if (lowerKey.isNotEmpty()) editor.putString("peer_focus_records_$lowerKey", serialized)
        editor.apply()
    }

    fun saveFocusRecords(context: Context, list: List<FocusRecord>) {
        synchronized(recordLock) {
            val serialized = list.joinToString("\n") { 
                val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
                "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}|${it.totalBreakMs}" 
            }
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("focus_records_list", serialized).apply()
        }

        // Automatic Firebase Sync - Push revised total focus and records
        // Deleted to prevent writing legacy and deleted root-level properties to Firebase.

        // Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    try {
                        GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                    }
                }
            }
        }
    }

    fun sanitizeRecordsList(list: List<FocusRecord>): List<FocusRecord> {
        val nonZeroList = list.filter { it.durationSeconds > 0 || it.durationMinutes > 0 }
        // Step 0: Deduplicate duplicate/overlapping records representing the same focus session
        val deduplicated = mutableListOf<FocusRecord>()
        for (record in nonZeroList) {
            val recordStartClean = record.startTime.take(8)
            val recordEndClean = record.endTime.take(8)
            val existingIndex = deduplicated.indexOfFirst { existing ->
                existing.id == record.id ||
                (existing.dateString == record.dateString &&
                 (existing.startTime == record.startTime ||
                  existing.startTime.take(8) == recordStartClean ||
                  (existing.endTime.take(8) == recordEndClean && kotlin.math.abs(existing.durationSeconds - record.durationSeconds) <= 15) ||
                  (kotlin.math.abs(existing.durationSeconds - record.durationSeconds) <= 10 && existing.startTime.take(5) == recordStartClean.take(5))))
            }
            if (existingIndex >= 0) {
                val existing = deduplicated[existingIndex]
                val title = when {
                    existing.taskTitle.isNotBlank() && existing.taskTitle != "General Focus" && existing.taskTitle != "Focus Session" -> existing.taskTitle
                    record.taskTitle.isNotBlank() && record.taskTitle != "General Focus" && record.taskTitle != "Focus Session" -> record.taskTitle
                    existing.taskTitle.isNotBlank() -> existing.taskTitle
                    else -> record.taskTitle.ifEmpty { "Focus Session" }
                }
                val tag = existing.tag.ifBlank { record.tag }
                val start = if (existing.startTime.length <= 8) existing.startTime else existing.startTime.take(8)
                val end = if (existing.endTime.length <= 8) existing.endTime else existing.endTime.take(8)
                deduplicated[existingIndex] = existing.copy(
                    taskTitle = title,
                    tag = tag,
                    startTime = start,
                    endTime = end
                )
            } else {
                val start = if (record.startTime.length > 8 && record.startTime.contains(":")) record.startTime.take(8) else record.startTime
                val end = if (record.endTime.length > 8 && record.endTime.contains(":")) record.endTime.take(8) else record.endTime
                deduplicated.add(record.copy(startTime = start, endTime = end))
            }
        }

        // 1. Cap each record to per-session limit of 6 hours (360 minutes, 21600 seconds)
        val sessionCapped = deduplicated.map {
            var changed = false
            var mins = it.durationMinutes
            var secs = it.durationSeconds
            if (mins > 360) {
                mins = 360
                changed = true
            }
            if (secs > 21600) {
                secs = 21600
                changed = true
            }
            if (changed) {
                it.copy(durationMinutes = mins, durationSeconds = secs)
            } else {
                it
            }
        }

        // 2. Cap per-day total to daily limit of 20 hours (1200 minutes, 72000 seconds)
        val groupedByDate = sessionCapped.groupBy { it.dateString }
        val finalSanitizedList = mutableListOf<FocusRecord>()

        for ((date, records) in groupedByDate) {
            val totalSecs = records.sumOf { it.durationSeconds }
            if (totalSecs > 72000) {
                var accumulatedSecs = 0
                var accumulatedMins = 0
                records.forEachIndexed { index, record ->
                    if (index == records.lastIndex) {
                        val remainingSecs = 72000 - accumulatedSecs
                        val remainingMins = 1200 - accumulatedMins
                        if (remainingSecs > 0) {
                            finalSanitizedList.add(record.copy(
                                durationMinutes = maxOf(1, remainingMins),
                                durationSeconds = maxOf(1, remainingSecs)
                            ))
                        }
                    } else {
                        val fraction = record.durationSeconds.toDouble() / totalSecs
                        val targetSecs = maxOf(1, (fraction * 72000).toInt())
                        val targetMins = maxOf(1, (fraction * 1200).toInt())
                        accumulatedSecs += targetSecs
                        accumulatedMins += targetMins
                        finalSanitizedList.add(record.copy(
                            durationMinutes = targetMins,
                            durationSeconds = targetSecs
                        ))
                    }
                }
            } else {
                finalSanitizedList.addAll(records)
            }
        }

        val orderMap = list.withIndex().associate { it.value.id to it.index }
        return finalSanitizedList.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    fun reloadFocusRecordsFromDb(context: Context) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.example.data.AppDatabase.getInstance(context)
                val dbHistory = db.localHistoryVaultDao().getAllHistoryDirect()
                val currentEmail = com.example.api.DevicePresenceManager.sanitizeEmail(com.example.api.DynamicCommandManager.activeEmail)
                val filteredHistory = if (currentEmail.isNotEmpty()) {
                    dbHistory.filter { it.userEmail.isEmpty() || com.example.api.DevicePresenceManager.sanitizeEmail(it.userEmail) == currentEmail }
                } else dbHistory

                val mapped = filteredHistory.filter { it.total_focus_ms > 0L }.map { record ->
                    val sec = (record.total_focus_ms / 1000).toInt()
                    val min = (record.total_focus_ms / 1000 / 60).toInt()
                    FocusRecord(
                        startTime = record.start_time_formatted,
                        endTime = record.end_time_formatted,
                        taskTitle = record.task_title ?: "Focus Session",
                        durationMinutes = min,
                        dateString = record.date_string,
                        notes = record.mode,
                        durationSeconds = if (sec > 0) sec else (min * 60),
                        tag = record.subject,
                        id = record.record_id,
                        totalBreakMs = record.total_break_ms,
                        userEmail = record.userEmail
                    )
                }.filter { it.durationSeconds > 0 || it.durationMinutes > 0 }
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val todayRecords = filteredHistory.filter { it.date_string == todayStr }
                val todayMs = todayRecords.sumOf { it.total_focus_ms }

                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val savedAdoptedDate = if (currentEmail.isNotEmpty()) prefs.getString("adopted_today_date_${currentEmail}", "") else ""
                val adoptedMs = if (savedAdoptedDate == todayStr && currentEmail.isNotEmpty()) {
                    prefs.getLong("adopted_today_ms_${currentEmail}", 0L)
                } else 0L

                val totalMins = ((todayMs + adoptedMs) / 1000 / 60).toInt()
                val totalPomos = todayRecords.size

                withContext(Dispatchers.Main) {
                    _focusRecords.value = mapped
                    _totalFocusMinutes.value = totalMins
                    _todayPomosCount.value = totalPomos
                }
                prefs.edit()
                    .putInt("total_focus_minutes", totalMins)
                    .putInt("today_pomos_count", totalPomos)
                    .apply()
                saveFocusRecords(context, mapped)
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error reloading focus records from DB", e)
            }
        }
    }

    fun loadFocusRecords(context: Context): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("focus_records_list", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            val list = serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    val breakMsValue = if (parts.size >= 10) parts[9].toLongOrNull() ?: 0L else 0L
                    FocusRecord(parts[0], parts[1], parts[2], originalMins, dateValue, notesValue, originalSecs, tagValue, idValue, totalBreakMs = breakMsValue)
                } else null
            }

            val sanitized = sanitizeRecordsList(list)
            val totalOriginalMins = list.sumOf { it.durationMinutes }
            val totalSanitizedMins = sanitized.sumOf { it.durationMinutes }
            val diffMins = totalOriginalMins - totalSanitizedMins

            if (sanitized != list || diffMins > 0) {
                saveFocusRecords(context, sanitized)
                if (diffMins > 0) {
                    val currentTotal = _totalFocusMinutes.value
                    val newTotal = maxOf(0, currentTotal - diffMins)
                    _totalFocusMinutes.value = newTotal
                    prefs.edit().putInt("total_focus_minutes", newTotal).apply()
                }
                sanitized
            } else {
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun parseTimeToHourFraction(timeStr: String): Double? {
        return try {
            val cleanStr = timeStr.trim().uppercase()
            val hasPm = cleanStr.contains("PM")
            val hasAm = cleanStr.contains("AM")
            
            val digitsPart = cleanStr.replace("PM", "").replace("AM", "").trim()
            val hms = digitsPart.split(":")
            if (hms.isEmpty()) return null
            
            var hour = hms[0].toIntOrNull() ?: 0
            val min = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
            
            if (hasPm && hour < 12) {
                hour += 12
            } else if (hasAm && hour == 12) {
                hour = 0
            }
            
            hour + min / 60.0
        } catch (e: Exception) {
            null
        }
    }

    fun getRecordStartAndEndMs(record: FocusRecord, targetDateStr: String? = null): Pair<Long, Long>? {
        val recDateStr = if (record.dateString.isNotEmpty()) record.dateString else (targetDateStr ?: "")
        if (recDateStr.isEmpty()) return null

        val sFrac = parseTimeToHourFraction(record.startTime)
        val eFrac = parseTimeToHourFraction(record.endTime)
        val durationMs = record.durationSeconds * 1000L

        val formats = listOf(
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )

        fun parseDate(str: String): java.util.Date? {
            for (fmt in formats) {
                try {
                    val parser = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                    val d = parser.parse(str)
                    if (d != null) return d
                } catch (_: Exception) {}
            }
            return null
        }

        if (sFrac != null && eFrac != null && eFrac < sFrac) {
            val parsedStart = parseDate("$recDateStr ${record.startTime}")
            val parsedEnd = parseDate("$recDateStr ${record.endTime}")

            if (parsedStart != null && parsedEnd != null) {
                val sTime: Long = parsedStart.time
                val eTime: Long = parsedEnd.time
                if (sTime > eTime) {
                    val endMs = eTime
                    val startMs = endMs - durationMs
                    return Pair(startMs, endMs)
                } else {
                    val startMs = sTime
                    val endMs = startMs + durationMs
                    return Pair(startMs, endMs)
                }
            } else if (parsedStart != null) {
                val startMs: Long = parsedStart.time
                val endMs = startMs + durationMs
                return Pair(startMs, endMs)
            } else if (parsedEnd != null) {
                val endMs: Long = parsedEnd.time
                val startMs = endMs - durationMs
                return Pair(startMs, endMs)
            }
        } else {
            val parsedStart = parseDate("$recDateStr ${record.startTime}")
            if (parsedStart != null) {
                val startMs: Long = parsedStart.time
                val endMs = if (durationMs > 0) startMs + durationMs else {
                    val parsedEnd = parseDate("$recDateStr ${record.endTime}")
                    if (parsedEnd != null) parsedEnd.time else (startMs + durationMs)
                }
                return Pair(startMs, endMs)
            }
        }
        return null
    }

    fun getOverlapSecondsForDate(record: FocusRecord, targetDateStr: String): Int {
        try {
            val isManual = record.id.startsWith("manual_") ||
                    record.notes.contains("MANUAL", ignoreCase = true) ||
                    record.tag.contains("MANUAL", ignoreCase = true)
            if (isManual) {
                if (record.dateString == targetDateStr || record.dateString.isEmpty()) {
                    return record.durationSeconds
                } else {
                    return 0
                }
            }

            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return record.durationSeconds
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis

            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis

            val startAndEnd = getRecordStartAndEndMs(record, targetDateStr)
            if (startAndEnd == null) {
                if (record.dateString == targetDateStr || record.dateString.isEmpty()) {
                    return record.durationSeconds
                }
                return 0
            }

            val (startMs, endMs) = startAndEnd

            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)

            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            if (record.dateString == targetDateStr || record.dateString.isEmpty()) {
                return record.durationSeconds
            }
            return 0
        }
    }

    fun getActiveSessionOverlapSeconds(startMs: Long, targetDateStr: String): Int {
        try {
            val endMs = StableTime.currentTimeMillis()
            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return 0
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis
            
            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)
            
            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            return ((StableTime.currentTimeMillis() - startMs) / 1000).toInt()
        }
    }

    private val auditMutex = kotlinx.coroutines.sync.Mutex()

    fun runBackgroundAuditAndHealing(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            if (!auditMutex.tryLock()) {
                Log.d("FocusAuditHealer", "⚠️ Audit already in progress, skipping redundant execution.")
                return@launch
            }
            try {
                Log.d("FocusAuditHealer", "⚡ Starting Background Audit & Healing Process...")
                val db = com.example.data.AppDatabase.getInstance(appContext)
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                // 1. Verify and heal database record overlaps (LeetCode 56)
                val todayVaultRecords = db.localHistoryVaultDao().getAllHistoryDirect()
                    .filter { it.date_string == todayStr }

                val sorted = todayVaultRecords.sortedBy { it.start_time_ms }
                val merged = mutableListOf<com.example.data.LocalHistoryVault>()
                val prunedRecordIds = mutableListOf<String>()

                if (sorted.isNotEmpty()) {
                    merged.add(sorted[0].copy())
                    for (i in 1 until sorted.size) {
                        val current = sorted[i]
                        val lastMerged = merged.last()

                        val overlapMs = minOf(lastMerged.end_time_ms, current.end_time_ms) - maxOf(lastMerged.start_time_ms, current.start_time_ms)

                        if (overlapMs > 0L || lastMerged.record_id == current.record_id) {
                            // Overlap or duplicate ID detected! Merge them without inflating duration.
                            val newStartTimeMs = minOf(lastMerged.start_time_ms, current.start_time_ms)
                            val newEndTimeMs = maxOf(lastMerged.end_time_ms, current.end_time_ms)
                            val newTotalMs = if (lastMerged.record_id == current.record_id) {
                                maxOf(lastMerged.total_focus_ms, current.total_focus_ms)
                            } else {
                                minOf(
                                    lastMerged.total_focus_ms + current.total_focus_ms,
                                    newEndTimeMs - newStartTimeMs
                                )
                            }

                            val updatedLastMerged = lastMerged.copy(
                                start_time_ms = newStartTimeMs,
                                end_time_ms = newEndTimeMs,
                                total_focus_ms = newTotalMs,
                                duration_formatted = com.example.util.TimeEngine.formatDuration(newTotalMs),
                                start_time_formatted = com.example.util.TimeEngine.formatTimestamp(newStartTimeMs),
                                end_time_formatted = com.example.util.TimeEngine.formatTimestamp(newEndTimeMs),
                                is_synced_to_firestore = 0
                            )
                            merged[merged.size - 1] = updatedLastMerged

                            // Prune the overlapped record
                            prunedRecordIds.add(current.record_id)
                            Log.w("FocusAuditHealer", "Overlap detected between ${lastMerged.record_id} and ${current.record_id}. Pruning.")
                        } else {
                            merged.add(current.copy())
                        }
                    }
                }

                // If overlaps were found, self-heal!
                if (prunedRecordIds.isNotEmpty()) {
                    Log.i("FocusAuditHealer", "Self-healing overlaps: Pruning ${prunedRecordIds.size} records.")
                    for (recordId in prunedRecordIds) {
                        db.localHistoryVaultDao().deleteRecordById(recordId)

                        // Enqueue delete tombstone to Firestore outbox
                        val deletePayload = org.json.JSONObject().apply {
                            put("recordId", recordId)
                            put("dateString", todayStr)
                            put("lastModifiedMs", System.currentTimeMillis())
                        }.toString()

                        val outboxDelete = com.example.data.OutboxQueue(
                            mutation_id = "mut_del_prune_${recordId}_${System.currentTimeMillis()}_android",
                            created_at_ms = System.currentTimeMillis(),
                            routing_target = "FIRESTORE_DIRECT_VAULT",
                            action_type = "DELETE_SESSION",
                            payload_json = deletePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxDelete)
                    }

                    // Save the merged records
                    for (mergedRecord in merged) {
                        db.localHistoryVaultDao().insertRecord(mergedRecord)

                        val archivePayload = org.json.JSONObject().apply {
                            put("recordId", mergedRecord.record_id)
                            put("dateString", mergedRecord.date_string)
                            put("subject", mergedRecord.subject)
                            put("taskTitle", mergedRecord.task_title ?: "General Focus")
                            put("startTimeMs", mergedRecord.start_time_ms)
                            put("endTimeMs", mergedRecord.end_time_ms)
                            put("totalFocusMs", mergedRecord.total_focus_ms)
                            put("totalBreakMs", mergedRecord.total_break_ms)
                            put("pauseCount", mergedRecord.pause_count)
                            put("durationFormatted", mergedRecord.duration_formatted)
                            put("startTimeFormatted", mergedRecord.start_time_formatted)
                            put("endTimeFormatted", mergedRecord.end_time_formatted)
                            put("mode", mergedRecord.mode)
                        }.toString()

                        val outboxArchive = com.example.data.OutboxQueue(
                            mutation_id = "mut_arch_merge_${mergedRecord.record_id}_${System.currentTimeMillis()}_android",
                            created_at_ms = System.currentTimeMillis(),
                            routing_target = "FIRESTORE_DIRECT_VAULT",
                            action_type = "ARCHIVE_SESSION",
                            payload_json = archivePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxArchive)
                    }
                }

                // 2. Synchronize focus_records table in local SQLite with localHistoryVault (complete cleanup)
                db.focusRecordDao().deleteRecordsForDate(todayStr)
                val vaultRecordsInDb = db.localHistoryVaultDao().getAllHistoryDirect().filter { it.date_string == todayStr }

                for (vr in vaultRecordsInDb) {
                    val cleanStart = if (vr.start_time_formatted.length > 8 && vr.start_time_formatted.contains(":")) vr.start_time_formatted.take(8) else vr.start_time_formatted
                    val cleanEnd = if (vr.end_time_formatted.length > 8 && vr.end_time_formatted.contains(":")) vr.end_time_formatted.take(8) else vr.end_time_formatted
                    val newFr = com.example.data.FocusRecordEntity(
                        taskTitle = vr.task_title ?: "General Focus",
                        tag = vr.subject,
                        notes = vr.mode,
                        durationSeconds = (vr.total_focus_ms / 1000).toInt(),
                        durationMinutes = (vr.total_focus_ms / 1000 / 60).toInt(),
                        dateString = vr.date_string,
                        startTime = cleanStart,
                        endTime = cleanEnd,
                        timestamp = vr.start_time_ms
                    )
                    db.focusRecordDao().insertRecord(newFr)
                }

                // 3. Recalculate and commit correct authoritative totals to memory and preferences
                setOptimisticTodayFocusSeconds(null)
                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val activeEmailClean = com.example.api.DevicePresenceManager.sanitizeEmail(com.example.api.DynamicCommandManager.activeEmail)
                val savedAdoptedDate = if (activeEmailClean.isNotEmpty()) prefs.getString("adopted_today_date_${activeEmailClean}", "") else ""
                val adoptedTodayMsVal = if (savedAdoptedDate == todayStr && activeEmailClean.isNotEmpty()) {
                    prefs.getLong("adopted_today_ms_${activeEmailClean}", 0L)
                } else 0L

                setAdoptedTodayMs(adoptedTodayMsVal)

                val healedVaultRecords = db.localHistoryVaultDao().getAllHistoryDirect()
                    .filter { it.date_string == todayStr }
                
                val correctTotalMs = healedVaultRecords.sumOf { it.total_focus_ms } + adoptedTodayMsVal
                val correctTotalMinutes = (correctTotalMs / 1000 / 60).toInt()
                val correctTodayPomosCount = healedVaultRecords.size

                withContext(Dispatchers.Main) {
                    _totalFocusMinutes.value = correctTotalMinutes
                    _todayPomosCount.value = correctTodayPomosCount
                }
                
                prefs.edit()
                    .putInt("total_focus_minutes", correctTotalMinutes)
                    .putInt("today_pomos_count", correctTodayPomosCount)
                    .apply()

                addSystemLog(appContext, "Healer Completed", "STATE_RESTORE", "Corrected totals: mins=$correctTotalMinutes, pomos=$correctTodayPomosCount")

                // 4. Force reload memory list of records to match SQLite perfectly
                reloadFocusRecordsFromDb(appContext)

                // 5. Check cloud backup mismatch & upload to Firebase RTDB and Firestore
                val email = com.example.api.DynamicCommandManager.activeEmail
                if (email.isNotEmpty()) {
                    com.example.api.WeeklyStatsUpdater.updateWeeklyStats(appContext, email, 0L, "")
                    com.example.api.DevicePresenceManager.updateDeviceFocusStats(appContext, email)
                    com.example.api.ArenaLeaderboardEngine.recomputeLeaderboard(email)
                    if (com.example.util.NetworkChecker.isOnline(appContext)) {
                        com.example.api.OutboxDrainer.start(appContext)
                    }
                }
                
                Log.d("FocusAuditHealer", "⚡ Background Audit & Healing Process Completed Successfully.")
            } catch (e: Exception) {
                Log.e("FocusAuditHealer", "Error running background audit and healer", e)
            } finally {
                auditMutex.unlock()
            }
        }
    }

    fun getTodayFocusMinutes(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySeconds = focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
        val adoptedSecs = (adoptedTodayMs.value / 1000).toInt()
        return (completedTodaySeconds + adoptedSecs + 30) / 60
    }

    fun getTodayFocusSeconds(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySeconds = focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
        val adoptedSecs = (adoptedTodayMs.value / 1000).toInt()
        return completedTodaySeconds + adoptedSecs
    }

    fun getFocusSecondsForDaysRange(days: Int?): Long {
        try {
            val records = focusRecords.value
            val todayStr = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                sdf.format(java.util.Date())
            } catch (e: Exception) {
                "2026-07-15"
            }
            
            val limitTimeMs = if (days != null) {
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -days + 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            } else {
                0L
            }
            
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            var sumSecs = 0L
            for (rec in records) {
                val rDateStr = if (rec.dateString.isNotEmpty()) rec.dateString else todayStr
                val rDate = try { sdf.parse(rDateStr) } catch (e: Exception) { null }
                if (rDate != null) {
                    if (days == null || rDate.time >= limitTimeMs) {
                        sumSecs += rec.durationSeconds
                    }
                } else {
                    sumSecs += rec.durationSeconds
                }
            }
            
            val isFocus = isFocusPhase.value
            val cumSecs = (accumulatedSessionTimeMs.value / 1000L).toInt()
            val swSecs = stopwatchSeconds.value
            
            val activeSessionSeconds = if (isFocus && pendingFocusReview.value == null) {
                if (cumSecs > 0) cumSecs else if (swSecs > 0) swSecs else 0
            } else {
                0
            }
            
            val pendingReviewSeconds = pendingFocusReview.value?.let {
                getOverlapSecondsForDate(it, todayStr)
            } ?: 0
            
            sumSecs += activeSessionSeconds + pendingReviewSeconds
            return sumSecs
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Error calculating range focus seconds", e)
            return 0L
        }
    }

    fun loadFocusTags(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val tagsString = prefs.getString("focus_tags_list", "")
        return if (tagsString.isNullOrBlank()) {
            listOf("Work", "Study", "Exercise", "Reading", "Relaxation", "Coding")
        } else {
            tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun saveFocusTags(context: Context, tags: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("focus_tags_list", tags.joinToString(",")).apply()
        _focusTags.value = tags
    }

    /**
     * Auto-heals discrepancies between LocalHistoryVaultDao and FocusRecordDao without losing sessions.
     * Backfills missing sessions into LocalHistoryVault first, then syncs FocusRecords table to match.
     */
    suspend fun autoHealVaultAndFocusRecords(context: Context) {
        try {
            val appContext = context.applicationContext
            val db = AppDatabase.getInstance(appContext)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val focusRecords = db.focusRecordDao().getRecordsForDate(todayStr)
            val vaultRecords = db.localHistoryVaultDao().getAllHistoryDirect().filter { it.date_string == todayStr }

            // 1. Backfill from FocusRecords to LocalHistoryVault if FocusRecords has non-overlapping extra records
            for (fr in focusRecords) {
                val durationMs = fr.durationSeconds * 1000L
                if (durationMs <= 0L) continue
                val startMs = if (fr.timestamp > 0L) fr.timestamp else (System.currentTimeMillis() - durationMs)

                val isAlreadyInVault = vaultRecords.any { vr ->
                    kotlin.math.abs(vr.start_time_ms - startMs) < 10000L ||
                    (vr.total_focus_ms == durationMs && (vr.task_title == fr.taskTitle || vr.subject == fr.tag))
                }

                if (!isAlreadyInVault) {
                    val cleanStart = if (fr.startTime.length > 8 && fr.startTime.contains(":")) fr.startTime.take(8) else fr.startTime
                    val cleanEnd = if (fr.endTime.length > 8 && fr.endTime.contains(":")) fr.endTime.take(8) else fr.endTime
                    val newVaultRecord = com.example.data.LocalHistoryVault(
                        record_id = "vlt_heal_${System.currentTimeMillis()}_${(1000..9999).random()}",
                        date_string = todayStr,
                        subject = fr.tag ?: "General",
                        task_title = fr.taskTitle ?: "General Focus",
                        start_time_ms = startMs,
                        end_time_ms = startMs + durationMs,
                        total_focus_ms = durationMs,
                        total_break_ms = 0L,
                        pause_count = 0,
                        duration_formatted = TimeEngine.formatDuration(durationMs),
                        start_time_formatted = if (cleanStart.isBlank()) TimeEngine.formatTimestamp(startMs) else cleanStart,
                        end_time_formatted = if (cleanEnd.isBlank()) TimeEngine.formatTimestamp(startMs + durationMs) else cleanEnd,
                        mode = fr.notes ?: "POMODORO",
                        is_synced_to_firestore = 0
                    )
                    db.localHistoryVaultDao().insertRecord(newVaultRecord)
                    Log.d("FocusTimerManager", "Auto-healed missing session from FocusRecords to LocalHistoryVault: ${newVaultRecord.record_id}")
                }
            }

            // 2. Refresh vault records after backfill and sync focus_records table to match localHistoryVault
            val refreshedVaultRecords = db.localHistoryVaultDao().getAllHistoryDirect().filter { it.date_string == todayStr && it.total_focus_ms > 0L }
            db.focusRecordDao().deleteRecordsForDate(todayStr)

            for (vr in refreshedVaultRecords) {
                val durSecs = (vr.total_focus_ms / 1000).toInt()
                if (durSecs <= 0) continue
                val cleanStart = if (vr.start_time_formatted.length > 8 && vr.start_time_formatted.contains(":")) vr.start_time_formatted.take(8) else vr.start_time_formatted
                val cleanEnd = if (vr.end_time_formatted.length > 8 && vr.end_time_formatted.contains(":")) vr.end_time_formatted.take(8) else vr.end_time_formatted
                val newFr = com.example.data.FocusRecordEntity(
                    taskTitle = vr.task_title ?: "General Focus",
                    tag = vr.subject,
                    notes = vr.mode,
                    durationSeconds = durSecs,
                    durationMinutes = (durSecs / 60),
                    dateString = vr.date_string,
                    startTime = cleanStart,
                    endTime = cleanEnd,
                    timestamp = vr.start_time_ms
                )
                db.focusRecordDao().insertRecord(newFr)
            }

            // 3. Reload memory lists and update display preferences
            reloadFocusRecordsFromDb(appContext)

            val correctTotalMs = refreshedVaultRecords.sumOf { it.total_focus_ms }
            val correctTotalMinutes = (correctTotalMs / 1000 / 60).toInt()
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("total_focus_minutes", correctTotalMinutes).apply()

        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Error in autoHealVaultAndFocusRecords", e)
        }
    }
}


// ==================== CONSOLIDATED FROM: IntervalMerger.kt ====================
object IntervalMerger {

    data class MergeResult(
        val merged: List<LocalHistoryVault>,
        val trueTotalMs: Long
    )

    data class FocusRecordMergeResult(
        val merged: List<FocusRecord>,
        val trueTotalMs: Long
    )

    /**
     * Robust helper to parse date and time strings into Unix epoch milliseconds.
     */
    fun parseTimeToMillis(dateStr: String, timeStr: String): Long {
        if (timeStr.isBlank()) return 0L
        val cleanDate = if (dateStr.isBlank()) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        } else {
            dateStr.trim()
        }
        val cleanTime = timeStr.trim().uppercase()

        val formats = listOf(
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy-MM-dd h:mm:ss a",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd HH:mm:ss:SSS",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                val date = sdf.parse("$cleanDate $cleanTime")
                if (date != null) return date.time
            } catch (e: Exception) {
                // Ignore and try next
            }
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                val date = sdf.parse("$cleanDate $cleanTime")
                if (date != null) return date.time
            } catch (e: Exception) {
                // Ignore and try next
            }
        }

        // Try raw millisecond timestamp
        timeStr.toLongOrNull()?.let {
            if (it > 1000000000000L) return it
        }

        return 0L
    }

    private fun formatToAmPm(timestampMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    /**
     * LEETCODE 56: INTERVAL MERGER FOR STUDY SESSIONS (LocalHistoryVault)
     * Takes a list of raw session blocks and collapses overlapping timestamps.
     */
    fun mergeOverlappingStudyIntervals(blocks: List<LocalHistoryVault>): MergeResult {
        if (blocks.isEmpty()) return MergeResult(emptyList(), 0L)

        // 1. Sort blocks chronologically by start time
        val sorted = blocks.sortedBy { it.start_time_ms }
        val merged = mutableListOf<LocalHistoryVault>()
        merged.add(sorted[0].copy())

        // 2. Iterate and merge overlapping time spans
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val lastMerged = merged[merged.size - 1]

            // Calculate the overlap duration
            val overlapMs = minOf(lastMerged.end_time_ms, current.end_time_ms) - maxOf(lastMerged.start_time_ms, current.start_time_ms)

            // Check if overlap exists (overlapMs > 0) or duplicate ID
            if (overlapMs > 0L || lastMerged.record_id == current.record_id) {
                // Overlap detected! Merge them.
                val newStartTimeMs = minOf(lastMerged.start_time_ms, current.start_time_ms)
                val newEndTimeMs = maxOf(lastMerged.end_time_ms, current.end_time_ms)
                val newTotalFocusMs = if (lastMerged.record_id == current.record_id) {
                    maxOf(lastMerged.total_focus_ms, current.total_focus_ms)
                } else {
                    minOf(
                        lastMerged.total_focus_ms + current.total_focus_ms,
                        newEndTimeMs - newStartTimeMs
                    )
                }
                
                merged[merged.size - 1] = lastMerged.copy(
                    start_time_ms = newStartTimeMs,
                    end_time_ms = newEndTimeMs,
                    total_focus_ms = newTotalFocusMs,
                    duration_formatted = TimeEngine.formatDuration(newTotalFocusMs),
                    start_time_formatted = TimeEngine.formatTimestamp(newStartTimeMs),
                    end_time_formatted = TimeEngine.formatTimestamp(newEndTimeMs)
                )
                Log.d("IntervalMerger", "Merged overlapping local intervals: ${lastMerged.record_id} and ${current.record_id}")
            } else {
                // No overlap, push as a distinct study interval
                merged.add(current.copy())
            }
        }

        // 3. Calculate true physical milliseconds studied
        var trueTotalMs = 0L
        for (span in merged) {
            trueTotalMs += span.total_focus_ms
        }

        return MergeResult(merged, trueTotalMs)
    }

    /**
     * LEETCODE 56: INTERVAL MERGER FOR FOCUS RECORDS (FocusRecord Remote sync)
     * Helper to clean up duplicates / overlaps on the remote sync node.
     */
    fun mergeOverlappingFocusRecords(records: List<FocusRecord>): FocusRecordMergeResult {
        if (records.isEmpty()) return FocusRecordMergeResult(emptyList(), 0L)

        // Helper to extract timestamps
        class HelperInterval(val record: FocusRecord, var startMs: Long, var endMs: Long)

        val helperIntervals = records.map { rec ->
            var endMs = parseTimeToMillis(rec.dateString, rec.endTime)
            if (endMs <= 0L) {
                endMs = parseTimeToMillis(rec.dateString, rec.startTime) + (rec.durationSeconds * 1000L)
            }
            if (endMs <= 0L) {
                endMs = System.currentTimeMillis()
            }
            var startMs = if (rec.durationSeconds > 0) {
                endMs - (rec.durationSeconds * 1000L)
            } else {
                parseTimeToMillis(rec.dateString, rec.startTime)
            }
            if (startMs <= 0L || startMs >= endMs) {
                startMs = endMs - (rec.durationSeconds * 1000L)
            }
            HelperInterval(rec, startMs, endMs)
        }

        // Sort chronologically
        val sorted = helperIntervals.sortedBy { it.startMs }
        val mergedHelpers = mutableListOf<HelperInterval>()
        mergedHelpers.add(sorted[0])

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val lastMerged = mergedHelpers[mergedHelpers.size - 1]

            val overlapMs = minOf(lastMerged.endMs, current.endMs) - maxOf(lastMerged.startMs, current.startMs)

            if (overlapMs > 0L || (lastMerged.record.id.isNotEmpty() && lastMerged.record.id == current.record.id)) {
                lastMerged.startMs = minOf(lastMerged.startMs, current.startMs)
                lastMerged.endMs = maxOf(lastMerged.endMs, current.endMs)
            } else {
                mergedHelpers.add(current)
            }
        }

        // Convert back to FocusRecords
        val mergedRecords = mergedHelpers.map { helper ->
            val calcSecs = ((helper.endMs - helper.startMs) / 1000L).toInt()
            val finalDurationSecs = if (helper.record.durationSeconds > 0 && calcSecs < helper.record.durationSeconds) {
                helper.record.durationSeconds
            } else {
                calcSecs
            }
            val finalDurationMins = finalDurationSecs / 60
            helper.record.copy(
                durationSeconds = finalDurationSecs,
                durationMinutes = finalDurationMins,
                startTime = formatToAmPm(helper.startMs),
                endTime = formatToAmPm(helper.endMs)
            )
        }

        var trueTotalMs = 0L
        for (helper in mergedHelpers) {
            trueTotalMs += (helper.record.durationSeconds * 1000L)
        }

        return FocusRecordMergeResult(mergedRecords, trueTotalMs)
    }
}


// ==================== CONSOLIDATED FROM: FocusSessionDbHelper.kt ====================
object FocusSessionDbHelper {
    private val scope = CoroutineScope(Dispatchers.IO)
    internal val dbMutex = Mutex()

    /**
     * Start Focus (Law) handler
     */
    fun handleStartFocus(context: Context, tag: String = "Study", taskTitle: String = "General Focus", mode: String = "POMODORO") {
        scope.launch {
            dbMutex.withLock {
                val nowMs = TimeEngine.getUniversalTimeMs()
                val nowFormatted = TimeEngine.formatTimestamp(nowMs)
                
                // If we have an existing local session, check for clock skew / causality
                val dbForStart = AppDatabase.getInstance(context)
                val existing = dbForStart.localActiveSessionDao().getActiveSession()
                if (existing != null && nowMs < existing.last_event_ts_ms) {
                    val skewMs = existing.last_event_ts_ms - nowMs
                    Log.w("FocusSessionDbHelper", "Clock skew detected on start ($skewMs ms). Adjusting to event time to preserve active session.")
                }

                val sessionId = "sess_$nowMs"
                com.example.api.DynamicCommandManager.activeSessionId = sessionId
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putString("active_session_id_rtdb", sessionId).apply()
                
                val activeSession = LocalActiveSession(
                    session_id = sessionId,
                    status = "FOCUSING",
                    mode = mode,
                    tag = tag,
                    task_title = taskTitle,
                    base_focus_time_ms = 0L,
                    base_break_time_ms = 0L,
                    last_event_ts_ms = nowMs,
                    base_focus_formatted = "00:00:00",
                    last_event_formatted = nowFormatted,
                    is_current_leader = 1
                )

                val timerDurationVal = com.example.util.PrefsDataStore.getInt(context, "timer_duration", 25)
                val breakDurationVal = com.example.util.PrefsDataStore.getInt(context, "break_duration", 5)
                val longBreakDurationVal = com.example.util.PrefsDataStore.getInt(context, "long_break_duration", 15)
                val autoStartBreakVal = com.example.util.PrefsDataStore.getBoolean(context, "timer_autostart_break", true)
                val autoStartPomoVal = com.example.util.PrefsDataStore.getBoolean(context, "timer_autostart_pomo", true)

                val syncSettingsObj = JSONObject().apply {
                    put("syncEnabled", true)
                    put("autoStartBreak", autoStartBreakVal)
                    put("autoStartPomo", autoStartPomoVal)
                    put("focusDurationMins", timerDurationVal)
                    put("breakDurationMins", breakDurationVal)
                    put("longBreakDurationMins", longBreakDurationVal)
                }

                val payload = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("status", "FOCUSING")
                    put("tag", tag)
                    put("taskTitle", taskTitle)
                    put("baseFocusTimeMs", 0L)
                    put("baseFocusTimeFormatted", "00:00:00")
                    put("lastEventTimestampMs", nowMs)
                    put("lastEventFormatted", nowFormatted)
                    put("syncSettings", syncSettingsObj)
                    put("mode", mode)
                }.toString()

                val outboxItem = OutboxQueue(
                    mutation_id = "mut_${nowMs}_android",
                    created_at_ms = nowMs,
                    routing_target = "RTDB_LIVE_SYNC",
                    action_type = "START",
                    payload_json = payload,
                    status = "PENDING"
                )

                try {
                    val db = AppDatabase.getInstance(context)
                    db.withTransaction {
                        // Clean any old active sessions
                        db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                        
                        // Insert active session
                        db.localActiveSessionDao().insertOrUpdateSession(activeSession)
                        
                        // Queue for outbox
                        db.outboxQueueDao().insertQueueItem(outboxItem)
                    }
                    Log.d("FocusSessionDbHelper", "handleStartFocus completed successfully for sessionId: $sessionId")
                } catch (e: Exception) {
                    Log.e("FocusSessionDbHelper", "Error during handleStartFocus transaction", e)
                }
            }
        }
    }

    /**
     * Pause Focus handler
     */
    fun handlePauseFocus(context: Context) {
        scope.launch {
            dbMutex.withLock {
                try {
                    val db = AppDatabase.getInstance(context)
                    val currentSession = db.localActiveSessionDao().getActiveSession()
                    val nowMs = TimeEngine.getUniversalTimeMs()
                    
                    if (currentSession == null) {
                        Log.w("FocusSessionDbHelper", "Focus folder/session is nil on pause! Ending/Wiping session.")
                        val wipePayload = JSONObject().apply {
                            put("status", "IDLE")
                        }.toString()
                        val outboxWipe = OutboxQueue(
                            mutation_id = "mut_wipe_${nowMs}_android",
                            created_at_ms = nowMs,
                            routing_target = "RTDB_LIVE_SYNC",
                            action_type = "WIPE",
                            payload_json = wipePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                        return@withLock
                    }
                    
                    var eventTimeMs = nowMs
                    if (nowMs < currentSession.last_event_ts_ms) {
                        val skewMs = currentSession.last_event_ts_ms - nowMs
                        Log.w("FocusSessionDbHelper", "Clock skew detected on pause ($skewMs ms). Adjusting event time to match last event.")
                        eventTimeMs = currentSession.last_event_ts_ms
                    }

                    if (currentSession.status != "FOCUSING") return@withLock

                    val nowFormatted = TimeEngine.formatTimestamp(eventTimeMs)
                    
                    val liveFocusMs = TimeEngine.calculateLiveElapsedMs(
                        currentSession.base_focus_time_ms,
                        currentSession.last_event_ts_ms,
                        currentSession.status
                    )

                    val updatedSession = currentSession.copy(
                        status = "PAUSED",
                        base_focus_time_ms = liveFocusMs,
                        last_event_ts_ms = eventTimeMs,
                        base_focus_formatted = TimeEngine.formatDuration(liveFocusMs),
                        last_event_formatted = nowFormatted
                    )

                    val payload = JSONObject().apply {
                        put("sessionId", currentSession.session_id)
                        put("status", "PAUSED")
                        put("tag", currentSession.tag)
                        put("taskTitle", currentSession.task_title)
                        put("baseFocusTimeMs", liveFocusMs)
                        put("baseFocusTimeFormatted", TimeEngine.formatDuration(liveFocusMs))
                        put("lastEventTimestampMs", eventTimeMs)
                        put("lastEventFormatted", nowFormatted)
                        put("mode", currentSession.mode)
                    }.toString()

                    val outboxItem = OutboxQueue(
                        mutation_id = "mut_${eventTimeMs}_android",
                        created_at_ms = eventTimeMs,
                        routing_target = "RTDB_LIVE_SYNC",
                        action_type = "PAUSE",
                        payload_json = payload,
                        status = "PENDING"
                    )

                    db.withTransaction {
                        db.localActiveSessionDao().insertOrUpdateSession(updatedSession)
                        db.outboxQueueDao().insertQueueItem(outboxItem)
                    }
                    Log.d("FocusSessionDbHelper", "handlePauseFocus completed successfully for sessionId: ${currentSession.session_id}")
                } catch (e: Exception) {
                    Log.e("FocusSessionDbHelper", "Error during handlePauseFocus transaction", e)
                }
            }
        }
    }

    /**
     * Resume Focus handler
     */
    fun handleResumeFocus(context: Context) {
        scope.launch {
            dbMutex.withLock {
                try {
                    val db = AppDatabase.getInstance(context)
                    val currentSession = db.localActiveSessionDao().getActiveSession()
                    val nowMs = TimeEngine.getUniversalTimeMs()
                    
                    if (currentSession == null) {
                        Log.w("FocusSessionDbHelper", "Focus folder/session is nil on resume! Ending/Wiping session.")
                        val wipePayload = JSONObject().apply {
                            put("status", "IDLE")
                        }.toString()
                        val outboxWipe = OutboxQueue(
                            mutation_id = "mut_wipe_${nowMs}_android",
                            created_at_ms = nowMs,
                            routing_target = "RTDB_LIVE_SYNC",
                            action_type = "WIPE",
                            payload_json = wipePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                        return@withLock
                    }
                    
                    var eventTimeMs = nowMs
                    if (nowMs < currentSession.last_event_ts_ms) {
                        val skewMs = currentSession.last_event_ts_ms - nowMs
                        Log.w("FocusSessionDbHelper", "Clock skew detected on resume ($skewMs ms). Adjusting event time to match last event.")
                        eventTimeMs = currentSession.last_event_ts_ms
                    }

                    if (currentSession.status != "PAUSED" && currentSession.status != "BREAKING") return@withLock

                    val nowFormatted = TimeEngine.formatTimestamp(eventTimeMs)
                    val breakDelta = if (currentSession.last_event_ts_ms > 0 && eventTimeMs > currentSession.last_event_ts_ms) {
                        eventTimeMs - currentSession.last_event_ts_ms
                    } else 0L

                    val updatedSession = currentSession.copy(
                        status = "FOCUSING",
                        base_break_time_ms = currentSession.base_break_time_ms + breakDelta,
                        last_event_ts_ms = eventTimeMs,
                        last_event_formatted = nowFormatted
                    )

                    val payload = JSONObject().apply {
                        put("sessionId", currentSession.session_id)
                        put("status", "FOCUSING")
                        put("tag", currentSession.tag)
                        put("taskTitle", currentSession.task_title)
                        put("baseFocusTimeMs", currentSession.base_focus_time_ms)
                        put("baseFocusTimeFormatted", currentSession.base_focus_formatted)
                        put("lastEventTimestampMs", eventTimeMs)
                        put("lastEventFormatted", nowFormatted)
                        put("mode", currentSession.mode)
                    }.toString()

                    val outboxItem = OutboxQueue(
                        mutation_id = "mut_${eventTimeMs}_android",
                        created_at_ms = eventTimeMs,
                        routing_target = "RTDB_LIVE_SYNC",
                        action_type = "RESUME",
                        payload_json = payload,
                        status = "PENDING"
                    )

                    db.withTransaction {
                        db.localActiveSessionDao().insertOrUpdateSession(updatedSession)
                        db.outboxQueueDao().insertQueueItem(outboxItem)
                    }
                    Log.d("FocusSessionDbHelper", "handleResumeFocus completed successfully for sessionId: ${currentSession.session_id}")
                } catch (e: Exception) {
                    Log.e("FocusSessionDbHelper", "Error during handleResumeFocus transaction", e)
                }
            }
        }
    }

    /**
     * End Focus handler (including 10-Second Short-Circuit Guard in Step 1.4)
     */
    fun handleEndSession(context: Context, onWiped: () -> Unit, onArchived: (LocalHistoryVault) -> Unit) {
        val endMutationId = "end_" + java.util.UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_local_end_mutation_id", endMutationId).apply()
        com.example.api.Firebase.didThisDeviceInitiateEnd = true
        scope.launch {
            dbMutex.withLock {
                try {
                    val db = AppDatabase.getInstance(context)
                    val currentSession = db.localActiveSessionDao().getActiveSession()
                val nowMs = TimeEngine.getUniversalTimeMs()
                
                if (currentSession == null) {
                    Log.w("FocusSessionDbHelper", "Focus folder/session is nil on end! Ending/Wiping session.")
                    val wipePayload = JSONObject().apply {
                        put("status", "IDLE")
                        put("lastMutationId", endMutationId)
                    }.toString()
                    val outboxWipe = OutboxQueue(
                        mutation_id = "mut_wipe_${nowMs}_android",
                        created_at_ms = nowMs,
                        routing_target = "RTDB_LIVE_SYNC",
                        action_type = "WIPE",
                        payload_json = wipePayload,
                        status = "PENDING"
                    )
                    db.outboxQueueDao().insertQueueItem(outboxWipe)
                    withContext(Dispatchers.Main) {
                        onWiped()
                    }
                    return@launch
                }
                
                var eventTimeMs = nowMs
                if (nowMs < currentSession.last_event_ts_ms) {
                    val skewMs = currentSession.last_event_ts_ms - nowMs
                    Log.w("FocusSessionDbHelper", "Clock skew detected on end ($skewMs ms). Adjusting event time to match last event.")
                    eventTimeMs = currentSession.last_event_ts_ms
                }

                val timeline = com.example.api.DynamicCommandManager.currentTimelineFlow.value
                val timelineFocusMs = if (timeline.isNotEmpty()) com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(timeline, "session_end") else 0L
                val timelineBreakMs = if (timeline.isNotEmpty()) com.example.api.TimelineSyncEngine.calculateAccumulatedBreakMs(timeline, "session_end") else 0L
                val timelinePauseMs = if (timeline.isNotEmpty()) com.example.api.TimelineSyncEngine.calculateAccumulatedPauseMs(timeline, "session_end") else 0L

                val finalFocusMs = if (timelineFocusMs > 0L) {
                    timelineFocusMs
                } else {
                    TimeEngine.calculateLiveElapsedMs(
                        currentSession.base_focus_time_ms,
                        currentSession.last_event_ts_ms,
                        currentSession.status
                    )
                }

                val MINIMUM_VALID_MS = 1000L // 1 second

                if (finalFocusMs < MINIMUM_VALID_MS) {
                    Log.w("FocusSessionDbHelper", "Session ignored: Only ${finalFocusMs}ms elapsed (under 1s threshold).")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Session too short to save! (< 1s)", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Queue an RTDB wipe payload
                    val wipePayload = JSONObject().apply {
                        put("sessionId", currentSession.session_id)
                        put("status", "IDLE")
                        put("lastMutationId", endMutationId)
                    }.toString()

                    val outboxWipe = OutboxQueue(
                        mutation_id = "mut_wipe_${nowMs}_android",
                        created_at_ms = nowMs,
                        routing_target = "RTDB_LIVE_SYNC",
                        action_type = "WIPE",
                        payload_json = wipePayload,
                        status = "PENDING"
                    )

                    db.withTransaction {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                    }

                    withContext(Dispatchers.Main) {
                        onWiped()
                    }
                    return@launch
                }

                // If valid (>= 10 seconds), archive!
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
                
                // --- DYNAMIC CAUSALITY GUARD ---
                val sessionStart = FocusTimerManager.extractSessionStartMs(
                    sessionId = currentSession.session_id,
                    lastEventTsMs = currentSession.last_event_ts_ms,
                    baseFocusMs = currentSession.base_focus_time_ms,
                    baseBreakMs = currentSession.base_break_time_ms,
                    timeline = timeline
                )
                val maxAllowedMs = maxOf(0L, TimeEngine.getUniversalTimeMs() - sessionStart)
                if (finalFocusMs > maxAllowedMs + 60000L) {
                    android.util.Log.w("FocusSessionDbHelper", "Causality Guard: Aborting session archival. Session focus duration ($finalFocusMs ms) exceeds max allowed ($maxAllowedMs ms).")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "You cannot record more focus time than has elapsed since start.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    
                    val wipePayload = org.json.JSONObject().apply {
                        put("sessionId", currentSession.session_id)
                        put("status", "IDLE")
                        put("lastMutationId", endMutationId)
                    }.toString()

                    val outboxWipe = com.example.data.OutboxQueue(
                        mutation_id = "mut_wipe_${nowMs}_android",
                        created_at_ms = nowMs,
                        routing_target = "RTDB_LIVE_SYNC",
                        action_type = "WIPE",
                        payload_json = wipePayload,
                        status = "PENDING"
                    )

                    db.withTransaction {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                        db.outboxQueueDao().insertQueueItem(outboxWipe)
                    }

                    withContext(Dispatchers.Main) {
                        onWiped()
                    }
                    return@launch
                }

                val startTimeMs = sessionStart

                val liveBreakMs = if (timelineBreakMs > 0L || timelinePauseMs > 0L) {
                    timelineBreakMs + timelinePauseMs
                } else if (currentSession.status == "PAUSED" || currentSession.status == "BREAKING") {
                    val delta = if (currentSession.last_event_ts_ms > 0 && nowMs > currentSession.last_event_ts_ms) {
                        nowMs - currentSession.last_event_ts_ms
                    } else 0L
                    currentSession.base_break_time_ms + delta
                } else {
                    currentSession.base_break_time_ms
                }

                // Count pauses from timeline
                val pauseCount = if (timeline.isNotEmpty()) {
                    timeline.count { event ->
                        val ev = event.event.lowercase().trim()
                        ev == "pause" || ev == "paused" || ev == "break_started" || ev == "break start"
                    }
                } else 0

                val realEmail = com.example.api.DynamicCommandManager.activeEmail.ifEmpty { prefs.getString("user_email", "") ?: "" }

                val archiveRecord = LocalHistoryVault(
                    record_id = currentSession.session_id,
                    date_string = dateStr,
                    subject = currentSession.tag,
                    task_title = currentSession.task_title,
                    start_time_ms = startTimeMs,
                    end_time_ms = nowMs,
                    total_focus_ms = finalFocusMs,
                    total_break_ms = liveBreakMs,
                    pause_count = pauseCount,
                    duration_formatted = TimeEngine.formatDuration(finalFocusMs),
                    start_time_formatted = TimeEngine.formatTimestamp(startTimeMs),
                    end_time_formatted = TimeEngine.formatTimestamp(nowMs),
                    is_synced_to_firestore = 0,
                    mode = currentSession.mode,
                    userEmail = realEmail
                )

                val cleanStart = TimeEngine.formatTimestamp(startTimeMs).take(8)
                val cleanEnd = TimeEngine.formatTimestamp(nowMs).take(8)
                val frEntity = com.example.data.FocusRecordEntity(
                    taskTitle = currentSession.task_title ?: "Focus Session",
                    tag = currentSession.tag,
                    notes = currentSession.mode,
                    durationSeconds = (finalFocusMs / 1000).toInt(),
                    durationMinutes = (finalFocusMs / 1000 / 60).toInt(),
                    dateString = dateStr,
                    startTime = cleanStart,
                    endTime = cleanEnd,
                    timestamp = startTimeMs
                )

                // Outbox Archive Payload (direct vault or live sync)
                val archivePayload = JSONObject().apply {
                    put("recordId", currentSession.session_id)
                    put("dateString", dateStr)
                    put("subject", currentSession.tag)
                    put("taskTitle", currentSession.task_title)
                    put("startTimeMs", startTimeMs)
                    put("endTimeMs", nowMs)
                    put("totalFocusMs", finalFocusMs)
                    put("totalBreakMs", liveBreakMs)
                    put("pauseCount", pauseCount)
                    put("durationFormatted", TimeEngine.formatDuration(finalFocusMs))
                    put("startTimeFormatted", TimeEngine.formatTimestamp(startTimeMs))
                    put("endTimeFormatted", TimeEngine.formatTimestamp(nowMs))
                    put("lastMutationId", endMutationId)
                    put("mode", currentSession.mode)
                }.toString()

                val outboxArchive = OutboxQueue(
                    mutation_id = "mut_archive_${currentSession.session_id}_android",
                    created_at_ms = nowMs,
                    routing_target = "FIRESTORE_DIRECT_VAULT",
                    action_type = "ARCHIVE_SESSION",
                    payload_json = archivePayload,
                    status = "PENDING"
                )

                val outboxWipe = OutboxQueue(
                    mutation_id = "mut_wipe_${nowMs}_android",
                    created_at_ms = nowMs,
                    routing_target = "RTDB_LIVE_SYNC",
                    action_type = "WIPE",
                    payload_json = JSONObject().apply {
                        put("sessionId", currentSession.session_id)
                        put("status", "IDLE")
                        put("lastMutationId", endMutationId)
                    }.toString(),
                    status = "PENDING"
                )

                // Calculate session XP and excess focus minutes
                try {
                    val sessionMins = finalFocusMs / 60000L
                    val baseEarned = sessionMins / 15
                    val sessionExcess = sessionMins % 15

                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val prevExcess = prefs.getSafeLong("accumulated_excess_minutes", 0L)
                    val newExcess = prevExcess + sessionExcess

                    val edit = prefs.edit()
                    if (newExcess >= 15) {
                        val extraXp = (newExcess / 15).toInt()
                        val remainingExcess = newExcess % 15
                        val prevExtraXp = prefs.getInt("extra_credits_xp", 0)
                        edit.putInt("extra_credits_xp", prevExtraXp + extraXp)
                        edit.putLong("accumulated_excess_minutes", remainingExcess)
                        com.example.api.FocusLogManager.logEvent(
                            context,
                            "Session of $sessionMins mins ended (Gained $baseEarned base XP). Excess focus accumulator reached $newExcess mins! Gained $extraXp extra XP (credit) added separately. Remaining excess: $remainingExcess mins."
                        )
                    } else {
                        edit.putLong("accumulated_excess_minutes", newExcess)
                        com.example.api.FocusLogManager.logEvent(
                            context,
                            "Session of $sessionMins mins ended (Gained $baseEarned base XP). $sessionExcess mins added to excess accumulator (Current excess: $newExcess mins)."
                        )
                    }
                    edit.apply()
                } catch (e: Exception) {
                    Log.e("FocusSessionDbHelper", "Error in calculating/logging session XP and excess", e)
                }

                db.withTransaction {
                    // Wipe active session scratchpad
                    db.openHelper.writableDatabase.execSQL("DELETE FROM local_active_session")
                    // Clear prior pending start/pause/resume RTDB live sync items (redundant now that session has ended)
                    db.outboxQueueDao().clearRtdbLiveSyncItems()
                    // Insert into local history vault
                    db.localHistoryVaultDao().insertRecord(archiveRecord)
                    // Insert into focus records
                    db.focusRecordDao().insertRecord(frEntity)
                    // Queue outbox items
                    db.outboxQueueDao().insertQueueItem(outboxArchive)
                    db.outboxQueueDao().insertQueueItem(outboxWipe)
                }

                // Update local device upload status and record timestamp to trigger 10-second cooldown
                prefs.edit()
                    .putString("local_device_upload_status", "PENDING")
                    .putLong("last_saved_session_timestamp", System.currentTimeMillis())
                    .apply()

                FocusTimerManager.reloadFocusRecordsFromDb(context)
                if (realEmail.isNotEmpty()) {
                    try {
                        com.example.api.WeeklyStatsUpdater.updateWeeklyStats(context, realEmail, finalFocusMs, currentSession.tag)
                        com.example.api.DevicePresenceManager.updateDeviceFocusStats(context, realEmail)
                        com.example.api.DevicePresenceManager.recheckTodayFocusMsWithSystem(context, realEmail)
                        com.example.api.ArenaLeaderboardEngine.recomputeLeaderboard(realEmail)
                    } catch (e: Exception) {
                        Log.e("FocusSessionDbHelper", "Error updating device focus stats at session end", e)
                    }
                }
                if (com.example.util.NetworkChecker.isOnline(context)) {
                    com.example.api.OutboxDrainer.start(context)
                }

                withContext(Dispatchers.Main) {
                    onArchived(archiveRecord)
                }
            } catch (e: Exception) {
                Log.e("FocusSessionDbHelper", "Error during handleEndSession transaction", e)
            }
            }
        }
    }
}


// ==================== CONSOLIDATED FROM: FocusReconciliationEngine.kt ====================
object FocusReconciliationEngine {
    private const val TAG = "FocusReconciliationEngine"
    private val mutex = Mutex()

    suspend fun runReconciliation(context: Context, username: String) {
        if (username.isBlank()) return
        
        mutex.withLock {
            Log.i(TAG, "⚡ EVENT TRIGGERED: Commencing 3-Phase State Reconciliation for user: $username")
            
            val db = AppDatabase.getInstance(context)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            // ==========================================
            // PHASE 1: The Optimistic High-Water Mark (Instant UI Snap)
            // ==========================================
            Log.d(TAG, "[Phase 1] Bypassed lightweight remote metadata fetch in offline-first mode.")
            FocusTimerManager.setOptimisticTodayFocusSeconds(null)

            // ==========================================
            // PHASE 2: The Background Cryptographic & Mathematical Audit
            // ==========================================
            try {
                Log.d(TAG, "[Phase 2] Starting Background Cryptographic & Mathematical Audit...")
                
                val reconPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val reconEmail = reconPrefs.getString("user_email", null) ?: reconPrefs.getString("user_email_${username}", null) ?: username
                if (reconEmail.isNotEmpty()) {
                    try {
                        com.example.api.FirestoreArchiver.pullAndSyncFocusHistoryFromFirestore(context, reconEmail)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error pulling history from Firestore during reconciliation: ${e.message}")
                    }
                }
                
                // 2. LeetCode 56 Interval Merging
                Log.d(TAG, "[Phase 2] Running LeetCode 56 Interval Merging for date: $todayStr...")
                val todayVaultRecords = db.localHistoryVaultDao().getAllHistoryDirect()
                    .filter { it.date_string == todayStr }
                
                // Pre-pass: Heal any corrupt records where start_time_ms is unrealistically far before end_time_ms
                val sanitizedRecords = todayVaultRecords.map { record ->
                    val maxAllowedSpan = record.total_focus_ms + record.total_break_ms + 600000L
                    if (record.end_time_ms > record.start_time_ms && (record.end_time_ms - record.start_time_ms > maxAllowedSpan)) {
                        val correctedStart = maxOf(1L, record.end_time_ms - record.total_focus_ms - record.total_break_ms)
                        val correctedStartFmt = TimeEngine.formatTimestamp(correctedStart)
                        val healed = record.copy(
                            start_time_ms = correctedStart,
                            start_time_formatted = correctedStartFmt
                        )
                        db.localHistoryVaultDao().insertRecord(healed)
                        healed
                    } else {
                        record
                    }
                }

                val sorted = sanitizedRecords.sortedBy { it.start_time_ms }
                val merged = mutableListOf<LocalHistoryVault>()
                val prunedRecordIds = mutableListOf<String>()
                
                if (sorted.isNotEmpty()) {
                    merged.add(sorted[0].copy())
                    for (i in 1 until sorted.size) {
                        val current = sorted[i]
                        val lastMerged = merged.last()
                        
                        val overlapMs = minOf(lastMerged.end_time_ms, current.end_time_ms) - maxOf(lastMerged.start_time_ms, current.start_time_ms)
                        
                        if (overlapMs > 0L || lastMerged.record_id == current.record_id) {
                            // Overlap or duplicate ID detected! Merge them without inflating duration.
                            var newStartTimeMs = minOf(lastMerged.start_time_ms, current.start_time_ms)
                            val newEndTimeMs = maxOf(lastMerged.end_time_ms, current.end_time_ms)
                            val newTotalMs = if (lastMerged.record_id == current.record_id) {
                                maxOf(lastMerged.total_focus_ms, current.total_focus_ms)
                            } else {
                                minOf(
                                    lastMerged.total_focus_ms + current.total_focus_ms,
                                    newEndTimeMs - newStartTimeMs
                                )
                            }
                            val combinedBreakMs = maxOf(lastMerged.total_break_ms, current.total_break_ms)
                            if (newEndTimeMs - newStartTimeMs > newTotalMs + combinedBreakMs + 600000L) {
                                newStartTimeMs = maxOf(lastMerged.start_time_ms, current.start_time_ms)
                                if (newEndTimeMs - newStartTimeMs > newTotalMs + combinedBreakMs + 600000L) {
                                    newStartTimeMs = maxOf(1L, newEndTimeMs - newTotalMs - combinedBreakMs)
                                }
                            }
                            
                            val updatedLastMerged = lastMerged.copy(
                                start_time_ms = newStartTimeMs,
                                end_time_ms = newEndTimeMs,
                                total_focus_ms = newTotalMs,
                                duration_formatted = TimeEngine.formatDuration(newTotalMs),
                                start_time_formatted = TimeEngine.formatTimestamp(newStartTimeMs),
                                end_time_formatted = TimeEngine.formatTimestamp(newEndTimeMs),
                                is_synced_to_firestore = 0
                            )
                            merged[merged.size - 1] = updatedLastMerged
                            
                            // Prune the overlapped record
                            prunedRecordIds.add(current.record_id)
                            Log.w(TAG, "[Phase 2] Overlap detected between ${lastMerged.record_id} and ${current.record_id}. Pruning current.")
                        } else {
                            merged.add(current.copy())
                        }
                    }
                }

                // ==========================================
                // PHASE 3: The Self-Healing Prune & Consensus Lock
                // ==========================================
                Log.d(TAG, "[Phase 3] Initiating Self-Healing Prune & Consensus Lock...")
                
                val focusRecordsInDb = db.focusRecordDao().getRecordsForDate(todayStr)
                // Drop pruned rows locally and generate deleted tombstones to Firestore
                for (recordId in prunedRecordIds) {
                    db.localHistoryVaultDao().deleteRecordById(recordId)
                    
                    val deletePayload = JSONObject().apply {
                        put("recordId", recordId)
                        put("dateString", todayStr)
                        put("lastModifiedMs", System.currentTimeMillis())
                    }.toString()
                    
                    val outboxDelete = OutboxQueue(
                        mutation_id = "mut_del_prune_${recordId}_${System.currentTimeMillis()}_android",
                        created_at_ms = System.currentTimeMillis(),
                        routing_target = "FIRESTORE_DIRECT_VAULT",
                        action_type = "DELETE_SESSION",
                        payload_json = deletePayload,
                        status = "PENDING"
                    )
                    db.outboxQueueDao().insertQueueItem(outboxDelete)
                    Log.d(TAG, "[Phase 3] Pruned $recordId. Delete tombstone enqueued.")
                }

                // Upsert merged/updated records in local history vault
                for (mergedRecord in merged) {
                    if (mergedRecord.is_synced_to_firestore == 0) {
                        db.localHistoryVaultDao().insertRecord(mergedRecord)
                        
                        // Enqueue upsert to Firestore
                        val archivePayload = JSONObject().apply {
                            put("recordId", mergedRecord.record_id)
                            put("dateString", mergedRecord.date_string)
                            put("subject", mergedRecord.subject)
                            put("taskTitle", mergedRecord.task_title ?: "General Focus")
                            put("startTimeMs", mergedRecord.start_time_ms)
                            put("endTimeMs", mergedRecord.end_time_ms)
                            put("totalFocusMs", mergedRecord.total_focus_ms)
                            put("totalBreakMs", mergedRecord.total_break_ms)
                            put("pauseCount", mergedRecord.pause_count)
                            put("durationFormatted", mergedRecord.duration_formatted)
                            put("startTimeFormatted", mergedRecord.start_time_formatted)
                            put("endTimeFormatted", mergedRecord.end_time_formatted)
                            put("mode", mergedRecord.mode)
                        }.toString()

                        val outboxArchive = OutboxQueue(
                            mutation_id = "mut_arch_merge_${mergedRecord.record_id}_${System.currentTimeMillis()}_android",
                            created_at_ms = System.currentTimeMillis(),
                            routing_target = "FIRESTORE_DIRECT_VAULT",
                            action_type = "ARCHIVE_SESSION",
                            payload_json = archivePayload,
                            status = "PENDING"
                        )
                        db.outboxQueueDao().insertQueueItem(outboxArchive)
                        Log.d(TAG, "[Phase 3] Upserted merged record ${mergedRecord.record_id} to DB and enqueued for cloud sync.")
                    } else {
                        Log.d(TAG, "[Phase 3] Merged record ${mergedRecord.record_id} is already synced. Skipping outbox enqueue.")
                    }
                }

                // Synchronize focus_records table in local SQLite with localHistoryVault (complete cleanup)
                db.focusRecordDao().deleteRecordsForDate(todayStr)
                val vaultRecordsInDb = db.localHistoryVaultDao().getAllHistoryDirect().filter { it.date_string == todayStr }

                for (vr in vaultRecordsInDb) {
                    val cleanStart = if (vr.start_time_formatted.length > 8 && vr.start_time_formatted.contains(":")) vr.start_time_formatted.take(8) else vr.start_time_formatted
                    val cleanEnd = if (vr.end_time_formatted.length > 8 && vr.end_time_formatted.contains(":")) vr.end_time_formatted.take(8) else vr.end_time_formatted
                    val newFr = com.example.data.FocusRecordEntity(
                        taskTitle = vr.task_title ?: "General Focus",
                        tag = vr.subject,
                        notes = vr.mode,
                        durationSeconds = (vr.total_focus_ms / 1000).toInt(),
                        durationMinutes = (vr.total_focus_ms / 1000 / 60).toInt(),
                        dateString = vr.date_string,
                        startTime = cleanStart,
                        endTime = cleanEnd,
                        timestamp = vr.start_time_ms
                    )
                    db.focusRecordDao().insertRecord(newFr)
                }

                // Force reload memory list of records to match SQLite perfectly
                FocusTimerManager.reloadFocusRecordsFromDb(context)

                // Clear the High-Water Mark status
                FocusTimerManager.setOptimisticTodayFocusSeconds(null)
                if (reconEmail.isNotEmpty()) {
                    com.example.api.DevicePresenceManager.adoptHighestTodayFocusMsFromOtherDevices(context, reconEmail)
                    com.example.api.WeeklyStatsUpdater.updateWeeklyStats(context, reconEmail, 0L, "")
                    com.example.api.DevicePresenceManager.updateDeviceFocusStats(context, reconEmail)
                    com.example.api.ArenaLeaderboardEngine.recomputeLeaderboard(reconEmail)
                }
                Log.i(TAG, "[Phase 3] High-Water Mark cleared. Authoritative total successfully locked.")
                
                // Trigger outbox drain immediately so changes propagate without delay
                OutboxDrainer.start(context)
                
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during Phase 2 or Phase 3 of reconciliation", e)
                // Safeguard: always clear HWM in case of error to avoid stuck optimistic total
                FocusTimerManager.setOptimisticTodayFocusSeconds(null)
            }
        }
    }
}
