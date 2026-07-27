package com.example.util

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Rational
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Screen Size Classes for Large Screen & Adaptive Layouts
 */
enum class ScreenSizeClass {
    COMPACT,   // Phones (< 600dp)
    MEDIUM,    // Foldables, Small Tablets (600dp - 840dp)
    EXPANDED   // Tablets, ChromeOS (> 840dp)
}

data class HdrSupportInfo(
    val isHdrSupported: Boolean,
    val supportsHlg: Boolean,
    val supportsHdr10: Boolean,
    val supportsDolbyVision: Boolean,
    val supportedTypes: IntArray
)

/**
 * Helper providing unified capabilities for:
 * 1. Adaptive & Large Screen layouts (List-Detail, Feed, Supporting Pane)
 * 2. HDR & Ultra HDR (GainMap checking)
 * 3. Picture-in-Picture (PiP) parameters
 * 4. Screen Projection auto-stop callback registration
 */
object AdaptiveMediaHelper {

    /**
     * Computes the screen size class based on width in dp.
     */
    fun getScreenSizeClass(widthDp: Int): ScreenSizeClass {
        return when {
            widthDp < 600 -> ScreenSizeClass.COMPACT
            widthDp < 840 -> ScreenSizeClass.MEDIUM
            else -> ScreenSizeClass.EXPANDED
        }
    }

    /**
     * Checks if the device display supports HDR content (e.g. HLG, HDR10).
     */
    fun checkHdrSupport(context: Context): HdrSupportInfo {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && display != null) {
            val hdrCapabilities = display.hdrCapabilities
            val types = hdrCapabilities?.supportedHdrTypes ?: intArrayOf()

            val supportsHlg = types.contains(Display.HdrCapabilities.HDR_TYPE_HLG)
            val supportsHdr10 = types.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)
            val supportsDolbyVision = types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)

            return HdrSupportInfo(
                isHdrSupported = types.isNotEmpty(),
                supportsHlg = supportsHlg,
                supportsHdr10 = supportsHdr10,
                supportsDolbyVision = supportsDolbyVision,
                supportedTypes = types
            )
        }

        return HdrSupportInfo(
            isHdrSupported = false,
            supportsHlg = false,
            supportsHdr10 = false,
            supportsDolbyVision = false,
            supportedTypes = intArrayOf()
        )
    }

    /**
     * Checks if a Bitmap contains an Ultra HDR Gain Map (Android 14+).
     */
    fun hasUltraHdrGainmap(bitmap: Bitmap): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            bitmap.hasGainmap()
        } else {
            false
        }
    }

    /**
     * Configures Picture-in-Picture parameters with proper sourceRectHint and autoEnter flag.
     */
    fun buildPipParams(
        sourceRect: Rect? = null,
        aspectRatioNumerator: Int = 16,
        aspectRatioDenominator: Int = 9,
        autoEnter: Boolean = true
    ): PictureInPictureParams {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(aspectRatioNumerator, aspectRatioDenominator))

            if (sourceRect != null) {
                builder.setSourceRectHint(sourceRect)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(autoEnter)
                builder.setSeamlessResizeEnabled(true)
            }

            return builder.build()
        } else {
            throw UnsupportedOperationException("Picture-in-Picture requires Android O or higher.")
        }
    }

    /**
     * Registers a MediaProjection callback for handling status bar chip auto-stop events.
     */
    fun registerMediaProjectionAutoStop(
        mediaProjection: MediaProjection,
        onStopped: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        onStopped()
                    }

                    override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                        super.onCapturedContentVisibilityChanged(isVisible)
                    }

                    override fun onCapturedContentResize(width: Int, height: Int) {
                        super.onCapturedContentResize(width, height)
                    }
                },
                null
            )
        }
    }
}
