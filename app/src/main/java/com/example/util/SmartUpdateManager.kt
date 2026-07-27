package com.example.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.api.Firebase
import com.example.api.FirebaseConfig
import com.example.api.OutboxDrainer
import com.example.data.AppDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class SmartUpdateStatus {
    object Idle : SmartUpdateStatus()
    object Checking : SmartUpdateStatus()
    object SecuringData : SmartUpdateStatus()
    data class NewVersionAvailable(
        val versionNo: Int,
        val patchFileUrl: String?,
        val fullApkUrl: String,
        val patchMd5: String?,
        val isForceUpdate: Boolean
    ) : SmartUpdateStatus()
    object NoUpdateAvailable : SmartUpdateStatus()
    data class Downloading(val progress: Float, val isPatch: Boolean) : SmartUpdateStatus()
    object Merging : SmartUpdateStatus()
    data class ReadyToInstall(val apkFile: File, val isForceUpdate: Boolean) : SmartUpdateStatus()
    data class Error(val message: String) : SmartUpdateStatus()
}

object SmartUpdateManager {
    private const val TAG = "SmartUpdateManager"

    private val _updateStatus = MutableStateFlow<SmartUpdateStatus>(SmartUpdateStatus.Idle)
    val updateStatus: StateFlow<SmartUpdateStatus> = _updateStatus.asStateFlow()

    private val _isForceUpdateRequired = MutableStateFlow(false)
    val isForceUpdateRequired: StateFlow<Boolean> = _isForceUpdateRequired.asStateFlow()

    var activeForceUpdateConfig: SmartUpdateStatus.NewVersionAvailable? = null
    var latestAvailableVersion: SmartUpdateStatus.NewVersionAvailable? = null

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        // Lifecycle Storage Cleanup (App Launch Purge)
        updateScope.launch(Dispatchers.IO) {
            try {
                val otaDir = File(context.cacheDir, "ota_updates")
                if (otaDir.exists()) {
                    otaDir.listFiles()?.forEach { file ->
                        if (file.delete()) {
                            Log.i(TAG, "Lifecycle Cleanup: Deleted stale update file: ${file.name}")
                        }
                    }
                } else {
                    otaDir.mkdirs()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during app launch storage cleanup", e)
            }
        }
    }

    /**
     * Compare local app version against the cloud configuration inside RTDB or via REST API at `/UPDATE_CONFIG`.
     */
    fun checkForUpdates(context: Context, manualCheck: Boolean = false) {
        if (_updateStatus.value is SmartUpdateStatus.Downloading || _updateStatus.value is SmartUpdateStatus.Merging) {
            Log.i(TAG, "Update operation in progress, ignoring check.")
            return
        }

        _updateStatus.value = SmartUpdateStatus.Checking
        updateScope.launch {
            try {
                val localVersion = AppUpdateManager.getCurrentVersionCode(context)
                Firebase.ensureAuthenticated(context)
                val dbUrl = FirebaseConfig.getDatabaseUrl(context)

                var cloudVersion = -1
                var fullApkUrl = ""
                var patchFileUrl: String? = null
                var patchMd5: String? = null
                var isForceUpdate = false

                // 1. Try Firebase RTDB SDK query with a 3.5s timeout
                if (dbUrl.isNotEmpty()) {
                    try {
                        val database = FirebaseDatabase.getInstance(dbUrl)
                        val ref = database.getReference("UPDATE_CONFIG")

                        val snapshot = withTimeoutOrNull(3500L) {
                            suspendCoroutine<DataSnapshot?> { continuation ->
                                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(s: DataSnapshot) {
                                        continuation.resume(s)
                                    }
                                    override fun onCancelled(error: DatabaseError) {
                                        continuation.resume(null)
                                    }
                                })
                            }
                        }

                        if (snapshot != null && snapshot.exists()) {
                            cloudVersion = snapshot.child("Version_no").getValue(Int::class.java) ?: -1
                            fullApkUrl = snapshot.child("Full_Apk_Url").getValue(String::class.java) ?: ""
                            patchFileUrl = snapshot.child("Patch_File_Url").getValue(String::class.java)
                            patchMd5 = snapshot.child("Patch_MD5").getValue(String::class.java)
                            isForceUpdate = snapshot.child("Is_Force_Update").getValue(Boolean::class.java) ?: false
                        } else if (snapshot != null && !snapshot.exists()) {
                            // Seed default config for reference asynchronously
                            ref.child("Version_no").setValue(localVersion)
                            ref.child("Full_Apk_Url").setValue("https://example.com/app-full.apk")
                            ref.child("Patch_File_Url").setValue("https://example.com/app-patch.bin")
                            ref.child("Patch_MD5").setValue("")
                            ref.child("Is_Force_Update").setValue(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "RTDB SDK update query error, fallback to REST", e)
                    }
                }

                // 2. Fallback to direct REST API if RTDB SDK didn't supply valid cloud version
                if (cloudVersion < 0) {
                    val activeUrl = Firebase.activeUrl.ifEmpty { if (dbUrl.isNotEmpty()) "$dbUrl/" else "" }
                    if (activeUrl.isNotEmpty()) {
                        val restUrl = if (activeUrl.endsWith("/")) "${activeUrl}UPDATE_CONFIG.json" else "$activeUrl/UPDATE_CONFIG.json"
                        try {
                            val request = Request.Builder()
                                .url(restUrl)
                                .header("Cache-Control", "no-cache")
                                .header("Pragma", "no-cache")
                                .get()
                                .build()
                            okHttpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (!body.isNullOrBlank() && body != "null") {
                                        val json = JSONObject(body)
                                        cloudVersion = json.optInt("Version_no", json.optInt("versionId", -1))
                                        fullApkUrl = json.optString("Full_Apk_Url", json.optString("apkFileId", ""))
                                        patchFileUrl = if (json.has("Patch_File_Url") && !json.isNull("Patch_File_Url")) json.optString("Patch_File_Url") else null
                                        patchMd5 = if (json.has("Patch_MD5") && !json.isNull("Patch_MD5")) json.optString("Patch_MD5") else null
                                        isForceUpdate = json.optBoolean("Is_Force_Update", false)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "REST update check error", e)
                        }
                    }
                }

                // 3. Check AppUpdateManager state as additional source if needed
                if (cloudVersion < 0) {
                    val appState = AppUpdateManager.updateStatus.value
                    if (appState is UpdateStatus.NewVersionAvailable) {
                        cloudVersion = appState.versionId
                        fullApkUrl = appState.apkFileId ?: ""
                    }
                }

                if (cloudVersion < 0) {
                    cloudVersion = localVersion
                }

                val versionDiff = cloudVersion - localVersion
                Log.d(TAG, "Cloud version: $cloudVersion, Local version: $localVersion, diff: $versionDiff")

                if (cloudVersion > localVersion) {
                    val forceThisUpdate = isForceUpdate
                    val allowedPatchUrl = if (versionDiff == 1 && !patchFileUrl.isNullOrEmpty() && patchFileUrl != "null" && patchFileUrl != "https://example.com/app-patch.bin") patchFileUrl else null
                    val updateConfig = SmartUpdateStatus.NewVersionAvailable(
                        versionNo = cloudVersion,
                        patchFileUrl = allowedPatchUrl,
                        fullApkUrl = fullApkUrl,
                        patchMd5 = patchMd5,
                        isForceUpdate = forceThisUpdate
                    )
                    latestAvailableVersion = updateConfig
                    _isForceUpdateRequired.value = forceThisUpdate
                    activeForceUpdateConfig = if (forceThisUpdate) updateConfig else null
                    _updateStatus.value = updateConfig
                } else {
                    _isForceUpdateRequired.value = false
                    activeForceUpdateConfig = null
                    _updateStatus.value = SmartUpdateStatus.NoUpdateAvailable
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                _updateStatus.value = SmartUpdateStatus.Error("Error checking updates: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Start the download and upgrade flow with priority to Delta Patches.
     */
    fun triggerSmartUpdate(context: Context, newVersion: SmartUpdateStatus.NewVersionAvailable, force: Boolean = false) {
        updateScope.launch {
            try {
                val localVersion = AppUpdateManager.getCurrentVersionCode(context)
                if (newVersion.versionNo <= localVersion) {
                    Log.w(TAG, "Download blocked: Version ID (${newVersion.versionNo}) is below or same as app version number ($localVersion)")
                    _updateStatus.value = SmartUpdateStatus.Error("Cannot download: Update version is not newer than currently installed version.")
                    return@launch
                }

                _updateStatus.value = SmartUpdateStatus.SecuringData

                // 1. Safety Lock Preconditions Check:
                // Do not initiate update download until Room Outbox is fully empty and focus timer status is "Relaxing".
                val db = AppDatabase.getInstance(context)
                var outboxItems = withContext(Dispatchers.IO) {
                    db.outboxQueueDao().getPendingQueueDirect()
                }
                if (!force && outboxItems.isNotEmpty()) {
                    Log.i(TAG, "Safety Lock: Outbox has ${outboxItems.size} items. Triggering active sync drain...")
                    withContext(Dispatchers.IO) {
                        OutboxDrainer.processQueue(context, outboxItems)
                        outboxItems = db.outboxQueueDao().getPendingQueueDirect()
                    }
                }

                val focusStatus = com.example.api.DynamicCommandManager.currentStatusFlow.value
                Log.d(TAG, "Safety Lock: Outbox remaining count: ${outboxItems.size}, Active focus status: $focusStatus")

                val isRelaxing = focusStatus.equals("Relaxing", ignoreCase = true) || 
                                 focusStatus.equals("IDLE", ignoreCase = true) || 
                                 focusStatus.isEmpty()

                if (!force && (outboxItems.isNotEmpty() || !isRelaxing)) {
                    Log.w(TAG, "Safety Lock preconditions NOT met! Focus status must be 'Relaxing' or 'IDLE' (Current: $focusStatus) and local outbox must be empty (Current size: ${outboxItems.size}). Aborting download.")
                    _updateStatus.value = SmartUpdateStatus.Error("Preconditions failed: Ensure focus timer status is 'Relaxing' or 'IDLE' and all pending syncs are complete. [ALLOW_FORCE]")
                    return@launch
                }

                // Preconditions met, proceed with Update!
                val patchUrl = newVersion.patchFileUrl
                if (!patchUrl.isNullOrEmpty()) {
                    Log.i(TAG, "Smart Updater: Delta patch is available. Fetching patch file...")
                    downloadDeltaPatchAndMerge(context, patchUrl, newVersion)
                } else {
                    Log.i(TAG, "Smart Updater: No patch available. Falling back to Full APK download...")
                    downloadFullApk(context, newVersion.fullApkUrl, newVersion)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Smart update process failed", e)
                _updateStatus.value = SmartUpdateStatus.Error("Update process failed: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun downloadDeltaPatchAndMerge(
        context: Context,
        patchUrl: String,
        newVersion: SmartUpdateStatus.NewVersionAvailable
    ) {
        _updateStatus.value = SmartUpdateStatus.Downloading(0f, isPatch = true)
        val otaDir = File(context.cacheDir, "ota_updates")
        if (!otaDir.exists()) otaDir.mkdirs()

        val patchFile = File(otaDir, "v${newVersion.versionNo}_patch.bin")
        val mergedApkFile = File(otaDir, "updated_app.apk")

        try {
            // Download patch using OkHttp inside a coroutine to support internal cache and progress updates
            val success = downloadFileWithProgress(patchUrl, patchFile) { progress ->
                _updateStatus.value = SmartUpdateStatus.Downloading(progress, isPatch = true)
            }

            if (!success) {
                throw IOException("Patch download failed from URL: $patchUrl")
            }

            if (!patchFile.exists() || patchFile.length() < 32) {
                throw IOException("Downloaded patch file is too small or missing (${patchFile.length()} bytes)")
            }

            val headerBytes = ByteArray(8)
            FileInputStream(patchFile).use { fis -> fis.read(headerBytes) }
            val headerStr = String(headerBytes, Charsets.US_ASCII)
            if (headerStr != "BSDIFF40") {
                throw IOException("Invalid delta patch format: Header is '$headerStr' instead of BSDIFF40")
            }

            _updateStatus.value = SmartUpdateStatus.Merging

            // Delta Merge: Locate the currently installed base APK using context.applicationInfo.sourceDir
            val baseApkPath = context.applicationInfo.sourceDir
            val baseApk = File(baseApkPath)

            Log.i(TAG, "Delta Merge: base.apk location: $baseApkPath (${baseApk.length()} bytes)")
            Log.i(TAG, "Delta Merge: patch location: ${patchFile.absolutePath} (${patchFile.length()} bytes)")

            // Merge base.apk + patch.bin -> updated_app.apk
            withContext(Dispatchers.IO) {
                BSPatch.patch(baseApk, mergedApkFile, patchFile)
            }

            // Verify the new APK using Patch_MD5 if provided
            val expectedMd5 = newVersion.patchMd5 ?: ""
            if (expectedMd5.isNotEmpty() && expectedMd5 != "null") {
                val actualMd5 = calculateMD5(mergedApkFile)
                Log.d(TAG, "Delta Merge: Verification. Expected MD5: $expectedMd5, Actual MD5: $actualMd5")
                if (!expectedMd5.equals(actualMd5, ignoreCase = true)) {
                    throw IOException("MD5 checksum mismatch! Patched APK is corrupted. Expected: $expectedMd5, Got: $actualMd5")
                }
            }

            // Verify patched APK is a valid Android package file
            if (!AppUpdateManager.isValidApk(mergedApkFile)) {
                throw IOException("Patched APK file signature/package is invalid.")
            }

            // Post-Merge Patch Deletion (Immediate Cleanup)
            if (patchFile.exists()) {
                if (patchFile.delete()) {
                    Log.i(TAG, "Post-Merge Cleanup: Deleted patch file: ${patchFile.name}")
                }
            }

            // Trigger install
            _updateStatus.value = SmartUpdateStatus.ReadyToInstall(mergedApkFile, newVersion.isForceUpdate)

        } catch (e: Exception) {
            Log.e(TAG, "Delta Patch update failed (${e.message}). Automatically starting clean full version download...", e)
            if (patchFile.exists()) patchFile.delete()
            if (mergedApkFile.exists()) mergedApkFile.delete()

            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Delta patch failed. Auto-downloading clean full version...",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            // AUTOMATICALLY fall back to downloading clean full version!
            downloadFullApk(context, newVersion.fullApkUrl, newVersion)
        }
    }

    private suspend fun downloadFullApk(
        context: Context,
        fullApkUrl: String,
        newVersion: SmartUpdateStatus.NewVersionAvailable
    ) {
        _updateStatus.value = SmartUpdateStatus.Downloading(0f, isPatch = false)
        val otaDir = File(context.cacheDir, "ota_updates")
        if (!otaDir.exists()) otaDir.mkdirs()

        val fullApkFile = File(otaDir, "v${newVersion.versionNo}_full.apk")

        var targetUrl = fullApkUrl
        if (targetUrl.isBlank() || targetUrl == "https://example.com/app-full.apk" || targetUrl == "null") {
            // Check if AppUpdateManager has an APK file URL
            val appState = AppUpdateManager.updateStatus.value
            if (appState is UpdateStatus.NewVersionAvailable && !appState.apkFileId.isNullOrBlank()) {
                targetUrl = appState.apkFileId
            }
        }

        try {
            val success = downloadFileWithProgress(targetUrl, fullApkFile) { progress ->
                _updateStatus.value = SmartUpdateStatus.Downloading(progress, isPatch = false)
            }

            if (!success || !AppUpdateManager.isValidApk(fullApkFile)) {
                throw IOException("Full APK download failed or file is not a valid APK.")
            }

            _updateStatus.value = SmartUpdateStatus.ReadyToInstall(fullApkFile, newVersion.isForceUpdate)

        } catch (e: Exception) {
            Log.e(TAG, "Full APK download fallback failed", e)
            _updateStatus.value = SmartUpdateStatus.Error("Failed to update clean version: ${e.localizedMessage}")
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()

                body.byteStream().use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "File download failed: $url", e)
            return@withContext false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        updateScope.launch {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L || !AppUpdateManager.isValidApk(apkFile)) {
                    _updateStatus.value = SmartUpdateStatus.Error("APK file is missing, empty, or corrupted.")
                    return@launch
                }

                try {
                    com.example.util.AppCrashRollbackManager.backupCurrentWorkingApk(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to backup current working APK prior to installation", e)
                }

                // Stop active background/foreground services so their persistent notifications are removed
                try {
                    context.stopService(Intent(context, com.example.service.KeepAliveService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop KeepAliveService", e)
                }
                try {
                    context.stopService(Intent(context, com.example.service.FocusForegroundService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop FocusForegroundService", e)
                }
                try {
                    context.stopService(Intent(context, com.example.service.NotificationBlockerService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop NotificationBlockerService", e)
                }

                // Dismiss all notifications to prevent SystemUI asset loading crashes during package upgrade
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.cancelAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel notifications prior to installation", e)
                }

                // Small non-blocking delay to allow the services to stop completely and SystemUI to clear notifications
                try {
                    delay(600)
                } catch (e: Exception) {
                    Log.e(TAG, "Delay interrupted", e)
                }

                // Secure user data asynchronously prior to installation
                updateScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getInstance(context)
                        DatabaseBackupHelper.autoBackup(context, db)
                    } catch (e: Exception) {
                        Log.e(TAG, "Pre-install backup failed", e)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        _updateStatus.value = SmartUpdateStatus.Error("Please enable 'Install unknown apps' permission and try again.")
                        return@launch
                    }
                }

                val authority = "${context.packageName}.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
                _updateStatus.value = SmartUpdateStatus.ReadyToInstall(apkFile, false)

            } catch (e: Exception) {
                Log.e(TAG, "Installation intent failed", e)
                _updateStatus.value = SmartUpdateStatus.Error("Installation failed: ${e.localizedMessage}")
            }
        }
    }

    fun calculateMD5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val md5sum = digest.digest()
        val bigInt = java.math.BigInteger(1, md5sum)
        var output = bigInt.toString(16)
        while (output.length < 32) {
            output = "0$output"
        }
        return output
    }
}

/**
 * Standard pure-Kotlin BSPatch implementation supporting sign bit negative longs and standard BSPatch layout.
 */
object BSPatch {
    private fun createDecompressedStream(bytes: ByteArray): InputStream {
        if (bytes.size >= 3 && bytes[0] == 'B'.toByte() && bytes[1] == 'Z'.toByte() && bytes[2] == 'h'.toByte()) {
            return BZip2CompressorInputStream(ByteArrayInputStream(bytes))
        }
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(bytes))
        }
        return try {
            BZip2CompressorInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            try {
                GZIPInputStream(ByteArrayInputStream(bytes))
            } catch (e2: Exception) {
                ByteArrayInputStream(bytes)
            }
        }
    }

    fun patch(oldFile: File, newFile: File, patchFile: File) {
        val oldBytes = oldFile.readBytes()
        val patchBytes = patchFile.readBytes()

        val bis = ByteArrayInputStream(patchBytes)
        val dis = DataInputStream(bis)

        // Read header
        val magicBytes = ByteArray(8)
        dis.readFully(magicBytes)
        val magic = String(magicBytes)

        if (magic != "BSDIFF40") {
            throw IOException("Invalid magic header: expected BSDIFF40, got $magic")
        }

        val ctrlBlockLen = readLong(dis)
        val diffBlockLen = readLong(dis)
        val newSize = readLong(dis).toInt()

        val MAX_BLOCK_SIZE = 100 * 1024 * 1024 // 100 MB max for ctrl/diff block
        val MAX_NEW_SIZE = 300 * 1024 * 1024 // 300 MB max for patched APK size

        if (ctrlBlockLen < 0 || diffBlockLen < 0 || newSize < 0 ||
            ctrlBlockLen > MAX_BLOCK_SIZE || diffBlockLen > MAX_BLOCK_SIZE || newSize > MAX_NEW_SIZE) {
            throw IOException("Invalid patch header: unsafe block sizes (ctrl=$ctrlBlockLen, diff=$diffBlockLen, newSize=$newSize)")
        }

        // Decompress blocks using GZIPInputStream
        val ctrlBytes = ByteArray(ctrlBlockLen.toInt())
        dis.readFully(ctrlBytes)

        val diffBytes = ByteArray(diffBlockLen.toInt())
        dis.readFully(diffBytes)

        val extraBlockLen = patchBytes.size - 32 - ctrlBlockLen.toInt() - diffBlockLen.toInt()
        if (extraBlockLen < 0 || extraBlockLen > MAX_BLOCK_SIZE) {
            throw IOException("Invalid patch header: unsafe extraBlockLen=$extraBlockLen")
        }
        val extraBytes = ByteArray(extraBlockLen)
        dis.readFully(extraBytes)

        val ctrlIn = DataInputStream(createDecompressedStream(ctrlBytes))
        val diffIn = createDecompressedStream(diffBytes)
        val extraIn = createDecompressedStream(extraBytes)

        val newBytes = ByteArray(newSize)
        var oldPtr = 0
        var newPtr = 0

        while (newPtr < newSize) {
            val diffLen = readLong(ctrlIn).toInt()
            val extraLen = readLong(ctrlIn).toInt()
            val offsetAdjust = readLong(ctrlIn).toInt()

            if (newPtr + diffLen > newSize) {
                throw IOException("Corrupt patch: diffLen exceeds output size")
            }

            var i = 0
            while (i < diffLen) {
                val b = diffIn.read()
                if (b == -1) throw EOFException("Unexpected EOF in diff stream")
                val oldVal = if (oldPtr >= 0 && oldPtr < oldBytes.size) oldBytes[oldPtr] else 0
                newBytes[newPtr] = (oldVal + b).toByte()
                newPtr++
                oldPtr++
                i++
            }

            if (newPtr + extraLen > newSize) {
                throw IOException("Corrupt patch: extraLen exceeds output size")
            }

            var j = 0
            while (j < extraLen) {
                val b = extraIn.read()
                if (b == -1) throw EOFException("Unexpected EOF in extra stream")
                newBytes[newPtr] = b.toByte()
                newPtr++
                j++
            }

            oldPtr += offsetAdjust
        }

        ctrlIn.close()
        diffIn.close()
        extraIn.close()

        FileOutputStream(newFile).use { fos ->
            fos.write(newBytes)
        }
    }

    private fun readLong(dis: DataInputStream): Long {
        var valLong = 0L
        for (i in 0..7) {
            val b = dis.read()
            if (b == -1) throw EOFException("Unexpected EOF reading long")
            valLong = valLong or (b.toLong() shl (i * 8))
        }
        val isNegative = (valLong and 0x8000000000000000UL.toLong()) != 0L
        if (isNegative) {
            valLong = valLong and 0x7FFFFFFFFFFFFFFFL
            valLong = -valLong
        }
        return valLong
    }
}
