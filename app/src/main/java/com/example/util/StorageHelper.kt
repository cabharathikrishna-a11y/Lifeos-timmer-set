package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

object StorageHelper {
    private const val PREFS_NAME = "app_storage_settings"
    private const val KEY_STORAGE = "preferred_storage"

    /**
     * Returns the base directory for writing application data and media files.
     * Searches for secondary external directory (SD Card) if preferred, falling back
     * gracefully to internal storage if not connected/available.
     */
    fun getAppFilesDir(context: Context): File {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preferred = sharedPrefs.getString(KEY_STORAGE, "internal") ?: "internal"
        
        if (preferred == "sd_card") {
            val externalDirs = context.getExternalFilesDirs(null)
            if (externalDirs.size > 1 && externalDirs[1] != null) {
                val sdCardDir = externalDirs[1]!!
                try {
                    if (!sdCardDir.exists()) {
                        sdCardDir.mkdirs()
                    }
                    if (sdCardDir.canWrite()) {
                        return sdCardDir
                    }
                } catch (e: Exception) {
                    // Fall back to internal on failure
                }
            }
        }
        return context.filesDir
    }

    /**
     * Copies a file from external URL / Uri to application datadir sandbox,
     * compressing it memory-safely if it is an image or audio, and returns
     * the local File inside getAppFilesDir.
     */
    fun copyFileToInternalSandbox(context: Context, uri: Uri): File? {
        return try {
            val cr = context.contentResolver
            var displayTitle = "attached_file_${System.currentTimeMillis()}"

            cr.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        displayTitle = cursor.getString(idx)
                    }
                }
            }

            // Clean up directory traversal from displayTitle
            displayTitle = File(displayTitle).name

            val destinationFile = File(getAppFilesDir(context), displayTitle)
            val nameLower = displayTitle.lowercase()
            val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")
            val isAudio = nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac") || nameLower.endsWith(".ogg")

            if (isImage) {
                val success = MediaCompressionHelper.compressImageFromUri(context, uri, destinationFile)
                if (!success) {
                    // Fallback to plain copy
                    cr.openInputStream(uri)?.use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                destinationFile
            } else if (isAudio) {
                // Memory efficient copy + gzip compression directly
                val tempRaw = File(context.cacheDir, "raw_upload_${System.currentTimeMillis()}")
                cr.openInputStream(uri)?.use { input ->
                    tempRaw.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val compressedDestFile = File(getAppFilesDir(context), "$displayTitle.gz")
                val success = MediaCompressionHelper.compressFileGzip(tempRaw, compressedDestFile)
                tempRaw.delete()
                if (success) compressedDestFile else {
                    cr.openInputStream(uri)?.use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destinationFile
                }
            } else {
                cr.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destinationFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Determines whether there is currently a valid physical external SD storage connected.
     */
    fun isExternalStorageConnected(context: Context): Boolean {
        val externalDirs = context.getExternalFilesDirs(null)
        return externalDirs.size > 1 && externalDirs[1] != null
    }

    /**
     * Retrieves the preferred storage flag.
     */
    fun getPreferredStorage(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_STORAGE, "internal") ?: "internal"
    }

    /**
     * Changes the preferred storage flag.
     */
    fun setPreferredStorage(context: Context, preference: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(KEY_STORAGE, preference).apply()
    }
}
