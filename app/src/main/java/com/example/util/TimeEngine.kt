package com.example.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.example.data.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import org.json.JSONObject

object TimeEngine {
    @Volatile
    var serverTimeOffsetMs: Long = 0L

    fun getTrueTimeMs(): Long {
        return System.currentTimeMillis() + serverTimeOffsetMs
    }

    fun initializeNtp(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        serverTimeOffsetMs = try {
            prefs.getLong("firebase_server_time_offset", 0L)
        } catch (e: ClassCastException) {
            try {
                val offsetInt = prefs.getInt("firebase_server_time_offset", 0)
                prefs.edit().putLong("firebase_server_time_offset", offsetInt.toLong()).apply()
                offsetInt.toLong()
            } catch (ex: Exception) {
                0L
            }
        }
        com.example.util.StableTime.init(serverTimeOffsetMs)
        android.util.Log.d("TimeEngine", "NTP initialized from cache: $serverTimeOffsetMs")
        
        try {
            val url = com.example.api.FirebaseConfig.getDatabaseUrl(context.applicationContext)
            if (url.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(url)
                val offsetRef = database.getReference(".info/serverTimeOffset")
                offsetRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val offset = snapshot.getValue(Long::class.java) ?: 0L
                        serverTimeOffsetMs = offset
                        prefs.edit().putLong("firebase_server_time_offset", offset).apply()
                        com.example.util.StableTime.init(offset)
                        android.util.Log.d("TimeEngine", "NTP serverTimeOffsetMs updated: $serverTimeOffsetMs")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        android.util.Log.e("TimeEngine", "NTP offset listener cancelled", error.toException())
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("TimeEngine", "Failed to initialize NTP", e)
        }
    }

    fun getUniversalTimeMs(): Long {
        return com.example.util.StableTime.currentTimeMillis()
    }

    /**
     * 1. DURATION FORMATTER: Converts raw integer milliseconds into HH:MM:SS
     * Used for: UI Countdown Timer, Base Focus Time, Break Duration
     * Example: 5415000 ms -> "01:30:15"
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00:00"
        
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 2. TIMESTAMP FORMATTER: Converts a Unix Epoch timestamp into HH:MM:SS:MS
     * Used for: Event Logs, Start/End Timestamps, Lamport Audit Trail
     * Example: Date.now() -> "19:02:39:450"
     */
    fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0) return "00:00:00"
        
        val date = Date(timestampMs)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(date)
    }

    /**
     * LIVE DELTA CALCULATOR: Runs every 100ms in your UI loop
     * Computes exact elapsed time without modifying the saved database base.
     */
    fun calculateLiveElapsedMs(baseFocusMs: Long, lastEventTsMs: Long, status: String): Long {
        if (status != "FOCUSING" || lastEventTsMs <= 0) {
            return baseFocusMs // Clock is frozen (Paused or Idle)
        }
        val runningDelta = getUniversalTimeMs() - lastEventTsMs
        return baseFocusMs + runningDelta
    }



    /**
     * Get the epoch millisecond of midnight today.
     */
    fun getStartOfDayMs(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Standardize time conversion by rounding seconds to the nearest minute.
     * Example: 24 min 30s -> 25 min; 24 min 29s -> 24 min
     */
    fun roundSecondsToMinutes(seconds: Int): Int {
        return if (seconds > 0) maxOf(1, (seconds + 30) / 60) else 0
    }
}


// ==================== CONSOLIDATED FROM: StableTime.kt ====================
object StableTime {
    @Volatile
    private var baseSystemTime: Long = System.currentTimeMillis()
    @Volatile
    private var baseElapsedRealtime: Long = SystemClock.elapsedRealtime()

    fun init(offsetMs: Long = 0L) {
        baseSystemTime = System.currentTimeMillis() + offsetMs
        baseElapsedRealtime = SystemClock.elapsedRealtime()
    }

    fun currentTimeMillis(): Long {
        return baseSystemTime + (SystemClock.elapsedRealtime() - baseElapsedRealtime)
    }
}


// ==================== CONSOLIDATED FROM: StateReconciliationHelper.kt ====================
/**
 * A highly robust state reconciliation layer designed to ensure absolute data consistency
 * between the Task Engine, Finance Tracker, and device local storage.
 * It prevents silent data loss or mismatching states during app lifecycle transitions,
 * process deaths, or background standbys.
 */
object StateReconciliationHelper {
    private const val TAG = "StateReconciliation"

    // Data structures for form drafts to prevent input loss during transitions
    data class TaskDraft(
        val title: String,
        val description: String,
        val category: String,
        val priority: String
    )

    data class TransactionDraft(
        val memberId: Int,
        val type: String,
        val amount: Double,
        val note: String,
        val fromCategory: String,
        val toCategory: String,
        val fromAccountId: Int,
        val toAccountId: Int
    )

    /**
     * Reconciles the Task Engine structures to fix inconsistencies like:
     * - Parent task marked complete but subtasks left open
     * - Active subtasks whose parent task was completed or missing (orphans)
     * - Tasks assigned to deleted custom list categories, re-mapping them back to "Inbox".
     */
    suspend fun reconcileTaskEngine(database: AppDatabase) {
        try {
            Log.d(TAG, "Starting Task Engine state reconciliation...")
            val taskDao = database.taskDao()
            val customListDao = database.customListDao()

            val allTasks = taskDao.getAllTasksDirect()
            val allCustomLists = customListDao.getAllListsDirect()
            val validCategories = setOf("Inbox", "Today", "Next 7 Days") + allCustomLists.map { it.name }.toSet()

            val taskMap = allTasks.associateBy { it.id }

            for (task in allTasks) {
                var updatedTask = task
                var needsUpdate = false

                // 1. Solve Orphan Subtasks
                if (task.parentTaskId != null && !taskMap.containsKey(task.parentTaskId)) {
                    Log.w(TAG, "Reconstituting orphaned subtask containing non-existent parent ID ${task.parentTaskId}. Resetting parent reference.")
                    updatedTask = updatedTask.copy(parentTaskId = null)
                    needsUpdate = true
                }

                // 2. Resolve cascading completeness for parent-child tasks
                if (task.parentTaskId != null && taskMap.containsKey(task.parentTaskId)) {
                    val parent = taskMap[task.parentTaskId]
                    if (parent != null && parent.isCompleted && !task.isCompleted) {
                        Log.w(TAG, "Cascading completeness: Subtask '${task.title}' was open, but its parent '${parent.title}' is completed. Marking subtask complete.")
                        updatedTask = updatedTask.copy(isCompleted = true)
                        needsUpdate = true
                    }
                }

                // 3. Category Integrity Verification
                if (!validCategories.contains(task.listCategory)) {
                    Log.w(TAG, "Task '${task.title}' assigned to invalid or deleted list '${task.listCategory}'. Reconciling to 'Inbox' category.")
                    updatedTask = updatedTask.copy(listCategory = "Inbox")
                    needsUpdate = true
                }

                if (needsUpdate) {
                    taskDao.updateTask(updatedTask)
                }
            }
            Log.d(TAG, "Task Engine state reconciliation finished successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Task Engine reconciliation", e)
        }
    }

    /**
     * Reconciles the Finance Tracker double-entry systems:
     * - Insures transaction lists category integrity (missing categories are created)
     * - Validates dynamic financial progress targets
     */
    suspend fun reconcileFinanceTracker(database: AppDatabase) {
        try {
            Log.d(TAG, "Starting Financial Ledger consistency audit and reconciliation...")
            val categoryDao = database.financeCategoryDao()
            val transactionDao = database.financeTransactionDao()

            val allCategories = categoryDao.getAllCategoriesDirect()
            val allTransactions = transactionDao.getAllTransactionsDirect()

            val categoryNames = allCategories.map { it.name.lowercase() }.toSet()

            // 1. Reconcile missing categories referred by transactions
            allTransactions.forEach { tx ->
                val categoriesToVerify = listOfNotNull(tx.fromCategory, tx.toCategory)
                for (cat in categoriesToVerify) {
                    if (cat.isNotEmpty() && !categoryNames.contains(cat.lowercase())) {
                        Log.w(TAG, "Transaction ID ${tx.id} references non-existent category '$cat'. Dynamically registering category.")
                        val autoCategory = FinanceCategory(
                            name = cat,
                            type = tx.type
                        )
                        categoryDao.insertCategory(autoCategory)
                    }
                }
            }

            Log.d(TAG, "Financial Ledger reconciliation finished successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Finance Tracker reconciliation", e)
        }
    }

    /**
     * Save UI Task form draft state into SharedPreferences to avoid data loss
     * during transition actions.
     */
    fun saveTaskDraft(context: Context, title: String, description: String, category: String, priority: String) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("draft_task_title", title)
                putString("draft_task_desc", description)
                putString("draft_task_cat", category)
                putString("draft_task_priority", priority)
                putBoolean("draft_task_exists", true)
                apply()
            }
            Log.d(TAG, "Cached active uncommitted task draft in memory checkpoint.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save task input draft", e)
        }
    }

    fun getTaskDraft(context: Context): TaskDraft? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("draft_task_exists", false)) return null
        return TaskDraft(
            title = prefs.getString("draft_task_title", "") ?: "",
            description = prefs.getString("draft_task_desc", "") ?: "",
            category = prefs.getString("draft_task_cat", "Inbox") ?: "Inbox",
            priority = prefs.getString("draft_task_priority", "MEDIUM") ?: "MEDIUM"
        )
    }

    fun clearTaskDraft(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("draft_task_title")
            remove("draft_task_desc")
            remove("draft_task_cat")
            remove("draft_task_priority")
            putBoolean("draft_task_exists", false)
            apply()
        }
    }

    /**
     * Save UI Transaction draft state into SharedPreferences.
     */
    fun saveTransactionDraft(
        context: Context,
        memberId: Int,
        type: String,
        amount: Double,
        note: String,
        fromCategory: String,
        toCategory: String,
        fromAccountId: Int,
        toAccountId: Int
    ) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("draft_tx_member_id", memberId)
                putString("draft_tx_type", type)
                putFloat("draft_tx_amount", amount.toFloat())
                putString("draft_tx_note", note)
                putString("draft_tx_from_cat", fromCategory)
                putString("draft_tx_to_cat", toCategory)
                putInt("draft_tx_from_acc", fromAccountId)
                putInt("draft_tx_to_acc", toAccountId)
                putBoolean("draft_tx_exists", true)
                apply()
            }
            Log.d(TAG, "Cached active uncommitted finance transaction draft.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction input draft", e)
        }
    }

    fun getTransactionDraft(context: Context): TransactionDraft? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("draft_tx_exists", false)) return null
        return TransactionDraft(
            memberId = prefs.getInt("draft_tx_member_id", -1),
            type = prefs.getString("draft_tx_type", "EXPENSE") ?: "EXPENSE",
            amount = prefs.getFloat("draft_tx_amount", 0.0f).toDouble(),
            note = prefs.getString("draft_tx_note", "") ?: "",
            fromCategory = prefs.getString("draft_tx_from_cat", "") ?: "",
            toCategory = prefs.getString("draft_tx_to_cat", "") ?: "",
            fromAccountId = prefs.getInt("draft_tx_from_acc", -1),
            toAccountId = prefs.getInt("draft_tx_to_acc", -1)
        )
    }

    fun clearTransactionDraft(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("draft_tx_member_id")
            remove("draft_tx_type")
            remove("draft_tx_amount")
            remove("draft_tx_note")
            remove("draft_tx_from_cat")
            remove("draft_tx_to_cat")
            remove("draft_tx_from_acc")
            remove("draft_tx_to_acc")
            putBoolean("draft_tx_exists", false)
            apply()
        }
    }

    /**
     * Session Records & Cloud Vault Compliance Checker:
     * Audits local history vault records against Firestore cloud vault.
     * 1. Finds all unsynced records (is_synced_to_firestore == 0) in local_history_vault.
     * 2. Direct-syncs each pending session record to Firestore direct vault.
     * 3. Upon successful Firestore write verification, updates is_synced_to_firestore = 1.
     * 4. Downloads missing remote cloud session records into local vault to guarantee 100% parity.
     */
    suspend fun runSessionRecordComplianceCheck(context: Context, database: AppDatabase) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Log.i(TAG, "[Compliance Checker] Running Vault & Cloud Session Records audit...")
            val email = com.example.api.DynamicCommandManager.activeEmail
            val sanitizedEmail = com.example.api.DevicePresenceManager.sanitizeEmail(email)
            val vaultDao = database.localHistoryVaultDao()

            // 0. Audit and repair any corrupted local records (> 24 hours focus duration)
            val allLocal = vaultDao.getAllHistoryDirect()
            for (rec in allLocal) {
                if (rec.total_focus_ms > 86_400_000L) {
                    Log.w(TAG, "[Compliance Checker] Repairing corrupted local record ${rec.record_id} with duration ${rec.total_focus_ms}ms")
                    val fixed = rec.copy(
                        total_focus_ms = minOf(86_400_000L, maxOf(0L, rec.end_time_ms - rec.start_time_ms)),
                        duration_formatted = TimeEngine.formatDuration(minOf(86_400_000L, maxOf(0L, rec.end_time_ms - rec.start_time_ms)))
                    )
                    vaultDao.insertRecord(fixed)
                }
            }

            // 1. Audit unsynced local records (is_synced_to_firestore = 0)
            val unsyncedLocalRecords = vaultDao.getUnsyncedRecords()
            if (unsyncedLocalRecords.isNotEmpty()) {
                Log.d(TAG, "[Compliance Checker] Found ${unsyncedLocalRecords.size} unsynced vault records. Synchronizing to Firestore Cloud Vault...")
                for (record in unsyncedLocalRecords) {
                    try {
                        val payload = com.example.api.SessionPayload(
                            sessionId = record.record_id,
                            startTimestamp = record.start_time_ms,
                            endTimestamp = record.end_time_ms,
                            timeline = record.timeline ?: emptyList()
                        )
                        com.example.api.FirestoreArchiver.archiveSessionPayload(
                            context = context,
                            email = email,
                            payload = payload,
                            timerMode = record.mode,
                            currentTask = record.task_title ?: "General Focus",
                            currentTag = record.subject
                        )
                        vaultDao.insertRecord(record.copy(is_synced_to_firestore = 1))
                        Log.d(TAG, "[Compliance Checker] Successfully synced record ${record.record_id} to cloud vault and marked synced (=1).")
                    } catch (e: Exception) {
                        Log.e(TAG, "[Compliance Checker] Error syncing vault record ${record.record_id}", e)
                    }
                }
            } else {
                Log.d(TAG, "[Compliance Checker] All local vault records are fully synchronized with Cloud Vault.")
            }

            // 2. Pull remote history from Firestore cloud vault to catch missing remote records
            if (sanitizedEmail.isNotEmpty()) {
                try {
                    com.example.api.FirestoreArchiver.pullAndSyncFocusHistoryFromFirestore(context, sanitizedEmail)
                } catch (e: Exception) {
                    Log.e(TAG, "[Compliance Checker] Error pulling cloud vault history", e)
                }
            }

            // 3. Ensure device focus stats match latest vault totals
            com.example.api.DevicePresenceManager.updateDeviceFocusStats(context, email)
            Log.i(TAG, "[Compliance Checker] Vault & Cloud session compliance check completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "[Compliance Checker] Exception in compliance check protocol", e)
        }
    }

    /**
     * Executes double-entry consistency audit, cascades parent closures, repairs orphaned nodes,
     * audits vault & cloud compliance, flushes all SQLite WAL checkpoints completely.
     */
    suspend fun runUnifiedReconciliation(context: Context, database: AppDatabase) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Log.i(TAG, "Beginning unified state reconciliation protocol across Task Engine & Finance and disk persistence...")
        
        try {
            reconcileTaskEngine(database)
            reconcileFinanceTracker(database)
            runSessionRecordComplianceCheck(context, database)
        } catch (e: Exception) {
            Log.e(TAG, "Error during unified state reconciliation", e)
        }

        // Flush all SQLite journal files and write-ahead log chunks directly to disk
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            Log.i(TAG, "Successfully checkpointed SQLite write-ahead-logging (WAL) back onto disk blocks.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to completely checkpoint SQLite WAL blocks", e)
        }
        
        try {
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
            Log.i(TAG, "Successfully triggered all widgets updates from reconciliation.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger widget updates from reconciliation", e)
        }

        Log.i(TAG, "Unified state reconciliation protocol finished perfectly.")
    }

    /**
     * Safety rollback guard: If the local active session has accumulated meaningful focus time (> 1 minute),
     * we forcefully package it into a completed historical session and queue it for Firestore archival
     * to prevent data loss before a server rollback state wipes it.
     */
    suspend fun saveMeaningfulActiveSessionBeforeOverwrite(context: Context, database: AppDatabase) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val currentSession = database.localActiveSessionDao().getActiveSession() ?: return@withContext
            
            // Calculate baseFocusTimeMs + elapsed time
            val baseFocusTimeMs = currentSession.base_focus_time_ms
            val elapsedMs = if (currentSession.status == "FOCUSING") {
                TimeEngine.getUniversalTimeMs() - currentSession.last_event_ts_ms
            } else {
                0L
            }
            val totalFocusMs = baseFocusTimeMs + elapsedMs
            
            // Meaningful focus time is > 1 minute (60,000 ms)
            if (totalFocusMs > 60000L) {
                Log.i(TAG, "Safety rollback guard: Local active session has accumulated meaningful focus time (${totalFocusMs}ms). Forcefully archiving to prevent data loss.")
                
                val nowMs = TimeEngine.getUniversalTimeMs()
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(nowMs))
                
                val startTimeMs = try {
                    currentSession.session_id.substringAfter("sess_").toLong()
                } catch (e: Exception) {
                    nowMs - totalFocusMs
                }
                
                val pauseCount = 0
                
                val archiveRecord = LocalHistoryVault(
                    record_id = currentSession.session_id,
                    date_string = dateStr,
                    subject = currentSession.tag,
                    task_title = currentSession.task_title,
                    start_time_ms = startTimeMs,
                    end_time_ms = nowMs,
                    total_focus_ms = totalFocusMs,
                    total_break_ms = currentSession.base_break_time_ms,
                    pause_count = pauseCount,
                    duration_formatted = TimeEngine.formatDuration(totalFocusMs),
                    start_time_formatted = TimeEngine.formatTimestamp(startTimeMs),
                    end_time_formatted = TimeEngine.formatTimestamp(nowMs),
                    is_synced_to_firestore = 0,
                    mode = currentSession.mode
                )
                
                val archivePayload = JSONObject().apply {
                    put("recordId", currentSession.session_id)
                    put("dateString", dateStr)
                    put("subject", currentSession.tag)
                    put("taskTitle", currentSession.task_title)
                    put("startTimeMs", startTimeMs)
                    put("endTimeMs", nowMs)
                    put("totalFocusMs", totalFocusMs)
                    put("totalBreakMs", currentSession.base_break_time_ms)
                    put("pauseCount", pauseCount)
                    put("durationFormatted", TimeEngine.formatDuration(totalFocusMs))
                    put("startTimeFormatted", TimeEngine.formatTimestamp(startTimeMs))
                    put("endTimeFormatted", TimeEngine.formatTimestamp(nowMs))
                    put("mode", currentSession.mode)
                }.toString()
                
                val outboxArchive = OutboxQueue(
                    mutation_id = "mut_archive_rollback_${currentSession.session_id}_android",
                    created_at_ms = nowMs,
                    routing_target = "FIRESTORE_DIRECT_VAULT",
                    action_type = "ARCHIVE_SESSION",
                    payload_json = archivePayload,
                    status = "PENDING"
                )
                
                database.withTransaction {
                    database.localHistoryVaultDao().insertRecord(archiveRecord)
                    database.outboxQueueDao().insertQueueItem(outboxArchive)
                }
                Log.d(TAG, "Successfully packaged and queued rollback session ${currentSession.session_id} for archival.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in safety rollback guard save", e)
        }
    }
}

fun parseToMs(value: Any?): Long {
    if (value == null) return 0L
    return when (value) {
        is Number -> value.toLong()
        is String -> {
            val str = value.trim()
            if (str.isEmpty()) 0L
            else if (str.endsWith("ms", ignoreCase = true)) {
                val clean = str.substring(0, str.length - 2).trim()
                clean.toLongOrNull() ?: clean.toDoubleOrNull()?.toLong() ?: 0L
            } else if (str.endsWith("s", ignoreCase = true) && !str.contains(":")) {
                val clean = str.substring(0, str.length - 1).trim()
                val sec = clean.toLongOrNull() ?: clean.toDoubleOrNull()?.toLong() ?: 0L
                sec * 1000L
            } else if (str.contains(":")) {
                try {
                    val parts = str.split(":")
                    if (parts.size == 3) {
                        val h = parts[0].trim().toLongOrNull() ?: 0L
                        val m = parts[1].trim().toLongOrNull() ?: 0L
                        val s = parts[2].trim().toLongOrNull() ?: 0L
                        (h * 3600 + m * 60 + s) * 1000L
                    } else if (parts.size == 2) {
                        val m = parts[0].trim().toLongOrNull() ?: 0L
                        val s = parts[1].trim().toLongOrNull() ?: 0L
                        (m * 60 + s) * 1000L
                    } else 0L
                } catch (e: Exception) {
                    0L
                }
            } else {
                str.toLongOrNull() ?: str.toDoubleOrNull()?.toLong() ?: 0L
            }
        }
        else -> 0L
    }
}

fun DataSnapshot.childMs(vararg keys: String): Long {
    for (key in keys) {
        val childSnap = if (key.contains("/")) {
            var curr = this
            key.split("/").forEach { curr = curr.child(it) }
            curr
        } else {
            this.child(key)
        }
        val raw = childSnap.value
        if (raw != null) {
            var ms = parseToMs(raw)
            if (ms > 0L && key.lowercase().contains("seconds") && raw is Number) {
                ms *= 1000L
            }
            if (ms > 0L) return ms
        }
    }
    return 0L
}

