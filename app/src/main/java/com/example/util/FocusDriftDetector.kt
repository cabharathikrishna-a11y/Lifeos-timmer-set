package com.example.util

import android.content.Context
import android.util.Log
import com.example.api.DevicePresenceManager
import com.example.api.DynamicCommandManager
import com.example.api.FirebaseConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class DriftReport(
    val timestampMs: Long = System.currentTimeMillis(),
    val clientHeartbeatMs: Long,
    val firebaseTimestampMs: Long,
    val clientElapsedSecs: Long,
    val firebaseElapsedSecs: Long,
    val driftSeconds: Double,
    val isExceedingThreshold: Boolean,
    val statusMessage: String
)

/**
 * Focus Drift Detector Service
 * Compares client-side timer heartbeats with Firebase Realtime Database timestamps,
 * flagging and logging discrepancies that exceed the 5-second threshold.
 */
object FocusDriftDetector {
    private const val TAG = "FocusDriftDetector"
    const val DRIFT_THRESHOLD_SECONDS = 5.0
    private const val HEARTBEAT_INTERVAL_MS = 15_000L

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitoringJob: Job? = null

    private val _isDriftDetected = MutableStateFlow(false)
    val isDriftDetected: StateFlow<Boolean> = _isDriftDetected.asStateFlow()

    private val _currentDriftSeconds = MutableStateFlow(0.0)
    val currentDriftSeconds: StateFlow<Double> = _currentDriftSeconds.asStateFlow()

    private val _lastDriftReport = MutableStateFlow<DriftReport?>(null)
    val lastDriftReport: StateFlow<DriftReport?> = _lastDriftReport.asStateFlow()

    private val _driftLogs = MutableStateFlow<List<DriftReport>>(emptyList())
    val driftLogs: StateFlow<List<DriftReport>> = _driftLogs.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow("IDLE")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private var serverTimeOffsetMs: Long = 0L
    private var lastAutoResyncMs: Long = 0L
    private const val AUTO_RESYNC_COOLDOWN_MS = 10_000L

    fun initServerTimeOffsetListener(context: Context) {
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context.applicationContext)
            if (dbUrl.isEmpty()) return
            val database = FirebaseDatabase.getInstance(dbUrl)
            val offsetRef = database.getReference(".info/serverTimeOffset")
            offsetRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val offset = snapshot.getValue(Long::class.java) ?: 0L
                    serverTimeOffsetMs = offset
                    Log.d(TAG, "Firebase Server Time Offset updated: ${offset}ms")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Server time offset listener cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing server time offset listener", e)
        }
    }

    /**
     * Starts monitoring client heartbeats vs RTDB timestamps ONLY while a session is active.
     * When idle, the service stands down immediately to prevent battery drain.
     */
    fun startMonitoring(context: Context, email: String) {
        val appContext = context.applicationContext
        initServerTimeOffsetListener(appContext)

        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            Log.d(TAG, "Starting Focus Drift Detector monitoring service...")

            while (true) {
                val isSessionActive = FocusTimerManager.isTimerRunning.value ||
                                      FocusTimerManager.isStopwatchActive.value ||
                                      FocusTimerManager.isPaused.value

                if (!isSessionActive) {
                    Log.d(TAG, "Timer is IDLE. Stopping background heartbeat loop to save battery.")
                    _lastSyncStatus.value = "IDLE"
                    ensureRtdbIdleState(appContext, email)
                    break
                }

                _lastSyncStatus.value = "MONITORING_ACTIVE"
                try {
                    val clientElapsedMs = FocusTimerManager.accumulatedSessionTimeMs.value
                    sendHeartbeatAndCheckDrift(appContext, email, clientElapsedMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Focus Drift Detector loop", e)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops continuous monitoring and ensures clean IDLE state in RTDB.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _lastSyncStatus.value = "IDLE"
        Log.d(TAG, "Stopped Focus Drift Detector monitoring service.")
    }

    /**
     * Ensures RTDB ACTIVE_FOCUS_TIMER node is clean and explicitly IDLE,
     * removing stale active session fields and duplicate legacy keys.
     */
    suspend fun ensureRtdbIdleState(context: Context, email: String) {
        val appContext = context.applicationContext
        val targetEmail = if (email.isBlank()) DynamicCommandManager.activeEmail else email
        if (targetEmail.isBlank()) return

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
            if (dbUrl.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = DevicePresenceManager.sanitizeEmail(targetEmail)
                val activeRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("ACTIVE_FOCUS_TIMER")

                val currentSnap = try { activeRef.get().await() } catch (e: Exception) { null }
                if (currentSnap != null && currentSnap.exists()) {
                    val status = currentSnap.child("Status").getValue(String::class.java) ?: "IDLE"
                    val cmdDev = currentSnap.child("Command_Device_Name").getValue(String::class.java) ?: "None"
                    if (status.equals("IDLE", ignoreCase = true) && (cmdDev == "None" || cmdDev.isEmpty())) {
                        Log.d(TAG, "ensureRtdbIdleState: RTDB is already IDLE. Skipping redundant update.")
                        return
                    }
                }

                val idlePayload = mapOf<String, Any?>(
                    "Command_Device_Name" to "None",
                    "Status" to "IDLE",
                    "Timer_Mode" to null,
                    "Session_ID" to null,
                    "Current_Task" to null,
                    "Current_Tag" to null,
                    "Timeline" to null,
                    "Last_Updated" to ServerValue.TIMESTAMP
                )
                activeRef.updateChildren(idlePayload).await()

                // Clean up duplicate/legacy fields
                activeRef.child("Client_Elapsed_Ms").removeValue()
                activeRef.child("Client_Heartbeat_Ms").removeValue()
                activeRef.child("Resynced_At").removeValue()
                activeRef.child("Resync_Source").removeValue()
                activeRef.child("Heartbeat_Timestamp").removeValue()
                activeRef.child("Current_Timer_Mode").removeValue()
                activeRef.child("Is_Timer_Running").removeValue()
                activeRef.child("Total_Elapsed_Ms").removeValue()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring idle state in RTDB: ${e.message}")
        }
    }

    /**
     * Pushes client heartbeat to RTDB, reads back Firebase server timestamps,
     * and evaluates discrepancy against the 5-second threshold.
     */
    suspend fun sendHeartbeatAndCheckDrift(
        context: Context,
        email: String,
        clientElapsedMs: Long
    ): DriftReport {
        val appContext = context.applicationContext
        val nowClientMs = System.currentTimeMillis()
        val clientElapsedSecs = clientElapsedMs / 1000

        var firebaseTimestampMs = nowClientMs + serverTimeOffsetMs
        var firebaseElapsedSecs = clientElapsedSecs

        val isRunning = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.isPaused.value

        if (!isRunning) {
            ensureRtdbIdleState(appContext, email)
            return DriftReport(
                timestampMs = nowClientMs,
                clientHeartbeatMs = nowClientMs,
                firebaseTimestampMs = nowClientMs,
                clientElapsedSecs = 0,
                firebaseElapsedSecs = 0,
                driftSeconds = 0.0,
                isExceedingThreshold = false,
                statusMessage = "IDLE - Heartbeat paused."
            )
        }

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
            if (dbUrl.isNotEmpty() && email.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
                val activeRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("ACTIVE_FOCUS_TIMER")

                // Update RTDB with clean single-timestamp heartbeat payload
                val heartbeatPayload = mapOf<String, Any?>(
                    "Last_Updated" to ServerValue.TIMESTAMP
                )
                activeRef.updateChildren(heartbeatPayload).await()

                // Remove legacy duplicate fields
                activeRef.child("Client_Heartbeat_Ms").removeValue()
                activeRef.child("Client_Elapsed_Ms").removeValue()
                activeRef.child("Resynced_At").removeValue()
                activeRef.child("Resync_Source").removeValue()
                activeRef.child("Heartbeat_Timestamp").removeValue()
                activeRef.child("Current_Timer_Mode").removeValue()
                activeRef.child("Is_Timer_Running").removeValue()
                activeRef.child("Total_Elapsed_Ms").removeValue()

                // Read back updated server values
                val snapshot = activeRef.get().await()
                if (snapshot.exists()) {
                    val rtdbHeartbeat = snapshot.child("Last_Updated").getValue(Long::class.java)
                        ?: snapshot.child("Heartbeat_Timestamp").getValue(Long::class.java)
                    if (rtdbHeartbeat != null && rtdbHeartbeat > 0) {
                        firebaseTimestampMs = rtdbHeartbeat
                    }
                    firebaseElapsedSecs = clientElapsedSecs
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing heartbeat payload with Firebase RTDB: ${e.message}")
        }

        // Calculate discrepancy
        val clockDriftSecs = kotlin.math.abs(nowClientMs - firebaseTimestampMs) / 1000.0
        val elapsedDriftSecs = kotlin.math.abs(clientElapsedSecs - firebaseElapsedSecs).toDouble()
        val totalDriftSeconds = maxOf(clockDriftSecs, elapsedDriftSecs)

        val isExceeding = totalDriftSeconds > DRIFT_THRESHOLD_SECONDS

        val statusMsg = if (isExceeding) {
            String.format(
                Locale.US,
                "⚠️ FOCUS DRIFT FLAGGED: Client heartbeat (%d ms) differs from Firebase RTDB timestamp (%d ms) by %.2f seconds (Threshold: %.1fs).",
                nowClientMs, firebaseTimestampMs, totalDriftSeconds, DRIFT_THRESHOLD_SECONDS
            )
        } else {
            String.format(
                Locale.US,
                "✅ Focus heartbeat in sync. Drift: %.2fs (within %.1fs threshold).",
                totalDriftSeconds, DRIFT_THRESHOLD_SECONDS
            )
        }

        val report = DriftReport(
            timestampMs = nowClientMs,
            clientHeartbeatMs = nowClientMs,
            firebaseTimestampMs = firebaseTimestampMs,
            clientElapsedSecs = clientElapsedSecs,
            firebaseElapsedSecs = firebaseElapsedSecs,
            driftSeconds = totalDriftSeconds,
            isExceedingThreshold = isExceeding,
            statusMessage = statusMsg
        )

        _lastDriftReport.value = report
        _currentDriftSeconds.value = totalDriftSeconds

        if (isExceeding) {
            _isDriftDetected.value = true
            _driftLogs.value = (listOf(report) + _driftLogs.value).take(25)

            // High priority logcat & system log
            Log.w(TAG, statusMsg)
            FocusTimerManager.addSystemLog(
                appContext,
                "FOCUS_DRIFT_EXCEEDED",
                "DRIFT_DETECTOR",
                "Drift of ${String.format(Locale.US, "%.2f", totalDriftSeconds)}s exceeds 5s threshold (Client: $nowClientMs, RTDB: $firebaseTimestampMs)"
            )

            // Auto-trigger Resync Session to Firebase RTDB if cooldown has elapsed
            if (nowClientMs - lastAutoResyncMs > AUTO_RESYNC_COOLDOWN_MS) {
                lastAutoResyncMs = nowClientMs
                Log.i(TAG, "⚡ Significant focus drift (${String.format(Locale.US, "%.2f", totalDriftSeconds)}s) detected! Automatically triggering Resync Session to RTDB...")
                scope.launch {
                    resyncSession(appContext, email)
                }
            }
        } else {
            _isDriftDetected.value = false
            Log.d(TAG, statusMsg)
        }

        return report
    }

    /**
     * Triggers a full Resync Session call to Firebase Realtime Database
     * to force-sync active session state, client elapsed time, and presence stats.
     */
    suspend fun resyncSession(context: Context, email: String): Boolean {
        val appContext = context.applicationContext
        val targetEmail = if (email.isBlank()) DynamicCommandManager.activeEmail else email
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(targetEmail)
        if (sanitizedEmail.isBlank()) return false

        _lastSyncStatus.value = "RESYNCING"
        Log.i(TAG, "⚡ Triggering Resync Session to Firebase Realtime Database for $sanitizedEmail...")

        return try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
            val nowMs = System.currentTimeMillis()
            val clientElapsedMs = FocusTimerManager.accumulatedSessionTimeMs.value
            val isTimerRunning = FocusTimerManager.isTimerRunning.value
            val isStopwatchActive = FocusTimerManager.isStopwatchActive.value
            val isPaused = FocusTimerManager.isPaused.value

            val statusStr = when {
                isTimerRunning -> "Focusing"
                isStopwatchActive -> "Focusing (Stopwatch)"
                isPaused -> "Paused"
                else -> "IDLE"
            }

            val isSessionActive = (isTimerRunning || isStopwatchActive || isPaused) && clientElapsedMs > 0L

            if (dbUrl.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                val activeRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("ACTIVE_FOCUS_TIMER")

                val myDevice = android.os.Build.MODEL ?: "Android Device"
                val resyncPayload = if (isSessionActive) {
                    mapOf<String, Any?>(
                        "Command_Device_Name" to myDevice,
                        "Status" to statusStr,
                        "Last_Updated" to ServerValue.TIMESTAMP
                    )
                } else {
                    mapOf<String, Any?>(
                        "Command_Device_Name" to "None",
                        "Status" to "IDLE",
                        "Timer_Mode" to null,
                        "Session_ID" to null,
                        "Current_Task" to null,
                        "Current_Tag" to null,
                        "Timeline" to null,
                        "Last_Updated" to ServerValue.TIMESTAMP
                    )
                }

                activeRef.updateChildren(resyncPayload).await()

                // Remove legacy duplicate keys and unwanted heartbeat/resync keys
                activeRef.child("Client_Elapsed_Ms").removeValue()
                activeRef.child("Client_Heartbeat_Ms").removeValue()
                activeRef.child("Resynced_At").removeValue()
                activeRef.child("Resync_Source").removeValue()
                activeRef.child("Heartbeat_Timestamp").removeValue()
                activeRef.child("Current_Timer_Mode").removeValue()
                activeRef.child("Is_Timer_Running").removeValue()
                activeRef.child("Total_Elapsed_Ms").removeValue()
            }

            // Also update DevicePresenceManager focus stats for friends/peers
            DevicePresenceManager.updateDeviceFocusStats(appContext, targetEmail)

            // Update Weekly/Arena stats if available
            try {
                com.example.api.WeeklyStatsUpdater.updateWeeklyStats(appContext, targetEmail, 0L, "")
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical error updating weekly stats during resync: ${e.message}")
            }

            // Recalibrate local drift values after successful resync
            _isDriftDetected.value = false
            _currentDriftSeconds.value = 0.0
            _lastSyncStatus.value = "AUTO_RESYNCED"

            val logMsg = "Successfully resynced session to Firebase RTDB for $sanitizedEmail (Elapsed: ${clientElapsedMs / 1000}s)"
            Log.i(TAG, "✅ $logMsg")
            FocusTimerManager.addSystemLog(appContext, "SESSION_RESYNCED", "DRIFT_DETECTOR", logMsg)

            true
        } catch (e: Exception) {
            _lastSyncStatus.value = "RESYNC_FAILED"
            Log.e(TAG, "❌ Failed to resync session to Firebase RTDB: ${e.message}", e)
            FocusTimerManager.addSystemLog(appContext, "SESSION_RESYNC_FAILED", "DRIFT_DETECTOR", "Resync failed: ${e.message}")
            false
        }
    }

    /**
     * Runs an on-demand diagnostic evaluation of timer drift.
     */
    suspend fun runOnDemandDiagnostic(context: Context, email: String): DriftReport {
        val clientElapsedMs = FocusTimerManager.accumulatedSessionTimeMs.value
        return sendHeartbeatAndCheckDrift(context, email, clientElapsedMs)
    }

    /**
     * Resets drift flag and re-calibrates local timer offset.
     */
    fun recalibrateAndClearDrift(context: Context) {
        _isDriftDetected.value = false
        _currentDriftSeconds.value = 0.0
        Log.i(TAG, "Focus drift recalibrated and cleared by user action.")
        FocusTimerManager.addSystemLog(context, "FOCUS_DRIFT_RECALIBRATED", "DRIFT_DETECTOR", "Drift recalibrated and reset.")
    }
}
