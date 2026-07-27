@file:Suppress("DEPRECATION")
package com.example.util

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Base64
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.Task
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GoogleTasksSyncManager {
    private const val TAG = "GoogleTasksSync"
    private const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_tasks_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, TASKS_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Tasks scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Tasks: ${e.message}", e)
            null
        }
    }

    /**
     * Performs a full 2-way sync for Google Tasks (tasks with NO date and time).
     */
    suspend fun syncTasks(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val database = AppDatabase.getInstance(context)
            val taskDao = database.taskDao()
            val allLocalTasks = taskDao.getAllTasks().first()

            // Filter local tasks that have NO date/time (dueDateString is empty)
            val localTasksNoDate = allLocalTasks.filter { it.dueDateString.isEmpty() }

            // ---- STEP 1: FETCH FROM GOOGLE TASKS ----
            val googleTasks = fetchGoogleTasks(token)
            val googleIdToTask = googleTasks.associateBy { it.id }

            var importedCount = 0
            var updatedCount = 0
            var exportedCount = 0
            var deletedCount = 0

            // Keep track of which Google tasks we matched to local tasks
            val matchedGoogleIds = mutableSetOf<String>()

            // ---- STEP 2: PROCESS GOOGLE TASKS AND UPDATE/CREATE LOCALLY ----
            for (gTask in googleTasks) {
                // Check if this Google task has been deleted locally
                val isDeletedLocally = DeletedTaskLogHelper.isGTaskIdDeletedLocally(context, gTask.id) ||
                        DeletedTaskLogHelper.isGoogleTaskDeletedLocally(context, gTask.title)

                if (isDeletedLocally) {
                    Log.d(TAG, "Sync: Google Task '${gTask.title}' (ID: ${gTask.id}) was deleted locally. Deleting from Google.")
                    try {
                        deleteGoogleTask(token, gTask.id)
                        deletedCount++
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting GTask ${gTask.id}: ${e.message}", e)
                    }
                    continue
                }

                // Find local task by [GTaskId: ...] tag or fallback to title matching (for tasks without date)
                val matchedLocal = localTasksNoDate.find { task ->
                    task.description.contains("[GTaskId: ${gTask.id}]")
                } ?: localTasksNoDate.find { task ->
                    task.title.trim().equals(gTask.title.trim(), ignoreCase = true) &&
                    !task.description.contains("[GTaskId:")
                }

                if (matchedLocal != null) {
                    matchedGoogleIds.add(gTask.id)
                    
                    // Check if local description already has the [GTaskId: ...] tag
                    val hasTag = matchedLocal.description.contains("[GTaskId: ${gTask.id}]")
                    val cleanLocalDesc = getCleanDescription(matchedLocal.description)

                    // Determine if updates are needed
                    val isGoogleCompleted = gTask.status == "completed"
                    val isLocalCompleted = matchedLocal.isCompleted

                    var needsUpdateLocal = false
                    var needsUpdateGoogle = false

                    var updatedLocalTask = matchedLocal

                    // 1. Resolve completion status difference
                    if (isGoogleCompleted != isLocalCompleted) {
                        // If one is completed and the other isn't, we can sync completion.
                        // Let's assume the local completed state is the latest, unless the Google task was marked completed.
                        // To be safe, if either is completed, mark both completed, or sync Google -> Local if Google is completed.
                        if (isGoogleCompleted) {
                            updatedLocalTask = updatedLocalTask.copy(isCompleted = true)
                            needsUpdateLocal = true
                        } else {
                            // Local is completed, but Google is not. Update Google.
                            needsUpdateGoogle = true
                        }
                    }

                    // 2. Resolve Title / Notes difference
                    if (matchedLocal.title != gTask.title || cleanLocalDesc != gTask.notes) {
                        // If they differ, update Google task with local changes (as user interacts with the app primarily)
                        needsUpdateGoogle = true
                    }

                    // 3. Ensure local task has the [GTaskId: ...] tag
                    if (!hasTag) {
                        val newDesc = if (matchedLocal.description.isEmpty()) {
                            "[GTaskId: ${gTask.id}]"
                        } else {
                            "${matchedLocal.description}\n\n[GTaskId: ${gTask.id}]"
                        }
                        updatedLocalTask = updatedLocalTask.copy(description = newDesc)
                        needsUpdateLocal = true
                    }

                    if (needsUpdateLocal) {
                        taskDao.updateTask(updatedLocalTask)
                        updatedCount++
                    }

                    if (needsUpdateGoogle) {
                        val notesWithId = if (cleanLocalDesc.isEmpty()) {
                            "[AppTaskId: ${updatedLocalTask.id}]"
                        } else {
                            "$cleanLocalDesc\n\n[AppTaskId: ${updatedLocalTask.id}]"
                        }
                        updateGoogleTask(token, gTask.id, updatedLocalTask.title, notesWithId, if (updatedLocalTask.isCompleted) "completed" else "needsAction")
                    }
                } else {
                    // Google Task has no local counterpart, so import it as a new local task (with no date)
                    val notes = gTask.notes
                    val cleanNotes = notes.replace(Regex("""\[AppTaskId:\s*([^\]]+)\]"""), "").trim()
                    val finalDesc = if (cleanNotes.isEmpty()) {
                        "[GTaskId: ${gTask.id}]"
                    } else {
                        "$cleanNotes\n\n[GTaskId: ${gTask.id}]"
                    }

                    val newLocal = Task(
                        title = gTask.title,
                        description = finalDesc,
                        isCompleted = gTask.status == "completed",
                        listCategory = "Google Tasks",
                        dueDateString = ""
                    )
                    val insertedId = taskDao.insertTask(newLocal)
                    importedCount++
                    matchedGoogleIds.add(gTask.id)

                    // Update Google Task's notes with the newly inserted AppTaskId so we can track deletion
                    try {
                        val notesWithId = if (cleanNotes.isEmpty()) {
                            "[AppTaskId: $insertedId]"
                        } else {
                            "$cleanNotes\n\n[AppTaskId: $insertedId]"
                        }
                        updateGoogleTask(token, gTask.id, gTask.title, notesWithId, gTask.status)
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        Log.e(TAG, "Failed to update Google Task notes with AppTaskId: ${e.message}", e)
                    }
                }
            }

            // ---- STEP 3: EXPORT NEW LOCAL TASKS TO GOOGLE TASKS ----
            for (local in localTasksNoDate) {
                val gTaskId = extractGoogleTaskId(local.description)
                if (gTaskId == null) {
                    // This is a new local task with NO date and NO Google Task ID yet! Export it.
                    val cleanDesc = getCleanDescription(local.description)
                    val notesWithId = if (cleanDesc.isEmpty()) {
                        "[AppTaskId: ${local.id}]"
                    } else {
                        "$cleanDesc\n\n[AppTaskId: ${local.id}]"
                    }
                    val status = if (local.isCompleted) "completed" else "needsAction"
                    val newGTaskId = createGoogleTask(token, local.title, notesWithId, status)
                    if (newGTaskId != null) {
                        val updatedDesc = if (local.description.isEmpty()) {
                            "[GTaskId: $newGTaskId]"
                        } else {
                            "${local.description}\n\n[GTaskId: $newGTaskId]"
                        }
                        taskDao.updateTask(local.copy(description = updatedDesc))
                        exportedCount++
                    }
                } else {
                    // Local task has a Google Task ID, but was it deleted on Google?
                    if (!matchedGoogleIds.contains(gTaskId)) {
                        // The task has a Google Task ID tag, but that ID was not returned by Google.
                        // This means the task was deleted on Google Tasks, so we delete it locally to keep them in sync.
                        taskDao.deleteTask(local)
                        deletedCount++
                    }
                }
            }

            // ---- STEP 4: DETECT LOCALLY DELETED TASKS AND DELETE THEM FROM GOOGLE ----
            // If there are Google Tasks that have [AppTaskId: ...] (if we used that), or if we want to be safe,
            // we can clean up Google Tasks that are no longer present in local tasks.
            // Wait, we didn't store AppTaskId in Google Tasks notes, but we can do that in the future to make delete sync 100% perfect.
            // For now, if local task with a GTaskId is deleted from the app's database, it won't be in localTasksNoDate.
            // But we can't easily know which GTaskId was deleted unless we track deletions, OR we can check if there are Google Tasks with notes containing "[AppTaskId: ID]" where ID is not in our database!
            // Let's add [AppTaskId: ID] to the notes we send to Google, so we can delete them on Google if the local task is deleted!
            // This is brilliant! Let's do that in createGoogleTask and updateGoogleTask.
            for (gTask in googleTasks) {
                val appTaskId = extractAppTaskId(gTask.notes)
                if (appTaskId != null) {
                    val localExists = allLocalTasks.any { it.id == appTaskId }
                    if (!localExists) {
                        // The local task was deleted by the user in our app. So delete it from Google Tasks!
                        deleteGoogleTask(token, gTask.id)
                        deletedCount++
                    }
                }
            }

            Pair(true, "Sync Complete! Imported $importedCount, Updated $updatedCount, Exported $exportedCount, Synced $deletedCount deletions.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing tasks: ${e.message}", e)
            Pair(false, "Sync failed: ${e.localizedMessage}")
        }
    }

    data class GoogleTaskDetails(
        val id: String,
        val title: String,
        val notes: String,
        val status: String,
        val updated: String
    )

    private fun fetchGoogleTasks(token: String): List<GoogleTaskDetails> {
        val list = mutableListOf<GoogleTaskDetails>()
        var pageToken: String? = null
        
        do {
            val urlBuilder = StringBuilder("https://tasks.googleapis.com/v1/lists/@default/tasks?showCompleted=true&showHidden=true")
            pageToken?.let {
                urlBuilder.append("&pageToken=").append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $token")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to fetch Google tasks: code=${response.code}, msg=${response.message}")
                        return list
                    }
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val items = json.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val id = item.optString("id", "")
                            val title = item.optString("title", "")
                            val notes = item.optString("notes", "")
                            val status = item.optString("status", "needsAction")
                            val updated = item.optString("updated", "")

                            if (id.isNotEmpty()) {
                                list.add(GoogleTaskDetails(id, title, notes, status, updated))
                            }
                        }
                    }
                    pageToken = json.optString("nextPageToken", "").takeIf { it.isNotEmpty() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Google tasks page: ${e.message}", e)
                pageToken = null
            }
        } while (pageToken != null)

        return list
    }

    private fun createGoogleTask(token: String, title: String, notes: String, status: String): String? {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks"
        val payload = JSONObject().apply {
            put("title", title)
            put("notes", notes)
            put("status", status)
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to create Google task: code=${response.code}, msg=${response.message}")
                    return null
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("id").takeIf { it.isNotEmpty() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google task: ${e.message}", e)
        }
        return null
    }

    private fun updateGoogleTask(token: String, id: String, title: String, notes: String, status: String): Boolean {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks/$id"
        val payload = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("notes", notes)
            put("status", status)
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to update Google task: code=${response.code}, msg=${response.message}")
                    return false
                }
                return true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Google task: ${e.message}", e)
        }
        return false
    }

    private fun deleteGoogleTask(token: String, id: String): Boolean {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks/$id"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to delete Google task: code=${response.code}, msg=${response.message}")
                    return false
                }
                return true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Google task: ${e.message}", e)
        }
        return false
    }

    // Helpers to parse tags
    fun extractGoogleTaskId(description: String): String? {
        val regex = Regex("""\[GTaskId:\s*([^\]]+)\]""")
        val match = regex.find(description)
        return match?.groupValues?.get(1)?.trim()
    }

    fun extractAppTaskId(notes: String): Int? {
        val regex = Regex("""\[AppTaskId:\s*([^\]]+)\]""")
        val match = regex.find(notes)
        return match?.groupValues?.get(1)?.trim()?.toIntOrNull()
    }

    fun getCleanDescription(description: String): String {
        // Remove [GTaskId: ...] and empty lines around it
        val cleaned = description.replace(Regex("""\[GTaskId:\s*([^\]]+)\]"""), "").trim()
        return cleaned
    }
}


// ==================== CONSOLIDATED CALENDAR & TASK UTILITIES ====================



// ==================== CONSOLIDATED FROM: DeletedTaskLogHelper.kt ====================



object DeletedTaskLogHelper {
    private const val PREFS_NAME = "deleted_tasks_log_prefs"
    private const val TAG = "DeletedTaskLog"

    fun logDeletedTask(context: Context, title: String, dueDate: String, gCalEventId: String?) {
        if (title.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        editor.putBoolean(titleDateKey, true)
        Log.d(TAG, "Logged deleted task by Title/Date: $titleDateKey")

        if (!gCalEventId.isNullOrEmpty()) {
            val eventIdKey = "gcal_event_id:$gCalEventId"
            editor.putBoolean(eventIdKey, true)
            Log.d(TAG, "Logged deleted task by GCalEventId: $eventIdKey")
        }
        
        editor.apply()
    }

    fun isTaskDeletedLocally(context: Context, title: String, dueDate: String): Boolean {
        if (title.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        return prefs.getBoolean(titleDateKey, false)
    }

    fun isGCalEventDeletedLocally(context: Context, gCalEventId: String): Boolean {
        if (gCalEventId.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eventIdKey = "gcal_event_id:$gCalEventId"
        return prefs.getBoolean(eventIdKey, false)
    }
    
    fun removeDeletedTaskFromLog(context: Context, title: String, dueDate: String, gCalEventId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        editor.remove(titleDateKey)
        
        if (!gCalEventId.isNullOrEmpty()) {
            val eventIdKey = "gcal_event_id:$gCalEventId"
            editor.remove(eventIdKey)
        }
        
        editor.apply()
    }

    // --- Google Tasks Deletion Logging Support ---
    fun logDeletedGoogleTask(context: Context, title: String, gTaskId: String?) {
        if (title.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        editor.putBoolean(titleKey, true)
        Log.d(TAG, "Logged deleted Google Task by Title: $titleKey")

        if (!gTaskId.isNullOrEmpty()) {
            val gTaskIdKey = "g_task_id:$gTaskId"
            editor.putBoolean(gTaskIdKey, true)
            Log.d(TAG, "Logged deleted Google Task by GTaskId: $gTaskIdKey")
        }
        
        editor.apply()
    }

    fun isGoogleTaskDeletedLocally(context: Context, title: String): Boolean {
        if (title.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        return prefs.getBoolean(titleKey, false)
    }

    fun isGTaskIdDeletedLocally(context: Context, gTaskId: String): Boolean {
        if (gTaskId.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gTaskIdKey = "g_task_id:$gTaskId"
        return prefs.getBoolean(gTaskIdKey, false)
    }

    fun removeDeletedGoogleTaskFromLog(context: Context, title: String, gTaskId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        editor.remove(titleKey)
        
        if (!gTaskId.isNullOrEmpty()) {
            val gTaskIdKey = "g_task_id:$gTaskId"
            editor.remove(gTaskIdKey)
        }
        
        editor.apply()
    }
}



// ==================== CONSOLIDATED FROM: GoogleCalendarSyncHelper.kt ====================



data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String
)

object GoogleCalendarSyncHelper {

    private const val TAG = "GoogleCalendarSync"

    // Helper to check and get a calendar ID (preferring Google account calendars or user's selected preferences)
    fun getOrCreateCalendarId(context: Context): Long? {
        val prefs = context.getSharedPreferences("app_calendar_prefs", Context.MODE_PRIVATE)
        val selectedAccount = prefs.getString("selected_calendar_account", null)
        val selectedName = prefs.getString("selected_calendar_name", null)
        val selectedId = prefs.getLong("selected_calendar_id", -1L)

        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            var matchedId: Long? = null
            var googleFallbackId: Long? = null
            var fallbackId: Long? = null

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountName = it.getString(1) ?: ""
                    val accountType = it.getString(2) ?: ""
                    val displayName = it.getString(3) ?: ""
                    
                    // Priority 1: Match saved calendar ID precisely
                    if (selectedId != -1L && id == selectedId) {
                        Log.d(TAG, "Found precise selected calendar ID match: $id")
                        return id
                    }
                    
                    // Priority 2: Match saved Account name & Display Name
                    if (selectedAccount != null && selectedName != null &&
                        accountName == selectedAccount && displayName == selectedName) {
                        matchedId = id
                    }
                    
                    // Priority 3: Fallbacks
                    if (accountType == "com.google" && googleFallbackId == null) {
                        googleFallbackId = id
                    }
                    if (fallbackId == null) {
                        fallbackId = id
                    }
                }
            }
            if (matchedId != null) {
                Log.d(TAG, "Found preference-matched calendar ID: $matchedId")
                return matchedId
            }
            if (googleFallbackId != null) {
                Log.d(TAG, "Found Google Account fallback calendar ID: $googleFallbackId")
                return googleFallbackId
            }
            if (fallbackId != null) {
                Log.d(TAG, "Found general fallback calendar ID: $fallbackId")
                return fallbackId
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing for querying calendars: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars: ${e.message}", e)
        }

        return null
    }

    // Helper to query all available calendars on the device
    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val list = mutableListOf<CalendarInfo>()
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountName = it.getString(1) ?: "Local"
                    val accountType = it.getString(2) ?: "Local"
                    val displayName = it.getString(3) ?: "My Calendar"
                    list.add(CalendarInfo(id, accountName, accountType, displayName))
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying calendars: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars: ${e.message}", e)
        }
        return list
    }

    // Bidirectional sync
    suspend fun syncGoogleCalendar(
        context: Context,
        localTasks: List<Task>,
        onImportTask: suspend (String, String, Int, String) -> Long,
        onUpdateTask: suspend (Task) -> Unit
    ): String {
        val calendarId = getOrCreateCalendarId(context)
            ?: return "No calendar found on device. Please set up a Google account first."

        var importedCount = 0
        var exportedCount = 0

        val resolver = context.contentResolver
        val timeZone = TimeZone.getDefault().id

        // 1. IMPORT FROM GOOGLE CALENDAR
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Define query window: from 365 days ago to 365 days in the future
        val calendarStart = Calendar.getInstance()
        calendarStart.add(Calendar.DAY_OF_YEAR, -365)
        val startMillis = calendarStart.timeInMillis
        
        val calendarEnd = Calendar.getInstance()
        calendarEnd.add(Calendar.DAY_OF_YEAR, 365)
        val endMillis = calendarEnd.timeInMillis

        val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (deleted != 1)"
        val selectionArgs = arrayOf(calendarId.toString(), startMillis.toString(), endMillis.toString())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )

        var eventCursor: Cursor? = null
        try {
            eventCursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            eventCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "Google Event"
                    val description = cursor.getString(2) ?: ""
                    val dtStart = cursor.getLong(3)
                    val dtEnd = cursor.getLong(4)
                    val gcalLocation = try { cursor.getString(5) ?: "" } catch (e: Exception) { "" }

                    val eventDateStr = sdfDate.format(Date(dtStart))

                    // Check if this event has been deleted locally
                    val isDeletedLocally = DeletedTaskLogHelper.isGCalEventDeletedLocally(context, eventId.toString()) ||
                            DeletedTaskLogHelper.isTaskDeletedLocally(context, title, eventDateStr)
                    
                    if (isDeletedLocally) {
                        Log.d(TAG, "Sync: Event '$title' on $eventDateStr (ID: $eventId) was deleted locally. Deleting from Google Calendar.")
                        try {
                            resolver.delete(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                null,
                                null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed deleting GCal Event $eventId: ${e.message}", e)
                        }
                        continue
                    }

                    // Extract AppTaskId if exists in description to check if local task was deleted
                    val appTaskIdRegex = Regex("""\[AppTaskId:\s*(\d+)\]""")
                    val appTaskIdMatch = appTaskIdRegex.find(description)
                    val appTaskId = appTaskIdMatch?.groupValues?.get(1)?.toIntOrNull()

                    if (appTaskId != null) {
                        val localExists = localTasks.any { it.id == appTaskId }
                        if (!localExists) {
                            // Local task was deleted, so delete corresponding Google Calendar Event
                            try {
                                resolver.delete(
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                    null,
                                    null
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed deleting GCal Event $eventId for deleted local task $appTaskId: ${e.message}", e)
                            }
                            continue
                        }
                    }

                    // Check if we already have this synced locally
                    val matchedLocal = localTasks.find { task ->
                        (appTaskId != null && task.id == appTaskId) ||
                        task.description.contains("[GCalEventId: $eventId]")
                    }

                    val alreadySynced = matchedLocal != null || localTasks.any { task ->
                        task.title.trim().equals(title.trim(), ignoreCase = true) && task.dueDateString == eventDateStr
                    }

                    if (matchedLocal != null) {
                        // Timing, Title, or Reminder sync from Google Calendar to local task
                        val gCalTime = parseTaskTime(matchedLocal.description)
                        val gCalDuration = parseTaskDuration(matchedLocal.description)

                        val newHourFormatter = SimpleDateFormat("hh:mm a", Locale.US)
                        val expectedTimeStr = newHourFormatter.format(Date(dtStart))
                        val expectedDuration = if (dtEnd > dtStart) {
                            ((dtEnd - dtStart) / 60000).toInt().coerceAtLeast(15)
                        } else {
                            30
                        }

                        val actualGCalTime = Calendar.getInstance().apply { timeInMillis = dtStart }
                        val actualHour = actualGCalTime.get(Calendar.HOUR_OF_DAY)
                        val actualMinute = actualGCalTime.get(Calendar.MINUTE)

                        val timeChanged = gCalTime == null || gCalTime.first != actualHour || gCalTime.second != actualMinute
                        val durationChanged = gCalDuration != expectedDuration
                        val dateChanged = matchedLocal.dueDateString != eventDateStr
                        val titleChanged = !matchedLocal.title.trim().equals(title.trim(), ignoreCase = true)

                        val remindersList = getEventReminders(context, eventId)
                        val currentRemindersList = getTaskRemindersInMinutes(matchedLocal.description)
                        val remindersChanged = remindersList.sorted() != currentRemindersList.sorted()

                        if (timeChanged || durationChanged || dateChanged || titleChanged || remindersChanged) {
                            var descriptionWithoutTags = matchedLocal.description
                            
                            // Remove existing tags if present
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Time:\s*[^\]]+\]"""), "").trim()
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Duration:\s*[^\]]+\]"""), "").trim()
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Reminders:\s*[^\]]+\]"""), "").trim()
                            
                            descriptionWithoutTags = descriptionWithoutTags.trim()

                            // Rebuild description with new tags
                            val remindersTag = if (remindersList.isNotEmpty()) {
                                " [Reminders: ${remindersList.map { formatMinutesToReminderString(it) }.joinToString(", ")}]"
                            } else {
                                ""
                            }
                            
                            val updatedDesc = if (descriptionWithoutTags.isEmpty()) {
                                "[Time: $expectedTimeStr] [Duration: ${expectedDuration}m]$remindersTag"
                            } else {
                                "$descriptionWithoutTags\n[Time: $expectedTimeStr] [Duration: ${expectedDuration}m]$remindersTag"
                            }

                            val updatedTask = matchedLocal.copy(
                                title = title,
                                dueDateString = eventDateStr,
                                estimatedMinutes = expectedDuration,
                                description = updatedDesc
                            )
                            onUpdateTask(updatedTask)
                        }
                    } else if (!alreadySynced && !description.contains("[AppTaskId:")) {
                        // Estimate duration
                        val estMinutes = if (dtEnd > dtStart) {
                            ((dtEnd - dtStart) / 60000).toInt().coerceAtLeast(15)
                        } else {
                            30
                        }

                        val hourFormatter = SimpleDateFormat("hh:mm a", Locale.US)
                        val timeStr = hourFormatter.format(Date(dtStart))
                        
                        val remindersList = getEventReminders(context, eventId)
                        val remindersTag = if (remindersList.isNotEmpty()) {
                            " [Reminders: ${remindersList.map { formatMinutesToReminderString(it) }.joinToString(", ")}]"
                        } else {
                            ""
                        }

                        val cleanDesc = buildString {
                            if (description.isNotEmpty()) {
                                append(description)
                                if (!description.endsWith("\n")) append("\n")
                            }
                            append("[Time: $timeStr] [Duration: ${estMinutes}m]$remindersTag")
                            if (gcalLocation.isNotEmpty()) {
                                append("\n[Location: $gcalLocation]")
                            }
                            append("\n\n[GCalEventId: $eventId]")
                        }

                        val newTaskId = onImportTask(title, cleanDesc, estMinutes, eventDateStr)
                        importedCount++

                        // Update Google Calendar event description with the newly created local AppTaskId
                        try {
                            val updatedDescription = if (description.isEmpty()) {
                                "[AppTaskId: $newTaskId]"
                            } else {
                                "$description\n\n[AppTaskId: $newTaskId]"
                            }
                            val updateValues = ContentValues().apply {
                                put(CalendarContract.Events.DESCRIPTION, updatedDescription)
                            }
                            resolver.update(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                updateValues,
                                null,
                                null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed updating Google Calendar event $eventId with AppTaskId: ${e.message}", e)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return "Calendar permissions are required to sync Google Calendar."
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing from Google Calendar: ${e.message}", e)
            return "Sync failed: ${e.message}"
        }

        // 2. EXPORT AND UPDATE TO GOOGLE CALENDAR
        for (task in localTasks) {
            if (task.dueDateString.isNotEmpty()) {
                if (!task.description.contains("[GCalEventId:")) {
                    // Export new event
                    try {
                        val dateParts = task.dueDateString.split("-")
                        if (dateParts.size == 3) {
                            val year = dateParts[0].toIntOrNull() ?: continue
                            val month = (dateParts[1].toIntOrNull() ?: continue) - 1
                            val day = dateParts[2].toIntOrNull() ?: continue

                            // Try parsing [Time: hh:mm AM/PM] or standard time from task description
                            var startHour = 9
                            var startMinute = 0
                            val parsedTime = parseTaskTime(task.description)
                            if (parsedTime != null) {
                                startHour = parsedTime.first
                                startMinute = parsedTime.second
                            }

                            val startCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.HOUR_OF_DAY, startHour)
                                set(Calendar.MINUTE, startMinute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            val durationMin = parseTaskDuration(task.description).coerceAtLeast(15)
                            val endCal = Calendar.getInstance().apply {
                                timeInMillis = startCal.timeInMillis + (durationMin * 60 * 1000L)
                            }

                            val reminderMins = getTaskRemindersInMinutes(task.description)
                            val locMatch = Regex("""\[Location:\s*([^\]]+)\]""").find(task.description)
                            val locValue = locMatch?.groupValues?.get(1)?.trim()

                            val values = ContentValues().apply {
                                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                                put(CalendarContract.Events.TITLE, task.title)
                                put(CalendarContract.Events.DESCRIPTION, "${task.description}\n\n[AppTaskId: ${task.id}]")
                                put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                                put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                                put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                                put(CalendarContract.Events.HAS_ALARM, if (reminderMins.isNotEmpty()) 1 else 0)
                                if (!locValue.isNullOrEmpty()) {
                                    put(CalendarContract.Events.EVENT_LOCATION, locValue)
                                }
                            }

                            val uri: Uri? = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                            if (uri != null) {
                                val newEventId = ContentUris.parseId(uri)
                                setEventReminders(context, newEventId, reminderMins)

                                // Dual Sync Add People: invite tagged contacts by email in calendar event
                                try {
                                    val localContacts = AppDatabase.getInstance(context).contactDao().getAllContacts().first()
                                    val tags = Regex("""@\w+""").findAll(task.description + " " + task.title)
                                        .map { it.value.lowercase().trim().replace("@", "") }.distinct().toList()
                                    for (tag in tags) {
                                        val contact = localContacts.find {
                                            it.firstName.lowercase() == tag ||
                                            it.email.substringBefore("@").replace(".", "_").lowercase() == tag
                                        }
                                        if (contact != null && contact.email.isNotEmpty()) {
                                            val attendeeValues = ContentValues().apply {
                                                put(CalendarContract.Attendees.EVENT_ID, newEventId)
                                                put(CalendarContract.Attendees.ATTENDEE_NAME, "${contact.firstName} ${contact.lastName}".trim())
                                                put(CalendarContract.Attendees.ATTENDEE_EMAIL, contact.email)
                                                put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE)
                                                put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
                                                put(CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
                                            }
                                            resolver.insert(CalendarContract.Attendees.CONTENT_URI, attendeeValues)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed adding attendees to event $newEventId", e)
                                }

                                // Update our local task description to reflect GCal event id
                                val updatedDesc = if (task.description.isEmpty()) {
                                    "[GCalEventId: $newEventId]"
                                } else {
                                    "${task.description}\n\n[GCalEventId: $newEventId]"
                                }
                                onUpdateTask(task.copy(description = updatedDesc))
                                exportedCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed exporting task '${task.title}': ${e.message}", e)
                    }
                } else {
                    // Update existing event to sync changes (e.g., end time/duration/reminders changes)
                    try {
                        val idRegex = Regex("""\[GCalEventId:\s*(\d+)\]""")
                        val match = idRegex.find(task.description)
                        val eventId = match?.groupValues?.get(1)?.toLongOrNull()
                        if (eventId != null) {
                            val dateParts = task.dueDateString.split("-")
                            if (dateParts.size == 3) {
                                val year = dateParts[0].toIntOrNull() ?: continue
                                val month = (dateParts[1].toIntOrNull() ?: continue) - 1
                                val day = dateParts[2].toIntOrNull() ?: continue

                                var startHour = 9
                                var startMinute = 0
                                val parsedTime = parseTaskTime(task.description)
                                if (parsedTime != null) {
                                    startHour = parsedTime.first
                                    startMinute = parsedTime.second
                                }

                                val startCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, startHour)
                                    set(Calendar.MINUTE, startMinute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }

                                val durationMin = parseTaskDuration(task.description).coerceAtLeast(15)
                                val endCal = Calendar.getInstance().apply {
                                    timeInMillis = startCal.timeInMillis + (durationMin * 60 * 1000L)
                                }

                                val reminderMins = getTaskRemindersInMinutes(task.description)
                                val locMatch = Regex("""\[Location:\s*([^\]]+)\]""").find(task.description)
                                val locValue = locMatch?.groupValues?.get(1)?.trim()
                                val values = ContentValues().apply {
                                    put(CalendarContract.Events.TITLE, task.title)
                                    put(CalendarContract.Events.DESCRIPTION, "${task.description}\n\n[AppTaskId: ${task.id}]")
                                    put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                                    put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                                    put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                                    put(CalendarContract.Events.HAS_ALARM, if (reminderMins.isNotEmpty()) 1 else 0)
                                    if (!locValue.isNullOrEmpty()) {
                                        put(CalendarContract.Events.EVENT_LOCATION, locValue)
                                    } else {
                                        putNull(CalendarContract.Events.EVENT_LOCATION)
                                    }
                                }

                                resolver.update(
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                    values,
                                    null,
                                    null
                                )
                                setEventReminders(context, eventId, reminderMins)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed updating task on GCal '${task.title}': ${e.message}", e)
                    }
                }
            }
        }

        return "Sync Complete! Imported $importedCount new events, Exported $exportedCount tasks."
    }

    // Helper to query all reminders for a given calendar event ID
    private fun getEventReminders(context: Context, eventId: Long): List<Int> {
        val list = mutableListOf<Int>()
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    list.add(it.getInt(0))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying reminders for event $eventId: ${e.message}")
        }
        return list
    }

    // Helper to clear and write reminders for a given calendar event ID
    private fun setEventReminders(context: Context, eventId: Long, minutesList: List<Int>) {
        val resolver = context.contentResolver
        try {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old reminders for event $eventId: ${e.message}")
        }

        for (mins in minutesList) {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, mins)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting reminder ($mins min) for event $eventId: ${e.message}")
            }
        }
    }

    // Helper to parse time from description
    private fun parseTaskTime(description: String): Pair<Int, Int>? {
        val amPmRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\s*(AM|PM)\]""", RegexOption.IGNORE_CASE)
        val amPmMatch = amPmRegex.find(description)
        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = amPmMatch.groupValues[3].uppercase(Locale.US)
            if (ampm == "PM" && hour < 12) {
                hour += 12
            } else if (ampm == "AM" && hour == 12) {
                hour = 0
            }
            return Pair(hour, minute)
        }

        val stdRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\]""")
        val stdMatch = stdRegex.find(description)
        if (stdMatch != null) {
            val hour = stdMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = stdMatch.groupValues[2].toIntOrNull() ?: 0
            return Pair(hour, minute)
        }
        return null
    }

    private fun parseTaskDuration(description: String): Int {
        val regex = Regex("""\[Duration:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        val match = regex.find(description)
        if (match != null) {
            val durationStr = match.groupValues[1].trim().lowercase(Locale.US)
            
            // Check for hour/hours/hr/hrs/h
            if (durationStr.contains("hour") || durationStr.contains("hr") || durationStr.contains("h")) {
                // Find the decimal number or integer before/in the unit
                val numRegex = Regex("""(\d+\.?\d*)""")
                val numMatch = numRegex.find(durationStr)
                if (numMatch != null) {
                    val numFloat = numMatch.groupValues[1].toFloatOrNull()
                    if (numFloat != null && numFloat > 0f) {
                        return (numFloat * 60).toInt()
                    }
                }
            }
            
            // Otherwise, try to extract minutes
            val digits = durationStr.filter { it.isDigit() }
            val durationInt = digits.toIntOrNull()
            if (durationInt != null && durationInt > 0) {
                return durationInt
            }
        }
        return 15
    }

    private fun getTaskRemindersInMinutes(description: String): List<Int> {
        val metaRemindersPattern = Regex("""\[Reminders: ([^\]]+)\]""")
        val match = metaRemindersPattern.find(description) ?: return emptyList()
        val content = match.groupValues[1]
        return content.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "None" }
            .mapNotNull { parseReminderStringToMinutes(it) }
    }

    private fun parseReminderStringToMinutes(reminderStr: String): Int? {
        val clean = reminderStr.lowercase().replace(" before", "").trim()
        val parts = clean.split(" ")
        if (parts.size < 2) return null
        val num = parts[0].toIntOrNull() ?: return null
        val unit = parts[1]
        return when {
            unit.startsWith("min") -> num
            unit.startsWith("hour") -> num * 60
            unit.startsWith("day") -> num * 24 * 60
            else -> null
        }
    }

    private fun formatMinutesToReminderString(minutes: Int): String {
        return when {
            minutes == 0 -> "At time of event"
            minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} days before"
            minutes % 60 == 0 -> "${minutes / 60} hours before"
            else -> "$minutes minutes before"
        }
    }
}


// ==================== CONSOLIDATED FROM: GoogleContactsSyncManager.kt ====================
object GoogleContactsSyncManager {
    private const val TAG = "GoogleContactsSync"
    private const val CONTACTS_SCOPE = "oauth2:https://www.googleapis.com/auth/contacts"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_contacts_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email ?: "cabharathikrishan@gmail.com"
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, CONTACTS_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Contacts scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Contacts: ${e.message}", e)
            null
        }
    }

    /**
     * Performs a full 2-way sync:
     * 1. Pulls contacts from Google Contacts and updates/creates them locally.
     * 2. Pushes local contacts that are new or updated to Google Contacts.
     */
    suspend fun syncContacts(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val database = AppDatabase.getInstance(context)
            val contactDao = database.contactDao()
            val localContacts = contactDao.getAllContacts().first()

            // Fetch Google Contact Groups (labels)
            val groupMap = fetchGoogleContactGroups(token).toMutableMap()
            val groupNameToResource = groupMap.entries.associate { it.value to it.key }.toMutableMap()

            // ---- STEP 1: PULL FROM GOOGLE ----
            val googleContacts = fetchGoogleConnections(token, groupMap)
            val googleIdToConnection = googleContacts.associateBy { it.resourceName }

            // Track if any new folders were fetched
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val currentFolders = prefs.getStringSet("contact_folders_set", emptySet())?.toMutableSet() ?: mutableSetOf()
            var foldersChanged = false

            for (gContact in googleContacts) {
                if (gContact.folder != "All" && !currentFolders.contains(gContact.folder)) {
                    currentFolders.add(gContact.folder)
                    foldersChanged = true
                }

                // Try to find matching local contact by googleContactId or fallback to names
                val matchedLocal = localContacts.find { it.googleContactId == gContact.resourceName }
                    ?: localContacts.find { 
                        gContact.firstName.isNotEmpty() &&
                        it.firstName.lowercase().trim() == gContact.firstName.lowercase().trim() &&
                        it.lastName.lowercase().trim() == gContact.lastName.lowercase().trim()
                    }

                if (matchedLocal != null) {
                    // Update existing local contact
                    val updated = matchedLocal.copy(
                        firstName = if (gContact.firstName.isNotEmpty()) gContact.firstName else matchedLocal.firstName,
                        middleName = if (gContact.middleName.isNotEmpty()) gContact.middleName else matchedLocal.middleName,
                        lastName = if (gContact.lastName.isNotEmpty()) gContact.lastName else matchedLocal.lastName,
                        phone = if (gContact.phone.isNotEmpty()) gContact.phone else matchedLocal.phone,
                        email = if (gContact.email.isNotEmpty()) gContact.email else matchedLocal.email,
                        address = if (gContact.address.isNotEmpty()) gContact.address else matchedLocal.address,
                        jobTitle = if (gContact.jobTitle.isNotEmpty()) gContact.jobTitle else matchedLocal.jobTitle,
                        dobString = if (gContact.dobString.isNotEmpty()) gContact.dobString else matchedLocal.dobString,
                        photoUri = if (!gContact.photoUrl.isNullOrEmpty()) gContact.photoUrl else matchedLocal.photoUri,
                        anniversaryString = if (gContact.anniversaryString.isNotEmpty()) gContact.anniversaryString else matchedLocal.anniversaryString,
                        additionalDatesJson = if (gContact.additionalDatesJson.isNotEmpty()) gContact.additionalDatesJson else matchedLocal.additionalDatesJson,
                        folder = gContact.folder, // Fully synchronized label/folder
                        googleContactId = gContact.resourceName
                    )
                    contactDao.updateContact(updated)
                } else {
                    // Create new local contact (including name, phone, email, address, job title, dob, profile pic, and dates)
                    val newContact = Contact(
                        firstName = gContact.firstName,
                        middleName = gContact.middleName,
                        lastName = gContact.lastName,
                        phone = gContact.phone,
                        dobString = gContact.dobString,
                        photoUri = gContact.photoUrl,
                        email = gContact.email,
                        address = gContact.address,
                        jobTitle = gContact.jobTitle,
                        anniversaryString = gContact.anniversaryString,
                        additionalDatesJson = gContact.additionalDatesJson,
                        folder = gContact.folder, // Fully synchronized label/folder
                        googleContactId = gContact.resourceName
                    )
                    contactDao.insertContact(newContact)
                }
            }

            if (foldersChanged) {
                prefs.edit().putStringSet("contact_folders_set", currentFolders).apply()
            }

            // ---- STEP 2: PUSH TO GOOGLE ----
            // Re-fetch local contacts after Pull updates
            val currentLocalContacts = contactDao.getAllContacts().first()

            for (local in currentLocalContacts) {
                // Determine target group resource name if folder is set
                var targetGroupResourceName: String? = null
                if (local.folder != "All" && local.folder.trim().isNotEmpty()) {
                    val folderName = local.folder.trim()
                    targetGroupResourceName = groupNameToResource[folderName]
                    if (targetGroupResourceName == null) {
                        // Create the group on Google
                        val newGroupRes = createGoogleContactGroup(token, folderName)
                        if (newGroupRes != null) {
                            groupNameToResource[folderName] = newGroupRes
                            groupMap[newGroupRes] = folderName
                            targetGroupResourceName = newGroupRes
                        }
                    }
                }

                if (local.googleContactId != null) {
                    // It was already synced. Let's see if it still exists on Google
                    val existsOnGoogle = googleIdToConnection.containsKey(local.googleContactId)
                    if (existsOnGoogle) {
                        // Let's update Google if local info is different
                        val gContact = googleIdToConnection[local.googleContactId]!!
                        if (local.firstName != gContact.firstName ||
                            local.middleName != gContact.middleName ||
                            local.lastName != gContact.lastName ||
                            local.phone != gContact.phone ||
                            local.email != gContact.email ||
                            local.address != gContact.address ||
                            local.jobTitle != gContact.jobTitle ||
                            local.dobString != gContact.dobString ||
                            local.anniversaryString != gContact.anniversaryString ||
                            local.additionalDatesJson != gContact.additionalDatesJson ||
                            local.folder != gContact.folder
                        ) {
                            updateGoogleContact(token, local, targetGroupResourceName)
                        }
                    } else {
                        // It was deleted on Google, so we can clear the googleContactId
                        contactDao.updateContact(local.copy(googleContactId = null))
                    }
                } else {
                    // No Google Contact ID -> This is a new local contact! Create on Google.
                    val newGoogleId = createGoogleContact(token, local, targetGroupResourceName)
                    if (newGoogleId != null) {
                        val updatedLocal = local.copy(googleContactId = newGoogleId)
                        contactDao.updateContact(updatedLocal)

                        // If local contact has a profile pic, upload it to Google Contacts!
                        if (!local.photoUri.isNullOrEmpty()) {
                            uploadGoogleContactPhoto(context, token, newGoogleId, local.photoUri)
                        }
                    }
                }
            }

            Pair(true, "Successfully completed 2-way sync with Google Contacts (${googleContacts.size} Google contacts synced).")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error performing Google Contacts 2-way sync: ${e.message}", e)
            Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun sanitizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }

    private data class GoogleContactDetails(
        val resourceName: String,
        val etag: String,
        val firstName: String,
        val lastName: String,
        val middleName: String,
        val phone: String,
        val dobString: String,
        val photoUrl: String?,
        val email: String,
        val address: String,
        val jobTitle: String,
        val anniversaryString: String,
        val additionalDatesJson: String,
        val folder: String
    )

    private suspend fun fetchGoogleContactGroups(token: String): Map<String, String> {
        val url = "https://people.googleapis.com/v1/contactGroups?pageSize=1000"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val groupMap = mutableMapOf<String, String>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val groups = json.optJSONArray("contactGroups")
                    if (groups != null) {
                        for (i in 0 until groups.length()) {
                            val g = groups.getJSONObject(i)
                            val resourceName = g.optString("resourceName")
                            val name = g.optString("name")
                            if (resourceName.isNotEmpty() && name.isNotEmpty()) {
                                groupMap[resourceName] = name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch contact groups", e)
        }
        return groupMap
    }

    private suspend fun createGoogleContactGroup(token: String, groupName: String): String? {
        val url = "https://people.googleapis.com/v1/contactGroups"
        val payload = JSONObject().apply {
            put("contactGroup", JSONObject().apply {
                put("name", groupName)
            })
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val groupObj = json.optJSONObject("contactGroup")
                    if (groupObj != null) {
                        return groupObj.optString("resourceName")
                    }
                } else {
                    Log.e(TAG, "Failed to create contact group '$groupName': code=${response.code}, body=${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating contact group '$groupName'", e)
        }
        return null
    }

    private suspend fun fetchGoogleConnections(token: String, groupMap: Map<String, String>): List<GoogleContactDetails> {
        val url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,phoneNumbers,birthdays,photos,emailAddresses,addresses,organizations,events,memberships&pageSize=1000"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val list = mutableListOf<GoogleContactDetails>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch connections: code=${response.code}, msg=${response.message}")
                return emptyList()
            }
            val bodyStr = response.body?.string() ?: ""
            Log.d(TAG, "fetchGoogleConnections Response JSON: $bodyStr")
            val json = JSONObject(bodyStr)
            val connections = json.optJSONArray("connections") ?: return emptyList()

            for (i in 0 until connections.length()) {
                val conn = connections.getJSONObject(i)
                val resourceName = conn.optString("resourceName")
                val etag = conn.optString("etag")

                // 2. Phone parsing
                var phone = ""
                val phoneNumbers = conn.optJSONArray("phoneNumbers")
                if (phoneNumbers != null && phoneNumbers.length() > 0) {
                    phone = phoneNumbers.getJSONObject(0).optString("value", "")
                }

                // 5. Email parsing
                var email = ""
                val emailAddresses = conn.optJSONArray("emailAddresses")
                if (emailAddresses != null && emailAddresses.length() > 0) {
                    email = emailAddresses.getJSONObject(0).optString("value", "")
                }

                // 1. Name parsing with display name fallback
                var firstName = ""
                var lastName = ""
                var middleName = ""
                val names = conn.optJSONArray("names")
                if (names != null && names.length() > 0) {
                    val nameObj = names.getJSONObject(0)
                    firstName = nameObj.optString("givenName", "").trim()
                    lastName = nameObj.optString("familyName", "").trim()
                    middleName = nameObj.optString("middleName", "").trim()
                    
                    if (firstName.isEmpty() && lastName.isEmpty()) {
                        val displayName = nameObj.optString("displayName", "").trim()
                        if (displayName.isNotEmpty()) {
                            val parts = displayName.split(" ", limit = 2)
                            firstName = parts.first()
                            lastName = parts.getOrNull(1) ?: ""
                        }
                    }
                }

                if (firstName.isEmpty() && lastName.isEmpty()) {
                    if (phone.isNotEmpty()) {
                        firstName = phone
                    } else if (email.isNotEmpty()) {
                        firstName = email.substringBefore("@")
                    } else {
                        firstName = "Unnamed Google Contact"
                    }
                }

                // 3. Birthday parsing with text fallback
                var dobString = ""
                val birthdays = conn.optJSONArray("birthdays")
                if (birthdays != null && birthdays.length() > 0) {
                    val bdayObj = birthdays.getJSONObject(0)
                    val dateObj = bdayObj.optJSONObject("date")
                    if (dateObj != null) {
                        val y = dateObj.optInt("year", 0)
                        val m = dateObj.optInt("month", 0)
                        val d = dateObj.optInt("day", 0)
                        if (y > 0 && m > 0 && d > 0) {
                            dobString = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                        } else if (m > 0 && d > 0) {
                            dobString = String.format(Locale.US, "%02d-%02d", m, d)
                        }
                    } else {
                        val text = bdayObj.optString("text", "").trim()
                        if (text.isNotEmpty()) {
                            dobString = text
                        }
                    }
                }

                // 4. Photo parsing (always extract the URL if present)
                var photoUrl: String? = null
                val photos = conn.optJSONArray("photos")
                if (photos != null && photos.length() > 0) {
                    photoUrl = photos.getJSONObject(0).optString("url")
                }

                // 6. Address parsing
                var address = ""
                val addresses = conn.optJSONArray("addresses")
                if (addresses != null && addresses.length() > 0) {
                    address = addresses.getJSONObject(0).optString("formattedValue", "")
                }

                // 7. Job Title parsing
                var jobTitle = ""
                val organizations = conn.optJSONArray("organizations")
                if (organizations != null && organizations.length() > 0) {
                    jobTitle = organizations.getJSONObject(0).optString("title", "")
                }

                // 8. Anniversary and other dates parsing
                var anniversaryString = ""
                val additionalDatesList = mutableListOf<String>()
                val events = conn.optJSONArray("events")
                if (events != null) {
                    for (j in 0 until events.length()) {
                        val eventObj = events.getJSONObject(j)
                        val type = eventObj.optString("type", "")
                        val formattedType = eventObj.optString("formattedType", type.replaceFirstChar { it.uppercase() })
                        val dateObj = eventObj.optJSONObject("date")
                        var dateStr = ""
                        if (dateObj != null) {
                            val y = dateObj.optInt("year", 0)
                            val m = dateObj.optInt("month", 0)
                            val d = dateObj.optInt("day", 0)
                            if (y > 0 && m > 0 && d > 0) {
                                dateStr = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                            } else if (m > 0 && d > 0) {
                                dateStr = String.format(Locale.US, "%02d-%02d", m, d)
                            }
                        } else {
                            dateStr = eventObj.optString("text", "").trim()
                        }

                        if (dateStr.isNotEmpty()) {
                            if (type == "anniversary") {
                                anniversaryString = dateStr
                            } else {
                                val label = if (formattedType.isNotEmpty()) formattedType else "Event"
                                additionalDatesList.add("$label:$dateStr")
                            }
                        }
                    }
                }
                val additionalDatesJson = additionalDatesList.joinToString(";")

                // 9. Membership parsing for group/label mapping to folder
                var folder = "All"
                val memberships = conn.optJSONArray("memberships")
                if (memberships != null) {
                    for (m in 0 until memberships.length()) {
                        val mObj = memberships.getJSONObject(m)
                        val cgMembership = mObj.optJSONObject("contactGroupMembership")
                        if (cgMembership != null) {
                            val cgResName = cgMembership.optString("contactGroupResourceName")
                            val mappedGroupName = groupMap[cgResName]
                            if (!mappedGroupName.isNullOrEmpty() && mappedGroupName != "myContacts" && mappedGroupName != "starred") {
                                folder = mappedGroupName
                                break
                            }
                        }
                    }
                }

                if (resourceName.isNotEmpty()) {
                    list.add(
                        GoogleContactDetails(
                            resourceName = resourceName,
                            etag = etag,
                            firstName = firstName,
                            lastName = lastName,
                            middleName = middleName,
                            phone = phone,
                            dobString = dobString,
                            photoUrl = photoUrl,
                            email = email,
                            address = address,
                            jobTitle = jobTitle,
                            anniversaryString = anniversaryString,
                            additionalDatesJson = additionalDatesJson,
                            folder = folder
                        )
                    )
                }
            }
        }
        return list
    }

    private suspend fun createGoogleContact(token: String, contact: Contact, groupResourceName: String?): String? {
        val url = "https://people.googleapis.com/v1/people/createContact"
        val payload = buildContactPayload(contact, groupResourceName)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("resourceName")
            } else {
                Log.e(TAG, "Failed to create contact on Google: code=${response.code}, body=${response.body?.string()}")
            }
        }
        return null
    }

    private suspend fun updateGoogleContact(token: String, contact: Contact, groupResourceName: String?): Boolean {
        val resourceName = contact.googleContactId ?: return false
        val etag = getEtag(token, resourceName) ?: return false

        val url = "https://people.googleapis.com/v1/$resourceName?updatePersonFields=names,phoneNumbers,birthdays,emailAddresses,addresses,organizations,events,memberships"
        val payload = buildContactPayload(contact, groupResourceName).apply {
            put("etag", etag)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return true
            } else {
                Log.e(TAG, "Failed to update contact on Google: code=${response.code}, body=${response.body?.string()}")
            }
        }
        return false
    }

    private suspend fun uploadGoogleContactPhoto(context: Context, token: String, resourceName: String, photoUriStr: String): Boolean {
        try {
            val uri = Uri.parse(photoUriStr)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bytes = inputStream.readBytes()
                inputStream.close()
                val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val url = "https://people.googleapis.com/v1/$resourceName:updateContactPhoto"
                val payload = JSONObject().apply {
                    put("photoBytes", base64Str)
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    return response.isSuccessful
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload contact photo: ${e.message}")
        }
        return false
    }

    private suspend fun getEtag(token: String, resourceName: String): String? {
        val request = Request.Builder()
            .url("https://people.googleapis.com/v1/$resourceName?personFields=metadata")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("etag")
            }
        }
        return null
    }

    private fun buildContactPayload(contact: Contact, groupResourceName: String?): JSONObject {
        val payload = JSONObject()

        val namesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("givenName", contact.firstName)
                put("familyName", contact.lastName)
                if (contact.middleName.isNotEmpty()) {
                    put("middleName", contact.middleName)
                }
            })
        }
        payload.put("names", namesArray)

        if (contact.phone.isNotEmpty()) {
            val phoneArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("value", contact.phone)
                    put("type", "mobile")
                })
            }
            payload.put("phoneNumbers", phoneArray)
        }

        if (contact.email.isNotEmpty()) {
            val emailArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("value", contact.email)
                    put("type", "home")
                })
            }
            payload.put("emailAddresses", emailArray)
        }

        if (contact.address.isNotEmpty()) {
            val addressArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("streetAddress", contact.address)
                    put("type", "home")
                })
            }
            payload.put("addresses", addressArray)
        }

        if (contact.jobTitle.isNotEmpty()) {
            val orgArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("title", contact.jobTitle)
                })
            }
            payload.put("organizations", orgArray)
        }

        if (contact.dobString.isNotEmpty()) {
            val dobStr = contact.dobString
            val parts = dobStr.split("-")
            val dateObj = JSONObject()
            if (parts.size == 3) {
                dateObj.put("year", parts[0].toIntOrNull() ?: 0)
                dateObj.put("month", parts[1].toIntOrNull() ?: 0)
                dateObj.put("day", parts[2].toIntOrNull() ?: 0)
            } else if (parts.size == 2) {
                dateObj.put("month", parts[0].toIntOrNull() ?: 0)
                dateObj.put("day", parts[1].toIntOrNull() ?: 0)
            }
            if (dateObj.length() > 0) {
                val birthdaysArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("date", dateObj)
                    })
                }
                payload.put("birthdays", birthdaysArray)
            }
        }

        val eventsArray = JSONArray()
        if (contact.anniversaryString.isNotEmpty()) {
            val annStr = contact.anniversaryString
            val parts = annStr.split("-")
            val dateObj = JSONObject()
            if (parts.size == 3) {
                dateObj.put("year", parts[0].toIntOrNull() ?: 0)
                dateObj.put("month", parts[1].toIntOrNull() ?: 0)
                dateObj.put("day", parts[2].toIntOrNull() ?: 0)
            } else if (parts.size == 2) {
                dateObj.put("month", parts[0].toIntOrNull() ?: 0)
                dateObj.put("day", parts[1].toIntOrNull() ?: 0)
            }
            if (dateObj.length() > 0) {
                eventsArray.put(JSONObject().apply {
                    put("type", "anniversary")
                    put("date", dateObj)
                })
            }
        }

        if (contact.additionalDatesJson.isNotEmpty()) {
            contact.additionalDatesJson.split(";").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val label = parts[0]
                    val dateVal = parts[1]
                    val dateParts = dateVal.split("-")
                    val dateObj = JSONObject()
                    if (dateParts.size == 3) {
                        dateObj.put("year", dateParts[0].toIntOrNull() ?: 0)
                        dateObj.put("month", dateParts[1].toIntOrNull() ?: 0)
                        dateObj.put("day", dateParts[2].toIntOrNull() ?: 0)
                    } else if (dateParts.size == 2) {
                        dateObj.put("month", dateParts[0].toIntOrNull() ?: 0)
                        dateObj.put("day", dateParts[1].toIntOrNull() ?: 0)
                    }
                    if (dateObj.length() > 0) {
                        eventsArray.put(JSONObject().apply {
                            put("type", "other")
                            put("formattedType", label)
                            put("date", dateObj)
                        })
                    }
                }
            }
        }

        if (eventsArray.length() > 0) {
            payload.put("events", eventsArray)
        }

        val membershipsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("contactGroupMembership", JSONObject().apply {
                    put("contactGroupResourceName", "contactGroups/myContacts")
                })
            })
            if (!groupResourceName.isNullOrEmpty() && groupResourceName != "contactGroups/myContacts") {
                put(JSONObject().apply {
                    put("contactGroupMembership", JSONObject().apply {
                        put("contactGroupResourceName", groupResourceName)
                    })
                })
            }
        }
        payload.put("memberships", membershipsArray)

        return payload
    }
}


// ==================== CONSOLIDATED FROM: GoogleDriveSyncManager.kt ====================
object GoogleDriveSyncManager {
    private val driveMutex = Mutex()
    private const val TAG = "GoogleDriveSync"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
    private const val BACKUP_FILE_NAME = "focus_backup.json"
    private const val ALL_DATA_BACKUP_FILE_NAME = "app_data_backup.zip"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Obtains the OAuth2 access token for the signed-in Google account.
     * If authentication resolution is required (e.g. user needs to approve permission),
     * [onAuthResolutionRequired] is invoked with the required Intent.
     */
    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_file_backup_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token: ${e.message}", e)
            null
        }
    }

    /**
     * Checks whether the user has signed in and granted the Drive AppData scope.
     */
    fun hasDrivePermission(context: Context): Boolean {
        val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, driveScope)
    }

    /**
     * Deletes a file from Google Drive.
     */
    suspend fun deleteGoogleDriveFile(context: Context, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken(context) ?: return@withContext false
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully deleted file $fileId from Google Drive.")
                    true
                } else {
                    Log.e(TAG, "Failed to delete file $fileId from Google Drive: code=${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file $fileId from Google Drive", e)
            false
        }
    }

    /**
     * Renames a file on Google Drive.
     */
    suspend fun renameGoogleDriveFile(context: Context, fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken(context) ?: return@withContext false
            val json = org.json.JSONObject().apply {
                put("name", newName)
            }
            val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully renamed file $fileId to '$newName' on Google Drive.")
                    true
                } else {
                    Log.e(TAG, "Failed to rename file $fileId on Google Drive: code=${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file $fileId on Google Drive", e)
            false
        }
    }

    /**
     * Performs a backup of focus-related data (focus records list, total focus minutes, today's pomos count)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Load local focus records
                val localRecords = FocusTimerManager.loadFocusRecords(context)
                
                // 2. Load total stats
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val totalMinutes = prefs.getInt("total_focus_minutes", 0)
                val pomosCount = prefs.getInt("today_pomos_count", 0)

                // 3. Serialize records
                val serializedRecords = serializeFocusRecords(localRecords)
                val backupJson = JSONObject().apply {
                    put("focus_records_list", serializedRecords)
                    put("total_focus_minutes", totalMinutes)
                    put("today_pomos_count", pomosCount)
                }

                // 4. Find if backup file already exists in AppData
                var fileId = findBackupFileId(token)
                if (fileId == null) {
                    fileId = createBackupFileMetadata(token)
                    if (fileId == null) {
                        return@withLock Pair(false, "Failed to initialize backup slot on Google Drive.")
                    }
                }

                // 5. Upload content to Google Drive
                val success = uploadBackupFileContent(token, fileId, backupJson.toString())
                if (success) {
                    prefs.edit().putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully backed up focus data to Google Drive.")
                } else {
                    Pair(false, "Failed to upload focus backup to Google Drive.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up focus data: ${e.message}", e)
                Pair(false, "Backup Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Retrieves focus-related data from Google Drive AppData folder,
     * reconciles and merges it with current local focus history (avoiding duplicates),
     * and restores the state.
     */
    suspend fun restoreFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Find the backup file in AppData folder
                val fileId = findBackupFileId(token)
                    ?: return@withLock Pair(false, "No backup file found on your Google Drive. Save a backup first.")

                // 2. Download backup content
                val contentStr = downloadBackupFileContent(token, fileId)
                    ?: return@withLock Pair(false, "Failed to read backup from Google Drive.")

                val backupJson = JSONObject(contentStr)
                val remoteSerializedRecords = backupJson.optString("focus_records_list", "")
                val remoteTotalMinutes = backupJson.optInt("total_focus_minutes", 0)
                val remotePomosCount = backupJson.optInt("today_pomos_count", 0)

                // 3. Load local focus records
                val localRecords = FocusTimerManager.loadFocusRecords(context)
                
                // Parse remote records from the serialized string
                val remoteRecords = parseSerializedFocusRecords(remoteSerializedRecords)

                // 4. Reconciliation: Merge lists and keep unique records (using unique key combination)
                val mergedRecords = (localRecords + remoteRecords).distinctBy { record ->
                    "${record.startTime}_${record.endTime}_${record.taskTitle}_${record.durationSeconds}"
                }

                // 5. Update local storage
                FocusTimerManager.saveFocusRecords(context, mergedRecords)
                
                // Overwrite total stats with max values or merged values to preserve progress
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val finalTotalMinutes = maxOf(prefs.getInt("total_focus_minutes", 0), remoteTotalMinutes, mergedRecords.sumOf { it.durationMinutes })
                val finalPomosCount = maxOf(prefs.getInt("today_pomos_count", 0), remotePomosCount)

                prefs.edit().apply {
                    putInt("total_focus_minutes", finalTotalMinutes)
                    putInt("today_pomos_count", finalPomosCount)
                    putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis())
                    apply()
                }

                // 6. Update FocusTimerManager live states on main thread
                withContext(Dispatchers.Main) {
                    FocusTimerManager.setFocusRecords(mergedRecords)
                    FocusTimerManager.setTotalFocusMinutes(finalTotalMinutes)
                    FocusTimerManager.setTodayPomosCount(finalPomosCount)
                }

                Pair(true, "Successfully restored and merged ${remoteRecords.size} focus records from Google Drive!")
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error restoring focus data: ${e.message}", e)
                Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Performs a backup of the entire app database and attachment files (ZIP package)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Create a temp file to hold our zip backup
                val tempFile = java.io.File(context.cacheDir, "temp_app_data_backup.zip")
                if (tempFile.exists()) tempFile.delete()

                // 2. Export database and files to the temp zip file
                val exportSuccess = tempFile.outputStream().use { fos ->
                    DatabaseBackupHelper.exportDataToStream(context, database, fos)
                }

                if (!exportSuccess) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withLock Pair(false, "Failed to compile backup package locally.")
                }

                // 3. Ensure vault structure & README exist
                val vault = ensureVaultStructureAndReadme(token)
                val targetFolderId = vault?.backupsId

                var fileId = if (targetFolderId != null) findFileInFolder(token, ALL_DATA_BACKUP_FILE_NAME, targetFolderId) else findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
                if (fileId == null) {
                    fileId = if (targetFolderId != null) createFileMetadataInFolder(token, ALL_DATA_BACKUP_FILE_NAME, targetFolderId) else createFileMetadata(token, ALL_DATA_BACKUP_FILE_NAME)
                    if (fileId == null) {
                        tempFile.delete()
                        return@withLock Pair(false, "Failed to initialize backup slot in Google Drive.")
                    }
                }

                // 4. Upload the zip binary
                val requestBody = tempFile.asRequestBody("application/zip".toMediaType())
                val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/zip")
                    .patch(requestBody)
                    .build()

                var uploadSuccess = false
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        uploadSuccess = true
                    } else {
                        Log.e(TAG, "Error uploading zip: code=${response.code} body=${response.body?.string()}")
                    }
                }

                // Clean up local temp file
                tempFile.delete()

                if (uploadSuccess) {
                    // Perform deduplication to purge any older duplicates in backup folder
                    if (targetFolderId != null) {
                        deleteOlderDuplicateFiles(token, targetFolderId, ALL_DATA_BACKUP_FILE_NAME, keepLatestId = fileId)
                    }
                    makeFilePublic(token, fileId)
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully backed up all app data and files to Google Drive (LifeOS_Cloud_Vault/App_Backups).")
                } else {
                    Pair(false, "Failed to upload backup package to Google Drive.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error backing up all app data", e)
                Pair(false, "Backup Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Downloads and restores the entire app database and attachment files (ZIP package)
     * from the user's hidden Google Drive AppData folder.
     */
    suspend fun restoreAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Find the file ID in Google Drive vault or fallback
                val vault = ensureVaultStructureAndReadme(token)
                val fileId = (if (vault != null) findFileInFolder(token, ALL_DATA_BACKUP_FILE_NAME, vault.backupsId) else null)
                    ?: findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
                    ?: return@withLock Pair(false, "No full app data backup found on Google Drive. Save a backup first.")

                // 2. Download zip content
                val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val tempFile = java.io.File(context.cacheDir, "temp_app_data_restore.zip")
                if (tempFile.exists()) tempFile.delete()

                var downloadSuccess = false
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        downloadSuccess = true
                    } else {
                        Log.e(TAG, "Failed downloading zip backup: code=${response.code}")
                    }
                }

                if (!downloadSuccess) {
                    tempFile.delete()
                    return@withLock Pair(false, "Failed to download backup package from Google Drive.")
                }

                // 3. Import data from temp zip file
                val importSuccess = tempFile.inputStream().use { fis ->
                    DatabaseBackupHelper.importDataFromStream(context, database, fis)
                }

                tempFile.delete()

                if (importSuccess) {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully restored all app data and files from Google Drive!")
                } else {
                    Pair(false, "Failed to restore downloaded backup package.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error restoring all app data", e)
                Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Queries Google Drive to find the size of the uploaded backups.
     * Returns a map of file name to size in bytes.
     */
    suspend fun getBackupSizes(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context) ?: return@withContext emptyMap()
        val result = mutableMapOf<String, Long>()
        try {
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val filesArray = JSONObject(bodyStr).optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val fileObj = filesArray.getJSONObject(i)
                            val name = fileObj.optString("name", "")
                            val size = fileObj.optLong("size", 0L)
                            if (name.isNotEmpty()) {
                                result[name] = size
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching backup sizes from Google Drive: ${e.message}", e)
        }
        result
    }

    /**
     * Checks if any backup data exists in Google Drive.
     */
    suspend fun hasExistingBackupData(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken(context) ?: return@withContext false
        try {
            val focusId = findFileId(token, BACKUP_FILE_NAME)
            val dbId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
            focusId != null || dbId != null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing backup data", e)
            false
        }
    }

    /**
     * Reconciles/Retrieves whichever backup files exist in Google Drive.
     */
    suspend fun checkAndRetrieveDriveData(context: Context, database: com.example.data.AppDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
            ?: return@withContext Pair(false, "Authentication required.")
        
        try {
            val focusId = findFileId(token, BACKUP_FILE_NAME)
            val dbId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
            
            if (focusId == null && dbId == null) {
                return@withContext Pair(false, "No existing backup files found.")
            }
            
            val results = mutableListOf<String>()
            var anySuccess = false
            
            if (dbId != null) {
                val (success, msg) = restoreAllAppData(context, database)
                if (success) {
                    anySuccess = true
                    results.add("App database restored.")
                } else {
                    results.add("App database restore failed: $msg")
                }
            }
            
            if (focusId != null) {
                val (success, msg) = restoreFocusData(context)
                if (success) {
                    anySuccess = true
                    results.add("Focus data restored.")
                } else {
                    results.add("Focus data restore failed: $msg")
                }
            }
            
            Pair(anySuccess, results.joinToString("\n"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndRetrieveDriveData", e)
            Pair(false, e.localizedMessage ?: "Unknown restore error.")
        }
    }

    /**
     * Searches for 'focus_backup.json' in the AppData folder.
     * Returns its fileId or null if not found.
     */
    private fun findBackupFileId(accessToken: String): String? {
        return findFileId(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Creates empty file metadata for 'focus_backup.json' in 'appDataFolder'.
     * Returns the created fileId or null.
     */
    private fun createBackupFileMetadata(accessToken: String): String? {
        return createFileMetadata(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Generic file finder inside Google Drive appDataFolder.
     */
    private fun findFileId(accessToken: String, fileName: String): String? {
        val query = "name = '$fileName'"
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "URLEncode failed", e)
            return null
        }
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error listing files: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive API Error (HTTP ${response.code}): $bodyStr")
            }
            val filesArray = JSONObject(bodyStr).getJSONArray("files")
            if (filesArray.length() > 0) {
                return filesArray.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    /**
     * Generic file metadata creator inside Google Drive appDataFolder.
     */
    private fun createFileMetadata(accessToken: String, fileName: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val bodyJson = JSONObject().apply {
            put("name", fileName)
            val parentsArray = org.json.JSONArray().apply {
                put("appDataFolder")
            }
            put("parents", parentsArray)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error creating metadata: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive Creation Error (HTTP ${response.code}): $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Uploads/Overwrites the file content using PATCH.
     */
    private fun uploadBackupFileContent(accessToken: String, fileId: String, content: String): Boolean {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .patch(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true
            }
            Log.e(TAG, "Error uploading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Upload Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Downloads file content from Google Drive.
     */
    private fun downloadBackupFileContent(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return bodyStr
            }
            Log.e(TAG, "Error downloading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Download Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Parsed serialized string back to FocusRecord list.
     */
    private fun parseSerializedFocusRecords(serialized: String): List<com.example.ui.FocusRecord> {
        if (serialized.isBlank()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    
                    val durationMins = if (originalMins > 720) 720 else originalMins
                    val durationSecs = if (originalSecs > 43200) 43200 else originalSecs

                    com.example.ui.FocusRecord(parts[0], parts[1], parts[2], durationMins, dateValue, notesValue, durationSecs)
                } else null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing serialized focus records: ${e.message}")
            emptyList()
        }
    }

    /**
     * Serializes FocusRecord list to a structured pipe-delimited string.
     */
    private fun serializeFocusRecords(records: List<com.example.ui.FocusRecord>): String {
        return records.joinToString("\n") { r ->
            val encodedNotes = try {
                android.util.Base64.encodeToString(r.notes.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
            "${r.startTime}|${r.endTime}|${r.taskTitle}|${r.durationMinutes}|${r.dateString}|$encodedNotes|${r.durationSeconds}"
        }
    }

    /**
     * Uploads a physical media file to Google Drive under the organized LifeOS_Cloud_Vault folder structure
     * (e.g. Task_Attachments, Shared_Media, General_Files), sets public permissions, cleans up older duplicate files,
     * and returns the direct Google Drive sharing URL.
     */
    suspend fun uploadPublicMediaFileDirect(
        context: Context,
        accessToken: String,
        file: java.io.File,
        categoryFolder: String = "Task_Attachments"
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure vault structure and README file exist in user's Drive
            val vault = ensureVaultStructureAndReadme(accessToken)
            val targetFolderId = when (categoryFolder) {
                "App_Backups" -> vault?.backupsId
                "Shared_Media" -> vault?.sharedMediaId
                "General_Files" -> vault?.generalFilesId
                else -> vault?.taskAttachmentsId
            } ?: findOrCreateSharedFolder(accessToken, "LifeOS_Shared_Media") ?: return@withContext null

            // 2. Check if file already exists or create new metadata slot
            val existingFileId = findFileInFolder(accessToken, file.name, targetFolderId)
            val fileId = if (existingFileId != null) {
                val mimeType = getMimeType(file)
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", mimeType)
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed updating file content for ${file.name}: code=${response.code}")
                        return@withContext null
                    }
                }
                existingFileId
            } else {
                val createdId = createFileMetadataInFolder(accessToken, file.name, targetFolderId) ?: return@withContext null
                val mimeType = getMimeType(file)
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", mimeType)
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed uploading public file content for ${file.name}: code=${response.code}")
                        return@withContext null
                    }
                }
                createdId
            }

            // 3. Purge older duplicate files in this subfolder to keep ONLY the latest
            deleteOlderDuplicateFiles(accessToken, targetFolderId, file.name, keepLatestId = fileId)

            // 4. Make file public
            makeFilePublic(accessToken, fileId)

            // 5. Return direct download link
            "https://drive.google.com/uc?export=download&id=$fileId"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading public media file ${file.name}", e)
            null
        }
    }

    data class VaultFolders(
        val rootId: String,
        val backupsId: String,
        val taskAttachmentsId: String,
        val sharedMediaId: String,
        val generalFilesId: String
    )

    fun ensureVaultStructureAndReadme(token: String): VaultFolders? {
        val rootId = findOrCreateSharedFolder(token, "LifeOS_Cloud_Vault") ?: return null
        val backupsId = findOrCreateSubFolderInParent(token, rootId, "App_Backups") ?: rootId
        val taskAttachmentsId = findOrCreateSubFolderInParent(token, rootId, "Task_Attachments") ?: rootId
        val sharedMediaId = findOrCreateSubFolderInParent(token, rootId, "Shared_Media") ?: rootId
        val generalFilesId = findOrCreateSubFolderInParent(token, rootId, "General_Files") ?: rootId

        ensureReadmeFile(token, rootId)

        return VaultFolders(
            rootId = rootId,
            backupsId = backupsId,
            taskAttachmentsId = taskAttachmentsId,
            sharedMediaId = sharedMediaId,
            generalFilesId = generalFilesId
        )
    }

    private fun findOrCreateSubFolderInParent(token: String, parentId: String, subFolderName: String): String? {
        try {
            val query = "name = '$subFolderName' and '$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    if (files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }

            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", subFolderName)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", org.json.JSONArray().apply { put(parentId) })
            }
            val createRequest = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return JSONObject(response.body?.string() ?: "").getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findOrCreateSubFolderInParent $subFolderName", e)
        }
        return null
    }

    private fun ensureReadmeFile(token: String, rootFolderId: String) {
        try {
            val readmeName = "README_DO_NOT_DELETE.txt"
            val existingId = findFileInFolder(token, readmeName, rootFolderId)

            val readmeContent = """
===================================================================
⚠️ CRITICAL NOTICE: DO NOT DELETE OR MODIFY ANYTHING IN THIS FOLDER ⚠️
===================================================================

WHY IS THIS FOLDER STORED HERE?
This Google Drive folder serves as the official cloud synchronization vault, media asset storage,
and disaster recovery backup hub for your LifeOS & Focus App.

ORDERED FOLDER STRUCTURE & WHAT EACH ITEM REPRESENTS:
-------------------------------------------------------------------
1. README_DO_NOT_DELETE.txt
   This manifest file explaining the cloud architecture, folder structure, and safety rules.

2. App_Backups/
   Contains the latest encrypted database & app configuration backup package (lifeos_full_data_backup.zip).
   Only the LATEST backup is preserved; older duplicate backup versions are automatically purged promptly.

3. Task_Attachments/
   Stores all photo, video, audio, and document media attached directly to your tasks.
   Every task with media attachments points directly to these cloud files.

4. Shared_Media/
   Stores images, videos, and media files uploaded during peer messaging, journal entries, or shared sessions.

5. General_Files/
   Stores documents, notes, and user files uploaded through the in-app File Explorer.

CONSEQUENCES OF DELETING OR ALTERING FILES IN THIS FOLDER:
-------------------------------------------------------------------
❌ Deleting media files will break image, video, and document rendering in your tasks and chats.
❌ Deleting App_Backups will prevent cross-device synchronization and data restoration.
❌ Modifying file names manually will corrupt attachment links inside the application.
❌ Removing this directory forces the app to recreate missing structures and re-sync assets.

===================================================================
Managed Automatically by LifeOS Cloud Vault Sync Engine
===================================================================
            """.trimIndent()

            val fileId = existingId ?: createFileMetadataInFolder(token, readmeName, rootFolderId)
            if (fileId != null) {
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val requestBody = readmeContent.toRequestBody("text/plain".toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "text/plain; charset=utf-8")
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().close()
                makeFilePublic(token, fileId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ensureReadmeFile", e)
        }
    }

    fun deleteOlderDuplicateFiles(token: String, folderId: String, fileName: String, keepLatestId: String) {
        try {
            val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id, name)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    for (i in 0 until files.length()) {
                        val fObj = files.getJSONObject(i)
                        val id = fObj.getString("id")
                        if (id != keepLatestId) {
                            deleteGoogleDriveFileDirect(token, id)
                            Log.i(TAG, "Deleted older duplicate file $id ($fileName) from Google Drive subfolder")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteOlderDuplicateFiles", e)
        }
    }

    fun deleteGoogleDriveFileDirect(token: String, fileId: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteMediaByUrlOrName(context: Context, urlOrName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken(context) ?: return@withContext false
            val extractedId = extractIdFromUrl(urlOrName)
            if (!extractedId.isNullOrEmpty()) {
                val success = deleteGoogleDriveFileDirect(token, extractedId)
                if (success) {
                    Log.i(TAG, "Successfully deleted Drive file $extractedId via URL deletion request")
                    return@withContext true
                }
            }

            val fileName = java.io.File(urlOrName).name
            if (fileName.isNotBlank()) {
                val query = "name = '$fileName' and trashed = false"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id, name)"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val files = JSONObject(body).getJSONArray("files")
                        var anyDeleted = false
                        for (i in 0 until files.length()) {
                            val fObj = files.getJSONObject(i)
                            val id = fObj.getString("id")
                            if (deleteGoogleDriveFileDirect(token, id)) {
                                anyDeleted = true
                                Log.i(TAG, "Deleted Drive file $id matching name $fileName")
                            }
                        }
                        return@withContext anyDeleted
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteMediaByUrlOrName for $urlOrName", e)
        }
        return@withContext false
    }

    private fun findOrCreateSharedFolder(token: String, folderName: String): String? {
        try {
            // Find folder
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    if (files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }

            // Create folder
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }
            val createRequest = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return JSONObject(response.body?.string() ?: "").getString("id")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in findOrCreateSharedFolder", e)
        }
        return null
    }

    private fun findFileInFolder(token: String, name: String, folderId: String): String? {
        try {
            val query = "name = '$name' and '$folderId' in parents and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    if (files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in findFileInFolder", e)
        }
        return null
    }

    private fun createFileMetadataInFolder(token: String, name: String, folderId: String): String? {
        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", name)
                val parents = org.json.JSONArray().apply { put(folderId) }
                put("parents", parents)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return JSONObject(response.body?.string() ?: "").getString("id")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in createFileMetadataInFolder", e)
        }
        return null
    }

    fun makeFilePublic(token: String, fileId: String) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions"
            val body = JSONObject().apply {
                put("role", "writer")
                put("type", "anyone")
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to make file $fileId public with role 'writer': code=${response.code}. Falling back to 'reader'.")
                    val fallbackBody = JSONObject().apply {
                        put("role", "reader")
                        put("type", "anyone")
                    }
                    val fallbackRequest = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .post(fallbackBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    client.newCall(fallbackRequest).execute().close()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in makeFilePublic", e)
        }
    }

    fun makeFilePublicAndEditor(token: String, fileId: String) {
        makeFilePublic(token, fileId)
    }

    fun extractIdFromUrl(url: String): String? {
        if (url.contains("folders/")) {
            val part = url.substringAfter("folders/")
            val id = part.substringBefore("?").substringBefore("/")
            if (id.isNotBlank()) return id
        }
        if (url.contains("/d/")) {
            val part = url.substringAfter("/d/")
            val id = part.substringBefore("?").substringBefore("/")
            if (id.isNotBlank()) return id
        }
        if (url.contains("id=")) {
            val part = url.substringAfter("id=")
            val id = part.substringBefore("&").substringBefore("/")
            if (id.isNotBlank()) return id
        }
        return null
    }

    suspend fun makeFolderAndContentsPublicAndEditorRecursive(
        token: String,
        folderId: String,
        collectedItems: MutableList<JSONObject> = mutableListOf(),
        pathPrefix: String = "",
        processedFolderIds: MutableSet<String> = mutableSetOf()
    ): Unit = withContext(Dispatchers.IO) {
        if (processedFolderIds.contains(folderId)) return@withContext
        processedFolderIds.add(folderId)

        // Make this folder public and editor
        makeFilePublicAndEditor(token, folderId)

        try {
            val query = "'$folderId' in parents and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,webViewLink,size)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        val id = fileObj.getString("id")
                        val name = fileObj.optString("name", "")
                        val mimeType = fileObj.optString("mimeType", "")
                        val webViewLink = fileObj.optString("webViewLink", "https://drive.google.com/open?id=$id")
                        val size = fileObj.optLong("size", 0L)

                        val sizeStr = if (size > 0) {
                            if (size > 1024 * 1024) "${String.format("%.1f", size.toDouble() / (1024 * 1024))} MB"
                            else "${size / 1024} KB"
                        } else {
                            "Unknown Size"
                        }

                        // Make file public and editor
                        makeFilePublicAndEditor(token, id)

                        val isFolder = mimeType == "application/vnd.google-apps.folder"

                        val itemJson = JSONObject().apply {
                            put("id", id)
                            put("name", name)
                            put("mimeType", mimeType)
                            put("filetype", mimeType)
                            put("webViewLink", webViewLink)
                            put("sharinglink", webViewLink)
                            put("url", webViewLink)
                            put("size", sizeStr)
                            put("isFolder", isFolder)
                            put("path", if (pathPrefix.isEmpty()) name else "$pathPrefix/$name")
                        }
                        collectedItems.add(itemJson)

                        if (isFolder) {
                            makeFolderAndContentsPublicAndEditorRecursive(
                                token = token,
                                folderId = id,
                                collectedItems = collectedItems,
                                pathPrefix = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name",
                                processedFolderIds = processedFolderIds
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recursive directory traversal for folder $folderId", e)
        }
    }

    suspend fun uploadPublicMediaFileToFolderDirect(
        context: Context,
        accessToken: String,
        file: java.io.File,
        folderId: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val existingFileId = findFileInFolder(accessToken, file.name, folderId)
            val fileId = if (existingFileId != null) {
                existingFileId
            } else {
                val createdId = createFileMetadataInFolder(accessToken, file.name, folderId) ?: return@withContext null
                val mimeType = getMimeType(file)
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", mimeType)
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed uploading public file content to folder for ${file.name}: code=${response.code}")
                        return@withContext null
                    }
                }
                createdId
            }

            makeFilePublicAndEditor(accessToken, fileId)
            "https://drive.google.com/uc?export=download&id=$fileId"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file to folder $folderId", e)
            null
        }
    }

    fun sendNotification(context: Context, title: String, message: String) {
        try {
            val channelId = "google_drive_sync_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Google Drive Sync Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts about Google Drive uploads and link processing"
                }
                notificationManager.createNotificationChannel(channel)
            }
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMimeType(file: java.io.File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    data class GoogleSheetFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val webViewLink: String,
        val size: Long
    )

    data class GoogleDocFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val webViewLink: String,
        val size: Long
    )

    data class GoogleDriveFileItem(
        val id: String,
        val name: String,
        val mimeType: String,
        val modifiedTime: String,
        val webViewLink: String,
        val size: Long,
        val isFolder: Boolean
    )

    suspend fun listGoogleDocs(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GoogleDocFile>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val encodedQuery = java.net.URLEncoder.encode("mimeType='application/vnd.google-apps.document' and trashed=false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,modifiedTime,webViewLink,size)&orderBy=modifiedTime%20desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to list Google Docs: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()
                val docsList = mutableListOf<GoogleDocFile>()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val id = fileObj.optString("id", "")
                    val name = fileObj.optString("name", "Untitled Document")
                    val modifiedTime = fileObj.optString("modifiedTime", "")
                    val webViewLink = fileObj.optString("webViewLink", "https://docs.google.com/document/d/$id/edit")
                    val size = fileObj.optLong("size", 0L)
                    docsList.add(GoogleDocFile(id, name, modifiedTime, webViewLink, size))
                }
                Pair(true, docsList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Google Docs: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    suspend fun createGoogleDoc(
        context: Context,
        title: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", title)
                put("mimeType", "application/vnd.google-apps.document")
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create Google Doc: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create Google Doc: ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val createdId = json.optString("id", "")
                val webLink = json.optString("webViewLink", "https://docs.google.com/document/d/$createdId/edit")
                
                makeFilePublic(token, createdId)
                
                Pair(true, webLink)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Doc: ${e.message}", e)
            Pair(false, "Error: ${e.localizedMessage}")
        }
    }

    suspend fun createGoogleDocWithContent(
        context: Context,
        title: String,
        content: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            
            val metadata = JSONObject().apply {
                put("name", title)
                put("mimeType", "application/vnd.google-apps.document")
            }

            val mediaTypeRelated = "multipart/related".toMediaType()
            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(mediaTypeRelated)
                .addPart(
                    okhttp3.Headers.Builder().add("Content-Type", "application/json; charset=UTF-8").build(),
                    metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    okhttp3.Headers.Builder().add("Content-Type", "text/plain; charset=UTF-8").build(),
                    content.toRequestBody("text/plain; charset=UTF-8".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create Google Doc with content: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create Google Doc with content: ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val createdId = json.optString("id", "")
                val webLink = json.optString("webViewLink", "https://docs.google.com/document/d/$createdId/edit")
                
                makeFilePublic(token, createdId)
                
                Pair(true, webLink)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Doc with content: ${e.message}", e)
            Pair(false, "Error: ${e.localizedMessage}")
        }
    }

    suspend fun createGoogleSheetWithContent(
        context: Context,
        title: String,
        csvContent: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            
            val metadata = JSONObject().apply {
                put("name", title)
                put("mimeType", "application/vnd.google-apps.spreadsheet")
            }

            val mediaTypeRelated = "multipart/related".toMediaType()
            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(mediaTypeRelated)
                .addPart(
                    okhttp3.Headers.Builder().add("Content-Type", "application/json; charset=UTF-8").build(),
                    metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    okhttp3.Headers.Builder().add("Content-Type", "text/csv; charset=UTF-8").build(),
                    csvContent.toRequestBody("text/csv; charset=UTF-8".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create Google Sheet with content: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create Google Sheet with content: ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val createdId = json.optString("id", "")
                val webLink = json.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$createdId/edit")
                
                makeFilePublic(token, createdId)
                
                Pair(true, webLink)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Sheet with content: ${e.message}", e)
            Pair(false, "Error: ${e.localizedMessage}")
        }
    }

    suspend fun listGoogleDriveFiles(
        context: Context,
        parentId: String?,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GoogleDriveFileItem>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val queryStr = if (parentId.isNullOrBlank() || parentId == "root") {
                "'root' in parents and trashed=false"
            } else {
                "'$parentId' in parents and trashed=false"
            }
            val encodedQuery = java.net.URLEncoder.encode(queryStr, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,modifiedTime,webViewLink,size)&orderBy=folder,name"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to list Google Drive files: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()
                val driveList = mutableListOf<GoogleDriveFileItem>()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val id = fileObj.optString("id", "")
                    val name = fileObj.optString("name", "Untitled")
                    val mimeType = fileObj.optString("mimeType", "")
                    val modifiedTime = fileObj.optString("modifiedTime", "")
                    val webViewLink = fileObj.optString("webViewLink", "https://drive.google.com/open?id=$id")
                    val size = fileObj.optLong("size", 0L)
                    val isFolder = mimeType == "application/vnd.google-apps.folder"
                    driveList.add(GoogleDriveFileItem(id, name, mimeType, modifiedTime, webViewLink, size, isFolder))
                }
                Pair(true, driveList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Google Drive files: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    suspend fun createGoogleDriveFolder(
        context: Context,
        name: String,
        parentId: String?,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required.")

        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                if (!parentId.isNullOrBlank() && parentId != "root") {
                    val pArray = org.json.JSONArray()
                    pArray.put(parentId)
                    put("parents", pArray)
                }
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create folder: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create folder: ${response.code}")
                }
                Pair(true, "Folder created successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun listGoogleSheets(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GoogleSheetFile>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val encodedQuery = java.net.URLEncoder.encode("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,modifiedTime,webViewLink,size)&orderBy=modifiedTime%20desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to list Google Sheets: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()
                val sheetsList = mutableListOf<GoogleSheetFile>()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val id = fileObj.optString("id", "")
                    val name = fileObj.optString("name", "Untitled Spreadsheet")
                    val modifiedTime = fileObj.optString("modifiedTime", "")
                    val webViewLink = fileObj.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$id/edit")
                    val size = fileObj.optLong("size", 0L)
                    sheetsList.add(GoogleSheetFile(id, name, modifiedTime, webViewLink, size))
                }
                Pair(true, sheetsList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Google Sheets: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    suspend fun createGoogleSheet(
        context: Context,
        title: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", title)
                put("mimeType", "application/vnd.google-apps.spreadsheet")
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create Google Sheet: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create Google Sheet: ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val createdId = json.optString("id", "")
                val webLink = json.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$createdId/edit")
                
                // Set the permission so anyone can view it or keep it private but shareable
                makeFilePublic(token, createdId)
                
                Pair(true, webLink)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Sheet: ${e.message}", e)
            Pair(false, "Error: ${e.localizedMessage}")
        }
    }

    /**
     * Performs a full 2-way sync for Google Keep Notes.
     */
    suspend fun syncKeepNotes(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                val keepNoteDao = database.keepNoteDao()
                val localNotes = keepNoteDao.getAllKeepNotesDirect()

                // 1. Find file named "google_keep_notes.json" in AppData
                var fileId = findFileId(token, "google_keep_notes.json")
                val remoteNotes = mutableListOf<com.example.data.KeepNote>()

                if (fileId != null) {
                    // Download cloud content
                    val cloudContent = downloadBackupFileContent(token, fileId)
                    if (!cloudContent.isNullOrBlank()) {
                        val jsonArray = JSONArray(cloudContent)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            remoteNotes.add(
                                com.example.data.KeepNote(
                                    title = obj.optString("title", ""),
                                    content = obj.optString("content", ""),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                    isPinned = obj.optBoolean("isPinned", false),
                                    colorHex = obj.optString("colorHex", "#202124"),
                                    isSynced = true,
                                    websiteUrl = if (obj.isNull("websiteUrl")) null else obj.optString("websiteUrl"),
                                    customLogoUrl = if (obj.isNull("customLogoUrl")) null else obj.optString("customLogoUrl")
                                )
                            )
                        }
                    }
                } else {
                    // Create metadata for the new file
                    fileId = createFileMetadata(token, "google_keep_notes.json")
                    if (fileId == null) {
                        return@withLock Pair(false, "Failed to initialize Google Keep Notes space in Google Drive.")
                    }
                }

                // 2. Reconciliation: Merge local & remote notes
                val mergedMap = mutableMapOf<String, com.example.data.KeepNote>()
                for (note in localNotes) {
                    val signature = "${note.title.trim()}|${note.content.trim()}"
                    mergedMap[signature] = note
                }
                for (remote in remoteNotes) {
                    val signature = "${remote.title.trim()}|${remote.content.trim()}"
                    val existing = mergedMap[signature]
                    if (existing == null || remote.timestamp > existing.timestamp) {
                        mergedMap[signature] = remote
                    }
                }

                val mergedList = mergedMap.values.toList()

                // 3. Serialize merged list back to JSON
                val uploadArray = JSONArray()
                for (note in mergedList) {
                    val obj = JSONObject().apply {
                        put("title", note.title)
                        put("content", note.content)
                        put("timestamp", note.timestamp)
                        put("isPinned", note.isPinned)
                        put("colorHex", note.colorHex)
                        put("websiteUrl", note.websiteUrl ?: JSONObject.NULL)
                        put("customLogoUrl", note.customLogoUrl ?: JSONObject.NULL)
                    }
                    uploadArray.put(obj)
                }

                val contentStr = uploadArray.toString()

                // 4. Upload merged back to Google Drive AppData
                val uploadSuccess = uploadBackupFileContent(token, fileId, contentStr)
                if (!uploadSuccess) {
                    return@withLock Pair(false, "Failed to write synchronized notes back to Google Drive.")
                }

                // 5. Update local database
                keepNoteDao.clearAllKeepNotes()
                for (note in mergedList) {
                    keepNoteDao.insertKeepNote(note.copy(isSynced = true))
                }

                Pair(true, "Successfully merged and synchronized ${mergedList.size} notes!")
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error syncing Google Keep notes: ${e.message}", e)
                Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }
}


// ==================== CONSOLIDATED FROM: GoogleFitSyncManager.kt ====================
object GoogleFitSyncManager {
    private const val TAG = "GoogleFitSync"
    private const val FIT_SCOPE = "oauth2:https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.body.read"

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val email = account?.email
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, FIT_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered.", recoverable)
            recoverable.intent?.let { intent -> 
                withContext(Dispatchers.Main) { onAuthResolutionRequired(intent) }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token: ${e.message}", e)
            null
        }
    }

    fun hasFitPermission(context: Context): Boolean {
        val scope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/fitness.activity.read")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, scope)
    }
}


// ==================== CONSOLIDATED FROM: SystemContactSyncHelper.kt ====================
object SystemContactSyncHelper {

    fun getContactPhotoBytes(context: Context, photoUriStr: String): ByteArray? {
        try {
            if (photoUriStr.startsWith("http")) {
                // Check local cache first to prevent redundant downloading
                val cacheFile = java.io.File(context.cacheDir, "contact_photo_${photoUriStr.hashCode()}.jpg")
                if (cacheFile.exists()) {
                    return cacheFile.readBytes()
                }

                var connection: java.net.HttpURLConnection? = null
                try {
                    val url = java.net.URL(photoUriStr)
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val bytes = input.readBytes()
                    input.close()

                    if (bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                    }
                    return bytes
                } finally {
                    connection?.disconnect()
                }
            } else {
                val file = java.io.File(photoUriStr)
                if (file.exists()) {
                    return file.readBytes()
                }
                val uri = Uri.parse(photoUriStr)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return inputStream.readBytes()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun systemContactHasPhoto(context: Context, systemId: Long): Boolean {
        val resolver = context.contentResolver
        return try {
            resolver.query(
                Data.CONTENT_URI,
                arrayOf(Photo._ID),
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Photo.PHOTO} IS NOT NULL",
                arrayOf(systemId.toString(), Photo.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun insertSystemContact(context: Context, contact: Contact): Long? {
        val hasName = contact.firstName.isNotEmpty() || contact.lastName.isNotEmpty()
        val hasPhone = contact.phone.isNotEmpty()
        if (!hasName || !hasPhone) return null

        val resolver = context.contentResolver
        val ops = arrayListOf<ContentProviderOperation>()

        // 1. Raw Contact insertion
        val rawContactOpIndex = ops.size
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build())

        // 2. Name insertion
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, "${contact.firstName} ${contact.lastName}".trim())
            .build())

        // 3. Phone insertion
        if (contact.phone.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, contact.phone)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build())
        }

        // 4. Photo insertion
        if (!contact.photoUri.isNullOrEmpty()) {
            val imageBytes = getContactPhotoBytes(context, contact.photoUri)
            if (imageBytes != null) {
                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, imageBytes)
                    .build())
            }
        }

        return try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (results.isNotEmpty()) {
                val rawContactUri = results[0].uri
                if (rawContactUri != null) {
                    val systemId = ContentUris.parseId(rawContactUri)
                    if (systemId > 0 && !contact.photoUri.isNullOrEmpty()) {
                        context.getSharedPreferences("system_contact_photo_sync_prefs", Context.MODE_PRIVATE)
                            .edit().putString(systemId.toString(), contact.photoUri).apply()
                    }
                    systemId
                } else null
            } else null
        } catch (e: SecurityException) {
            android.util.Log.e("SystemContactSync", "Permission missing for contact insertion: ${e.message}")
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateSystemContact(context: Context, contact: Contact): Long? {
        val hasName = contact.firstName.isNotEmpty() || contact.lastName.isNotEmpty()
        val hasPhone = contact.phone.isNotEmpty()
        if (!hasName || !hasPhone) {
            // Requirement: If it doesn't have both name and phone number, don't sync
            return contact.systemContactId
        }

        val systemId = contact.systemContactId ?: return insertSystemContact(context, contact)
        val resolver = context.contentResolver

        // Check if raw contact actually exists on system
        val rawUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, systemId)
        val cursor = try {
            resolver.query(rawUri, arrayOf(RawContacts._ID), null, null, null)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val exists = cursor?.use { it.moveToFirst() } ?: false
        if (!exists) {
            return insertSystemContact(context, contact)
        }

        val ops = arrayListOf<ContentProviderOperation>()

        // Update StructuredName
        ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
            .withSelection(
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(systemId.toString(), StructuredName.CONTENT_ITEM_TYPE)
            )
            .withValue(StructuredName.DISPLAY_NAME, "${contact.firstName} ${contact.lastName}".trim())
            .build())

        // Calculate photo update status
        val hasSystemPhoto = systemContactHasPhoto(context, systemId)
        val photoPrefs = context.getSharedPreferences("system_contact_photo_sync_prefs", Context.MODE_PRIVATE)
        val lastSyncedPhoto = photoPrefs.getString(systemId.toString(), null)
        val currentPhoto = contact.photoUri ?: ""

        val shouldUpdatePhoto = if (currentPhoto.isEmpty()) {
            hasSystemPhoto
        } else {
            !hasSystemPhoto || lastSyncedPhoto != currentPhoto
        }

        // Delete old phone cleanly
        ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection(
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(systemId.toString(), Phone.CONTENT_ITEM_TYPE)
            )
            .build())

        // Delete photo if required
        if (shouldUpdatePhoto) {
            ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                    arrayOf(systemId.toString(), Photo.CONTENT_ITEM_TYPE)
                )
                .build())
        }

        // Re-insert phone
        if (contact.phone.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, systemId)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, contact.phone)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build())
        }

        // Re-insert photo ONLY if it changed and is present
        if (shouldUpdatePhoto && currentPhoto.isNotEmpty()) {
            val imageBytes = getContactPhotoBytes(context, currentPhoto)
            if (imageBytes != null) {
                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, systemId)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, imageBytes)
                    .build())
                photoPrefs.edit().putString(systemId.toString(), currentPhoto).apply()
            }
        }

        return try {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            systemId
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            systemId
        }
    }

    fun deleteSystemContact(context: Context, contact: Contact) {
        val systemId = contact.systemContactId ?: return
        val resolver = context.contentResolver
        val rawUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, systemId)
        try {
            resolver.delete(rawUri, null, null)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ==================== GOOGLE PHOTOS SYNC MANAGER ====================
object GooglePhotosSyncManager {
    private const val TAG = "GooglePhotosSync"
    private const val PHOTOS_SCOPE = "oauth2:https://www.googleapis.com/auth/photoslibrary.readonly"

    private val client = okhttp3.OkHttpClient()

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val email = account?.email
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found. Triggering Google Sign-In for Photos scope.")
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/photoslibrary.readonly"))
                    .build()
                val intent = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signInIntent
                withContext(Dispatchers.Main) {
                    onAuthResolutionRequired(intent)
                }
                return@withContext null
            }
            com.google.android.gms.auth.GoogleAuthUtil.getToken(context, email, PHOTOS_SCOPE)
        } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Photos scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Photos: ${e.message}", e)
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/photoslibrary.readonly"))
                .build()
            val intent = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signInIntent
            withContext(Dispatchers.Main) {
                onAuthResolutionRequired(intent)
            }
            null
        }
    }

    data class GooglePhotoItem(
        val id: String,
        val description: String?,
        val productUrl: String,
        val baseUrl: String,
        val mimeType: String,
        val filename: String
    )

    suspend fun fetchGooglePhotos(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GooglePhotoItem>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val url = "https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=50"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch photos: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val mediaItemsArray = json.optJSONArray("mediaItems")
                val photosList = mutableListOf<GooglePhotoItem>()
                if (mediaItemsArray != null) {
                    for (i in 0 until mediaItemsArray.length()) {
                        val item = mediaItemsArray.getJSONObject(i)
                        val id = item.getString("id")
                        val description = item.optString("description", null)
                        val productUrl = item.optString("productUrl", "")
                        val baseUrl = item.optString("baseUrl", "")
                        val mimeType = item.optString("mimeType", "")
                        val filename = item.optString("filename", "")
                        photosList.add(GooglePhotoItem(id, description, productUrl, baseUrl, mimeType, filename))
                    }
                }
                Pair(true, photosList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google Photos: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    suspend fun fetchGooglePhotosByDate(
        context: Context,
        year: Int,
        month: Int,
        day: Int,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GooglePhotoItem>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val url = "https://photoslibrary.googleapis.com/v1/mediaItems:search"
            
            val requestBodyJson = org.json.JSONObject().apply {
                put("pageSize", 100)
                put("filters", org.json.JSONObject().apply {
                    put("dateFilter", org.json.JSONObject().apply {
                        put("dates", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("year", year)
                                put("month", month)
                                put("day", day)
                            })
                        })
                    })
                })
            }

            val body = okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                requestBodyJson.toString()
            )

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch photos by date: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val mediaItemsArray = json.optJSONArray("mediaItems")
                val photosList = mutableListOf<GooglePhotoItem>()
                if (mediaItemsArray != null) {
                    for (i in 0 until mediaItemsArray.length()) {
                        val item = mediaItemsArray.getJSONObject(i)
                        val id = item.getString("id")
                        val description = item.optString("description", null)
                        val productUrl = item.optString("productUrl", "")
                        val baseUrl = item.optString("baseUrl", "")
                        val mimeType = item.optString("mimeType", "")
                        val filename = item.optString("filename", "")
                        photosList.add(GooglePhotoItem(id, description, productUrl, baseUrl, mimeType, filename))
                    }
                }
                Pair(true, photosList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google Photos by date: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    private const val PICKER_SCOPE = "oauth2:https://www.googleapis.com/auth/photospicker.mediaitems.readonly"

    suspend fun getPickerAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val email = account?.email
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found for Picker. Triggering Google Sign-In.")
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/photospicker.mediaitems.readonly"))
                    .build()
                val intent = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signInIntent
                withContext(Dispatchers.Main) {
                    onAuthResolutionRequired(intent)
                }
                return@withContext null
            }
            com.google.android.gms.auth.GoogleAuthUtil.getToken(context, email, PICKER_SCOPE)
        } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Picker scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Picker: ${e.message}", e)
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/photospicker.mediaitems.readonly"))
                .build()
            val intent = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signInIntent
            withContext(Dispatchers.Main) {
                onAuthResolutionRequired(intent)
            }
            null
        }
    }

    data class PickerSession(
        val id: String,
        val pickerUri: String,
        val pollInterval: String?,
        val timeoutIn: String?,
        val mediaItemsSet: Boolean
    )

    suspend fun createPickerSession(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): PickerSession? = withContext(Dispatchers.IO) {
        val token = getPickerAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext null

        try {
            val url = "https://photospicker.googleapis.com/v1/sessions"
            val body = okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                "{}"
            )
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to create picker session: code=${response.code} body=${response.body?.string()}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val id = json.getString("id")
                val pickerUri = json.getString("pickerUri")
                val pollingConfig = json.optJSONObject("pollingConfig")
                val pollInterval = pollingConfig?.optString("pollInterval")
                val timeoutIn = pollingConfig?.optString("timeoutIn")
                val mediaItemsSet = json.optBoolean("mediaItemsSet", false)
                PickerSession(id, pickerUri, pollInterval, timeoutIn, mediaItemsSet)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Picker session: ${e.message}", e)
            null
        }
    }

    suspend fun getPickerSession(
        context: Context,
        sessionId: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): PickerSession? = withContext(Dispatchers.IO) {
        val token = getPickerAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext null

        try {
            val url = "https://photospicker.googleapis.com/v1/sessions/$sessionId"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get picker session: code=${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val id = json.getString("id")
                val pickerUri = json.getString("pickerUri")
                val pollingConfig = json.optJSONObject("pollingConfig")
                val pollInterval = pollingConfig?.optString("pollInterval")
                val timeoutIn = pollingConfig?.optString("timeoutIn")
                val mediaItemsSet = json.optBoolean("mediaItemsSet", false)
                PickerSession(id, pickerUri, pollInterval, timeoutIn, mediaItemsSet)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Picker session: ${e.message}", e)
            null
        }
    }

    suspend fun fetchPickerMediaItems(
        context: Context,
        sessionId: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): List<GooglePhotoItem> = withContext(Dispatchers.IO) {
        val token = getPickerAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext emptyList()

        try {
            val url = "https://photospicker.googleapis.com/v1/mediaItems?sessionId=$sessionId"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch picker media items: code=${response.code}")
                    return@withContext emptyList()
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val mediaItemsArray = json.optJSONArray("mediaItems")
                val photosList = mutableListOf<GooglePhotoItem>()
                if (mediaItemsArray != null) {
                    for (i in 0 until mediaItemsArray.length()) {
                        val item = mediaItemsArray.getJSONObject(i)
                        val id = item.getString("id")
                        val mimeType = item.optString("mimeType", "")
                        
                        var baseUrl = item.optString("baseUrl", "")
                        if (baseUrl.isEmpty()) {
                            baseUrl = item.optJSONObject("mediaFile")?.optString("baseUrl", "") ?: ""
                        }
                        
                        photosList.add(GooglePhotoItem(
                            id = id,
                            description = item.optString("description", null),
                            productUrl = item.optString("productUrl", ""),
                            baseUrl = baseUrl,
                            mimeType = mimeType,
                            filename = item.optString("filename", "")
                        ))
                    }
                }
                photosList
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching picker media items: ${e.message}", e)
            emptyList()
        }
    }
}

