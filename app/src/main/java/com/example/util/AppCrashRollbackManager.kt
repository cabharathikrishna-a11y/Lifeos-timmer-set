package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * AppCrashRollbackManager
 *
 * Provides automatic self-healing, crash detection, clipboard error reporting,
 * and version rollback/degradation capabilities for startup failures.
 *
 * FLOW:
 * 1. Catches uncaught exceptions / startup crashes.
 * 2. Immediately copies full crash stack trace & error code to Android Clipboard.
 * 3. Registers the crashed version as a "failed version" in SharedPreferences.
 * 4. On reboot/rollback check, automatically degrades/installs the last known working APK.
 * 5. Bypasses failed version updates (e.g., skips v57 and allows upgrading to v58 when available).
 * 6. Once a newer version successfully completes startup, sets it as the active working baseline
 *    and DELETES all older backup APKs and failed version records!
 */
object AppCrashRollbackManager {
    private const val TAG = "AppCrashRollback"
    private const val PREFS_NAME = "app_crash_rollback_prefs"

    private const val KEY_LAST_WORKING_VERSION = "key_last_working_version"
    private const val KEY_FAILED_VERSION_CODES = "key_failed_version_codes"
    private const val KEY_ROLLBACK_PENDING = "key_rollback_pending"
    private const val KEY_FAILED_VERSION = "key_failed_version"
    private const val KEY_LAST_CRASH_LOG = "key_last_crash_log"

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false

    /**
     * Initializes crash handling and sets up default uncaught exception interceptor.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext

        // Backup current APK on initial boot if no working backup exists yet
        try {
            backupCurrentWorkingApk(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed initial APK backup check", e)
        }

        // Set up Uncaught Exception Handler for startup error capture
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is CrashRollbackExceptionHandler) {
            defaultExceptionHandler = currentHandler
            Thread.setDefaultUncaughtExceptionHandler(CrashRollbackExceptionHandler(appContext, currentHandler))
            Log.i(TAG, "Registered AppCrashRollbackManager UncaughtExceptionHandler.")
        }
    }

    private class CrashRollbackExceptionHandler(
        private val context: Context,
        private val defaultHandler: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                handleCrash(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling uncaught exception", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Called when an uncaught exception occurs.
     */
    fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        val currentVersionCode = AppUpdateManager.getCurrentVersionCode(context)
        val stackTrace = Log.getStackTraceString(throwable)

        val crashReportText = """
            ==================================================
            [APP STARTUP FAILURE / CRASH REPORT]
            Version Code: $currentVersionCode
            Version Name: ${AppUpdateManager.getCurrentVersionName(context)}
            Thread: ${thread.name}
            Error Type: ${throwable.javaClass.name}
            Message: ${throwable.message ?: "No error message"}
            Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            --------------------------------------------------
            STACK TRACE:
            $stackTrace
            ==================================================
        """.trimIndent()

        Log.e(TAG, "CRASH DETECTED on v$currentVersionCode!\n$crashReportText")

        // 1. Copy error stacktrace directly to Android Clipboard
        copyToClipboard(context, "Startup Error Log (v$currentVersionCode)", crashReportText)

        // 2. Save crash log and mark version as failed
        val prefs = getPrefs(context)
        val failedSet = prefs.getStringSet(KEY_FAILED_VERSION_CODES, emptySet())?.toMutableSet() ?: mutableSetOf()
        failedSet.add(currentVersionCode.toString())

        prefs.edit()
            .putStringSet(KEY_FAILED_VERSION_CODES, failedSet)
            .putBoolean(KEY_ROLLBACK_PENDING, true)
            .putInt(KEY_FAILED_VERSION, currentVersionCode)
            .putString(KEY_LAST_CRASH_LOG, crashReportText)
            .apply()

        Log.w(TAG, "Marked version code $currentVersionCode as failed. Rollback pending for next startup.")
    }

    /**
     * Copies text directly to the Android Clipboard.
     */
    fun copyToClipboard(context: Context, label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            Log.i(TAG, "Successfully copied crash report to clipboard.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying crash log to clipboard", e)
        }
    }

    /**
     * Checks if a rollback is pending or if the current version is marked as non-working.
     * Triggers installation of the previous working APK if found.
     */
    fun checkAndExecuteRollback(context: Context): Boolean {
        val prefs = getPrefs(context)
        val currentVersion = AppUpdateManager.getCurrentVersionCode(context)
        val isRollbackPending = prefs.getBoolean(KEY_ROLLBACK_PENDING, false)
        val failedSet = prefs.getStringSet(KEY_FAILED_VERSION_CODES, emptySet()) ?: emptySet()
        val isCurrentVersionFailed = failedSet.contains(currentVersion.toString())

        if (!isRollbackPending && !isCurrentVersionFailed) {
            return false
        }

        Log.w(TAG, "Rollback check triggered! Current Version=$currentVersion, RollbackPending=$isRollbackPending, FailedVersions=$failedSet")

        // Copy last crash log to clipboard again just in case
        val lastCrashLog = prefs.getString(KEY_LAST_CRASH_LOG, "") ?: ""
        if (lastCrashLog.isNotBlank()) {
            copyToClipboard(context, "Startup Error Log (v$currentVersion)", lastCrashLog)
        }

        val workingApkFile = findLatestWorkingBackupApk(context, currentVersion, failedSet)
        if (workingApkFile != null && workingApkFile.exists()) {
            val workingVersion = parseVersionFromBackupFileName(workingApkFile.name)
            Log.i(TAG, "Found working backup APK: ${workingApkFile.name} (v$workingVersion). Executing rollback installation...")

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Startup error detected on v$currentVersion!\nError log copied to Clipboard.\nDegrading to previous working version v$workingVersion...",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Clear pending flag prior to launching package installer
            prefs.edit().putBoolean(KEY_ROLLBACK_PENDING, false).apply()

            // Trigger installation of previous working APK
            AppUpdateManager.installApk(context, workingApkFile)
            return true
        } else {
            Log.e(TAG, "No valid working backup APK found for rollback from v$currentVersion")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Startup error on v$currentVersion (copied to Clipboard). No previous APK backup found to roll back.",
                    Toast.LENGTH_LONG
                ).show()
            }
            prefs.edit().putBoolean(KEY_ROLLBACK_PENDING, false).apply()
            return false
        }
    }

    /**
     * Called once the app starts up successfully and reaches a stable running state.
     * Establishes the current version as the new working baseline and DELETES older APK backups
     * and non-working version histories.
     */
    fun markStartupSuccess(context: Context) {
        val currentVersion = AppUpdateManager.getCurrentVersionCode(context)
        val prefs = getPrefs(context)
        val lastWorking = prefs.getInt(KEY_LAST_WORKING_VERSION, -1)

        Log.i(TAG, "Confirming startup success for version code $currentVersion (Previous working = $lastWorking)")

        // Save running APK as the latest working backup
        backupCurrentWorkingApk(context)

        // Record current version as active working baseline
        prefs.edit()
            .putInt(KEY_LAST_WORKING_VERSION, currentVersion)
            .putBoolean(KEY_ROLLBACK_PENDING, false)
            // Clear failed version set once a successful upgrade/startup is achieved
            .remove(KEY_FAILED_VERSION_CODES)
            .remove(KEY_FAILED_VERSION)
            .remove(KEY_LAST_CRASH_LOG)
            .apply()

        // Prune older backup APK files (keep only current working baseline)
        pruneOlderBackupApks(context, currentVersion)
    }

    /**
     * Checks if a given version code has been marked as failed/non-working.
     */
    fun isVersionFailed(context: Context, versionCode: Int): Boolean {
        val prefs = getPrefs(context)
        val failedSet = prefs.getStringSet(KEY_FAILED_VERSION_CODES, emptySet()) ?: emptySet()
        return failedSet.contains(versionCode.toString())
    }

    /**
     * Gets set of all failed version codes.
     */
    fun getFailedVersionCodes(context: Context): Set<Int> {
        val prefs = getPrefs(context)
        val set = prefs.getStringSet(KEY_FAILED_VERSION_CODES, emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }

    /**
     * Gets the last known working version code.
     */
    fun getLastWorkingVersionCode(context: Context): Int {
        val prefs = getPrefs(context)
        return prefs.getInt(KEY_LAST_WORKING_VERSION, AppUpdateManager.getCurrentVersionCode(context))
    }

    /**
     * Backs up the currently running APK source file to `apk_backups/working_v{versionCode}.apk`.
     */
    fun backupCurrentWorkingApk(context: Context) {
        val currentVersion = AppUpdateManager.getCurrentVersionCode(context)
        val backupDir = getBackupDir(context)
        val targetFile = File(backupDir, "working_v${currentVersion}.apk")

        if (targetFile.exists() && targetFile.length() > 0) {
            return // Backup already exists
        }

        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (sourceApk.exists() && sourceApk.length() > 0) {
                copyFile(sourceApk, targetFile)
                Log.i(TAG, "Successfully backed up running APK v$currentVersion (${targetFile.length()} bytes) to ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating working APK backup for v$currentVersion", e)
        }
    }

    /**
     * Deletes older backup APKs and non-working files once a newer working condition is established.
     */
    private fun pruneOlderBackupApks(context: Context, currentWorkingVersion: Int) {
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return

        val files = backupDir.listFiles() ?: return
        for (file in files) {
            val ver = parseVersionFromBackupFileName(file.name)
            if (ver > 0 && ver < currentWorkingVersion) {
                Log.i(TAG, "Pruning older working backup APK: ${file.name} (v$ver < current working v$currentWorkingVersion)")
                file.delete()
            }
        }
    }

    private fun findLatestWorkingBackupApk(context: Context, currentVersion: Int, failedSet: Set<String>): File? {
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return null

        val files = backupDir.listFiles() ?: return null
        var bestFile: File? = null
        var highestVersion = -1

        for (file in files) {
            if (!file.name.endsWith(".apk")) continue
            val ver = parseVersionFromBackupFileName(file.name)
            if (ver > 0 && ver < currentVersion && !failedSet.contains(ver.toString())) {
                if (ver > highestVersion) {
                    highestVersion = ver
                    bestFile = file
                }
            }
        }

        return bestFile
    }

    private fun parseVersionFromBackupFileName(name: String): Int {
        // Expected format: working_v56.apk
        return try {
            name.substringAfter("working_v").substringBefore(".apk").toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, "apk_backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}
