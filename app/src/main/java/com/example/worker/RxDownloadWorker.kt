package com.example.worker

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * RxWorker / Reactive Stream Worker implementation.
 * Provides interoperability for reactive stream execution strategies.
 * Returns a ListenableFuture/Single indicating the Result of your execution.
 */
class RxDownloadWorker(
    context: Context,
    params: WorkerParameters
) : ListenableWorker(context, params) {

    private val TAG = "RxDownloadWorker"

    override fun startWork(): ListenableFuture<Result> {
        Log.d(TAG, "Starting RxDownloadWorker execution using reactive range pipeline...")
        val targetUrl = inputData.getString("url") ?: "https://www.example.com"
        val count = inputData.getInt("repeat_count", 100)

        return CallbackToFutureAdapter.getFuture { completer ->
            try {
                // Reactive stream execution equivalent
                val results = runBlocking {
                    (0 until count).asFlow()
                        .map { index ->
                            download(targetUrl)
                        }
                        .toList()
                }

                Log.d(TAG, "RxDownloadWorker finished reactive pipeline with ${results.size} items.")
                completer.set(Result.success(workDataOf("downloaded_count" to results.size)))
            } catch (e: Exception) {
                Log.e(TAG, "RxDownloadWorker execution error: ${e.message}", e)
                completer.setException(e)
            }

            "RxDownloadWorker_Pipeline"
        }
    }

    private fun download(url: String): String {
        return "RxDownloaded $url"
    }
}
