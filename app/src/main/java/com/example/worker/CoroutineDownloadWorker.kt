package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * CoroutineWorker is the recommended implementation for Kotlin users.
 * CoroutineWorker instances expose a suspending function for background work.
 * By default, they run on Dispatchers.Default, which can be customized with withContext(Dispatchers.IO).
 * Stoppages are handled automatically by cancelling the coroutine.
 */
class CoroutineDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "CoroutineDownloadWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting CoroutineDownloadWorker execution on Dispatchers.IO...")
        val targetUrl = inputData.getString("url") ?: "https://www.google.com"
        val totalDownloads = inputData.getInt("repeat_count", 100)

        var downloadCount = 0

        for (i in 1..totalDownloads) {
            try {
                val data = downloadSynchronously(targetUrl)
                saveData(data)
                downloadCount++

                val progressPercent = (downloadCount * 100) / totalDownloads
                setProgress(workDataOf("progress" to progressPercent, "downloaded" to downloadCount))

                delay(40L) // Suspending delay - automatically supports coroutine cancellation when stopped
            } catch (e: Exception) {
                Log.e(TAG, "Coroutine download error: ${e.message}", e)
                return@withContext Result.failure(workDataOf("error" to (e.message ?: "Failed")))
            }
        }

        Log.d(TAG, "CoroutineDownloadWorker finished successfully with $downloadCount items.")
        Result.success(workDataOf("total_downloaded" to downloadCount, "worker_type" to "CoroutineWorker"))
    }

    private fun downloadSynchronously(url: String): String {
        return "Downloaded content from $url"
    }

    private fun saveData(data: String) {
        // Persist data or perform DB write
    }
}
