package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LocalHistoryVault
import com.example.api.TimelineEvent
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.database.FirebaseDatabase
import com.example.util.TimeEngine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AnalyticsVaultEngine {
    private const val TAG = "AnalyticsVaultEngine"

    // Data classes to structure the aggregated metrics
    data class AggregatedMetrics(
        val totalFocusMs: Long = 0L,
        val totalBreakMs: Long = 0L,
        val totalFocusFormatted: String = "0h 0m 0s",
        val totalBreakFormatted: String = "0h 0m 0s",
        val focusToBreakRatioPercent: Float = 0f,
        val tagDistribution: Map<String, Long> = emptyMap(),
        val taskDistribution: Map<String, Long> = emptyMap()
    )

    enum class SegmentType {
        FOCUS, BREAK, IDLE
    }

    data class TimelineSegment(
        val type: SegmentType,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val taskTitle: String? = null,
        val tag: String? = null,
        val startOffsetPercent: Float = 0f,
        val endOffsetPercent: Float = 0f
    )

    /**
     * Formatting helper: converts milliseconds into clean "Xh Ym Zs" readable strings
     */
    fun formatMsToReadable(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m ${seconds}s"
        } else if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }

    /**
     * Safe mathematical focus-to-break efficiency percentage calculator
     */
    fun calculateRatio(focusMs: Long, breakMs: Long): Float {
        val total = focusMs + breakMs
        if (total == 0L) return 100f // Default to 100% study efficiency if zero time overall
        return (focusMs.toFloat() / total.toFloat()) * 100f
    }

    /**
     * Processes raw SQLite focus records from LocalHistoryVaultDao to aggregate metrics.
     */
    fun calculateMetricsForWindow(
        records: List<LocalHistoryVault>,
        startDateString: String? = null,
        endDateString: String? = null
    ): AggregatedMetrics {
        val filteredRecords = if (startDateString != null && endDateString != null) {
            records.filter { it.date_string in startDateString..endDateString }
        } else {
            records
        }

        var totalFocus = 0L
        var totalBreak = 0L
        val tagDistribution = mutableMapOf<String, Long>()
        val taskDistribution = mutableMapOf<String, Long>()

        for (record in filteredRecords) {
            totalFocus += record.total_focus_ms
            totalBreak += record.total_break_ms

            val tag = record.subject.ifBlank { "Study" }
            tagDistribution[tag] = (tagDistribution[tag] ?: 0L) + record.total_focus_ms

            val task = record.task_title ?: "General Session"
            taskDistribution[task] = (taskDistribution[task] ?: 0L) + record.total_focus_ms
        }

        return AggregatedMetrics(
            totalFocusMs = totalFocus,
            totalBreakMs = totalBreak,
            totalFocusFormatted = formatMsToReadable(totalFocus),
            totalBreakFormatted = formatMsToReadable(totalBreak),
            focusToBreakRatioPercent = calculateRatio(totalFocus, totalBreak),
            tagDistribution = tagDistribution,
            taskDistribution = taskDistribution
        )
    }

    /**
     * Daily Consistency Streak Calculator:
     * Iterates backward starting from today, checking consecutive days.
     * Streak breaks when a gap day is found (i.e. focus Ms falls below minimum target threshold).
     */
    fun calculateDailyConsistencyStreak(
        context: Context?,
        records: List<LocalHistoryVault>,
        thresholdMs: Long = 6 * 60 * 60 * 1000L // Default target is 6 hours of focus
    ): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Group total focus ms by date string
        val dailyTotals = records.groupBy { it.date_string }
            .mapValues { (_, dayRecords) -> dayRecords.sumOf { it.total_focus_ms } }

        // Fetch unconsumed shields locally from SQLite
        val unconsumedShields = if (context != null) {
            try {
                val db = AppDatabase.getInstance(context)
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    db.localShieldsVaultDao().getUnconsumedShieldsSync().toMutableList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching unconsumed shields for streak calculation", e)
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        var streak = 0
        val currentCal = Calendar.getInstance()
        val todayStr = sdf.format(currentCal.time)
        val todayFocus = dailyTotals[todayStr] ?: 0L
        val tenHoursMs = 10L * 60 * 60 * 1000L // 10 hours focus threshold to retrieve streak
        val canRetrieveStreak = todayFocus >= tenHoursMs
        var isFirstDay = true

        while (true) {
            val dateStr = sdf.format(currentCal.time)
            val dailyFocus = dailyTotals[dateStr] ?: 0L

            if (dailyFocus >= thresholdMs) {
                streak++
                currentCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                // If it is today and we haven't met the target yet, we skip today without consuming a shield
                if (isFirstDay && dateStr == todayStr) {
                    currentCal.add(Calendar.DAY_OF_YEAR, -1)
                    isFirstDay = false
                    continue
                }

                // Gap day! Check if we can retrieve the streak using the 10-hour focus rule
                if (canRetrieveStreak) {
                    // Gap is bridged via the 10-hour focus retrieval rule!
                    streak++
                    currentCal.add(Calendar.DAY_OF_YEAR, -1)
                    if (context != null) {
                        try {
                            com.example.api.FocusLogManager.logEvent(context, "Streak retrieved back! Focused for 10 hours today to bridge gap day on $dateStr.")
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                } else if (unconsumedShields.isNotEmpty()) {
                    val installDateStr = if (context != null) {
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        var inst = prefs.getString("user_install_date", "") ?: ""
                        if (inst.isEmpty()) {
                            inst = todayStr
                            prefs.edit().putString("user_install_date", inst).apply()
                        }
                        inst
                    } else {
                        ""
                    }

                    if (installDateStr.isNotEmpty() && dateStr < installDateStr) {
                        break
                    }

                    if (streak < 1) {
                        // Shields are only used if streak is 1 or more
                        break
                    }

                    // Gap day! Check if we have shields to bridge the gap
                    val shield = unconsumedShields.removeAt(0)

                    if (context != null) {
                        try {
                            val db = AppDatabase.getInstance(context)
                            runBlocking {
                                db.localShieldsVaultDao().markShieldConsumed(shield.uuid, true, dateStr)
                            }
                            com.example.api.FocusLogManager.logEvent(context, "Streak Shield ('${shield.donor_name}') consumed to bridge gap day on $dateStr and protect study streak!")

                            // Sync consumed state & gap date to RTDB cloud immediately
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dbUrl = FirebaseConfig.getDatabaseUrl(context)
                                    if (dbUrl.isNotEmpty()) {
                                        val rtdb = FirebaseDatabase.getInstance(dbUrl)
                                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                        val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                                        val savedUsername = prefs.getString("current_username", "Guest") ?: "Guest"
                                        val myEmail = googleAccount?.email ?: prefs.getString("user_email_$savedUsername", "") ?: "$savedUsername@gmail.com"
                                        if (myEmail.isNotEmpty()) {
                                            val sanitized = DevicePresenceManager.sanitizeEmail(myEmail)
                                            val updates = mapOf<String, Any?>(
                                                "Is_Consumed" to true,
                                                "Consumed_Date" to dateStr,
                                                "is_consumed" to true,
                                                "consumed_date" to dateStr
                                            )
                                            rtdb.getReference("FOCUS_TIMMER")
                                                .child("USER")
                                                .child(sanitized)
                                                .child("SHIELDS")
                                                .child(shield.uuid)
                                                .updateChildren(updates)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to sync consumed shield ${shield.uuid} in cloud", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to consume shield locally", e)
                        }
                    }

                    // Gap is bridged!
                    streak++
                    currentCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    // No shields left, streak breaks
                    break
                }
            }
            isFirstDay = false
        }

        return streak
    }

    // Overload for backward compatibility
    fun calculateDailyConsistencyStreak(
        records: List<LocalHistoryVault>,
        thresholdMs: Long = 6 * 60 * 60 * 1000L
    ): Int {
        return calculateDailyConsistencyStreak(null, records, thresholdMs)
    }

    /**
     * Parses the LocalHistoryVault serialized timeline JSON array back into typed TimelineEvents.
     */
    fun parseTimelineEvents(jsonString: String?): List<TimelineEvent> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<TimelineEvent>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val deviceId = obj.optString("deviceId", "")
                val event = obj.optString("event", "")
                val timestamp = obj.optLong("timestamp", 0L)
                list.add(TimelineEvent(deviceId, event, timestamp))
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error parsing timeline JSON: $jsonString", e)
        }
        return list
    }

    /**
     * Chronological Day-Block Reconstructor:
     * Takes a single day's records, maps study segments, and constructs a continuous timeline from 00:00:00 to 23:59:59.
     */
    fun reconstructDayBlocks(
        dateString: String,
        dayRecords: List<LocalHistoryVault>,
        zoomToActiveWindow: Boolean = false
    ): List<TimelineSegment> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = try { sdf.parse(dateString) } catch (e: java.lang.Exception) { null }
        val dayStartMs = parsedDate?.time ?: System.currentTimeMillis()
        val dayEndMs = dayStartMs + 24 * 60 * 60 * 1000L - 1L

        val activeSegments = mutableListOf<TimelineSegment>()

        for (record in dayRecords) {
            val events = parseTimelineEvents(record.timeline_json)
            if (events.isNotEmpty()) {
                val sortedEvents = events.sortedBy { it.timestamp }
                var currentType: SegmentType? = null
                var segmentStart = record.start_time_ms

                for (event in sortedEvents) {
                    val eventName = event.event.lowercase()
                    if (currentType != null) {
                        // Close existing segment
                        activeSegments.add(
                            TimelineSegment(
                                type = currentType,
                                startTimeMs = segmentStart,
                                endTimeMs = event.timestamp,
                                taskTitle = record.task_title,
                                tag = record.subject
                            )
                        )
                    }

                    // Transition to new state
                    when {
                        eventName.contains("start") || eventName.contains("resume") -> {
                            currentType = SegmentType.FOCUS
                            segmentStart = event.timestamp
                        }
                        eventName.contains("pause") || eventName.contains("break_start") || eventName.contains("break start") -> {
                            currentType = SegmentType.BREAK
                            segmentStart = event.timestamp
                        }
                        eventName.contains("break_end") || eventName.contains("break end") -> {
                            currentType = SegmentType.FOCUS
                            segmentStart = event.timestamp
                        }
                        eventName.contains("end") || eventName.contains("session_end") -> {
                            currentType = null
                        }
                    }
                }

                // Close any remaining open segment
                if (currentType != null) {
                    activeSegments.add(
                        TimelineSegment(
                            type = currentType,
                            startTimeMs = segmentStart,
                            endTimeMs = record.end_time_ms,
                            taskTitle = record.task_title,
                            tag = record.subject
                        )
                    )
                }
            } else {
                // Synthesize focus/break segments for records without timeline JSON (e.g. manual entry or compiled entries)
                val focusEnd = record.start_time_ms + record.total_focus_ms
                activeSegments.add(
                    TimelineSegment(
                        type = SegmentType.FOCUS,
                        startTimeMs = record.start_time_ms,
                        endTimeMs = focusEnd,
                        taskTitle = record.task_title,
                        tag = record.subject
                    )
                )
                if (record.total_break_ms > 0L) {
                    activeSegments.add(
                        TimelineSegment(
                            type = SegmentType.BREAK,
                            startTimeMs = focusEnd,
                            endTimeMs = record.end_time_ms,
                            taskTitle = record.task_title,
                            tag = record.subject
                        )
                    )
                }
            }
        }

        // Sort compiled segments by start time
        val sortedActive = activeSegments.sortedBy { it.startTimeMs }

        // Establish the timeline boundaries (either absolute 24h, or zoomed bounds)
        val timelineStart = if (zoomToActiveWindow && sortedActive.isNotEmpty()) {
            maxOf(dayStartMs, sortedActive.first().startTimeMs)
        } else {
            dayStartMs
        }

        val timelineEnd = if (zoomToActiveWindow && sortedActive.isNotEmpty()) {
            minOf(dayEndMs, sortedActive.last().endTimeMs)
        } else {
            dayEndMs
        }

        val durationRange = timelineEnd - timelineStart
        if (durationRange <= 0L) return emptyList()

        val continuousList = mutableListOf<TimelineSegment>()
        var currentPointer = timelineStart

        for (segment in sortedActive) {
            val startClamped = maxOf(timelineStart, minOf(timelineEnd, segment.startTimeMs))
            val endClamped = maxOf(timelineStart, minOf(timelineEnd, segment.endTimeMs))

            if (startClamped >= timelineEnd) break

            // 1. Add IDLE gap segment before the active segment
            if (startClamped > currentPointer) {
                continuousList.add(
                    TimelineSegment(
                        type = SegmentType.IDLE,
                        startTimeMs = currentPointer,
                        endTimeMs = startClamped
                    )
                )
            }

            // 2. Add the active FOCUS / BREAK segment
            if (endClamped > currentPointer && endClamped > startClamped) {
                continuousList.add(
                    segment.copy(
                        startTimeMs = maxOf(currentPointer, startClamped),
                        endTimeMs = endClamped
                    )
                )
                currentPointer = endClamped
            }
        }

        // 3. Close final IDLE gap if there is space left
        if (currentPointer < timelineEnd) {
            continuousList.add(
                TimelineSegment(
                    type = SegmentType.IDLE,
                    startTimeMs = currentPointer,
                    endTimeMs = timelineEnd
                )
            )
        }

        // 4. Map final relative percentages for layout rendering
        return continuousList.map { seg ->
            seg.copy(
                startOffsetPercent = (seg.startTimeMs - timelineStart).toFloat() / durationRange.toFloat(),
                endOffsetPercent = (seg.endTimeMs - timelineStart).toFloat() / durationRange.toFloat()
            )
        }
    }

    /**
     * Lazy Firestore Ledger Fallback Path:
     * Executes single targeted fetch to Firestore if local SQLite is empty for that date, caching results locally.
     */
    suspend fun fetchAndCacheDailyLedgerFallback(
        context: Context,
        email: String,
        dateString: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (email.isBlank() || dateString.isBlank()) return@withContext false

        try {
            val db = AppDatabase.getInstance(context)
            // Safety Check: Verify if local records indeed returned 0
            val localCount = db.localHistoryVaultDao().getTodayTotalFocusTimeMs(dateString)
            if (localCount > 0) {
                Log.d(TAG, "No fetch required, local SQLite already has $localCount ms of focus records for $dateString")
                return@withContext true
            }

            Log.d(TAG, "Local database returned zero records for $dateString. Initiating lazy Firestore fallback fetch...")

            val firestore = FirebaseFirestore.getInstance(
                com.google.firebase.FirebaseApp.getInstance(),
                "main"
            )

            val documentSnapshot = firestore.collection("users").document(email)
                .collection("compiled_daily_records").document(dateString)
                .get()
                .awaitTask()

            if (documentSnapshot.exists()) {
                val totalFocusMs = documentSnapshot.getLong("Total_Focus_Time_Ms") ?: 0L
                val totalBreakMs = documentSnapshot.getLong("Total_Break_Time_Ms") ?: 0L
                val sessionCount = documentSnapshot.getLong("Session_Count") ?: 1L
                val dateStrFromDoc = documentSnapshot.getString("Date_String") ?: dateString

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val parsedDate = try { sdf.parse(dateStrFromDoc) } catch (e: Exception) { null }
                val startOfDayMs = parsedDate?.time ?: System.currentTimeMillis()
                val endOfDayMs = startOfDayMs + totalFocusMs + totalBreakMs

                val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
                val startFormatted = sdfTime.format(Date(startOfDayMs))
                val endFormatted = sdfTime.format(Date(endOfDayMs))

                // Construct compiled LocalHistoryVault entry
                val compiledRecord = LocalHistoryVault(
                    record_id = "compiled_ledger_${dateString}",
                    date_string = dateStrFromDoc,
                    subject = "Compiled Study",
                    task_title = "Cloud Merged Session ($sessionCount sessions)",
                    start_time_ms = startOfDayMs,
                    end_time_ms = endOfDayMs,
                    total_focus_ms = totalFocusMs,
                    total_break_ms = totalBreakMs,
                    pause_count = sessionCount.toInt(),
                    duration_formatted = formatMsToReadable(totalFocusMs),
                    start_time_formatted = startFormatted,
                    end_time_formatted = endFormatted,
                    is_synced_to_firestore = 1,
                    mode = "POMODORO"
                )

                // Cache directly in local Room database
                db.localHistoryVaultDao().insertRecord(compiledRecord)
                Log.d(TAG, "Successfully compiled and cached Cloud daily record into local SQLite for $dateString")
                return@withContext true
            } else {
                Log.d(TAG, "No compiled record found in cloud for date: $dateString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing targeted lazy Firestore ledger sync for $dateString", e)
        }
        return@withContext false
    }

    /**
     * Firebase Document Task await extension wrapper
     */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Firestore targeted fetch task failed"))
            }
        }
    }
}
