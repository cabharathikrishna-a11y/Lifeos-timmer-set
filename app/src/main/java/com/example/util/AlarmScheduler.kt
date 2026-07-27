package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.data.Task
import com.example.receiver.TaskReminderReceiver
import java.text.SimpleDateFormat
import java.util.*

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    
    const val BEDTIME_REMINDER_REQUEST_CODE = 20001
    const val WAKEUP_ALARM_REQUEST_CODE = 20002

    fun scheduleBedtimeReminder(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("bedtime_reminder_enabled", true)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("TASK_ID", BEDTIME_REMINDER_REQUEST_CODE)
            putExtra("TASK_TITLE", "Bedtime Reminder! 🌙")
            putExtra("TASK_TIME", "")
            putExtra("TASK_PRIORITY", "HIGH")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            BEDTIME_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling bedtime alarm", e)
        }

        if (!enabled) {
            Log.d(TAG, "Bedtime reminder is disabled.")
            return
        }

        val sleepTime = SleepTimeHelper.getSleepTime(context) ?: "22:00"
        val triggerTimeMs = calculateNextNotificationTimeMs(sleepTime)
        if (triggerTimeMs != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
                Log.d(TAG, "Scheduled bedtime reminder at $triggerTimeMs (Time: $sleepTime)")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling bedtime alarm", e)
            }
        }
    }

    fun scheduleWakeUpAlarm(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("wakeup_alarm_enabled", false)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("TASK_ID", WAKEUP_ALARM_REQUEST_CODE)
            putExtra("TASK_TITLE", "Wake up Alarm! ☀️")
            putExtra("TASK_TIME", "")
            putExtra("TASK_PRIORITY", "HIGH")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WAKEUP_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling wakeup alarm", e)
        }

        if (!enabled) {
            Log.d(TAG, "Wakeup alarm is disabled.")
            return
        }

        val wakeUpTime = SleepTimeHelper.getWakeUpTime(context) ?: "07:00"
        val triggerTimeMs = calculateNextNotificationTimeMs(wakeUpTime)
        if (triggerTimeMs != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
                Log.d(TAG, "Scheduled wakeup alarm at $triggerTimeMs (Time: $wakeUpTime)")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling wakeup alarm", e)
            }
        }
    }

    fun scheduleReminder(context: Context, task: Task) {
        // First cancel all previous alarms for this task so they don't leak or duplicate
        cancelReminder(context, task.id)

        if (task.isCompleted) {
            return
        }

        val dueDate = task.dueDateString
        val timeStr = getTaskTime(task.description) ?: return // only schedule if exact due date & time is set!

        val mainTriggerTimeMs = parseDateTime(dueDate, timeStr) ?: return

        // 1. Schedule main task alarm (at the exact due time) under request code `task.id * 100`
        if (mainTriggerTimeMs >= System.currentTimeMillis() - 60_000) {
            scheduleExactAlarm(context, task.id * 100, task.title, timeStr, task.priority, mainTriggerTimeMs)
            Log.d(TAG, "Scheduled main exact alarm for task ${task.id} at $mainTriggerTimeMs (Time: $timeStr, Priority: ${task.priority})")
        }

        // 2. Parse and schedule all custom reminders & additional global settings reminders under unique sub-ids
        val taskReminders = getTaskReminders(task.description)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val additionalRemindersStr = prefs.getString("additional_reminder_times", "") ?: ""
        val globalReminders = additionalRemindersStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "None" }
        
        val combinedReminders = (taskReminders + globalReminders).distinct()

        combinedReminders.forEachIndexed { index, reminderStr ->
            val offsetMs = parseReminderOffsetMs(reminderStr)
            if (offsetMs != null) {
                val reminderTriggerTimeMs = mainTriggerTimeMs - offsetMs
                // Allow a small grace window of past alarms in case of tight edits
                if (reminderTriggerTimeMs >= System.currentTimeMillis() - 10_000) {
                    val reminderRequestCode = task.id * 100 + (index + 1)
                    val reminderTimeStr = formatTimeFromTimestamp(reminderTriggerTimeMs)
                    scheduleExactAlarm(
                        context = context,
                        taskId = reminderRequestCode,
                        taskTitle = "${task.title} (Reminder: $reminderStr)",
                        taskTime = reminderTimeStr,
                        taskPriority = task.priority,
                        triggerTimeMs = reminderTriggerTimeMs
                    )
                    Log.d(TAG, "Scheduled relative reminder '$reminderStr' for task ${task.id} at $reminderTriggerTimeMs (formatted time: $reminderTimeStr)")
                } else {
                    Log.d(TAG, "Relative reminder '$reminderStr' starts in the past, skipping scheduling.")
                }
            }
        }
    }

    fun scheduleSnooze(context: Context, taskId: Int, taskTitle: String, taskTime: String, taskPriority: String = "MEDIUM", snoozeDurationMinutes: Int = 5) {
        val triggerTimeMs = System.currentTimeMillis() + snoozeDurationMinutes * 60 * 1000L
        scheduleExactAlarm(context, taskId, taskTitle, taskTime, taskPriority, triggerTimeMs)
    }

    private fun scheduleExactAlarm(context: Context, taskId: Int, taskTitle: String, taskTime: String, taskPriority: String, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", taskTitle)
            putExtra("TASK_TIME", taskTime)
            putExtra("TASK_PRIORITY", taskPriority)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for request code $taskId at $triggerTimeMs")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled inexact alarm for request code $taskId (no exact alarm permission) at $triggerTimeMs")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for request code $taskId at $triggerTimeMs")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for request code $taskId: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java)

        // Cancel the legacy single-taskId alarm
        cancelSpecificAlarm(context, alarmManager, intent, taskId)

        // Cancel the task main alarm (taskId * 100)
        cancelSpecificAlarm(context, alarmManager, intent, taskId * 100)

        // Cancel any associated index alarms (taskId * 100 + i)
        for (i in 1..30) {
            cancelSpecificAlarm(context, alarmManager, intent, taskId * 100 + i)
        }
    }

    private fun cancelSpecificAlarm(context: Context, alarmManager: AlarmManager, intent: Intent, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm request code $requestCode")
        }
    }

    private fun getTaskTime(description: String): String? {
        val metaTimePattern = Regex("""\[Time: ([^\]]+)\]""")
        val match = metaTimePattern.find(description)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun getTaskReminders(description: String): List<String> {
        val metaRemindersPattern = Regex("""\[Reminders: ([^\]]+)\]""")
        val match = metaRemindersPattern.find(description) ?: return emptyList()
        val content = match.groupValues[1]
        return content.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "None" }
    }

    private fun parseReminderOffsetMs(reminderStr: String): Long? {
        val clean = reminderStr.lowercase().replace(" before", "").trim()
        val parts = clean.split(" ")
        if (parts.size < 2) return null
        val num = parts[0].toLongOrNull() ?: return null
        val unit = parts[1]
        return when {
            unit.startsWith("min") -> num * 60 * 1000L
            unit.startsWith("hour") -> num * 60 * 60 * 1000L
            unit.startsWith("day") -> num * 24 * 60 * 60 * 1000L
            else -> null
        }
    }

    private fun formatTimeFromTimestamp(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.US)
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseDateTime(dateStr: String, timeStr: String): Long? {
        if (dateStr.isEmpty() || timeStr.isEmpty() || timeStr == "None") return null
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US),
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        )
        for (f in formats) {
            try {
                return f.parse("$dateStr $timeStr")?.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return null
    }

    fun scheduleAllDayNotification(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("all_day_notification_enabled", false)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("TASK_ID", 9999)
            putExtra("TASK_TITLE", "All-day Pending Tasks Notification")
            putExtra("TASK_TIME", "")
            putExtra("TASK_PRIORITY", "MEDIUM")
            putExtra("IS_ALL_DAY_CHECK", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel existing
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling existing all-day alarm", e)
        }

        if (!enabled) {
            Log.d(TAG, "All-day pending tasks notification is disabled, cancelled alarm.")
            return
        }

        val timeStr = prefs.getString("all_day_notification_time", "09:00 AM") ?: "09:00 AM"
        val triggerTimeMs = calculateNextNotificationTimeMs(timeStr)
        if (triggerTimeMs != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled next all-day pending check alarm at $triggerTimeMs (Formatted: $timeStr)")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling all-day pending check alarm: ${e.message}")
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed fallback scheduling all-day alarm", ex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling all-day pending alarm", e)
            }
        }
    }

    fun scheduleOnThisDayNotification(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("on_this_day_notification_enabled", false)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("TASK_ID", 10001)
            putExtra("TASK_TITLE", "On This Day Anniversary Notification")
            putExtra("TASK_TIME", "")
            putExtra("TASK_PRIORITY", "MEDIUM")
            putExtra("IS_ON_THIS_DAY_CHECK", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            10001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel existing
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling existing on-this-day alarm", e)
        }

        if (!enabled) {
            Log.d(TAG, "On-this-day notification is disabled, cancelled alarm.")
            return
        }

        val timeStr = prefs.getString("on_this_day_notification_time", "09:00 AM") ?: "09:00 AM"
        val triggerTimeMs = calculateNextNotificationTimeMs(timeStr)
        if (triggerTimeMs != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled next on-this-day check alarm at $triggerTimeMs (Formatted: $timeStr)")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling on-this-day check alarm: ${e.message}")
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed fallback scheduling on-this-day alarm", ex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling on-this-day checklist alarm", e)
            }
        }
    }

    private fun calculateNextNotificationTimeMs(timeStr: String): Long? {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        var parsedDate = try {
            sdf.parse(timeStr)
        } catch (e: Exception) {
            null
        }

        if (parsedDate == null) {
            parsedDate = try {
                SimpleDateFormat("HH:mm", Locale.US).parse(timeStr)
            } catch (e: Exception) {
                null
            }
        }

        if (parsedDate == null) {
            parsedDate = try {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(timeStr)
            } catch (e: Exception) {
                null
            }
        }

        if (parsedDate == null) return null

        val parsedCal = Calendar.getInstance().apply {
            time = parsedDate
        }

        val now = Calendar.getInstance()
        val triggerCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (triggerCal.before(now)) {
            triggerCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return triggerCal.timeInMillis
    }

    const val TIMER_ALARM_REQUEST_CODE = 99999

    fun scheduleTimerEndAlarm(context: Context, durationSeconds: Int, isFocusPhase: Boolean = true) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, com.example.receiver.TimerAlertReceiver::class.java).apply {
            action = "com.example.action.TIMER_FINISHED"
            putExtra("is_focus_phase", isFocusPhase)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeRealtime = SystemClock.elapsedRealtime() + (durationSeconds * 1000L)

        // Android 12+ / 14+ Security Check: Ensure we are allowed to schedule exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w("AlarmScheduler", "Exact alarm permission missing. Falling back to setAndAllowWhileIdle.")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTimeRealtime,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e("AlarmScheduler", "Failed to schedule fallback timer end alarm", e)
            }
            return
        }

        // Wakes up the CPU even if it is in deep sleep Doze mode.
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTimeRealtime,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Unbreakable timer alarm scheduled for $durationSeconds seconds from now.")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Failed to set exact alarm: ${e.message}. Falling back to setAndAllowWhileIdle...")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTimeRealtime,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e("AlarmScheduler", "Failed fallback setAndAllowWhileIdle", ex)
            }
        }
    }

    fun cancelTimerEndAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.example.receiver.TimerAlertReceiver::class.java).apply {
            action = "com.example.action.TIMER_FINISHED"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
