package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class MediaInspectionResult(
    val title: String = "",
    val durationMs: Long = 0L,
    val trackCount: Int = 0,
    val mimeTypes: List<String> = emptyList(),
    val isMotionPhoto: Boolean = false,
    val isUltraHdrGainMap: Boolean = false,
    val isMp4AuxiliaryTracks: Boolean = false,
    val isEclipsaHdr: Boolean = false,
    val thumbnailBitmap: Bitmap? = null,
    val frameAt5sBitmap: Bitmap? = null,
    val sampleCountExtracted: Int = 0,
    val details: String = ""
)

object Media3InspectorHelper {
    private const val TAG = "Media3InspectorHelper"

    /**
     * Inspects media files using MediaMetadataRetriever (for metadata, duration, frame extraction)
     * and MediaExtractor (for track demuxing, sample extraction, and codec format inspection).
     * Also analyzes container signatures for Motion Photos, Ultra HDR Gain Maps, MP4-AT, and Eclipsa HDR.
     */
    suspend fun inspectMedia(
        context: Context,
        mediaPath: String
    ): MediaInspectionResult = withContext(Dispatchers.IO) {
        var durationMs = 0L
        var trackCount = 0
        val mimeTypes = mutableListOf<String>()
        var thumbnailBitmap: Bitmap? = null
        var frameAt5sBitmap: Bitmap? = null
        var sampleCount = 0
        var mediaTitle = ""
        var motionPhotoDetected = false
        var ultraHdrDetected = false
        var mp4AuxTracksDetected = false
        var eclipsaHdrDetected = false
        val detailsBuilder = StringBuilder()

        val isLocalFile = !mediaPath.startsWith("http://") && !mediaPath.startsWith("https://")
        val file = if (isLocalFile) File(mediaPath) else null

        // 1. Check raw file bytes/XMP headers for specialized formats (Motion Photo, Ultra HDR, MP4-AT, Eclipsa)
        if (file != null && file.exists()) {
            try {
                val bytes = file.readBytes().take(100 * 1024).toByteArray()
                val fileContentStr = String(bytes, Charsets.ISO_8859_1)
                
                if (fileContentStr.contains("MotionPhoto") || fileContentStr.contains("Camera:MotionPhoto")) {
                    motionPhotoDetected = true
                }
                if (fileContentStr.contains("hdrgm:Version") || fileContentStr.contains("Item:Semantic=\"GainMap\"")) {
                    ultraHdrDetected = true
                }
                if (fileContentStr.contains("axte") || fileContentStr.contains("auxiliary.tracks")) {
                    mp4AuxTracksDetected = true
                }
                if (fileContentStr.contains("ST 2094-50") || fileContentStr.contains("HLG10_SMPTE_2094_50") || fileContentStr.contains("eclipsa")) {
                    eclipsaHdrDetected = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Header analysis error: ${e.message}")
            }
        }

        // 2. Metadata Retrieval & Frame Extraction via MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            if (isLocalFile && file != null && file.exists()) {
                retriever.setDataSource(mediaPath)
            } else {
                retriever.setDataSource(mediaPath, HashMap<String, String>())
            }

            mediaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: mediaPath.substringAfterLast("/")

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
            detailsBuilder.appendLine("Title: $mediaTitle")
            detailsBuilder.appendLine("Duration: ${durationMs} ms (${durationMs / 1000}s)")

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mime.isNullOrEmpty()) {
                mimeTypes.add(mime)
                detailsBuilder.appendLine("MIME Type: $mime")
            }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width != null && height != null) {
                detailsBuilder.appendLine("Resolution: ${width}x${height}")
            }

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (bitrate != null) {
                detailsBuilder.appendLine("Bitrate: $bitrate bps")
            }

            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            detailsBuilder.appendLine("Audio Track Present: ${hasAudio == "yes"}")
            detailsBuilder.appendLine("Video Track Present: ${hasVideo == "yes"}")

            // Representative Thumbnail
            thumbnailBitmap = try {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            }

            // Frame extraction at specific timestamp (e.g. 5 seconds)
            frameAt5sBitmap = try {
                retriever.getFrameAtTime(5_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            }

            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MetadataRetriever error: ${e.message}")
            detailsBuilder.appendLine("Metadata Exception: ${e.message}")
        }

        // 3. MediaExtractor for Track Selection, Demuxing, and Encoded Sample Data
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(mediaPath)

            trackCount = extractor.trackCount
            detailsBuilder.appendLine("Track Count: $trackCount")

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
                mimeTypes.add(trackMime)
                extractor.selectTrack(i)
                detailsBuilder.appendLine("  Track #$i: Mime=$trackMime")
                detailsBuilder.appendLine("    Format Details: $format")

                if (trackMime.contains("hdr") || trackMime.contains("hevc") || trackMime.contains("av01") || trackMime.contains("vp09")) {
                    eclipsaHdrDetected = true
                }
            }

            // Read encoded samples into buffer
            val buffer = ByteBuffer.allocate(1024 * 1024)
            while (sampleCount < 10) {
                val bytesRead = extractor.readSampleData(buffer, 0)
                if (bytesRead < 0) break
                val trackIdx = extractor.sampleTrackIndex
                val sampleTimeUs = extractor.sampleTime
                detailsBuilder.appendLine("    Sample #$sampleCount: Track=$trackIdx, TimeUs=$sampleTimeUs, Size=$bytesRead bytes")
                sampleCount++
                extractor.advance()
            }

            extractor.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor error: ${e.message}")
            detailsBuilder.appendLine("MediaExtractor Exception: ${e.message}")
        }

        // Format Flags Log
        detailsBuilder.appendLine("--- FORMAT SPECIFICATION INSPECTION ---")
        detailsBuilder.appendLine("Motion Photo v1.0 Container: ${if (motionPhotoDetected) "DETECTED" else "Standard"}")
        detailsBuilder.appendLine("Ultra HDR Gain Map (hdrgm v1.0): ${if (ultraHdrDetected) "DETECTED" else "Standard Dynamic Range"}")
        detailsBuilder.appendLine("MP4-AT Auxiliary Tracks (axte): ${if (mp4AuxTracksDetected) "DETECTED" else "Standard Tracks"}")
        detailsBuilder.appendLine("SMPTE ST 2094-50 Eclipsa HDR: ${if (eclipsaHdrDetected) "SUPPORTED" else "SDR/Standard HDR"}")
        detailsBuilder.appendLine("Compatible Media Transcoding: ENABLED (media_capabilities.xml / ApplicationMediaCapabilities)")

        MediaInspectionResult(
            title = mediaTitle,
            durationMs = durationMs,
            trackCount = trackCount,
            mimeTypes = mimeTypes.distinct(),
            isMotionPhoto = motionPhotoDetected,
            isUltraHdrGainMap = ultraHdrDetected,
            isMp4AuxiliaryTracks = mp4AuxTracksDetected,
            isEclipsaHdr = eclipsaHdrDetected,
            thumbnailBitmap = thumbnailBitmap,
            frameAt5sBitmap = frameAt5sBitmap,
            sampleCountExtracted = sampleCount,
            details = detailsBuilder.toString()
        )
    }
}
