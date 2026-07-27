package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object DriveUrlUtil {

    private const val TAG = "DriveUrlUtil"

    data class DriveAttachmentInfo(
        val type: String, // "voice", "audio", "video", "image", "doc"
        val directUrl: String,
        val fileName: String,
        val durationSec: Int = 0,
        val fileSizeKb: Int = 0
    )

    /**
     * Formats a Drive sharing attachment into a standard formatted text string payload.
     * Format: [ATTACHMENT:type|url|fileName|durationSec|fileSizeKb]
     */
    fun formatAttachmentText(
        type: String,
        driveSharingUrl: String,
        fileName: String,
        durationSec: Int = 0,
        fileSizeKb: Int = 0
    ): String {
        val directUrl = toDirectDownloadUrl(driveSharingUrl)
        return "[ATTACHMENT:$type|$directUrl|$fileName|$durationSec|$fileSizeKb]"
    }

    /**
     * Parses a text message to detect if it is a formatted Drive attachment string.
     */
    fun parseAttachmentText(rawText: String): DriveAttachmentInfo? {
        if (!rawText.startsWith("[ATTACHMENT:") || !rawText.endsWith("]")) {
            // Also check for raw drive link containing keywords
            if ((rawText.contains("drive.google.com") || rawText.contains("docs.google.com"))) {
                val direct = toDirectDownloadUrl(rawText.trim())
                val lower = rawText.lowercase()
                val type = when {
                    lower.endsWith(".mp3") || lower.contains("audio") || lower.contains("voice") -> "voice"
                    lower.endsWith(".mp4") || lower.contains("video") -> "video"
                    lower.endsWith(".jpg") || lower.endsWith(".png") || lower.contains("image") -> "image"
                    else -> "doc"
                }
                return DriveAttachmentInfo(type = type, directUrl = direct, fileName = "drive_file")
            }
            return null
        }

        try {
            val inner = rawText.substring("[ATTACHMENT:".length, rawText.length - 1)
            val parts = inner.split("|")
            if (parts.size >= 3) {
                val type = parts[0]
                val directUrl = toDirectDownloadUrl(parts[1])
                val fileName = parts[2]
                val durationSec = parts.getOrNull(3)?.toIntOrNull() ?: 0
                val fileSizeKb = parts.getOrNull(4)?.toIntOrNull() ?: 0
                return DriveAttachmentInfo(
                    type = type,
                    directUrl = directUrl,
                    fileName = fileName,
                    durationSec = durationSec,
                    fileSizeKb = fileSizeKb
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing attachment text: $rawText", e)
        }
        return null
    }

    /**
     * Converts Google Drive web view/sharing links into direct export download URLs.
     * Example input: https://drive.google.com/file/d/1A2B3C4D5E6F7G8H9/view?usp=sharing
     * Output: https://drive.google.com/uc?export=download&id=1A2B3C4D5E6F7G8H9
     */
    fun toDirectDownloadUrl(url: String): String {
        if (url.isBlank()) return url
        if (!url.contains("drive.google.com") && !url.contains("docs.google.com")) {
            return url
        }

        val patternFileD = Pattern.compile("/file/d/([a-zA-Z0-9_-]+)")
        val matcherFileD = patternFileD.matcher(url)
        if (matcherFileD.find()) {
            val fileId = matcherFileD.group(1)
            if (!fileId.isNullOrEmpty()) {
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
        }

        val patternIdParam = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)")
        val matcherIdParam = patternIdParam.matcher(url)
        if (matcherIdParam.find()) {
            val fileId = matcherIdParam.group(1)
            if (!fileId.isNullOrEmpty()) {
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
        }

        return url
    }

    /**
     * Silently downloads media/doc/voice payloads to local device storage and returns local File path.
     */
    suspend fun downloadMediaToLocal(
        context: Context,
        contentUrl: String,
        suggestedFileName: String?
    ): File? = withContext(Dispatchers.IO) {
        try {
            val directUrlStr = toDirectDownloadUrl(contentUrl)
            val mediaDir = File(context.filesDir, "chat_media").apply { if (!exists()) mkdirs() }
            val fileName = suggestedFileName?.ifBlank { null } ?: "media_${System.currentTimeMillis()}.dat"
            val targetFile = File(mediaDir, fileName)

            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext targetFile
            }

            val connection = URL(directUrlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Successfully downloaded chat media to ${targetFile.absolutePath}")
                return@withContext targetFile
            } else {
                Log.e(TAG, "Download failed with HTTP code ${connection.responseCode} for $directUrlStr")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading chat media from $contentUrl", e)
        }
        return@withContext null
    }
}
