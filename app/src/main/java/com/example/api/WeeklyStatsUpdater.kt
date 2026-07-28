package com.example.api

import android.content.Context
import android.util.Log
import com.example.api.FirebaseConfig
import com.example.api.DevicePresenceManager
import com.example.util.TimeEngine
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WeeklyStatsUpdater {
    private const val TAG = "WeeklyStatsUpdater"

    fun getYearAndWeekNumber(timestampMs: Long): String {
        val cal = Calendar.getInstance(Locale.US)
        cal.timeInMillis = timestampMs
        cal.minimalDaysInFirstWeek = 4
        cal.firstDayOfWeek = Calendar.MONDAY
        val year = cal.get(Calendar.YEAR)
        val weekNo = cal.get(Calendar.WEEK_OF_YEAR)
        return "${year}_W${String.format(Locale.US, "%02d", weekNo)}"
    }

    suspend fun updateWeeklyStats(
        context: Context,
        email: String,
        focusDurationMs: Long,
        currentTag: String
    ) {
        if (email.isBlank()) {
            Log.d(TAG, "Empty email, skipping weekly stats update.")
            return
        }

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                Log.e(TAG, "Database URL is empty, skipping weekly stats update.")
                return
            }

            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
            
            val trueTime = TimeEngine.getTrueTimeMs()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdf.format(Date(trueTime))

            // Get current display name and emoji from app_prefs
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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

            // Query local Room database for the consistency streak index
            val db = com.example.data.AppDatabase.getInstance(context)
            val targetEmail = email.lowercase().trim()
            val allLocalRecords = db.localHistoryVaultDao().getAllHistoryDirect().filter {
                it.userEmail.isBlank() || it.userEmail.lowercase().trim() == targetEmail
            }
            val activeStreak = AnalyticsVaultEngine.calculateDailyConsistencyStreak(context, allLocalRecords)
            Log.d(TAG, "Calculated streak for weekly stats updater: $activeStreak")

            // Filter lists for different periods
            // 1. Today
            val todayRecords = allLocalRecords.filter { it.date_string == todayStr }
            var todayFocusMs = todayRecords.sumOf { it.total_focus_ms }

            val focusRecords = com.example.util.FocusTimerManager.loadFocusRecords(context)
            val focusTimerSecs = focusRecords.sumOf { r ->
                com.example.util.FocusTimerManager.getOverlapSecondsForDate(r, todayStr)
            }
            val focusTimerTodayMs = focusTimerSecs * 1000L
            todayFocusMs = maxOf(todayFocusMs, focusTimerTodayMs, focusDurationMs)

            // 2. Past 7 Days Dates
            val past7Dates = (0..6).map { offset ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = trueTime
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                sdf.format(cal.time)
            }
            val past7Records = allLocalRecords.filter { it.date_string in past7Dates }
            val past7FocusMs = past7Records.sumOf { it.total_focus_ms }

            // 3. Past 30 Days Dates
            val past30Dates = (0..29).map { offset ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = trueTime
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                sdf.format(cal.time)
            }
            val past30Records = allLocalRecords.filter { it.date_string in past30Dates }
            val past30FocusMs = past30Records.sumOf { it.total_focus_ms }

            // 4. Past 50 Days Dates
            val past50Dates = (0..49).map { offset ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = trueTime
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                sdf.format(cal.time)
            }
            val past50Records = allLocalRecords.filter { it.date_string in past50Dates }
            val past50FocusMs = past50Records.sumOf { it.total_focus_ms }

            // 5. All Time
            val allTimeFocusMs = allLocalRecords.sumOf { it.total_focus_ms }

            // Recalculate adopted stats with fresh SQLite records
            DevicePresenceManager.adoptHighestTodayFocusMsFromOtherDevices(context, email, todayFocusMs)

            val adoptedTodayDate = appPrefs.getString("adopted_today_date_${sanitizedEmail}", "")
            val maxOtherTodayMs = if (adoptedTodayDate == todayStr) {
                appPrefs.getLong("max_other_today_ms_${sanitizedEmail}", 0L)
            } else {
                0L
            }
            val maxOtherPast7Ms = appPrefs.getLong("max_other_past7_ms_${sanitizedEmail}", 0L)
            val maxOtherPast30Ms = appPrefs.getLong("max_other_past30_ms_${sanitizedEmail}", 0L)
            val maxOtherAllTimeMs = appPrefs.getLong("max_other_alltime_ms_${sanitizedEmail}", 0L)

            val rawTodayMs = maxOf(todayFocusMs, maxOtherTodayMs)
            val finalTodayFocusMs = minOf(86_400_000L, rawTodayMs)
            val finalPast7FocusMs = maxOf(past7FocusMs, maxOtherPast7Ms)
            val finalPast30FocusMs = maxOf(past30FocusMs, maxOtherPast30Ms)
            val finalPast50FocusMs = maxOf(past50FocusMs, maxOtherPast30Ms)
            val finalAllTimeFocusMs = maxOf(allTimeFocusMs, maxOtherAllTimeMs)

            // Consolidated ARENA write
            val arenaRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("ARENA")

            val currentXpSnapshot = withContext(Dispatchers.IO) {
                kotlin.coroutines.suspendCoroutine { continuation ->
                    arenaRef.child("XpScore").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            continuation.resumeWith(Result.success(snapshot))
                        }
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            continuation.resumeWith(Result.success(null))
                        }
                    })
                }
            }

            val currentServerXp = currentXpSnapshot?.getValue(Int::class.java)
            val overallXpScore = if (currentServerXp != null) {
                val newlyEarnedXp = (focusDurationMs / 60000L / 15L).toInt()
                currentServerXp + newlyEarnedXp
            } else {
                ArenaLeaderboardEngine.calculateXp(finalAllTimeFocusMs, activeStreak)
            }

            val sdfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val lastUpdatedStr = sdfDateTime.format(Date(trueTime))

            val breakdown = getSubjectBreakdown(allLocalRecords)
            val topSubjectName = breakdown.maxByOrNull { it.value }?.key ?: "None"

            val arenaData = mapOf<String, Any?>(
                "ActiveStreak" to activeStreak,
                "XpScore" to overallXpScore,
                "Top_Subject" to topSubjectName,
                "topSubject" to null,
                "Last_Updated" to trueTime,
                "Last_Updated_String" to null,
                "DisplayName" to displayName,
                "CustomEmoji" to cachedEmoji,
                "customEmoji" to null,
                
                // Keep Todays_Focus_Ms in ARENA
                "TODAY" to null,
                "PAST_7_DAYS" to null,
                "PAST_30_DAYS" to null,
                "ALL_TIME" to null,
                "Subject_Breakdown" to null,
                "Todays_Focus_Ms" to finalTodayFocusMs,
                "todayFocusMs" to finalTodayFocusMs
            )

            arenaRef.updateChildren(arenaData)

            val userNodeRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
            
            // Keep root clean of stats fields under USER node
            val rootUpdates = mapOf<String, Any?>(
                "Todays_Focus_Ms" to null,
                "todayFocusMs" to null,
                "customEmoji" to null,
                "CustomEmoji" to null,
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
            userNodeRef.updateChildren(rootUpdates)

            // Centralized Shared Room LEADERBOARD node under FOCUS_TIMMER
            val leaderboardRef = database.getReference("FOCUS_TIMMER")
                .child("LEADERBOARD")
                .child(sanitizedEmail)

            val leaderboardData = mapOf<String, Any?>(
                "email" to email,
                "displayName" to displayName,
                "CustomEmoji" to cachedEmoji,
                "customEmoji" to null,
                "Top_Subject" to topSubjectName,
                "topSubject" to null,
                "Todays_Focus_Ms" to finalTodayFocusMs,
                "Past_7_Days_Focus_Ms" to finalPast7FocusMs,
                "Past_30_Days_Focus_Ms" to finalPast30FocusMs,
                "All_Time_Focus_Ms" to finalAllTimeFocusMs,
                "Last_Updated" to trueTime,
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

            leaderboardRef.updateChildren(leaderboardData)

            // Clean up legacy WEEKLY_STATS node to prevent junk
            database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("WEEKLY_STATS")
                .removeValue()

            Log.d(TAG, "Successfully recalculated and synchronized static stats branches to ARENA in RTDB.")

            // Update device timings in Firebase
            DevicePresenceManager.updateDeviceFocusStats(context, email)

            // Trigger structural repair verification
            FirebaseRepairKit.repairUserData(context, email)

            // Recompute Arena leaderboard so UI updates immediately
            ArenaLeaderboardEngine.recomputeLeaderboard(email)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating weekly stats nodes in RTDB", e)
        }
    }

    private fun getSubjectBreakdown(records: List<com.example.data.LocalHistoryVault>): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (r in records) {
            val tag = r.subject.trim()
                .ifBlank { "Study" }
                .replace(".", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("#", "_")
                .replace("/", "_")
            map[tag] = (map[tag] ?: 0L) + r.total_focus_ms
        }
        return map
    }

    fun adoptCloudStatsOnLogin(context: Context, email: String) {
        if (email.isBlank()) return
        
        // Trigger Firestore and RTDB cleaning, folder consolidation, and settings sync on login
        FirestoreCleaner.cleanUserData(context, email)
        FirebaseRepairKit.repairUserData(context, email)
        UserSettingsSyncEngine.pullSettingsFromCloud(context, email)
        UserSettingsSyncEngine.startListeningForRemoteSettingsUpdates(context, email)

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return
        
        try {
            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
            
            // Try to read from ARENA first
            val arenaRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("ARENA")

            arenaRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(arenaSnapshot: com.google.firebase.database.DataSnapshot) {
                    if (arenaSnapshot.exists()) {
                        val allTimeMs = arenaSnapshot.child("ALL_TIME").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                        val past30Ms = arenaSnapshot.child("PAST_30_DAYS").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                        val past7Ms = arenaSnapshot.child("PAST_7_DAYS").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                        val todayMs = arenaSnapshot.child("TODAY").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                        
                        // Cloud-based 7 days inactivity check
                        val lastUpdated = arenaSnapshot.child("Last_Updated").getValue(Long::class.java) ?: 0L
                        if (lastUpdated > 0L) {
                            val daysPassed = ((System.currentTimeMillis() - lastUpdated) / (24L * 3600L * 1000L)).toInt()
                            if (daysPassed >= 7) {
                                com.example.api.StreakShieldManager.grantFreeShieldAndResetXp(context, email)
                            }
                        }
                        
                        Log.d(TAG, "adoptCloudStatsOnLogin found ARENA cloud stats: AllTime=$allTimeMs, Past30=$past30Ms, Past7=$past7Ms, Today=$todayMs")
                        applyAdoptedStats(context, sanitizedEmail, allTimeMs, past30Ms, past7Ms, todayMs)
                    } else {
                        // Fallback to old WEEKLY_STATS
                        val legacyRef = database.getReference("FOCUS_TIMMER")
                            .child("USER")
                            .child(sanitizedEmail)
                            .child("WEEKLY_STATS")
                            
                        legacyRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(legacySnapshot: com.google.firebase.database.DataSnapshot) {
                                if (legacySnapshot.exists()) {
                                    val allTimeMs = legacySnapshot.child("ALL_TIME_STAT").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                                    val past30Ms = legacySnapshot.child("PAST_30_DAYS_STAT").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                                    val past7Ms = legacySnapshot.child("PAST_7_DAYS_STAT").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                                    val todayMs = legacySnapshot.child("TODAYS_STAT").child("Total_Focus_Ms").getValue(Long::class.java) ?: 0L
                                    
                                    Log.d(TAG, "adoptCloudStatsOnLogin fallback found WEEKLY_STATS cloud stats: AllTime=$allTimeMs, Past30=$past30Ms, Past7=$past7Ms, Today=$todayMs")
                                    applyAdoptedStats(context, sanitizedEmail, allTimeMs, past30Ms, past7Ms, todayMs)
                                } else {
                                    Log.d(TAG, "No existing cloud stats found in ARENA or WEEKLY_STATS. Initializing ARENA node...")
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        updateWeeklyStats(context, email, 0L, "")
                                    }
                                }
                            }
                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                Log.e(TAG, "adoptCloudStatsOnLogin legacy fallback cancelled: ${error.message}")
                            }
                        })
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "adoptCloudStatsOnLogin arena fetch cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in adoptCloudStatsOnLogin", e)
        }
    }

    private fun applyAdoptedStats(
        context: Context,
        sanitizedEmail: String,
        allTimeMs: Long,
        past30Ms: Long,
        past7Ms: Long,
        todayMs: Long
    ) {
        val db = com.example.data.AppDatabase.getInstance(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val allHistoryRaw = db.localHistoryVaultDao().getAllHistoryDirect()
                val targetEmail = sanitizedEmail.replace(",", ".").lowercase().trim()
                val allHistory = allHistoryRaw.filter {
                    it.userEmail.isBlank() || it.userEmail.lowercase().trim() == targetEmail
                }
                val nowMs = System.currentTimeMillis()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayStr = sdf.format(Date(nowMs))

                var localTodayMs = 0L
                var localPast7Ms = 0L
                var localPast30Ms = 0L
                var localAllTimeMs = 0L

                for (record in allHistory) {
                    val duration = record.total_focus_ms
                    localAllTimeMs += duration
                    if (record.date_string == todayStr) {
                        localTodayMs += duration
                    }
                    if (record.start_time_ms >= nowMs - (7L * 24 * 60 * 60 * 1000)) {
                        localPast7Ms += duration
                    }
                    if (record.start_time_ms >= nowMs - (30L * 24 * 60 * 60 * 1000)) {
                        localPast30Ms += duration
                    }
                }

                val adoptedAllTimeMs = maxOf(0L, allTimeMs - localAllTimeMs)
                val adoptedPast30Ms = maxOf(0L, past30Ms - localPast30Ms)
                val adoptedPast7Ms = maxOf(0L, past7Ms - localPast7Ms)
                val adoptedTodayMs = maxOf(0L, todayMs - localTodayMs)

                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val localTotalFocusMinutes = appPrefs.getInt("total_focus_minutes", 0)
                val cloudTotalFocusMinutes = (allTimeMs / 1000 / 60).toInt()
                val adoptedTotalFocusMinutes = maxOf(0, cloudTotalFocusMinutes - localTotalFocusMinutes)

                appPrefs.edit().apply {
                    putLong("adopted_all_time_ms_${sanitizedEmail}", adoptedAllTimeMs)
                    putLong("adopted_past_30_ms_${sanitizedEmail}", adoptedPast30Ms)
                    putLong("adopted_past_7_ms_${sanitizedEmail}", adoptedPast7Ms)
                    putLong("adopted_today_ms_${sanitizedEmail}", adoptedTodayMs)
                    putString("adopted_today_date_${sanitizedEmail}", todayStr)
                    putInt("adopted_total_focus_minutes_${sanitizedEmail}", adoptedTotalFocusMinutes)

                    val updatedTotalFocusMinutes = localTotalFocusMinutes + adoptedTotalFocusMinutes
                    putInt("total_focus_minutes", updatedTotalFocusMinutes)
                    apply()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.example.util.FocusTimerManager.setTotalFocusMinutes(localTotalFocusMinutes + adoptedTotalFocusMinutes)
                }

                Log.d(TAG, "Successfully adopted cloud stats: AllTime=$adoptedAllTimeMs ms, TotalFocusMinutes=$adoptedTotalFocusMinutes")
            } catch (e: Exception) {
                Log.e(TAG, "Error in applyAdoptedStats background processing", e)
            }
        }
    }
}
