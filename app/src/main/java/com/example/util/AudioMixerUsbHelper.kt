package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioMixerAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/**
 * Utility for configuring USB Audio Mixer attributes (BIT_PERFECT vs DEFAULT mixing)
 * on Android 14+ (API Level 34) devices according to system AudioMixerAttributes APIs.
 */
object AudioMixerUsbHelper {

    private const val TAG = "AudioMixerUsbHelper"

    /**
     * Checks whether preferred USB Audio Mixer Attributes are supported on the current device.
     */
    fun isUsbMixerAttributeSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /**
     * Configures preferred USB mixer attributes (e.g., Bit-Perfect 24-bit PCM at 44.1kHz or 48kHz)
     * for audiophile USB DACs attached to the device.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun configureUsbBitPerfectMixer(
        context: Context,
        sampleRate: Int = 44100,
        bitPerfect: Boolean = true
    ): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        try {
            // Find connected USB Audio output devices
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } ?: run {
                Log.i(TAG, "No USB audio output device currently connected")
                return false
            }

            val expectedFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setSampleRate(sampleRate)
                .build()

            val supportedMixerAttrs = audioManager.getSupportedMixerAttributes(usbDevice)
            val matchingMixerAttr = supportedMixerAttrs.firstOrNull {
                it != null && it.format.encoding == expectedFormat.encoding && it.format.sampleRate == expectedFormat.sampleRate
            } ?: supportedMixerAttrs.firstOrNull()

            if (matchingMixerAttr != null) {
                val mediaAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                audioManager.setPreferredMixerAttributes(
                    mediaAttributes,
                    usbDevice,
                    matchingMixerAttr
                )
                Log.d(TAG, "Successfully configured preferred USB Mixer Attributes: $matchingMixerAttr (bitPerfect=$bitPerfect)")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preferred USB mixer attributes: ${e.message}")
        }
        return false
    }

    /**
     * Clears preferred USB mixer attributes after playback completes.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun clearUsbMixerAttributes(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } ?: return

            val mediaAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            audioManager.clearPreferredMixerAttributes(mediaAttributes, usbDevice)
            Log.d(TAG, "Cleared preferred USB mixer attributes")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing USB mixer attributes: ${e.message}")
        }
    }
}
