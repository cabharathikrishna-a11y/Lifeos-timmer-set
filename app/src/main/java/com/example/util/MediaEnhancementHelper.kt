package com.example.util

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.media.effect.enhancement.Enhancement
import com.google.android.gms.media.effect.enhancement.EnhancementCallback
import com.google.android.gms.media.effect.enhancement.EnhancementClient
import com.google.android.gms.media.effect.enhancement.EnhancementMode
import com.google.android.gms.media.effect.enhancement.EnhancementOptions
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import com.google.android.gms.media.effect.enhancement.EnhancementSessionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MediaEnhancementHelper providing asynchronous suspending wrappers and state management
 * for Google Play Services Media Enhancement API (com.google.android.gms:play-services-media-effect-enhancement).
 */
object MediaEnhancementHelper {
    private const val TAG = "MediaEnhancementHelper"

    /**
     * Verifies if host hardware supports NPU/GPU acceleration.
     */
    suspend fun EnhancementClient.isDeviceSupportedAsync(): Boolean {
        val client = this
        return suspendCancellableCoroutine { continuation ->
            client.isDeviceSupported()
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
        }
    }

    /**
     * Verifies the presence of required neural network models in Google Play Services.
     */
    suspend fun EnhancementClient.isModuleInstalledAsync(): Boolean {
        val client = this
        return suspendCancellableCoroutine { continuation ->
            client.isModuleInstalled()
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
        }
    }

    /**
     * Creates an EnhancementSession asynchronously wrapping task & callback.
     */
    suspend fun EnhancementClient.createSessionAsync(
        options: EnhancementOptions,
        executor: Executor = Executors.newSingleThreadExecutor()
    ): EnhancementSession = withContext(Dispatchers.Main) {
        val client = this@createSessionAsync
        suspendCancellableCoroutine { continuation ->
            val callback = object : EnhancementSessionCallback {
                override fun onSessionCreated(session: EnhancementSession) {
                    if (continuation.isActive) {
                        continuation.resume(session)
                    }
                }

                override fun onSessionCreationFailed(status: Status) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            Exception("Session creation failed: ${status.statusMessage} (${status.statusCode})")
                        )
                    }
                }

                override fun onSessionDestroyed() {
                    Log.d(TAG, "EnhancementSession destroyed.")
                }

                override fun onSessionDisconnected(status: Status) {
                    Log.w(TAG, "EnhancementSession disconnected: ${status.statusMessage}")
                }
            }

            client.createSession(options, callback)
                .addOnSuccessListener { session ->
                    if (session != null && continuation.isActive) {
                        continuation.resume(session)
                    }
                }
                .addOnFailureListener(executor) { e ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
        }
    }

    /**
     * Wraps single bitmap process execution in a suspending function.
     */
    suspend fun EnhancementSession.processBitmapAsync(
        bitmap: Bitmap,
        options: EnhancementOptions
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        val callback = object : EnhancementCallback {
            override fun onBitmapProcessed(bitmap: Bitmap) {
                if (continuation.isActive) {
                    continuation.resume(bitmap)
                }
            }

            override fun onError(statusCode: Int) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        Exception("Bitmap processing failed with status code: $statusCode")
                    )
                }
            }

            override fun onSurfaceProcessed(timestamp: Long) {}
        }
        this.process(bitmap, options, callback)
    }

    /**
     * Helper to construct EnhancementOptions for Bitmap mode.
     */
    fun createBitmapOptions(
        width: Int,
        height: Int,
        enableTonemap: Boolean = true,
        enableDeblurDenoisePhoto: Boolean = true,
        enableDeblurDenoiseVideo: Boolean = false,
        enableUpscalePhoto: Boolean = false,
        enableUpscaleVideo: Boolean = false
    ): EnhancementOptions {
        return EnhancementOptions(
            width,
            height,
            EnhancementMode.BITMAP,
            enableTonemap,
            enableDeblurDenoisePhoto,
            enableDeblurDenoiseVideo,
            enableUpscalePhoto,
            enableUpscaleVideo
        )
    }

    /**
     * Helper to construct EnhancementOptions for Surface mode.
     */
    fun createSurfaceOptions(
        width: Int,
        height: Int,
        enableTonemap: Boolean = true,
        enableDeblurDenoisePhoto: Boolean = false,
        enableDeblurDenoiseVideo: Boolean = true,
        enableUpscalePhoto: Boolean = false,
        enableUpscaleVideo: Boolean = false
    ): EnhancementOptions {
        return EnhancementOptions(
            width,
            height,
            EnhancementMode.SURFACE,
            enableTonemap,
            enableDeblurDenoisePhoto,
            enableDeblurDenoiseVideo,
            enableUpscalePhoto,
            enableUpscaleVideo
        )
    }
}

// ------------------------------------------------------------------------
// VIEWMODELS FOR MANAGING ENHANCEMENT ENGINE SETUP AND PROCESSING PIPELINE
// ------------------------------------------------------------------------

data class ImageInfo(val bitmap: Bitmap)

data class EnhancementUiState(
    val isInitialized: Boolean = false,
    val isDownloadingModels: Boolean = false,
    val isDeviceSupported: Boolean = true,
    val isLoading: Boolean = false,
    val enhancementError: String? = null,
    val enhancedImage: ImageInfo? = null
)

class MediaSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val enhancementClient: EnhancementClient = Enhancement.getClient(application)

    private val _uiState = MutableStateFlow(EnhancementUiState())
    val uiState: StateFlow<EnhancementUiState> = _uiState.asStateFlow()

    fun initializeEnhancementEngine(
        onReady: () -> Unit = {},
        onIncompatible: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val isSupported = with(MediaEnhancementHelper) { enhancementClient.isDeviceSupportedAsync() }
                if (!isSupported) {
                    _uiState.update { it.copy(isDeviceSupported = false) }
                    onIncompatible()
                    return@launch
                }

                val isInstalled = with(MediaEnhancementHelper) { enhancementClient.isModuleInstalledAsync() }
                if (!isInstalled) {
                    _uiState.update { it.copy(isDownloadingModels = true) }
                    val installStatusCallback = object : EnhancementClient.InstallStatusCallback {
                        override fun onError(description: String) {
                            Log.e("MediaSetupViewModel", "Module install error: $description")
                        }
                        override fun onCancelled() {}
                        override fun onDownloadProgressUpdate(progress: Int) {}
                        override fun onDownloadPending() {}
                        override fun onDownloadStart() {}
                        override fun onDownloadPaused() {}
                        override fun onDownloadComplete() {}
                        override fun onInstalled() {}
                    }
                    enhancementClient.installModule(installStatusCallback).await()
                }

                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isDownloadingModels = false,
                        isDeviceSupported = true
                    )
                }
                onReady()
            } catch (e: Exception) {
                Log.e("MediaSetupViewModel", "Initialization failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isDownloadingModels = false,
                        enhancementError = e.message
                    )
                }
                onError(e.message ?: "Initialization error")
            }
        }
    }
}

class EnhancementViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EnhancementUiState())
    val uiState: StateFlow<EnhancementUiState> = _uiState.asStateFlow()

    private val enhancementClient: EnhancementClient = Enhancement.getClient(application)
    private val enhancementExecutor: Executor = Executors.newSingleThreadExecutor()
    private var enhancementSession: EnhancementSession? = null

    fun enhanceImage(
        bitmap: Bitmap,
        enableTonemap: Boolean = true,
        enableDeblurDenoisePhoto: Boolean = true,
        enableUpscalePhoto: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, enhancementError = null) }
            try {
                val options = MediaEnhancementHelper.createBitmapOptions(
                    width = bitmap.width,
                    height = bitmap.height,
                    enableTonemap = enableTonemap,
                    enableDeblurDenoisePhoto = enableDeblurDenoisePhoto,
                    enableUpscalePhoto = enableUpscalePhoto
                )

                if (enhancementSession == null) {
                    enhancementSession = with(MediaEnhancementHelper) {
                        enhancementClient.createSessionAsync(options, enhancementExecutor)
                    }
                }

                val session = enhancementSession ?: throw IllegalStateException("Session unavailable.")
                val enhancedBitmap = with(MediaEnhancementHelper) {
                    session.processBitmapAsync(bitmap, options)
                }

                _uiState.update {
                    it.copy(
                        enhancedImage = ImageInfo(bitmap = enhancedBitmap),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("EnhancementViewModel", "Enhancement failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        enhancementError = e.message ?: "Processing error",
                        isLoading = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        enhancementSession?.release()
        enhancementSession = null
        super.onCleared()
    }
}
