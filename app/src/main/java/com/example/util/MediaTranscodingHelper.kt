package com.example.util

import android.content.Context
import android.media.ApplicationMediaCapabilities
import android.media.MediaFormat
import android.media.MediaFeature
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log

object MediaTranscodingHelper {
    private const val TAG = "MediaTranscodingHelper"

    /**
     * Constructs programmatic ApplicationMediaCapabilities specifying HEVC support
     * while delegating HDR10/HDR10+ to automatic AVC/SDR compatible transcoding.
     */
    fun buildMediaCapabilities(): Bundle {
        val bundle = Bundle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val mediaCapabilities = ApplicationMediaCapabilities.Builder()
                    .addSupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    .addUnsupportedHdrType(MediaFeature.HdrType.HDR10)
                    .addUnsupportedHdrType(MediaFeature.HdrType.HDR10_PLUS)
                    .build()
                bundle.putParcelable(MediaStore.EXTRA_MEDIA_CAPABILITIES, mediaCapabilities)
                Log.d(TAG, "Built ApplicationMediaCapabilities: HEVC supported, HDR10/HDR10+ transcoded")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build ApplicationMediaCapabilities: ${e.message}")
            }
        }
        return bundle
    }

    /**
     * Prepares options bundle targeting a specific calling UID for inter-app media sharing transcoding.
     */
    fun buildCapabilitiesForCallingUid(callingUid: Int): Bundle {
        val bundle = Bundle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bundle.putInt(MediaStore.EXTRA_MEDIA_CAPABILITIES_UID, callingUid)
        }
        return bundle
    }

    /**
     * Opens an AssetFileDescriptor requesting automatic platform transcoding if needed when sending video off-device.
     */
    fun openTranscodedAsset(
        context: Context,
        mediaUri: Uri,
        mediaMimeType: String = "video/*"
    ): ParcelFileDescriptor? {
        return try {
            val providerOptions = buildMediaCapabilities()
            val afd = context.contentResolver.openTypedAssetFileDescriptor(
                mediaUri,
                mediaMimeType,
                providerOptions
            )
            afd?.parcelFileDescriptor
        } catch (e: Exception) {
            Log.e(TAG, "Error opening transcoded asset descriptor: ${e.message}")
            null
        }
    }
}
