package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupOptions(
    val exportTasks: Boolean = true,
    val exportHabits: Boolean = true,
    val exportJournal: Boolean = true,
    val exportFinances: Boolean = true,
    val exportContacts: Boolean = true,
    val exportFiles: Boolean = true,
    val exportSettings: Boolean = true,
    val exportHealth: Boolean = true,
    val exportNotes: Boolean = true,
    val exportFocus: Boolean = true
)

object DatabaseBackupHelper {
    private const val TAG = "DatabaseBackupHelper"

    fun getBackupOptions(context: Context): BackupOptions {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return BackupOptions(
            exportTasks = prefs.getBoolean("backup_option_tasks", true),
            exportHabits = prefs.getBoolean("backup_option_habits", true),
            exportJournal = prefs.getBoolean("backup_option_journal", true),
            exportFinances = prefs.getBoolean("backup_option_finances", true),
            exportContacts = prefs.getBoolean("backup_option_contacts", true),
            exportFiles = prefs.getBoolean("backup_option_files", true),
            exportSettings = prefs.getBoolean("backup_option_settings", true),
            exportHealth = prefs.getBoolean("backup_option_health", true),
            exportNotes = prefs.getBoolean("backup_option_notes", true),
            exportFocus = prefs.getBoolean("backup_option_focus", true)
        )
    }

    suspend fun exportData(context: Context, database: AppDatabase, uri: Uri, isAutoBackup: Boolean = false): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { fos ->
                exportDataToStream(context, database, fos, isAutoBackup)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data to Uri", e)
            false
        }
    }

    suspend fun exportDataToStream(context: Context, database: AppDatabase, outputStream: java.io.OutputStream, isAutoBackup: Boolean = false): Boolean {
        return try {
            val options = getBackupOptions(context)
            val root = JSONObject()
            root.put("version", 5) // current schema version
            root.put("files_dir_path_placeholder", com.example.util.StorageHelper.getAppFilesDir(context).absolutePath)

            // 1. SharedPreferences backup
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (options.exportSettings) {
                val settingsJson = JSONObject()
                val allPrefs = prefs.all
                allPrefs.forEach { (key, value) ->
                    if (key.startsWith("backup_option_")) return@forEach
                    when (value) {
                        is Boolean -> settingsJson.put(key, value)
                        is Int -> settingsJson.put(key, value)
                        is Long -> settingsJson.put(key, value)
                        is Float -> settingsJson.put(key, value.toDouble())
                        is String -> settingsJson.put(key, value)
                    }
                }
                root.put("shared_preferences", settingsJson)
            }

            // 2. Tasks
            val tasks = if (options.exportTasks) database.taskDao().getAllTasksDirect() else emptyList()
            val tasksArray = JSONArray()
            tasks.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("title", it.title)
                obj.put("description", it.description)
                obj.put("estimatedMinutes", it.estimatedMinutes)
                obj.put("actualMinutes", it.actualMinutes)
                obj.put("isCompleted", it.isCompleted)
                obj.put("parentTaskId", it.parentTaskId ?: -1)
                obj.put("listCategory", it.listCategory)
                obj.put("timeBlockTimestamp", it.timeBlockTimestamp ?: -1L)
                obj.put("nagModeEnabled", it.nagModeEnabled)
                obj.put("nagIntervalMinutes", it.nagIntervalMinutes)
                obj.put("priority", it.priority)
                obj.put("dueDateString", it.dueDateString)
                obj.put("orderIndex", it.orderIndex)
                tasksArray.put(obj)
            }
            root.put("tasks", tasksArray)

            // 3. Habits
            val habits = if (options.exportHabits) database.habitDao().getAllHabits().first() else emptyList()
            val habitsArray = JSONArray()
            habits.forEach {
                val obj = JSONObject()
                obj.put("id", it.id) // keep id so completions match if possible
                obj.put("name", it.name)
                obj.put("streakCount", it.streakCount)
                obj.put("lastCompletedTimestamp", it.lastCompletedTimestamp ?: -1L)
                obj.put("listCategory", it.listCategory)
                obj.put("timeOfDay", it.timeOfDay)
                obj.put("targetCount", it.targetCount)
                obj.put("frequency", it.frequency)
                obj.put("weeklyDay", it.weeklyDay)
                obj.put("monthlyStartDate", it.monthlyStartDate)
                obj.put("monthlyEndDate", it.monthlyEndDate)
                obj.put("orderIndex", it.orderIndex)
                obj.put("scheduledTime", it.scheduledTime)
                obj.put("isReminderEnabled", it.isReminderEnabled)
                habitsArray.put(obj)
            }
            root.put("habits", habitsArray)

            // 4. Habit Completions
            val completions = if (options.exportHabits) database.habitDao().getAllCompletions().first() else emptyList()
            val completionsArray = JSONArray()
            completions.forEach {
                val obj = JSONObject()
                obj.put("habitId", it.habitId)
                obj.put("dateString", it.dateString)
                completionsArray.put(obj)
            }
            root.put("habit_completions", completionsArray)

            // 5. Journal Entries
            val journal = if (options.exportJournal) database.journalDao().getAllJournalEntries().first() else emptyList()
            val journalArray = JSONArray()
            journal.forEach {
                val obj = JSONObject()
                obj.put("title", it.title)
                obj.put("text", it.text)
                obj.put("dateString", it.dateString)
                obj.put("timestamp", it.timestamp)
                obj.put("attachmentsJson", it.attachmentsJson)
                journalArray.put(obj)
            }
            root.put("journal_entries", journalArray)

            // 6. Ledger Entries
            val ledger = if (options.exportFinances) database.ledgerDao().getAllLedgerEntries().first() else emptyList()
            val ledgerArray = JSONArray()
            ledger.forEach {
                val obj = JSONObject()
                obj.put("type", it.type)
                obj.put("amount", it.amount)
                obj.put("categoryTag", it.categoryTag)
                obj.put("note", it.note)
                obj.put("timestamp", it.timestamp)
                ledgerArray.put(obj)
            }
            root.put("ledger_entries", ledgerArray)

            // 7. Deadlines
            val deadlines = if (options.exportTasks) database.deadlineDao().getAllDeadlines().first() else emptyList()
            val deadlinesArray = JSONArray()
            deadlines.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("targetTimestamp", it.targetTimestamp)
                obj.put("isCompleted", it.isCompleted)
                deadlinesArray.put(obj)
            }
            root.put("deadlines", deadlinesArray)

            // 8. Financial Goals
            val fg = if (options.exportFinances) database.financialGoalDao().getAllFinancialGoals().first() else emptyList()
            val fgArray = JSONArray()
            fg.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("targetAmount", it.targetAmount)
                obj.put("type", it.type)
                obj.put("categoryTag", it.categoryTag)
                fgArray.put(obj)
            }
            root.put("financial_goals", fgArray)

            // 9. Contacts
            val contacts = if (options.exportContacts) database.contactDao().getAllContacts().first() else emptyList()
            val contactsArray = JSONArray()
            contacts.forEach {
                val obj = JSONObject()
                obj.put("firstName", it.firstName)
                obj.put("middleName", it.middleName)
                obj.put("lastName", it.lastName)
                obj.put("jobTitle", it.jobTitle)
                obj.put("email", it.email)
                obj.put("address", it.address)
                obj.put("phone", it.phone)
                obj.put("dobString", it.dobString)
                obj.put("photoUri", it.photoUri ?: "")
                obj.put("anniversaryString", it.anniversaryString)
                obj.put("additionalFieldsJson", it.additionalFieldsJson)
                obj.put("additionalDatesJson", it.additionalDatesJson)
                obj.put("folder", it.folder)
                obj.put("attachedFilesJson", it.attachedFilesJson)
                contactsArray.put(obj)
            }
            root.put("contacts", contactsArray)

            // 10. App Files
            val files = if (options.exportFiles) database.appFileDao().getAllFiles().first() else emptyList()
            val filesArray = JSONArray()
            files.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("path", it.path)
                obj.put("size", it.size)
                obj.put("mimeType", it.mimeType)
                obj.put("uriString", it.uriString)
                obj.put("timestamp", it.timestamp)
                obj.put("isFavorite", it.isFavorite)
                filesArray.put(obj)
            }
            root.put("app_files", filesArray)

            // 11. Custom Lists
            val lists = if (options.exportTasks) database.customListDao().getAllLists().first() else emptyList()
            val listsArray = JSONArray()
            lists.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("colorHex", it.colorHex)
                obj.put("viewType", it.viewType)
                obj.put("parentListName", it.parentListName ?: "")
                listsArray.put(obj)
            }
            root.put("custom_lists", listsArray)

            // 12. Family Members
            val members = if (options.exportFinances) database.familyMemberDao().getAllMembers().first() else emptyList()
            val membersArray = JSONArray()
            members.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                membersArray.put(obj)
            }
            root.put("family_members", membersArray)

            // 13. Financial Accounts
            val accounts = if (options.exportFinances) database.financialAccountDao().getAllAccounts().first() else emptyList()
            val accountsArray = JSONArray()
            accounts.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("memberId", it.memberId)
                obj.put("name", it.name)
                obj.put("categoryType", it.categoryType)
                obj.put("openingValue", it.openingValue)
                accountsArray.put(obj)
            }
            root.put("financial_accounts", accountsArray)

            // 14. Financial Logs
            val logs = if (options.exportFinances) database.financialLogDao().getAllLogs().first() else emptyList()
            val logsArray = JSONArray()
            logs.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("accountId", it.accountId)
                obj.put("logType", it.logType)
                obj.put("amount", it.amount)
                obj.put("timestamp", it.timestamp)
                logsArray.put(obj)
            }
            root.put("financial_logs", logsArray)

            // 15. Finance Transactions
            val transactions = if (options.exportFinances) database.financeTransactionDao().getAllTransactions().first() else emptyList()
            val transactionsArray = JSONArray()
            transactions.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("memberId", it.memberId)
                obj.put("type", it.type)
                obj.put("fromAccountId", it.fromAccountId ?: -1)
                obj.put("fromCategory", it.fromCategory ?: "")
                obj.put("toAccountId", it.toAccountId ?: -1)
                obj.put("toCategory", it.toCategory ?: "")
                obj.put("amount", it.amount)
                obj.put("timestamp", it.timestamp)
                obj.put("note", it.note)
                transactionsArray.put(obj)
            }
            root.put("finance_transactions", transactionsArray)

            // 16. Finance Categories
            val categories = if (options.exportFinances) database.financeCategoryDao().getAllCategories().first() else emptyList()
            val categoriesArray = JSONArray()
            categories.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                obj.put("type", it.type)
                categoriesArray.put(obj)
            }
            root.put("finance_categories", categoriesArray)

            // 17. Health Records
            val healthRecords = if (options.exportHealth) database.healthRecordDao().getAllHealthRecordsFlow().first() else emptyList()
            val healthArray = JSONArray()
            healthRecords.forEach {
                val obj = JSONObject()
                obj.put("dateString", it.dateString)
                obj.put("steps", it.steps)
                obj.put("stepGoal", it.stepGoal)
                obj.put("sleepMinutes", it.sleepMinutes)
                obj.put("sleepGoalMinutes", it.sleepGoalMinutes)
                obj.put("waterMl", it.waterMl)
                obj.put("waterGoalMl", it.waterGoalMl)
                obj.put("caloriesBurned", it.caloriesBurned)
                obj.put("calorieGoal", it.calorieGoal)
                obj.put("activeMinutes", it.activeMinutes)
                obj.put("activeMinutesGoal", it.activeMinutesGoal)
                obj.put("heartRateAvg", it.heartRateAvg)
                obj.put("heartRateMin", it.heartRateMin)
                obj.put("heartRateMax", it.heartRateMax)
                obj.put("breakfastFoods", it.breakfastFoods)
                obj.put("lunchFoods", it.lunchFoods)
                obj.put("dinnerFoods", it.dinnerFoods)
                obj.put("snacksFoods", it.snacksFoods)
                obj.put("timestamp", it.timestamp)
                obj.put("isSynced", it.isSynced)
                healthArray.put(obj)
            }
            root.put("health_records", healthArray)

            // 18. Keep Notes
            val keepNotes = if (options.exportNotes) database.keepNoteDao().getAllKeepNotesDirect() else emptyList()
            val keepArray = JSONArray()
            keepNotes.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("title", it.title)
                obj.put("content", it.content)
                obj.put("timestamp", it.timestamp)
                obj.put("isPinned", it.isPinned)
                obj.put("colorHex", it.colorHex)
                obj.put("isSynced", it.isSynced)
                obj.put("websiteUrl", it.websiteUrl ?: "")
                obj.put("customLogoUrl", it.customLogoUrl ?: "")
                keepArray.put(obj)
            }
            root.put("keep_notes", keepArray)

            // 19. Focus Session Records
            val focusRecords = if (options.exportFocus) database.focusRecordDao().getAllRecords().first() else emptyList()
            val focusArray = JSONArray()
            focusRecords.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("taskTitle", it.taskTitle)
                obj.put("tag", it.tag)
                obj.put("notes", it.notes)
                obj.put("durationSeconds", it.durationSeconds)
                obj.put("durationMinutes", it.durationMinutes)
                obj.put("dateString", it.dateString)
                obj.put("startTime", it.startTime)
                obj.put("endTime", it.endTime)
                obj.put("timestamp", it.timestamp)
                focusArray.put(obj)
            }
            root.put("focus_records", focusArray)

            // 20. Local History Vault (Focus details and history)
            val history = if (options.exportFocus) database.localHistoryVaultDao().getAllHistoryDirect() else emptyList()
            val historyArray = JSONArray()
            val timelineConverters = TimelineConverters()
            history.forEach {
                val obj = JSONObject()
                obj.put("record_id", it.record_id)
                obj.put("date_string", it.date_string)
                obj.put("subject", it.subject)
                obj.put("task_title", it.task_title ?: "")
                obj.put("start_time_ms", it.start_time_ms)
                obj.put("end_time_ms", it.end_time_ms)
                obj.put("total_focus_ms", it.total_focus_ms)
                obj.put("total_break_ms", it.total_break_ms)
                obj.put("pause_count", it.pause_count)
                obj.put("duration_formatted", it.duration_formatted)
                obj.put("start_time_formatted", it.start_time_formatted)
                obj.put("end_time_formatted", it.end_time_formatted)
                obj.put("is_synced_to_firestore", it.is_synced_to_firestore)
                obj.put("mode", it.mode)
                obj.put("last_modified_ms", it.lastModifiedMs)
                obj.put("is_manual_entry", it.isManualEntry)
                obj.put("timeline_json", it.timeline_json ?: "")
                obj.put("timeline_serialized", timelineConverters.fromTimelineEventList(it.timeline))
                historyArray.put(obj)
            }
            root.put("local_history_vault", historyArray)

            // 21. Local Shields Vault (Streak shields)
            val shields = if (options.exportFocus) database.localShieldsVaultDao().getAllShields().first() else emptyList()
            val shieldsArray = JSONArray()
            shields.forEach {
                val obj = JSONObject()
                obj.put("uuid", it.uuid)
                obj.put("donor_email", it.donor_email)
                obj.put("donor_name", it.donor_name)
                obj.put("granted_timestamp", it.granted_timestamp)
                obj.put("is_consumed", it.is_consumed)
                obj.put("consumed_date", it.consumed_date ?: "")
                shieldsArray.put(obj)
            }
            root.put("local_shields_vault", shieldsArray)

            // 22. Syllabus Completion Vault
            val syllabus = if (options.exportTasks) database.syllabusCompletionDao().getAllCompletionSync() else emptyList()
            val syllabusArray = JSONArray()
            syllabus.forEach {
                val obj = JSONObject()
                obj.put("topicId", it.topicId)
                obj.put("isCompleted", it.isCompleted)
                obj.put("lastModifiedMs", it.lastModifiedMs)
                syllabusArray.put(obj)
            }
            root.put("syllabus_completion_vault", syllabusArray)

            val driveLinksJson = JSONObject()
            if (!isAutoBackup) {
                try {
                    val accessToken = GoogleDriveSyncManager.getAccessToken(context)
                    if (accessToken != null) {
                        val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
                        val filesList = filesDir.listFiles() ?: emptyArray()
                        filesList.forEach { file ->
                            if (file.isFile && file.name != "backup_summary.txt" && file.name != "backup_data.json" && !file.name.endsWith(".zip")) {
                                val nameLower = file.name.lowercase()
                                val isGooglePhotosSyncable = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || 
                                                           nameLower.endsWith(".png") || nameLower.endsWith(".webp") || 
                                                           nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || 
                                                           nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || 
                                                           nameLower.endsWith(".avi")
                                if (!isGooglePhotosSyncable) {
                                    val sharingUrl = GoogleDriveSyncManager.uploadPublicMediaFileDirect(context, accessToken, file)
                                    if (sharingUrl != null) {
                                        driveLinksJson.put(file.name, sharingUrl)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating drive links during export", e)
                }
            }
            root.put("drive_media_links", driveLinksJson)

            val jsonString = root.toString()

            // Calculate stats for backup_summary.txt
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            val filesList = filesDir.listFiles() ?: emptyArray()

            var imageCount = 0
            var videoCount = 0
            var pdfCount = 0
            var wordCount = 0
            var excelCount = 0
            var otherCount = 0

            filesList.forEach { file ->
                if (file.isFile) {
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")) {
                        imageCount++
                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || nameLower.endsWith(".avi")) {
                        videoCount++
                    } else if (nameLower.endsWith(".pdf")) {
                        pdfCount++
                    } else if (nameLower.endsWith(".doc") || nameLower.endsWith(".docx")) {
                        wordCount++
                    } else if (nameLower.endsWith(".xls") || nameLower.endsWith(".xlsx")) {
                        excelCount++
                    } else {
                        if (file.name != "backup_summary.txt" && file.name != "backup_data.json" && !file.name.endsWith(".zip")) {
                            otherCount++
                        }
                    }
                }
            }

            val journalsCount = journal.size
            val tasksCount = tasks.size
            val habitsCount = habits.size
            val ledgerCount = ledger.size
            val healthRecordsCount = healthRecords.size
            val keepNotesCount = keepNotes.size

            val serializedFocus = prefs.getString("focus_records_list", null) ?: ""
            val focusRecordsLines = if (serializedFocus.isBlank()) emptyList() else serializedFocus.split("\n")
            val focusRecordsCount = focusRecordsLines.filter { it.isNotBlank() }.size
            var totalFocusMins = 0
            focusRecordsLines.forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val mins = parts[3].toIntOrNull() ?: 0
                        totalFocusMins += mins
                    }
                }
            }

            val summaryText = """
                --- BACKUP SUMMARY MANIFEST ---
                Images Count: $imageCount (excluded from Drive zip, synced via Google Photos)
                Videos Count: $videoCount (excluded from Drive zip, synced via Google Photos)
                PDF Documents Count: $pdfCount
                Word Documents Count: $wordCount
                Excel Spreadsheets Count: $excelCount
                Other Files Count: $otherCount
                Journals Count: $journalsCount
                Tasks Count: $tasksCount
                Habits Count: $habitsCount
                History (Ledger) Entries Count: $ledgerCount
                Health & Fitness Records Count: $healthRecordsCount
                Keep Notes Count: $keepNotesCount
                Focused Session Record Count: $focusRecordsCount
                Total Focused Session Duration (minutes): $totalFocusMins
                -------------------------------
            """.trimIndent()
            
            // Export inside a UNIFIED zip archive
            java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                // 1. Write the backup JSON database dump
                val jsonEntry = java.util.zip.ZipEntry("backup_data.json")
                zipOut.putNextEntry(jsonEntry)
                val jsonWriter = java.io.BufferedWriter(java.io.OutputStreamWriter(zipOut, Charsets.UTF_8))
                jsonWriter.write(jsonString)
                jsonWriter.flush()
                zipOut.closeEntry()

                // 1b. Write the backup summary manifest
                val summaryEntry = java.util.zip.ZipEntry("backup_summary.txt")
                zipOut.putNextEntry(summaryEntry)
                val summaryWriter = java.io.BufferedWriter(java.io.OutputStreamWriter(zipOut, Charsets.UTF_8))
                summaryWriter.write(summaryText)
                summaryWriter.flush()
                zipOut.closeEntry()

                // 2. Write all physical files (journal photos, recordings, local files)
                if (options.exportFiles || options.exportJournal) {
                    filesList.forEach { file ->
                        if (file.isFile) {
                            val nameLower = file.name.lowercase()
                            val isGooglePhotosSyncable = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || 
                                                       nameLower.endsWith(".png") || nameLower.endsWith(".webp") || 
                                                       nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || 
                                                       nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || 
                                                       nameLower.endsWith(".avi")
                            if (!isGooglePhotosSyncable) {
                                val entryName = "media/${file.name}"
                                val fileEntry = java.util.zip.ZipEntry(entryName)
                                zipOut.putNextEntry(fileEntry)
                                file.inputStream().use { input ->
                                    FileChunkHelper.copyStreamSecure(input, zipOut, bufferSize = 8192)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data", e)
            false
        }
    }

    suspend fun importData(context: Context, database: AppDatabase, uri: Uri): Boolean {
        return try {
            val success = context.contentResolver.openInputStream(uri)?.use { rawIn ->
                importDataFromStream(context, database, rawIn)
            } ?: false
            if (success) {
                deleteBackupFiles(context)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from Uri", e)
            false
        }
    }

    suspend fun importDataFromStream(context: Context, database: AppDatabase, rawIn: java.io.InputStream): Boolean {
        return try {
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            var backupSummaryString: String? = null
            var tempJsonFile: java.io.File? = null

            val bufferedIn = java.io.BufferedInputStream(rawIn)
            bufferedIn.mark(4)
            val header = ByteArray(4)
            val read = bufferedIn.read(header)
            if (read == -1) return false
            bufferedIn.reset()

            val isZip = read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()

            if (!isZip) {
                val contentStr = bufferedIn.bufferedReader(Charsets.UTF_8).readText()
                return parseAndRestoreDb(context, database, contentStr)
            } else {
                val zipIn = java.util.zip.ZipInputStream(bufferedIn)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "backup_summary.txt") {
                        val bos = java.io.ByteArrayOutputStream()
                        FileChunkHelper.copyStreamSecure(zipIn, bos, bufferSize = 8192)
                        backupSummaryString = bos.toString("UTF-8")
                    } else if (entry.name == "backup_data.json") {
                        val tempFile = java.io.File(context.cacheDir, "temp_backup_data.json")
                        tempFile.outputStream().use { output ->
                            FileChunkHelper.copyStreamSecure(zipIn, output, bufferSize = 8192)
                        }
                        tempJsonFile = tempFile
                    } else if (entry.name.startsWith("media/")) {
                        val fileName = entry.name.substringAfter("media/")
                        if (fileName.isNotEmpty()) {
                            val destFile = java.io.File(filesDir, fileName)
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { output ->
                                FileChunkHelper.copyStreamSecure(zipIn, output, bufferSize = 8192)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                if (tempJsonFile != null && tempJsonFile.exists()) {
                    val jsonContent = tempJsonFile.readText(Charsets.UTF_8)
                    val restoreResult = parseAndRestoreDb(context, database, jsonContent)
                    tempJsonFile.delete() // Clean up disk cache immediately
                    
                    if (restoreResult) {
                        if (backupSummaryString != null) {
                            verifyImportCounts(context, database, backupSummaryString)
                        }
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from Stream", e)
            false
        }
    }

    private suspend fun parseAndRestoreDb(context: Context, database: AppDatabase, contentStr: String): Boolean {
        return try {
            val rootObj = JSONObject(contentStr)
            val originalFilesDir = rootObj.optString("files_dir_path_placeholder", "")
            val currentFilesDir = com.example.util.StorageHelper.getAppFilesDir(context).absolutePath

            // Perform automatic filesystem translation to match new installation details seamlessly
            var finalContentStr = contentStr
            if (originalFilesDir.isNotEmpty() && originalFilesDir != currentFilesDir) {
                finalContentStr = finalContentStr.replace(originalFilesDir, currentFilesDir)
            }

            val root = JSONObject(finalContentStr)

            val options = getBackupOptions(context)

            // Transact-clear existing databases & tables to prevent ID collisions, clones or leaks
            database.runInTransaction {
                if (options.exportTasks) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM tasks")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM custom_lists")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM deadlines")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM syllabus_completion_vault")
                }
                if (options.exportHabits) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM habit_completions")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM habits")
                }
                if (options.exportJournal) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM journal_entries")
                }
                if (options.exportFinances) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM ledger_entries")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM financial_goals")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM financial_logs")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM finance_transactions")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM financial_accounts")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM family_members")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM finance_categories")
                }
                if (options.exportContacts) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM contacts")
                }
                if (options.exportFiles) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM app_files")
                }
                if (options.exportHealth) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM health_records")
                }
                if (options.exportNotes) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM keep_notes")
                }
                if (options.exportFocus) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM focus_records")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM local_history_vault")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM local_shields_vault")
                }
            }

            // Restore SharedPreferences if present
            if (options.exportSettings) {
                val settingsJson = root.optJSONObject("shared_preferences")
                if (settingsJson != null) {
                    val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    val oldPrefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).all
                    oldPrefs.keys.forEach { key ->
                        if (!key.startsWith("backup_option_")) {
                            prefs.remove(key)
                        }
                    }
                    val keys = settingsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.startsWith("backup_option_")) continue
                        val value = settingsJson.get(key)
                        when (value) {
                            is Boolean -> prefs.putBoolean(key, value)
                            is Int -> prefs.putInt(key, value)
                            is Long -> prefs.putLong(key, value)
                            is Double -> prefs.putFloat(key, value.toFloat())
                            is String -> prefs.putString(key, value)
                        }
                    }
                    prefs.apply()
                }
            }

             // 1. Tasks
            val tasksArray = root.optJSONArray("tasks")
            val taskIdMapping = mutableMapOf<Int, Int>()
            val tasksWithOldParents = mutableListOf<Pair<Int, Int>>() // list of Pair(newTaskId, oldParentId)
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    val oldId = obj.optInt("id", -1)
                    val parentIdVal = obj.optInt("parentTaskId", -1)
                    
                    val blockTimeVal = obj.optLong("timeBlockTimestamp", -1L)
                    val timeBlockTimestamp: Long? = if (blockTimeVal == -1L) null else blockTimeVal

                    val task = Task(
                        title = obj.optString("title", "Untitled Task"),
                        description = obj.optString("description", ""),
                        estimatedMinutes = obj.optInt("estimatedMinutes", 30),
                        actualMinutes = obj.optInt("actualMinutes", 0),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        parentTaskId = null, // Set null initially, we will resolve parentTaskId in pass 2
                        listCategory = obj.optString("listCategory", "Inbox"),
                        timeBlockTimestamp = timeBlockTimestamp,
                        nagModeEnabled = obj.optBoolean("nagModeEnabled", false),
                        nagIntervalMinutes = obj.optInt("nagIntervalMinutes", 5),
                        priority = obj.optString("priority", "MEDIUM"),
                        dueDateString = obj.optString("dueDateString", ""),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                    val newId = database.taskDao().insertTask(task).toInt()
                    if (oldId != -1) {
                        taskIdMapping[oldId] = newId
                    }
                    if (parentIdVal != -1) {
                        tasksWithOldParents.add(Pair(newId, parentIdVal))
                    }
                }
                
                // Pass 2: Update parentTaskId with resolved mapped IDs
                for ((newId, oldParentId) in tasksWithOldParents) {
                    val newParentId = taskIdMapping[oldParentId]
                    if (newParentId != null) {
                        val t = database.taskDao().getTaskById(newId)
                        if (t != null) {
                            database.taskDao().updateTask(t.copy(parentTaskId = newParentId))
                        }
                    }
                }
            }

            // 2. Habits
            val habitsArray = root.optJSONArray("habits")
            val idMapping = mutableMapOf<Int, Int>() // maps old habit id to newly generated habit id
            if (habitsArray != null) {
                for (i in 0 until habitsArray.length()) {
                    val obj = habitsArray.getJSONObject(i)
                    val oldId = obj.optInt("id", -1)
                    val lastCompletedVal = obj.optLong("lastCompletedTimestamp", -1L)
                    val lastCompletedTimestamp: Long? = if (lastCompletedVal == -1L) null else lastCompletedVal

                    val habit = Habit(
                        name = obj.optString("name", "Untitled Habit"),
                        streakCount = obj.optInt("streakCount", 0),
                        lastCompletedTimestamp = lastCompletedTimestamp,
                        listCategory = obj.optString("listCategory", "Health & Vigor"),
                        timeOfDay = obj.optString("timeOfDay", "Morning"),
                        targetCount = obj.optInt("targetCount", 1),
                        frequency = obj.optString("frequency", "DAILY"),
                        weeklyDay = obj.optInt("weeklyDay", 2),
                        monthlyStartDate = obj.optInt("monthlyStartDate", 1),
                        monthlyEndDate = obj.optInt("monthlyEndDate", 30),
                        orderIndex = obj.optInt("orderIndex", 0),
                        scheduledTime = obj.optString("scheduledTime", "08:00"),
                        isReminderEnabled = obj.optBoolean("isReminderEnabled", false)
                    )
                    val newId = database.habitDao().insertHabit(habit).toInt()
                    if (oldId != -1) {
                        idMapping[oldId] = newId
                    }
                }
            }

            // 3. Habit Completions
            val completionsArray = root.optJSONArray("habit_completions")
            if (completionsArray != null) {
                for (i in 0 until completionsArray.length()) {
                    val obj = completionsArray.getJSONObject(i)
                    val oldHabitId = obj.optInt("habitId", -1)
                    val newHabitId = idMapping[oldHabitId] ?: oldHabitId
                    if (newHabitId != -1) {
                        val completion = HabitCompletion(
                            habitId = newHabitId,
                            dateString = obj.optString("dateString", "")
                        )
                        database.habitDao().insertCompletion(completion)
                    }
                }
            }

            // 4. Journal Entries
            val journalArray = root.optJSONArray("journal_entries")
            if (journalArray != null) {
                for (i in 0 until journalArray.length()) {
                    val obj = journalArray.getJSONObject(i)
                    val entry = JournalEntry(
                        title = obj.optString("title", "Untitled Entry"),
                        text = obj.optString("text", ""),
                        dateString = obj.optString("dateString", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        attachmentsJson = obj.optString("attachmentsJson", "")
                    )
                    database.journalDao().insertJournalEntry(entry)
                }
            }

            // 5. Ledger Entries
            val ledgerArray = root.optJSONArray("ledger_entries")
            if (ledgerArray != null) {
                for (i in 0 until ledgerArray.length()) {
                    val obj = ledgerArray.getJSONObject(i)
                    val entry = LedgerEntry(
                        type = obj.optString("type", "EXPENSE"),
                        amount = obj.optDouble("amount", 0.0),
                        categoryTag = obj.optString("categoryTag", "General"),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    database.ledgerDao().insertLedgerEntry(entry)
                }
            }

            // 6. Deadlines
            val deadlinesArray = root.optJSONArray("deadlines")
            if (deadlinesArray != null) {
                for (i in 0 until deadlinesArray.length()) {
                    val obj = deadlinesArray.getJSONObject(i)
                    val deadline = Deadline(
                        name = obj.optString("name", "Untitled Deadline"),
                        targetTimestamp = obj.optLong("targetTimestamp", System.currentTimeMillis()),
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                    database.deadlineDao().insertDeadline(deadline)
                }
            }

            // 7. Financial Goals
            val fgArray = root.optJSONArray("financial_goals")
            if (fgArray != null) {
                for (i in 0 until fgArray.length()) {
                    val obj = fgArray.getJSONObject(i)
                    val goal = FinancialGoal(
                        name = obj.optString("name", "Untitled Goal"),
                        targetAmount = obj.optDouble("targetAmount", 0.0),
                        type = obj.optString("type", "SAVINGS"),
                        categoryTag = obj.optString("categoryTag", "General")
                    )
                    database.financialGoalDao().insertFinancialGoal(goal)
                }
            }

            // 8. Contacts
            val contactsArray = root.optJSONArray("contacts")
            if (contactsArray != null) {
                for (i in 0 until contactsArray.length()) {
                    val obj = contactsArray.getJSONObject(i)
                    val photoUriVal = obj.optString("photoUri", "")
                    val photoUri = photoUriVal.ifEmpty { null }
                    
                    val contact = Contact(
                        firstName = obj.optString("firstName", ""),
                        middleName = obj.optString("middleName", ""),
                        lastName = obj.optString("lastName", ""),
                        jobTitle = obj.optString("jobTitle", ""),
                        email = obj.optString("email", ""),
                        address = obj.optString("address", ""),
                        phone = obj.optString("phone", ""),
                        dobString = obj.optString("dobString", ""),
                        photoUri = photoUri,
                        anniversaryString = obj.optString("anniversaryString", ""),
                        additionalFieldsJson = obj.optString("additionalFieldsJson", ""),
                        additionalDatesJson = obj.optString("additionalDatesJson", ""),
                        folder = obj.optString("folder", "All"),
                        attachedFilesJson = obj.optString("attachedFilesJson", "")
                    )
                    database.contactDao().insertContact(contact)
                }
            }

            // 9. App Files
            val filesArray = root.optJSONArray("app_files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val file = AppFile(
                        name = obj.optString("name", ""),
                        path = obj.optString("path", ""),
                        size = obj.optLong("size", 0L),
                        mimeType = obj.optString("mimeType", ""),
                        uriString = obj.optString("uriString", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                    database.appFileDao().insertFile(file)
                }
            }

            // 11. Custom Lists
            val listsArray = root.optJSONArray("custom_lists")
            if (listsArray != null) {
                for (i in 0 until listsArray.length()) {
                    val obj = listsArray.getJSONObject(i)
                    val parentVal = obj.optString("parentListName", "")
                    val parentListName = parentVal.ifEmpty { null }

                    val customList = CustomList(
                        name = obj.optString("name", "Inbox"),
                        colorHex = obj.optString("colorHex", "#2196F3"),
                        viewType = obj.optString("viewType", "List"),
                        parentListName = parentListName
                    )
                    database.customListDao().insertList(customList)
                }
            }

            // 12. Family Members
            val membersArray = root.optJSONArray("family_members")
            if (membersArray != null) {
                for (i in 0 until membersArray.length()) {
                    val obj = membersArray.getJSONObject(i)
                    val mem = FamilyMember(
                        id = obj.optInt("id"),
                        name = obj.optString("name", "Unknown")
                    )
                    database.familyMemberDao().insertMember(mem)
                }
            }

            // 13. Financial Accounts
            val accountsArray = root.optJSONArray("financial_accounts")
            if (accountsArray != null) {
                for (i in 0 until accountsArray.length()) {
                    val obj = accountsArray.getJSONObject(i)
                    val acc = FinancialAccount(
                        id = obj.optInt("id"),
                        memberId = obj.optInt("memberId"),
                        name = obj.optString("name", "Account"),
                        categoryType = obj.optString("categoryType", "CURRENT_ASSETS"),
                        openingValue = obj.optDouble("openingValue", 0.0)
                    )
                    database.financialAccountDao().insertAccount(acc)
                }
            }

            // 14. Financial Logs
            val logsArray = root.optJSONArray("financial_logs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    val logEntry = FinancialLog(
                        id = obj.optInt("id"),
                        accountId = obj.optInt("accountId"),
                        logType = obj.optString("logType", "INITIAL"),
                        amount = obj.optDouble("amount", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    database.financialLogDao().insertLog(logEntry)
                }
            }

            // 15. Finance Transactions
            val transactionsArray = root.optJSONArray("finance_transactions")
            if (transactionsArray != null) {
                for (i in 0 until transactionsArray.length()) {
                    val obj = transactionsArray.getJSONObject(i)
                    val fromAccIdVal = obj.optInt("fromAccountId", -1)
                    val fromAccountId: Int? = if (fromAccIdVal == -1) null else fromAccIdVal
                    val fromCategoryVal = obj.optString("fromCategory", "")
                    val fromCategory: String? = if (fromCategoryVal.isEmpty()) null else fromCategoryVal
                    val toAccIdVal = obj.optInt("toAccountId", -1)
                    val toAccountId: Int? = if (toAccIdVal == -1) null else toAccIdVal
                    val toCategoryVal = obj.optString("toCategory", "")
                    val toCategory: String? = if (toCategoryVal.isEmpty()) null else toCategoryVal

                    val tx = FinanceTransaction(
                        id = obj.optInt("id"),
                        memberId = obj.optInt("memberId"),
                        type = obj.optString("type", "EXPENSE"),
                        fromAccountId = fromAccountId,
                        fromCategory = fromCategory,
                        toAccountId = toAccountId,
                        toCategory = toCategory,
                        amount = obj.optDouble("amount", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                    database.financeTransactionDao().insertTransaction(tx)
                }
            }

            // 16. Finance Categories
            val categoriesArray = root.optJSONArray("finance_categories")
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val obj = categoriesArray.getJSONObject(i)
                    val cat = FinanceCategory(
                        id = obj.optInt("id"),
                        name = obj.optString("name", ""),
                        type = obj.optString("type", "EXPENSE")
                    )
                    database.financeCategoryDao().insertCategory(cat)
                }
            }

            // 17. Restore public Drive media links if they are not present locally
            val driveLinks = root.optJSONObject("drive_media_links")
            if (driveLinks != null) {
                val keys = driveLinks.keys()
                val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
                while (keys.hasNext()) {
                    val fileName = keys.next()
                    val sharingUrl = driveLinks.optString(fileName, "")
                    if (sharingUrl.isNotEmpty()) {
                        val localFile = java.io.File(filesDir, fileName)
                        if (!localFile.exists()) {
                            try {
                                downloadPublicFileDirect(sharingUrl, localFile)
                                Log.d(TAG, "Successfully restored missing local file $fileName from Drive link")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to download missing file $fileName from Drive link: $sharingUrl", e)
                            }
                        }
                    }
                }
            }

            // 18. Health Records
            if (options.exportHealth) {
                val healthArray = root.optJSONArray("health_records")
                if (healthArray != null) {
                    for (i in 0 until healthArray.length()) {
                        val obj = healthArray.getJSONObject(i)
                        val hr = HealthRecord(
                            dateString = obj.optString("dateString", ""),
                            steps = obj.optInt("steps", 0),
                            stepGoal = obj.optInt("stepGoal", 10000),
                            sleepMinutes = obj.optInt("sleepMinutes", 0),
                            sleepGoalMinutes = obj.optInt("sleepGoalMinutes", 480),
                            waterMl = obj.optInt("waterMl", 0),
                            waterGoalMl = obj.optInt("waterGoalMl", 2000),
                            caloriesBurned = obj.optInt("caloriesBurned", 0),
                            calorieGoal = obj.optInt("calorieGoal", 2000),
                            activeMinutes = obj.optInt("activeMinutes", 0),
                            activeMinutesGoal = obj.optInt("activeMinutesGoal", 45),
                            heartRateAvg = obj.optInt("heartRateAvg", 72),
                            heartRateMin = obj.optInt("heartRateMin", 60),
                            heartRateMax = obj.optInt("heartRateMax", 120),
                            breakfastFoods = obj.optString("breakfastFoods", ""),
                            lunchFoods = obj.optString("lunchFoods", ""),
                            dinnerFoods = obj.optString("dinnerFoods", ""),
                            snacksFoods = obj.optString("snacksFoods", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isSynced = obj.optBoolean("isSynced", false)
                        )
                        database.healthRecordDao().insertOrUpdate(hr)
                    }
                }
            }

            // 19. Keep Notes
            if (options.exportNotes) {
                val keepArray = root.optJSONArray("keep_notes")
                if (keepArray != null) {
                    for (i in 0 until keepArray.length()) {
                        val obj = keepArray.getJSONObject(i)
                        val kn = KeepNote(
                            id = obj.optInt("id", 0),
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isPinned = obj.optBoolean("isPinned", false),
                            colorHex = obj.optString("colorHex", "#202124"),
                            isSynced = obj.optBoolean("isSynced", false),
                            websiteUrl = obj.optString("websiteUrl", "").let { if (it.isEmpty()) null else it },
                            customLogoUrl = obj.optString("customLogoUrl", "").let { if (it.isEmpty()) null else it }
                        )
                        database.keepNoteDao().insertKeepNote(kn)
                    }
                }
            }

            // 20. Focus Session Records
            if (options.exportFocus) {
                val focusArray = root.optJSONArray("focus_records")
                if (focusArray != null) {
                    for (i in 0 until focusArray.length()) {
                        val obj = focusArray.getJSONObject(i)
                        val fr = FocusRecordEntity(
                            id = obj.optInt("id", 0),
                            taskTitle = obj.optString("taskTitle", ""),
                            tag = obj.optString("tag", ""),
                            notes = obj.optString("notes", ""),
                            durationSeconds = obj.optInt("durationSeconds", 0),
                            durationMinutes = obj.optInt("durationMinutes", 0),
                            dateString = obj.optString("dateString", ""),
                            startTime = obj.optString("startTime", ""),
                            endTime = obj.optString("endTime", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                        database.focusRecordDao().insertRecord(fr)
                    }
                }

                // 21. Local History Vault (Focus details and history)
                val historyArray = root.optJSONArray("local_history_vault")
                if (historyArray != null) {
                    val timelineConverters = TimelineConverters()
                    for (i in 0 until historyArray.length()) {
                        val obj = historyArray.getJSONObject(i)
                        val timelineSerialized = obj.optString("timeline_serialized", "[]")
                        val timelineList = timelineConverters.toTimelineEventList(timelineSerialized)
                        val lh = LocalHistoryVault(
                            record_id = obj.optString("record_id", java.util.UUID.randomUUID().toString()),
                            date_string = obj.optString("date_string", ""),
                            subject = obj.optString("subject", ""),
                            task_title = obj.optString("task_title", "").let { if (it.isEmpty()) null else it },
                            start_time_ms = obj.optLong("start_time_ms", 0L),
                            end_time_ms = obj.optLong("end_time_ms", 0L),
                            total_focus_ms = obj.optLong("total_focus_ms", 0L),
                            total_break_ms = obj.optLong("total_break_ms", 0L),
                            pause_count = obj.optInt("pause_count", 0),
                            duration_formatted = obj.optString("duration_formatted", "00:00:00"),
                            start_time_formatted = obj.optString("start_time_formatted", ""),
                            end_time_formatted = obj.optString("end_time_formatted", ""),
                            is_synced_to_firestore = obj.optInt("is_synced_to_firestore", 0),
                            mode = obj.optString("mode", "POMODORO"),
                            lastModifiedMs = obj.optLong("last_modified_ms", System.currentTimeMillis()),
                            isManualEntry = obj.optBoolean("is_manual_entry", false),
                            timeline_json = obj.optString("timeline_json", "").let { if (it.isEmpty()) null else it },
                            timeline = timelineList
                        )
                        database.localHistoryVaultDao().insertRecord(lh)
                    }
                }

                // 22. Local Shields Vault (Streak shields)
                val shieldsArray = root.optJSONArray("local_shields_vault")
                if (shieldsArray != null) {
                    for (i in 0 until shieldsArray.length()) {
                        val obj = shieldsArray.getJSONObject(i)
                        val consumedDateVal = obj.optString("consumed_date", "")
                        val consumedDate = if (consumedDateVal.isEmpty()) null else consumedDateVal
                        val ls = LocalShieldsVault(
                            uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
                            donor_email = obj.optString("donor_email", ""),
                            donor_name = obj.optString("donor_name", ""),
                            granted_timestamp = obj.optLong("granted_timestamp", 0L),
                            is_consumed = obj.optBoolean("is_consumed", false),
                            consumed_date = consumedDate
                        )
                        database.localShieldsVaultDao().insertShield(ls)
                    }
                }
            }

            // 23. Syllabus Completion Vault
            if (options.exportTasks) {
                val syllabusArray = root.optJSONArray("syllabus_completion_vault")
                if (syllabusArray != null) {
                    for (i in 0 until syllabusArray.length()) {
                        val obj = syllabusArray.getJSONObject(i)
                        val sc = SyllabusCompletionVault(
                            topicId = obj.optString("topicId", ""),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            lastModifiedMs = obj.optLong("lastModifiedMs", System.currentTimeMillis())
                        )
                        database.syllabusCompletionDao().insertCompletion(sc)
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed parseAndRestoreDb", e)
            false
        }
    }

    private suspend fun verifyImportCounts(context: Context, database: AppDatabase, summaryStr: String): Boolean {
        return try {
            Log.d(TAG, "Verifying imported database counts against backup_summary.txt...")
            
            var expectedImages = -1
            var expectedVideos = -1
            var expectedOtherFiles = -1
            var expectedJournals = -1
            var expectedTasks = -1
            var expectedHabits = -1
            var expectedLedgerCount = -1
            var expectedFocusRecords = -1
            var expectedFocusMinutes = -1

            summaryStr.split("\n").forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Images Count:") -> expectedImages = trimmed.substringAfter("Images Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Videos Count:") -> expectedVideos = trimmed.substringAfter("Videos Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Other Files Count:") -> expectedOtherFiles = trimmed.substringAfter("Other Files Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Journals Count:") -> expectedJournals = trimmed.substringAfter("Journals Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Tasks Count:") -> expectedTasks = trimmed.substringAfter("Tasks Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Habits Count:") -> expectedHabits = trimmed.substringAfter("Habits Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("History (Ledger) Entries Count:") -> expectedLedgerCount = trimmed.substringAfter("History (Ledger) Entries Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Focused Session Record Count:") -> expectedFocusRecords = trimmed.substringAfter("Focused Session Record Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Total Focused Session Duration (minutes):") -> expectedFocusMinutes = trimmed.substringAfter("Total Focused Session Duration (minutes):").trim().toIntOrNull() ?: -1
                }
            }

            // Count actuals in filesystem
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            val filesList = filesDir.listFiles() ?: emptyArray()
            
            var actualImages = 0
            var actualVideos = 0
            var actualOtherFiles = 0
            
            filesList.forEach { file ->
                if (file.isFile) {
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")) {
                        actualImages++
                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || nameLower.endsWith(".avi")) {
                        actualVideos++
                    } else {
                        if (file.name != "backup_summary.txt" && file.name != "backup_data.json") {
                            actualOtherFiles++
                        }
                    }
                }
            }

            // Count actuals in database
            val actualJournals = database.journalDao().getAllJournalEntries().first().size
            val actualTasks = database.taskDao().getAllTasks().first().size
            val actualHabits = database.habitDao().getAllHabits().first().size
            val actualLedger = database.ledgerDao().getAllLedgerEntries().first().size

            // Count focus from new shared preferences
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val serializedFocus = prefs.getString("focus_records_list", null) ?: ""
            val focusRecordsLines = if (serializedFocus.isBlank()) emptyList() else serializedFocus.split("\n")
            val actualFocusRecords = focusRecordsLines.filter { it.isNotBlank() }.size
            var actualFocusMins = 0
            focusRecordsLines.forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val mins = parts[3].toIntOrNull() ?: 0
                        actualFocusMins += mins
                    }
                }
            }

            Log.d(TAG, "VERIFICATION COMPARISON RESULTS:")
            Log.d(TAG, "Images: Expected: $expectedImages, Actual: $actualImages")
            Log.d(TAG, "Videos: Expected: $expectedVideos, Actual: $actualVideos")
            Log.d(TAG, "Other Files: Expected: $expectedOtherFiles, Actual: $actualOtherFiles")
            Log.d(TAG, "Journals: Expected: $expectedJournals, Actual: $actualJournals")
            Log.d(TAG, "Tasks: Expected: $expectedTasks, Actual: $actualTasks")
            Log.d(TAG, "Habits: Expected: $expectedHabits, Actual: $actualHabits")
            Log.d(TAG, "Ledger Entries: Expected: $expectedLedgerCount, Actual: $actualLedger")
            Log.d(TAG, "Focus Records: Expected: $expectedFocusRecords, Actual: $actualFocusRecords")
            Log.d(TAG, "Focus Minutes: Expected: $expectedFocusMinutes, Actual: $actualFocusMins")

            var match = true
            if (expectedImages != -1 && expectedImages != actualImages) match = false
            if (expectedVideos != -1 && expectedVideos != actualVideos) match = false
            if (expectedOtherFiles != -1 && expectedOtherFiles != actualOtherFiles) match = false
            if (expectedJournals != -1 && expectedJournals != actualJournals) match = false
            if (expectedTasks != -1 && expectedTasks != actualTasks) match = false
            if (expectedHabits != -1 && expectedHabits != actualHabits) match = false
            if (expectedLedgerCount != -1 && expectedLedgerCount != actualLedger) match = false
            if (expectedFocusRecords != -1 && expectedFocusRecords != actualFocusRecords) match = false
            if (expectedFocusMinutes != -1 && expectedFocusMinutes != actualFocusMins) match = false

            if (!match) {
                Log.w(TAG, "Import completed but counts did not match perfectly!")
            } else {
                Log.d(TAG, "Import counts matched database statistics perfectly!")
            }
            match
        } catch (e: Exception) {
            Log.e(TAG, "Error in verifyImportCounts", e)
            false
        }
    }

    fun getBackupLocations(context: Context): List<java.io.File> {
        val locations = mutableListOf<java.io.File>()
        
        // 1. Standard public Downloads & Documents folders on primary shared storage
        val primaryDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val primaryDocument = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        
        if (primaryDownload != null) {
            locations.add(java.io.File(primaryDownload, "LifeOS_Backup"))
        }
        if (primaryDocument != null) {
            locations.add(java.io.File(primaryDocument, "LifeOS_Backup"))
        }
        
        // Add generic path fallbacks
        locations.add(java.io.File("/sdcard/Download/LifeOS_Backup"))
        locations.add(java.io.File("/sdcard/Documents/LifeOS_Backup"))
        locations.add(java.io.File("/storage/emulated/0/Download/LifeOS_Backup"))
        locations.add(java.io.File("/storage/emulated/0/Documents/LifeOS_Backup"))
        
        // 2. Secondary external storages (connected SD cards, USB OTG, etc.) from system /storage/
        try {
            val storageDir = java.io.File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val volumes = storageDir.listFiles()
                if (volumes != null) {
                    for (volume in volumes) {
                        try {
                            if (volume.isDirectory && volume.canRead()) {
                                val name = volume.name
                                if (name != "self" && name != "emulated") {
                                    // This is likely an external SD card or USB drive!
                                    locations.add(java.io.File(volume, "Download/LifeOS_Backup"))
                                    locations.add(java.io.File(volume, "Documents/LifeOS_Backup"))
                                    locations.add(java.io.File(volume, "LifeOS_Backup"))
                                }
                            }
                        } catch (ex: Exception) {
                            // Suppress per-volume permission errors safely
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning external storage volumes", e)
        }

        // Context external files dirs
        try {
            context.getExternalFilesDirs(null).forEach { dir ->
                if (dir != null) {
                    locations.add(java.io.File(dir, "LifeOS_Backup"))
                }
            }
        } catch (ex: Exception) {
            // Ignored
        }

        // Return unique existing/creatable directories
        return locations.distinct()
    }

    fun deleteBackupFiles(context: Context) {
        try {
            val locations = getBackupLocations(context)
            for (dir in locations) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && (file.name == "lifeos_backup.zip" || file.name.endsWith(".zip") || file.name.contains("backup"))) {
                            val deleted = file.delete()
                            Log.d(TAG, "Deleted backup file after successful restore: ${file.absolutePath}, success = $deleted")
                        }
                    }
                    val backupFile = java.io.File(dir, "lifeos_backup.zip")
                    if (backupFile.exists() && backupFile.isFile) {
                        val deleted = backupFile.delete()
                        Log.d(TAG, "Deleted backup file direct: ${backupFile.absolutePath}, success = $deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup files after successful import", e)
        }
    }

    suspend fun autoBackup(context: Context, database: AppDatabase): Boolean {
        var success = false
        try {
            val tempFile = java.io.File(context.cacheDir, "lifeos_auto_backup_temp.zip")
            if (tempFile.exists()) tempFile.delete()
            
            val tempUri = Uri.fromFile(tempFile)
            // Perform backup once locally, bypassing Google Drive uploads for auto-backup
            val exported = exportData(context, database, tempUri, isAutoBackup = true)
            if (exported && tempFile.exists() && tempFile.length() > 0) {
                val locations = getBackupLocations(context)
                for (dir in locations) {
                    try {
                        if (!dir.exists()) {
                            dir.mkdirs()
                        }
                        if (dir.exists() && dir.canWrite()) {
                            val backupFile = java.io.File(dir, "lifeos_backup.zip")
                            tempFile.copyTo(backupFile, overwrite = true)
                            Log.d(TAG, "Auto-backup succeeded to: ${backupFile.absolutePath}")
                            success = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-backup failed to copy to directory: ${dir.absolutePath}", e)
                    }
                }
            }
            if (tempFile.exists()) tempFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform auto-backup", e)
        }
        return success
    }

    suspend fun autoRestoreIfNeeded(context: Context, database: AppDatabase): Boolean {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val previouslyInitialized = prefs.getBoolean("previously_initialized", false)
        if (previouslyInitialized) {
            // App was already initialized and run before. No auto-restore needed.
            return false
        }

        // Set preference immediately so we don't end up in an infinite auto-restore loop
        prefs.edit().putBoolean("previously_initialized", true).apply()

        // Now, scan all potential backup locations for lifeos_backup.zip
        val locations = getBackupLocations(context)
        for (dir in locations) {
            val backupFile = java.io.File(dir, "lifeos_backup.zip")
            if (backupFile.exists() && backupFile.isFile) {
                try {
                    Log.d(TAG, "Found previously exported backup at: ${backupFile.absolutePath}. Attempting auto-restore.")
                    val uri = Uri.fromFile(backupFile)
                    val imported = importData(context, database, uri)
                    if (imported) {
                        Log.d(TAG, "Auto-restore successful from: ${backupFile.absolutePath}")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-restore failed from path: ${backupFile.absolutePath}", e)
                }
            }
        }
        return false
    }

    private fun downloadPublicFileDirect(urlStr: String, destFile: java.io.File) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(urlStr)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download file: HTTP ${response.code}")
            }
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { fos ->
                response.body?.byteStream()?.copyTo(fos)
            }
        }
    }

    suspend fun exportHtmlZip(context: Context, database: AppDatabase, outputStream: java.io.OutputStream): Boolean {
        return try {
            val tasks = database.taskDao().getAllTasks().first()
            val journal = database.journalDao().getAllJournalEntries().first()
            val ledger = database.ledgerDao().getAllLedgerEntries().first()
            val habits = database.habitDao().getAllHabits().first()
            val contacts = database.contactDao().getAllContacts().first()
            val filesList = com.example.util.StorageHelper.getAppFilesDir(context).listFiles() ?: emptyArray()

            val htmlContent = generateHtmlDashboard(tasks, journal, ledger, habits, contacts)

            java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                // 1. Write index.html
                val htmlEntry = java.util.zip.ZipEntry("index.html")
                zipOut.putNextEntry(htmlEntry)
                val htmlWriter = java.io.BufferedWriter(java.io.OutputStreamWriter(zipOut, Charsets.UTF_8))
                htmlWriter.write(htmlContent)
                htmlWriter.flush()
                zipOut.closeEntry()

                // 2. Write all media files inside a "media/" folder
                filesList.forEach { file ->
                    if (file.isFile && file.name != "backup_summary.txt" && file.name != "backup_data.json" && !file.name.endsWith(".zip")) {
                        val entryName = "media/${file.name}"
                        val fileEntry = java.util.zip.ZipEntry(entryName)
                        zipOut.putNextEntry(fileEntry)
                        file.inputStream().use { input ->
                            FileChunkHelper.copyStreamSecure(input, zipOut, bufferSize = 8192)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HTML ZIP", e)
            false
        }
    }

    private fun generateHtmlDashboard(
        tasks: List<Task>,
        journals: List<JournalEntry>,
        ledger: List<LedgerEntry>,
        habits: List<Habit>,
        contacts: List<Contact>
    ): String {
        val tasksJson = JSONArray()
        tasks.forEach {
            tasksJson.put(JSONObject().apply {
                put("title", it.title)
                put("description", it.description)
                put("isCompleted", it.isCompleted)
                put("listCategory", it.listCategory)
                put("priority", it.priority)
                put("dueDateString", it.dueDateString)
            })
        }

        val journalsJson = JSONArray()
        journals.forEach {
            journalsJson.put(JSONObject().apply {
                put("title", it.title)
                put("text", it.text)
                put("dateString", it.dateString)
                put("timestamp", it.timestamp)
                put("attachmentsJson", it.attachmentsJson)
            })
        }

        val ledgerJson = JSONArray()
        ledger.forEach {
            ledgerJson.put(JSONObject().apply {
                put("type", it.type)
                put("amount", it.amount)
                put("categoryTag", it.categoryTag)
                put("note", it.note)
                put("timestamp", it.timestamp)
            })
        }

        val habitsJson = JSONArray()
        habits.forEach {
            habitsJson.put(JSONObject().apply {
                put("name", it.name)
                put("streakCount", it.streakCount)
                put("lastCompletedTimestamp", it.lastCompletedTimestamp ?: -1L)
            })
        }

        val contactsJson = JSONArray()
        contacts.forEach {
            contactsJson.put(JSONObject().apply {
                put("firstName", it.firstName)
                put("lastName", it.lastName)
                put("jobTitle", it.jobTitle)
                put("email", it.email)
                put("phone", it.phone)
                put("photoUri", it.photoUri ?: "")
                put("attachedFilesJson", it.attachedFilesJson)
            })
        }

        return """
<!DOCTYPE html>
<html lang="en" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LifeOS Offline Personal Archive</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body {
            background-color: #0d0d11;
            color: #f3f4f6;
            font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        }
        .tab-btn.active {
            border-bottom: 2px solid #3b82f6;
            color: #3b82f6;
        }
    </style>
</head>
<body class="min-h-screen flex flex-col">
    <header class="border-b border-neutral-800 bg-[#121217] py-4 px-6 flex items-center justify-between shadow-md">
        <div class="flex items-center space-x-3">
            <span class="text-2xl">✨</span>
            <div>
                <h1 class="text-xl font-bold tracking-tight text-white">LifeOS Personal Archive</h1>
                <p class="text-xs text-neutral-400">Complete offline database and file companion</p>
            </div>
        </div>
        <div class="text-xs text-neutral-500 bg-neutral-900 px-3 py-1.5 rounded-full border border-neutral-800">
            Exported: <span id="export-date"></span>
        </div>
    </header>

    <nav class="bg-[#121217] border-b border-neutral-800 px-6 flex space-x-6 text-sm overflow-x-auto">
        <button onclick="switchTab('dashboard')" id="tab-dashboard" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white active">Dashboard</button>
        <button onclick="switchTab('journals')" id="tab-journals" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white">Journals</button>
        <button onclick="switchTab('tasks')" id="tab-tasks" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white">Tasks</button>
        <button onclick="switchTab('contacts')" id="tab-contacts" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white">Contacts</button>
        <button onclick="switchTab('finances')" id="tab-finances" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white">Finances</button>
        <button onclick="switchTab('habits')" id="tab-habits" class="tab-btn py-4 font-medium transition text-neutral-400 hover:text-white">Habits</button>
    </nav>

    <main class="flex-1 p-6 max-w-7xl w-full mx-auto">
        <section id="sect-dashboard" class="tab-content space-y-6">
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div class="bg-[#15151b] p-5 rounded-xl border border-neutral-800 flex items-center justify-between">
                    <div>
                        <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Journals</p>
                        <h3 id="stat-journals" class="text-3xl font-extrabold text-white mt-1">0</h3>
                    </div>
                    <span class="text-3xl">📓</span>
                </div>
                <div class="bg-[#15151b] p-5 rounded-xl border border-neutral-800 flex items-center justify-between">
                    <div>
                        <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Active Tasks</p>
                        <h3 id="stat-tasks" class="text-3xl font-extrabold text-white mt-1">0</h3>
                    </div>
                    <span class="text-3xl">✅</span>
                </div>
                <div class="bg-[#15151b] p-5 rounded-xl border border-neutral-800 flex items-center justify-between">
                    <div>
                        <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Contacts</p>
                        <h3 id="stat-contacts" class="text-3xl font-extrabold text-white mt-1">0</h3>
                    </div>
                    <span class="text-3xl">👥</span>
                </div>
                <div class="bg-[#15151b] p-5 rounded-xl border border-neutral-800 flex items-center justify-between">
                    <div>
                        <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Habits Tracked</p>
                        <h3 id="stat-habits" class="text-3xl font-extrabold text-white mt-1">0</h3>
                    </div>
                    <span class="text-3xl">🔥</span>
                </div>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div class="bg-[#121217] p-6 rounded-xl border border-neutral-800 shadow-md">
                    <h2 class="text-lg font-bold text-white mb-4 flex items-center justify-between">
                        <span>Latest Journal Entries</span>
                        <button onclick="switchTab('journals')" class="text-xs text-blue-400 hover:underline">View All</button>
                    </h2>
                    <div id="recent-journals-list" class="space-y-4"></div>
                </div>

                <div class="bg-[#121217] p-6 rounded-xl border border-neutral-800 shadow-md">
                    <h2 class="text-lg font-bold text-white mb-4 flex items-center justify-between">
                        <span>Focus Tasks</span>
                        <button onclick="switchTab('tasks')" class="text-xs text-blue-400 hover:underline">View All</button>
                    </h2>
                    <div id="recent-tasks-list" class="space-y-3"></div>
                </div>
            </div>
        </section>

        <section id="sect-journals" class="tab-content hidden space-y-4">
            <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-[#121217] p-4 rounded-xl border border-neutral-800">
                <input type="text" id="journal-search" oninput="renderJournals()" placeholder="Search journals by title or text..." class="flex-1 bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                <select id="journal-sort" onchange="renderJournals()" class="bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                    <option value="newest">Newest First</option>
                    <option value="oldest">Oldest First</option>
                </select>
            </div>
            <div id="journals-grid" class="grid grid-cols-1 md:grid-cols-2 gap-6"></div>
        </section>

        <section id="sect-tasks" class="tab-content hidden space-y-4">
            <div class="flex flex-col md:flex-row md:items-center gap-4 bg-[#121217] p-4 rounded-xl border border-neutral-800">
                <input type="text" id="task-search" oninput="renderTasks()" placeholder="Search tasks..." class="flex-1 bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                <select id="task-filter-status" onchange="renderTasks()" class="bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                    <option value="all">All Statuses</option>
                    <option value="active">Active Only</option>
                    <option value="completed">Completed Only</option>
                </select>
                <select id="task-filter-priority" onchange="renderTasks()" class="bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                    <option value="all">All Priorities</option>
                    <option value="HIGH">High Priority</option>
                    <option value="MEDIUM">Medium Priority</option>
                    <option value="LOW">Low Priority</option>
                </select>
            </div>
            <div class="bg-[#121217] rounded-xl border border-neutral-800 overflow-hidden shadow-md">
                <table class="w-full text-left border-collapse">
                    <thead>
                        <tr class="bg-neutral-900 border-b border-neutral-800 text-xs font-bold uppercase text-neutral-400">
                            <th class="py-3 px-4">Status</th>
                            <th class="py-3 px-4">Task Details</th>
                            <th class="py-3 px-4">Priority</th>
                            <th class="py-3 px-4">Category</th>
                            <th class="py-3 px-4">Due Date</th>
                        </tr>
                    </thead>
                    <tbody id="tasks-table-body" class="divide-y divide-neutral-800"></tbody>
                </table>
            </div>
        </section>

        <section id="sect-contacts" class="tab-content hidden space-y-4">
            <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-[#121217] p-4 rounded-xl border border-neutral-800">
                <input type="text" id="contact-search" oninput="renderContacts()" placeholder="Search contacts by name, email, title..." class="flex-1 bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                <select id="contact-sort" onchange="renderContacts()" class="bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                    <option value="name">Sort by Name</option>
                    <option value="title">Sort by Job Title</option>
                </select>
            </div>
            <div id="contacts-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"></div>
        </section>

        <section id="sect-finances" class="tab-content hidden space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div class="bg-[#121217] p-5 rounded-xl border border-neutral-800">
                    <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Total Income</p>
                    <h3 id="finance-income" class="text-2xl font-extrabold text-green-400 mt-1">${'$'}0.00</h3>
                </div>
                <div class="bg-[#121217] p-5 rounded-xl border border-neutral-800">
                    <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Total Expenses</p>
                    <h3 id="finance-expense" class="text-2xl font-extrabold text-red-400 mt-1">${'$'}0.00</h3>
                </div>
                <div class="bg-[#121217] p-5 rounded-xl border border-neutral-800">
                    <p class="text-xs text-neutral-400 uppercase font-bold tracking-wider">Net Balance</p>
                    <h3 id="finance-balance" class="text-2xl font-extrabold text-blue-400 mt-1">${'$'}0.00</h3>
                </div>
            </div>

            <div class="flex flex-col md:flex-row md:items-center gap-4 bg-[#121217] p-4 rounded-xl border border-neutral-800">
                <input type="text" id="finance-search" oninput="renderFinances()" placeholder="Search transactions by note or category..." class="flex-1 bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                <select id="finance-filter-type" onchange="renderFinances()" class="bg-neutral-900 border border-neutral-800 rounded-lg px-4 py-2 text-sm focus:outline-none focus:border-blue-500 text-white">
                    <option value="all">All Transactions</option>
                    <option value="INCOME">Income Only</option>
                    <option value="EXPENSE">Expense Only</option>
                </select>
            </div>

            <div class="bg-[#121217] rounded-xl border border-neutral-800 overflow-hidden shadow-md">
                <table class="w-full text-left border-collapse">
                    <thead>
                        <tr class="bg-neutral-900 border-b border-neutral-800 text-xs font-bold uppercase text-neutral-400">
                            <th class="py-3 px-4">Date</th>
                            <th class="py-3 px-4">Category</th>
                            <th class="py-3 px-4">Note</th>
                            <th class="py-3 px-4">Type</th>
                            <th class="py-3 px-4 text-right">Amount</th>
                        </tr>
                    </thead>
                    <tbody id="finance-table-body" class="divide-y divide-neutral-800"></tbody>
                </table>
            </div>
        </section>

        <section id="sect-habits" class="tab-content hidden space-y-4">
            <div id="habits-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"></div>
        </section>
    </main>

    <script>
        const TASKS = ${'$'}{tasksJson.toString()};
        const JOURNAL = ${'$'}{journalsJson.toString()};
        const LEDGER = ${'$'}{ledgerJson.toString()};
        const HABITS = ${'$'}{habitsJson.toString()};
        const CONTACTS = ${'$'}{contactsJson.toString()};

        document.getElementById('export-date').innerText = new Date().toLocaleDateString(undefined, {
            year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });

        function switchTab(tabId) {
            document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(content => content.classList.add('hidden'));

            document.getElementById('tab-' + tabId).classList.add('active');
            document.getElementById('sect-' + tabId).classList.remove('hidden');
        }

        function getMediaUrl(path) {
            if (!path) return '';
            if (path.startsWith('http://') || path.startsWith('https://')) return path;
            const parts = path.split('/');
            const filename = parts[parts.length - 1];
            return 'media/' + filename;
        }

        function getAttachmentHtml(attachment) {
            if (!attachment) return '';
            const lower = attachment.toLowerCase();
            const url = getMediaUrl(attachment);
            if (lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.png') || lower.endsWith('.webp')) {
                return `<img class="w-full max-w-md rounded-lg border border-neutral-800 shadow-md mt-2" src="${'\$'}{url}" onerror="this.style.display='none'" />`;
            } else if (lower.endsWith('.mp3') || lower.endsWith('.wav') || lower.endsWith('.m4a') || lower.endsWith('.ogg')) {
                return `<audio class="w-full max-w-sm mt-2" controls src="${'\$'}{url}"></audio>`;
            } else if (lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.3gp')) {
                return `<video class="w-full max-w-md rounded-lg border border-neutral-800 shadow-md mt-2" controls src="${'\$'}{url}"></video>`;
            } else {
                const parts = attachment.split('/');
                const filename = parts[parts.length - 1];
                return `<a href="${'\$'}{url}" download class="inline-flex items-center space-x-1.5 text-xs text-blue-400 hover:underline mt-2">📎 <span>Download file (${'\$'}{filename})</span></a>`;
            }
        }

        function renderDashboard() {
            document.getElementById('stat-journals').innerText = JOURNAL.length;
            document.getElementById('stat-tasks').innerText = TASKS.filter(t => !t.isCompleted).length;
            document.getElementById('stat-contacts').innerText = CONTACTS.length;
            document.getElementById('stat-habits').innerText = HABITS.length;

            const recentJournals = [...JOURNAL].sort((a,b) => b.timestamp - a.timestamp).slice(0, 3);
            const rjContainer = document.getElementById('recent-journals-list');
            if (recentJournals.length === 0) {
                rjContainer.innerHTML = '<p class="text-xs text-neutral-500">No journal entries found.</p>';
            } else {
                rjContainer.innerHTML = recentJournals.map(entry => `
                    <div class="p-3 bg-neutral-900 border border-neutral-800 rounded-lg">
                        <div class="flex items-center justify-between">
                            <h4 class="text-sm font-bold text-white">${'\$'}{entry.title || 'Untitled Entry'}</h4>
                            <span class="text-xs text-neutral-500">${'\$'}{entry.dateString}</span>
                        </div>
                        <p class="text-xs text-neutral-400 mt-1.5 line-clamp-2">${'\$'}{entry.text || ''}</p>
                    </div>
                `).join('');
            }

            const activeTasks = TASKS.filter(t => !t.isCompleted).slice(0, 4);
            const rtContainer = document.getElementById('recent-tasks-list');
            if (activeTasks.length === 0) {
                rtContainer.innerHTML = '<p class="text-xs text-neutral-500">All tasks completed! Great job.</p>';
            } else {
                rtContainer.innerHTML = activeTasks.map(task => `
                    <div class="flex items-center justify-between p-2.5 bg-neutral-900 border border-neutral-800 rounded-lg">
                        <div class="flex items-center space-x-2.5">
                            <span class="w-2 h-2 rounded-full ${'\$'}{task.priority === 'HIGH' ? 'bg-red-500' : task.priority === 'MEDIUM' ? 'bg-amber-500' : 'bg-emerald-500'}"></span>
                            <span class="text-sm text-neutral-200 font-medium">${'\$'}{task.title}</span>
                        </div>
                        <span class="text-[10px] text-neutral-500 bg-neutral-800 border border-neutral-700 px-2 py-0.5 rounded-full">${'\$'}{task.listCategory}</span>
                    </div>
                `).join('');
            }
        }

        function renderJournals() {
            const query = document.getElementById('journal-search').value.toLowerCase();
            const sort = document.getElementById('journal-sort').value;

            let filtered = JOURNAL.filter(j => 
                (j.title && j.title.toLowerCase().includes(query)) || 
                (j.text && j.text.toLowerCase().includes(query))
            );

            if (sort === 'newest') {
                filtered.sort((a,b) => b.timestamp - a.timestamp);
            } else {
                filtered.sort((a,b) => a.timestamp - b.timestamp);
            }

            const grid = document.getElementById('journals-grid');
            if (filtered.length === 0) {
                grid.innerHTML = '<div class="col-span-full text-center text-neutral-500 py-12">No matching journal entries found.</div>';
                return;
            }

            grid.innerHTML = filtered.map(entry => {
                const attList = entry.attachmentsJson ? entry.attachmentsJson.split(';;').filter(Boolean) : [];
                const attHtml = attList.map(getAttachmentHtml).join('');

                return `
                    <div class="bg-[#121217] border border-neutral-800 rounded-xl p-5 shadow-md flex flex-col justify-between space-y-4">
                        <div>
                            <div class="flex justify-between items-start">
                                <h3 class="text-base font-bold text-white">${'\$'}{entry.title || 'Untitled Entry'}</h3>
                                <span class="text-xs text-neutral-500 bg-neutral-900 border border-neutral-800 px-2 py-1 rounded">${'\$'}{entry.dateString}</span>
                            </div>
                            <p class="text-sm text-neutral-300 mt-3 whitespace-pre-line leading-relaxed">${'\$'}{entry.text || ''}</p>
                        </div>
                        ${'\$'}{attHtml ? `<div class="pt-2 border-t border-neutral-800/50 space-y-2">${'\$'}{attHtml}</div>` : ''}
                    </div>
                `;
            }).join('');
        }

        function renderTasks() {
            const query = document.getElementById('task-search').value.toLowerCase();
            const statusFilter = document.getElementById('task-filter-status').value;
            const priorityFilter = document.getElementById('task-filter-priority').value;

            let filtered = TASKS.filter(t => {
                const matchesQuery = t.title.toLowerCase().includes(query) || (t.description && t.description.toLowerCase().includes(query));
                const matchesStatus = statusFilter === 'all' || 
                    (statusFilter === 'active' && !t.isCompleted) || 
                    (statusFilter === 'completed' && t.isCompleted);
                const matchesPriority = priorityFilter === 'all' || t.priority === priorityFilter;
                return matchesQuery && matchesStatus && matchesPriority;
            });

            const body = document.getElementById('tasks-table-body');
            if (filtered.length === 0) {
                body.innerHTML = '<tr><td colspan="5" class="text-center text-neutral-500 py-12">No tasks found.</td></tr>';
                return;
            }

            body.innerHTML = filtered.map(task => `
                <tr class="hover:bg-[#15151b] transition">
                    <td class="py-4 px-4 text-sm font-medium">
                        ${'\$'}{task.isCompleted ? '<span class="text-emerald-500 font-bold">✓ Done</span>' : '<span class="text-amber-500 font-medium">◽ Active</span>'}
                    </td>
                    <td class="py-4 px-4">
                        <div class="text-sm font-bold text-white">${'\$'}{task.title}</div>
                        ${'\$'}{task.description ? `<div class="text-xs text-neutral-400 mt-1 max-w-lg truncate">${'\$'}{task.description}</div>` : ''}
                    </td>
                    <td class="py-4 px-4 text-xs font-semibold">
                        <span class="px-2.5 py-1 rounded-full ${'\$'}{task.priority === 'HIGH' ? 'bg-red-500/10 text-red-400 border border-red-500/20' : task.priority === 'MEDIUM' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'}">
                            ${'\$'}{task.priority}
                        </span>
                    </td>
                    <td class="py-4 px-4 text-xs text-neutral-300">${'\$'}{task.listCategory}</td>
                    <td class="py-4 px-4 text-xs text-neutral-400">${'\$'}{task.dueDateString || '—'}</td>
                </tr>
            `).join('');
        }

        // Render Contacts View
        function renderContacts() {
            const query = document.getElementById('contact-search').value.toLowerCase();
            const sort = document.getElementById('contact-sort').value;

            let filtered = CONTACTS.filter(c => {
                const name = (c.firstName + ' ' + (c.lastName || '')).toLowerCase();
                return name.includes(query) || (c.email && c.email.toLowerCase().includes(query)) || (c.jobTitle && c.jobTitle.toLowerCase().includes(query));
            });

            if (sort === 'name') {
                filtered.sort((a,b) => a.firstName.localeCompare(b.firstName));
            } else {
                filtered.sort((a,b) => (a.jobTitle || '').localeCompare(b.jobTitle || ''));
            }

            const grid = document.getElementById('contacts-grid');
            if (filtered.length === 0) {
                grid.innerHTML = '<div class="col-span-full text-center text-neutral-500 py-12">No contacts found.</div>';
                return;
            }

            grid.innerHTML = filtered.map(c => {
                const photoUrl = c.photoUri ? getMediaUrl(c.photoUri) : '';
                const attached = c.attachedFilesJson ? JSON.parse(c.attachedFilesJson) : [];

                return `
                    <div class="bg-[#121217] border border-neutral-800 rounded-xl p-5 shadow-md flex flex-col space-y-4 hover:border-neutral-700 transition">
                        <div class="flex items-center space-x-4">
                            ${'\$'}{photoUrl ? `
                                <img src="${'\$'}{photoUrl}" class="w-14 h-14 rounded-full object-cover border-2 border-neutral-800 shadow" onerror="this.src='https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80'" />
                            ` : `
                                <div class="w-14 h-14 rounded-full bg-neutral-800 flex items-center justify-center text-lg font-bold text-white uppercase border border-neutral-700">
                                    ${'\$'}{c.firstName[0]}${'\$'}{c.lastName ? c.lastName[0] : ''}
                                </div>
                            `}
                            <div>
                                <h3 class="text-base font-bold text-white">${'\$'}{c.firstName} ${'\$'}{c.lastName || ''}</h3>
                                <p class="text-xs text-neutral-400 font-medium">${'\$'}{c.jobTitle || 'No Title'}</p>
                            </div>
                        </div>
                        <div class="space-y-2 text-xs text-neutral-300">
                            ${'\$'}{c.phone ? `<p class="flex items-center space-x-1.5"><span>📞</span> <span class="font-mono text-neutral-200">${'\$'}{c.phone}</span></p>` : ''}
                            ${'\$'}{c.email ? `<p class="flex items-center space-x-1.5"><span>✉️</span> <span class="text-neutral-200 hover:underline">${'\$'}{c.email}</span></p>` : ''}
                        </div>
                        ${'\$'}{attached.length > 0 ? `
                            <div class="pt-3 border-t border-neutral-800">
                                <p class="text-[10px] text-neutral-400 font-bold uppercase tracking-wider mb-2">Attached Files</p>
                                <div class="space-y-1.5">
                                    ${'\$'}{attached.map(f => getAttachmentHtml(f)).join('')}
                                </div>
                            </div>
                        ` : ''}
                    </div>
                `;
            }).join('');
        }

        function renderFinances() {
            const query = document.getElementById('finance-search').value.toLowerCase();
            const typeFilter = document.getElementById('finance-filter-type').value;

            let filtered = LEDGER.filter(l => {
                const matchesQuery = (l.note && l.note.toLowerCase().includes(query)) || (l.categoryTag && l.categoryTag.toLowerCase().includes(query));
                const matchesType = typeFilter === 'all' || l.type === typeFilter;
                return matchesQuery && matchesType;
            });

            let totalIncome = 0;
            let totalExpense = 0;
            LEDGER.forEach(l => {
                if (l.type === 'INCOME') totalIncome += l.amount;
                else if (l.type === 'EXPENSE') totalExpense += l.amount;
            });

            document.getElementById('finance-income').innerText = '${'\$'}' + totalIncome.toFixed(2);
            document.getElementById('finance-expense').innerText = '${'\$'}' + totalExpense.toFixed(2);
            document.getElementById('finance-balance').innerText = '${'\$'}' + (totalIncome - totalExpense).toFixed(2);

            const body = document.getElementById('finance-table-body');
            if (filtered.length === 0) {
                body.innerHTML = '<tr><td colspan="5" class="text-center text-neutral-500 py-12">No financial transactions found.</td></tr>';
                return;
            }

            body.innerHTML = filtered.map(item => `
                <tr class="hover:bg-[#15151b] transition">
                    <td class="py-4 px-4 text-xs font-mono text-neutral-400">${'\$'}{new Date(item.timestamp).toLocaleDateString()}</td>
                    <td class="py-4 px-4 text-xs font-semibold text-neutral-300">
                        <span class="bg-neutral-800 px-2.5 py-1 rounded-full border border-neutral-700">${'\$'}{item.categoryTag}</span>
                    </td>
                    <td class="py-4 px-4 text-sm text-neutral-200">${'\$'}{item.note || '—'}</td>
                    <td class="py-4 px-4 text-xs font-bold">
                        ${'\$'}{item.type === 'INCOME' ? '<span class="text-green-400">INCOME</span>' : '<span class="text-red-400">EXPENSE</span>'}
                    </td>
                    <td class="py-4 px-4 text-sm font-bold font-mono text-right ${'\$'}{item.type === 'INCOME' ? 'text-green-400' : 'text-red-400'}">
                        ${'\$'}{item.type === 'INCOME' ? '+' : '-'}${'\$'}${'\$'}{item.amount.toFixed(2)}
                    </td>
                </tr>
            `).join('');
        }

        function renderHabits() {
            const grid = document.getElementById('habits-grid');
            if (HABITS.length === 0) {
                grid.innerHTML = '<div class="col-span-full text-center text-neutral-500 py-12">No habits tracked yet.</div>';
                return;
            }

            grid.innerHTML = HABITS.map(h => {
                const dateStr = h.lastCompletedTimestamp > 0 ? new Date(h.lastCompletedTimestamp).toLocaleDateString() : 'Never';
                return `
                    <div class="bg-[#121217] border border-neutral-800 rounded-xl p-5 shadow-md flex items-center justify-between hover:border-neutral-700 transition">
                        <div>
                            <h3 class="text-base font-bold text-white">${'\$'}{h.name}</h3>
                            <p class="text-xs text-neutral-400 mt-2">Last Completed: <span class="font-medium text-neutral-300">${'\$'}{dateStr}</span></p>
                        </div>
                        <div class="text-right">
                            <span class="text-3xl">🔥</span>
                            <div class="text-sm font-bold text-white mt-1">${'\$'}{h.streakCount} Streak</div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        renderDashboard();
        renderJournals();
        renderTasks();
        renderContacts();
        renderFinances();
        renderHabits();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
