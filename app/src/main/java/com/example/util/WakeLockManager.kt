package com.example.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

object WakeLockManager {
    private const val TAG = "WakeLockManager"
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context) {
        synchronized(this) {
            if (wakeLock?.isHeld == true) {
                Log.d(TAG, "WakeLock already held.")
                return
            }
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LifeOS::FocusTimerWakeLock"
                ).apply {
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire WakeLock", e)
            }
        }
    }

    fun release() {
        synchronized(this) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d(TAG, "WakeLock released successfully.")
                } else {
                    Log.d(TAG, "WakeLock not held.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock", e)
            } finally {
                wakeLock = null
            }
        }
    }
}
