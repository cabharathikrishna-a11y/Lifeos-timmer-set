package com.example.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

class MediaPlaybackService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        const val CUSTOM_ACTION_FAVORITE = "com.example.media3.ACTION_FAVORITE"
        const val CUSTOM_ACTION_BOOST_BASS = "com.example.media3.ACTION_BOOST_BASS"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize ExoPlayer with AudioAttributes for background playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val localExoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Populate sample media item playlist
        val sampleItems = listOf(
            MediaItem.Builder()
                .setMediaId("sample_audio_1")
                .setUri("https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Synthwave Echoes")
                        .setArtist("Life OS Audio Lab")
                        .setAlbumTitle("Ambient Focus Essentials")
                        .setDisplayTitle("Synthwave Echoes")
                        .setSubtitle("Life OS Audio Lab")
                        .build()
                )
                .build(),
            MediaItem.Builder()
                .setMediaId("sample_audio_2")
                .setUri("https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Jazz In Paris")
                        .setArtist("Media3 Studio Orchestra")
                        .setAlbumTitle("Deep Focus Sessions")
                        .setDisplayTitle("Jazz In Paris")
                        .setSubtitle("Media3 Studio Orchestra")
                        .build()
                )
                .build()
        )

        localExoPlayer.setMediaItems(sampleItems)
        localExoPlayer.prepare()
        exoPlayer = localExoPlayer

        // 2. Build CastPlayer wrapping CastContext for seamless local/remote casting
        var playerToUse: Player = localExoPlayer
        try {
            val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
            val castPlayerInstance = CastPlayer(castContext)
            castPlayer = castPlayerInstance
            playerToUse = castPlayerInstance
        } catch (e: Exception) {
            // Fallback gracefully to local ExoPlayer if Google Play Services / CastContext is not initialized
            playerToUse = localExoPlayer
        }

        // 3. Define Custom Command Buttons for Media Notification
        val favoriteButton = CommandButton.Builder()
            .setDisplayName("Save to Favorites")
            .setIconResId(android.R.drawable.btn_star_big_on)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
            .build()

        val bassBoostButton = CommandButton.Builder()
            .setDisplayName("Bass Boost FX")
            .setIconResId(android.R.drawable.ic_media_play)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_BOOST_BASS, Bundle.EMPTY))
            .build()

        // 4. Build MediaSession with Custom Callback using CastPlayer (or ExoPlayer fallback)
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_BOOST_BASS, Bundle.EMPTY))
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .setCustomLayout(listOf(favoriteButton, bassBoostButton))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_ACTION_FAVORITE -> {
                        val currentItem = session.player.currentMediaItem
                        val title = currentItem?.mediaMetadata?.title ?: "Unknown Track"
                        val extras = Bundle().apply {
                            putString("MESSAGE", "Toggled favorite for $title")
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                    }
                    CUSTOM_ACTION_BOOST_BASS -> {
                        val extras = Bundle().apply {
                            putString("MESSAGE", "Audio Equalizer Bass Boost applied")
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                val resumptionItems = listOf(
                    MediaItem.Builder()
                        .setMediaId("resumed_track_1")
                        .setUri("https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Resumed Track: Synthwave Echoes")
                                .setArtist("Life OS Audio Lab")
                                .build()
                        )
                        .build()
                )
                val mediaItemsWithStartPosition = MediaSession.MediaItemsWithStartPosition(
                    resumptionItems,
                    0,
                    0L
                )
                future.set(mediaItemsWithStartPosition)
                return future
            }
        }

        mediaSession = MediaSession.Builder(this, playerToUse)
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(favoriteButton, bassBoostButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        castPlayer?.release()
        castPlayer = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
