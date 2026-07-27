package com.example.util

import android.content.Context
import java.util.Calendar

object SleepTimeHelper {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_WAKE_UP_TIME = "wake_up_time"
    private const val KEY_SLEEP_TIME = "sleep_time"

    fun isWakeUpAndSleepTimeSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wakeUp = prefs.getString(KEY_WAKE_UP_TIME, null)
        val sleep = prefs.getString(KEY_SLEEP_TIME, null)
        return !wakeUp.isNullOrBlank() && !sleep.isNullOrBlank()
    }

    fun getWakeUpTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WAKE_UP_TIME, null)
    }

    fun getSleepTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SLEEP_TIME, null)
    }

    fun setWakeUpTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WAKE_UP_TIME, time).apply()
    }

    fun setSleepTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SLEEP_TIME, time).apply()
    }

    fun isInSleepTime(context: Context): Boolean {
        if (!isWakeUpAndSleepTimeSet(context)) return false

        val wakeUp = getWakeUpTime(context) ?: return false
        val sleep = getSleepTime(context) ?: return false

        // Parse wakeUp "HH:mm"
        val wakeParts = wakeUp.split(":")
        if (wakeParts.size != 2) return false
        val wakeHour = wakeParts[0].toIntOrNull() ?: return false
        val wakeMin = wakeParts[1].toIntOrNull() ?: 0

        // Parse sleep "HH:mm"
        val sleepParts = sleep.split(":")
        if (sleepParts.size != 2) return false
        val sleepHour = sleepParts[0].toIntOrNull() ?: return false
        val sleepMin = sleepParts[1].toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)
        val curMin = cal.get(Calendar.MINUTE)

        val curMinutes = curHour * 60 + curMin
        val wakeMinutes = wakeHour * 60 + wakeMin
        val sleepMinutes = sleepHour * 60 + sleepMin

        return if (sleepMinutes < wakeMinutes) {
            // Sleep window is within the same calendar day (e.g. sleep 13:00 to 15:00)
            curMinutes in sleepMinutes until wakeMinutes
        } else {
            // Sleep window spans midnight (e.g. sleep 22:00 to 07:00)
            curMinutes >= sleepMinutes || curMinutes < wakeMinutes
        }
    }

    /**
     * Differentiates sleep time before 12 AM (midnight) and after 12 AM (midnight).
     * For today's time wasted calculation, only the portion of sleep occurring AFTER 12 AM (midnight)
     * belongs to today's elapsed time window (00:00 to now).
     */
    fun getTodaySleepMinutesPortion(context: Context? = null, totalLoggedSleepMins: Int): Int {
        if (totalLoggedSleepMins <= 0) return 0

        val sleepTime = if (context != null) getSleepTime(context) ?: "22:00" else "22:00"
        val wakeTime = if (context != null) getWakeUpTime(context) ?: "07:00" else "07:00"

        val sleepParts = sleepTime.split(":")
        val wakeParts = wakeTime.split(":")
        val sleepHour = sleepParts.getOrNull(0)?.toIntOrNull() ?: 22
        val sleepMin = sleepParts.getOrNull(1)?.toIntOrNull() ?: 0
        val wakeHour = wakeParts.getOrNull(0)?.toIntOrNull() ?: 7
        val wakeMin = wakeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val sleepMinsTotal = sleepHour * 60 + sleepMin
        val wakeMinsTotal = wakeHour * 60 + wakeMin

        if (sleepMinsTotal > wakeMinsTotal) {
            // Sleep window spans midnight (e.g. 22:00 to 07:00).
            // Sleep duration before midnight (12 AM) = (1440 - sleepMinsTotal).
            val minsBeforeMidnight = (1440 - sleepMinsTotal).coerceAtLeast(0)
            // Portion after midnight (today's elapsed window) = total - minsBeforeMidnight.
            val todayPortion = (totalLoggedSleepMins - minsBeforeMidnight).coerceIn(0, totalLoggedSleepMins)
            return todayPortion
        } else {
            // Same calendar day sleep (e.g. 01:00 to 08:00)
            return totalLoggedSleepMins
        }
    }
}
