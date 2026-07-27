package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.LocalAiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgressState(
    val modelId: String? = null,
    val progress: Float = 0f, // 0.0 to 1.0
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMBps: Double = 0.0,
    val statusText: String = "",
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

object ModelDownloadManager {

    private val _downloadState = MutableStateFlow(DownloadProgressState())
    val downloadState: StateFlow<DownloadProgressState> = _downloadState.asStateFlow()

    private var activeJob: Job? = null

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "ai_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isModelDownloaded(context: Context, model: LocalAiModel): Boolean {
        val file = File(getModelsDir(context), model.fileName)
        return file.exists() && file.length() > 1024 * 1024 // at least 1MB
    }

    fun getDownloadedModelFile(context: Context, model: LocalAiModel): File? {
        val file = File(getModelsDir(context), model.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun deleteModel(context: Context, model: LocalAiModel): Boolean {
        val file = File(getModelsDir(context), model.fileName)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
        _downloadState.value = DownloadProgressState(
            isDownloading = false,
            statusText = "Download cancelled by user."
        )
    }

    fun startDownload(
        context: Context,
        model: LocalAiModel,
        customUrl: String? = null,
        coroutineScope: CoroutineScope,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_downloadState.value.isDownloading) {
            onComplete(false, "Another model download is already in progress.")
            return
        }

        val urlStr = customUrl?.takeIf { it.isNotBlank() } ?: model.downloadUrl
        val destFile = File(getModelsDir(context), model.fileName)

        _downloadState.value = DownloadProgressState(
            modelId = model.id,
            progress = 0f,
            statusText = "Connecting to model repository...",
            isDownloading = true
        )

        activeJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP response code $responseCode")
                }

                val contentLength = connection.contentLengthLong.let {
                    if (it > 0) it else (model.sizeGb * 1024 * 1024 * 1024).toLong()
                }

                val inputStream: InputStream = connection.inputStream
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var startTime = System.currentTimeMillis()
                var lastTime = startTime
                var lastBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!activeJob!!.isActive) {
                        outputStream.close()
                        inputStream.close()
                        if (destFile.exists()) destFile.delete()
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiffSec = (now - lastTime) / 1000.0

                    var speedMBps = _downloadState.value.speedMBps
                    if (timeDiffSec >= 0.5) {
                        val bytesSinceLast = totalBytesRead - lastBytesRead
                        speedMBps = (bytesSinceLast / (1024.0 * 1024.0)) / timeDiffSec
                        lastTime = now
                        lastBytesRead = totalBytesRead
                    }

                    val progress = if (contentLength > 0) {
                        (totalBytesRead.toDouble() / contentLength.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else 0.5f

                    val mbRead = (totalBytesRead / (1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }
                    val totalMb = (contentLength / (1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }

                    _downloadState.value = DownloadProgressState(
                        modelId = model.id,
                        progress = progress,
                        bytesDownloaded = totalBytesRead,
                        totalBytes = contentLength,
                        speedMBps = speedMBps,
                        statusText = "Downloading ${model.name} (${mbRead} MB / ${totalMb} MB)",
                        isDownloading = true
                    )
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadState.value = DownloadProgressState(
                    modelId = model.id,
                    progress = 1.0f,
                    statusText = "Download completed successfully!",
                    isDownloading = false,
                    isCompleted = true
                )

                withContext(Dispatchers.Main) {
                    onComplete(true, null)
                }
            } catch (e: Exception) {
                if (destFile.exists()) {
                    destFile.delete()
                }
                val errMsg = e.localizedMessage ?: "Failed to download model file."
                _downloadState.value = DownloadProgressState(
                    modelId = model.id,
                    progress = 0f,
                    statusText = "Download failed: $errMsg",
                    isDownloading = false,
                    error = errMsg
                )
                withContext(Dispatchers.Main) {
                    onComplete(false, errMsg)
                }
            }
        }
    }
}
