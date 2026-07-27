package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ui.ReminderActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TaskReminderReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "task_reminders_channel"
    private val CHANNEL_NAME = "Task Reminders"

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("master_silent_mode", false) || 
            (prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false))) {
            Log.d("TaskReminderReceiver", "Silent/Sleep mode is active and app is closed. Suppressing background notification and full-screen activity.")
            return
        }

        val rawTaskId = intent.getIntExtra("TASK_ID", -1)

        // Handle Bedtime Reminder & Morning Wakeup Alarm
        if (rawTaskId == 20001 || rawTaskId == 20002) {
            val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Alarm"
            val taskTime = intent.getStringExtra("TASK_TIME") ?: ""
            val taskPriority = intent.getStringExtra("TASK_PRIORITY") ?: "HIGH"
            
            val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("TASK_ID", rawTaskId)
                putExtra("RAW_TASK_ID", rawTaskId)
                putExtra("TASK_TITLE", taskTitle)
                putExtra("TASK_TIME", taskTime)
                putExtra("TASK_PRIORITY", taskPriority)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sleep alarms and bedtime reminders"
                    enableLights(true)
                    enableVibration(true)
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                rawTaskId,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                .setContentTitle(taskTitle)
                .setContentText("Tap to open alarm controls.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
            
            notificationManager.notify(rawTaskId, builder.build())
            
            try {
                context.startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.e("TaskReminderReceiver", "Direct activity start omitted: ${e.message}")
            }
            
            // Re-schedule for tomorrow
            if (rawTaskId == 20001) {
                com.example.util.AlarmScheduler.scheduleBedtimeReminder(context)
            } else {
                com.example.util.AlarmScheduler.scheduleWakeUpAlarm(context)
            }
            return
        }

        if (com.example.util.SleepTimeHelper.isInSleepTime(context)) {
            Log.d("TaskReminderReceiver", "Currently in sleep time. Suppressing background notification and full-screen activity.")
            return
        }

        val isAllDayCheck = intent.getBooleanExtra("IS_ALL_DAY_CHECK", false) || (rawTaskId == 9999)
        if (isAllDayCheck) {
            Log.d("TaskReminderReceiver", "All-day task notification alarm fired.")
            triggerAllDayTasksNotification(context)
            // Re-schedule next all-day check
            com.example.util.AlarmScheduler.scheduleAllDayNotification(context)
            return
        }

        val isOnThisDayCheck = intent.getBooleanExtra("IS_ON_THIS_DAY_CHECK", false) || (rawTaskId == 10001)
        if (isOnThisDayCheck) {
            Log.d("TaskReminderReceiver", "On This Day notification alarm fired.")
            triggerOnThisDayNotification(context)
            // Re-schedule next on-this-day check
            com.example.util.AlarmScheduler.scheduleOnThisDayNotification(context)
            return
        }

        if (rawTaskId == -1) return
        val taskId = if (rawTaskId >= 100) rawTaskId / 100 else rawTaskId
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Task Reminder"
        val taskTime = intent.getStringExtra("TASK_TIME") ?: ""
        val taskPriority = intent.getStringExtra("TASK_PRIORITY") ?: "MEDIUM"

        Log.d("TaskReminderReceiver", "Alarm received! TaskId: $taskId, Title: $taskTitle, Priority: $taskPriority")

        if (taskId == -1) return

        val priority = taskPriority.uppercase()
        val notifKey = when (priority) {
            "HIGH" -> "task_high_notif"
            "MEDIUM" -> "task_medium_notif"
            else -> "task_low_notif"
        }
        val displayKey = when (priority) {
            "HIGH" -> "task_high_display"
            "MEDIUM" -> "task_medium_display"
            else -> "task_low_display"
        }

        val notifEnabled = prefs.getBoolean(notifKey, true)
        val displayEnabled = prefs.getBoolean(displayKey, true)

        Log.d("TaskReminderReceiver", "Priority: $priority, notifEnabled: $notifEnabled, displayEnabled: $displayEnabled")

        if (!notifEnabled && !displayEnabled) {
            Log.d("TaskReminderReceiver", "Both notification and display options are off for priority $priority. Aborting.")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Setup the full screen activity intent for all priorities (including NONE, LOW, MEDIUM, HIGH)
        val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TASK_ID", taskId)
            putExtra("RAW_TASK_ID", rawTaskId)
            putExtra("TASK_TITLE", taskTitle)
            putExtra("TASK_TIME", taskTime)
            putExtra("TASK_PRIORITY", taskPriority)
        }

        // 2. High-importance channel configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent full-screen notifications for scheduled task agendas"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            rawTaskId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Prepare notification if enabled
        if (notifEnabled) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                .setContentTitle("Remix: Life OS Reminder")
                .setContentText(taskTitle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)

            if (displayEnabled) {
                builder.setFullScreenIntent(pendingIntent, true)
                builder.setVibrate(longArrayOf(0, 500, 200, 500))
            } else {
                builder.setContentIntent(pendingIntent)
            }

            if (prefs.getBoolean("task_silent_mode", false)) {
                builder.setVibrate(null)
                builder.setSound(null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val silentChannelId = "task_reminders_silent_channel"
                    val silentChannel = NotificationChannel(silentChannelId, "Silent Task Reminders", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Silent task reminders"
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    notificationManager.createNotificationChannel(silentChannel)
                    builder.setChannelId(silentChannelId)
                }
            }

            notificationManager.notify(rawTaskId, builder.build())
        }

        // 4. Fire display directly as well if enabled
        if (displayEnabled) {
            try {
                context.startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.e("TaskReminderReceiver", "Direct activity start omitted by system bounds: ${e.message}")
            }
        }
    }

    private fun triggerAllDayTasksNotification(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = com.example.data.AppDatabase.getInstance(context)
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val tasksList = database.taskDao().getAllTasks().first()
                
                val pendingAllDayToday = tasksList.filter { task ->
                    !task.isCompleted && 
                    task.dueDateString == todayStr && 
                    (getTaskTime(task.description) == null)
                }
                
                if (pendingAllDayToday.isNotEmpty()) {
                    val count = pendingAllDayToday.size
                    showAllDayPendingNotification(context, count)
                }
            } catch (e: Exception) {
                Log.e("TaskReminderReceiver", "Error in triggerAllDayTasksNotification: ${e.message}", e)
            }
        }
    }

    private fun getTaskTime(description: String): String? {
        val metaTimePattern = Regex("""\[Time: ([^\]]+)\]""")
        val match = metaTimePattern.find(description)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun showAllDayPendingNotification(context: Context, count: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task notifications and reminders"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action to open app
        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "TASKS")
            putExtra("SHOW_TASKS_PAGE", true)
        }
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                8888,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setContentTitle("Today's All-Day Tasks")
            .setContentText("You have $count pending all-day task${if (count > 1) "s" else ""} for today!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
        
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(8888, builder.build())
    }

    private fun triggerOnThisDayNotification(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = com.example.data.AppDatabase.getInstance(context)
                val currentDayMonth = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date())
                val entriesList = database.journalDao().getAllJournalEntries().first()
                val matchedAnniversaryEntries = entriesList.filter { it.dateString.endsWith(currentDayMonth) }
                
                if (matchedAnniversaryEntries.isNotEmpty()) {
                    val count = matchedAnniversaryEntries.size
                    showOnThisDayNotification(context, count, matchedAnniversaryEntries.first().title)
                }
            } catch (e: Exception) {
                Log.e("TaskReminderReceiver", "Error in triggerOnThisDayNotification: ${e.message}", e)
            }
        }
    }

    private fun showOnThisDayNotification(context: Context, count: Int, firstTitle: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "On This Day notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "JOURNAL")
            putExtra("SHOW_JOURNAL_PAGE", true)
        }
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                10001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val descriptionText = if (count == 1) {
            "You have 1 entry from this day in history: \"$firstTitle\""
        } else {
            "You have $count entries from this day in history, starting with: \"$firstTitle\""
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setContentTitle("🎉 On This Day in History")
            .setContentText(descriptionText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descriptionText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
        
        val countdownPrefs = context.getSharedPreferences("countdown_settings_prefs", Context.MODE_PRIVATE)
        if (countdownPrefs.getBoolean("countdown_silent_mode", false)) {
            builder.setVibrate(null)
            builder.setSound(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val silentChannelId = "countdown_silent_channel"
                val silentChannel = NotificationChannel(silentChannelId, "Silent Countdowns", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Silent countdown reminders"
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(silentChannel)
                builder.setChannelId(silentChannelId)
            }
        }

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(10001, builder.build())
    }
}
