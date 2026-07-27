package com.example.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.VolumeShaper
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log

data class AudioTrackItem(
    val id: Long,
    val title: String,
    val uri: Uri
)

/**
 * Modern MediaPlayer helper demonstrating:
 * 1. AudioAttributes setup (USAGE_MEDIA / CONTENT_TYPE_MUSIC)
 * 2. State-aware async preparation (prepareAsync, OnPreparedListener, OnErrorListener)
 * 3. Wake lock management (setWakeMode PARTIAL_WAKE_LOCK)
 * 4. VolumeShaper automated fade-in / fade-out ramping (Android 8.0+)
 * 5. MediaStore audio querying via ContentResolver
 */
class MediaPlayerAudioHelper(private val context: Context) : MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var volumeShaper: VolumeShaper? = null
    private var isPrepared = false

    companion object {
        private const val TAG = "MediaPlayerAudioHelper"
    }

    /**
     * Queries local device music tracks from MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.
     */
    fun queryLocalAudioTracks(): List<AudioTrackItem> {
        val tracks = mutableListOf<AudioTrackItem>()
        val resolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    tracks.add(AudioTrackItem(id, title, contentUri))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore audio tracks: ${e.message}")
        }
        return tracks
    }

    /**
     * Plays media from a Content URI or HTTP URL asynchronously with proper AudioAttributes and WakeLock.
     */
    fun playMediaAsync(uri: Uri, onReady: (() -> Unit)? = null) {
        releasePlayer()

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setOnPreparedListener(this@MediaPlayerAudioHelper)
                setOnErrorListener(this@MediaPlayerAudioHelper)
            }
            mediaPlayer = mp
            isPrepared = false
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for URI $uri: ${e.message}")
        }
    }

    override fun onPrepared(mp: MediaPlayer) {
        isPrepared = true
        Log.d(TAG, "MediaPlayer prepared asynchronously")

        // Create VolumeShaper for smooth 2-second linear fade-in
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val config = VolumeShaper.Configuration.Builder()
                    .setDuration(2000)
                    .setCurve(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f))
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .build()

                volumeShaper = mp.createVolumeShaper(config)
                volumeShaper?.apply(VolumeShaper.Operation.PLAY)
            } catch (e: Exception) {
                Log.w(TAG, "VolumeShaper initialization error: ${e.message}")
            }
        }

        mp.start()
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error occurred: what=$what, extra=$extra")
        isPrepared = false
        mp.reset()
        return true
    }

    /**
     * Applies a 1-second fade-out before pausing using VolumeShaper.
     */
    fun fadeOutAndPause(onComplete: (() -> Unit)? = null) {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val fadeOutConfig = VolumeShaper.Configuration.Builder()
                    .setDuration(1000)
                    .setCurve(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f))
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .build()

                volumeShaper?.replace(fadeOutConfig, VolumeShaper.Operation.PLAY, true)
            } catch (e: Exception) {
                Log.w(TAG, "VolumeShaper fadeOut error: ${e.message}")
            }
        }
        mp.pause()
        onComplete?.invoke()
    }

    fun releasePlayer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                volumeShaper?.close()
                volumeShaper = null
            }
            mediaPlayer?.run {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPrepared = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }
    }
}
