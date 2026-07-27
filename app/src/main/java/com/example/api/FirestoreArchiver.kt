package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LocalHistoryVault
import com.example.data.OutboxQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirestoreArchiver {
    private const val TAG = "FirestoreArchiver"

    fun docToVaultRecord(doc: com.google.firebase.firestore.DocumentSnapshot, fallbackEmail: String = ""): LocalHistoryVault {
        val sessionId = doc.getString("Session_ID")
            ?: doc.getString("recordId")
            ?: doc.getString("sessionId")
            ?: doc.id

        val userEmail = doc.getString("user_email")
            ?: doc.getString("userEmail")
            ?: doc.getString("userid")
            ?: fallbackEmail

        val currentTag = doc.getString("Current_Tag")
            ?: doc.getString("subject")
            ?: doc.getString("tag")
            ?: "Study"

        val currentTask = doc.getString("Current_Task")
            ?: doc.getString("taskTitle")
            ?: doc.getString("task_title")
            ?: ""

        val timerMode = doc.getString("Timer_Mode")
            ?: doc.getString("mode")
            ?: "POMODORO"

        val totalFocusMs = doc.getLong("Total_Focus_Time_Ms")
            ?: doc.getLong("totalFocusMs")
            ?: doc.getLong("duration_ms")
            ?: ((doc.getLong("durationSeconds") ?: 0L) * 1000L)

        val totalBreakMs = doc.getLong("Total_Break_Time_Ms")
            ?: doc.getLong("totalBreakMs")
            ?: 0L

        val startTimestamp = doc.getLong("Start_Timestamp")
            ?: doc.getLong("startTimeMs")
            ?: 0L

        val endTimestamp = doc.getLong("End_Timestamp")
            ?: doc.getLong("endTimeMs")
            ?: (if (startTimestamp > 0L && totalFocusMs > 0L) startTimestamp + totalFocusMs else 0L)

        val totalFocusFormatted = doc.getString("Total_Focus_Time_Formatted")
            ?: doc.getString("durationFormatted")
            ?: TimelineSyncEngine.formatTimeMsToHhMmSs(totalFocusMs)

        val totalBreakFormatted = doc.getString("Total_Break_Time_Formatted")
            ?: TimelineSyncEngine.formatTimeMsToHhMmSs(totalBreakMs)

        val timelineList = mutableListOf<TimelineEvent>()
        val rawTimeline = (doc.get("Timeline") ?: doc.get("timeline")) as? List<Map<String, Any>>
        if (rawTimeline != null) {
            for (item in rawTimeline) {
                val deviceId = item["deviceId"] as? String ?: ""
                val event = item["event"] as? String ?: ""
                val timestamp = (item["timestamp"] as? Number)?.toLong() ?: 0L
                if (event.isNotEmpty()) {
                    timelineList.add(TimelineEvent(deviceId, event, timestamp))
                }
            }
        }

        val pauseCount = (doc.getLong("pauseCount")?.toInt())
            ?: timelineList.count { it.event.lowercase() == "paused" || it.event.lowercase() == "break_started" }

        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
        val startTimeFormatted = doc.getString("startTimeFormatted")
            ?: if (startTimestamp > 0L) sdfTime.format(Date(startTimestamp)) else "00:00:00"
        val endTimeFormatted = doc.getString("endTimeFormatted")
            ?: if (endTimestamp > 0L) sdfTime.format(Date(endTimestamp)) else "00:00:00"

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = doc.getString("dateString")
            ?: doc.getString("Date_String")
            ?: (if (startTimestamp > 0L) sdfDate.format(Date(startTimestamp)) else sdfDate.format(Date()))

        val timelineJsonArray = JSONArray()
        for (event in timelineList) {
            val eventObj = JSONObject()
            eventObj.put("deviceId", event.deviceId)
            eventObj.put("event", event.event)
            eventObj.put("timestamp", event.timestamp)
            timelineJsonArray.put(eventObj)
        }

        return LocalHistoryVault(
            record_id = sessionId,
            date_string = dateString,
            subject = if (currentTag.isNotEmpty()) currentTag else "Study",
            task_title = currentTask,
            start_time_ms = startTimestamp,
            end_time_ms = endTimestamp,
            total_focus_ms = totalFocusMs,
            total_break_ms = totalBreakMs,
            pause_count = pauseCount,
            duration_formatted = totalFocusFormatted,
            start_time_formatted = startTimeFormatted,
            end_time_formatted = endTimeFormatted,
            is_synced_to_firestore = 1,
            mode = timerMode.uppercase(),
            timeline_json = timelineJsonArray.toString(),
            timeline = timelineList,
            userEmail = userEmail
        )
    }

    suspend fun pullAndSyncFocusHistoryFromFirestore(context: Context, email: String): Pair<Boolean, String> {
        if (email.isBlank()) {
            return Pair(false, "User email is blank")
        }
        if (!com.example.util.NetworkChecker.isOnline(context)) {
            return Pair(false, "Device is offline")
        }

        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(
                com.google.firebase.FirebaseApp.getInstance(),
                "main"
            )

            val documentsMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            val emailsToQuery = listOf(sanitizedEmail).filter { it.isNotEmpty() }

            for (eKey in emailsToQuery) {
                if (eKey.isEmpty()) continue
                // Query focus_records
                try {
                    val snap1 = suspendCancellableCoroutine<com.google.firebase.firestore.QuerySnapshot?> { cont ->
                        firestore.collection("users").document(eKey)
                            .collection("focus_records")
                            .get()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                            }
                    }
                    snap1?.documents?.forEach { doc ->
                        val sId = doc.getString("Session_ID") ?: doc.getString("recordId") ?: doc.id
                        if (sId.isNotEmpty()) documentsMap[sId] = doc
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying focus_records for $eKey: ${e.message}")
                }

                // Query focus_history
                try {
                    val snap2 = suspendCancellableCoroutine<com.google.firebase.firestore.QuerySnapshot?> { cont ->
                        firestore.collection("users").document(eKey)
                            .collection("focus_history")
                            .get()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                            }
                    }
                    snap2?.documents?.forEach { doc ->
                        val sId = doc.getString("Session_ID") ?: doc.getString("recordId") ?: doc.id
                        if (sId.isNotEmpty() && !documentsMap.containsKey(sId)) {
                            documentsMap[sId] = doc
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying focus_history for $eKey: ${e.message}")
                }

                // Query daily_records/{date}/sessions
                try {
                    val snapDaily = suspendCancellableCoroutine<com.google.firebase.firestore.QuerySnapshot?> { cont ->
                        firestore.collection("users").document(eKey)
                            .collection("daily_records")
                            .get()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                            }
                    }
                    snapDaily?.documents?.forEach { dateDoc ->
                        val dateStr = dateDoc.id
                        try {
                            val snapSessions = suspendCancellableCoroutine<com.google.firebase.firestore.QuerySnapshot?> { cont ->
                                firestore.collection("users").document(eKey)
                                    .collection("daily_records").document(dateStr)
                                    .collection("sessions")
                                    .get()
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                                    }
                            }
                            snapSessions?.documents?.forEach { doc ->
                                val sId = doc.getString("Session_ID") ?: doc.getString("recordId") ?: doc.id
                                if (sId.isNotEmpty() && !documentsMap.containsKey(sId)) {
                                    documentsMap[sId] = doc
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error querying daily_records/$dateStr/sessions for $eKey: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying daily_records for $eKey: ${e.message}")
                }
            }

            val db = AppDatabase.getInstance(context)
            var count = 0

            for ((_, doc) in documentsMap) {
                val vaultRecord = docToVaultRecord(doc, sanitizedEmail)
                if (vaultRecord.record_id.isNotEmpty() && vaultRecord.total_focus_ms > 0L) {
                    db.localHistoryVaultDao().insertRecord(vaultRecord)
                    count++
                }
            }

            // Also query Realtime Database nodes for history files if present
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    val userRef = database.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)
                    val pathsToTry = listOf("focusTimer/historyFiles", "historyFiles", "HISTORY", "focus_records")
                    for (path in pathsToTry) {
                        val rtdbSnap = suspendCancellableCoroutine<com.google.firebase.database.DataSnapshot?> { cont ->
                            userRef.child(path).get().addOnCompleteListener { task ->
                                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                            }
                        }
                        if (rtdbSnap != null && rtdbSnap.exists()) {
                            for (dateChild in rtdbSnap.children) {
                                val dateKey = dateChild.key ?: ""
                                val rawVal = dateChild.value
                                if (rawVal is String && rawVal.isNotBlank()) {
                                    val lines = rawVal.split("\n")
                                    for (line in lines) {
                                        val parts = line.split("|")
                                        if (parts.size >= 5) {
                                            val startStr = parts[0]
                                            val endStr = parts[1]
                                            val taskTitle = parts[2]
                                            val mins = parts[3].toIntOrNull() ?: 0
                                            val dStr = if (parts[4].isNotBlank()) parts[4] else dateKey
                                            val notes = if (parts.size >= 6) {
                                                try { String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP)) } catch (e: Exception) { parts[5] }
                                            } else ""
                                            val secs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (mins * 60) else (mins * 60)
                                            val tag = if (parts.size >= 8) parts[7] else "Study"
                                            val id = if (parts.size >= 9 && parts[8].isNotBlank()) parts[8] else "rtdb_${dStr}_${kotlin.math.abs((startStr + taskTitle).hashCode())}"
                                            val breakMs = if (parts.size >= 10) parts[9].toLongOrNull() ?: 0L else 0L

                                            if (secs > 0 || mins > 0) {
                                                val focusMs = maxOf(secs * 1000L, mins * 60000L)
                                                val nowMs = System.currentTimeMillis()
                                                val vaultRec = LocalHistoryVault(
                                                    record_id = id,
                                                    date_string = dStr,
                                                    subject = tag.ifEmpty { "Study" },
                                                    task_title = taskTitle.ifEmpty { "Focus Session" },
                                                    start_time_ms = nowMs - focusMs,
                                                    end_time_ms = nowMs,
                                                    total_focus_ms = focusMs,
                                                    total_break_ms = breakMs,
                                                    pause_count = 0,
                                                    duration_formatted = com.example.util.TimeEngine.formatDuration(focusMs),
                                                    start_time_formatted = startStr,
                                                    end_time_formatted = endStr,
                                                    is_synced_to_firestore = 1,
                                                    mode = notes.ifEmpty { "POMODORO" },
                                                    userEmail = sanitizedEmail
                                                )
                                                db.localHistoryVaultDao().insertRecord(vaultRec)
                                                count++
                                            }
                                        }
                                    }
                                } else if (dateChild.hasChildren()) {
                                    for (sessChild in dateChild.children) {
                                        val rawKey = sessChild.key ?: ""
                                        val taskTitle = sessChild.child("taskTitle").getValue(String::class.java)
                                            ?: sessChild.child("task_title").getValue(String::class.java)
                                            ?: sessChild.child("subject").getValue(String::class.java)
                                            ?: "Focus Session"
                                        val tag = sessChild.child("tag").getValue(String::class.java)
                                            ?: sessChild.child("subject").getValue(String::class.java)
                                            ?: "Study"
                                        val durSecs = sessChild.child("durationSeconds").getValue(Long::class.java)
                                            ?: sessChild.child("duration_seconds").getValue(Long::class.java)
                                            ?: ((sessChild.child("durationMinutes").getValue(Long::class.java) ?: 0L) * 60L)
                                        val rawFocusMs = sessChild.child("totalFocusMs").getValue(Long::class.java)
                                            ?: sessChild.child("total_focus_ms").getValue(Long::class.java)
                                            ?: (durSecs * 1000L)
                                        val focusMs = minOf(86_400_000L, rawFocusMs)

                                        if (focusMs > 0L) {
                                            val dStr = sessChild.child("dateString").getValue(String::class.java)
                                                ?: sessChild.child("date_string").getValue(String::class.java)
                                                ?: dateKey
                                            val id = if (rawKey.isNotBlank() && rawKey != "null" && rawKey != "undefined") rawKey else "rtdb_sess_${dStr}_${kotlin.math.abs((taskTitle + focusMs).hashCode())}"
                                            val startStr = sessChild.child("startTime").getValue(String::class.java) ?: "00:00:00"
                                            val endStr = sessChild.child("endTime").getValue(String::class.java) ?: "00:00:00"
                                            val nowMs = System.currentTimeMillis()
                                            val startMsVal = sessChild.child("startTimeMs").getValue(Long::class.java)
                                                ?: sessChild.child("Start_Timestamp").getValue(Long::class.java)
                                                ?: sessChild.child("start_time_ms").getValue(Long::class.java)
                                                ?: (nowMs - focusMs)
                                            val endMsVal = sessChild.child("endTimeMs").getValue(Long::class.java)
                                                ?: sessChild.child("End_Timestamp").getValue(Long::class.java)
                                                ?: sessChild.child("end_time_ms").getValue(Long::class.java)
                                                ?: (startMsVal + focusMs)

                                            val vaultRec = LocalHistoryVault(
                                                record_id = id,
                                                date_string = dStr,
                                                subject = tag,
                                                task_title = taskTitle,
                                                start_time_ms = startMsVal,
                                                end_time_ms = endMsVal,
                                                total_focus_ms = focusMs,
                                                total_break_ms = 0L,
                                                pause_count = 0,
                                                duration_formatted = com.example.util.TimeEngine.formatDuration(focusMs),
                                                start_time_formatted = startStr,
                                                end_time_formatted = endStr,
                                                is_synced_to_firestore = 1,
                                                mode = "POMODORO",
                                                userEmail = sanitizedEmail
                                            )
                                            db.localHistoryVaultDao().insertRecord(vaultRec)
                                            count++
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying RTDB history files: ${e.message}")
                }
            }

            // Purge any legacy synthetic "Cloud Synced Focus Session" records from local SQLite
            try {
                val fakeRecords = db.localHistoryVaultDao().getAllHistoryDirect().filter {
                    it.record_id.startsWith("synced_cloud_") || it.task_title == "Cloud Synced Focus Session"
                }
                for (fake in fakeRecords) {
                    db.localHistoryVaultDao().deleteRecord(fake)
                    Log.i(TAG, "Purged synthetic focus session record ${fake.record_id} from local vault")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error purging synthetic focus records: ${e.message}")
            }

            Log.d(TAG, "Successfully pulled and synced $count records from Firestore to SQLite.")
            com.example.util.FocusTimerManager.reloadFocusRecordsFromDb(context)
            WeeklyStatsUpdater.updateWeeklyStats(context, email, 0L, "")
            DevicePresenceManager.updateDeviceFocusStats(context, email)
            ArenaLeaderboardEngine.recomputeLeaderboard(email)

            return Pair(true, "Successfully synchronized $count sessions from cloud.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in pullAndSyncFocusHistoryFromFirestore", e)
            return Pair(false, e.message ?: "Unknown error")
        }
    }

    suspend fun fetchSingleSessionFromFirestore(context: Context, email: String, sessionId: String): LocalHistoryVault? {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email).ifEmpty { email }
        val trimmedId = sessionId.trim()
        if (trimmedId.isBlank()) return null

        val db = AppDatabase.getInstance(context)
        val localRec = db.localHistoryVaultDao().getRecordById(trimmedId)
        if (localRec != null) return localRec

        if (!com.example.util.NetworkChecker.isOnline(context)) return null

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(
                com.google.firebase.FirebaseApp.getInstance(),
                "main"
            )

            val collectionsToTry = listOf("focus_records", "focus_history")
            for (col in collectionsToTry) {
                val docSnap = suspendCancellableCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { cont ->
                    firestore.collection("users").document(sanitizedEmail)
                        .collection(col).document(trimmedId)
                        .get()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
                        }
                }
                if (docSnap != null && docSnap.exists()) {
                    val vaultRecord = docToVaultRecord(docSnap, email)
                    db.localHistoryVaultDao().insertRecord(vaultRecord)
                    com.example.util.FocusTimerManager.reloadFocusRecordsFromDb(context)
                    return vaultRecord
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching single session $trimmedId from Firestore", e)
        }
        return null
    }

    suspend fun archiveSessionPayload(
        context: Context,
        email: String,
        payload: SessionPayload,
        timerMode: String,
        currentTask: String,
        currentTag: String
    ) {
        val sessionId = payload.sessionId
        val startTimestamp = payload.startTimestamp
        val endTimestamp = payload.endTimestamp
        val timeline = payload.timeline

        val totalFocusMs = TimelineSyncEngine.calculateAccumulatedFocusMs(timeline, "session_end")
        val totalBreakMs = TimelineSyncEngine.calculateAccumulatedBreakMs(timeline, "session_end")

        val totalFocusFormatted = TimelineSyncEngine.formatTimeMsToHhMmSs(totalFocusMs)
        val totalBreakFormatted = TimelineSyncEngine.formatTimeMsToHhMmSs(totalBreakMs)

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = sdfDate.format(Date(startTimestamp))

        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)

        // Construct unified Firestore payload map (both camelCase and PascalCase keys)
        val payloadMap = hashMapOf<String, Any>(
            "Session_ID" to sessionId,
            "recordId" to sessionId,
            "user_email" to sanitizedEmail,
            "userEmail" to sanitizedEmail,
            "userid" to sanitizedEmail,
            "Current_Tag" to currentTag,
            "subject" to currentTag,
            "Current_Task" to currentTask,
            "taskTitle" to currentTask,
            "Timer_Mode" to timerMode,
            "mode" to timerMode,
            "Total_Focus_Time_Formatted" to totalFocusFormatted,
            "durationFormatted" to totalFocusFormatted,
            "Total_Break_Time_Formatted" to totalBreakFormatted,
            "Total_Focus_Time_Ms" to totalFocusMs,
            "totalFocusMs" to totalFocusMs,
            "Total_Break_Time_Ms" to totalBreakMs,
            "totalBreakMs" to totalBreakMs,
            "Start_Timestamp" to startTimestamp,
            "startTimeMs" to startTimestamp,
            "End_Timestamp" to endTimestamp,
            "endTimeMs" to endTimestamp,
            "dateString" to dateString,
            "Date_String" to dateString,
            "isDeleted" to false,
            "Timeline" to timeline.map {
                mapOf(
                    "deviceId" to it.deviceId,
                    "event" to it.event,
                    "timestamp" to it.timestamp
                )
            }
        )

        var isSyncedSuccessfully = false

        // 1. Primary Upload: Attempt direct Firestore set to all relevant collections
        try {
            if (com.example.util.NetworkChecker.isOnline(context)) {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(
                    com.google.firebase.FirebaseApp.getInstance(),
                    "main"
                )

                kotlinx.coroutines.withTimeout(5000L) {
                    firestore.collection("users").document(sanitizedEmail)
                        .collection("focus_records").document(sessionId)
                        .set(payloadMap, com.google.firebase.firestore.SetOptions.merge())
                        .awaitTask()

                    firestore.collection("users").document(sanitizedEmail)
                        .collection("focus_history").document(sessionId)
                        .set(payloadMap, com.google.firebase.firestore.SetOptions.merge())
                        .awaitTask()

                    firestore.collection("users").document(sanitizedEmail)
                        .collection("daily_records").document(dateString)
                        .collection("sessions").document(sessionId)
                        .set(payloadMap, com.google.firebase.firestore.SetOptions.merge())
                        .awaitTask()
                }

                isSyncedSuccessfully = true
                Log.d(TAG, "Successfully uploaded focus record to Firestore: $sessionId")
            } else {
                Log.d(TAG, "Device is offline. Skipping direct Firestore upload for $sessionId and queueing in local outbox.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload focus record direct to Firestore: $sessionId. Will queue in local outbox.", e)
        }

        // 2. Local SQLite Backup: Save the exact same data to the Room database
        val pauseCount = timeline.count { it.event.lowercase() == "paused" || it.event.lowercase() == "break_started" }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
        val startTimeFormatted = sdfTime.format(Date(startTimestamp))
        val endTimeFormatted = sdfTime.format(Date(endTimestamp))

        // Serialize timeline to JSON
        val timelineJsonArray = JSONArray()
        for (event in timeline) {
            val eventObj = JSONObject()
            eventObj.put("deviceId", event.deviceId)
            eventObj.put("event", event.event)
            eventObj.put("timestamp", event.timestamp)
            timelineJsonArray.put(eventObj)
        }
        val timelineJsonString = timelineJsonArray.toString()

        val vaultRecord = LocalHistoryVault(
            record_id = sessionId,
            date_string = dateString,
            subject = if (currentTag.isNotEmpty()) currentTag else "Study",
            task_title = currentTask,
            start_time_ms = startTimestamp,
            end_time_ms = endTimestamp,
            total_focus_ms = totalFocusMs,
            total_break_ms = totalBreakMs,
            pause_count = pauseCount,
            duration_formatted = totalFocusFormatted,
            start_time_formatted = startTimeFormatted,
            end_time_formatted = endTimeFormatted,
            is_synced_to_firestore = if (isSyncedSuccessfully) 1 else 0,
            mode = timerMode.uppercase(),
            timeline_json = timelineJsonString,
            timeline = timeline,
            userEmail = sanitizedEmail
        )

        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                db.localHistoryVaultDao().insertRecord(vaultRecord)
                Log.d(TAG, "Successfully backed up focus record to SQLite: $sessionId")
            } catch (dbEx: Exception) {
                Log.e(TAG, "Failed to write local SQLite backup for $sessionId", dbEx)
            }
        }

        // 3. Outbox Fallback: If direct upload failed, serialize and save to Room Outbox table
        if (!isSyncedSuccessfully) {
            val payloadJsonStr = serializePayloadToJson(payloadMap)
            val outboxItem = OutboxQueue(
                mutation_id = "mut_arch_${UUID.randomUUID()}",
                created_at_ms = com.example.util.TimeEngine.getTrueTimeMs(),
                routing_target = "FIRESTORE",
                action_type = "ARCHIVE_SESSION",
                payload_json = payloadJsonStr,
                retry_count = 0,
                status = "PENDING"
            )

            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.outboxQueueDao().insertQueueItemRaw(outboxItem)
                    Log.d(TAG, "Successfully queued unsynced focus record $sessionId in local Outbox queue.")
                } catch (dbEx: Exception) {
                    Log.e(TAG, "Failed to enqueue outbox fallback item for $sessionId", dbEx)
                }
            }
        }
    }

    private fun serializePayloadToJson(payload: Map<String, Any>): String {
        val json = JSONObject()
        val sessionId = (payload["Session_ID"] ?: payload["recordId"] ?: "").toString()
        val tag = (payload["Current_Tag"] ?: payload["subject"] ?: "").toString()
        val task = (payload["Current_Task"] ?: payload["taskTitle"] ?: "").toString()
        val mode = (payload["Timer_Mode"] ?: payload["mode"] ?: "POMODORO").toString()
        val focusMs = (payload["Total_Focus_Time_Ms"] as? Number)?.toLong()
            ?: (payload["totalFocusMs"] as? Number)?.toLong() ?: 0L
        val breakMs = (payload["Total_Break_Time_Ms"] as? Number)?.toLong()
            ?: (payload["totalBreakMs"] as? Number)?.toLong() ?: 0L
        val startTs = (payload["Start_Timestamp"] as? Number)?.toLong()
            ?: (payload["startTimeMs"] as? Number)?.toLong() ?: 0L
        val endTs = (payload["End_Timestamp"] as? Number)?.toLong()
            ?: (payload["endTimeMs"] as? Number)?.toLong() ?: 0L

        json.put("Session_ID", sessionId)
        json.put("recordId", sessionId)
        json.put("Current_Tag", tag)
        json.put("subject", tag)
        json.put("Current_Task", task)
        json.put("taskTitle", task)
        json.put("Timer_Mode", mode)
        json.put("mode", mode)
        json.put("Total_Focus_Time_Formatted", payload["Total_Focus_Time_Formatted"] ?: TimelineSyncEngine.formatTimeMsToHhMmSs(focusMs))
        json.put("durationFormatted", payload["Total_Focus_Time_Formatted"] ?: TimelineSyncEngine.formatTimeMsToHhMmSs(focusMs))
        json.put("Total_Break_Time_Formatted", payload["Total_Break_Time_Formatted"] ?: TimelineSyncEngine.formatTimeMsToHhMmSs(breakMs))
        json.put("Total_Focus_Time_Ms", focusMs)
        json.put("totalFocusMs", focusMs)
        json.put("Total_Break_Time_Ms", breakMs)
        json.put("totalBreakMs", breakMs)
        json.put("Start_Timestamp", startTs)
        json.put("startTimeMs", startTs)
        json.put("End_Timestamp", endTs)
        json.put("endTimeMs", endTs)

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = if (startTs > 0L) sdfDate.format(Date(startTs)) else sdfDate.format(Date())
        json.put("dateString", dateStr)
        json.put("Date_String", dateStr)

        val timelineArray = JSONArray()
        val timelineList = payload["Timeline"] as? List<Map<String, Any>> ?: emptyList()
        for (event in timelineList) {
            val eventObj = JSONObject()
            eventObj.put("deviceId", event["deviceId"])
            eventObj.put("event", event["event"])
            eventObj.put("timestamp", event["timestamp"])
            timelineArray.put(eventObj)
        }
        json.put("Timeline", timelineArray)
        return json.toString()
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Task failed"))
            }
        }
    }
}
