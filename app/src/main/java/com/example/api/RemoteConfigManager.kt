package com.example.api

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SduiPreferences(
    val rawBannerText: String = "CA Inter Exams on 1st Sep 2026! Grind!",
    val isArenaEnabled: Boolean = true,
    val defaultThemeOverride: String = "SLATE_DARK"
) {
    val motivationalBannerText: String
        get() {
            try {
                val targetCal = java.util.Calendar.getInstance().apply {
                    set(2026, java.util.Calendar.SEPTEMBER, 1, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val diffMs = targetCal.timeInMillis - System.currentTimeMillis()
                val days = if (diffMs > 0) {
                    // Safe division for days left
                    diffMs / (1000L * 60L * 60L * 24L)
                } else {
                    0L
                }
                return "CA Inter Exams in $days Days! Grind!"
            } catch (e: Exception) {
                return "CA Inter Exams on 1st Sep 2026! Grind!"
            }
        }
}

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val PREFS_NAME = "sdui_prefs"
    private const val KEY_BANNER_TEXT = "motivational_banner_text"
    private const val KEY_ARENA_ENABLED = "is_arena_enabled"
    private const val KEY_THEME_OVERRIDE = "default_theme_override"

    private val _sduiPreferences = MutableStateFlow(SduiPreferences())
    val sduiPreferences: StateFlow<SduiPreferences> = _sduiPreferences.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedBanner = prefs.getString(KEY_BANNER_TEXT, "CA Inter Exams on 1st Sep 2026! Grind!") ?: "CA Inter Exams on 1st Sep 2026! Grind!"
        val cachedArena = prefs.getBoolean(KEY_ARENA_ENABLED, true)
        val cachedTheme = prefs.getString(KEY_THEME_OVERRIDE, "SLATE_DARK") ?: "SLATE_DARK"

        _sduiPreferences.value = SduiPreferences(
            rawBannerText = cachedBanner,
            isArenaEnabled = cachedArena,
            defaultThemeOverride = cachedTheme
        )

        // Ensure APP_CONFIG nodes are removed from Realtime Database to prevent unused data fragmentation
        try {
            Firebase.ensureFirebaseInitialized(context)
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance(dbUrl)
                database.getReference("APP_CONFIG").removeValue()
                database.getReference("FOCUS_TIMMER/APP_CONFIG").removeValue()
                Log.d(TAG, "Successfully requested removal of APP_CONFIG from RTDB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing APP_CONFIG from RTDB", e)
        }
    }
}
