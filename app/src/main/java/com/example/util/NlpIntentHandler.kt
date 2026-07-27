package com.example.util

import android.content.Context
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class NlpIntentResult {
    data class TaskCreation(
        val title: String,
        val priority: String = "MEDIUM",
        val estMin: Int = 25,
        val category: String = "Inbox",
        val dueDateString: String = "",
        val description: String = "Added via NLP Chat Assistant"
    ) : NlpIntentResult()

    enum class ScheduleChangeType {
        RESCHEDULE_DUE_DATE,
        CHANGE_PRIORITY,
        CHANGE_CATEGORY,
        CHANGE_DURATION,
        COMPLETE_TASK,
        DELETE_TASK
    }

    data class ScheduleChange(
        val changeType: ScheduleChangeType,
        val targetTitle: String,
        val newDueDateString: String? = null,
        val newPriority: String? = null,
        val newCategory: String? = null,
        val newEstMin: Int? = null
    ) : NlpIntentResult()

    data class FinanceLogging(
        val type: String, // "EXPENSE" or "INCOME"
        val amount: Double,
        val tag: String,
        val note: String
    ) : NlpIntentResult()

    data class ReminderScheduling(
        val title: String,
        val timeString: String = "",
        val dateString: String = "",
        val priority: String = "MEDIUM"
    ) : NlpIntentResult()

    data class HealthLogging(
        val metricType: String, // "WATER", "STEPS", "SLEEP", "WEIGHT"
        val value: Double,
        val unit: String,
        val notes: String = ""
    ) : NlpIntentResult()

    data class JournalEntry(
        val title: String,
        val content: String,
        val mood: String = "Neutral"
    ) : NlpIntentResult()

    object None : NlpIntentResult()
}

interface NlpActionCallbackBridge {
    fun onCreateTask(task: NlpIntentResult.TaskCreation)
    fun onScheduleChange(change: NlpIntentResult.ScheduleChange)
    fun onLogFinance(finance: NlpIntentResult.FinanceLogging)
    fun onScheduleReminder(reminder: NlpIntentResult.ReminderScheduling)
    fun onLogHealth(health: NlpIntentResult.HealthLogging)
    fun onJournalEntry(journal: NlpIntentResult.JournalEntry)
    fun onUnhandledIntent(query: String)
}

object NlpIntentHandler {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Uses Gemini AI (gemini-3.5-flash) to classify input and extract structured intent parameters.
     * Fallback to rule-based detection if offline or if key is unavailable/error occurs.
     */
    suspend fun detectIntentWithGemini(
        query: String,
        apiKey: String = BuildConfig.GEMINI_API_KEY,
        currentDateStr: String = getCurrentDateString()
    ): NlpIntentResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext NlpIntentResult.None

        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            android.util.Log.d("NlpIntentHandler", "No valid Gemini API key found. Falling back to rule-based intent parsing.")
            return@withContext detectIntent(trimmedQuery, currentDateStr)
        }

        try {
            val systemPrompt = """
                You are an NLP Intent Classifier for a productivity, finance, and wellness Android app.
                Today's date is: $currentDateStr.
                Map the user chat command to ONE of the following action types:
                1. "createTask": Adding a task/todo.
                   Extract: title, priority (HIGH, MEDIUM, LOW), estMin (int), category, dueDateString (YYYY-MM-DD), description.
                2. "logFinance": Spent or earned money.
                   Extract: type ("EXPENSE" or "INCOME"), amount (number), tag (e.g., Food & Dining, Auto & Transport, Bills, Salary, Freelance), note.
                3. "scheduleReminder": Setting an alarm or reminder for a specific time/date.
                   Extract: title, timeString (HH:MM or 12hr format), dateString (YYYY-MM-DD), priority.
                4. "scheduleChange": Modifying an existing task (reschedule, complete, priority change, duration change, delete).
                   Extract: changeType ("RESCHEDULE_DUE_DATE", "CHANGE_PRIORITY", "CHANGE_CATEGORY", "CHANGE_DURATION", "COMPLETE_TASK", "DELETE_TASK"), targetTitle, newDueDateString, newPriority, newCategory, newEstMin.
                5. "logHealth": Water, steps, sleep, weight logging.
                   Extract: metricType ("WATER", "STEPS", "SLEEP", "WEIGHT"), value (number), unit ("ml", "steps", "hrs", "kg"), notes.
                6. "journalEntry": Journal reflection or diary entry.
                   Extract: title, content, mood ("Happy", "Neutral", "Productive", "Stressed", "Calm").
                7. "none": General conversation or query not calling an automated app action.

                Respond ONLY with a valid JSON object matching this exact structure:
                {
                  "action": "createTask" | "logFinance" | "scheduleReminder" | "scheduleChange" | "logHealth" | "journalEntry" | "none",
                  "task": { "title": "...", "priority": "HIGH", "estMin": 30, "category": "Work", "dueDateString": "$currentDateStr", "description": "..." },
                  "finance": { "type": "EXPENSE", "amount": 250.0, "tag": "Food & Dining", "note": "Lunch" },
                  "reminder": { "title": "...", "timeString": "15:00", "dateString": "$currentDateStr", "priority": "MEDIUM" },
                  "scheduleChange": { "changeType": "RESCHEDULE_DUE_DATE", "targetTitle": "...", "newDueDateString": "$currentDateStr", "newPriority": "HIGH", "newCategory": "Study", "newEstMin": 45 },
                  "health": { "metricType": "WATER", "value": 500.0, "unit": "ml", "notes": "Cold water" },
                  "journal": { "title": "Daily Reflection", "content": "...", "mood": "Productive" }
                }
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", "System Instruction:\n$systemPrompt\n\nUser Command: \"$trimmedQuery\"") })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-3.5-flash")
            for (model in candidateModels) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    val response = httpClient.newCall(request).execute()
                    val responseBodyStr = response.body?.string()

                    if (response.isSuccessful && !responseBodyStr.isNullOrEmpty()) {
                        val parsedResult = parseGeminiResponseJson(responseBodyStr, currentDateStr)
                        if (parsedResult != NlpIntentResult.None) {
                            return@withContext parsedResult
                        }
                    } else {
                        android.util.Log.w("NlpIntentHandler", "Model $model HTTP error ${response.code}: $responseBodyStr")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NlpIntentHandler", "Model $model failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NlpIntentHandler", "Error executing Gemini intent recognition", e)
        }

        // Fallback to local rule-based intent engine
        detectIntent(trimmedQuery, currentDateStr)
    }

    private fun parseGeminiResponseJson(responseJsonStr: String, defaultDate: String): NlpIntentResult {
        return try {
            val rootObj = JSONObject(responseJsonStr)
            val candidates = rootObj.optJSONArray("candidates") ?: return NlpIntentResult.None
            if (candidates.length() == 0) return NlpIntentResult.None

            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return NlpIntentResult.None
            val parts = content.optJSONArray("parts") ?: return NlpIntentResult.None
            if (parts.length() == 0) return NlpIntentResult.None

            val text = parts.getJSONObject(0).optString("text", "").trim()
            if (text.isEmpty()) return NlpIntentResult.None

            val jsonIntent = JSONObject(text)
            val action = jsonIntent.optString("action", "none")

            when (action) {
                "createTask" -> {
                    val task = jsonIntent.optJSONObject("task") ?: return NlpIntentResult.None
                    NlpIntentResult.TaskCreation(
                        title = task.optString("title", "New Task").ifEmpty { "New Task" },
                        priority = task.optString("priority", "MEDIUM").uppercase(Locale.ROOT),
                        estMin = task.optInt("estMin", 25),
                        category = task.optString("category", "Inbox").ifEmpty { "Inbox" },
                        dueDateString = task.optString("dueDateString", defaultDate).ifEmpty { defaultDate },
                        description = task.optString("description", "Added via Gemini NLP Assistant")
                    )
                }
                "logFinance" -> {
                    val fin = jsonIntent.optJSONObject("finance") ?: return NlpIntentResult.None
                    NlpIntentResult.FinanceLogging(
                        type = fin.optString("type", "EXPENSE").uppercase(Locale.ROOT),
                        amount = fin.optDouble("amount", 0.0),
                        tag = fin.optString("tag", "General"),
                        note = fin.optString("note", "Logged via Gemini NLP")
                    )
                }
                "scheduleReminder" -> {
                    val rem = jsonIntent.optJSONObject("reminder") ?: return NlpIntentResult.None
                    NlpIntentResult.ReminderScheduling(
                        title = rem.optString("title", "Reminder").ifEmpty { "Reminder" },
                        timeString = rem.optString("timeString", "09:00"),
                        dateString = rem.optString("dateString", defaultDate),
                        priority = rem.optString("priority", "MEDIUM")
                    )
                }
                "scheduleChange" -> {
                    val sc = jsonIntent.optJSONObject("scheduleChange") ?: return NlpIntentResult.None
                    val changeTypeStr = sc.optString("changeType", "RESCHEDULE_DUE_DATE")
                    val changeType = try {
                        NlpIntentResult.ScheduleChangeType.valueOf(changeTypeStr)
                    } catch (e: Exception) {
                        NlpIntentResult.ScheduleChangeType.RESCHEDULE_DUE_DATE
                    }
                    NlpIntentResult.ScheduleChange(
                        changeType = changeType,
                        targetTitle = sc.optString("targetTitle", ""),
                        newDueDateString = sc.optString("newDueDateString", null),
                        newPriority = sc.optString("newPriority", null),
                        newCategory = sc.optString("newCategory", null),
                        newEstMin = if (sc.has("newEstMin")) sc.optInt("newEstMin") else null
                    )
                }
                "logHealth" -> {
                    val h = jsonIntent.optJSONObject("health") ?: return NlpIntentResult.None
                    NlpIntentResult.HealthLogging(
                        metricType = h.optString("metricType", "WATER").uppercase(Locale.ROOT),
                        value = h.optDouble("value", 0.0),
                        unit = h.optString("unit", "ml"),
                        notes = h.optString("notes", "")
                    )
                }
                "journalEntry" -> {
                    val j = jsonIntent.optJSONObject("journal") ?: return NlpIntentResult.None
                    NlpIntentResult.JournalEntry(
                        title = j.optString("title", "Journal Entry"),
                        content = j.optString("content", ""),
                        mood = j.optString("mood", "Neutral")
                    )
                }
                else -> NlpIntentResult.None
            }
        } catch (e: Exception) {
            android.util.Log.e("NlpIntentHandler", "Failed to parse Gemini intent JSON response", e)
            NlpIntentResult.None
        }
    }

    /**
     * Dispatches an extracted NlpIntentResult to the given NlpActionCallbackBridge.
     */
    fun dispatchToBridge(result: NlpIntentResult, bridge: NlpActionCallbackBridge, rawQuery: String = "") {
        when (result) {
            is NlpIntentResult.TaskCreation -> bridge.onCreateTask(result)
            is NlpIntentResult.ScheduleChange -> bridge.onScheduleChange(result)
            is NlpIntentResult.FinanceLogging -> bridge.onLogFinance(result)
            is NlpIntentResult.ReminderScheduling -> bridge.onScheduleReminder(result)
            is NlpIntentResult.HealthLogging -> bridge.onLogHealth(result)
            is NlpIntentResult.JournalEntry -> bridge.onJournalEntry(result)
            is NlpIntentResult.None -> bridge.onUnhandledIntent(rawQuery)
        }
    }

    fun detectIntent(rawQuery: String, currentDateStr: String = getCurrentDateString()): NlpIntentResult {
        val query = rawQuery.trim()
        val lowercase = query.lowercase(Locale.ROOT)

        if (query.isEmpty()) return NlpIntentResult.None

        // 1. Check Finance Logging Intent FIRST if amount is present
        val financeResult = detectFinanceIntent(query, lowercase)
        if (financeResult != null) return financeResult

        // 2. Check Schedule Change Intent (Reschedule, Update Due Date, Change Priority, Duration, Category, Complete)
        val scheduleResult = detectScheduleChangeIntent(query, lowercase, currentDateStr)
        if (scheduleResult != null) return scheduleResult

        // 3. Check Task Creation Intent
        val taskResult = detectTaskCreationIntent(query, lowercase, currentDateStr)
        if (taskResult != null) return taskResult

        return NlpIntentResult.None
    }

    private fun detectFinanceIntent(query: String, lowercase: String): NlpIntentResult.FinanceLogging? {
        val amountPattern = Regex("(?:[\\$₹]|Rs\\.?|INR)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:rupees|rs|usd|dollars|bucks)?", RegexOption.IGNORE_CASE)
        val amountMatch = amountPattern.find(query) ?: return null
        val amount = amountMatch.groupValues[1].toDoubleOrNull() ?: return null

        if (amount <= 0.0) return null

        val isExpense = lowercase.startsWith("add expense") ||
                lowercase.startsWith("log expense") ||
                lowercase.startsWith("spent") ||
                lowercase.startsWith("paid") ||
                lowercase.startsWith("bought") ||
                lowercase.startsWith("log spending") ||
                lowercase.contains("expense of") ||
                lowercase.contains("spent ") ||
                lowercase.contains("paid ") ||
                lowercase.contains("bought ")

        val isIncome = lowercase.startsWith("add income") ||
                lowercase.startsWith("log income") ||
                lowercase.startsWith("earned") ||
                lowercase.startsWith("earn") ||
                lowercase.startsWith("got paid") ||
                lowercase.startsWith("received") ||
                lowercase.contains("income of") ||
                lowercase.contains("earned ") ||
                lowercase.contains("salary")

        if (!isExpense && !isIncome) return null

        val type = if (isExpense) "EXPENSE" else "INCOME"

        var note = query.replace(amountMatch.value, "", ignoreCase = true)

        val prefixes = listOf(
            "add expense", "log expense", "add income", "log income", "spend", "spent",
            "earned", "earn", "paid", "bought", "for", "from", "on", "rupees", "rs", "logged spending of",
            "income of", "expense of"
        )
        var cleanedNote = note
        prefixes.forEach { prefix ->
            if (cleanedNote.lowercase(Locale.ROOT).trim().startsWith(prefix)) {
                cleanedNote = cleanedNote.trim().substring(prefix.length).trim()
            }
        }
        cleanedNote = cleanedNote.trim().removePrefix("for").removePrefix("on").removePrefix("from").trim()
        if (cleanedNote.isEmpty()) {
            cleanedNote = if (type == "EXPENSE") "Expense logged via NLP AI" else "Income logged via NLP AI"
        }

        val tag = if (type == "EXPENSE") {
            val lower = cleanedNote.lowercase(Locale.ROOT)
            when {
                lower.contains("food") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") || lower.contains("tea") || lower.contains("restaurant") || lower.contains("snacks") || lower.contains("breakfast") || lower.contains("swiggy") || lower.contains("zomato") -> "Food & Dining"
                lower.contains("taxi") || lower.contains("uber") || lower.contains("ola") || lower.contains("gas") || lower.contains("petrol") || lower.contains("bus") || lower.contains("metro") || lower.contains("train") || lower.contains("cab") || lower.contains("ride") || lower.contains("transport") -> "Auto & Transport"
                lower.contains("rent") || lower.contains("bill") || lower.contains("electricity") || lower.contains("water") || lower.contains("wifi") || lower.contains("recharge") || lower.contains("mobile") -> "Bills & Utilities"
                lower.contains("movie") || lower.contains("game") || lower.contains("cinema") || lower.contains("ticket") || lower.contains("netflix") || lower.contains("spotify") -> "Entertainment"
                lower.contains("gym") || lower.contains("fitness") || lower.contains("health") || lower.contains("medicine") || lower.contains("doctor") || lower.contains("hospital") || lower.contains("pharmacy") -> "Health & Fitness"
                lower.contains("book") || lower.contains("textbook") || lower.contains("course") || lower.contains("tuition") || lower.contains("exam") || lower.contains("study") -> "Education & Books"
                lower.contains("clothing") || lower.contains("clothes") || lower.contains("shoes") || lower.contains("amazon") || lower.contains("flipkart") || lower.contains("shopping") -> "Shopping"
                lower.contains("grocery") || lower.contains("groceries") || lower.contains("market") || lower.contains("milk") -> "Groceries"
                else -> "General Expense"
            }
        } else {
            val lower = cleanedNote.lowercase(Locale.ROOT)
            when {
                lower.contains("salary") || lower.contains("stipend") -> "Salary"
                lower.contains("freelance") || lower.contains("client") || lower.contains("project") -> "Freelance"
                lower.contains("interest") || lower.contains("dividend") || lower.contains("stock") -> "Investment Income"
                lower.contains("gift") || lower.contains("bonus") -> "Gift"
                else -> "Other Income"
            }
        }

        return NlpIntentResult.FinanceLogging(
            type = type,
            amount = amount,
            tag = tag,
            note = cleanedNote
        )
    }

    private fun detectScheduleChangeIntent(query: String, lowercase: String, currentDateStr: String): NlpIntentResult.ScheduleChange? {
        // A. Reschedule due date
        val isReschedule = lowercase.startsWith("reschedule task") ||
                lowercase.startsWith("reschedule") ||
                lowercase.contains("change due date of") ||
                lowercase.contains("update due date of") ||
                lowercase.contains("set due date of") ||
                lowercase.contains("move task") ||
                lowercase.contains("postpone task") ||
                lowercase.contains("postpone") ||
                lowercase.contains("defer task") ||
                lowercase.contains("delay task")

        if (isReschedule) {
            val newDate = parseRelativeDate(lowercase, currentDateStr)
            val targetTitle = extractTaskTitleFromScheduleQuery(query)
            if (targetTitle.isNotEmpty()) {
                return NlpIntentResult.ScheduleChange(
                    changeType = NlpIntentResult.ScheduleChangeType.RESCHEDULE_DUE_DATE,
                    targetTitle = targetTitle,
                    newDueDateString = newDate
                )
            }
        }

        // B. Change Priority
        val isPriorityChange = lowercase.contains("change priority of") ||
                lowercase.contains("set priority of") ||
                lowercase.contains("update priority of") ||
                (lowercase.startsWith("make task") && (lowercase.contains("priority") || lowercase.contains("urgent")))

        if (isPriorityChange) {
            val newPriority = when {
                lowercase.contains("high") || lowercase.contains("urgent") || lowercase.contains("top") -> "HIGH"
                lowercase.contains("low") -> "LOW"
                else -> "MEDIUM"
            }
            val targetTitle = extractTaskTitleFromScheduleQuery(query)
            if (targetTitle.isNotEmpty()) {
                return NlpIntentResult.ScheduleChange(
                    changeType = NlpIntentResult.ScheduleChangeType.CHANGE_PRIORITY,
                    targetTitle = targetTitle,
                    newPriority = newPriority
                )
            }
        }

        // C. Change Duration
        val isDurationChange = lowercase.contains("change duration of") ||
                lowercase.contains("set duration of") ||
                lowercase.contains("update duration of") ||
                lowercase.contains("change estimated time of") ||
                lowercase.contains("set est time of")

        if (isDurationChange) {
            val estMin = extractDurationMinutes(lowercase) ?: 30
            val targetTitle = extractTaskTitleFromScheduleQuery(query)
            if (targetTitle.isNotEmpty()) {
                return NlpIntentResult.ScheduleChange(
                    changeType = NlpIntentResult.ScheduleChangeType.CHANGE_DURATION,
                    targetTitle = targetTitle,
                    newEstMin = estMin
                )
            }
        }

        // D. Change Category / Move
        val isCategoryChange = lowercase.contains("change category of") ||
                (lowercase.contains("move task") && (lowercase.contains("to category") || lowercase.contains("to folder") || lowercase.contains("to list")))

        if (isCategoryChange) {
            val catPattern = Regex("(?i)to (?:category|folder|list)\\s+([a-zA-Z0-9_\\s]+)")
            val match = catPattern.find(query)
            val newCat = match?.groupValues?.get(1)?.trim() ?: "General"
            val targetTitle = extractTaskTitleFromScheduleQuery(query)
            if (targetTitle.isNotEmpty()) {
                return NlpIntentResult.ScheduleChange(
                    changeType = NlpIntentResult.ScheduleChangeType.CHANGE_CATEGORY,
                    targetTitle = targetTitle,
                    newCategory = newCat
                )
            }
        }

        // E. Complete / Finish Task
        val isCompletion = (lowercase.contains("mark task") && (lowercase.contains("complete") || lowercase.contains("done"))) ||
                (lowercase.contains("mark ") && lowercase.contains(" as done")) ||
                (lowercase.contains("mark ") && lowercase.contains(" as complete")) ||
                lowercase.startsWith("complete task") ||
                lowercase.startsWith("finish task")

        if (isCompletion) {
            val targetTitle = extractTaskTitleFromScheduleQuery(query)
            if (targetTitle.isNotEmpty()) {
                return NlpIntentResult.ScheduleChange(
                    changeType = NlpIntentResult.ScheduleChangeType.COMPLETE_TASK,
                    targetTitle = targetTitle
                )
            }
        }

        return null
    }

    private fun detectTaskCreationIntent(query: String, lowercase: String, currentDateStr: String): NlpIntentResult.TaskCreation? {
        val triggers = listOf(
            "add task", "create task", "new task", "todo", "remind me to", "add todo", "create todo",
            "schedule task", "i need to", "i have to", "i must", "i should", "schedule a task",
            "schedule a study session", "set a task to", "set a reminder to", "make a note to",
            "please create a task", "please add a task", "put ", "create a todo to", "add a task to"
        )

        val matchedTrigger = triggers.find { lowercase.startsWith(it) || lowercase.contains(" $it ") }
        val isExplicitTaskPrefix = lowercase.startsWith("add task") || lowercase.startsWith("create task") ||
                lowercase.startsWith("new task") || lowercase.startsWith("todo") ||
                lowercase.startsWith("remind me to") || lowercase.startsWith("add todo") ||
                lowercase.startsWith("create todo") || lowercase.startsWith("i need to") ||
                lowercase.startsWith("i have to") || lowercase.startsWith("schedule a task") ||
                lowercase.startsWith("schedule a study session") || lowercase.startsWith("set a task to") ||
                lowercase.startsWith("set a reminder to")

        if (!isExplicitTaskPrefix && matchedTrigger == null) return null

        var content = query
        val prefixesToStrip = listOf(
            "add task", "create task", "new task", "todo", "remind me to", "add todo", "create todo",
            "schedule task", "i need to", "i have to", "i must", "i should", "schedule a task for",
            "schedule a task to", "schedule a task", "schedule a study session for", "schedule a study session to",
            "schedule a study session", "set a task to", "set a reminder to", "make a note to",
            "please create a task to", "please create a task for", "please create a task",
            "please add a task to", "please add a task for", "please add a task", "create a todo to", "add a task to"
        )

        for (p in prefixesToStrip) {
            if (content.lowercase(Locale.ROOT).startsWith(p)) {
                content = content.substring(p.length).trim()
                break
            }
        }

        if (content.isEmpty()) return null

        // Extract priority
        var priority = "MEDIUM"
        val lowerContent = content.lowercase(Locale.ROOT)
        if (lowerContent.contains("priority high") || lowerContent.contains("high priority") || lowerContent.contains("urgent") || lowerContent.contains("top priority")) {
            priority = "HIGH"
            content = content.replace(Regex("(?i)\\b(priority high|high priority|urgent|top priority)\\b"), "").trim()
        } else if (lowerContent.contains("priority low") || lowerContent.contains("low priority")) {
            priority = "LOW"
            content = content.replace(Regex("(?i)\\b(priority low|low priority)\\b"), "").trim()
        }

        // Extract duration
        val estMin = extractDurationMinutes(content) ?: 25
        val minPattern = Regex("(?i)(\\d+)\\s*(mins|min|minutes|hrs|hr|hours)")
        content = content.replace(minPattern, "").trim()

        // Extract due date
        val dueDate = parseRelativeDate(content, currentDateStr)
        val datePhrases = listOf(
            "due today", "for today", "due tomorrow", "for tomorrow", "today", "tomorrow",
            "by friday", "by monday", "by tuesday", "by wednesday", "by thursday", "by saturday", "by sunday",
            "on friday", "on monday", "on tuesday", "on wednesday", "on thursday", "on saturday", "on sunday"
        )
        var cleanTitle = content
        for (dp in datePhrases) {
            cleanTitle = cleanTitle.replace(Regex("(?i)\\b$dp\\b"), "").trim()
        }

        // Extract Category
        var category = "Inbox"
        val catPattern = Regex("(?i)\\b(list|category|folder|subject)\\s+([a-zA-Z0-9_]+)\\b")
        val catMatch = catPattern.find(cleanTitle)
        if (catMatch != null) {
            category = catMatch.groupValues[2].trim()
            cleanTitle = cleanTitle.replace(catMatch.value, "").trim()
        } else {
            val lowerTitle = cleanTitle.lowercase(Locale.ROOT)
            category = when {
                lowerTitle.contains("study") || lowerTitle.contains("law") || lowerTitle.contains("audit") || lowerTitle.contains("tax") || lowerTitle.contains("chapter") || lowerTitle.contains("syllabus") || lowerTitle.contains("exam") -> "Study"
                lowerTitle.contains("work") || lowerTitle.contains("meeting") || lowerTitle.contains("office") || lowerTitle.contains("client") -> "Work"
                lowerTitle.contains("health") || lowerTitle.contains("gym") || lowerTitle.contains("run") || lowerTitle.contains("doctor") -> "Health"
                lowerTitle.contains("buy") || lowerTitle.contains("pay") || lowerTitle.contains("bank") || lowerTitle.contains("bill") -> "Finance"
                else -> "Inbox"
            }
        }

        cleanTitle = cleanTitle.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
        if (cleanTitle.isEmpty()) {
            cleanTitle = "New Task"
        }

        return NlpIntentResult.TaskCreation(
            title = cleanTitle,
            priority = priority,
            estMin = estMin,
            category = category,
            dueDateString = dueDate
        )
    }

    private fun extractTaskTitleFromScheduleQuery(query: String): String {
        var clean = query
        val phrasesToStrip = listOf(
            "reschedule task", "reschedule", "change due date of task", "change due date of",
            "update due date of task", "update due date of", "set due date of task", "set due date of",
            "move task", "postpone task", "postpone", "defer task", "delay task",
            "change priority of task", "change priority of", "set priority of task", "set priority of",
            "change duration of task", "change duration of", "set duration of task", "set duration of",
            "change category of task", "change category of", "mark task", "mark", "as complete", "as done",
            "complete task", "finish task"
        )
        val lower = query.lowercase(Locale.ROOT)
        for (p in phrasesToStrip) {
            if (lower.contains(p)) {
                clean = clean.replace(Regex("(?i)\\b$p\\b"), "").trim()
            }
        }
        clean = clean.replace(Regex("(?i)\\b(to|for|on|due|by)\\s+(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|high|low|medium|urgent|\\d+.*)\$"), "").trim()
        return clean.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
    }

    private fun extractDurationMinutes(text: String): Int? {
        val minPattern = Regex("(?i)(\\d+)\\s*(mins|min|minutes)")
        val hrPattern = Regex("(?i)(\\d+)\\s*(hrs|hr|hours)")

        val minMatch = minPattern.find(text)
        if (minMatch != null) {
            return minMatch.groupValues[1].toIntOrNull()
        }

        val hrMatch = hrPattern.find(text)
        if (hrMatch != null) {
            val hrs = hrMatch.groupValues[1].toIntOrNull() ?: 1
            return hrs * 60
        }

        return null
    }

    fun parseRelativeDate(text: String, currentDateStr: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val calendar = Calendar.getInstance()

        if (lower.contains("tomorrow") || lower.contains("for tomorrow") || lower.contains("due tomorrow")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return android.text.format.DateFormat.format("yyyy-MM-dd", calendar.time).toString()
        }

        if (lower.contains("today") || lower.contains("for today") || lower.contains("due today")) {
            return currentDateStr
        }

        val daysOfWeek = mapOf(
            "sunday" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY
        )

        for ((dayName, dayConst) in daysOfWeek) {
            if (lower.contains(dayName)) {
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                var daysUntil = dayConst - currentDayOfWeek
                if (daysUntil <= 0) {
                    daysUntil += 7
                }
                calendar.add(Calendar.DAY_OF_YEAR, daysUntil)
                return android.text.format.DateFormat.format("yyyy-MM-dd", calendar.time).toString()
            }
        }

        val dateRegex = Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b")
        val match = dateRegex.find(text)
        if (match != null) {
            return match.groupValues[1]
        }

        return currentDateStr
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
