package com.example.worker

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteWorkerService
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * RemoteCoroutineWorker binds to a specific isolated process (e.g. :worker1)
 * specified by ARGUMENT_PACKAGE_NAME and ARGUMENT_CLASS_NAME in its input data.
 * Useful for CPU-heavy tasks, memory-isolated operations, or multi-process architectures.
 */
class ExampleRemoteCoroutineWorker(
    context: Context,
    params: WorkerParameters
) : RemoteCoroutineWorker(context, params) {

    private val TAG = "RemoteCoroutineWorker"

    override suspend fun doRemoteWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing RemoteCoroutineWorker in isolated process...")
        val targetUrl = inputData.getString("url") ?: "https://www.google.com"
        val totalDownloads = inputData.getInt("repeat_count", 50)

        var count = 0
        for (i in 1..totalDownloads) {
            delay(30L)
            count++
            setProgress(workDataOf("progress" to (count * 100 / totalDownloads), "remote_count" to count))
        }

        Log.d(TAG, "RemoteCoroutineWorker completed $count tasks in separate process.")
        Result.success(workDataOf("total_remote_downloads" to count, "process" to ":worker1"))
    }

    companion object {
        fun buildWorkRequest(context: Context, url: String, repeatCount: Int = 50): OneTimeWorkRequest {
            val packageName = context.packageName
            val serviceName = RemoteWorkerService::class.java.name
            val componentName = ComponentName(packageName, serviceName)

            val inputData = Data.Builder()
                .putString(ARGUMENT_PACKAGE_NAME, componentName.packageName)
                .putString(ARGUMENT_CLASS_NAME, componentName.className)
                .putString("url", url)
                .putInt("repeat_count", repeatCount)
                .build()

            return OneTimeWorkRequest.Builder(ExampleRemoteCoroutineWorker::class.java)
                .setInputData(inputData)
                .addTag("RemoteWorker")
                .build()
        }
    }
}
