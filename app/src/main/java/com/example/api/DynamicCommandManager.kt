package com.example.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.util.TimeEngine
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import com.example.data.Task

object DynamicCommandManager {
    private const val TAG = "DynamicCommandManager"

    val currentTimelineFlow = kotlinx.coroutines.flow.MutableStateFlow<List<TimelineEvent>>(emptyList())
    val currentStatusFlow = kotlinx.coroutines.flow.MutableStateFlow<String>("IDLE")
    val currentTimerModeFlow = kotlinx.coroutines.flow.MutableStateFlow<String>("pomodoro")

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    var activeEmail: String = ""

    @Volatile
    var activeSessionId: String = ""

    fun initialize(context: Context, email: String) {
        applicationContext = context.applicationContext
        activeEmail = email
        
        // Try to load activeSessionId from SharedPreferences if empty
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        activeSessionId = prefs.getString("active_session_id_rtdb", "") ?: ""

        // Try to recover the existing session timeline from SharedPreferences
        val timelineJson = prefs.getString("session_timeline_json", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(timelineJson)
            val list = mutableListOf<TimelineEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cmd = obj.optString("command", "")
                val ts = obj.optLong("timestamp", 0L)
                if (cmd.isNotEmpty()) {
                    list.add(TimelineEvent(deviceId = com.example.util.DeviceIdProvider.getDeviceId(context), event = cmd, timestamp = ts))
                }
            }
            currentTimelineFlow.value = list
            Log.d(TAG, "Loaded ${list.size} events from session_timeline_json SharedPreferences during initialization.")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing session_timeline_json SharedPreferences in initialize", e)
        }
    }

    fun resetToIdle() {
        currentTimelineFlow.value = emptyList()
        currentStatusFlow.value = "IDLE"
        activeSessionId = ""
        applicationContext?.let { ctx ->
            ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("session_timeline_json")
                .remove("active_session_id_rtdb")
                .apply()

            val email = activeEmail
            if (email.isNotEmpty()) {
                val dbUrl = FirebaseConfig.getDatabaseUrl(ctx)
                if (dbUrl.isNotEmpty()) {
                    try {
                        val sanitized = DevicePresenceManager.sanitizeEmail(email)
                        val database = FirebaseDatabase.getInstance(dbUrl)
                        val activeRef = database.getReference("FOCUS_TIMMER")
                            .child("USER")
                            .child(sanitized)
                            .child("ACTIVE_FOCUS_TIMER")
                        val idlePayload = mapOf<String, Any?>(
                            "Status" to "IDLE",
                            "Command_Device_Name" to "None",
                            "Timer_Mode" to null,
                            "Session_ID" to null,
                            "Current_Task" to null,
                            "Current_Tag" to null,
                            "Timeline" to null,
                            "Last_Updated" to ServerValue.TIMESTAMP
                        )
                        activeRef.updateChildren(idlePayload)
                        activeRef.child("Client_Elapsed_Ms").removeValue()
                        activeRef.child("Client_Heartbeat_Ms").removeValue()
                        activeRef.child("Resynced_At").removeValue()
                        activeRef.child("Resync_Source").removeValue()
                        activeRef.child("Heartbeat_Timestamp").removeValue()
                        activeRef.child("Current_Timer_Mode").removeValue()
                        activeRef.child("Is_Timer_Running").removeValue()
                        activeRef.child("Total_Elapsed_Ms").removeValue()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in resetToIdle RTDB cleanup", e)
                    }
                }
            }
        }
    }

    fun executeMidSessionCommand(
        action: String,
        currentTimeline: List<TimelineEvent>,
        timerMode: String,
        currentTask: String,
        currentTag: String
    ) {
        val context = applicationContext
        val email = activeEmail
        if (context == null || email.isBlank()) {
            Log.e(TAG, "DynamicCommandManager not initialized. Context or Email is missing.")
            return
        }

        if (com.example.util.FocusTimerManager.isPassiveCalibrationInProgress) {
            Log.d(TAG, "executeMidSessionCommand: Passive calibration in progress. Skipping command propagation.")
            return
        }

        val myDevice = com.example.util.DeviceIdProvider.getDeviceId(context)
        val trueTime = com.example.util.StableTime.currentTimeMillis()

        // Normalize and map the action command to standard UPPERCASE big letters
        val mappedAction = when (action.lowercase().trim()) {
            "start" -> "START"
            "resumed", "resume" -> "RESUME"
            "paused", "pause" -> "PAUSE"
            "break_started", "break_start" -> "BREAK START"
            "break_ended", "break_end" -> "BREAK END"
            "end", "completed" -> "END"
            else -> action.uppercase().trim()
        }

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Since we are triggering a local user command action on this device,
        // we automatically assume/claim the commanding role!
        val editor = prefs.edit().putBoolean("is_command_device", true)
        if (mappedAction == "START" || mappedAction == "RESUME") {
            if (timerMode.lowercase() == "stopwatch") {
                editor.putBoolean("timer_is_stopwatch_active", true).putBoolean("timer_is_running", false).putBoolean("is_paused", false)
            } else {
                editor.putBoolean("timer_is_running", true).putBoolean("timer_is_stopwatch_active", false).putBoolean("is_paused", false)
            }
        } else if (mappedAction == "PAUSE") {
            editor.putBoolean("is_paused", true)
        } else if (mappedAction == "END") {
            editor.putBoolean("timer_is_running", false).putBoolean("timer_is_stopwatch_active", false).putBoolean("is_paused", false)
        }
        editor.apply()
        val isLocalCommander = true

        // Create a new TimelineEvent with standard UPPERCASE command
        val newEvent = TimelineEvent(deviceId = myDevice, event = mappedAction, timestamp = trueTime)
        
        // Clean out any duplicate adjacent identical events to keep the timeline clean
        val lastEvent = currentTimeline.lastOrNull()
        val updatedTimeline = if (mappedAction == "START") {
            listOf(newEvent)
        } else if (lastEvent != null && lastEvent.event == newEvent.event && (trueTime - lastEvent.timestamp < 1500)) {
            currentTimeline
        } else {
            currentTimeline + newEvent
        }

        // Generate Session_ID ONLY if action is "START", otherwise pass existing.
        if (mappedAction == "START") {
            activeSessionId = "sess_$trueTime"
            prefs.edit().putString("active_session_id_rtdb", activeSessionId).apply()
        } else if (activeSessionId.isEmpty()) {
            activeSessionId = prefs.getString("active_session_id_rtdb", "") ?: ""
            if (activeSessionId.isEmpty()) {
                activeSessionId = "sess_$trueTime"
                prefs.edit().putString("active_session_id_rtdb", activeSessionId).apply()
            }
        }
        val sessionId = activeSessionId

        // Status mapping:
        val statusStr = when (mappedAction) {
            "START", "RESUME" -> "Focusing"
            "PAUSE" -> "Paused"
            "BREAK START" -> "Break"
            "BREAK END" -> {
                if (com.example.util.FocusTimerManager.isStopwatchActive.value || com.example.util.FocusTimerManager.isTimerRunning.value) {
                    "Focusing"
                } else {
                    "IDLE"
                }
            }
            "END" -> "IDLE"
            else -> "Focusing"
        }

        // Update local state flows
        this.currentTimelineFlow.value = updatedTimeline
        this.currentStatusFlow.value = statusStr
        this.currentTimerModeFlow.value = timerMode

        // Save updated timeline to SharedPreferences so it persists across process death
        try {
            val arr = org.json.JSONArray()
            for (ev in updatedTimeline) {
                val obj = org.json.JSONObject().apply {
                    put("command", ev.event)
                    put("timestamp", ev.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString("session_timeline_json", arr.toString()).apply()
            Log.d(TAG, "Successfully synced updatedTimeline of size ${updatedTimeline.size} to session_timeline_json SharedPreferences.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save updated timeline to prefs", e)
        }

        // Trigger Foreground Service and WakeLock
        if (statusStr == "Focusing" || statusStr == "Paused" || statusStr == "Break") {
            try {
                com.example.service.KeepAliveService.updateNotification(context)
                com.example.util.WakeLockManager.acquire(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update KeepAliveService notification", e)
            }
        } else if (statusStr == "IDLE") {
            try {
                com.example.service.KeepAliveService.updateNotification(context)
                com.example.util.WakeLockManager.release()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop/update KeepAliveService notification", e)
            }
        }

        // If this is not the commanding device, do not write/publish updates to Firebase. This prevents loops!
        if (!isLocalCommander) {
            Log.d(TAG, "executeMidSessionCommand: Device is in Reading Mode (isLocalCommander=false). Skipping writing to Firebase to prevent loops.")
            return
        }

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                Log.e(TAG, "Firebase DB URL is empty.")
                return
            }
            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)

            // Database Path: FOCUS_TIMMER/USER/{sanitizedEmail}/ACTIVE_FOCUS_TIMER
            val activeRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("ACTIVE_FOCUS_TIMER")

            val clientElapsedMs = com.example.util.FocusTimerManager.accumulatedSessionTimeMs.value

            val payload = if (statusStr == "IDLE") {
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
            } else {
                mapOf<String, Any?>(
                    "Command_Device_Name" to myDevice,
                    "Status" to statusStr,
                    "Timer_Mode" to timerMode,
                    "Session_ID" to sessionId,
                    "Current_Task" to currentTask,
                    "Current_Tag" to currentTag,
                    "Timeline" to updatedTimeline,
                    "Last_Updated" to ServerValue.TIMESTAMP
                )
            }

            activeRef.updateChildren(payload).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Clean up legacy duplicate keys and heartbeat/resync keys
                    activeRef.child("Client_Elapsed_Ms").removeValue()
                    activeRef.child("Client_Heartbeat_Ms").removeValue()
                    activeRef.child("Resynced_At").removeValue()
                    activeRef.child("Resync_Source").removeValue()
                    activeRef.child("Heartbeat_Timestamp").removeValue()
                    activeRef.child("Current_Timer_Mode").removeValue()
                    activeRef.child("Is_Timer_Running").removeValue()
                    activeRef.child("Total_Elapsed_Ms").removeValue()
                    Log.d(TAG, "Successfully updated active focus timer payload in RTDB.")
                    // Trigger dynamic focus stats update to sync Todays_Focus_Ms in Realtime Database for peers
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            DevicePresenceManager.updateDeviceFocusStats(context, email)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating device focus stats in executeMidSessionCommand", e)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to update active focus timer payload in RTDB.", task.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing mid session command", e)
        }
    }

    private var activeFocusTimerRef: com.google.firebase.database.DatabaseReference? = null
    private var activeFocusTimerListener: com.google.firebase.database.ValueEventListener? = null

    fun startListeningToActiveFocusTimer(context: Context, email: String) {
        val appContext = context.applicationContext
        val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
        if (dbUrl.isEmpty()) {
            Log.e(TAG, "Firebase DB URL is empty. Cannot start listening to active focus timer.")
            return
        }

        stopListeningToActiveFocusTimer()

        val database = FirebaseDatabase.getInstance(dbUrl)
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        val activeRef = database.getReference("FOCUS_TIMMER")
            .child("USER")
            .child(sanitizedEmail)
            .child("ACTIVE_FOCUS_TIMER")

        activeFocusTimerRef = activeRef

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) return

                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                var isLocalCommander = prefs.getBoolean("is_command_device", true)
                val cmdDevice = snapshot.child("Command_Device_Name").getValue(String::class.java) ?: ""
                val myDevice = com.example.util.DeviceIdProvider.getDeviceId(appContext)

                val rawStatus = snapshot.child("Status").getValue(String::class.java) ?: "IDLE"
                val isRemoteActive = rawStatus.isNotEmpty() &&
                    !rawStatus.equals("IDLE", ignoreCase = true) &&
                    !rawStatus.equals("Relaxing", ignoreCase = true) &&
                    !rawStatus.equals("End", ignoreCase = true) &&
                    !rawStatus.equals("Completed", ignoreCase = true) &&
                    !rawStatus.equals("Ended", ignoreCase = true) &&
                    !rawStatus.equals("Session_End", ignoreCase = true)

                val hasLocalActive = com.example.util.FocusTimerManager.isTimerRunning.value ||
                    com.example.util.FocusTimerManager.isStopwatchActive.value ||
                    com.example.util.FocusTimerManager.isPaused.value ||
                    prefs.getBoolean("timer_is_running", false) ||
                    prefs.getBoolean("timer_is_stopwatch_active", false) ||
                    prefs.getBoolean("is_paused", false)

                // If this update is from ourselves, we skip it to prevent echo loops
                if (cmdDevice == myDevice) {
                    Log.d(TAG, "Local device is the command device ($cmdDevice). Skipping calibration.")
                    return
                }

                // If local device is actively running a timer as commander and remote is IDLE/None, skip calibration to avoid resetting local timer
                if (isLocalCommander && hasLocalActive && !isRemoteActive) {
                    Log.d(TAG, "Local device is actively running timer as commander, remote is IDLE. Skipping calibration.")
                    return
                }

                // If both local device and remote device are IDLE, skip calibration to avoid feedback loops and redundant reset logs
                if (!hasLocalActive && !isRemoteActive) {
                    Log.d(TAG, "Both local and remote are IDLE. Skipping redundant calibration.")
                    return
                }

                // If remote is active from another device, yield commander role to become follower
                if (isRemoteActive && cmdDevice.isNotEmpty() && cmdDevice != "None" && cmdDevice != myDevice) {
                    Log.d(TAG, "Received update from active remote device '$cmdDevice'. Yielding commander role.")
                    prefs.edit().putBoolean("is_command_device", false).apply()
                }

                // If we are a reading device or remote is IDLE/Ended, we sync the timer state live!
                val cleanRaw = rawStatus.lowercase().trim()
                val statusStr = if (cleanRaw == "relaxing" || cleanRaw == "end" || cleanRaw == "completed" || cleanRaw == "ended" || cleanRaw == "session_end") "idle" else rawStatus
                val timerMode = snapshot.child("Timer_Mode").getValue(String::class.java) ?: "pomodoro"
                val currentTask = snapshot.child("Current_Task").getValue(String::class.java) ?: ""
                val currentTag = snapshot.child("Current_Tag").getValue(String::class.java) ?: ""
                val sessionId = snapshot.child("Session_ID").getValue(String::class.java) ?: ""

                // Reconstruct timeline
                val timelineList = mutableListOf<TimelineEvent>()
                val timelineSnapshot = snapshot.child("Timeline")
                if (timelineSnapshot.exists()) {
                    for (child in timelineSnapshot.children) {
                        val evDevice = child.child("deviceId").getValue(String::class.java) ?: ""
                        val evAction = child.child("event").getValue(String::class.java) ?: ""
                        val evTs = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (evAction.isNotEmpty()) {
                            timelineList.add(TimelineEvent(deviceId = evDevice, event = evAction, timestamp = evTs))
                        }
                    }
                }

                Log.d(TAG, "Received active focus timer live update from '$cmdDevice'. Syncing statusStr='$statusStr', timerMode='$timerMode'")
                
                // Update activeSessionId
                activeSessionId = sessionId
                prefs.edit().putString("active_session_id_rtdb", sessionId).apply()

                // Save timeline to session_timeline_json
                try {
                    val arr = org.json.JSONArray()
                    for (ev in timelineList) {
                        val obj = org.json.JSONObject().apply {
                            put("command", ev.event)
                            put("timestamp", ev.timestamp)
                        }
                        arr.put(obj)
                    }
                    prefs.edit().putString("session_timeline_json", arr.toString()).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving timeline JSON", e)
                }

                // Calibrate local state
                calibrateLocalState(appContext, statusStr, timerMode, currentTask, currentTag, timelineList)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Error listening to active focus timer", error.toException())
            }
        }

        activeFocusTimerListener = listener
        activeRef.addValueEventListener(listener)
        Log.d(TAG, "Successfully started listening to active focus timer live updates in Firebase.")
    }

    fun forceReadActiveFocusTimerAndCalibrate(context: Context, email: String) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
        if (dbUrl.isEmpty()) return

        val database = FirebaseDatabase.getInstance(dbUrl)
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        val activeRef = database.getReference("FOCUS_TIMMER")
            .child("USER")
            .child(sanitizedEmail)
            .child("ACTIVE_FOCUS_TIMER")

        activeRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val cmdDevice = snapshot.child("Command_Device_Name").getValue(String::class.java) ?: ""
            val myDevice = com.example.util.DeviceIdProvider.getDeviceId(appContext)

            val rawStatus = snapshot.child("Status").getValue(String::class.java) ?: "IDLE"
            val isRemoteActive = rawStatus.isNotEmpty() &&
                !rawStatus.equals("IDLE", ignoreCase = true) &&
                !rawStatus.equals("Relaxing", ignoreCase = true) &&
                !rawStatus.equals("End", ignoreCase = true) &&
                !rawStatus.equals("Completed", ignoreCase = true) &&
                !rawStatus.equals("Ended", ignoreCase = true) &&
                !rawStatus.equals("Session_End", ignoreCase = true)

            var isLocalCommander = prefs.getBoolean("is_command_device", true)

            val hasLocalActive = com.example.util.FocusTimerManager.isTimerRunning.value ||
                com.example.util.FocusTimerManager.isStopwatchActive.value ||
                com.example.util.FocusTimerManager.isPaused.value ||
                prefs.getBoolean("timer_is_running", false) ||
                prefs.getBoolean("timer_is_stopwatch_active", false) ||
                prefs.getBoolean("is_paused", false)

            if (cmdDevice == myDevice) {
                Log.d(TAG, "forceReadActiveFocusTimerAndCalibrate: Local device is command device ($cmdDevice). Skipping calibration.")
                return@addOnSuccessListener
            }

            if (isLocalCommander && hasLocalActive && !isRemoteActive) {
                Log.d(TAG, "forceReadActiveFocusTimerAndCalibrate: Local device is actively running timer as commander, remote is IDLE. Skipping calibration.")
                return@addOnSuccessListener
            }

            if (!hasLocalActive && !isRemoteActive) {
                Log.d(TAG, "forceReadActiveFocusTimerAndCalibrate: Both local and remote are IDLE. Skipping calibration.")
                return@addOnSuccessListener
            }

            if (isRemoteActive && cmdDevice.isNotEmpty() && cmdDevice != "None" && cmdDevice != myDevice) {
                Log.d(TAG, "forceReadActiveFocusTimerAndCalibrate: Yielding commander role to '$cmdDevice'")
                prefs.edit().putBoolean("is_command_device", false).apply()
            }

            val cleanRawForce = rawStatus.lowercase().trim()
            val statusStr = if (cleanRawForce == "relaxing" || cleanRawForce == "end" || cleanRawForce == "completed" || cleanRawForce == "ended" || cleanRawForce == "session_end") "idle" else rawStatus
            val timerMode = snapshot.child("Timer_Mode").getValue(String::class.java) ?: "pomodoro"
            val currentTask = snapshot.child("Current_Task").getValue(String::class.java) ?: ""
            val currentTag = snapshot.child("Current_Tag").getValue(String::class.java) ?: ""
            val sessionId = snapshot.child("Session_ID").getValue(String::class.java) ?: ""

            val timelineList = mutableListOf<TimelineEvent>()
            val timelineSnapshot = snapshot.child("Timeline")
            if (timelineSnapshot.exists()) {
                for (child in timelineSnapshot.children) {
                    val evDevice = child.child("deviceId").getValue(String::class.java) ?: ""
                    val evAction = child.child("event").getValue(String::class.java) ?: ""
                    val evTs = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (evAction.isNotEmpty()) {
                        timelineList.add(TimelineEvent(deviceId = evDevice, event = evAction, timestamp = evTs))
                    }
                }
            }

            activeSessionId = sessionId
            prefs.edit().putString("active_session_id_rtdb", sessionId).apply()

            try {
                val arr = org.json.JSONArray()
                for (ev in timelineList) {
                    val obj = org.json.JSONObject().apply {
                        put("command", ev.event)
                        put("timestamp", ev.timestamp)
                    }
                    arr.put(obj)
                }
                prefs.edit().putString("session_timeline_json", arr.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving timeline JSON", e)
            }

            calibrateLocalState(appContext, statusStr, timerMode, currentTask, currentTag, timelineList)
        }
    }

    fun updateCommandDeviceName(context: Context, email: String) {
        if (email.isBlank()) return
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return
            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
            val myDevice = com.example.util.DeviceIdProvider.getDeviceId(context)
            val activeRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("ACTIVE_FOCUS_TIMER")
            activeRef.child("Command_Device_Name").setValue(myDevice)
            Log.d(TAG, "Updated Command_Device_Name in RTDB to $myDevice")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Command_Device_Name in RTDB", e)
        }
    }

    fun stopListeningToActiveFocusTimer() {
        activeFocusTimerRef?.let { ref ->
            activeFocusTimerListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        activeFocusTimerRef = null
        activeFocusTimerListener = null
    }

    fun calculateFocusMsFromTimeline(timeline: List<TimelineEvent>): Long {
        if (timeline.isEmpty()) return 0L
        
        var accumulatedMs = 0L
        var lastResumeTs = 0L
        var isRunning = false

        for (event in timeline) {
            val action = event.event.lowercase().trim()
            val ts = event.timestamp
            
            if (action == "start" || action == "resume" || action == "resumed" || action == "break_ended" || action == "break end" || action == "break_end") {
                lastResumeTs = ts
                isRunning = true
            } else if (action == "pause" || action == "paused" || action == "break_started" || action == "break start" || action == "end" || action == "completed" || action == "session_end") {
                if (isRunning) {
                    accumulatedMs += (ts - lastResumeTs)
                    isRunning = false
                }
            }
        }
        
        if (isRunning) {
            val trueTime = com.example.util.StableTime.currentTimeMillis()
            accumulatedMs += (trueTime - lastResumeTs)
        }
        
        return accumulatedMs
    }

    fun calibrateLocalState(
        context: Context,
        statusStr: String,
        timerMode: String,
        currentTask: String,
        currentTag: String,
        timeline: List<TimelineEvent>
    ) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isLocalCommander = prefs.getBoolean("is_command_device", true)
        val isLocalTimerRunning = com.example.util.FocusTimerManager.isTimerRunning.value
        val isLocalStopwatchActive = com.example.util.FocusTimerManager.isStopwatchActive.value
        val isLocalPaused = com.example.util.FocusTimerManager.isPaused.value

        val cleanStatusStr = statusStr.lowercase().trim()
        val lastEventName = timeline.lastOrNull()?.event?.lowercase()?.trim() ?: ""
        val isTargetIdle = cleanStatusStr == "idle" ||
            cleanStatusStr == "relaxing" ||
            cleanStatusStr == "end" ||
            cleanStatusStr == "completed" ||
            cleanStatusStr == "ended" ||
            cleanStatusStr == "session_end" ||
            cleanStatusStr.isEmpty() ||
            lastEventName == "end" ||
            lastEventName == "completed" ||
            lastEventName == "session_end"

        if (isLocalCommander && (isLocalTimerRunning || isLocalStopwatchActive || isLocalPaused)) {
            Log.d(TAG, "calibrateLocalState: Local device is commander and active/paused. Skipping calibration to preserve active local session.")
            return
        }

        // Set passive calibration in progress to true, so local actions don't claim command device
        com.example.util.FocusTimerManager.isPassiveCalibrationInProgress = true

        // Safety Rollback Guard: Before passive calibration overwrites local state, save meaningful local active session if any!
        try {
            com.example.util.FocusTimerManager.saveActiveSessionState(context)
            val db = com.example.data.AppDatabase.getInstance(context)
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                com.example.util.StateReconciliationHelper.saveMeaningfulActiveSessionBeforeOverwrite(context, db)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running safety rollback guard in calibrateLocalState", e)
        }

        try {
            val totalFocusMs = calculateFocusMsFromTimeline(timeline)
            val focusMinsSetting = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getInt("pomodoro_focus_duration_mins", 25)
            
            // Log for clarity
            Log.d(TAG, "Calibrating local state: statusStr='$statusStr', timerMode='$timerMode', totalFocusMs=$totalFocusMs")

            // Update local state flows on reading device so UI reactive state matches perfectly
            this.currentTimelineFlow.value = timeline
            this.currentStatusFlow.value = when (statusStr.lowercase()) {
                "focusing" -> "Focusing"
                "paused" -> "Paused"
                "break" -> "Break"
                else -> "IDLE"
            }
            this.currentTimerModeFlow.value = timerMode

            // Sync attachments
            if (currentTask.isNotEmpty()) {
                val db = com.example.data.AppDatabase.getInstance(context)
                // Retrieve task from local DB or create a placeholder if it doesn't exist
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                scope.launch {
                    try {
                        val tasks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            db.taskDao().getAllTasksDirect()
                        }
                        val task = tasks.firstOrNull { it.title.equals(currentTask, ignoreCase = true) }
                        if (task != null) {
                            com.example.util.FocusTimerManager.setAttachedTask(task)
                        } else {
                            com.example.util.FocusTimerManager.setAttachedTask(com.example.data.Task(title = currentTask))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve attached task", e)
                    }
                }
            } else {
                com.example.util.FocusTimerManager.setAttachedTask(null)
            }
            
            if (currentTag.isNotEmpty()) {
                com.example.util.FocusTimerManager.setAttachedTag(currentTag)
            }

            if (timerMode.lowercase() == "stopwatch") {
                if (statusStr.lowercase() != "idle") {
                    com.example.util.FocusTimerManager.setTabFocusTimerSelected(false)
                    com.example.util.FocusTimerManager.setWasStartedFromStopwatch(true)
                }

                when (statusStr.lowercase()) {
                    "focusing" -> {
                        val lastResumeEvent = timeline.lastOrNull {
                            val evName = it.event.lowercase().trim()
                            evName == "start" || evName == "resume" || evName == "resumed" || evName == "break_ended" || evName == "break end" || evName == "break_end"
                        }
                        val activeChunkMs = if (lastResumeEvent != null) {
                            (com.example.util.StableTime.currentTimeMillis() - lastResumeEvent.timestamp).coerceAtLeast(0L)
                        } else 0L
                        val bankedMs = (totalFocusMs - activeChunkMs).coerceAtLeast(0L)

                        val elapsedSeconds = (totalFocusMs / 1000).toInt()
                        com.example.util.FocusTimerManager.setStopwatchSeconds(elapsedSeconds)
                        com.example.util.FocusTimerManager.setFocusPhase(true)
                        if (!com.example.util.FocusTimerManager.isStopwatchActive.value) {
                            com.example.util.FocusTimerManager.startStopwatch(context, stopActiveAlarm = false, isResuming = true)
                        }

                        // Apply precise, calibrated times post-initialization
                        com.example.util.FocusTimerManager.setAccumulatedSessionTimeMs(bankedMs)
                        if (lastResumeEvent != null) {
                            com.example.util.FocusTimerManager.setLastResumeTimeMs(lastResumeEvent.timestamp)
                            com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 
                                android.os.SystemClock.elapsedRealtime() - activeChunkMs
                        } else {
                            com.example.util.FocusTimerManager.setLastResumeTimeMs(null)
                            com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 0L
                        }
                    }
                    "paused" -> {
                        val elapsedSeconds = (totalFocusMs / 1000).toInt()
                        com.example.util.FocusTimerManager.setAccumulatedSessionTimeMs(totalFocusMs)
                        com.example.util.FocusTimerManager.setStopwatchSeconds(elapsedSeconds)
                        com.example.util.FocusTimerManager.setLastResumeTimeMs(null)
                        com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 0L
                        if (com.example.util.FocusTimerManager.isStopwatchActive.value) {
                            com.example.util.FocusTimerManager.pauseStopwatch(context)
                        } else {
                            com.example.util.FocusTimerManager.setStopwatchActive(false)
                            val prefsEditor = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                            prefsEditor.putBoolean("is_paused", true).apply()
                        }
                    }
                    "break" -> {
                        val lastBreakStart = timeline.lastOrNull {
                            val evName = it.event.uppercase().trim()
                            evName == "BREAK START" || evName == "BREAK_STARTED"
                        }
                        val breakMins = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .getInt("stopwatch_break_duration", 5)
                        val breakDurationSecs = breakMins * 60
                        val remainingBreakSecs = if (lastBreakStart != null) {
                            val elapsedSecs = ((com.example.util.StableTime.currentTimeMillis() - lastBreakStart.timestamp) / 1000).toInt()
                            (breakDurationSecs - elapsedSecs).coerceAtLeast(0)
                        } else {
                            breakDurationSecs
                        }

                        com.example.util.FocusTimerManager.setFocusPhase(false)
                        com.example.util.FocusTimerManager.setTimerSecondsLeft(remainingBreakSecs)
                        if (com.example.util.FocusTimerManager.isStopwatchActive.value) {
                            com.example.util.FocusTimerManager.pauseStopwatch(context)
                        } else {
                            com.example.util.FocusTimerManager.setStopwatchActive(false)
                        }

                        if (remainingBreakSecs > 0 && !com.example.util.FocusTimerManager.isTimerRunning.value) {
                            com.example.util.FocusTimerManager.startTimer(context, stopActiveAlarm = false, isResuming = true)
                        }
                    }
                    "idle" -> {
                        if (com.example.util.FocusTimerManager.isTimerRunning.value ||
                            com.example.util.FocusTimerManager.isStopwatchActive.value ||
                            com.example.util.FocusTimerManager.isPaused.value) {
                            com.example.util.FocusTimerManager.resetStopwatch(context, saveSession = false)
                            com.example.util.FocusTimerManager.resetTimer(context, saveSession = false)
                        }
                        val email = activeEmail
                        if (email.isNotEmpty()) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    FirestoreArchiver.pullAndSyncFocusHistoryFromFirestore(context, email)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed background pull on stopwatch idle", e)
                                }
                            }
                        }
                    }
                }
            } else {
                if (statusStr.lowercase() != "idle") {
                    com.example.util.FocusTimerManager.setTabFocusTimerSelected(true)
                    com.example.util.FocusTimerManager.setWasStartedFromStopwatch(false)
                }

                val timerDurationSecs = focusMinsSetting * 60
                val elapsedSecs = (totalFocusMs / 1000).toInt()
                val secondsLeft = (timerDurationSecs - elapsedSecs).coerceAtLeast(0)

                when (statusStr.lowercase()) {
                    "focusing" -> {
                        val lastResumeEvent = timeline.lastOrNull {
                            val evName = it.event.lowercase().trim()
                            evName == "start" || evName == "resume" || evName == "resumed" || evName == "break_ended" || evName == "break end" || evName == "break_end"
                        }
                        val activeChunkMs = if (lastResumeEvent != null) {
                            (com.example.util.StableTime.currentTimeMillis() - lastResumeEvent.timestamp).coerceAtLeast(0L)
                        } else 0L
                        val bankedMs = (totalFocusMs - activeChunkMs).coerceAtLeast(0L)

                        com.example.util.FocusTimerManager.setTimerSecondsLeft(secondsLeft)
                        com.example.util.FocusTimerManager.setFocusPhase(true)
                        if (!com.example.util.FocusTimerManager.isTimerRunning.value) {
                            com.example.util.FocusTimerManager.startTimer(context, stopActiveAlarm = false, isResuming = true)
                        }

                        // Apply precise, calibrated times post-initialization
                        com.example.util.FocusTimerManager.setAccumulatedSessionTimeMs(bankedMs)
                        if (lastResumeEvent != null) {
                            com.example.util.FocusTimerManager.setLastResumeTimeMs(lastResumeEvent.timestamp)
                            com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 
                                android.os.SystemClock.elapsedRealtime() - activeChunkMs
                        } else {
                            com.example.util.FocusTimerManager.setLastResumeTimeMs(null)
                            com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 0L
                        }
                    }
                    "paused" -> {
                        com.example.util.FocusTimerManager.setTimerSecondsLeft(secondsLeft)
                        com.example.util.FocusTimerManager.setAccumulatedSessionTimeMs(totalFocusMs)
                        com.example.util.FocusTimerManager.setLastResumeTimeMs(null)
                        com.example.util.FocusTimerManager.activeSessionStartRealtimeMs = 0L
                        if (com.example.util.FocusTimerManager.isTimerRunning.value) {
                            com.example.util.FocusTimerManager.pauseTimer(context)
                        }
                    }
                    "break" -> {
                        val lastBreakStart = timeline.lastOrNull {
                            val evName = it.event.uppercase().trim()
                            evName == "BREAK START" || evName == "BREAK_STARTED"
                        }
                        val breakMins = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .getInt("break_duration", 5)
                        val breakDurationSecs = breakMins * 60
                        val remainingBreakSecs = if (lastBreakStart != null) {
                            val elapsedSecs = ((com.example.util.StableTime.currentTimeMillis() - lastBreakStart.timestamp) / 1000).toInt()
                            (breakDurationSecs - elapsedSecs).coerceAtLeast(0)
                        } else {
                            breakDurationSecs
                        }

                        com.example.util.FocusTimerManager.setFocusPhase(false)
                        com.example.util.FocusTimerManager.setTimerSecondsLeft(remainingBreakSecs)
                        if (remainingBreakSecs > 0 && !com.example.util.FocusTimerManager.isTimerRunning.value) {
                            com.example.util.FocusTimerManager.startTimer(context, stopActiveAlarm = false, isResuming = true)
                        }
                    }
                    "idle" -> {
                        if (com.example.util.FocusTimerManager.isTimerRunning.value ||
                            com.example.util.FocusTimerManager.isStopwatchActive.value ||
                            com.example.util.FocusTimerManager.isPaused.value) {
                            com.example.util.FocusTimerManager.resetTimer(context, saveSession = false)
                            com.example.util.FocusTimerManager.resetStopwatch(context, saveSession = false)
                        }
                        val email = activeEmail
                        if (email.isNotEmpty()) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    FirestoreArchiver.pullAndSyncFocusHistoryFromFirestore(context, email)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed background pull on timer idle", e)
                                }
                            }
                        }
                    }
                }
            }

            // Trigger dynamic focus stats update on secondary device to sync with RTDB live
            if (activeEmail.isNotBlank()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        DevicePresenceManager.updateDeviceFocusStats(context, activeEmail)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating device focus stats in calibrateLocalState", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during local state calibration", e)
        } finally {
            com.example.util.FocusTimerManager.isPassiveCalibrationInProgress = false
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
        }
    }
}
