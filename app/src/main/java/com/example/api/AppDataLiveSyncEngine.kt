package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * AppDataLiveSyncEngine
 *
 * Real-time, zero-duplicate cross-device synchronization engine for:
 * - Tasks
 * - Journal
 * - Health
 * - File Explorer
 * - Finance
 * - Settings (delegated to UserSettingsSyncEngine)
 * - Keep Notes (delegated to KeepNotesLiveSyncEngine)
 *
 * Employs Realtime Database nodes under `FOCUS_TIMMER/USER/{sanitizedEmail}/`
 * combined with Google Drive backup triggers and FCM signals.
 */
object AppDataLiveSyncEngine {
    private const val TAG = "AppDataLiveSyncEngine"

    private val isListening = AtomicBoolean(false)
    private var activeUserEmail: String = ""

    private var tasksListener: ValueEventListener? = null
    private var journalListener: ValueEventListener? = null
    private var healthListener: ValueEventListener? = null
    private var filesListener: ValueEventListener? = null
    private var financeListener: ValueEventListener? = null

    fun startListening(context: Context, email: String, database: AppDatabase) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        if (isListening.get() && activeUserEmail == sanitizedEmail) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        try {
            stopListening(context)
            activeUserEmail = sanitizedEmail

            val rtdb = FirebaseDatabase.getInstance(dbUrl)
            val userRef = rtdb.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)

            // 1. Tasks listener
            val tListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileTasks(database, snapshot)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userRef.child("TASKS_LIVE").addValueEventListener(tListener)
            tasksListener = tListener

            // 2. Journal listener
            val jListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileJournal(database, snapshot)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userRef.child("JOURNAL_LIVE").addValueEventListener(jListener)
            journalListener = jListener

            // 4. File Explorer listener
            val fListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileFiles(database, snapshot)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userRef.child("FILE_EXPLORER_LIVE").addValueEventListener(fListener)
            filesListener = fListener

            // 5. Finance listener
            val finListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileFinance(database, snapshot)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userRef.child("FINANCE_LIVE").addValueEventListener(finListener)
            financeListener = finListener

            // Delegates
            UserSettingsSyncEngine.startListeningForRemoteSettingsUpdates(context, sanitizedEmail)
            KeepNotesLiveSyncEngine.startListening(context, sanitizedEmail, database)

            isListening.set(true)
            Log.d(TAG, "Started AppDataLiveSyncEngine listeners for $sanitizedEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AppDataLiveSyncEngine listeners", e)
        }
    }

    fun stopListening(context: Context) {
        if (!isListening.get()) return
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty() && activeUserEmail.isNotBlank()) {
                val sanitizedEmail = DevicePresenceManager.sanitizeEmail(activeUserEmail)
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val userRef = rtdb.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)

                tasksListener?.let { userRef.child("TASKS_LIVE").removeEventListener(it) }
                journalListener?.let { userRef.child("JOURNAL_LIVE").removeEventListener(it) }
                filesListener?.let { userRef.child("FILE_EXPLORER_LIVE").removeEventListener(it) }
                financeListener?.let { userRef.child("FINANCE_LIVE").removeEventListener(it) }

                UserSettingsSyncEngine.stopListening(context, activeUserEmail)
                KeepNotesLiveSyncEngine.stopListening(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AppDataLiveSyncEngine listeners", e)
        } finally {
            tasksListener = null
            journalListener = null
            healthListener = null
            filesListener = null
            financeListener = null
            isListening.set(false)
            activeUserEmail = ""
        }
    }

    fun pullAllDataFromCloud(context: Context, email: String, database: AppDatabase) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val userRef = rtdb.getReference("FOCUS_TIMMER").child("USER").child(sanitizedEmail)

                userRef.child("TASKS_LIVE").get().addOnSuccessListener { snapshot ->
                    CoroutineScope(Dispatchers.IO).launch { reconcileTasks(database, snapshot) }
                }
                userRef.child("JOURNAL_LIVE").get().addOnSuccessListener { snapshot ->
                    CoroutineScope(Dispatchers.IO).launch { reconcileJournal(database, snapshot) }
                }
                userRef.child("FILE_EXPLORER_LIVE").get().addOnSuccessListener { snapshot ->
                    CoroutineScope(Dispatchers.IO).launch { reconcileFiles(database, snapshot) }
                }
                userRef.child("FINANCE_LIVE").get().addOnSuccessListener { snapshot ->
                    CoroutineScope(Dispatchers.IO).launch { reconcileFinance(database, snapshot) }
                }

                UserSettingsSyncEngine.pullSettingsFromCloud(context, sanitizedEmail)
                KeepNotesLiveSyncEngine.pullKeepNotesFromCloud(context, sanitizedEmail, database)
            } catch (e: Exception) {
                Log.e(TAG, "Error pulling all data from cloud", e)
            }
        }
    }

    // ==========================================
    // PUSH METHODS
    // ==========================================

    fun pushTaskToCloud(context: Context, email: String, task: Task, isDeleted: Boolean = false) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val key = createTaskKey(task)
                val ref = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER").child(sanitizedEmail)
                    .child("TASKS_LIVE").child(key)

                if (isDeleted) {
                    ref.setValue(null)
                } else {
                    val payload = mapOf(
                        "key" to key,
                        "title" to task.title,
                        "description" to task.description,
                        "estimatedMinutes" to task.estimatedMinutes,
                        "actualMinutes" to task.actualMinutes,
                        "isCompleted" to task.isCompleted,
                        "listCategory" to task.listCategory,
                        "priority" to task.priority,
                        "dueDateString" to task.dueDateString,
                        "orderIndex" to task.orderIndex,
                        "nagModeEnabled" to task.nagModeEnabled,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    ref.setValue(payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push Task to RTDB", e)
            }
        }
    }

    fun pushJournalToCloud(context: Context, email: String, entry: JournalEntry, isDeleted: Boolean = false) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val key = createJournalKey(entry)
                val ref = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER").child(sanitizedEmail)
                    .child("JOURNAL_LIVE").child(key)

                if (isDeleted) {
                    ref.setValue(null)
                } else {
                    val payload = mapOf(
                        "key" to key,
                        "title" to entry.title,
                        "text" to entry.text,
                        "dateString" to entry.dateString,
                        "timestamp" to entry.timestamp,
                        "attachmentsJson" to entry.attachmentsJson,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    ref.setValue(payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push Journal to RTDB", e)
            }
        }
    }

    fun pushHealthToCloud(context: Context, email: String, record: HealthRecord) {
        // Health live branch has been removed from RTDB; health data sync is handled via FCM data triggers.
    }

    fun pushFileToCloud(context: Context, email: String, file: AppFile, isDeleted: Boolean = false) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val key = createFileKey(file)
                val ref = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER").child(sanitizedEmail)
                    .child("FILE_EXPLORER_LIVE").child(key)

                if (isDeleted) {
                    ref.setValue(null)
                } else {
                    val payload = mapOf(
                        "key" to key,
                        "name" to file.name,
                        "path" to file.path,
                        "size" to file.size,
                        "mimeType" to file.mimeType,
                        "uriString" to file.uriString,
                        "timestamp" to file.timestamp,
                        "isFavorite" to file.isFavorite,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    ref.setValue(payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push File to RTDB", e)
            }
        }
    }

    fun pushFinanceToCloud(context: Context, email: String, transaction: FinanceTransaction, isDeleted: Boolean = false) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val key = createFinanceKey(transaction)
                val ref = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER").child(sanitizedEmail)
                    .child("FINANCE_LIVE").child(key)

                if (isDeleted) {
                    ref.setValue(null)
                } else {
                    val payload = mapOf(
                        "key" to key,
                        "type" to transaction.type,
                        "amount" to transaction.amount,
                        "fromCategory" to (transaction.fromCategory ?: ""),
                        "toCategory" to (transaction.toCategory ?: ""),
                        "note" to transaction.note,
                        "timestamp" to transaction.timestamp,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    ref.setValue(payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push Finance to RTDB", e)
            }
        }
    }

    fun triggerGoogleDriveBackupIfConnected(context: Context, database: AppDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) {
                    com.example.util.GoogleDriveSyncManager.backupAllAppData(context, database)
                    Log.d(TAG, "Triggered background Google Drive backup")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering Google Drive backup", e)
            }
        }
    }

    // ==========================================
    // RECONCILIATION & DEDUPLICATION LOGIC
    // ==========================================

    private suspend fun reconcileTasks(database: AppDatabase, snapshot: DataSnapshot) {
        try {
            val taskDao = database.taskDao()
            val localTasks = taskDao.getAllTasksDirect()

            val remoteMap = mutableMapOf<String, TaskSnapshotItem>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val title = child.child("title").getValue(String::class.java) ?: continue
                    val dueDateString = child.child("dueDateString").getValue(String::class.java) ?: ""
                    val listCategory = child.child("listCategory").getValue(String::class.java) ?: "Inbox"
                    val isCompleted = child.child("isCompleted").getValue(Boolean::class.java) ?: false
                    val description = child.child("description").getValue(String::class.java) ?: ""
                    val priority = child.child("priority").getValue(String::class.java) ?: "MEDIUM"
                    val estimatedMinutes = child.child("estimatedMinutes").getValue(Int::class.java) ?: 30
                    val actualMinutes = child.child("actualMinutes").getValue(Int::class.java) ?: 0

                    val sig = createTaskSig(title, dueDateString, listCategory)
                    remoteMap[sig] = TaskSnapshotItem(
                        title = title,
                        description = description,
                        dueDateString = dueDateString,
                        listCategory = listCategory,
                        isCompleted = isCompleted,
                        priority = priority,
                        estimatedMinutes = estimatedMinutes,
                        actualMinutes = actualMinutes
                    )
                }
            }

            // Deduplicate local DB tasks
            val localMap = mutableMapOf<String, Task>()
            val duplicatesToDelete = mutableListOf<Task>()

            for (local in localTasks) {
                val sig = createTaskSig(local.title, local.dueDateString, local.listCategory)
                if (localMap.containsKey(sig)) {
                    duplicatesToDelete.add(local)
                } else {
                    localMap[sig] = local
                }
            }

            for (dup in duplicatesToDelete) {
                taskDao.deleteTask(dup)
                Log.d(TAG, "Deduplicated and removed duplicate local task id=${dup.id}")
            }

            for ((sig, remote) in remoteMap) {
                val existing = localMap[sig]
                if (existing == null) {
                    val newTask = Task(
                        title = remote.title,
                        description = remote.description,
                        dueDateString = remote.dueDateString,
                        listCategory = remote.listCategory,
                        isCompleted = remote.isCompleted,
                        priority = remote.priority,
                        estimatedMinutes = remote.estimatedMinutes,
                        actualMinutes = remote.actualMinutes
                    )
                    taskDao.insertTask(newTask)
                } else {
                    if (existing.isCompleted != remote.isCompleted ||
                        existing.description != remote.description ||
                        existing.priority != remote.priority
                    ) {
                        val updated = existing.copy(
                            isCompleted = remote.isCompleted,
                            description = remote.description,
                            priority = remote.priority
                        )
                        taskDao.updateTask(updated)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Tasks", e)
        }
    }

    private suspend fun reconcileJournal(database: AppDatabase, snapshot: DataSnapshot) {
        try {
            val journalDao = database.journalDao()
            val localEntries = journalDao.getAllJournalEntriesDirect()

            val remoteMap = mutableMapOf<String, JournalSnapshotItem>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val dateString = child.child("dateString").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val attachmentsJson = child.child("attachmentsJson").getValue(String::class.java) ?: ""

                    val sig = createJournalSig(title, text, dateString)
                    remoteMap[sig] = JournalSnapshotItem(
                        title = title,
                        text = text,
                        dateString = dateString,
                        timestamp = timestamp,
                        attachmentsJson = attachmentsJson
                    )
                }
            }

            val localMap = mutableMapOf<String, JournalEntry>()
            val duplicatesToDelete = mutableListOf<JournalEntry>()

            for (local in localEntries) {
                val sig = createJournalSig(local.title, local.text, local.dateString)
                if (localMap.containsKey(sig)) {
                    duplicatesToDelete.add(local)
                } else {
                    localMap[sig] = local
                }
            }

            for (dup in duplicatesToDelete) {
                journalDao.deleteJournalEntry(dup)
            }

            for ((sig, remote) in remoteMap) {
                if (!localMap.containsKey(sig)) {
                    val newEntry = JournalEntry(
                        title = remote.title,
                        text = remote.text,
                        dateString = remote.dateString,
                        timestamp = remote.timestamp,
                        attachmentsJson = remote.attachmentsJson
                    )
                    journalDao.insertJournalEntry(newEntry)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Journal", e)
        }
    }

    private suspend fun reconcileHealth(database: AppDatabase, snapshot: DataSnapshot) {
        try {
            val healthDao = database.healthRecordDao()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val dateString = child.child("dateString").getValue(String::class.java) ?: continue
                    val steps = child.child("steps").getValue(Int::class.java) ?: 0
                    val stepGoal = child.child("stepGoal").getValue(Int::class.java) ?: 10000
                    val sleepMinutes = child.child("sleepMinutes").getValue(Int::class.java) ?: 0
                    val waterMl = child.child("waterMl").getValue(Int::class.java) ?: 0
                    val caloriesBurned = child.child("caloriesBurned").getValue(Int::class.java) ?: 0
                    val activeMinutes = child.child("activeMinutes").getValue(Int::class.java) ?: 0
                    val heartRateAvg = child.child("heartRateAvg").getValue(Int::class.java) ?: 72

                    val existing = healthDao.getHealthRecordDirect(dateString)
                    if (existing == null) {
                        val record = HealthRecord(
                            dateString = dateString,
                            steps = steps,
                            stepGoal = stepGoal,
                            sleepMinutes = sleepMinutes,
                            waterMl = waterMl,
                            caloriesBurned = caloriesBurned,
                            activeMinutes = activeMinutes,
                            heartRateAvg = heartRateAvg
                        )
                        healthDao.insertOrUpdate(record)
                    } else {
                        val updated = existing.copy(
                            steps = maxOf(existing.steps, steps),
                            sleepMinutes = maxOf(existing.sleepMinutes, sleepMinutes),
                            waterMl = maxOf(existing.waterMl, waterMl),
                            caloriesBurned = maxOf(existing.caloriesBurned, caloriesBurned),
                            activeMinutes = maxOf(existing.activeMinutes, activeMinutes)
                        )
                        healthDao.insertOrUpdate(updated)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Health", e)
        }
    }

    private suspend fun reconcileFiles(database: AppDatabase, snapshot: DataSnapshot) {
        try {
            val fileDao = database.appFileDao()
            val localFiles = fileDao.getAllFilesDirect()

            val remoteMap = mutableMapOf<String, FileSnapshotItem>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: continue
                    val path = child.child("path").getValue(String::class.java) ?: ""
                    val size = child.child("size").getValue(Long::class.java) ?: 0L
                    val mimeType = child.child("mimeType").getValue(String::class.java) ?: "*/*"
                    val uriString = child.child("uriString").getValue(String::class.java) ?: ""
                    val isFavorite = child.child("isFavorite").getValue(Boolean::class.java) ?: false
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                    val sig = createFileSig(name, path)
                    remoteMap[sig] = FileSnapshotItem(
                        name = name,
                        path = path,
                        size = size,
                        mimeType = mimeType,
                        uriString = uriString,
                        isFavorite = isFavorite,
                        timestamp = timestamp
                    )
                }
            }

            val localMap = mutableMapOf<String, AppFile>()
            val duplicatesToDelete = mutableListOf<AppFile>()

            for (local in localFiles) {
                val sig = createFileSig(local.name, local.path)
                if (localMap.containsKey(sig)) {
                    duplicatesToDelete.add(local)
                } else {
                    localMap[sig] = local
                }
            }

            for (dup in duplicatesToDelete) {
                fileDao.deleteFile(dup)
            }

            for ((sig, remote) in remoteMap) {
                if (!localMap.containsKey(sig)) {
                    val newFile = AppFile(
                        name = remote.name,
                        path = remote.path,
                        size = remote.size,
                        mimeType = remote.mimeType,
                        uriString = remote.uriString,
                        timestamp = remote.timestamp,
                        isFavorite = remote.isFavorite
                    )
                    fileDao.insertFile(newFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Files", e)
        }
    }

    private suspend fun reconcileFinance(database: AppDatabase, snapshot: DataSnapshot) {
        try {
            val financeDao = database.financeTransactionDao()
            val localTransactions = financeDao.getAllTransactionsDirect()

            val remoteMap = mutableMapOf<String, FinanceSnapshotItem>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val type = child.child("type").getValue(String::class.java) ?: "EXPENSE"
                    val amount = child.child("amount").getValue(Double::class.java) ?: 0.0
                    val note = child.child("note").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val fromCategory = child.child("fromCategory").getValue(String::class.java)
                    val toCategory = child.child("toCategory").getValue(String::class.java)

                    val sig = createFinanceSig(type, amount, note, timestamp)
                    remoteMap[sig] = FinanceSnapshotItem(
                        type = type,
                        amount = amount,
                        note = note,
                        timestamp = timestamp,
                        fromCategory = fromCategory,
                        toCategory = toCategory
                    )
                }
            }

            val localMap = mutableMapOf<String, FinanceTransaction>()
            val duplicatesToDelete = mutableListOf<FinanceTransaction>()

            for (local in localTransactions) {
                val sig = createFinanceSig(local.type, local.amount, local.note, local.timestamp)
                if (localMap.containsKey(sig)) {
                    duplicatesToDelete.add(local)
                } else {
                    localMap[sig] = local
                }
            }

            for (dup in duplicatesToDelete) {
                financeDao.deleteTransaction(dup)
            }

            for ((sig, remote) in remoteMap) {
                if (!localMap.containsKey(sig)) {
                    val newTransaction = FinanceTransaction(
                        memberId = 0,
                        type = remote.type,
                        amount = remote.amount,
                        note = remote.note,
                        timestamp = remote.timestamp,
                        fromCategory = remote.fromCategory,
                        toCategory = remote.toCategory
                    )
                    financeDao.insertTransaction(newTransaction)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Finance", e)
        }
    }

    // Helper Signatures
    private fun createTaskSig(title: String, dueDateString: String, listCategory: String): String {
        return "${title.trim().lowercase()}|${dueDateString.trim()}|${listCategory.trim().lowercase()}"
    }

    private fun createTaskKey(task: Task): String {
        val hash = abs(createTaskSig(task.title, task.dueDateString, task.listCategory).hashCode())
        return "task_${hash}"
    }

    private fun createJournalSig(title: String, text: String, dateString: String): String {
        return "${dateString.trim()}|${title.trim().lowercase()}|${text.trim().lowercase()}"
    }

    private fun createJournalKey(entry: JournalEntry): String {
        val hash = abs(createJournalSig(entry.title, entry.text, entry.dateString).hashCode())
        return "journal_${entry.timestamp}_${hash}"
    }

    private fun createFileSig(name: String, path: String): String {
        return "${path.trim().lowercase()}/${name.trim().lowercase()}"
    }

    private fun createFileKey(file: AppFile): String {
        val hash = abs(createFileSig(file.name, file.path).hashCode())
        return "file_${hash}"
    }

    private fun createFinanceSig(type: String, amount: Double, note: String, timestamp: Long): String {
        return "${type.trim()}|$amount|${note.trim().lowercase()}|$timestamp"
    }

    private fun createFinanceKey(transaction: FinanceTransaction): String {
        val hash = abs(createFinanceSig(transaction.type, transaction.amount, transaction.note, transaction.timestamp).hashCode())
        return "finance_${transaction.timestamp}_${hash}"
    }

    private data class TaskSnapshotItem(
        val title: String,
        val description: String,
        val dueDateString: String,
        val listCategory: String,
        val isCompleted: Boolean,
        val priority: String,
        val estimatedMinutes: Int,
        val actualMinutes: Int
    )

    private data class JournalSnapshotItem(
        val title: String,
        val text: String,
        val dateString: String,
        val timestamp: Long,
        val attachmentsJson: String
    )

    private data class FileSnapshotItem(
        val name: String,
        val path: String,
        val size: Long,
        val mimeType: String,
        val uriString: String,
        val isFavorite: Boolean,
        val timestamp: Long
    )

    private data class FinanceSnapshotItem(
        val type: String,
        val amount: Double,
        val note: String,
        val timestamp: Long,
        val fromCategory: String?,
        val toCategory: String?
    )
}
