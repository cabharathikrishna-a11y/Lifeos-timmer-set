package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancelAll()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to cancel old notifications: ${e.message}")
            }

            // Start the persistent foreground service if enabled to prevent sleep/termination
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_device_boot_timestamp", System.currentTimeMillis()).apply()
            if (prefs.getBoolean("keep_notification_enabled", true)) {
                com.example.service.KeepAliveService.start(context)
            }

            try {
                Log.d("BootReceiver", "Enqueueing BootRescheduleWorker using WorkManager")
                val workRequest = OneTimeWorkRequestBuilder<BootRescheduleWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "BootRescheduleWorkerUnique",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to enqueue BootRescheduleWorker via WorkManager", e)
            }
        }
    }
}
