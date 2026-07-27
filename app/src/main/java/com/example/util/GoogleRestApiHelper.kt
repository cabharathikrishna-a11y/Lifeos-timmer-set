package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/**
 * A highly robust REST API Client implementing full support for Google Calendar v3,
 * Google Keep v1, and Google Docs v1 as defined in the provided API Reference.
 */
object GoogleRestApiHelper {
    private const val TAG = "GoogleRestApiHelper"
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Base URLs
    private const val CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3"
    private const val KEEP_BASE_URL = "https://keep.googleapis.com"
    private const val DOCS_BASE_URL = "https://docs.googleapis.com"

    /**
     * Retrieves an OAuth2 access token for the given scopes.
     */
    suspend fun getAccessTokenForScopes(
        context: Context,
        scopes: List<String>,
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
                Log.w(TAG, "No Google account email found for oauth token retrieval.")
                return@withContext null
            }
            val scopeString = "oauth2:" + scopes.joinToString(" ")
            GoogleAuthUtil.getToken(context, email, scopeString)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered during token retrieval.", recoverable)
            recoverable.intent?.let { intent ->
                withContext(Dispatchers.Main) {
                    onAuthResolutionRequired(intent)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for REST calls: ${e.message}", e)
            null
        }
    }

    // ==========================================
    // 1. GOOGLE CALENDAR v3 REST API METHODS
    // ==========================================

    // --- Acl Methods ---
    suspend fun deleteAcl(token: String, calendarId: String, ruleId: String): Pair<Boolean, String> =
        executeDelete("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl/${URLEncoder.encode(ruleId, "UTF-8")}", token)

    suspend fun getAcl(token: String, calendarId: String, ruleId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl/${URLEncoder.encode(ruleId, "UTF-8")}", token)

    suspend fun insertAcl(token: String, calendarId: String, ruleJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl", ruleJson, token)

    suspend fun listAcl(token: String, calendarId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl", token)

    suspend fun patchAcl(token: String, calendarId: String, ruleId: String, ruleJson: String): Pair<Boolean, String> =
        executePatch("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl/${URLEncoder.encode(ruleId, "UTF-8")}", ruleJson, token)

    suspend fun updateAcl(token: String, calendarId: String, ruleId: String, ruleJson: String): Pair<Boolean, String> =
        executePut("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl/${URLEncoder.encode(ruleId, "UTF-8")}", ruleJson, token)

    suspend fun watchAcl(token: String, calendarId: String, watchJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/acl/watch", watchJson, token)

    // --- CalendarList Methods ---
    suspend fun deleteCalendarList(token: String, calendarId: String): Pair<Boolean, String> =
        executeDelete("$CALENDAR_BASE_URL/users/me/calendarList/${URLEncoder.encode(calendarId, "UTF-8")}", token)

    suspend fun getCalendarList(token: String, calendarId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/users/me/calendarList/${URLEncoder.encode(calendarId, "UTF-8")}", token)

    suspend fun insertCalendarList(token: String, calendarListJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/users/me/calendarList", calendarListJson, token)

    suspend fun listCalendarList(token: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/users/me/calendarList", token)

    suspend fun patchCalendarList(token: String, calendarId: String, calendarListJson: String): Pair<Boolean, String> =
        executePatch("$CALENDAR_BASE_URL/users/me/calendarList/${URLEncoder.encode(calendarId, "UTF-8")}", calendarListJson, token)

    suspend fun updateCalendarList(token: String, calendarId: String, calendarListJson: String): Pair<Boolean, String> =
        executePut("$CALENDAR_BASE_URL/users/me/calendarList/${URLEncoder.encode(calendarId, "UTF-8")}", calendarListJson, token)

    suspend fun watchCalendarList(token: String, watchJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/users/me/calendarList/watch", watchJson, token)

    // --- Calendars Methods ---
    suspend fun clearCalendar(token: String, calendarId: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/clear", "", token)

    suspend fun deleteCalendar(token: String, calendarId: String): Pair<Boolean, String> =
        executeDelete("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}", token)

    suspend fun getCalendarMetadata(token: String, calendarId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}", token)

    suspend fun insertCalendar(token: String, calendarJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars", calendarJson, token)

    suspend fun patchCalendar(token: String, calendarId: String, calendarJson: String): Pair<Boolean, String> =
        executePatch("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}", calendarJson, token)

    suspend fun transferOwnership(token: String, calendarId: String, newDataOwner: String, useAdminAccess: Boolean = true): Pair<Boolean, String> {
        val url = "$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/transferOwnership" +
                "?newDataOwner=${URLEncoder.encode(newDataOwner, "UTF-8")}&useAdminAccess=$useAdminAccess"
        return executePost(url, "", token)
    }

    suspend fun updateCalendar(token: String, calendarId: String, calendarJson: String): Pair<Boolean, String> =
        executePut("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}", calendarJson, token)

    // --- Channels Methods ---
    suspend fun stopChannel(token: String, channelJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/channels/stop", channelJson, token)

    // --- Colors Methods ---
    suspend fun getColors(token: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/colors", token)

    // --- Events Methods ---
    suspend fun deleteEvent(token: String, calendarId: String, eventId: String): Pair<Boolean, String> =
        executeDelete("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}", token)

    suspend fun getEvent(token: String, calendarId: String, eventId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}", token)

    suspend fun importEvent(token: String, calendarId: String, eventJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/import", eventJson, token)

    suspend fun insertEvent(token: String, calendarId: String, eventJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events", eventJson, token)

    suspend fun listEventInstances(token: String, calendarId: String, eventId: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}/instances", token)

    suspend fun listEvents(token: String, calendarId: String, queryParams: Map<String, String> = emptyMap()): Pair<Boolean, String> {
        val queryBuilder = StringBuilder()
        if (queryParams.isNotEmpty()) {
            queryBuilder.append("?")
            queryParams.forEach { (key, value) ->
                queryBuilder.append(URLEncoder.encode(key, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(value, "UTF-8"))
                    .append("&")
            }
            queryBuilder.setLength(queryBuilder.length - 1) // Trim last ampersand
        }
        val url = "$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events$queryBuilder"
        return executeGet(url, token)
    }

    suspend fun moveEvent(token: String, calendarId: String, eventId: String, destination: String): Pair<Boolean, String> {
        val url = "$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}/move" +
                "?destination=${URLEncoder.encode(destination, "UTF-8")}"
        return executePost(url, "", token)
    }

    suspend fun patchEvent(token: String, calendarId: String, eventId: String, eventJson: String): Pair<Boolean, String> =
        executePatch("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}", eventJson, token)

    suspend fun quickAddEvent(token: String, calendarId: String, text: String): Pair<Boolean, String> {
        val url = "$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/quickAdd" +
                "?text=${URLEncoder.encode(text, "UTF-8")}"
        return executePost(url, "", token)
    }

    suspend fun updateEvent(token: String, calendarId: String, eventId: String, eventJson: String): Pair<Boolean, String> =
        executePut("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/${URLEncoder.encode(eventId, "UTF-8")}", eventJson, token)

    suspend fun watchEvents(token: String, calendarId: String, watchJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/calendars/${URLEncoder.encode(calendarId, "UTF-8")}/events/watch", watchJson, token)

    // --- Freebusy Methods ---
    suspend fun queryFreebusy(token: String, queryJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/freeBusy", queryJson, token)

    // --- Settings Methods ---
    suspend fun getSetting(token: String, setting: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/users/me/settings/${URLEncoder.encode(setting, "UTF-8")}", token)

    suspend fun listSettings(token: String): Pair<Boolean, String> =
        executeGet("$CALENDAR_BASE_URL/users/me/settings", token)

    suspend fun watchSettings(token: String, watchJson: String): Pair<Boolean, String> =
        executePost("$CALENDAR_BASE_URL/users/me/settings/watch", watchJson, token)


    // ==========================================
    // 2. GOOGLE KEEP v1 REST API METHODS
    // ==========================================

    // --- Media Methods ---
    suspend fun downloadKeepMedia(token: String, attachmentName: String): Pair<Boolean, ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$KEEP_BASE_URL/v1/${attachmentName.trimStart('/')}"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed downloadKeepMedia: code=${response.code}")
                    Pair(false, ByteArray(0))
                } else {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    Pair(true, bytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Keep media: ${e.message}", e)
            Pair(false, ByteArray(0))
        }
    }

    // --- Notes Methods ---
    suspend fun createKeepNote(token: String, noteJson: String): Pair<Boolean, String> =
        executePost("$KEEP_BASE_URL/v1/notes", noteJson, token)

    suspend fun deleteKeepNote(token: String, noteName: String): Pair<Boolean, String> =
        executeDelete("$KEEP_BASE_URL/v1/${noteName.trimStart('/')}", token)

    suspend fun getKeepNote(token: String, noteName: String): Pair<Boolean, String> =
        executeGet("$KEEP_BASE_URL/v1/${noteName.trimStart('/')}", token)

    suspend fun listKeepNotes(token: String): Pair<Boolean, String> =
        executeGet("$KEEP_BASE_URL/v1/notes", token)

    // --- Permissions Methods ---
    suspend fun batchCreatePermissions(token: String, noteName: String, permissionsJson: String): Pair<Boolean, String> =
        executePost("$KEEP_BASE_URL/v1/${noteName.trimStart('/')}/permissions:batchCreate", permissionsJson, token)

    suspend fun batchDeletePermissions(token: String, noteName: String, permissionsJson: String): Pair<Boolean, String> =
        executePost("$KEEP_BASE_URL/v1/${noteName.trimStart('/')}/permissions:batchDelete", permissionsJson, token)


    // ==========================================
    // 3. GOOGLE DOCS v1 REST API METHODS
    // ==========================================

    // --- Documents Methods ---
    suspend fun batchUpdateDocument(token: String, documentId: String, requestsJson: String): Pair<Boolean, String> =
        executePost("$DOCS_BASE_URL/v1/documents/${URLEncoder.encode(documentId, "UTF-8")}:batchUpdate", requestsJson, token)

    suspend fun createDocument(token: String, documentJson: String): Pair<Boolean, String> =
        executePost("$DOCS_BASE_URL/v1/documents", documentJson, token)

    suspend fun getDocument(token: String, documentId: String): Pair<Boolean, String> =
        executeGet("$DOCS_BASE_URL/v1/documents/${URLEncoder.encode(documentId, "UTF-8")}", token)


    // ==========================================
    // HTTP VERB EXECUTORS
    // ==========================================

    private suspend fun executeGet(url: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "GET $url Failed: code=${response.code} body=$bodyStr")
                    Pair(false, bodyStr)
                } else {
                    Pair(true, bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $url Error: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown Error")
        }
    }

    private suspend fun executePost(url: String, json: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "POST $url Failed: code=${response.code} body=$bodyStr")
                    Pair(false, bodyStr)
                } else {
                    Pair(true, bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $url Error: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown Error")
        }
    }

    private suspend fun executePut(url: String, json: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "PUT $url Failed: code=${response.code} body=$bodyStr")
                    Pair(false, bodyStr)
                } else {
                    Pair(true, bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PUT $url Error: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown Error")
        }
    }

    private suspend fun executePatch(url: String, json: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .patch(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "PATCH $url Failed: code=${response.code} body=$bodyStr")
                    Pair(false, bodyStr)
                } else {
                    Pair(true, bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PATCH $url Error: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown Error")
        }
    }

    private suspend fun executeDelete(url: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "DELETE $url Failed: code=${response.code} body=$bodyStr")
                    Pair(false, bodyStr)
                } else {
                    Pair(true, bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $url Error: ${e.message}", e)
            Pair(false, e.localizedMessage ?: "Unknown Error")
        }
    }

    // ==========================================
    // 4. GOOGLE SHEETS v4 REST API METHODS
    // ==========================================
    private const val SHEETS_BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"

    /**
     * Applies one or more updates to the spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}:batchUpdate
     */
    suspend fun batchUpdateSpreadsheet(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}:batchUpdate", bodyJson, token)

    /**
     * Creates a spreadsheet, returning the newly created spreadsheet.
     * POST /v4/spreadsheets
     */
    suspend fun createSpreadsheet(token: String, bodyJson: String): Pair<Boolean, String> =
        executePost(SHEETS_BASE_URL, bodyJson, token)

    /**
     * Returns the spreadsheet at the given ID.
     * GET /v4/spreadsheets/{spreadsheetId}
     */
    suspend fun getSpreadsheet(token: String, spreadsheetId: String, includeGridData: Boolean = false): Pair<Boolean, String> =
        executeGet("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}?includeGridData=$includeGridData", token)

    /**
     * Returns the spreadsheet at the given ID filtering by DataFilter.
     * POST /v4/spreadsheets/{spreadsheetId}:getByDataFilter
     */
    suspend fun getSpreadsheetByDataFilter(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}:getByDataFilter", bodyJson, token)

    /**
     * Copies a single sheet from a spreadsheet to another spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}/sheets/{sheetId}:copyTo
     */
    suspend fun copySheetTo(token: String, spreadsheetId: String, sheetId: Int, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/sheets/$sheetId:copyTo", bodyJson, token)

    /**
     * Appends values to a spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}/values/{range}:append
     */
    suspend fun appendSpreadsheetValues(
        token: String,
        spreadsheetId: String,
        range: String,
        bodyJson: String,
        valueInputOption: ValueInputOption = ValueInputOption.USER_ENTERED,
        insertDataOption: String = "INSERT_ROWS"
    ): Pair<Boolean, String> {
        val url = "$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values/${URLEncoder.encode(range, "UTF-8")}:append" +
                "?valueInputOption=${valueInputOption.name}&insertDataOption=$insertDataOption"
        return executePost(url, bodyJson, token)
    }

    /**
     * Clears one or more ranges of values from a spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}/values:batchClear
     */
    suspend fun batchClearSpreadsheetValues(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchClear", bodyJson, token)

    /**
     * Clears one or more ranges of values from a spreadsheet by DataFilter.
     * POST /v4/spreadsheets/{spreadsheetId}/values:batchClearByDataFilter
     */
    suspend fun batchClearSpreadsheetValuesByDataFilter(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchClearByDataFilter", bodyJson, token)

    /**
     * Returns one or more ranges of values from a spreadsheet.
     * GET /v4/spreadsheets/{spreadsheetId}/values:batchGet
     */
    suspend fun batchGetSpreadsheetValues(
        token: String,
        spreadsheetId: String,
        ranges: List<String>,
        valueRenderOption: ValueRenderOption = ValueRenderOption.FORMATTED_VALUE,
        dateTimeRenderOption: DateTimeRenderOption = DateTimeRenderOption.FORMATTED_STRING
    ): Pair<Boolean, String> {
        val rangesParam = ranges.joinToString("&") { "ranges=${URLEncoder.encode(it, "UTF-8")}" }
        val url = "$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchGet" +
                "?$rangesParam&valueRenderOption=${valueRenderOption.name}&dateTimeRenderOption=${dateTimeRenderOption.name}"
        return executeGet(url, token)
    }

    /**
     * Returns one or more ranges of values that match the specified data filters.
     * POST /v4/spreadsheets/{spreadsheetId}/values:batchGetByDataFilter
     */
    suspend fun batchGetSpreadsheetValuesByDataFilter(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchGetByDataFilter", bodyJson, token)

    /**
     * Sets values in one or more ranges of a spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}/values:batchUpdate
     */
    suspend fun batchUpdateSpreadsheetValues(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchUpdate", bodyJson, token)

    /**
     * Sets values in one or more ranges of a spreadsheet by DataFilter.
     * POST /v4/spreadsheets/{spreadsheetId}/values:batchUpdateByDataFilter
     */
    suspend fun batchUpdateSpreadsheetValuesByDataFilter(token: String, spreadsheetId: String, bodyJson: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values:batchUpdateByDataFilter", bodyJson, token)

    /**
     * Clears values from a spreadsheet.
     * POST /v4/spreadsheets/{spreadsheetId}/values/{range}:clear
     */
    suspend fun clearSpreadsheetValues(token: String, spreadsheetId: String, range: String): Pair<Boolean, String> =
        executePost("$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values/${URLEncoder.encode(range, "UTF-8")}:clear", "", token)

    /**
     * Returns a range of values from a spreadsheet.
     * GET /v4/spreadsheets/{spreadsheetId}/values/{range}
     */
    suspend fun getSpreadsheetValues(
        token: String,
        spreadsheetId: String,
        range: String,
        valueRenderOption: ValueRenderOption = ValueRenderOption.FORMATTED_VALUE,
        dateTimeRenderOption: DateTimeRenderOption = DateTimeRenderOption.FORMATTED_STRING
    ): Pair<Boolean, String> {
        val url = "$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values/${URLEncoder.encode(range, "UTF-8")}" +
                "?valueRenderOption=${valueRenderOption.name}&dateTimeRenderOption=${dateTimeRenderOption.name}"
        return executeGet(url, token)
    }

    /**
     * Sets values in a range of a spreadsheet.
     * PUT /v4/spreadsheets/{spreadsheetId}/values/{range}
     */
    suspend fun updateSpreadsheetValues(
        token: String,
        spreadsheetId: String,
        range: String,
        bodyJson: String,
        valueInputOption: ValueInputOption = ValueInputOption.USER_ENTERED
    ): Pair<Boolean, String> {
        val url = "$SHEETS_BASE_URL/${URLEncoder.encode(spreadsheetId, "UTF-8")}/values/${URLEncoder.encode(range, "UTF-8")}" +
                "?valueInputOption=${valueInputOption.name}"
        return executePut(url, bodyJson, token)
    }

    // ==========================================
    // GOOGLE SHEETS API STRUCTURAL MODELS
    // ==========================================

    data class DataFilter(
        val developerMetadataLookup: DeveloperMetadataLookup? = null,
        val a1Range: String? = null,
        val gridRange: GridRange? = null
    )

    data class DeveloperMetadataLookup(
        val metadataId: Int? = null,
        val metadataKey: String? = null,
        val metadataValue: String? = null,
        val visibility: String? = null,
        val locationType: String? = null
    )

    data class GridRange(
        val sheetId: Int? = null,
        val startRowIndex: Int? = null,
        val endRowIndex: Int? = null,
        val startColumnIndex: Int? = null,
        val endColumnIndex: Int? = null
    )

    enum class DateTimeRenderOption {
        SERIAL_NUMBER,
        FORMATTED_STRING
    }

    enum class Dimension {
        ROWS,
        COLUMNS
    }

    data class DimensionRange(
        val sheetId: Int? = null,
        val dimension: Dimension? = null,
        val startIndex: Int? = null,
        val endIndex: Int? = null
    )

    enum class ErrorCode {
        OK, CANCELLED, UNKNOWN, INVALID_ARGUMENT, DEADLINE_EXCEEDED, NOT_FOUND,
        ALREADY_EXISTS, PERMISSION_DENIED, RESOURCE_EXHAUSTED, FAILED_PRECONDITION,
        ABORTED, OUT_OF_RANGE, UNIMPLEMENTED, INTERNAL, UNAVAILABLE, DATA_LOSS,
        UNAUTHENTICATED
    }

    data class ErrorDetails(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
        val details: List<Map<String, Any>>? = null
    )

    data class UpdateValuesResponse(
        val spreadsheetId: String? = null,
        val updatedRange: String? = null,
        val updatedRows: Int? = null,
        val updatedColumns: Int? = null,
        val updatedCells: Int? = null,
        val updatedData: ValueRange? = null
    )

    data class ValueRange(
        val range: String? = null,
        val majorDimension: Dimension? = null,
        val values: List<List<Any>>? = null
    )

    enum class ValueInputOption {
        RAW,
        USER_ENTERED
    }

    enum class ValueRenderOption {
        FORMATTED_VALUE,
        UNFORMATTED_VALUE,
        FORMULA
    }
}
