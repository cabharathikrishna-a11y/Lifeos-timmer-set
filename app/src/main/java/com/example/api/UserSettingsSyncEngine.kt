package com.example.api

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UserSettingsSyncEngine
 *
 * Manages cross-device synchronization of non-live user settings via Firestore
 * document storage (`users/{sanitizedEmail}/user_settings/settings_config`) and FCM / RTDB signals.
 */
object UserSettingsSyncEngine {
    private const val TAG = "UserSettingsSyncEngine"

    private val isListening = AtomicBoolean(false)
    private var activeSignalListener: ValueEventListener? = null
    private var lastSyncedTimestamp = 0L

    /**
     * Upload local settings to Firestore and send sync signal to other devices of the same user.
     */
    fun pushSettingsToCloud(context: Context, email: String) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val deviceKey = DevicePresenceManager.getDeviceKey(context)
        val now = System.currentTimeMillis()
        lastSyncedTimestamp = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!com.example.util.NetworkChecker.isOnline(context)) return@launch

                val firestore = FirebaseFirestore.getInstance(
                    FirebaseApp.getInstance(),
                    "main"
                )

                // Gather preferences from local SharedPreferences
                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).all
                val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).all
                val countdownPrefs = context.getSharedPreferences("countdown_settings_prefs", Context.MODE_PRIVATE).all
                val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE).all
                val calendarPrefs = context.getSharedPreferences("app_calendar_prefs", Context.MODE_PRIVATE).all

                val settingsPayload = mapOf(
                    "updatedAt" to now,
                    "originDeviceId" to deviceKey,
                    "email" to email,
                    "app_prefs" to serializePrefMap(appPrefs),
                    "app_settings" to serializePrefMap(appSettings),
                    "countdown_settings_prefs" to serializePrefMap(countdownPrefs),
                    "strict_mode_prefs" to serializePrefMap(strictPrefs),
                    "app_calendar_prefs" to serializePrefMap(calendarPrefs)
                )

                // 1. Write settings file to Firestore
                firestore.collection("users")
                    .document(sanitizedEmail)
                    .collection("user_settings")
                    .document("settings_config")
                    .set(settingsPayload, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                Log.d(TAG, "Successfully saved settings config to Firestore for $sanitizedEmail")

                // 2. Broadcast RTDB signal so other logged-in devices of this user receive the update
                val dbUrl = FirebaseConfig.getDatabaseUrl(context)
                if (dbUrl.isNotEmpty()) {
                    val database = FirebaseDatabase.getInstance(dbUrl)
                    val signalRef = database.getReference("FOCUS_TIMMER")
                        .child("USER")
                        .child(sanitizedEmail)
                        .child("SETTINGS_SYNC_SIGNAL")

                    val signalData = mapOf(
                        "timestamp" to now,
                        "originDeviceId" to deviceKey,
                        "type" to "SETTINGS_UPDATED"
                    )

                    signalRef.setValue(signalData).await()
                    Log.d(TAG, "Broadcasted SETTINGS_SYNC_SIGNAL to RTDB for $sanitizedEmail")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing settings to cloud for $sanitizedEmail", e)
            }
        }
    }

    /**
     * Download settings from Firestore and apply them locally.
     */
    fun pullSettingsFromCloud(context: Context, email: String, onComplete: (() -> Unit)? = null) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val deviceKey = DevicePresenceManager.getDeviceKey(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!com.example.util.NetworkChecker.isOnline(context)) return@launch

                val firestore = FirebaseFirestore.getInstance(
                    FirebaseApp.getInstance(),
                    "main"
                )

                val docSnap = firestore.collection("users")
                    .document(sanitizedEmail)
                    .collection("user_settings")
                    .document("settings_config")
                    .get()
                    .await()

                if (!docSnap.exists()) {
                    Log.d(TAG, "No remote settings_config file found for $sanitizedEmail")
                    return@launch
                }

                val originDevice = docSnap.getString("originDeviceId") ?: ""
                val updatedAt = docSnap.getLong("updatedAt") ?: 0L

                // Ignore if this device uploaded it
                if (originDevice == deviceKey && updatedAt <= lastSyncedTimestamp) {
                    Log.d(TAG, "Skipping pullSettingsFromCloud: change originated from this device")
                    return@launch
                }

                lastSyncedTimestamp = updatedAt

                // Apply preference maps
                applyPrefMap(context, "app_prefs", docSnap.get("app_prefs") as? Map<*, *>)
                applyPrefMap(context, "app_settings", docSnap.get("app_settings") as? Map<*, *>)
                applyPrefMap(context, "countdown_settings_prefs", docSnap.get("countdown_settings_prefs") as? Map<*, *>)
                applyPrefMap(context, "strict_mode_prefs", docSnap.get("strict_mode_prefs") as? Map<*, *>)
                applyPrefMap(context, "app_calendar_prefs", docSnap.get("app_calendar_prefs") as? Map<*, *>)

                Log.d(TAG, "Successfully pulled and applied remote settings for $sanitizedEmail")

                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pulling settings from cloud for $sanitizedEmail", e)
            }
        }
    }

    /**
     * Listen for settings sync signals from other devices of the same user.
     */
    fun startListeningForRemoteSettingsUpdates(context: Context, email: String) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        if (!isListening.compareAndSet(false, true)) return

        try {
            val database = FirebaseDatabase.getInstance(dbUrl)
            val signalRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("SETTINGS_SYNC_SIGNAL")

            val deviceKey = DevicePresenceManager.getDeviceKey(context)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val originDeviceId = snapshot.child("originDeviceId").getValue(String::class.java) ?: ""
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    if (originDeviceId.isNotEmpty() && originDeviceId != deviceKey && timestamp > lastSyncedTimestamp) {
                        Log.i(TAG, "Received SETTINGS_SYNC_SIGNAL from device $originDeviceId. Syncing settings...")
                        pullSettingsFromCloud(context, email)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "SETTINGS_SYNC_SIGNAL listener cancelled: ${error.message}")
                }
            }

            activeSignalListener = listener
            signalRef.addValueEventListener(listener)
            Log.d(TAG, "Started listening for remote settings signals for $sanitizedEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start settings signal listener", e)
            isListening.set(false)
        }
    }

    fun stopListening(context: Context, email: String) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        try {
            val listener = activeSignalListener
            if (listener != null) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                database.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("SETTINGS_SYNC_SIGNAL")
                    .removeEventListener(listener)
                activeSignalListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping settings signal listener", e)
        } finally {
            isListening.set(false)
        }
    }

    private fun serializePrefMap(map: Map<String, *>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((k, v) in map) {
            if (v != null) {
                result[k] = v.toString()
            }
        }
        return result
    }

    private fun applyPrefMap(context: Context, prefName: String, dataMap: Map<*, *>?) {
        if (dataMap == null) return
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for ((k, v) in dataMap) {
            val key = k.toString()
            val strVal = v.toString()

            // Best effort type recovery
            if (strVal == "true" || strVal == "false") {
                editor.putBoolean(key, strVal.toBoolean())
            } else if (strVal.toLongOrNull() != null) {
                editor.putLong(key, strVal.toLong())
            } else if (strVal.toIntOrNull() != null) {
                editor.putInt(key, strVal.toInt())
            } else if (strVal.toFloatOrNull() != null) {
                editor.putFloat(key, strVal.toFloat())
            } else {
                editor.putString(key, strVal)
            }
        }
        editor.apply()
    }
}
