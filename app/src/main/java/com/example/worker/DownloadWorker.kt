package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException

/**
 * Worker is the simplest implementation of WorkManager.
 * WorkManager automatically runs it on a background thread from the configured Executor.
 * doWork() is a synchronous call - background work must be done in a blocking fashion.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val TAG = "DownloadWorker"

    override fun doWork(): Result {
        Log.d(TAG, "Starting synchronous DownloadWorker execution...")
        val targetUrl = inputData.getString("url") ?: "https://www.google.com"
        val totalDownloads = inputData.getInt("repeat_count", 100)

        var downloadCount = 0

        for (i in 1..totalDownloads) {
            // Checkpoint code when work is stopped or cancelled
            if (isStopped) {
                Log.w(TAG, "DownloadWorker stopped early at iteration $i of $totalDownloads.")
                break
            }

            try {
                // Simulate network operation synchronously
                downloadSynchronously(targetUrl)
                downloadCount++
                
                // Update progress for real-time UI tracking
                val progress = (downloadCount * 100) / totalDownloads
                setProgressAsync(workDataOf("progress" to progress, "downloaded" to downloadCount))
                
                // Slight delay to simulate actual chunked downloads during testing
                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed at iteration $i: ${e.message}", e)
                return Result.failure(workDataOf("error" to (e.message ?: "Download failed")))
            }
        }

        Log.d(TAG, "DownloadWorker completed $downloadCount downloads successfully.")
        return Result.success(workDataOf("total_downloaded" to downloadCount))
    }

    override fun onStopped() {
        super.onStopped()
        Log.i(TAG, "DownloadWorker onStopped() callback invoked. Freeing resources...")
    }

    private fun downloadSynchronously(url: String) {
        // Synchronous blocking work demonstration
        check(!url.isBlank()) { "URL cannot be blank" }
    }
}
