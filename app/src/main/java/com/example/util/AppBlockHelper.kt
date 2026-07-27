package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

object AppBlockHelper {
    private const val PREFS_NAME = "app_blocks_preferences"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_LAST_USAGE_DATE = "last_usage_date"
    private const val PREFIX_DAILY_LIMIT = "daily_limit_"
    private const val PREFIX_DAILY_USAGE = "daily_usage_"
    private const val PREFIX_SESSION_EXPIRY = "session_expiry_"
    private const val PREFIX_DAILY_BYPASS = "daily_bypass_"

    // In-memory cache for ultra-fast accessibility checks without disk I/O
    @Volatile private var isCacheInitialized = false
    private val cachedBlockedApps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cachedStrictBlockedPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    @Volatile private var cachedStrictModeEnabled = false
    
    // Instagram selective blocking cache
    @Volatile private var cachedIgUseSelective = true
    @Volatile private var cachedIgReelsBlocked = true
    @Volatile private var cachedIgStoriesBlocked = true
    @Volatile private var cachedIgExploreBlocked = true
    @Volatile private var cachedIgAllowSharedReels = true
    @Volatile private var cachedIgFeedScrollLimit = false
    @Volatile private var cachedIgReelsMuteAudio = true
    @Volatile private var cachedIgReelsLimitMinutes = 0

    // YouTube selective blocking cache
    @Volatile private var cachedYtUseSelective = true
    @Volatile private var cachedYtShortsBlocked = true
    @Volatile private var cachedYtSearchBlocked = false
    @Volatile private var cachedYtCommentsBlocked = false
    @Volatile private var cachedYtOnlyAllowApprovedChannels = false
    @Volatile private var cachedYtApprovedChannels = ""

    // Snapchat selective blocking cache
    @Volatile private var cachedSnapUseSelective = true
    @Volatile private var cachedSnapDiscoverBlocked = true
    @Volatile private var cachedSnapSpotlightBlocked = true
    @Volatile private var cachedSnapMapBlocked = false

    // Facebook selective blocking cache
    @Volatile private var cachedFbUseSelective = true
    @Volatile private var cachedFbWatchBlocked = true
    @Volatile private var cachedFbStoriesBlocked = true

    @Synchronized
    fun initializeCache(context: Context) {
        if (isCacheInitialized) return
        
        val appContext = context.applicationContext
        
        // Load block apps
        val blockPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedBlock = blockPrefs.getStringSet(KEY_BLOCKED_APPS, null) ?: DEFAULT_BLOCKED_APPS
        cachedBlockedApps.clear()
        cachedBlockedApps.addAll(savedBlock)
        
        // Load strict mode prefs
        val strictPrefs = appContext.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        cachedStrictModeEnabled = strictPrefs.getBoolean("strict_mode_enabled", false)
        val savedStrict = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        cachedStrictBlockedPackages.clear()
        cachedStrictBlockedPackages.addAll(savedStrict)

        // Load Instagram prefs
        val igPrefs = appContext.getSharedPreferences("instagram_blocker_prefs", Context.MODE_PRIVATE)
        cachedIgUseSelective = igPrefs.getBoolean("ig_use_selective_blocking", true)
        cachedIgReelsBlocked = igPrefs.getBoolean("ig_reels_blocked", true)
        cachedIgStoriesBlocked = igPrefs.getBoolean("ig_stories_blocked", true)
        cachedIgExploreBlocked = igPrefs.getBoolean("ig_explore_blocked", true)
        cachedIgAllowSharedReels = igPrefs.getBoolean("ig_allow_shared_reels", true)
        cachedIgFeedScrollLimit = igPrefs.getBoolean("ig_feed_scroll_limit", false)
        cachedIgReelsMuteAudio = igPrefs.getBoolean("ig_reels_mute_audio", true)
        cachedIgReelsLimitMinutes = igPrefs.getInt("ig_reels_limit_minutes", 0)

        // Load YouTube prefs
        val ytPrefs = appContext.getSharedPreferences("youtube_blocker_prefs", Context.MODE_PRIVATE)
        cachedYtUseSelective = ytPrefs.getBoolean("yt_use_selective_blocking", true)
        cachedYtShortsBlocked = ytPrefs.getBoolean("yt_shorts_blocked", true)
        cachedYtSearchBlocked = ytPrefs.getBoolean("yt_search_blocked", false)
        cachedYtCommentsBlocked = ytPrefs.getBoolean("yt_comments_blocked", false)
        cachedYtOnlyAllowApprovedChannels = ytPrefs.getBoolean("yt_only_allow_approved_channels", false)
        cachedYtApprovedChannels = ytPrefs.getString("yt_approved_channels", "") ?: ""

        // Load Snapchat prefs
        val snapPrefs = appContext.getSharedPreferences("snapchat_blocker_prefs", Context.MODE_PRIVATE)
        cachedSnapUseSelective = snapPrefs.getBoolean("snap_use_selective_blocking", true)
        cachedSnapDiscoverBlocked = snapPrefs.getBoolean("snap_discover_blocked", true)
        cachedSnapSpotlightBlocked = snapPrefs.getBoolean("snap_spotlight_blocked", true)
        cachedSnapMapBlocked = snapPrefs.getBoolean("snap_map_blocked", false)

        // Load Facebook prefs
        val fbPrefs = appContext.getSharedPreferences("facebook_blocker_prefs", Context.MODE_PRIVATE)
        cachedFbUseSelective = fbPrefs.getBoolean("fb_use_selective_blocking", true)
        cachedFbWatchBlocked = fbPrefs.getBoolean("fb_watch_blocked", true)
        cachedFbStoriesBlocked = fbPrefs.getBoolean("fb_stories_blocked", true)

        // Register SharedPreferences listeners so the cache stays in sync dynamically
        blockPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BLOCKED_APPS) {
                val updated = blockPrefs.getStringSet(KEY_BLOCKED_APPS, null) ?: DEFAULT_BLOCKED_APPS
                cachedBlockedApps.clear()
                cachedBlockedApps.addAll(updated)
            }
        }

        strictPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "strict_mode_enabled" -> {
                    cachedStrictModeEnabled = strictPrefs.getBoolean("strict_mode_enabled", false)
                }
                "blocked_packages" -> {
                    val updated = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                    cachedStrictBlockedPackages.clear()
                    cachedStrictBlockedPackages.addAll(updated)
                }
            }
        }

        igPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "ig_use_selective_blocking" -> cachedIgUseSelective = igPrefs.getBoolean("ig_use_selective_blocking", true)
                "ig_reels_blocked" -> cachedIgReelsBlocked = igPrefs.getBoolean("ig_reels_blocked", true)
                "ig_stories_blocked" -> cachedIgStoriesBlocked = igPrefs.getBoolean("ig_stories_blocked", true)
                "ig_explore_blocked" -> cachedIgExploreBlocked = igPrefs.getBoolean("ig_explore_blocked", true)
                "ig_allow_shared_reels" -> cachedIgAllowSharedReels = igPrefs.getBoolean("ig_allow_shared_reels", true)
                "ig_feed_scroll_limit" -> cachedIgFeedScrollLimit = igPrefs.getBoolean("ig_feed_scroll_limit", false)
                "ig_reels_mute_audio" -> cachedIgReelsMuteAudio = igPrefs.getBoolean("ig_reels_mute_audio", true)
                "ig_reels_limit_minutes" -> cachedIgReelsLimitMinutes = igPrefs.getInt("ig_reels_limit_minutes", 0)
            }
        }

        ytPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "yt_use_selective_blocking" -> cachedYtUseSelective = ytPrefs.getBoolean("yt_use_selective_blocking", true)
                "yt_shorts_blocked" -> cachedYtShortsBlocked = ytPrefs.getBoolean("yt_shorts_blocked", true)
                "yt_search_blocked" -> cachedYtSearchBlocked = ytPrefs.getBoolean("yt_search_blocked", false)
                "yt_comments_blocked" -> cachedYtCommentsBlocked = ytPrefs.getBoolean("yt_comments_blocked", false)
                "yt_only_allow_approved_channels" -> cachedYtOnlyAllowApprovedChannels = ytPrefs.getBoolean("yt_only_allow_approved_channels", false)
                "yt_approved_channels" -> cachedYtApprovedChannels = ytPrefs.getString("yt_approved_channels", "") ?: ""
            }
        }

        snapPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "snap_use_selective_blocking" -> cachedSnapUseSelective = snapPrefs.getBoolean("snap_use_selective_blocking", true)
                "snap_discover_blocked" -> cachedSnapDiscoverBlocked = snapPrefs.getBoolean("snap_discover_blocked", true)
                "snap_spotlight_blocked" -> cachedSnapSpotlightBlocked = snapPrefs.getBoolean("snap_spotlight_blocked", true)
                "snap_map_blocked" -> cachedSnapMapBlocked = snapPrefs.getBoolean("snap_map_blocked", false)
            }
        }

        fbPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "fb_use_selective_blocking" -> cachedFbUseSelective = fbPrefs.getBoolean("fb_use_selective_blocking", true)
                "fb_watch_blocked" -> cachedFbWatchBlocked = fbPrefs.getBoolean("fb_watch_blocked", true)
                "fb_stories_blocked" -> cachedFbStoriesBlocked = fbPrefs.getBoolean("fb_stories_blocked", true)
            }
        }

        isCacheInitialized = true
    }

    // Default social apps
    val DEFAULT_BLOCKED_APPS = setOf("com.instagram.android", "com.snapchat.android")

    /**
     * Checks if an app is installed on the device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initializes default strict apps on install/first load of settings if not already done.
     */
    fun initializeStrictAppsIfNeeded(context: Context) {
        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        if (!strictPrefs.contains("strict_mode_enabled")) {
            strictPrefs.edit().putBoolean("strict_mode_enabled", true).apply()
        }
        if (!strictPrefs.getBoolean("strict_apps_initialized_v2", false)) {
            val defaultStrict = mutableSetOf<String>()
            if (isAppInstalled(context, "com.instagram.android")) {
                defaultStrict.add("com.instagram.android")
            }
            if (isAppInstalled(context, "com.snapchat.android")) {
                defaultStrict.add("com.snapchat.android")
            }
            if (defaultStrict.isNotEmpty()) {
                val currentSet = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                val resultSet = currentSet + defaultStrict
                strictPrefs.edit()
                    .putStringSet("blocked_packages", resultSet)
                    .putBoolean("strict_apps_initialized_v2", true)
                    .apply()
            } else {
                // Mark initialized even if none installed to avoid repeating checks
                strictPrefs.edit().putBoolean("strict_apps_initialized_v2", true).apply()
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if Usage Stats permission is robustly granted.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                true
            } else {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                if (usageStatsManager != null) {
                    val now = System.currentTimeMillis()
                    val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60000, now)
                    !stats.isNullOrEmpty()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Resets or updates daily stats if the date has changed.
     */
    fun checkAndResetDailyUsageIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_LAST_USAGE_DATE, "")
        
        if (savedDate != today) {
            val editor = prefs.edit()
            editor.putString(KEY_LAST_USAGE_DATE, today)
            
            // Clear all accumulated daily usage seconds and bypass states
            val allKeys = prefs.all
            for (key in allKeys.keys) {
                if (key.startsWith(PREFIX_DAILY_USAGE)) {
                    editor.putInt(key, 0)
                } else if (key.startsWith(PREFIX_DAILY_BYPASS)) {
                    editor.putBoolean(key, false)
                }
            }
            editor.apply()
            Log.d("AppBlockHelper", "Daily screen-time usage stats reset for a new day: $today")
        }
    }

    /**
     * Set whether the daily limit has been bypassed for the day.
     */
    fun setDailyBypass(context: Context, packageName: String, bypassed: Boolean) {
        getPrefs(context).edit().putBoolean(PREFIX_DAILY_BYPASS + packageName, bypassed).apply()
    }

    /**
     * Check if the daily limit has been bypassed for the day.
     */
    fun isDailyBypassed(context: Context, packageName: String): Boolean {
        checkAndResetDailyUsageIfNeeded(context)
        return getPrefs(context).getBoolean(PREFIX_DAILY_BYPASS + packageName, false)
    }

    /**
     * Gets the set of monitored apps (including standard ones and user-added ones).
     */
    fun getBlockedApps(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val saved = prefs.getStringSet(KEY_BLOCKED_APPS, null)
        if (saved == null) {
            // First time initialization
            prefs.edit().putStringSet(KEY_BLOCKED_APPS, DEFAULT_BLOCKED_APPS).apply()
            return DEFAULT_BLOCKED_APPS
        }
        return saved
    }

    /**
     * Updates the monitored apps set.
     */
    fun setBlockedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
        
        // Update strict mode blocked packages to match exactly
        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        strictPrefs.edit().putStringSet("blocked_packages", apps).apply()
    }

    /**
     * Checks if a package is actively present in the user's block list.
     */
    fun isAppInBlockList(context: Context, packageName: String): Boolean {
        return getBlockedApps(context).contains(packageName)
    }

    /**
     * Adds an app to the monitored apps list.
     */
    fun addBlockedApp(context: Context, packageName: String) {
        val current = getBlockedApps(context).toMutableSet()
        current.add(packageName)
        setBlockedApps(context, current)
    }

    /**
     * Removes an app from the monitored apps list.
     */
    fun removeBlockedApp(context: Context, packageName: String) {
        val current = getBlockedApps(context).toMutableSet()
        current.remove(packageName)
        setBlockedApps(context, current)
    }

    /**
     * Gets the configured daily limit in minutes for an app. Defaults to 30 mins.
     */
    fun getDailyLimitMinutes(context: Context, packageName: String): Int {
        val defaultLimit = if (packageName == "com.instagram.android" || packageName == "com.snapchat.android" || packageName == "com.google.android.youtube") 45 else 30
        return getPrefs(context).getInt(PREFIX_DAILY_LIMIT + packageName, defaultLimit)
    }

    /**
     * Sets the configured daily limit in minutes for an app.
     */
    fun setDailyLimitMinutes(context: Context, packageName: String, minutes: Int) {
        getPrefs(context).edit().putInt(PREFIX_DAILY_LIMIT + packageName, minutes).apply()
    }

    /**
     * Gets today's usage in seconds for an app.
     */
    fun getDailyUsageSeconds(context: Context, packageName: String): Int {
        checkAndResetDailyUsageIfNeeded(context)
        return getPrefs(context).getInt(PREFIX_DAILY_USAGE + packageName, 0)
    }

    /**
     * Increments today's usage by a specified amount of seconds (defaults to 1).
     */
    fun incrementDailyUsageSeconds(context: Context, packageName: String, amount: Int = 1) {
        checkAndResetDailyUsageIfNeeded(context)
        val current = getDailyUsageSeconds(context, packageName)
        getPrefs(context).edit().putInt(PREFIX_DAILY_USAGE + packageName, current + amount).apply()
    }

    /**
     * Checks if today's usage for an app has exceeded its daily limit.
     */
    fun isDailyLimitExceeded(context: Context, packageName: String): Boolean {
        val limitMinutes = getDailyLimitMinutes(context, packageName)
        val usageSeconds = getDailyUsageSeconds(context, packageName)
        return usageSeconds >= (limitMinutes * 60)
    }

    /**
     * Starts a temporary usage session (e.g., 5, 10, 15, 20 minutes) for an app.
     */
    fun startTemporarySession(context: Context, packageName: String, durationMinutes: Int) {
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        getPrefs(context).edit().putLong(PREFIX_SESSION_EXPIRY + packageName, expiryTime).apply()
        Log.d("AppBlockHelper", "Started temporary session for $packageName: duration = $durationMinutes minutes")
    }

    /**
     * Checks if an active temporary session is currently running and hasn't expired.
     */
    fun isSessionActive(context: Context, packageName: String): Boolean {
        val expiryTime = getPrefs(context).getLong(PREFIX_SESSION_EXPIRY + packageName, 0L)
        val active = System.currentTimeMillis() < expiryTime
        if (!active && expiryTime > 0L) {
            // Clean up expired session
            getPrefs(context).edit().putLong(PREFIX_SESSION_EXPIRY + packageName, 0L).apply()
        }
        return active
    }

    /**
     * Checks if we need to show the temporary duration select popup for this app.
     * This occurs if the app is opened, daily limit is NOT exceeded, and there is no active session.
     */
    fun shouldShowSessionSelector(context: Context, packageName: String): Boolean {
        val isBlocked = getBlockedApps(context).contains(packageName)
        if (!isBlocked) return false
        
        val limitOver = isDailyLimitExceeded(context, packageName)
        if (limitOver) return false
        
        val sessionActive = isSessionActive(context, packageName)
        return !sessionActive
    }

    /**
     * Clear all sessions for manual resets.
     */
    fun clearSessionForPackage(context: Context, packageName: String) {
        getPrefs(context).edit().putLong(PREFIX_SESSION_EXPIRY + packageName, 0L).apply()
    }

    fun clearSessions(context: Context) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        val allKeys = prefs.all
        for (key in allKeys.keys) {
            if (key.startsWith(PREFIX_SESSION_EXPIRY)) {
                editor.putLong(key, 0L)
            }
        }
        editor.apply()
    }

    fun isPackageBlockedInStrictMode(context: Context, packageName: String): Boolean {
        if (!isCacheInitialized) {
            initializeCache(context)
        }
        // Strict mode only blocks apps selected in blocks and screen limits
        if (!cachedStrictBlockedPackages.contains(packageName)) {
            return false
        }

        // 1. Exclude our own app
        if (packageName == context.packageName) return false
        
        // 2. Exclude Google Gemini, WhatsApp, Chrome
        val lowerPkg = packageName.lowercase()
        if (lowerPkg.contains("gemini") || 
            lowerPkg.contains("bard") || 
            lowerPkg.contains("whatsapp") || 
            lowerPkg.contains("chrome") ||
            packageName == "com.google.android.apps.bard" ||
            packageName == "com.whatsapp" ||
            packageName == "com.android.chrome") {
            return false
        }
        
        // 3. Exclude essential apps: Phone, Messages, Contacts
        if (lowerPkg.contains("dialer") || 
            lowerPkg.contains("phone") || 
            lowerPkg.contains("messaging") || 
            lowerPkg.contains("message") ||
            lowerPkg.contains("contacts") ||
            lowerPkg.contains("contacts.android") ||
            packageName == "com.google.android.dialer" ||
            packageName == "com.android.phone" ||
            packageName == "com.google.android.apps.messaging" ||
            packageName == "com.android.messaging" ||
            packageName == "com.google.android.contacts" ||
            packageName == "com.android.contacts") {
            return false
        }
        
        // 4. Exclude system launchers / UI (Android OS components)
        if (lowerPkg.contains("launcher") || 
            lowerPkg.contains("systemui") || 
            packageName == "android" || 
            packageName == "com.android.systemui") {
            return false
        }
        
        // 5. Exclude system apps (unless it's an updated system app)
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
        
        return true
    }

    fun isFocusGuardEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("focus_guard_enabled", true)
    }

    fun checkForegroundAppAndBlockIfNeeded(context: Context, packageName: String) {
        if (!isFocusGuardEnabled(context)) return
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        if (!isFocusing) return

        if (packageName == "com.instagram.android") {
            val igPrefs = context.getSharedPreferences("instagram_blocker_prefs", Context.MODE_PRIVATE)
            val useSelective = igPrefs.getBoolean("ig_use_selective_blocking", true)
            if (useSelective) {
                Log.d("AppBlocker", "Instagram opened. Bypassing full block because selective blocking is enabled.")
                return
            }
        }
        if (packageName == "com.google.android.youtube") {
            val ytPrefs = context.getSharedPreferences("youtube_blocker_prefs", Context.MODE_PRIVATE)
            val useSelective = ytPrefs.getBoolean("yt_use_selective_blocking", true)
            if (useSelective) {
                Log.d("AppBlocker", "YouTube opened. Bypassing full block because selective blocking is enabled.")
                return
            }
        }
        if (packageName == "com.snapchat.android") {
            val snapPrefs = context.getSharedPreferences("snapchat_blocker_prefs", Context.MODE_PRIVATE)
            val useSelective = snapPrefs.getBoolean("snap_use_selective_blocking", true)
            if (useSelective) {
                Log.d("AppBlocker", "Snapchat opened. Bypassing full block because selective blocking is enabled.")
                return
            }
        }

        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        val strictEnabled = strictPrefs.getBoolean("strict_mode_enabled", false)

        val isBlocked = if (strictEnabled) {
            isPackageBlockedInStrictMode(context, packageName)
        } else {
            val blockedApps = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
            blockedApps.contains(packageName)
        }

        if (isBlocked) {
            Log.w("AppBlocker", "Intercepted blocked app via Accessibility: $packageName")
            
            val blockIntent = Intent(context, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                putExtra("INTERCEPTED_PACKAGE", packageName)
                putExtra("IS_LIMIT_BLOCK", false)
                putExtra("IS_STRICT_MODE_INTERCEPT", strictEnabled)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(blockIntent)
        }
    }

    fun checkForegroundAppAndBlockIfNeeded(context: Context) {
        if (!isFocusGuardEnabled(context)) return
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        if (!isFocusing) return

        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        val strictEnabled = strictPrefs.getBoolean("strict_mode_enabled", false)
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 2000, time)
        
        var currentForegroundApp: String? = null
        val event = android.app.usage.UsageEvents.Event()
        
        while (usageEvents != null && usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundApp = event.packageName
            }
        }

        if (currentForegroundApp != null) {
            if (currentForegroundApp == "com.instagram.android") {
                val igPrefs = context.getSharedPreferences("instagram_blocker_prefs", Context.MODE_PRIVATE)
                val useSelective = igPrefs.getBoolean("ig_use_selective_blocking", true)
                if (useSelective) {
                    Log.d("AppBlocker", "Instagram in foreground. Bypassing full block because selective blocking is enabled.")
                    return
                }
            }
            if (currentForegroundApp == "com.google.android.youtube") {
                val ytPrefs = context.getSharedPreferences("youtube_blocker_prefs", Context.MODE_PRIVATE)
                val useSelective = ytPrefs.getBoolean("yt_use_selective_blocking", true)
                if (useSelective) {
                    Log.d("AppBlocker", "YouTube in foreground. Bypassing full block because selective blocking is enabled.")
                    return
                }
            }
            if (currentForegroundApp == "com.snapchat.android") {
                val snapPrefs = context.getSharedPreferences("snapchat_blocker_prefs", Context.MODE_PRIVATE)
                val useSelective = snapPrefs.getBoolean("snap_use_selective_blocking", true)
                if (useSelective) {
                    Log.d("AppBlocker", "Snapchat in foreground. Bypassing full block because selective blocking is enabled.")
                    return
                }
            }

            val isBlocked = if (strictEnabled) {
                isPackageBlockedInStrictMode(context, currentForegroundApp)
            } else {
                val blockedApps = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                blockedApps.contains(currentForegroundApp)
            }

            if (isBlocked) {
                Log.w("AppBlocker", "Intercepted blocked app: $currentForegroundApp")
                
                val blockIntent = Intent(context, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                    putExtra("INTERCEPTED_PACKAGE", currentForegroundApp)
                    putExtra("IS_LIMIT_BLOCK", false)
                    putExtra("IS_STRICT_MODE_INTERCEPT", strictEnabled)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(blockIntent)
            }
        }
    }

    data class AppInfo(val packageName: String, val label: String)

    /**
     * Dynamically queries all user-launchable apps installed on the device.
     * This ensures any newly installed apps are instantly captured.
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val appList = mutableListOf<AppInfo>()
        val uniquePackages = mutableSetOf<String>()
        
        for (ri in resolveInfos) {
            val pkgName = ri.activityInfo.packageName
            if (pkgName == context.packageName) continue
            if (uniquePackages.add(pkgName)) {
                val label = ri.loadLabel(pm).toString()
                appList.add(AppInfo(packageName = pkgName, label = label))
            }
        }
        return appList.sortedBy { it.label.lowercase() }
    }

    /**
     * Saves a notification that was blocked during Focus Mode.
     */
    fun saveBlockedNotification(context: Context, packageName: String, title: String, text: String) {
        val prefs = context.getSharedPreferences("blocked_notifications_prefs", Context.MODE_PRIVATE)
        val listStr = prefs.getString("blocked_list", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(listStr)
            val jsonObject = JSONObject().apply {
                put("packageName", packageName)
                put("title", title)
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            }
            jsonArray.put(jsonObject)
            prefs.edit().putString("blocked_list", jsonArray.toString()).apply()
            Log.d("AppBlockHelper", "Saved blocked notification for $packageName: $title")
        } catch (e: Exception) {
            Log.e("AppBlockHelper", "Error saving blocked notification: ${e.message}")
        }
    }

    /**
     * Releases (re-posts) all blocked notifications.
     */
    fun releaseBlockedNotifications(context: Context) {
        val prefs = context.getSharedPreferences("blocked_notifications_prefs", Context.MODE_PRIVATE)
        val listStr = prefs.getString("blocked_list", "[]") ?: "[]"
        if (listStr == "[]") return

        try {
            val jsonArray = JSONArray(listStr)
            val totalCount = jsonArray.length()
            if (totalCount == 0) return

            Log.d("AppBlockHelper", "Releasing $totalCount blocked notifications!")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Ensure notification channel exists
            val channelId = "released_notifications_channel"
            val channelName = "Released Notifications"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications that were delayed during Focus Mode"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val pm = context.packageManager

            if (totalCount == 1) {
                val obj = jsonArray.getJSONObject(0)
                val pkg = obj.optString("packageName", "")
                val title = obj.optString("title", "Notification")
                val text = obj.optString("text", "")
                
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.')
                }

                // Create notification with the original content
                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSubText("Delayed: $appLabel")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                // Notify with unique ID
                val notificationId = (System.currentTimeMillis() % 1000000).toInt()
                notificationManager.notify(notificationId, builder.build())
            } else {
                // Bundle them into a single summary "InboxStyle" notification
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("$totalCount Missed Notifications")
                    .setSummaryText("Focus Guard Digest")

                for (i in 0 until totalCount) {
                    val obj = jsonArray.getJSONObject(i)
                    val pkg = obj.optString("packageName", "")
                    val title = obj.optString("title", "Notification")
                    val text = obj.optString("text", "")
                    
                    val appLabel = try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg.substringAfterLast('.')
                    }

                    inboxStyle.addLine("$appLabel: $title - $text")
                }

                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                    .setContentTitle("$totalCount Missed Notifications")
                    .setContentText("Expand to see what you missed during the focus block.")
                    .setStyle(inboxStyle)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                val notificationId = (System.currentTimeMillis() % 1000000).toInt()
                notificationManager.notify(notificationId, builder.build())
            }

            // Clear the list
            prefs.edit().putString("blocked_list", "[]").apply()
        } catch (e: Exception) {
            Log.e("AppBlockHelper", "Error releasing blocked notifications: ${e.message}")
        }
    }

    fun getInstagramPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("instagram_blocker_prefs", Context.MODE_PRIVATE)
    }

    fun isIgSelectiveBlockingEnabled(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgUseSelective
    }

    fun setIgSelectiveBlockingEnabled(context: Context, enabled: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_use_selective_blocking", enabled).apply()
        cachedIgUseSelective = enabled
    }

    fun isIgReelsBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgReelsBlocked
    }

    fun setIgReelsBlocked(context: Context, blocked: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_reels_blocked", blocked).apply()
        cachedIgReelsBlocked = blocked
    }

    fun isIgStoriesBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgStoriesBlocked
    }

    fun setIgStoriesBlocked(context: Context, blocked: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_stories_blocked", blocked).apply()
        cachedIgStoriesBlocked = blocked
    }

    fun isIgExploreBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgExploreBlocked
    }

    fun setIgExploreBlocked(context: Context, blocked: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_explore_blocked", blocked).apply()
        cachedIgExploreBlocked = blocked
    }

    fun isIgAllowSharedReels(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgAllowSharedReels
    }

    fun setIgAllowSharedReels(context: Context, allowed: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_allow_shared_reels", allowed).apply()
        cachedIgAllowSharedReels = allowed
    }

    fun isIgFeedScrollLimit(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgFeedScrollLimit
    }

    fun setIgFeedScrollLimit(context: Context, limited: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_feed_scroll_limit", limited).apply()
        cachedIgFeedScrollLimit = limited
    }

    fun isIgReelsMuteAudio(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgReelsMuteAudio
    }

    fun setIgReelsMuteAudio(context: Context, mute: Boolean) {
        getInstagramPrefs(context).edit().putBoolean("ig_reels_mute_audio", mute).apply()
        cachedIgReelsMuteAudio = mute
    }

    fun getIgReelsLimitMinutes(context: Context): Int {
        if (!isCacheInitialized) initializeCache(context)
        return cachedIgReelsLimitMinutes
    }

    fun setIgReelsLimitMinutes(context: Context, minutes: Int) {
        getInstagramPrefs(context).edit().putInt("ig_reels_limit_minutes", minutes).apply()
        cachedIgReelsLimitMinutes = minutes
    }

    // YouTube Advanced Blocker Helpers
    fun getYoutubePrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("youtube_blocker_prefs", Context.MODE_PRIVATE)
    }

    fun isYtSelectiveBlockingEnabled(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtUseSelective
    }

    fun setYtSelectiveBlockingEnabled(context: Context, enabled: Boolean) {
        getYoutubePrefs(context).edit().putBoolean("yt_use_selective_blocking", enabled).apply()
        cachedYtUseSelective = enabled
    }

    fun isYtShortsBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtShortsBlocked
    }

    fun setYtShortsBlocked(context: Context, blocked: Boolean) {
        getYoutubePrefs(context).edit().putBoolean("yt_shorts_blocked", blocked).apply()
        cachedYtShortsBlocked = blocked
    }

    fun isYtSearchBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtSearchBlocked
    }

    fun setYtSearchBlocked(context: Context, blocked: Boolean) {
        getYoutubePrefs(context).edit().putBoolean("yt_search_blocked", blocked).apply()
        cachedYtSearchBlocked = blocked
    }

    fun isYtCommentsBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtCommentsBlocked
    }

    fun setYtCommentsBlocked(context: Context, blocked: Boolean) {
        getYoutubePrefs(context).edit().putBoolean("yt_comments_blocked", blocked).apply()
        cachedYtCommentsBlocked = blocked
    }

    fun isYtOnlyAllowApprovedChannels(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtOnlyAllowApprovedChannels
    }

    fun setYtOnlyAllowApprovedChannels(context: Context, enabled: Boolean) {
        getYoutubePrefs(context).edit().putBoolean("yt_only_allow_approved_channels", enabled).apply()
        cachedYtOnlyAllowApprovedChannels = enabled
    }

    fun getYtApprovedChannels(context: Context): String {
        if (!isCacheInitialized) initializeCache(context)
        return cachedYtApprovedChannels
    }

    fun setYtApprovedChannels(context: Context, channels: String) {
        getYoutubePrefs(context).edit().putString("yt_approved_channels", channels).apply()
        cachedYtApprovedChannels = channels
    }

    // Snapchat Advanced Blocker Helpers
    fun getSnapchatPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("snapchat_blocker_prefs", Context.MODE_PRIVATE)
    }

    fun isSnapSelectiveBlockingEnabled(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedSnapUseSelective
    }

    fun setSnapSelectiveBlockingEnabled(context: Context, enabled: Boolean) {
        getSnapchatPrefs(context).edit().putBoolean("snap_use_selective_blocking", enabled).apply()
        cachedSnapUseSelective = enabled
    }

    fun isSnapSpotlightBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedSnapSpotlightBlocked
    }

    fun setSnapSpotlightBlocked(context: Context, blocked: Boolean) {
        getSnapchatPrefs(context).edit().putBoolean("snap_spotlight_blocked", blocked).apply()
        cachedSnapSpotlightBlocked = blocked
    }

    fun isSnapMapBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedSnapMapBlocked
    }

    fun setSnapMapBlocked(context: Context, blocked: Boolean) {
        getSnapchatPrefs(context).edit().putBoolean("snap_map_blocked", blocked).apply()
        cachedSnapMapBlocked = blocked
    }

    fun isSnapDiscoverBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedSnapDiscoverBlocked
    }

    fun setSnapDiscoverBlocked(context: Context, blocked: Boolean) {
        getSnapchatPrefs(context).edit().putBoolean("snap_discover_blocked", blocked).apply()
        cachedSnapDiscoverBlocked = blocked
    }

    // Facebook Advanced Blocker Helpers
    fun getFacebookPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("facebook_blocker_prefs", Context.MODE_PRIVATE)
    }

    fun isFbSelectiveBlockingEnabled(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedFbUseSelective
    }

    fun setFbSelectiveBlockingEnabled(context: Context, enabled: Boolean) {
        getFacebookPrefs(context).edit().putBoolean("fb_use_selective_blocking", enabled).apply()
        cachedFbUseSelective = enabled
    }

    fun isFbReelsBlocked(context: Context): Boolean {
        return getFacebookPrefs(context).getBoolean("fb_reels_blocked", true)
    }

    fun setFbReelsBlocked(context: Context, blocked: Boolean) {
        getFacebookPrefs(context).edit().putBoolean("fb_reels_blocked", blocked).apply()
    }

    fun isFbWatchBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedFbWatchBlocked
    }

    fun setFbWatchBlocked(context: Context, blocked: Boolean) {
        getFacebookPrefs(context).edit().putBoolean("fb_watch_blocked", blocked).apply()
        cachedFbWatchBlocked = blocked
    }

    fun isFbStoriesBlocked(context: Context): Boolean {
        if (!isCacheInitialized) initializeCache(context)
        return cachedFbStoriesBlocked
    }

    fun setFbStoriesBlocked(context: Context, blocked: Boolean) {
        getFacebookPrefs(context).edit().putBoolean("fb_stories_blocked", blocked).apply()
        cachedFbStoriesBlocked = blocked
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = android.content.ComponentName(context, "com.example.service.AppBlockAccessibilityService")
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && (enabledService.packageName == context.packageName || enabledService == expectedComponentName)) {
                return true
            }
        }
        return false
    }
}
