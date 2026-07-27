package com.example.worker

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.util.concurrent.Executors

/**
 * ListenableWorker is the base class for Worker, CoroutineWorker, and RxWorker.
 * It is intended for callback-based asynchronous APIs where you manually signal completion
 * via a ListenableFuture and add cancellation listeners when stopped.
 */
class CallbackWorker(
    context: Context,
    params: WorkerParameters
) : ListenableWorker(context, params) {

    private val TAG = "CallbackWorker"
    private val executor = Executors.newSingleThreadExecutor()

    interface AsyncCallback {
        fun onFailure(e: IOException)
        fun onResponse(response: String)
    }

    override fun startWork(): ListenableFuture<Result> {
        Log.d(TAG, "Starting CallbackWorker using CallbackToFutureAdapter...")
        val targetUrl = inputData.getString("url") ?: "https://example.com"
        val totalDownloads = inputData.getInt("repeat_count", 100)

        return CallbackToFutureAdapter.getFuture { completer ->
            val cancelDownloadsRunnable = Runnable {
                Log.w(TAG, "Cancellation listener triggered on CallbackWorker! Cleaning up executor...")
                executor.shutdownNow()
            }

            completer.addCancellationListener(cancelDownloadsRunnable, executor)

            val callback = object : AsyncCallback {
                private var successes = 0

                override fun onFailure(e: IOException) {
                    Log.e(TAG, "CallbackWorker failed: ${e.message}", e)
                    completer.setException(e)
                }

                override fun onResponse(response: String) {
                    successes++
                    if (successes >= totalDownloads) {
                        Log.d(TAG, "CallbackWorker successfully completed $successes async callbacks.")
                        completer.set(Result.success())
                    }
                }
            }

            executor.execute {
                for (i in 1..totalDownloads) {
                    if (executor.isShutdown) break
                    downloadAsynchronously(targetUrl, callback)
                    try {
                        Thread.sleep(30)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }

            "CallbackWorker_Execution_Task"
        }
    }

    private fun downloadAsynchronously(url: String, callback: AsyncCallback) {
        if (url.startsWith("http")) {
            callback.onResponse("OK: $url")
        } else {
            callback.onFailure(IOException("Invalid URL format: $url"))
        }
    }

    override fun onStopped() {
        super.onStopped()
        executor.shutdownNow()
        Log.i(TAG, "CallbackWorker onStopped() invoked. Executor terminated.")
    }
}
