package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log

/**
 * BroadcastReceiver listening for ACTION_AUDIO_BECOMING_NOISY.
 * Automatically pauses media playback when headphones or Bluetooth audio devices are disconnected
 * to avoid unintended loud noise output from the built-in speaker.
 */
class BecomingNoisyReceiver(
    private val onAudioBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BecomingNoisyReceiver"

        fun createIntentFilter(): IntentFilter {
            return IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            Log.d(TAG, "Audio output became noisy (headset/Bluetooth disconnected). Pausing playback.")
            onAudioBecomingNoisy()
        }
    }
}
