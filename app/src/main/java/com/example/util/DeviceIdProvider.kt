package com.example.util

import android.content.Context
import java.util.UUID

object DeviceIdProvider {
    @Volatile
    private var cachedDeviceId: String? = null

    fun getDeviceId(context: Context): String {
        return cachedDeviceId ?: synchronized(this) {
            cachedDeviceId ?: run {
                val rawModel = android.os.Build.MODEL ?: "Android_Device"
                val sanitized = rawModel.replace(Regex("[.\\$\\[\\]#/\\s]"), "_").trim()
                val finalId = if (sanitized.isEmpty()) "Android_Device" else sanitized
                
                // Overwrite any old SharedPreferences cache to ensure consistency
                val prefs = context.applicationContext.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("device_id", finalId).apply()
                
                cachedDeviceId = finalId
                finalId
            }
        }
    }

    fun forceSetDeviceId(context: Context, newId: String) {
        synchronized(this) {
            val prefs = context.applicationContext.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("device_id", newId).apply()
            cachedDeviceId = newId
        }
    }
}
