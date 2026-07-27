package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/**
 * Modern Audio Capture and MediaRecorder / MediaMuxer helper demonstrating:
 * 1. Audio Recording via MediaRecorder (MPEG2_TS / THREE_GPP support, UNPROCESSED / MIC sources)
 * 2. MediaMuxer multi-track recording & custom metadata track writing (application/ mime prefix)
 * 3. AudioPlaybackCapture API (Android 10+) for capturing audio from other media/game apps
 * 4. Privacy-Sensitive capture configuration (setPrivacySensitive) & AudioRecordingCallback state monitoring
 * 5. MediaProjection token lifecycle handling (MediaProjection.Callback)
 */
class AudioCaptureAndRecordingHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    companion object {
        private const val TAG = "AudioCaptureHelper"
    }

    /**
     * Starts audio recording using MediaRecorder with configurable audio sources and MPEG2_TS / 3GP format.
     */
    fun startAudioRecording(
        outputFile: File,
        useUnprocessedSource: Boolean = false,
        useMpeg2TsFormat: Boolean = true
    ): Boolean {
        stopAudioRecording()

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            val audioSource = if (useUnprocessedSource && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.MIC
            }

            recorder.setAudioSource(audioSource)

            if (useMpeg2TsFormat && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }

            recorder.setOutputFile(outputFile.absolutePath)

            // Enable privacy-sensitive audio capture if Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                recorder.setPrivacySensitive(true)
            }

            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            Log.d(TAG, "Audio recording started successfully at ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaRecorder: ${e.message}")
            stopAudioRecording()
            return false
        }
    }

    fun stopAudioRecording() {
        try {
            mediaRecorder?.run {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder: ${e.message}")
        }
    }

    /**
     * Demonstrates MediaMuxer multi-channel audio & custom metadata track initialization.
     */
    fun createMultiTrackMediaMuxer(outputFile: File): MediaMuxer? {
        return try {
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // Add Audio Track
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)
            val audioTrackIndex = muxer.addTrack(audioFormat)

            // Add Custom Metadata Track (prefix: application/)
            val metadataFormat = MediaFormat()
            metadataFormat.setString(MediaFormat.KEY_MIME, "application/x-custom-sensor-metadata")
            val metadataTrackIndex = muxer.addTrack(metadataFormat)

            muxer.start()
            mediaMuxer = muxer
            Log.d(TAG, "MediaMuxer started with audio track #$audioTrackIndex and metadata track #$metadataTrackIndex")
            muxer
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaMuxer: ${e.message}")
            null
        }
    }

    /**
     * Builds an AudioRecord instance for capturing audio played by other apps via AudioPlaybackCapture (Android 10+).
     */
    fun createAudioPlaybackCaptureRecord(
        mediaProjection: MediaProjection,
        sampleRate: Int = 44100
    ): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires Android 10 (API level 29) or higher")
            return null
        }

        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val builder = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setPrivacySensitive(true)
            }

            val record = builder.build()
            audioRecord = record
            Log.d(TAG, "AudioPlaybackCapture AudioRecord created successfully")
            return record
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioPlaybackCapture record: ${e.message}")
            return null
        }
    }

    /**
     * Registers a callback on MediaProjection token to properly handle token loss / session cancellation.
     */
    fun registerMediaProjectionTokenCallback(
        mediaProjection: MediaProjection,
        onSessionStopped: () -> Unit
    ) {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.i(TAG, "MediaProjection token session revoked or stopped")
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                onSessionStopped()
            }
        }
        mediaProjection.registerCallback(callback, null)
        mediaProjectionCallback = callback
    }

    /**
     * Registers AudioRecordingCallback to monitor capture state changes (e.g., when recording is silenced by a higher priority call/assistant).
     */
    fun registerRecordingStateCallback(
        audioRecordInstance: AudioRecord,
        executor: Executor,
        onSilenceStatusChanged: (isSilenced: Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    val currentConfig = audioRecordInstance.activeRecordingConfiguration
                    if (currentConfig != null) {
                        val isSilenced = currentConfig.isClientSilenced
                        Log.d(TAG, "Recording configuration updated. Client silenced: $isSilenced")
                        onSilenceStatusChanged(isSilenced)
                    }
                }
            }
            audioRecordInstance.registerAudioRecordingCallback(executor, callback)
        }
    }

    fun releaseAll() {
        stopAudioRecording()
        try {
            mediaMuxer?.stop()
            mediaMuxer?.release()
            mediaMuxer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaMuxer: ${e.message}")
        }
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        }
    }
}
