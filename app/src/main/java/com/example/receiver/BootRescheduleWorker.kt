package com.example.receiver

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.util.AlarmScheduler

class BootRescheduleWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BootRescheduleWorker", "Starting rescheduled reminders and notifications work...")
        return try {
            val db = AppDatabase.getInstance(appContext)
            // Retrieve active tasks with deadlines directly to avoid OOM
            val tasks = db.taskDao().getActiveTasksWithDeadlines()
            
            Log.d("BootRescheduleWorker", "Rescheduling reminders for ${tasks.size} tasks on device initialization")
            tasks.forEach { task ->
                AlarmScheduler.scheduleReminder(appContext, task)
            }
            AlarmScheduler.scheduleAllDayNotification(appContext)
            
            Result.success()
        } catch (e: Exception) {
            Log.e("BootRescheduleWorker", "Error rescheduling: ${e.message}", e)
            Result.retry()
        }
    }
}
