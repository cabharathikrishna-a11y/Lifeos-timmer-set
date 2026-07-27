package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class LocalRepository(val db: AppDatabase, val context: android.content.Context) {

    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null

    private fun triggerAutoBackup() {
        val appContext = context.applicationContext
        if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(appContext)) {
            backupJob?.cancel()
            backupJob = backupScope.launch {
                delay(5000L) // Debounce by 5 seconds
                try {
                    android.util.Log.d("LocalRepository", "Auto-backing up to Google Drive...")
                    com.example.util.GoogleDriveSyncManager.backupAllAppData(appContext, db, {})
                } catch (e: Exception) {
                    android.util.Log.e("LocalRepository", "Auto-backup failed", e)
                }
            }
        }
    }

    private val taskDao = db.taskDao()
    private val habitDao = db.habitDao()
    private val journalDao = db.journalDao()
    private val ledgerDao = db.ledgerDao()
    private val deadlineDao = db.deadlineDao()
    private val financialGoalDao = db.financialGoalDao()
    private val contactDao = db.contactDao()
    private val appFileDao = db.appFileDao()
    private val customListDao = db.customListDao()
    private val familyMemberDao = db.familyMemberDao()
    private val financialAccountDao = db.financialAccountDao()
    private val financialLogDao = db.financialLogDao()
    private val financeTransactionDao = db.financeTransactionDao()
    private val financeCategoryDao = db.financeCategoryDao()
    private val focusRecordDao = db.focusRecordDao()
    private val localActiveSessionDao = db.localActiveSessionDao()
    private val outboxQueueDao = db.outboxQueueDao()
    private val localHistoryVaultDao = db.localHistoryVaultDao()

    init {
        val observer = object : InvalidationTracker.Observer(
            arrayOf(
                "tasks", "habits", "habit_completions", "journal_entries", "ledger_entries",
                "deadlines", "financial_goals", "contacts", "app_files", "focus_records",
                "keep_notes", "custom_lists", "family_members", "financial_accounts",
                "financial_logs", "finance_transactions", "finance_categories", "health_records",
                "local_active_session", "outbox_queue", "local_history_vault"
            )
        ) {
            override fun onInvalidated(tables: Set<String>) {
                android.util.Log.d("LocalRepository", "Tables invalidated: $tables, checking auto-backup...")
                triggerAutoBackup()
            }
        }
        db.invalidationTracker.addObserver(observer)
    }

    // Custom List Operations
    val allLists: Flow<List<CustomList>> = customListDao.getAllLists()

    suspend fun insertList(list: CustomList): Long = withContext(NonCancellable) {
        customListDao.insertList(list)
    }

    suspend fun updateList(list: CustomList) = withContext(NonCancellable) {
        customListDao.updateList(list)
    }

    suspend fun deleteList(list: CustomList) = withContext(NonCancellable) {
        customListDao.deleteList(list)
    }

    private fun pushLiveSync(action: (email: String) -> Unit) {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""
        if (email.isNotBlank()) {
            action(email)
        }
        triggerAutoBackup()
    }

    // Task Operations
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    
    suspend fun insertTask(task: Task): Long = withContext(NonCancellable) {
        val sanitized = validateAndSanitizeTask(task)
        val res = if (sanitized.id == 0) {
            taskDao.insertTask(sanitized)
        } else {
            taskDao.updateTask(sanitized)
            sanitized.id.toLong()
        }
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushTaskToCloud(context, email, sanitized)
        }
        res
    }

    suspend fun updateTask(task: Task) = withContext(NonCancellable) {
        val sanitized = validateAndSanitizeTask(task)
        val res = if (sanitized.id == 0) {
            taskDao.insertTask(sanitized)
        } else {
            taskDao.updateTask(sanitized)
            sanitized.id.toLong()
        }
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushTaskToCloud(context, email, sanitized)
        }
        res
    }

    suspend fun deleteTask(task: Task) = withContext(NonCancellable) {
        taskDao.deleteTask(task)
        // Also delete subtasks if it's a parent
        taskDao.deleteSubtasks(task.id)
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushTaskToCloud(context, email, task, isDeleted = true)
        }
    }

    suspend fun getTaskById(id: Int): Task? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(id)
    }

    private suspend fun validateAndSanitizeTask(task: Task): Task {
        val trimmedTitle = task.title.trim()
        if (trimmedTitle.isEmpty()) {
            throw IllegalArgumentException("Database Integrity Error: Task title cannot be blank.")
        }

        val validatedPriority = when (task.priority.uppercase(java.util.Locale.ROOT)) {
            "HIGH", "MEDIUM", "LOW" -> task.priority.uppercase(java.util.Locale.ROOT)
            else -> "MEDIUM"
        }

        val validatedMinutes = task.estimatedMinutes.coerceAtLeast(0)
        val validatedActual = task.actualMinutes.coerceAtLeast(0)

        // Referential Integrity Rule: Verify that the parent task exists
        task.parentTaskId?.let { parentId ->
            if (parentId != 0) {
                val parentTask = taskDao.getTaskById(parentId)
                if (parentTask == null) {
                    throw IllegalArgumentException("Referential Integrity Violated: Parent Task with ID $parentId does not exist.")
                }
            }
        }

        return task.copy(
            title = trimmedTitle,
            description = task.description.trim(),
            priority = validatedPriority,
            estimatedMinutes = validatedMinutes,
            actualMinutes = validatedActual
        )
    }

    // Habit Operations
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    suspend fun insertHabit(habit: Habit): Long = withContext(NonCancellable) {
        habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.deleteHabit(habit)
    }

    suspend fun insertHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
    }

    suspend fun deleteHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.deleteCompletion(habitId, dateString)
    }

    // Journal Operations
    val allJournalEntries: Flow<List<JournalEntry>> = journalDao.getAllJournalEntries()

    fun searchJournal(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournalEntries("%$query%")
    }

    suspend fun insertJournal(entry: JournalEntry): Long = withContext(NonCancellable) {
        val sanitized = validateAndSanitizeJournalEntry(entry)
        val id = journalDao.insertJournalEntry(sanitized)
        val saved = sanitized.copy(id = id.toInt())
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushJournalToCloud(context, email, saved)
        }
        id
    }

    suspend fun deleteJournal(entry: JournalEntry) = withContext(NonCancellable) {
        journalDao.deleteJournalEntry(entry)
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushJournalToCloud(context, email, entry, isDeleted = true)
        }
    }

    private fun validateAndSanitizeJournalEntry(entry: JournalEntry): JournalEntry {
        val trimmedTitle = entry.title.trim()
        val trimmedText = entry.text.trim()

        val finalTitle = if (trimmedTitle.isEmpty()) "Untitled Draft" else trimmedTitle
        val finalText = if (trimmedText.isEmpty()) "Chronicle entry started..." else trimmedText

        // Standardize dateString to YYYY-MM-DD
        val datePattern = """^\d{4}-\d{2}-\d{2}$""".toRegex()
        val validatedDateString = if (datePattern.matches(entry.dateString)) {
            entry.dateString
        } else {
            try {
                val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(entry.dateString)
                if (parsed != null) {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(parsed)
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                }
            } catch (e: Exception) {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            }
        }

        return entry.copy(
            title = finalTitle,
            text = finalText,
            dateString = validatedDateString
        )
    }

    // Financial Operations
    val allLedgerEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllLedgerEntries()

    suspend fun insertLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        val sanitized = validateAndSanitizeLedgerEntry(entry)
        ledgerDao.insertLedgerEntry(sanitized)
    }

    suspend fun deleteLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        ledgerDao.deleteLedgerEntry(entry)
    }

    private fun validateAndSanitizeLedgerEntry(entry: LedgerEntry): LedgerEntry {
        if (entry.amount <= 0.0) {
            throw IllegalArgumentException("Database Integrity Error: Financial ledger amount must be positive.")
        }

        val validatedType = when (entry.type.uppercase(java.util.Locale.ROOT)) {
            "INCOME", "EXPENSE" -> entry.type.uppercase(java.util.Locale.ROOT)
            else -> throw IllegalArgumentException("Database Integrity Error: Invalid ledger entry type. Must be INCOME or EXPENSE.")
        }

        val trimmedCategory = entry.categoryTag.trim()
        if (trimmedCategory.isEmpty()) {
            throw IllegalArgumentException("Database Integrity Error: Financial category tag cannot be blank.")
        }

        return entry.copy(
            type = validatedType,
            categoryTag = trimmedCategory,
            note = entry.note.trim()
        )
    }

    // Deadline Operations
    val allDeadlines: Flow<List<Deadline>> = deadlineDao.getAllDeadlines()

    suspend fun insertDeadline(deadline: Deadline): Long = withContext(NonCancellable) {
        deadlineDao.insertDeadline(deadline)
    }

    suspend fun updateDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.updateDeadline(deadline)
    }

    suspend fun deleteDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.deleteDeadline(deadline)
    }

    // Financial Goal Operations
    val allFinancialGoals: Flow<List<FinancialGoal>> = financialGoalDao.getAllFinancialGoals()

    suspend fun insertFinancialGoal(goal: FinancialGoal): Long = withContext(NonCancellable) {
        financialGoalDao.insertFinancialGoal(goal)
    }

    suspend fun updateFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.updateFinancialGoal(goal)
    }

    suspend fun deleteFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.deleteFinancialGoal(goal)
    }

    // Contact Operations
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertContact(contact: Contact): Long = withContext(NonCancellable) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.deleteContact(contact)
    }

    // File Operations
    val allFiles: Flow<List<AppFile>> = appFileDao.getAllFiles()

    suspend fun insertFile(file: AppFile): Long = withContext(NonCancellable) {
        val id = appFileDao.insertFile(file)
        val saved = file.copy(id = id.toInt())
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushFileToCloud(context, email, saved)
        }
        id
    }

    suspend fun deleteFile(file: AppFile) = withContext(NonCancellable) {
        appFileDao.deleteFile(file)
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushFileToCloud(context, email, file, isDeleted = true)
        }
    }

    // Family Ledger Operations
    val allFamilyMembers: Flow<List<FamilyMember>> = familyMemberDao.getAllMembers()
    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()
    val allFinancialLogs: Flow<List<FinancialLog>> = financialLogDao.getAllLogs()
    val allFinanceTransactions: Flow<List<FinanceTransaction>> = financeTransactionDao.getAllTransactions()
    val allFinanceCategories: Flow<List<FinanceCategory>> = financeCategoryDao.getAllCategories()

    suspend fun insertFamilyMember(member: FamilyMember): Long = withContext(NonCancellable) {
        familyMemberDao.insertMember(member)
    }

    suspend fun deleteFamilyMember(member: FamilyMember) = withContext(NonCancellable) {
        familyMemberDao.deleteMember(member)
    }

    suspend fun insertFinancialAccount(account: FinancialAccount): Long = withContext(NonCancellable) {
        financialAccountDao.insertAccount(account)
    }

    suspend fun deleteFinancialAccount(account: FinancialAccount) = withContext(NonCancellable) {
        financialAccountDao.deleteAccount(account)
    }

    suspend fun insertFinancialLog(log: FinancialLog): Long = withContext(NonCancellable) {
        financialLogDao.insertLog(log)
    }

    suspend fun deleteFinancialLog(log: FinancialLog) = withContext(NonCancellable) {
        financialLogDao.deleteLog(log)
    }

    suspend fun insertFinanceTransaction(transaction: FinanceTransaction): Long = withContext(NonCancellable) {
        val id = financeTransactionDao.insertTransaction(transaction)
        val saved = transaction.copy(id = id.toInt())
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushFinanceToCloud(context, email, saved)
        }
        id
    }

    suspend fun deleteFinanceTransaction(transaction: FinanceTransaction) = withContext(NonCancellable) {
        financeTransactionDao.deleteTransaction(transaction)
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushFinanceToCloud(context, email, transaction, isDeleted = true)
        }
    }

    suspend fun insertFinanceCategory(category: FinanceCategory): Long = withContext(NonCancellable) {
        financeCategoryDao.insertCategory(category)
    }

    suspend fun deleteFinanceCategory(category: FinanceCategory) = withContext(NonCancellable) {
        financeCategoryDao.deleteCategory(category)
    }

    // Focus Record Operations
    val allFocusRecords: Flow<List<FocusRecordEntity>> = focusRecordDao.getAllRecords()

    suspend fun insertFocusRecord(record: FocusRecordEntity): Long = withContext(NonCancellable) {
        focusRecordDao.insertRecord(record)
    }

    suspend fun updateFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.updateRecord(record)
    }

    suspend fun deleteFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.deleteRecord(record)
    }

    suspend fun getFocusRecordsForDate(dateStr: String): List<FocusRecordEntity> {
        return focusRecordDao.getRecordsForDate(dateStr)
    }

    // Keep Note Operations
    private val keepNoteDao = db.keepNoteDao()

    val allKeepNotes: Flow<List<KeepNote>> = keepNoteDao.getAllKeepNotes()

    suspend fun getAllKeepNotesDirect(): List<KeepNote> {
        return keepNoteDao.getAllKeepNotesDirect()
    }

    suspend fun insertKeepNote(note: KeepNote): Long = withContext(NonCancellable) {
        keepNoteDao.insertKeepNote(note)
    }

    suspend fun updateKeepNote(note: KeepNote) = withContext(NonCancellable) {
        keepNoteDao.updateKeepNote(note)
    }

    suspend fun deleteKeepNote(note: KeepNote) = withContext(NonCancellable) {
        keepNoteDao.deleteKeepNote(note)
    }

    suspend fun clearAllKeepNotes() = withContext(NonCancellable) {
        keepNoteDao.clearAllKeepNotes()
    }

    // Health Record Operations
    private val healthRecordDao = db.healthRecordDao()

    fun getHealthRecordFlow(dateString: String): Flow<HealthRecord?> {
        return healthRecordDao.getHealthRecordFlow(dateString)
    }

    suspend fun getHealthRecordDirect(dateString: String): HealthRecord? {
        return healthRecordDao.getHealthRecordDirect(dateString)
    }

    fun getAllHealthRecordsFlow(): Flow<List<HealthRecord>> {
        return healthRecordDao.getAllHealthRecordsFlow()
    }

    suspend fun insertOrUpdateHealthRecord(record: HealthRecord) = withContext(NonCancellable) {
        healthRecordDao.insertOrUpdate(record)
        pushLiveSync { email ->
            com.example.api.AppDataLiveSyncEngine.pushHealthToCloud(context, email, record)
        }
    }

    suspend fun clearAllHealthRecords() = withContext(NonCancellable) {
        healthRecordDao.clearAllHealthRecords()
    }

    // Local Active Session Operations
    val activeSession: Flow<LocalActiveSession?> = localActiveSessionDao.getActiveSessionFlow()

    suspend fun getActiveSession(): LocalActiveSession? {
        return localActiveSessionDao.getActiveSession()
    }

    suspend fun insertOrUpdateActiveSession(session: LocalActiveSession) = withContext(NonCancellable) {
        localActiveSessionDao.insertOrUpdateSession(session)
    }

    suspend fun clearActiveSession() = withContext(NonCancellable) {
        localActiveSessionDao.clearActiveSession()
    }

    // Outbox Queue Operations
    val pendingQueue: Flow<List<OutboxQueue>> = outboxQueueDao.getPendingQueueFlow()

    suspend fun getPendingQueueDirect(): List<OutboxQueue> {
        return outboxQueueDao.getPendingQueueDirect()
    }

    suspend fun insertQueueItem(item: OutboxQueue): Long = withContext(NonCancellable) {
        // Delegates to OutboxQueueDao which handles RTDB live sync consolidation
        android.util.Log.d("LocalRepository", "Enqueuing outbox item: action=${item.action_type}, target=${item.routing_target}")
        outboxQueueDao.insertQueueItem(item)
    }

    suspend fun updateQueueItem(item: OutboxQueue) = withContext(NonCancellable) {
        outboxQueueDao.updateQueueItem(item)
    }

    suspend fun deleteQueueItem(item: OutboxQueue) = withContext(NonCancellable) {
        outboxQueueDao.deleteQueueItem(item)
    }

    suspend fun deleteQueueItemById(queueId: Int) = withContext(NonCancellable) {
        outboxQueueDao.deleteQueueItemById(queueId)
    }

    suspend fun updateQueueItemStatusAndIncrementRetry(queueId: Int, status: String) = withContext(NonCancellable) {
        outboxQueueDao.updateStatusAndIncrementRetry(queueId, status)
    }

    suspend fun recoverQuarantinedRecords() = withContext(NonCancellable) {
        outboxQueueDao.recoverQuarantinedRecords()
    }

    // Local History Vault Operations
    val allHistoryVault: Flow<List<LocalHistoryVault>> = localHistoryVaultDao.getAllHistoryFlow()

    fun getHistoryVaultForDate(dateString: String): Flow<List<LocalHistoryVault>> {
        return localHistoryVaultDao.getHistoryForDateFlow(dateString)
    }

    suspend fun getHistoryIntervalsForDate(dateString: String): List<LocalHistoryVaultInterval> {
        return localHistoryVaultDao.getHistoryIntervalsForDate(dateString)
    }

    suspend fun insertHistoryVaultRecord(record: LocalHistoryVault): Long = withContext(NonCancellable) {
        localHistoryVaultDao.insertRecord(record)
    }

    suspend fun updateHistoryVaultRecord(record: LocalHistoryVault) = withContext(NonCancellable) {
        localHistoryVaultDao.updateRecord(record)
    }

    suspend fun deleteHistoryVaultRecord(record: LocalHistoryVault) = withContext(NonCancellable) {
        localHistoryVaultDao.deleteRecord(record)
    }

    suspend fun getHistoryVaultRecordById(recordId: String): LocalHistoryVault? {
        return localHistoryVaultDao.getRecordById(recordId)
    }

    suspend fun getUnsyncedHistoryVaultRecords(): List<LocalHistoryVault> {
        return localHistoryVaultDao.getUnsyncedRecords()
    }
}
