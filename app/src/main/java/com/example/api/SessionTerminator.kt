package com.example.api

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.util.TimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SessionPayload(
    val sessionId: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val timeline: List<TimelineEvent>
)

object SessionTerminator {
    private const val TAG = "SessionTerminator"

    suspend fun executeSessionTermination(
        context: Context,
        email: String,
        currentTimeline: List<TimelineEvent>,
        timerMode: String,
        currentTask: String,
        currentTag: String,
        originalSessionId: String
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        try {
            val myDevice = Build.MODEL
            val trueTime = TimeEngine.getTrueTimeMs()

            // Action 1: Append the final session_end event to the local Timeline list
            val finalEvent = TimelineEvent(deviceId = myDevice, event = "session_end", timestamp = trueTime)
            val updatedTimeline = currentTimeline + finalEvent

            // Action 2: Check for a Midnight Crossover
            val firstStartEvent = updatedTimeline.firstOrNull { it.event.lowercase() == "start" }
            val sessionStartFromId = originalSessionId.substringAfter("sess_").takeWhile { it.isDigit() }.toLongOrNull()
            val firstStartTs = if (sessionStartFromId != null && sessionStartFromId > 0L && sessionStartFromId <= trueTime) {
                sessionStartFromId
            } else {
                firstStartEvent?.timestamp ?: updatedTimeline.firstOrNull()?.timestamp ?: trueTime
            }

            val isSameDay = isSameDay(firstStartTs, trueTime)

            if (isSameDay) {
                // Standard Archival
                val payload = SessionPayload(
                    sessionId = originalSessionId,
                    startTimestamp = firstStartTs,
                    endTimestamp = trueTime,
                    timeline = updatedTimeline
                )
                FirestoreArchiver.archiveSessionPayload(context, email, payload, timerMode, currentTask, currentTag)
                
                // Increment the client-side weekly stats node
                val totalFocusMs = TimelineSyncEngine.calculateAccumulatedFocusMs(updatedTimeline, "session_end")
                WeeklyStatsUpdater.updateWeeklyStats(context, email, totalFocusMs, currentTag)
            } else {
                // Midnight Split
                val midnightBoundary = getMidnightBoundaryMs(trueTime)

                // Split into Payload A (Yesterday) and Payload B (Today)
                // Payload A (Yesterday): All events before midnightBoundary, plus synthetic session_end at midnight boundary
                val timelineA = updatedTimeline.filter { it.timestamp < midnightBoundary }.toMutableList()
                timelineA.add(TimelineEvent(deviceId = myDevice, event = "session_end", timestamp = midnightBoundary))

                val payloadA = SessionPayload(
                    sessionId = "${originalSessionId}_part1",
                    startTimestamp = firstStartTs,
                    endTimestamp = midnightBoundary,
                    timeline = timelineA
                )

                // Payload B (Today): Starts at midnightBoundary, contains synthetic start at boundary, plus events >= midnightBoundary
                val timelineB = mutableListOf<TimelineEvent>()
                timelineB.add(TimelineEvent(deviceId = myDevice, event = "start", timestamp = midnightBoundary))
                // Filter other events at or after midnightBoundary (excluding any final session_end that might be duplicated)
                timelineB.addAll(updatedTimeline.filter { it.timestamp >= midnightBoundary && it.event != "session_end" })
                timelineB.add(TimelineEvent(deviceId = myDevice, event = "session_end", timestamp = trueTime))

                val payloadB = SessionPayload(
                    sessionId = "${originalSessionId}_part2",
                    startTimestamp = midnightBoundary,
                    endTimestamp = trueTime,
                    timeline = timelineB
                )

                // Archive both payloads
                FirestoreArchiver.archiveSessionPayload(context, email, payloadA, timerMode, currentTask, currentTag)
                FirestoreArchiver.archiveSessionPayload(context, email, payloadB, timerMode, currentTask, currentTag)

                // Increment the weekly stats node for both split chunks
                val focusMsA = TimelineSyncEngine.calculateAccumulatedFocusMs(timelineA, "session_end")
                val focusMsB = TimelineSyncEngine.calculateAccumulatedFocusMs(timelineB, "session_end")
                WeeklyStatsUpdater.updateWeeklyStats(context, email, focusMsA, currentTag)
                WeeklyStatsUpdater.updateWeeklyStats(context, email, focusMsB, currentTag)
            }

            // Recheck and verify today's focus ms against system database records after session end
            DevicePresenceManager.recheckTodayFocusMsWithSystem(context, email)

            // Overwrite ACTIVE_FOCUS_TIMER in RTDB with {"Status": "Relaxing", "Command_Device_Name": "None"}
            shrinkActiveFocusTimerInRTDB(context, email)

            // Clear local active session ID and timeline
            DynamicCommandManager.activeSessionId = ""
            DynamicCommandManager.currentTimelineFlow.value = emptyList()
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .remove("active_session_id_rtdb")
                .remove("session_timeline_json")
                .apply()
        } finally {
            try {
                com.example.service.KeepAliveService.updateNotification(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update KeepAliveService notification on session_end", e)
            }
            com.example.util.WakeLockManager.release()
        }
    }

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance()
        cal1.timeInMillis = ts1
        val cal2 = java.util.Calendar.getInstance()
        cal2.timeInMillis = ts2
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun getMidnightBoundaryMs(endTs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = endTs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun shrinkActiveFocusTimerInRTDB(context: Context, email: String) {
        withContext(Dispatchers.IO) {
            try {
                val dbUrl = FirebaseConfig.getDatabaseUrl(context)
                if (dbUrl.isEmpty()) {
                    Log.e(TAG, "Database URL is empty, skipping RTDB shrink.")
                    return@withContext
                }
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
                val activeRef = database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("ACTIVE_FOCUS_TIMER")

                val shrinkPayload = mapOf<String, Any?>(
                    "Status" to "IDLE",
                    "Command_Device_Name" to "None",
                    "Timer_Mode" to null,
                    "Session_ID" to null,
                    "Current_Task" to null,
                    "Current_Tag" to null,
                    "Timeline" to null,
                    "Last_Updated" to com.google.firebase.database.ServerValue.TIMESTAMP
                )

                // Update children to preserve User_Emoji, User_Display_Name, etc.
                activeRef.updateChildren(shrinkPayload)
                activeRef.child("Client_Elapsed_Ms").removeValue()
                activeRef.child("Client_Heartbeat_Ms").removeValue()
                activeRef.child("Resynced_At").removeValue()
                activeRef.child("Resync_Source").removeValue()
                activeRef.child("Heartbeat_Timestamp").removeValue()
                activeRef.child("Current_Timer_Mode").removeValue()
                activeRef.child("Is_Timer_Running").removeValue()
                activeRef.child("Total_Elapsed_Ms").removeValue()
                Log.d(TAG, "Successfully shrank active RTDB node to IDLE status.")
            } catch (e: Exception) {
                Log.e(TAG, "Error shrinking active focus timer in RTDB", e)
            }
        }
    }
}
