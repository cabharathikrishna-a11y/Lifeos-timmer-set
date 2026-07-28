package com.example.api

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.util.TimeEngine
import com.example.util.childMs
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DevicePresenceManager {
    private const val TAG = "DevicePresenceManager"

    @Volatile
    private var isCurrentlyReconciling = false

    // Sanitize email by normalizing usernames to full email format and replacing dots '.' with underscores '_'
    fun sanitizeEmail(email: String): String {
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty()) return ""
        val fullEmail = when {
            trimmed.contains("@") -> trimmed
            trimmed.endsWith("_gmail_com") -> trimmed
            trimmed.contains("_") && !trimmed.contains("@") && trimmed.contains("gmail") -> trimmed
            else -> "${trimmed}@gmail.com"
        }
        return fullEmail.replace(".", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("#", "_")
            .replace("/", "_")
    }

    fun getDeviceKey(context: Context): String {
        return com.example.util.DeviceIdProvider.getDeviceId(context)
    }

    private fun getAppVersionString(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "19.0"
        } catch (e: Exception) {
            "19.0"
        }
    }

    /**
     * Engine that runs on app launch/login to write presence data to RTDB.
     */
    fun registerPresence(context: Context, email: String) {
        if (email.isBlank()) return
        
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                Log.e(TAG, "Firebase DB URL is empty. Cannot register presence.")
                return
            }
            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = sanitizeEmail(email)
            val deviceKey = getDeviceKey(context)
            
            // Database Path: FOCUS_TIMMER/USER/{user_gmail_com}/DEVICES_LOGGED_IN/{DeviceKey}
            val presenceRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("DEVICES_LOGGED_IN")
                .child(deviceKey)

            val trueTime = TimeEngine.getTrueTimeMs()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(trueTime))

            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { fcmTask ->
                    val fcmToken = if (fcmTask.isSuccessful) fcmTask.result else "null_or_failed"
                    val payload = mapOf<String, Any?>(
                        "Login_status" to true,
                        "Upload_Status" to "COMPLETED",
                        "Last_Update_Time_and_Date" to null,
                        "lastUpdateDate" to null,
                        "fcm token number" to fcmToken,
                        "App_Version_No" to getAppVersionString(context),
                        "isLoggedIn" to null,
                        "uploadStatus" to null,
                        "Last_Stats_Updated" to null,
                        "lastActiveTime" to null,
                        "App_Version" to null
                    )

                    presenceRef.updateChildren(payload).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Successfully registered presence payload with FCM token for $deviceKey")
                            // Also update the focus timings for this device
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    adoptHighestTodayFocusMsFromOtherDevices(context, email)
                                    updateDeviceFocusStats(context, email)
                                    WeeklyStatsUpdater.updateWeeklyStats(context, email, 0L, "")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to run adopt/updateDeviceFocusStats inside registerPresence", e)
                                }
                            }
                        } else {
                            Log.e(TAG, "Failed to register presence for $deviceKey", task.exception)
                        }
                    }
                }
            } catch (fcmEx: Exception) {
                Log.e(TAG, "Error getting FCM token inside registerPresence, continuing with fallback", fcmEx)
                val payload = mapOf<String, Any?>(
                    "Login_status" to true,
                    "Upload_Status" to "COMPLETED",
                    "Last_Update_Time_and_Date" to null,
                    "lastUpdateDate" to null,
                    "fcm token number" to "error_or_not_initialized",
                    "App_Version_No" to getAppVersionString(context),
                    "isLoggedIn" to null,
                    "uploadStatus" to null,
                    "Last_Stats_Updated" to null,
                    "lastActiveTime" to null,
                    "App_Version" to null
                )
                presenceRef.updateChildren(payload).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Successfully registered fallback presence payload for $deviceKey")
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                adoptHighestTodayFocusMsFromOtherDevices(context, email)
                                updateDeviceFocusStats(context, email)
                                WeeklyStatsUpdater.updateWeeklyStats(context, email, 0L, "")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to run adopt/updateDeviceFocusStats inside registerPresence fallback", e)
                            }
                        }
                    }
                }
            }

            // Disconnection Rule: Attach .onDisconnect().setValue(false) to the Login_status node.
            presenceRef.child("Login_status").onDisconnect().setValue(false)

        } catch (e: Exception) {
            Log.e(TAG, "Error registering presence", e)
        }
    }

    suspend fun adoptHighestTodayFocusMsFromOtherDevices(context: Context, email: String, providedLocalTodayFocusMs: Long? = null) {
        if (email.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        try {
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val realEmail = if (email.contains("@")) {
                email
            } else {
                appPrefs.getString("user_email", null) ?: appPrefs.getString("user_email_${email}", null) ?: email
            }
            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = sanitizeEmail(realEmail)
            val myDeviceKey = getDeviceKey(context)
            
            val lbRef = database.getReference("FOCUS_TIMMER")
                .child("LEADERBOARD")
                .child(sanitizedEmail)

            val lbSnapshot = suspendCancellableCoroutine<com.google.firebase.database.DataSnapshot?> { cont ->
                lbRef.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                }
            }

            val nowMs = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date(nowMs))

            val lbLastUpdated = lbSnapshot?.child("Last_Updated")?.getValue(Long::class.java) ?: 0L
            val lbDateStr = if (lbLastUpdated > 0L) sdf.format(Date(lbLastUpdated)) else ""
            val maxOtherTodayFocusMs = if (lbDateStr == todayStr) {
                lbSnapshot?.childMs("Todays_Focus_Ms", "todayFocusMs") ?: 0L
            } else {
                0L
            }

                var localTodayFocusMs = providedLocalTodayFocusMs ?: run {
                    val db = com.example.data.AppDatabase.getInstance(context)
                    val targetEmail = realEmail.lowercase().trim()
                    val allHistory = db.localHistoryVaultDao().getAllHistoryDirect()
                    var sum = 0L
                    for (record in allHistory) {
                        val recEmail = record.userEmail.lowercase().trim()
                        if ((recEmail.isEmpty() || recEmail == targetEmail) && record.date_string == todayStr) {
                            sum += record.total_focus_ms
                        }
                    }
                    sum
                }

                // If another device logged focus time today that this device doesn't have in local DB,
                // identify missing undownloaded focus records and pull them automatically from Firestore!
                if (maxOtherTodayFocusMs > localTodayFocusMs + 10_000L && !isCurrentlyReconciling) {
                    isCurrentlyReconciling = true
                    Log.i(TAG, "⚡ Focus discrepancy identified! Other device has ${maxOtherTodayFocusMs / 1000}s focus today, local DB has ${localTodayFocusMs / 1000}s. Downloading missing focus records...")
                    try {
                        val syncResult = FirestoreArchiver.pullAndSyncFocusHistoryFromFirestore(context, realEmail)
                        Log.i(TAG, "⚡ Auto focus record download result: ${syncResult.second}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download missing focus records during cross-device reconciliation", e)
                    } finally {
                        isCurrentlyReconciling = false
                    }

                    // Recalculate local today focus ms after downloading missing records
                    val db = com.example.data.AppDatabase.getInstance(context)
                    val targetEmail = realEmail.lowercase().trim()
                    val updatedHistory = db.localHistoryVaultDao().getAllHistoryDirect()
                    var updatedSum = 0L
                    for (record in updatedHistory) {
                        val recEmail = record.userEmail.lowercase().trim()
                        if ((recEmail.isEmpty() || recEmail == targetEmail) && record.date_string == todayStr) {
                            updatedSum += record.total_focus_ms
                        }
                    }
                    localTodayFocusMs = updatedSum
                }

                val finalAdoptedMs = if (maxOtherTodayFocusMs > localTodayFocusMs + 10_000L) {
                    Log.w(TAG, "⚡ RTDB had stale today focus ms (${maxOtherTodayFocusMs}ms) not verified in Firestore. Correcting RTDB to true local focus ms (${localTodayFocusMs}ms)...")
                    WeeklyStatsUpdater.updateWeeklyStats(context, realEmail, localTodayFocusMs, "")
                    localTodayFocusMs
                } else {
                    maxOtherTodayFocusMs
                }

                appPrefs.edit()
                    .putLong("max_other_today_ms_${sanitizedEmail}", finalAdoptedMs)
                    .putString("adopted_today_date_${sanitizedEmail}", todayStr)
                    .apply()

                Log.d(TAG, "Synchronized today focus time: $finalAdoptedMs ms (localToday: $localTodayFocusMs)")
        } catch (e: Exception) {
            Log.e(TAG, "Error adopting highest today focus time from other devices", e)
        }
    }

    /**
     * Calculates user focus metrics (today, past 7 days, past 30 days, all time) from the local database
     * and uploads them under the active device node in Firebase.
     */
    suspend fun updateDeviceFocusStats(context: Context, email: String) {
        if (email.isBlank()) return
        
        try {
            val db = com.example.data.AppDatabase.getInstance(context)
            val allHistory = db.localHistoryVaultDao().getAllHistoryDirect()
            
            val nowMs = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date(nowMs))

            var todayFocusMs = 0L
            var past7DaysFocusMs = 0L
            var past30DaysFocusMs = 0L
            var past50DaysFocusMs = 0L
            var allTimeFocusMs = 0L

            val targetEmail = email.lowercase().trim()
            for (record in allHistory) {
                val recEmail = record.userEmail.lowercase().trim()
                if (recEmail.isNotEmpty() && recEmail != targetEmail) continue

                val duration = record.total_focus_ms
                allTimeFocusMs += duration
                
                if (record.date_string == todayStr) {
                    todayFocusMs += duration
                }
                
                if (record.start_time_ms >= nowMs - (7L * 24 * 60 * 60 * 1000)) {
                    past7DaysFocusMs += duration
                }
                
                if (record.start_time_ms >= nowMs - (30L * 24 * 60 * 60 * 1000)) {
                    past30DaysFocusMs += duration
                }

                if (record.start_time_ms >= nowMs - (50L * 24 * 60 * 60 * 1000)) {
                    past50DaysFocusMs += duration
                }
            }

            // Calculate today's focus time directly from FocusTimerManager records as well
            val focusRecords = com.example.util.FocusTimerManager.loadFocusRecords(context)
            val focusTimerSecs = focusRecords.sumOf { r ->
                com.example.util.FocusTimerManager.getOverlapSecondsForDate(r, todayStr)
            }
            val focusTimerTodayMs = focusTimerSecs * 1000L
            todayFocusMs = maxOf(todayFocusMs, focusTimerTodayMs)

            // Adopt highest today focus from other devices using maxOf without recursive feedback addition
            adoptHighestTodayFocusMsFromOtherDevices(context, email, todayFocusMs)

            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = sanitizeEmail(email)
                val deviceKey = getDeviceKey(context)
                
                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val adoptedTodayDate = appPrefs.getString("adopted_today_date_${sanitizedEmail}", "")
                val maxOtherTodayMs = if (adoptedTodayDate == todayStr) {
                    appPrefs.getLong("max_other_today_ms_${sanitizedEmail}", 0L)
                } else {
                    0L
                }
                val maxOtherPast7Ms = appPrefs.getLong("max_other_past7_ms_${sanitizedEmail}", 0L)
                val maxOtherPast30Ms = appPrefs.getLong("max_other_past30_ms_${sanitizedEmail}", 0L)
                val maxOtherAllTimeMs = appPrefs.getLong("max_other_alltime_ms_${sanitizedEmail}", 0L)

                // Cap single-day focus time to maximum 24 hours (86,400,000 ms) to prevent corrupt numbers
                val rawToday = maxOf(todayFocusMs, maxOtherTodayMs)
                val finalTodayFocusMs = minOf(86_400_000L, rawToday)
                val finalPast7DaysFocusMs = maxOf(past7DaysFocusMs, maxOtherPast7Ms)
                val finalPast30DaysFocusMs = maxOf(past30DaysFocusMs, maxOtherPast30Ms)
                val finalPast50DaysFocusMs = maxOf(past50DaysFocusMs, maxOtherPast30Ms)
                val finalAllTimeFocusMs = maxOf(allTimeFocusMs, maxOtherAllTimeMs)

                val formattedNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))
                val deviceNameStr = android.os.Build.MODEL ?: "Android Device"

                // Write authoritative base stats to FOCUS_TIMMER/LEADERBOARD/{sanitizedEmail}
                val currentUsername = appPrefs.getString("current_username", "Guest") ?: "Guest"
                val cachedNickname = appPrefs.getString("user_nickname_$currentUsername", "") ?: ""
                val cachedName = appPrefs.getString("user_name_$currentUsername", "") ?: ""
                val displayName = if (cachedNickname.isNotEmpty()) cachedNickname else if (cachedName.isNotEmpty()) cachedName else currentUsername
                val rawEmojiFromPrefs = appPrefs.getString("user_emoji_$currentUsername", "")
                    ?.takeIf { it.isNotEmpty() && it != "👤" }
                    ?: appPrefs.getString("user_emoji", "")
                    ?.takeIf { it.isNotEmpty() && it != "👤" }
                    ?: ""
                val cachedEmoji = if (rawEmojiFromPrefs.isNotEmpty()) rawEmojiFromPrefs else "👤"

                val leaderboardRef = database.getReference("FOCUS_TIMMER")
                    .child("LEADERBOARD")
                    .child(sanitizedEmail)

                val leaderboardUpdates = mapOf<String, Any?>(
                    "email" to email,
                    "displayName" to displayName,
                    "CustomEmoji" to cachedEmoji,
                    "customEmoji" to null,
                    "topSubject" to null,
                    "Todays_Focus_Ms" to finalTodayFocusMs,
                    "Past_7_Days_Focus_Ms" to finalPast7DaysFocusMs,
                    "Past_30_Days_Focus_Ms" to finalPast30DaysFocusMs,
                    "All_Time_Focus_Ms" to finalAllTimeFocusMs,
                    "Last_Updated" to nowMs,
                    "Last_Updated_String" to null,
                    "lastUpdateDate" to null,

                    // Remove duplicate and obsolete fields
                    "todayFocusMs" to null,
                    "Last_Update_Time_and_Date" to null,
                    "Past_50_Days_Focus_Ms" to null,
                    "userEmail" to null,
                    "DisplayName" to null,
                    "TODAY" to null,
                    "PAST_7_DAYS" to null,
                    "PAST_30_DAYS" to null,
                    "ALL_TIME" to null,
                    "ActiveStreak" to null,
                    "XpScore" to null,
                    "Subject_Breakdown" to null
                )
                leaderboardRef.updateChildren(leaderboardUpdates)

                val userRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)

                val arenaRef = userRef.child("ARENA")
                val arenaUpdates = mapOf<String, Any?>(
                    "DisplayName" to displayName,
                    "CustomEmoji" to cachedEmoji,
                    "customEmoji" to null,
                    "topSubject" to null,
                    "Todays_Focus_Ms" to finalTodayFocusMs,
                    "todayFocusMs" to finalTodayFocusMs,
                    "Last_Updated" to nowMs,
                    "Last_Updated_String" to null,
                    "TODAY" to null,
                    "PAST_7_DAYS" to null,
                    "PAST_30_DAYS" to null,
                    "ALL_TIME" to null,
                    "Subject_Breakdown" to null
                )
                arenaRef.updateChildren(arenaUpdates)

                // Sync root-level fields directly under user node for liveListeners
                val rootUpdates = mapOf<String, Any?>(
                    "Todays_Focus_Ms" to null,
                    "todayFocusMs" to null,
                    "CustomEmoji" to null,
                    "customEmoji" to null,
                    "Todays_Total_Focus_Seconds" to null,
                    "Past_7_Days_Focus_Ms" to null,
                    "Past_30_Days_Focus_Ms" to null,
                    "Past_50_Days_Focus_Ms" to null,
                    "All_Time_Focus_Ms" to null,
                    "Last_Updated" to null,
                    "Last_Updated_String" to null,
                    "Last_Update_Time_and_Date" to null,
                    "lastUpdateDate" to null,
                    "HEALTH_LIVE" to null,
                    "Custom_Emoji" to null,
                    "email" to email,
                    "userEmail" to null,
                    "focus_timestamps" to null,
                    "focusTimestamps" to null,
                    "focus_timestamps_ms" to null
                )
                userRef.updateChildren(rootUpdates)

                val deviceRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("DEVICES_LOGGED_IN")
                    .child(deviceKey)

                val statsUpdates = mapOf<String, Any?>(
                    "Last_Update_Time_and_Date" to null,
                    "lastUpdateDate" to null,
                    "Upload_Status" to "COMPLETED",
                    "Login_status" to true,
                    "deviceName" to deviceNameStr,
                    "App_Version_No" to getAppVersionString(context),
                    "Todays_Focus_Ms" to null,
                    "todayFocusMs" to null,
                    "Past_7_Days_Focus_Ms" to null,
                    "Past_30_Days_Focus_Ms" to null,
                    "All_Time_Focus_Ms" to null
                )
                
                deviceRef.updateChildren(statsUpdates)

                val activeTimerRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("ACTIVE_FOCUS_TIMER")
                val timerTodayMap = mapOf<String, Any?>(
                    "Todays_Focus_Ms" to null,
                    "todayFocusMs" to null
                )
                activeTimerRef.updateChildren(timerTodayMap)

                appPrefs.edit().putString("local_device_upload_status", "COMPLETED").apply()
                Log.d(TAG, "Successfully updated device focus timings in Firebase")

                // Check for 10-hr Focus Achievement to grant a shield
                val tenHoursMs = 10L * 3600000L
                val shieldGrantedKey = "shield_granted_${email}_${todayStr}"
                if (todayFocusMs >= tenHoursMs && !appPrefs.getBoolean(shieldGrantedKey, false)) {
                    val myShieldsRef = database.getReference("FOCUS_TIMMER")
                        .child("USER")
                        .child(sanitizedEmail)
                        .child("SHIELDS")

                    val uuid = java.util.UUID.randomUUID().toString()
                    val trueTime = TimeEngine.getTrueTimeMs()
                    val shieldPayload = mapOf(
                        "Donor_Email" to "system@focussphere.com",
                        "Donor_Name" to "Focus Sphere System (10-hr Achievement)",
                        "Granted_Timestamp" to trueTime,
                        "Is_Consumed" to false,
                        "Consumed_Date" to null
                    )

                    myShieldsRef.child(uuid).setValue(shieldPayload).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Shield granted in RTDB for 10-hr focus!")
                            appPrefs.edit().putBoolean(shieldGrantedKey, true).apply()
                            com.example.api.FocusLogManager.logEvent(context, "Granted 1 Streak Shield for achieving 10-hour daily focus!")
                        }
                    }

                    // Also save locally
                    val localShield = com.example.data.LocalShieldsVault(
                        uuid = uuid,
                        donor_email = "system@focussphere.com",
                        donor_name = "Focus Sphere System (10-hr Achievement)",
                        granted_timestamp = trueTime,
                        is_consumed = false,
                        consumed_date = null
                    )
                    db.localShieldsVaultDao().insertShield(localShield)
                }

                // Keep local focus records and today's stats 100% in sync
                com.example.util.FocusTimerManager.reloadFocusRecordsFromDb(context)
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error updating device focus timings in Firebase", e)
        }
    }

    /**
     * Active Evaluation Function: Create a function isUserActivelyLoggedIn(devicesMap).
     * Enforce these rules:
     * - Web devices (WEB_USER_) are deemed logged out if Last_Update_Time_and_Date is older than 48 hours.
     * - Native devices must have Login_status == true AND an update timestamp within 12 hours.
     */
    fun isUserActivelyLoggedIn(devicesMap: Map<String, Any>?): Boolean {
        if (devicesMap == null) return false
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val trueTime = TimeEngine.getTrueTimeMs()
        val fortyEightHoursMs = 48L * 60 * 60 * 1000
        val twelveHoursMs = 12L * 60 * 60 * 1000

        for ((deviceKey, deviceData) in devicesMap) {
            try {
                val data = deviceData as? Map<*, *> ?: continue
                
                // Get Last_Update_Time_and_Date
                val lastUpdateStr = data["Last_Update_Time_and_Date"] as? String ?: continue
                val lastUpdateDate = sdf.parse(lastUpdateStr) ?: continue
                val lastUpdateTimeMs = lastUpdateDate.time
                val ageMs = trueTime - lastUpdateTimeMs

                if (deviceKey.startsWith("WEB_USER_")) {
                    // Web devices: logged out if older than 48 hours
                    if (ageMs <= fortyEightHoursMs) {
                        return true
                    }
                } else {
                    // Native devices: Login_status == true AND within 12 hours
                    val loginStatus = data["Login_status"] as? Boolean ?: false
                    if (loginStatus && ageMs <= twelveHoursMs) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating presence for device $deviceKey", e)
            }
        }
        return false
    }

    /**
     * Executes sync logic: If the user is IDLE (no active timer or stopwatch session),
     * deletes stale todayFocusMs, recalculates/revises it strictly from DB history records,
     * and syncs the revised value to Firebase.
     */
    suspend fun syncAndReviseTodayFocusMsIfIdle(context: Context, email: String): Long {
        if (email.isBlank()) return 0L
        val sanitizedEmail = sanitizeEmail(email)
        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val isIdle = !com.example.util.FocusTimerManager.isTimerRunning.value &&
                     !com.example.util.FocusTimerManager.isStopwatchActive.value &&
                     !com.example.util.FocusTimerManager.isPaused.value &&
                     com.example.util.FocusTimerManager.accumulatedSessionTimeMs.value == 0L

        if (isIdle) {
            Log.i(TAG, "⚡ SYNC PRESSED (IDLE): Deleting stale todayFocusMs and revising strictly from DB records...")

            // 1. Clear cached adopted max focus time from other devices
            appPrefs.edit().remove("max_other_today_ms_${sanitizedEmail}").apply()

            // 2. Delete todayFocusMs node in Firebase RTDB to clear stale values
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                try {
                    val database = FirebaseDatabase.getInstance(dbUrl)
                    val userRef = database.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)
                    val leaderboardRef = database.getReference("FOCUS_TIMMER").child("LEADERBOARD").child(sanitizedEmail)

                    val wipeMap = mapOf<String, Any?>(
                        "Todays_Focus_Ms" to null,
                        "todayFocusMs" to null
                    )
                    userRef.updateChildren(wipeMap)
                    userRef.child("ARENA").updateChildren(wipeMap)
                    leaderboardRef.updateChildren(wipeMap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing RTDB todayFocusMs during idle sync", e)
                }
            }

            // 3. Recalculate revised today focus time strictly from local DB completed history records
            val nowMs = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date(nowMs))

            val db = com.example.data.AppDatabase.getInstance(context)
            val allHistory = db.localHistoryVaultDao().getAllHistoryDirect()
            var revisedTodayFocusMs = 0L
            val targetEmail = email.lowercase().trim()

            for (record in allHistory) {
                val recEmail = record.userEmail.lowercase().trim()
                if ((recEmail.isEmpty() || recEmail == targetEmail) && record.date_string == todayStr) {
                    revisedTodayFocusMs += record.total_focus_ms
                }
            }

            val focusRecords = com.example.util.FocusTimerManager.loadFocusRecords(context)
            val focusTimerSecs = focusRecords.sumOf { r ->
                com.example.util.FocusTimerManager.getOverlapSecondsForDate(r, todayStr)
            }
            val focusTimerMs = focusTimerSecs * 1000L
            revisedTodayFocusMs = maxOf(revisedTodayFocusMs, focusTimerMs)

            // 4. Update device stats with revised value
            updateDeviceFocusStatsWithExplicitTodayMs(context, email, revisedTodayFocusMs)

            FocusLogManager.logEvent(context, "Sync Action (IDLE): Deleted stale todayFocusMs and revised value to ${revisedTodayFocusMs / 1000}s.")
            return revisedTodayFocusMs
        } else {
            Log.i(TAG, "⚡ SYNC PRESSED (ACTIVE): Preserving active session focus time, updating stats.")
            updateDeviceFocusStats(context, email)
            return 0L
        }
    }

    /**
     * Rechecks today's focus time with system DB records once whenever a session ends,
     * ensuring 100% precision and eliminating drift across devices.
     */
    suspend fun recheckTodayFocusMsWithSystem(context: Context, email: String): Long {
        if (email.isBlank()) return 0L
        val targetEmail = email.lowercase().trim()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date(TimeEngine.getUniversalTimeMs()))

        val db = com.example.data.AppDatabase.getInstance(context)
        val allHistory = db.localHistoryVaultDao().getAllHistoryDirect()

        var systemVerifiedMs = 0L
        for (record in allHistory) {
            val recEmail = record.userEmail.lowercase().trim()
            if ((recEmail.isEmpty() || recEmail == targetEmail) && record.date_string == todayStr) {
                systemVerifiedMs += record.total_focus_ms
            }
        }

        val focusRecords = com.example.util.FocusTimerManager.loadFocusRecords(context)
        val focusTimerSecs = focusRecords.sumOf { r ->
            com.example.util.FocusTimerManager.getOverlapSecondsForDate(r, todayStr)
        }
        val focusTimerMs = focusTimerSecs * 1000L

        val finalVerifiedTodayMs = maxOf(systemVerifiedMs, focusTimerMs)

        Log.i(TAG, "⚡ System recheck for todayFocusMs: verified $finalVerifiedTodayMs ms from DB history for $targetEmail")

        updateDeviceFocusStatsWithExplicitTodayMs(context, email, finalVerifiedTodayMs)

        FocusLogManager.logEvent(context, "Session End System Recheck: Verified todayFocusMs = ${finalVerifiedTodayMs / 1000}s.")
        return finalVerifiedTodayMs
    }

    /**
     * Updates device focus stats in Firebase RTDB with an explicit, verified today's focus ms value.
     */
    suspend fun updateDeviceFocusStatsWithExplicitTodayMs(context: Context, email: String, explicitTodayMs: Long) {
        if (email.isBlank()) return
        try {
            val db = com.example.data.AppDatabase.getInstance(context)
            val allHistory = db.localHistoryVaultDao().getAllHistoryDirect()
            val nowMs = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date(nowMs))

            var past7DaysFocusMs = 0L
            var past30DaysFocusMs = 0L
            var allTimeFocusMs = 0L
            val targetEmail = email.lowercase().trim()

            for (record in allHistory) {
                val recEmail = record.userEmail.lowercase().trim()
                if (recEmail.isNotEmpty() && recEmail != targetEmail) continue
                val duration = record.total_focus_ms
                allTimeFocusMs += duration
                if (record.start_time_ms >= nowMs - (7L * 24 * 60 * 60 * 1000)) past7DaysFocusMs += duration
                if (record.start_time_ms >= nowMs - (30L * 24 * 60 * 60 * 1000)) past30DaysFocusMs += duration
            }

            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = sanitizeEmail(email)
                val deviceKey = getDeviceKey(context)
                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

                val finalTodayFocusMs = minOf(86_400_000L, maxOf(0L, explicitTodayMs))
                val formattedNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))
                val deviceNameStr = android.os.Build.MODEL ?: "Android Device"

                val currentUsername = appPrefs.getString("current_username", "Guest") ?: "Guest"
                val cachedNickname = appPrefs.getString("user_nickname_$currentUsername", "") ?: ""
                val cachedName = appPrefs.getString("user_name_$currentUsername", "") ?: ""
                val displayName = if (cachedNickname.isNotEmpty()) cachedNickname else if (cachedName.isNotEmpty()) cachedName else currentUsername
                val rawEmojiFromPrefs = appPrefs.getString("user_emoji_$currentUsername", "")
                    ?.takeIf { it.isNotEmpty() && it != "👤" }
                    ?: appPrefs.getString("user_emoji", "")
                    ?.takeIf { it.isNotEmpty() && it != "👤" }
                    ?: ""
                val cachedEmoji = if (rawEmojiFromPrefs.isNotEmpty()) rawEmojiFromPrefs else "👤"

                val leaderboardRef = database.getReference("FOCUS_TIMMER").child("LEADERBOARD").child(sanitizedEmail)
                leaderboardRef.updateChildren(mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "CustomEmoji" to cachedEmoji,
                    "customEmoji" to null,
                    "topSubject" to null,
                    "Todays_Focus_Ms" to finalTodayFocusMs,
                    "Past_7_Days_Focus_Ms" to past7DaysFocusMs,
                    "Past_30_Days_Focus_Ms" to past30DaysFocusMs,
                    "All_Time_Focus_Ms" to allTimeFocusMs,
                    "Last_Updated" to nowMs,
                    "Last_Updated_String" to null,
                    "lastUpdateDate" to null
                ))

                val userRef = database.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)
                userRef.child("ARENA").updateChildren(mapOf(
                    "DisplayName" to displayName,
                    "CustomEmoji" to cachedEmoji,
                    "customEmoji" to null,
                    "topSubject" to null,
                    "Todays_Focus_Ms" to finalTodayFocusMs,
                    "todayFocusMs" to finalTodayFocusMs,
                    "Last_Updated" to nowMs,
                    "Last_Updated_String" to null
                ))

                userRef.updateChildren(mapOf(
                    "Todays_Focus_Ms" to null,
                    "todayFocusMs" to null,
                    "CustomEmoji" to null,
                    "customEmoji" to null,
                    "Past_7_Days_Focus_Ms" to null,
                    "Past_30_Days_Focus_Ms" to null,
                    "All_Time_Focus_Ms" to null,
                    "Last_Updated" to null,
                    "Last_Updated_String" to null,
                    "lastUpdateDate" to null,
                    "HEALTH_LIVE" to null,
                    "email" to email
                ))

                val deviceRef = userRef.child("DEVICES_LOGGED_IN").child(deviceKey)
                deviceRef.updateChildren(mapOf(
                    "Last_Update_Time_and_Date" to null,
                    "lastUpdateDate" to null,
                    "Upload_Status" to "COMPLETED",
                    "Login_status" to true,
                    "deviceName" to deviceNameStr,
                    "App_Version_No" to getAppVersionString(context),
                    "Todays_Focus_Ms" to null,
                    "todayFocusMs" to null,
                    "Past_7_Days_Focus_Ms" to null,
                    "Past_30_Days_Focus_Ms" to null,
                    "All_Time_Focus_Ms" to null
                ))
                com.example.util.FocusTimerManager.reloadFocusRecordsFromDb(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device focus stats with explicit today ms", e)
        }
    }
}
