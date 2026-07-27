package com.example.api

import android.content.Context
import android.util.Log
import com.example.util.TimeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsCalibrationEngine {
    private const val TAG = "SettingsCalibrationEngine"

    suspend fun calibrateSettings(context: Context, email: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val initialized = prefs.contains("pomodoro_focus_duration_mins")
            if (!initialized) {
                Log.d(TAG, "Initializing default local settings...")
                val trueTime = TimeEngine.getTrueTimeMs()
                val generatedTsStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(trueTime))
                prefs.edit()
                    .putInt("pomodoro_focus_duration_mins", 25)
                    .putInt("pomodoro_break_duration_mins", 5)
                    .putBoolean("stopwatch_break_auto_start_enabled", false)
                    .putInt("stopwatch_break_duration_mins", 5)
                    .putBoolean("auto_start_break_after_focus", true)
                    .putBoolean("auto_start_focus_after_break", true)
                    .putBoolean("timer_autostart_break", true)
                    .putBoolean("timer_autostart_pomo", true)
                    .putInt("timer_duration", 25)
                    .putInt("break_duration", 5)
                    .putInt("stopwatch_break_duration", 5)
                    .putString("last_synced_settings_ts", generatedTsStr)
                    .apply()
            } else {
                Log.d(TAG, "Settings are already initialized locally.")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings locally", e)
            return false
        }
    }
}
