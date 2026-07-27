package com.example.timer

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.LocalHistoryVault
import com.example.util.SleepTimeHelper
import com.example.util.TimeEngine
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ManualLogRepository(
    private val database: AppDatabase,
    private val timerDao: TimerDao,
    private val gson: Gson
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * LOG MANUAL FOCUS STUDY SESSION WITH SPECIFIED RULES:
     * 1. Max time recorded in a single log is 4 hours (240 minutes).
     * 2. Max total manual focus time summed up per day is 12 hours (720 minutes).
     * @return Pair<Boolean, String> -> (Success status, UI Message/Reason)
     */
    suspend fun logManualStudySession(
        taskTitle: String,
        subjectTag: String,
        durationMinutes: Int
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (durationMinutes <= 0) return@withContext Pair(false, "Duration must be greater than 0 minutes.")

        // Rule 1: Max time that can be recorded in a single log is 4 hours (240 minutes)
        if (durationMinutes > 240) {
            return@withContext Pair(
                false,
                "The maximum focus time you can record in a single log is 4 hours (240 minutes)."
            )
        }

        val nowMs = System.currentTimeMillis()
        val durationMs = durationMinutes * 60 * 1000L
        val dateStr = dateFormat.format(Date(nowMs))

        // Rule 2: Max total manual focus time summed up per day is 12 hours (720 minutes)
        val todayManualFocusMs = database.localHistoryVaultDao().getTodayManualFocusTimeMs(dateStr)
        val existingManualMins = (todayManualFocusMs / 1000 / 60).toInt()

        if (existingManualMins + durationMinutes > 720) {
            val remainingMins = (720 - existingManualMins).coerceAtLeast(0)
            val remainingFormatted = if (remainingMins >= 60) "${remainingMins / 60}h ${remainingMins % 60}m" else "${remainingMins}m"
            return@withContext Pair(
                false,
                "Total manual focus logged today cannot exceed 12 hours (720 minutes). You have logged ${existingManualMins}m today ($remainingFormatted remaining)."
            )
        }

        // --- STEP 2: PREPARE RECORD WITH MANUAL_LOG MODE ---
        val approximatedStartMs = nowMs - durationMs
        val recordId = "manual_${nowMs}_${subjectTag.lowercase()}"

        val syntheticTimeline = listOf(
            com.example.api.TimelineEvent(deviceId = "manual", event = "start", timestamp = approximatedStartMs),
            com.example.api.TimelineEvent(deviceId = "manual", event = "session_end", timestamp = nowMs)
        )
        val syntheticTimelineJson = gson.toJson(syntheticTimeline)

        val manualVaultRecord = LocalHistoryVault(
            record_id = recordId,
            date_string = dateStr,
            subject = subjectTag,
            task_title = taskTitle, // Raw title preserved; mode handles the display badge
            start_time_ms = approximatedStartMs,
            end_time_ms = nowMs,
            total_focus_ms = durationMs,
            duration_formatted = TimeEngine.formatDuration(durationMs),
            start_time_formatted = TimeEngine.formatTimestamp(approximatedStartMs),
            end_time_formatted = TimeEngine.formatTimestamp(nowMs),
            is_synced_to_firestore = 0,
            mode = "MANUAL_LOG",
            lastModifiedMs = nowMs,
            isManualEntry = true,
            timeline_json = syntheticTimelineJson,
            timeline = syntheticTimeline
        )

        // --- STEP 3: ATOMIC ROOM TRANSACTION & DIRECT-TO-VAULT ROUTING ---
        database.withTransaction {
            // Save to local SQLite Vault
            timerDao.archiveToVault(manualVaultRecord)

            // Enqueue Outbox payload with explicit "MANUAL_LOG" mode stamp
            val cloudPayload = gson.toJson(mapOf(
                "recordId" to recordId,
                "dateString" to dateStr,
                "subject" to subjectTag,
                "taskTitle" to taskTitle,
                "mode" to "MANUAL_LOG", // Explicitly replaces Pomodoro/Stopwatch
                "metrics" to mapOf(
                    "totalFocusMs" to durationMs,
                    "durationFormatted" to TimeEngine.formatDuration(durationMs),
                    "startTimeFormatted" to TimeEngine.formatTimestamp(approximatedStartMs),
                    "endTimeFormatted" to TimeEngine.formatTimestamp(nowMs)
                ),
                "loggedByDevice" to "android_mobile_apk",
                "isManualEntry" to true
            ))

            timerDao.enqueueOutboxMutation(
                OutboxMutation(
                    mutationId = "mut_manual_$nowMs",
                    createdAtMs = nowMs,
                    routingTarget = "FIRESTORE_DIRECT_VAULT",
                    actionType = "ARCHIVE_SESSION",
                    payloadJson = cloudPayload
                )
            )
        }

        return@withContext Pair(true, "Successfully logged ${durationMinutes}m of manual study time!")
    }
}
