package com.example.api

import android.content.Context
import android.util.Log
import com.example.util.TimeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object FocusLogManager {
    private const val TAG = "FocusLogManager"
    private const val PREF_NAME = "focus_logs_prefs"
    private const val KEY_LOGS = "focus_logs_list"
    private const val MAX_LOG_LINES = 100

    private val executor = Executors.newSingleThreadExecutor()

    fun logEvent(context: Context, message: String) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existing = prefs.getString(KEY_LOGS, "") ?: ""
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestamp = sdf.format(Date(TimeEngine.getTrueTimeMs()))
                val logLine = "[$timestamp] $message"
                
                val lines = if (existing.isEmpty()) {
                    mutableListOf(logLine)
                } else {
                    val list = existing.split("\n").toMutableList()
                    list.add(0, logLine)
                    if (list.size > MAX_LOG_LINES) {
                        list.subList(0, MAX_LOG_LINES).toMutableList()
                    } else {
                        list
                    }
                }
                val updated = lines.joinToString("\n")
                prefs.edit().putString(KEY_LOGS, updated).apply()
                Log.d(TAG, "Logged event: $logLine")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing focus log", e)
            }
        }
    }

    fun getLogs(context: Context): List<String> {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_LOGS, "") ?: ""
            if (existing.isEmpty()) {
                listOf("No focus log history yet.")
            } else {
                existing.split("\n")
            }
        } catch (e: Exception) {
            listOf("No focus log history yet.")
        }
    }
    
    fun clearLogs(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LOGS).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing focus logs", e)
            }
        }
    }
}
