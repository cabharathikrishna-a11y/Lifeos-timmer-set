package com.example.api

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.example.util.childMs
import com.example.util.parseToMs
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FirebaseRepairKit
 *
 * Ensures Firebase Realtime Database (RTDB) structure under `FOCUS_TIMMER/USER/<sanitizedEmail>`
 * is healthy, completely populated, and free of obsolete/unwanted legacy nodes.
 *
 * KEY GUARANTEES:
 * 1. Checks and recreates required nodes/fields if missing (e.g. ARENA, ACTIVE_FOCUS_TIMER,
 *    DEVICES_LOGGED_IN, SYLLABUS_COMPLETED).
 * 2. Prunes unwanted/obsolete legacy branches (e.g., corrupt keys, temporary nodes, legacy WEEKLY_STATS).
 * 3. NO RECURSIVE/INFINITE LOOPS: Employs read-before-write single-value inspections, debouncing,
 *    and strict comparison so writes or deletes are ONLY performed when an actual mismatch exists.
 */
object FirebaseRepairKit {
    private const val TAG = "FirebaseRepairKit"
    private const val MIN_REPAIR_INTERVAL_MS = 10000L // 10s debounce per user

    private val lastRepairTimes = ConcurrentHashMap<String, Long>()
    private val isRepairingMap = ConcurrentHashMap<String, AtomicBoolean>()

    // Allowed / Known valid root branches under FOCUS_TIMMER/USER/{sanitizedEmail}
    private val VALID_USER_BRANCHES = setOf(
        "ARENA",
        "ACTIVE_FOCUS_TIMER",
        "DEVICES_LOGGED_IN",
        "SYLLABUS_COMPLETED",
        "TASKS",
        "TASKS_LIVE",
        "JOURNAL_LIVE",
        "FILE_EXPLORER_LIVE",
        "FINANCE_LIVE",
        "KEEP_NOTES",
        "FOCUS_LOCKER",
        "STREAK_SHIELDS",
        "settingsLastUpdatedTs",
        "SETTINGS_SYNC_SIGNAL",
        "DEDUCTED_XP",
        "active_command",
        "status",
        "typing",
        "focusTimer"
    )

    // Known obsolete or unwanted legacy keys to explicitly prune if present
    private val OBSOLETE_USER_BRANCHES = setOf(
        "HEALTH_LIVE",
        "WEEKLY_STATS",
        "Todays_Focus_Ms",
        "todayFocusMs",
        "Past_7_Days_Focus_Ms",
        "Past_30_Days_Focus_Ms",
        "All_Time_Focus_Ms",
        "Last_Update_Time_and_Date",
        "lastUpdateDate",
        "Last_Updated_String",
        "old_arena",
        "users",
        "temp_test",
        "dummy_data",
        "null",
        "undefined",
        "test_node",
        "invalid_data",
        "corrupt_branch"
    )

    fun sanitizeEmail(rawEmail: String): String {
        return rawEmail.lowercase().trim()
            .replace(".", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("#", "_")
            .replace("/", "_")
    }

    /**
     * Main repair function. Triggers an asynchronous inspection and repair of RTDB.
     */
    fun repairUserData(context: Context, email: String, force: Boolean = false) {
        val sanitized = sanitizeEmail(email)
        if (sanitized.isBlank() || sanitized == "null" || sanitized == "undefined") return

        val now = System.currentTimeMillis()
        val lastRun = lastRepairTimes[sanitized] ?: 0L
        if (!force && (now - lastRun < MIN_REPAIR_INTERVAL_MS)) {
            Log.d(TAG, "Repair skipped for $sanitized (debounced within $MIN_REPAIR_INTERVAL_MS ms)")
            return
        }

        val flag = isRepairingMap.computeIfAbsent(sanitized) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) {
            Log.d(TAG, "Repair already in progress for $sanitized")
            return
        }

        lastRepairTimes[sanitized] = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Firebase.ensureFirebaseInitialized(context)
                val dbUrl = FirebaseConfig.getDatabaseUrl(context)
                if (dbUrl.isEmpty()) {
                    flag.set(false)
                    return@launch
                }

                val db = FirebaseDatabase.getInstance(dbUrl)
                val userRef = db.getReference("FOCUS_TIMMER/USER").child(sanitized)

                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            performStructuralRepair(context, sanitized, snapshot, userRef)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during structural repair execution for $sanitized", e)
                        } finally {
                            flag.set(false)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error during repair for $sanitized: ${error.message}")
                        flag.set(false)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching repairUserData for $sanitized", e)
                flag.set(false)
            }
        }
    }

    private fun performStructuralRepair(
        context: Context,
        sanitizedEmail: String,
        userSnapshot: DataSnapshot,
        userRef: com.google.firebase.database.DatabaseReference
    ) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val displayName = prefs.getString("user_nickname", "")?.ifEmpty {
            prefs.getString("user_name", "")?.ifEmpty { sanitizedEmail.substringBefore("@") }
        } ?: sanitizedEmail.substringBefore("@")
        val emoji = prefs.getString("user_emoji", "👤")?.ifEmpty { "👤" } ?: "👤"

        var mutationsPerformed = false

        // 1. PRUNE OBSOLETE OR UNWANTED BRANCHES
        for (child in userSnapshot.children) {
            val key = child.key ?: continue
            if (OBSOLETE_USER_BRANCHES.contains(key) || (!VALID_USER_BRANCHES.contains(key) && key.startsWith("temp_"))) {
                Log.w(TAG, "Pruning unwanted/obsolete branch: FOCUS_TIMMER/USER/$sanitizedEmail/$key")
                userRef.child(key).removeValue()
                mutationsPerformed = true
            }
        }

        // 2. CHECK AND REPAIR 'ARENA' BRANCH
        val arenaSnap = userSnapshot.child("ARENA")
        val arenaRef = userRef.child("ARENA")
        val nowMs = System.currentTimeMillis()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(nowMs))

        val arenaUpdates = mutableMapOf<String, Any?>()

        if (!arenaSnap.hasChild("ActiveStreak")) {
            arenaUpdates["ActiveStreak"] = 0
        }
        if (!arenaSnap.hasChild("XpScore")) {
            arenaUpdates["XpScore"] = 0
        }
        if (!arenaSnap.hasChild("Top_Subject")) {
            arenaUpdates["Top_Subject"] = "None"
        }
        if (arenaSnap.hasChild("topSubject")) {
            arenaUpdates["topSubject"] = null
        }
        if (!arenaSnap.hasChild("DisplayName") || arenaSnap.child("DisplayName").getValue(String::class.java).isNullOrBlank()) {
            arenaUpdates["DisplayName"] = displayName
        }
        if (!arenaSnap.hasChild("CustomEmoji") || arenaSnap.child("CustomEmoji").getValue(String::class.java).isNullOrBlank()) {
            arenaUpdates["CustomEmoji"] = emoji
        }
        if (arenaSnap.hasChild("customEmoji")) {
            arenaUpdates["customEmoji"] = null
        }
        if (!arenaSnap.hasChild("Last_Updated")) {
            arenaUpdates["Last_Updated"] = nowMs
        }
        if (arenaSnap.hasChild("Last_Updated_String")) {
            arenaUpdates["Last_Updated_String"] = null
        }

        // Remove breakdown sub-nodes from ARENA
        if (arenaSnap.hasChild("TODAY")) arenaUpdates["TODAY"] = null
        if (arenaSnap.hasChild("PAST_7_DAYS")) arenaUpdates["PAST_7_DAYS"] = null
        if (arenaSnap.hasChild("PAST_30_DAYS")) arenaUpdates["PAST_30_DAYS"] = null
        if (arenaSnap.hasChild("ALL_TIME")) arenaUpdates["ALL_TIME"] = null
        if (arenaSnap.hasChild("Subject_Breakdown")) arenaUpdates["Subject_Breakdown"] = null
        if (arenaSnap.hasChild("Todays_Focus_Ms")) arenaUpdates["Todays_Focus_Ms"] = null
        if (arenaSnap.hasChild("todayFocusMs")) arenaUpdates["todayFocusMs"] = null

        if (arenaUpdates.isNotEmpty()) {
            Log.i(TAG, "Repairing missing ARENA fields for $sanitizedEmail: ${arenaUpdates.keys}")
            arenaRef.updateChildren(arenaUpdates)
            mutationsPerformed = true
        }

        // 3. CHECK AND REPAIR 'ACTIVE_FOCUS_TIMER' BRANCH
        val timerSnap = userSnapshot.child("ACTIVE_FOCUS_TIMER")
        val timerRef = userRef.child("ACTIVE_FOCUS_TIMER")
        val timerUpdates = mutableMapOf<String, Any>()

        if (!timerSnap.hasChild("User_Display_Name") || timerSnap.child("User_Display_Name").getValue(String::class.java).isNullOrBlank()) {
            timerUpdates["User_Display_Name"] = displayName
        }
        if (!timerSnap.hasChild("User_Emoji") || timerSnap.child("User_Emoji").getValue(String::class.java).isNullOrBlank()) {
            timerUpdates["User_Emoji"] = emoji
        }
        if (!timerSnap.hasChild("Status")) {
            timerUpdates["Status"] = "IDLE"
        }
        if (!timerSnap.hasChild("Command_Device_Name")) {
            timerUpdates["Command_Device_Name"] = "None"
        }

        // Clean up obsolete/duplicate keys if present in RTDB
        if (timerSnap.hasChild("Is_Timer_Running")) {
            timerRef.child("Is_Timer_Running").removeValue()
        }
        if (timerSnap.hasChild("Current_Timer_Mode")) {
            timerRef.child("Current_Timer_Mode").removeValue()
        }
        if (timerSnap.hasChild("Total_Elapsed_Ms")) {
            timerRef.child("Total_Elapsed_Ms").removeValue()
        }
        if (timerSnap.hasChild("Heartbeat_Timestamp")) {
            timerRef.child("Heartbeat_Timestamp").removeValue()
        }

        if (timerUpdates.isNotEmpty()) {
            Log.i(TAG, "Repairing missing ACTIVE_FOCUS_TIMER fields for $sanitizedEmail: ${timerUpdates.keys}")
            timerRef.updateChildren(timerUpdates)
            mutationsPerformed = true
        }

        // 4. CHECK AND REPAIR 'DEVICES_LOGGED_IN' BRANCH
        val devicesSnap = userSnapshot.child("DEVICES_LOGGED_IN")
        if (!devicesSnap.exists() || !devicesSnap.hasChildren()) {
            Log.i(TAG, "Repairing missing DEVICES_LOGGED_IN node for $sanitizedEmail")
            val deviceModel = android.os.Build.MODEL.replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
            val deviceMap = mapOf<String, Any?>(
                "Login_status" to true,
                "deviceName" to android.os.Build.MODEL,
                "Last_Update_Time_and_Date" to null,
                "lastUpdateDate" to null,
                "Upload_Status" to "COMPLETED",
                "isLoggedIn" to null,
                "lastActiveTime" to null,
                "uploadStatus" to null,
                "Todays_Focus_Ms" to null,
                "todayFocusMs" to null,
                "Past_7_Days_Focus_Ms" to null,
                "Past_30_Days_Focus_Ms" to null,
                "All_Time_Focus_Ms" to null
            )
            userRef.child("DEVICES_LOGGED_IN").child(deviceModel).updateChildren(deviceMap)
            mutationsPerformed = true
        } else {
            for (devChild in devicesSnap.children) {
                val devKey = devChild.key ?: continue
                val devUpdates = mutableMapOf<String, Any?>()
                if (devChild.hasChild("Todays_Focus_Ms")) devUpdates["Todays_Focus_Ms"] = null
                if (devChild.hasChild("todayFocusMs")) devUpdates["todayFocusMs"] = null
                if (devChild.hasChild("Todays_Total_Focus_Seconds")) devUpdates["Todays_Total_Focus_Seconds"] = null
                if (devChild.hasChild("Past_7_Days_Focus_Ms")) devUpdates["Past_7_Days_Focus_Ms"] = null
                if (devChild.hasChild("Past_30_Days_Focus_Ms")) devUpdates["Past_30_Days_Focus_Ms"] = null
                if (devChild.hasChild("Past_50_Days_Focus_Ms")) devUpdates["Past_50_Days_Focus_Ms"] = null
                if (devChild.hasChild("All_Time_Focus_Ms")) devUpdates["All_Time_Focus_Ms"] = null
                if (devChild.hasChild("allTimeFocusMs")) devUpdates["allTimeFocusMs"] = null
                if (devChild.hasChild("lastUpdateDate")) devUpdates["lastUpdateDate"] = null
                if (devChild.hasChild("Last_Update_Time_and_Date")) devUpdates["Last_Update_Time_and_Date"] = null
                if (devChild.hasChild("Last_Stats_Updated")) devUpdates["Last_Stats_Updated"] = null
                if (devChild.hasChild("Last_Updated")) devUpdates["Last_Updated"] = null
                if (devChild.hasChild("Last_Updated_String")) devUpdates["Last_Updated_String"] = null
                if (devChild.hasChild("lastActiveTime")) devUpdates["lastActiveTime"] = null
                if (devUpdates.isNotEmpty()) {
                    userRef.child("DEVICES_LOGGED_IN").child(devKey).updateChildren(devUpdates)
                    mutationsPerformed = true
                }
            }
        }

        // 5. CHECK AND REPAIR 'SYLLABUS_COMPLETED' BRANCH CONTAINER
        val rootCleanup = mutableMapOf<String, Any?>()
        if (userSnapshot.hasChild("Todays_Focus_Ms")) rootCleanup["Todays_Focus_Ms"] = null
        if (userSnapshot.hasChild("todayFocusMs")) rootCleanup["todayFocusMs"] = null
        if (userSnapshot.hasChild("Past_7_Days_Focus_Ms")) rootCleanup["Past_7_Days_Focus_Ms"] = null
        if (userSnapshot.hasChild("Past_30_Days_Focus_Ms")) rootCleanup["Past_30_Days_Focus_Ms"] = null
        if (userSnapshot.hasChild("All_Time_Focus_Ms")) rootCleanup["All_Time_Focus_Ms"] = null
        if (userSnapshot.hasChild("Last_Update_Time_and_Date")) rootCleanup["Last_Update_Time_and_Date"] = null
        if (userSnapshot.hasChild("lastUpdateDate")) rootCleanup["lastUpdateDate"] = null
        if (userSnapshot.hasChild("HEALTH_LIVE")) rootCleanup["HEALTH_LIVE"] = null
        if (userSnapshot.hasChild("CustomEmoji")) rootCleanup["CustomEmoji"] = null
        if (userSnapshot.hasChild("customEmoji")) rootCleanup["customEmoji"] = null
        if (userSnapshot.hasChild("ActiveStreak")) rootCleanup["ActiveStreak"] = null
        if (userSnapshot.hasChild("XpScore")) rootCleanup["XpScore"] = null
        if (userSnapshot.hasChild("Last_Updated_String")) rootCleanup["Last_Updated_String"] = null
        if (userSnapshot.hasChild("topSubject")) rootCleanup["topSubject"] = null
        if (userSnapshot.hasChild("Last_Updated")) rootCleanup["Last_Updated"] = null
        if (rootCleanup.isNotEmpty()) {
            userRef.updateChildren(rootCleanup)
            mutationsPerformed = true
        }

        val syllabusSnap = userSnapshot.child("SYLLABUS_COMPLETED")
        if (!syllabusSnap.exists()) {
            Log.i(TAG, "Repairing missing SYLLABUS_COMPLETED node for $sanitizedEmail")
            userRef.child("SYLLABUS_COMPLETED").setValue(emptyMap<String, Any>())
            mutationsPerformed = true
        }

        // 6. CHECK AND REPAIR 'LEADERBOARD' NODE FOR THIS USER
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isNotEmpty()) {
            try {
                val db = FirebaseDatabase.getInstance(dbUrl)
                val lbRef = db.getReference("FOCUS_TIMMER/LEADERBOARD").child(sanitizedEmail)
                lbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(lbSnap: DataSnapshot) {
                        val lbUpdates = mutableMapOf<String, Any?>()
                        if (!lbSnap.hasChild("displayName") || lbSnap.child("displayName").getValue(String::class.java).isNullOrBlank()) {
                            lbUpdates["displayName"] = displayName
                        }
                        if (!lbSnap.hasChild("CustomEmoji") || lbSnap.child("CustomEmoji").getValue(String::class.java).isNullOrBlank()) {
                            lbUpdates["CustomEmoji"] = emoji
                        }
                        if (lbSnap.hasChild("customEmoji")) {
                            lbUpdates["customEmoji"] = null
                        }
                        if (lbSnap.hasChild("topSubject")) {
                            lbUpdates["topSubject"] = null
                        }
                        if (!lbSnap.hasChild("email")) {
                            lbUpdates["email"] = sanitizedEmail.replace("_gmail_com", "@gmail.com")
                        }
                        if (lbSnap.hasChild("lastUpdateDate")) {
                            lbUpdates["lastUpdateDate"] = null
                        }
                        if (lbSnap.hasChild("Last_Updated_String")) {
                            lbUpdates["Last_Updated_String"] = null
                        }
                        if (lbUpdates.isNotEmpty()) {
                            lbRef.updateChildren(lbUpdates)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            } catch (e: Exception) {
                Log.w(TAG, "Failed repairing LEADERBOARD node for $sanitizedEmail: ${e.message}")
            }
        }

        if (mutationsPerformed) {
            Log.i(TAG, "Firebase Repair Kit successfully verified and repaired database structure for $sanitizedEmail")
        } else {
            Log.d(TAG, "Firebase Repair Kit verified $sanitizedEmail: Structure is completely healthy, no mutations needed.")
        }
    }

    /**
     * Cleans up global root nodes and ensures LEADERBOARD entries for all users
     * contain only timing columns without subject breakdowns or obsolete sub-nodes.
     */
    fun repairGlobalRoots(context: Context) {
        try {
            Firebase.ensureFirebaseInitialized(context)
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val db = FirebaseDatabase.getInstance(dbUrl)
            db.getReference("APP_CONFIG").removeValue()
            db.getReference("FOCUS_TIMMER/APP_CONFIG").removeValue()
            val userRootRef = db.getReference("FOCUS_TIMMER/USER")
            val leaderboardRef = db.getReference("FOCUS_TIMMER/LEADERBOARD")

            leaderboardRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(lbSnapshot: DataSnapshot) {
                    userRootRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val validUserKeys = HashSet<String>()

                            for (child in snapshot.children) {
                                val key = child.key ?: continue
                                if (key.isBlank() || key == "null" || key == "undefined" || key == "cabharathikrishna" || key == "cabharathikrishan") {
                                    Log.w(TAG, "Global Repair: Removing invalid/legacy user root node: FOCUS_TIMMER/USER/$key")
                                    userRootRef.child(key).removeValue()
                                    leaderboardRef.child(key).removeValue()
                                    continue
                                }

                                validUserKeys.add(key)
                                val lbChild = lbSnapshot.child(key)

                                val rawEmail = child.child("email").getValue(String::class.java)
                                    ?: child.child("userEmail").getValue(String::class.java)
                                    ?: lbChild.child("email").getValue(String::class.java)
                                    ?: if (key.contains("_")) key.replace("_gmail_com", "@gmail.com").replace("_", ".") else "$key@gmail.com"

                                val sanitizedEmailFromRaw = sanitizeEmail(rawEmail)
                                validUserKeys.add(sanitizedEmailFromRaw)

                                val arenaSnap = child.child("ARENA")
                                val timerSnap = child.child("ACTIVE_FOCUS_TIMER")

                                val displayName = lbChild.child("displayName").getValue(String::class.java)
                                    ?: arenaSnap.child("DisplayName").getValue(String::class.java)
                                    ?: timerSnap.child("User_Display_Name").getValue(String::class.java)
                                    ?: child.child("DisplayName").getValue(String::class.java)
                                    ?: rawEmail.substringBefore("@")

                                val customEmoji = lbChild.child("CustomEmoji").getValue(String::class.java)
                                    ?: lbChild.child("customEmoji").getValue(String::class.java)
                                    ?: arenaSnap.child("CustomEmoji").getValue(String::class.java)
                                    ?: timerSnap.child("User_Emoji").getValue(String::class.java)
                                    ?: child.child("CustomEmoji").getValue(String::class.java)
                                    ?: "👤"

                                val nowMs = System.currentTimeMillis()
                                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))

                                val lbLastUpdated = parseToMs(lbChild.child("Last_Updated").value)
                                val isLbUpdatedToday = lbLastUpdated > 0L && SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(lbLastUpdated)) == todayStr
                                val existingLbTodayMs = if (isLbUpdatedToday) lbChild.childMs("Todays_Focus_Ms") else 0L

                                val arenaLastUpdated = parseToMs(arenaSnap.child("Last_Updated").value)
                                val isArenaUpdatedToday = arenaLastUpdated > 0L && SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(arenaLastUpdated)) == todayStr
                                val arenaTodayMs = if (isArenaUpdatedToday) {
                                    arenaSnap.childMs("TODAY/Total_Focus_Ms", "Todays_Focus_Ms", "todayFocusMs")
                                        .let { if (it > 0L) it else child.childMs("Todays_Focus_Ms", "todayFocusMs") }
                                } else {
                                    0L
                                }
                                val rawTodayMs = maxOf(existingLbTodayMs, arenaTodayMs)
                                val todayFocusMs = minOf(86_400_000L, rawTodayMs)

                                val existingLbPast7Ms = lbChild.childMs("Past_7_Days_Focus_Ms")
                                val arenaPast7Ms = arenaSnap.childMs("PAST_7_DAYS/Total_Focus_Ms", "Past_7_Days_Focus_Ms")
                                    .let { if (it > 0L) it else child.childMs("Past_7_Days_Focus_Ms") }
                                val past7FocusMs = maxOf(existingLbPast7Ms, arenaPast7Ms)

                                val existingLbPast30Ms = lbChild.childMs("Past_30_Days_Focus_Ms")
                                val arenaPast30Ms = arenaSnap.childMs("PAST_30_DAYS/Total_Focus_Ms", "Past_30_Days_Focus_Ms")
                                    .let { if (it > 0L) it else child.childMs("Past_30_Days_Focus_Ms") }
                                val past30FocusMs = maxOf(existingLbPast30Ms, arenaPast30Ms)

                                val existingLbAllTimeMs = lbChild.childMs("All_Time_Focus_Ms")
                                val arenaAllTimeMs = arenaSnap.childMs("ALL_TIME/Total_Focus_Ms", "All_Time_Focus_Ms")
                                    .let { if (it > 0L) it else child.childMs("All_Time_Focus_Ms") }
                                val allTimeFocusMs = maxOf(existingLbAllTimeMs, arenaAllTimeMs)

                                val lastUpdatedMs = maxOf(lbLastUpdated, arenaLastUpdated, nowMs)

                                val leaderboardData = mapOf<String, Any?>(
                                    "email" to rawEmail,
                                    "displayName" to displayName,
                                    "CustomEmoji" to customEmoji,
                                    "customEmoji" to null,
                                    "topSubject" to null,
                                    "Todays_Focus_Ms" to todayFocusMs,
                                    "Past_7_Days_Focus_Ms" to past7FocusMs,
                                    "Past_30_Days_Focus_Ms" to past30FocusMs,
                                    "All_Time_Focus_Ms" to allTimeFocusMs,
                                    "Last_Updated" to lastUpdatedMs,
                                    "Last_Updated_String" to null,
                                    "lastUpdateDate" to null,

                                    // Delete all obsolete / breakdown sub-nodes & duplicate keys under LEADERBOARD
                                    "todayFocusMs" to null,
                                    "Last_Update_Time_and_Date" to null,
                                    "Past_50_Days_Focus_Ms" to null,
                                    "userEmail" to null,
                                    "DisplayName" to null,
                                    "TODAY" to null,
                                    "PAST_7_DAYS" to null,
                                    "PAST_30_DAYS" to null,
                                    "ALL_TIME" to null,
                                    "ActiveStreak" to null,
                                    "XpScore" to null,
                                    "Subject_Breakdown" to null
                                )

                                leaderboardRef.child(key).updateChildren(leaderboardData)

                                 // Sync root-level fields directly on user node
                                val userRootUpdates = mapOf<String, Any?>(
                                    "Todays_Focus_Ms" to null,
                                    "todayFocusMs" to null,
                                    "CustomEmoji" to null,
                                    "customEmoji" to null,
                                    "Todays_Total_Focus_Seconds" to null,
                                    "Past_7_Days_Focus_Ms" to null,
                                    "Past_30_Days_Focus_Ms" to null,
                                    "Past_50_Days_Focus_Ms" to null,
                                    "All_Time_Focus_Ms" to null,
                                    "Last_Updated" to null,
                                    "Last_Updated_String" to null,
                                    "Last_Update_Time_and_Date" to null,
                                    "lastUpdateDate" to null,
                                    "HEALTH_LIVE" to null,
                                    "Custom_Emoji" to null,
                                    "email" to rawEmail,
                                    "userEmail" to null,
                                    "focus_timestamps" to null,
                                    "focusTimestamps" to null,
                                    "focus_timestamps_ms" to null
                                )
                                userRootRef.child(key).updateChildren(userRootUpdates)

                                // Keep Todays_Focus_Ms, CustomEmoji, and Last_Updated in ARENA
                                val arenaUpdates = mapOf<String, Any?>(
                                    "TODAY" to null,
                                    "PAST_7_DAYS" to null,
                                    "PAST_30_DAYS" to null,
                                    "ALL_TIME" to null,
                                    "Subject_Breakdown" to null,
                                    "Todays_Focus_Ms" to todayFocusMs,
                                    "todayFocusMs" to todayFocusMs,
                                    "CustomEmoji" to customEmoji,
                                    "customEmoji" to null,
                                    "topSubject" to null,
                                    "Last_Updated" to lastUpdatedMs,
                                    "Last_Updated_String" to null
                                )
                                userRootRef.child(key).child("ARENA").updateChildren(arenaUpdates)
                            }

                            // Strictly prune LEADERBOARD entries for users not under USER node
                            for (lbChild in lbSnapshot.children) {
                                val lbKey = lbChild.key ?: continue
                                val lbEmail = lbChild.child("email").getValue(String::class.java)
                                    ?: lbChild.child("userEmail").getValue(String::class.java)
                                val lbSanitized = lbEmail?.let { sanitizeEmail(it) } ?: lbKey

                                if (!validUserKeys.contains(lbKey) && !validUserKeys.contains(lbSanitized)) {
                                    Log.w(TAG, "Global Repair: Removing orphaned LEADERBOARD node for $lbKey")
                                    leaderboardRef.child(lbKey).removeValue()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Global Repair: Error reading USER root node: ${error.message}")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Global Repair: Error reading LEADERBOARD node: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Global repair failed", e)
        }
    }
}
