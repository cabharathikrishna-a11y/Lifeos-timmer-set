package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * CameraXHelper encapsulating modern CameraX features:
 * - Intent-based Photo and Video Capture
 * - CameraX Preview, ImageCapture, VideoCapture with Recorder, and ImageAnalysis
 * - CameraX Extensions (Night, Bokeh, HDR, Face Retouch)
 * - Camera2Interop Low Light Boost AE Mode
 * - Torch, Zoom, Tap-to-focus, Exposure Compensation, and Rotation listener
 */
object CameraXHelper {
    private const val TAG = "CameraXHelper"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    // ----------------------------------------------------
    // 1. INTENT-BASED CAMERA ACTIONS
    // ----------------------------------------------------

    /**
     * Launch default camera app via Intent to take a photo.
     */
    fun dispatchTakePictureIntent(context: Context, outputUri: Uri? = null): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (outputUri != null) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent
    }

    /**
     * Launch default camera app via Intent to record video.
     */
    fun dispatchTakeVideoIntent(context: Context, outputUri: Uri? = null, qualityHigh: Boolean = true): Intent {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        if (outputUri != null) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, if (qualityHigh) 1 else 0)
        return intent
    }

    // ----------------------------------------------------
    // 2. CAMERAX CONFIGURATION STATE & CONTROLLER
    // ----------------------------------------------------

    class CameraStateController(
        val context: Context,
        val lifecycleOwner: LifecycleOwner
    ) {
        private var cameraProvider: ProcessCameraProvider? = null
        private var extensionsManager: ExtensionsManager? = null
        private var camera: Camera? = null

        var preview: Preview? = null
        var imageCapture: ImageCapture? = null
        var videoCapture: VideoCapture<Recorder>? = null
        var imageAnalysis: ImageAnalysis? = null
        var activeRecording: Recording? = null

        val executor: Executor = Executors.newSingleThreadExecutor()

        var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
        var flashMode by mutableStateOf(ImageCapture.FLASH_MODE_OFF)
        var extensionMode by mutableStateOf(ExtensionMode.NONE)
        var isTorchEnabled by mutableStateOf(false)
        var currentZoomRatio by mutableFloatStateOf(1f)
        var isLowLightBoostSupported by mutableStateOf(false)
        var isLowLightBoostActive by mutableStateOf(false)

        var orientationEventListener: OrientationEventListener? = null

        fun initCamera(
            previewView: PreviewView,
            onInitialized: () -> Unit = {}
        ) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                cameraProvider = cameraProviderFuture.get()
                val extFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider!!)
                extFuture.addListener({
                    extensionsManager = extFuture.get()
                    bindUseCases(previewView)
                    setupOrientationListener()
                    onInitialized()
                }, ContextCompat.getMainExecutor(context))
            }, ContextCompat.getMainExecutor(context))
        }

        fun bindUseCases(
            previewView: PreviewView,
            analyzer: ImageAnalysis.Analyzer? = null,
            enableLowLightBoost: Boolean = false
        ) {
            val provider = cameraProvider ?: return
            provider.unbindAll()

            var cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Check & apply Extensions if enabled
            extensionsManager?.let { extManager ->
                if (extensionMode != ExtensionMode.NONE && extManager.isExtensionAvailable(cameraSelector, extensionMode)) {
                    cameraSelector = extManager.getExtensionEnabledCameraSelector(cameraSelector, extensionMode)
                }
            }

            // 1. Preview
            val previewBuilder = Preview.Builder()
            if (enableLowLightBoost) {
                applyLowLightBoostCamera2Interop(previewBuilder)
            }
            preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. Image Capture
            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
            imageCapture = captureBuilder.build()

            // 3. Video Capture
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // 4. Image Analysis (Optional frame analyzer)
            if (analyzer != null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(executor, analyzer)
                    }
            } else {
                imageAnalysis = null
            }

            try {
                camera = if (imageAnalysis != null) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture,
                        imageAnalysis
                    )
                } else {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture
                    )
                }

                checkLowLightBoostSupport()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases: ${e.message}", e)
            }
        }

        private fun applyLowLightBoostCamera2Interop(builder: Preview.Builder) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM || Build.VERSION.SDK_INT >= 35) {
                Camera2Interop.Extender(builder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    35 // CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
                )
            }
        }

        private fun checkLowLightBoostSupport() {
            camera?.let { cam ->
                val camInfo = cam.cameraInfo
                try {
                    val camera2Info = Camera2CameraInfo.from(camInfo)
                    val availableAeModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                    if (availableAeModes != null) {
                        // AE mode 5 or 35 corresponds to Low Light Boost depending on vendor implementation
                        isLowLightBoostSupported = availableAeModes.contains(35) || availableAeModes.contains(5)
                    }
                } catch (e: Exception) {
                    isLowLightBoostSupported = false
                }
            }
        }

        private fun setupOrientationListener() {
            orientationEventListener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    val rotation = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    imageCapture?.targetRotation = rotation
                    videoCapture?.targetRotation = rotation
                    imageAnalysis?.targetRotation = rotation
                }
            }
            orientationEventListener?.enable()
        }

        fun takePhoto(
            outputFile: File,
            onPhotoSaved: (Uri) -> Unit,
            onError: (Exception) -> Unit
        ) {
            val capture = imageCapture ?: run {
                onError(IllegalStateException("ImageCapture not bound"))
                return
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri ?: Uri.fromFile(outputFile)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outputFile.absolutePath),
                            null,
                            null
                        )
                        onPhotoSaved(savedUri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onError(exception)
                    }
                }
            )
        }

        fun startRecordingVideo(
            outputFile: File,
            recordAudio: Boolean = true,
            onVideoSaved: (Uri) -> Unit,
            onError: (Exception) -> Unit
        ) {
            val vCap = videoCapture ?: run {
                onError(IllegalStateException("VideoCapture not bound"))
                return
            }

            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            var pendingRecording = vCap.output.prepareRecording(context, outputOptions)

            if (recordAudio && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            val savedUri = event.outputResults.outputUri
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(outputFile.absolutePath),
                                null,
                                null
                            )
                            onVideoSaved(savedUri)
                        } else {
                            activeRecording?.close()
                            activeRecording = null
                            onError(Exception("Video record error: ${event.error}"))
                        }
                    }
                }
            }
        }

        fun stopRecordingVideo() {
            activeRecording?.stop()
            activeRecording = null
        }

        fun toggleCamera(previewView: PreviewView) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindUseCases(previewView)
        }

        fun setFlash(mode: Int) {
            flashMode = mode
            imageCapture?.flashMode = mode
        }

        fun toggleTorch(enable: Boolean) {
            camera?.cameraControl?.enableTorch(enable)
            isTorchEnabled = enable
        }

        fun setZoom(zoomRatio: Float) {
            currentZoomRatio = zoomRatio
            camera?.cameraControl?.setZoomRatio(zoomRatio)
        }

        fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()
            camera?.cameraControl?.startFocusAndMetering(action)
        }

        fun cleanup() {
            orientationEventListener?.disable()
            activeRecording?.close()
            activeRecording = null
        }
    }
}

/**
 * Modern Compose CameraX View Component with live preview, controls, and capture actions.
 */
@Composable
fun CameraXView(
    modifier: Modifier = Modifier,
    onMediaCaptured: (File, Boolean) -> Unit = { _, _ -> },
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraXHelper.CameraStateController(context, lifecycleOwner) }

    var isRecording by remember { mutableStateOf(false) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            controller.cleanup()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller.initCamera(this) {
                        previewViewInstance = this
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        previewViewInstance?.let { pv ->
                            controller.tapToFocus(pv, offset.x, offset.y)
                        }
                    }
                }
        )

        // Top Control Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Toggle Button
            IconButton(
                onClick = {
                    val nextFlash = when (controller.flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    controller.setFlash(nextFlash)
                }
            ) {
                Icon(
                    imageVector = when (controller.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash Toggle",
                    tint = Color.White
                )
            }

            // Torch Toggle Button
            IconButton(
                onClick = {
                    controller.toggleTorch(!controller.isTorchEnabled)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Torch Toggle",
                    tint = if (controller.isTorchEnabled) Color.Yellow else Color.White
                )
            }

            // Lens Switch Button
            IconButton(
                onClick = {
                    previewViewInstance?.let { pv ->
                        controller.toggleCamera(pv)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }
        }

        // Bottom Action Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Take Photo Button
            Button(
                onClick = {
                    val file = File(
                        StorageHelper.getAppFilesDir(context),
                        "IMG_${System.currentTimeMillis()}.jpg"
                    )
                    controller.takePhoto(
                        outputFile = file,
                        onPhotoSaved = { uri ->
                            onMediaCaptured(file, false)
                        },
                        onError = { exc ->
                            onError(exc.message ?: "Photo capture failed")
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take Photo",
                    tint = Color.Black
                )
            }

            // Record Video Button
            Button(
                onClick = {
                    if (isRecording) {
                        controller.stopRecordingVideo()
                        isRecording = false
                    } else {
                        val file = File(
                            StorageHelper.getAppFilesDir(context),
                            "VID_${System.currentTimeMillis()}.mp4"
                        )
                        controller.startRecordingVideo(
                            outputFile = file,
                            onVideoSaved = { uri ->
                                isRecording = false
                                onMediaCaptured(file, true)
                            },
                            onError = { exc ->
                                isRecording = false
                                onError(exc.message ?: "Video record failed")
                            }
                        )
                        isRecording = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else Color(0xFF2E6FF3)
                ),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                    contentDescription = if (isRecording) "Stop Video" else "Record Video",
                    tint = Color.White
                )
            }
        }
    }
}
