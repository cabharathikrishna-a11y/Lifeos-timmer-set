package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object UrgentNotificationHelper {

    const val CHANNEL_HIGH_PRIORITY_ID = "high_priority_notifications_channel"
    const val CHANNEL_URGENT_ALERTS_ID = "urgent_alerts_channel"
    const val CHANNEL_CUSTOM_DECOR_ID = "custom_decor_notifications_channel"

    /**
     * Creates notification channels required for high-priority, urgent time-sensitive,
     * and custom decorated view notifications. Call this in Application onCreate.
     */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // 1. High priority notification channel
            val highPriorityChannel = NotificationChannel(
                CHANNEL_HIGH_PRIORITY_ID,
                "High Priority Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts, alarms, and time-sensitive reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            }

            // 2. Urgent alerts channel (ongoing calls / alarms / time-sensitive alerts)
            val urgentAlertsChannel = NotificationChannel(
                CHANNEL_URGENT_ALERTS_ID,
                "Urgent Time-Sensitive Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ongoing time-sensitive alerts, live updates, and urgent events"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
            }

            // 3. Custom Decorated notification channel
            val customDecorChannel = NotificationChannel(
                CHANNEL_CUSTOM_DECOR_ID,
                "Custom Styled Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom layout notifications with DecoratedCustomViewStyle"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(highPriorityChannel)
            notificationManager.createNotificationChannel(urgentAlertsChannel)
            notificationManager.createNotificationChannel(customDecorChannel)
        }
    }

    /**
     * Sends a standard high-priority notification with priority and category tags.
     */
    fun showHighPriorityNotification(
        context: Context,
        title: String = "HIGH PRIORITY ALERT",
        content: String = "Important time-sensitive update requires your attention!",
        category: String = NotificationCompat.CATEGORY_RECOMMENDATION,
        notificationId: Int = 1001
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "NOTIFICATIONS_STUDIO")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_HIGH_PRIORITY_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Creates and displays a notification using NotificationCompat.DecoratedCustomViewStyle
     * and custom RemoteViews layouts for small (collapsed) and large (expanded) states.
     */
    fun showCustomDecoratedNotification(
        context: Context,
        title: String = "Custom Styled Notification",
        body: String = "This is an expanded custom notification using DecoratedCustomViewStyle with standard system typography.",
        notificationId: Int = 1002
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Inflate custom layouts via RemoteViews
        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_small).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_body, body)
        }

        val notificationLayoutExpanded = RemoteViews(context.packageName, R.layout.notification_large).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_body, body)
            setTextViewText(R.id.notification_footer, "Decorated Custom View • Life OS Urgent Engine")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "NOTIFICATIONS_STUDIO")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val customNotification = NotificationCompat.Builder(context, CHANNEL_CUSTOM_DECOR_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationLayout)
            .setCustomBigContentView(notificationLayoutExpanded)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, customNotification)
    }

    /**
     * Shows an ongoing time-sensitive notification (e.g. active call / alarm / live update).
     */
    fun showOngoingTimeSensitiveNotification(
        context: Context,
        title: String = "Ongoing Urgent Task",
        body: String = "Active session or alarm is running in background.",
        subtext: String = "Live Update",
        notificationId: Int = 1003
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "NOTIFICATIONS_STUDIO")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT_ALERTS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(subtext)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    Intent(context, com.example.receiver.TimerAlertReceiver::class.java).apply {
                        action = "DISMISS_NOTIFICATION"
                        putExtra("NOTIFICATION_ID", notificationId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Dismisses a specific notification ID or all test notifications.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * Checks if POST_NOTIFICATIONS runtime permission is granted on Android 13+ or notifications are enabled.
     */
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        }
    }
}
