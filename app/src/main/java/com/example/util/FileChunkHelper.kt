package com.example.util

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A memory-safe utility for managing large physical media files (journal media blobs, task history artifacts)
 * by chunking them into segment segments before storage/transfer, and reassembling them.
 * This prevents memory overflows (OutOfMemoryError) during imports/exports or database backups.
 */
object FileChunkHelper {
    private const val TAG = "FileChunkHelper"
    private const val DEFAULT_CHUNK_SIZE = 1024 * 1024 // 1 MB default chunk size

    /**
     * Splits a large media file into smaller sequential segments.
     * Saved as: originalFileName.part0, originalFileName.part1, etc.
     */
    fun splitFile(sourceFile: File, destDir: File, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<File> {
        val chunkFiles = mutableListOf<File>()
        if (!sourceFile.exists()) return chunkFiles

        val buffer = ByteArray(8192) // 8 KB copy buffer
        var segmentIndex = 0
        var bytesReadInChunk = 0
        var currentOutputStream: FileOutputStream? = null
        var currentChunkFile: File? = null

        try {
            FileInputStream(sourceFile).use { inputStream ->
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    if (currentOutputStream == null || bytesReadInChunk >= chunkSize) {
                        currentOutputStream?.close()
                        
                        currentChunkFile = File(destDir, "${sourceFile.name}.part$segmentIndex")
                        currentOutputStream = FileOutputStream(currentChunkFile)
                        chunkFiles.add(currentChunkFile)
                        
                        segmentIndex++
                        bytesReadInChunk = 0
                    }

                    currentOutputStream.write(buffer, 0, bytesRead)
                    bytesReadInChunk += bytesRead
                    bytesRead = inputStream.read(buffer)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error splitting file: ${sourceFile.name}", e)
        } finally {
            currentOutputStream?.close()
        }

        return chunkFiles
    }

    /**
     * Merges sequentially segmented chunk parts back into a single output file.
     */
    fun mergeFiles(chunkFiles: List<File>, destFile: File): Boolean {
        val buffer = ByteArray(8192) // 8 KB buffer
        try {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { outputStream ->
                // Sort files numerically by the segment suffix
                val sortedChunks = chunkFiles.sortedBy { file ->
                    val suffix = file.name.substringAfterLast(".part", "0")
                    suffix.toIntOrNull() ?: 0
                }

                for (chunk in sortedChunks) {
                    if (!chunk.exists()) {
                        Log.e(TAG, "Missing trunk chunk: ${chunk.name}")
                        return false
                    }
                    FileInputStream(chunk).use { inputStream ->
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesRead = inputStream.read(buffer)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error merging chunk files to ${destFile.name}", e)
            return false
        }
    }

    /**
     * Memory-safe chunked copy of an entire input stream to an output stream
     * using a small 8KB memory foot-print.
     */
    fun copyStreamSecure(inputStream: InputStream, outputStream: OutputStream, bufferSize: Int = 8192): Long {
        var totalBytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesCopied += bytesRead
            bytesRead = inputStream.read(buffer)
        }
        outputStream.flush()
        return totalBytesCopied
    }
}
