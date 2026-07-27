package com.example.util

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.Locale

data class AiActionLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val actionType: String, // "TASK_CREATED", "SCHEDULE_UPDATED", "FINANCE_LOGGED", "HEALTH_LOGGED", "CONTACT_CREATED", "JOURNAL_CREATED"
    val title: String,
    val details: String,
    val sourceQuery: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AiActionLogManager {

    private const val ACTION_LOGS_FILE = "ai_automated_action_logs.json"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logListAdapter = moshi.adapter<List<AiActionLogEntry>>(
        Types.newParameterizedType(List::class.java, AiActionLogEntry::class.java)
    )

    @Volatile
    private var logsCache: MutableList<AiActionLogEntry>? = null

    fun loadActionLogs(context: Context): List<AiActionLogEntry> {
        val cached = logsCache
        if (cached != null) return cached

        return synchronized(this) {
            val file = File(context.filesDir, ACTION_LOGS_FILE)
            if (!file.exists()) {
                val empty = mutableListOf<AiActionLogEntry>()
                logsCache = empty
                empty
            } else {
                try {
                    val json = file.readText()
                    val list = logListAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
                    logsCache = list
                    list
                } catch (e: Exception) {
                    android.util.Log.e("AiActionLogManager", "Failed to load action logs", e)
                    val empty = mutableListOf<AiActionLogEntry>()
                    logsCache = empty
                    empty
                }
            }
        }
    }

    private fun saveLogsDisk(context: Context, list: List<AiActionLogEntry>) {
        synchronized(this) {
            try {
                val file = File(context.filesDir, ACTION_LOGS_FILE)
                val json = logListAdapter.toJson(list)
                file.writeText(json)
                logsCache = list.toMutableList()
            } catch (e: Exception) {
                android.util.Log.e("AiActionLogManager", "Failed to save action logs", e)
            }
        }
    }

    fun recordAction(
        context: Context,
        actionType: String,
        title: String,
        details: String,
        sourceQuery: String,
        timestamp: Long = System.currentTimeMillis()
    ): AiActionLogEntry {
        val entry = AiActionLogEntry(
            actionType = actionType,
            title = title,
            details = details,
            sourceQuery = sourceQuery,
            timestamp = timestamp
        )

        val current = loadActionLogs(context).toMutableList()
        current.add(0, entry) // Newest first

        // Keep maximum 300 action log records
        if (current.size > 300) {
            while (current.size > 300) {
                current.removeAt(current.size - 1)
            }
        }

        saveLogsDisk(context, current)
        return entry
    }

    fun deleteLog(context: Context, logId: String): Boolean {
        val current = loadActionLogs(context).toMutableList()
        val removed = current.removeIf { it.id == logId }
        if (removed) {
            saveLogsDisk(context, current)
        }
        return removed
    }

    fun clearAllLogs(context: Context) {
        saveLogsDisk(context, emptyList())
    }
}
