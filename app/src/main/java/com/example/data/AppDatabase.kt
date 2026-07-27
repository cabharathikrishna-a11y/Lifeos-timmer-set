package com.example.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.example.api.TimelineEvent

// ==========================================
// 1. Entities
// ==========================================

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int = 30,
    val actualMinutes: Int = 0,
    val isCompleted: Boolean = false,
    val parentTaskId: Int? = null,
    val listCategory: String = "Inbox",
    val timeBlockTimestamp: Long? = null,
    val nagModeEnabled: Boolean = false,
    val nagIntervalMinutes: Int = 5,
    val priority: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW"
    val dueDateString: String = "", // "YYYY-MM-DD" style execution date target
    val orderIndex: Int = 0
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val streakCount: Int = 0,
    val lastCompletedTimestamp: Long? = null,
    val listCategory: String = "Health & Vigor",
    val timeOfDay: String = "Morning",
    val targetCount: Int = 1,
    val frequency: String = "DAILY", // "DAILY", "WEEKLY", "MONTHLY", "MONTHLY_ONCE"
    val weeklyDay: Int = 2, // 2 = Calendar.MONDAY
    val monthlyStartDate: Int = 1,
    val monthlyEndDate: Int = 30,
    val orderIndex: Int = 0,
    val scheduledTime: String = "08:00",
    val isReminderEnabled: Boolean = false
)

@Entity(tableName = "habit_completions",
        foreignKeys = [
            ForeignKey(
                entity = Habit::class,
                parentColumns = ["id"],
                childColumns = ["habitId"],
                onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [Index(value = ["habitId"])])
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val dateString: String // "YYYY-MM-DD"
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val dateString: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentsJson: String = "" // Serialized as a JSON list of attachment details
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "INCOME" or "EXPENSE"
    val amount: Double,
    val categoryTag: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "deadlines")
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetTimestamp: Long,
    val isCompleted: Boolean = false
)

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val type: String = "SAVINGS", // "SAVINGS" or "BUDGET"
    val categoryTag: String = "General"
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val middleName: String = "",
    val lastName: String,
    val jobTitle: String = "",
    val email: String = "",
    val address: String = "",
    val phone: String = "",
    val dobString: String = "", // "YYYY-MM-DD" or "MM-DD"
    val photoUri: String? = null,
    val anniversaryString: String = "",
    val additionalFieldsJson: String = "",
    val additionalDatesJson: String = "",
    val folder: String = "All",
    val attachedFilesJson: String = "",
    val systemContactId: Long? = null,
    val googleContactId: String? = null
)

@Entity(tableName = "app_files")
data class AppFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // e.g. "/docs" or "/media"
    val size: Long,
    val mimeType: String,
    val uriString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Entity(tableName = "focus_records")
data class FocusRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskTitle: String = "",
    val tag: String = "",
    val notes: String = "",
    val durationSeconds: Int = 0,
    val durationMinutes: Int = 0,
    val dateString: String = "", // e.g., "yyyy-MM-dd"
    val startTime: String = "",  // e.g., "14:30"
    val endTime: String = "",    // e.g., "14:55"
    val timestamp: Long = System.currentTimeMillis(), // Universal sorting anchor
    @ColumnInfo(name = "user_email") val userEmail: String = ""
)

@Entity(tableName = "keep_notes")
data class KeepNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val colorHex: String = "#202124", // Default Keep dark theme Charcoal
    val isSynced: Boolean = false,
    val websiteUrl: String? = null,
    val customLogoUrl: String? = null
)

// ==========================================
// 2. DAOs
// ==========================================

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasksDirect(): List<Task>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY id DESC")
    suspend fun getCompletedTasksDirect(): List<Task>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDateString != ''")
    suspend fun getActiveTasksWithDeadlines(): List<Task>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId")
    fun getSubtasks(parentId: Int): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): Task?

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubtasks(parentId: Int)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY orderIndex ASC, id ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    // Completions
    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId")
    fun getCompletionsForHabit(habitId: Int): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions")
    fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: HabitCompletion)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString")
    suspend fun deleteCompletion(habitId: Int, dateString: String)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    suspend fun getAllJournalEntriesDirect(): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE title LIKE :query OR text LIKE :query")
    fun searchJournalEntries(query: String): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntry)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY timestamp DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries ORDER BY timestamp DESC")
    suspend fun getAllLedgerEntriesDirect(): List<LedgerEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntry)

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntry)

    @Delete
    suspend fun deleteLedgerEntry(entry: LedgerEntry)
}

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadlines ORDER BY isCompleted ASC, targetTimestamp ASC")
    fun getAllDeadlines(): Flow<List<Deadline>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeadline(deadline: Deadline): Long

    @Update
    suspend fun updateDeadline(deadline: Deadline)

    @Delete
    suspend fun deleteDeadline(deadline: Deadline)
}

@Dao
interface FinancialGoalDao {
    @Query("SELECT * FROM financial_goals ORDER BY id DESC")
    fun getAllFinancialGoals(): Flow<List<FinancialGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialGoal(goal: FinancialGoal): Long

    @Update
    suspend fun updateFinancialGoal(goal: FinancialGoal)

    @Delete
    suspend fun deleteFinancialGoal(goal: FinancialGoal)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY firstName ASC, lastName ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)
}

@Entity(tableName = "custom_lists")
data class CustomList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String = "#2196F3",
    val viewType: String = "List", // "List", "Kanban", "Timeline"
    val parentListName: String? = null // Sublist relationship: null if primary list
)

@Dao
interface AppFileDao {
    @Query("SELECT * FROM app_files ORDER BY timestamp DESC")
    fun getAllFiles(): Flow<List<AppFile>>

    @Query("SELECT * FROM app_files ORDER BY timestamp DESC")
    suspend fun getAllFilesDirect(): List<AppFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: AppFile): Long

    @Delete
    suspend fun deleteFile(file: AppFile)
}

@Dao
interface FocusRecordDao {
    @Query("SELECT * FROM focus_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<FocusRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: FocusRecordEntity): Long

    @Update
    suspend fun updateRecord(record: FocusRecordEntity)

    @Delete
    suspend fun deleteRecord(record: FocusRecordEntity)

    // Specific query to pull today's stats efficiently without loading all history
    @Query("SELECT * FROM focus_records WHERE dateString = :dateStr")
    suspend fun getRecordsForDate(dateStr: String): List<FocusRecordEntity>

    @Query("DELETE FROM focus_records WHERE dateString = :dateStr")
    suspend fun deleteRecordsForDate(dateStr: String)
}

@Dao
interface CustomListDao {
    @Query("SELECT * FROM custom_lists ORDER BY name ASC")
    fun getAllLists(): Flow<List<CustomList>>

    @Query("SELECT * FROM custom_lists ORDER BY name ASC")
    suspend fun getAllListsDirect(): List<CustomList>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: CustomList): Long

    @Update
    suspend fun updateList(list: CustomList)

    @Delete
    suspend fun deleteList(list: CustomList)
}

// ==========================================
// Family Ledger Entities
// ==========================================

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "financial_accounts")
data class FinancialAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: Int, // id of FamilyMember
    val name: String,
    val categoryType: String, // "LONG_TERM_ASSETS", "CURRENT_ASSETS", "LONG_TERM_LIABILITIES", "CURRENT_LIABILITIES"
    val openingValue: Double
)

@Entity(tableName = "financial_logs")
data class FinancialLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val logType: String, // "APPRECIATION", "DEPRECIATION", "INTEREST_ACCRUED", "PAID", "INITIAL"
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_transactions")
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: Int,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val fromAccountId: Int? = null,
    val fromCategory: String? = null,
    val toAccountId: Int? = null,
    val toCategory: String? = null,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "finance_categories")
data class FinanceCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String // "INCOME" or "EXPENSE"
)

// ==========================================
// Family Ledger DAOs
// ==========================================

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY name ASC")
    fun getAllMembers(): Flow<List<FamilyMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FamilyMember): Long

    @Delete
    suspend fun deleteMember(member: FamilyMember)
}

@Dao
interface FinancialAccountDao {
    @Query("SELECT * FROM financial_accounts")
    fun getAllAccounts(): Flow<List<FinancialAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: FinancialAccount): Long

    @Delete
    suspend fun deleteAccount(account: FinancialAccount)
}

@Dao
interface FinancialLogDao {
    @Query("SELECT * FROM financial_logs ORDER BY timestamp ASC")
    fun getAllLogs(): Flow<List<FinancialLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FinancialLog): Long

    @Delete
    suspend fun deleteLog(log: FinancialLog)
}

@Dao
interface FinanceTransactionDao {
    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsDirect(): List<FinanceTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceTransaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: FinanceTransaction)
}

@Dao
interface FinanceCategoryDao {
    @Query("SELECT * FROM finance_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<FinanceCategory>>

    @Query("SELECT * FROM finance_categories ORDER BY name ASC")
    suspend fun getAllCategoriesDirect(): List<FinanceCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FinanceCategory): Long

    @Delete
    suspend fun deleteCategory(category: FinanceCategory)
}

@Dao
interface KeepNoteDao {
    @Query("SELECT * FROM keep_notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllKeepNotes(): Flow<List<KeepNote>>

    @Query("SELECT * FROM keep_notes ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getAllKeepNotesDirect(): List<KeepNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeepNote(note: KeepNote): Long

    @Update
    suspend fun updateKeepNote(note: KeepNote)

    @Delete
    suspend fun deleteKeepNote(note: KeepNote)

    @Query("DELETE FROM keep_notes")
    suspend fun clearAllKeepNotes()
}

@Entity(tableName = "health_records")
data class HealthRecord(
    @PrimaryKey val dateString: String, // e.g., "yyyy-MM-dd"
    val steps: Int = 0,
    val stepGoal: Int = 10000,
    val sleepMinutes: Int = 0,
    val sleepGoalMinutes: Int = 480, // 8 hours
    val waterMl: Int = 0,
    val waterGoalMl: Int = 2000, // 2 Liters
    val caloriesBurned: Int = 0,
    val calorieGoal: Int = 2000,
    val activeMinutes: Int = 0,
    val activeMinutesGoal: Int = 45,
    val heartRateAvg: Int = 72,
    val heartRateMin: Int = 60,
    val heartRateMax: Int = 120,
    val breakfastFoods: String = "",
    val lunchFoods: String = "",
    val dinnerFoods: String = "",
    val snacksFoods: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_records WHERE dateString = :dateString LIMIT 1")
    fun getHealthRecordFlow(dateString: String): Flow<HealthRecord?>

    @Query("SELECT * FROM health_records WHERE dateString = :dateString LIMIT 1")
    suspend fun getHealthRecordDirect(dateString: String): HealthRecord?

    @Query("SELECT * FROM health_records ORDER BY dateString DESC")
    fun getAllHealthRecordsFlow(): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records ORDER BY dateString DESC")
    suspend fun getAllHealthRecordsDirect(): List<HealthRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: HealthRecord)

    @Query("DELETE FROM health_records")
    suspend fun clearAllHealthRecords()
}

// ==========================================
// Sandbox, Outbox, and local history vault (Step 1.1)
// ==========================================

@Entity(tableName = "local_active_session")
data class LocalActiveSession(
    @PrimaryKey val session_id: String,
    val status: String,                  // 'FOCUSING', 'PAUSED', 'BREAKING', 'IDLE'
    val mode: String = "POMODORO",
    val tag: String = "Study",
    val task_title: String = "General Focus",
    val base_focus_time_ms: Long = 0,
    val base_break_time_ms: Long = 0,
    val last_event_ts_ms: Long = 0,
    val base_focus_formatted: String = "00:00:00",
    val last_event_formatted: String = "00:00:00:000",
    val is_current_leader: Int = 1       // 1 = True (Leader), 0 = False (Follower)
)

@Entity(
    tableName = "outbox_queue",
    indices = [
        Index(value = ["created_at_ms"], name = "idx_outbox_created"),
        Index(value = ["mutation_id"], unique = true)
    ]
)
data class OutboxQueue(
    @PrimaryKey(autoGenerate = true) val queue_id: Int = 0,
    val mutation_id: String,
    val created_at_ms: Long,
    val routing_target: String,          // 'RTDB_LIVE_SYNC' or 'FIRESTORE_DIRECT_VAULT'
    val action_type: String,             // 'START', 'PAUSE', 'ARCHIVE_SESSION', etc.
    val payload_json: String,            
    val retry_count: Int = 0,
    val status: String = "PENDING"       // 'PENDING', 'PROCESSING', 'FAILED'
)

@Entity(
    tableName = "local_history_vault",
    indices = [
        Index(value = ["date_string", "subject"], name = "idx_vault_date")
    ]
)
data class LocalHistoryVault(
    @PrimaryKey val record_id: String,
    val date_string: String,
    val subject: String,
    val task_title: String?,
    val start_time_ms: Long,
    val end_time_ms: Long,
    val total_focus_ms: Long,
    val total_break_ms: Long = 0,
    val pause_count: Int = 0,
    val duration_formatted: String,
    val start_time_formatted: String,
    val end_time_formatted: String,
    val is_synced_to_firestore: Int = 0,  // 0 = Unsynced, 1 = Synced to Cloud
    @ColumnInfo(name = "mode") val mode: String = "POMODORO",
    @ColumnInfo(name = "last_modified_ms") val lastModifiedMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_manual_entry") val isManualEntry: Boolean = false,
    @ColumnInfo(name = "timeline_json") val timeline_json: String? = null,
    @ColumnInfo(name = "timeline") val timeline: List<TimelineEvent> = emptyList(),
    @ColumnInfo(name = "user_email") val userEmail: String = ""
)

@Entity(tableName = "local_shields_vault")
data class LocalShieldsVault(
    @PrimaryKey val uuid: String,
    val donor_email: String,
    val donor_name: String,
    val granted_timestamp: Long,
    val is_consumed: Boolean,
    val consumed_date: String? // "yyyy-MM-dd"
)

@Entity(tableName = "syllabus_completion_vault")
data class SyllabusCompletionVault(
    @PrimaryKey val topicId: String,
    val isCompleted: Boolean,
    val lastModifiedMs: Long = System.currentTimeMillis()
)

@Dao
interface SyllabusCompletionDao {
    @Query("SELECT * FROM syllabus_completion_vault")
    fun getAllCompletionFlow(): Flow<List<SyllabusCompletionVault>>

    @Query("SELECT * FROM syllabus_completion_vault")
    suspend fun getAllCompletionSync(): List<SyllabusCompletionVault>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(item: SyllabusCompletionVault)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(items: List<SyllabusCompletionVault>)

    @Query("DELETE FROM syllabus_completion_vault WHERE topicId = :topicId")
    suspend fun deleteCompletion(topicId: String)

    @Query("DELETE FROM syllabus_completion_vault")
    suspend fun deleteAll()
}

@Dao
interface LocalShieldsVaultDao {
    @Query("SELECT * FROM local_shields_vault ORDER BY granted_timestamp DESC")
    fun getAllShields(): Flow<List<LocalShieldsVault>>

    @Query("SELECT * FROM local_shields_vault WHERE is_consumed = 0 ORDER BY granted_timestamp ASC")
    suspend fun getUnconsumedShieldsSync(): List<LocalShieldsVault>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShield(shield: LocalShieldsVault)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShields(shields: List<LocalShieldsVault>)

    @Query("UPDATE local_shields_vault SET is_consumed = :isConsumed, consumed_date = :consumedDate WHERE uuid = :uuid")
    suspend fun markShieldConsumed(uuid: String, isConsumed: Boolean, consumedDate: String?)

    @Query("DELETE FROM local_shields_vault WHERE uuid = :uuid")
    suspend fun deleteShield(uuid: String)

    @Query("DELETE FROM local_shields_vault")
    suspend fun deleteAllShields()
}

data class LocalHistoryVaultInterval(
    val start_time_ms: Long,
    val end_time_ms: Long,
    val total_focus_ms: Long
)

@Dao
interface LocalActiveSessionDao {
    @Query("SELECT * FROM local_active_session LIMIT 1")
    fun getActiveSessionFlow(): Flow<LocalActiveSession?>

    @Query("SELECT * FROM local_active_session LIMIT 1")
    suspend fun getActiveSession(): LocalActiveSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: LocalActiveSession)

    @Query("DELETE FROM local_active_session")
    suspend fun clearActiveSession()
}

@Dao
interface OutboxQueueDao {
    @Query("SELECT * FROM outbox_queue WHERE status = 'PENDING' ORDER BY created_at_ms ASC")
    fun getPendingQueueFlow(): Flow<List<OutboxQueue>>

    @Query("SELECT * FROM outbox_queue WHERE status = 'PENDING' ORDER BY created_at_ms ASC")
    suspend fun getPendingQueueDirect(): List<OutboxQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItemRaw(item: OutboxQueue): Long

    @Transaction
    suspend fun insertQueueItem(item: OutboxQueue): Long {
        return insertQueueItemRaw(item)
    }

    @Update
    suspend fun updateQueueItem(item: OutboxQueue)

    @Delete
    suspend fun deleteQueueItem(item: OutboxQueue)

    @Query("DELETE FROM outbox_queue WHERE queue_id = :queueId")
    suspend fun deleteQueueItemById(queueId: Int)

    @Query("DELETE FROM outbox_queue WHERE routing_target = 'RTDB_LIVE_SYNC'")
    suspend fun clearRtdbLiveSyncItems()

    @Query("UPDATE outbox_queue SET status = :status, retry_count = retry_count + 1 WHERE queue_id = :queueId")
    suspend fun updateStatusAndIncrementRetry(queueId: Int, status: String)

    @Query("UPDATE outbox_queue SET retry_count = retry_count + 1 WHERE queue_id = :queueId")
    suspend fun incrementRetryCount(queueId: Int)

    @Query("UPDATE outbox_queue SET status = :status WHERE queue_id = :queueId")
    suspend fun updateStatus(queueId: Int, status: String)

    @Query("UPDATE outbox_queue SET status = 'PENDING', retry_count = 0 WHERE status = 'QUARANTINED'")
    suspend fun recoverQuarantinedRecords()
}

@Dao
interface LocalHistoryVaultDao {
    @Query("SELECT * FROM local_history_vault ORDER BY start_time_ms DESC LIMIT 1000")
    fun getAllHistoryFlow(): Flow<List<LocalHistoryVault>>

    @Query("SELECT * FROM local_history_vault WHERE date_string = :dateString ORDER BY start_time_ms ASC")
    fun getHistoryForDateFlow(dateString: String): Flow<List<LocalHistoryVault>>

    @Query("SELECT start_time_ms, end_time_ms, total_focus_ms FROM local_history_vault WHERE date_string = :dateString ORDER BY start_time_ms ASC")
    suspend fun getHistoryIntervalsForDate(dateString: String): List<LocalHistoryVaultInterval>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: LocalHistoryVault): Long

    @Update
    suspend fun updateRecord(record: LocalHistoryVault)

    @Delete
    suspend fun deleteRecord(record: LocalHistoryVault)

    @Query("SELECT * FROM local_history_vault WHERE record_id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: String): LocalHistoryVault?

    @Query("SELECT * FROM local_history_vault WHERE is_synced_to_firestore = 0")
    suspend fun getUnsyncedRecords(): List<LocalHistoryVault>

    @Query("SELECT COALESCE(SUM(total_focus_ms), 0) FROM local_history_vault WHERE date_string = :date AND (record_id LIKE 'manual_%' OR mode = 'MANUAL_LOG' OR is_manual_entry = 1)")
    suspend fun getTodayManualFocusTimeMs(date: String): Long

    @Query("SELECT COALESCE(SUM(total_focus_ms), 0) FROM local_history_vault WHERE date_string = :date")
    suspend fun getTodayTotalFocusTimeMs(date: String): Long

    @Query("DELETE FROM local_history_vault WHERE record_id = :recordId")
    suspend fun deleteRecordById(recordId: String)

    @Query("SELECT * FROM local_history_vault ORDER BY start_time_ms DESC")
    suspend fun getAllHistoryDirect(): List<LocalHistoryVault>
}

// ==========================================
// 3. Database
// ==========================================

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `focus_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskTitle` TEXT NOT NULL,
                `tag` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `dateString` TEXT NOT NULL,
                `startTime` TEXT NOT NULL,
                `endTime` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("ALTER TABLE `local_history_vault` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'POMODORO'")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            database.execSQL("ALTER TABLE `local_history_vault` ADD COLUMN `last_modified_ms` INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            database.execSQL("ALTER TABLE `local_history_vault` ADD COLUMN `is_manual_entry` INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("ALTER TABLE `local_history_vault` ADD COLUMN `timeline` TEXT NOT NULL DEFAULT '[]'")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Safe empty migration
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Safe empty migration
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS `local_shields_vault` (`uuid` TEXT NOT NULL, `donor_email` TEXT NOT NULL, `donor_name` TEXT NOT NULL, `granted_timestamp` INTEGER NOT NULL, `is_consumed` INTEGER NOT NULL, `consumed_date` TEXT, PRIMARY KEY(`uuid`))")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS `syllabus_completion_vault` (`topicId` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `lastModifiedMs` INTEGER NOT NULL, PRIMARY KEY(`topicId`))")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("ALTER TABLE `app_files` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("ALTER TABLE `app_files` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("DROP TABLE IF EXISTS `chat_messages`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER NOT NULL, `sender_id` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, `created_at` TEXT, `reactions` TEXT NOT NULL, `is_pinned` INTEGER NOT NULL, `reply_to_id` INTEGER, `reply_to_text` TEXT, `reply_to_sender` TEXT, PRIMARY KEY(`id`))")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("DROP TABLE IF EXISTS `chat_messages`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER NOT NULL, `sender_id` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, `created_at` TEXT, `reactions` TEXT NOT NULL, `is_pinned` INTEGER NOT NULL, `reply_to_id` INTEGER, `reply_to_text` TEXT, `reply_to_sender` TEXT, PRIMARY KEY(`id`))")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("DROP TABLE IF EXISTS `chat_messages`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER NOT NULL, `sender_id` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, `created_at` TEXT, `reactions` TEXT NOT NULL, `is_pinned` INTEGER NOT NULL, `reply_to_id` INTEGER, `reply_to_text` TEXT, `reply_to_sender` TEXT, `type` TEXT NOT NULL DEFAULT 'text', `content_url` TEXT, `local_path` TEXT, `file_name` TEXT, `file_size_kb` INTEGER, `mime_type` TEXT, `duration_sec` INTEGER, `timestamp` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("ALTER TABLE `local_history_vault` ADD COLUMN `user_email` TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            database.execSQL("ALTER TABLE `focus_records` ADD COLUMN `user_email` TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val autoMigrations = (13..19).map { startVersion ->
    object : Migration(startVersion, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `keep_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `colorHex` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, `websiteUrl` TEXT, `customLogoUrl` TEXT)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `health_records` (`dateString` TEXT NOT NULL, `steps` INTEGER NOT NULL, `stepGoal` INTEGER NOT NULL, `sleepMinutes` INTEGER NOT NULL, `sleepGoalMinutes` INTEGER NOT NULL, `waterMl` INTEGER NOT NULL, `waterGoalMl` INTEGER NOT NULL, `caloriesBurned` INTEGER NOT NULL, `calorieGoal` INTEGER NOT NULL, `activeMinutes` INTEGER NOT NULL, `activeMinutesGoal` INTEGER NOT NULL, `heartRateAvg` INTEGER NOT NULL, `heartRateMin` INTEGER NOT NULL, `heartRateMax` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`dateString`))")
            try { database.execSQL("ALTER TABLE `health_records` ADD COLUMN `breakfastFoods` TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            try { database.execSQL("ALTER TABLE `health_records` ADD COLUMN `lunchFoods` TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            try { database.execSQL("ALTER TABLE `health_records` ADD COLUMN `dinnerFoods` TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            try { database.execSQL("ALTER TABLE `health_records` ADD COLUMN `snacksFoods` TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
        }
    }
}.toTypedArray()

@Database(
    entities = [
        Task::class,
        Habit::class,
        HabitCompletion::class,
        JournalEntry::class,
        LedgerEntry::class,
        Deadline::class,
        FinancialGoal::class,
        Contact::class,
        AppFile::class,
        CustomList::class,
        FamilyMember::class,
        FinancialAccount::class,
        FinancialLog::class,
        FinanceTransaction::class,
        FinanceCategory::class,
        FocusRecordEntity::class,
        KeepNote::class,
        HealthRecord::class,
        LocalActiveSession::class,
        OutboxQueue::class,
        LocalHistoryVault::class,
        LocalShieldsVault::class,
        SyllabusCompletionVault::class,
        com.example.model.ChatMessage::class
    ],
    version = 31,
    exportSchema = true
)
@TypeConverters(TimelineConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun journalDao(): JournalDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun financialGoalDao(): FinancialGoalDao
    abstract fun contactDao(): ContactDao
    abstract fun appFileDao(): AppFileDao
    abstract fun customListDao(): CustomListDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun financialLogDao(): FinancialLogDao
    abstract fun financeTransactionDao(): FinanceTransactionDao
    abstract fun financeCategoryDao(): FinanceCategoryDao
    abstract fun focusRecordDao(): FocusRecordDao
    abstract fun keepNoteDao(): KeepNoteDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun localActiveSessionDao(): LocalActiveSessionDao
    abstract fun outboxQueueDao(): OutboxQueueDao
    abstract fun localHistoryVaultDao(): LocalHistoryVaultDao
    abstract fun localShieldsVaultDao(): LocalShieldsVaultDao
    abstract fun syllabusCompletionDao(): SyllabusCompletionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = androidx.room.Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java, "life_os_database"
                    )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addMigrations(
                        MIGRATION_12_13, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                        MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                        MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, *autoMigrations
                    )
                    .build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Database initialization failed, destructive fallback forced", e)
                    context.deleteDatabase("life_os_database")
                    val fallbackInstance = androidx.room.Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java, "life_os_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    INSTANCE = fallbackInstance
                    fallbackInstance
                }
            }
        }
    }
}
