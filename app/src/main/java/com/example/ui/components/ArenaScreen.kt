package com.example.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.*
import com.example.data.LocalHistoryVault
import com.example.data.LocalShieldsVault
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val sduiPrefs = com.example.api.RemoteConfigManager.sduiPreferences.collectAsStateWithLifecycle().value
    if (!sduiPrefs.isArenaEnabled) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F11)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Leaderboard Maintenance",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Leaderboard Under Maintenance",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The CA Inter Arena is currently down for scheduled synchronization optimizations. Please check back shortly!",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val email = viewModel.getActiveUserEmail()
    var leaderboardPeriod by remember { mutableStateOf("TODAY") } // "TODAY", "PAST_7_DAYS", "PAST_30_DAYS", "PAST_50_DAYS", "ALL_TIME"
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Sync shields and start leaderboard listeners
    LaunchedEffect(email, leaderboardPeriod) {
        if (email.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.api.WeeklyStatsUpdater.updateWeeklyStats(context, email, 0L, "")
            }
            ArenaLeaderboardEngine.startListening(context, email, leaderboardPeriod)
            StreakShieldManager.startRealtimeShieldSync(context, email)
            StreakShieldManager.fetchAndSyncShields(context, email)
        }
    }

    DisposableEffect(email) {
        onDispose {
            ArenaLeaderboardEngine.stopListening()
            StreakShieldManager.stopRealtimeShieldSync()
        }
    }

    val leaderboard by ArenaLeaderboardEngine.leaderboardFlow.collectAsStateWithLifecycle()
    val historyRecordsRaw by viewModel.allHistoryVault.collectAsStateWithLifecycle()
    
    val isTimerRunning by com.example.util.FocusTimerManager.isTimerRunning.collectAsStateWithLifecycle()
    val isStopwatchActive by com.example.util.FocusTimerManager.isStopwatchActive.collectAsStateWithLifecycle()
    val isFocusPhase by com.example.util.FocusTimerManager.isFocusPhase.collectAsStateWithLifecycle()
    val isPaused by com.example.util.FocusTimerManager.isPaused.collectAsStateWithLifecycle()
    val accumulatedSessionTimeMs by com.example.util.FocusTimerManager.accumulatedSessionTimeMs.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by com.example.util.FocusTimerManager.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val timerSecondsLeft by com.example.util.FocusTimerManager.timerSecondsLeft.collectAsStateWithLifecycle()
    val stopwatchSeconds by com.example.util.FocusTimerManager.stopwatchSeconds.collectAsStateWithLifecycle()
    val attachedTag by com.example.util.FocusTimerManager.attachedTag.collectAsStateWithLifecycle()

    val wasStartedFromStopwatch by com.example.util.FocusTimerManager.wasStartedFromStopwatch.collectAsStateWithLifecycle()

    val systemTodayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val activeSecs = remember(isTimerRunning, isStopwatchActive, isFocusPhase, isPaused, cumulativeSessionFocusSeconds, stopwatchSeconds, wasStartedFromStopwatch) {
        if (isFocusPhase && !isPaused) {
            if (wasStartedFromStopwatch) (stopwatchSeconds ?: 0) else (cumulativeSessionFocusSeconds ?: 0)
        } else {
            0
        }
    }
    val activeFocusMs = activeSecs * 1000L

    val historyRecords = remember(historyRecordsRaw, attachedTag, activeFocusMs) {
        if (activeFocusMs > 0L) {
            val tagToUse = attachedTag.ifBlank { "Study" }
            val now = System.currentTimeMillis()
            val dummyRecord = com.example.data.LocalHistoryVault(
                record_id = "temp_active_session",
                date_string = systemTodayStr,
                subject = tagToUse,
                task_title = "Active Study Session",
                start_time_ms = now,
                end_time_ms = now + activeFocusMs,
                total_focus_ms = activeFocusMs,
                duration_formatted = "",
                start_time_formatted = "",
                end_time_formatted = ""
            )
            historyRecordsRaw + dummyRecord
        } else {
            historyRecordsRaw
        }
    }

    val localShields by StreakShieldManager.getLocalShieldsFlow(context).collectAsStateWithLifecycle(initialValue = emptyList())

    val activeShields = remember(localShields) { localShields.filter { !it.is_consumed } }
    val consumedShields = remember(localShields) { localShields.filter { it.is_consumed } }

    val peerUiCards by viewModel.peerUiCards.collectAsStateWithLifecycle()
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userNickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userEmoji by viewModel.userEmoji.collectAsStateWithLifecycle()

    var masteryPeriod by remember { mutableStateOf("TODAY") } // "TODAY", "WEEKLY", "MONTHLY"
    var activeTabSelection by remember { mutableStateOf(0) } // 0 = Arena, 1 = Syllabus Tree

    var showShieldsBottomSheet by remember { mutableStateOf(false) }
    var showGiftConfirmDialog by remember { mutableStateOf(false) }
    var giftingPeer by remember { mutableStateOf<ArenaRankModel?>(null) }
    var isGiftingInProgress by remember { mutableStateOf(false) }
    var giftErrorMsg by remember { mutableStateOf<String?>(null) }
    var giftSuccessMsg by remember { mutableStateOf<String?>(null) }
    var showAiCalcDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    val myEmail = remember(userEmail, currentUsername) {
        val emailVal = userEmail ?: ""
        if (emailVal.isNotEmpty()) {
            emailVal
        } else if (currentUsername?.contains("@") == true) {
            currentUsername ?: ""
        } else {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("user_email_$currentUsername", "") ?: ""
        }
    }

    val myDisplayName = remember(userName, userNickname, currentUsername, myEmail) {
        val base = if (!userNickname.isNullOrEmpty()) userNickname else if (!userName.isNullOrEmpty()) userName else currentUsername ?: ""
        if (base.isEmpty() || base == "Anonymous") {
            if (myEmail.isNotEmpty()) myEmail.substringBefore("@") else "Anonymous"
        } else {
            base
        }
    }

    val myTodayFocusMs = remember(peerUiCards, isTimerRunning, isStopwatchActive, isFocusPhase, isPaused, accumulatedSessionTimeMs, leaderboard) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySecs = com.example.util.FocusTimerManager.focusRecords.value.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
        val pendingSecs = com.example.util.FocusTimerManager.pendingFocusReview.value?.let { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
        val localCompletedMs = (completedTodaySecs + pendingSecs) * 1000L

        val baseCompletedMs = localCompletedMs

        var localActiveMs = 0L
        val hasActiveSession = (isTimerRunning || isStopwatchActive || accumulatedSessionTimeMs > 0L) && com.example.util.FocusTimerManager.pendingFocusReview.value == null
        if (hasActiveSession) {
            localActiveMs = accumulatedSessionTimeMs
        }
        baseCompletedMs + localActiveMs
    }

    val filteredPeerUiCards = remember(peerUiCards, myEmail, currentUsername) {
        val cleanMyEmail = myEmail.lowercase().trim()
        val cleanMyUsername = currentUsername?.lowercase()?.trim() ?: ""
        
        fun normalize(str: String): String {
            return str.lowercase().replace(".", "").replace("_", "").replace("-", "").replace("@", "").trim()
        }
        
        val normalizedMyEmail = normalize(cleanMyEmail)
        val normalizedMyUsername = normalize(cleanMyUsername)
        val sanitizedMyEmail = if (cleanMyEmail.isNotEmpty()) com.example.api.DevicePresenceManager.sanitizeEmail(cleanMyEmail) else ""

        peerUiCards.filter { card ->
            val peerIdClean = card.peerState.userId.lowercase().trim()
            val normalizedPeerId = normalize(peerIdClean)
            
            val isEmailMatch = cleanMyEmail.isNotEmpty() && (peerIdClean == cleanMyEmail || normalizedPeerId == normalizedMyEmail || peerIdClean == sanitizedMyEmail)
            val isUsernameMatch = cleanMyUsername.isNotEmpty() && (peerIdClean == cleanMyUsername || normalizedPeerId == normalizedMyUsername)
            val isMe = isEmailMatch || isUsernameMatch
            !isMe
        }
    }



    val dynamicLeaderboard = remember(
        filteredPeerUiCards, 
        historyRecords, 
        isTimerRunning, 
        isStopwatchActive, 
        isFocusPhase, 
        isPaused, 
        accumulatedSessionTimeMs, 
        leaderboardPeriod, 
        myEmail, 
        myDisplayName, 
        userEmoji,
        leaderboard
    ) {
        val list = mutableListOf<ArenaRankModel>()

        // 1. Calculate activeSessionMs for me
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        var localActiveMs = 0L
        val hasActiveSession = (isTimerRunning || isStopwatchActive || accumulatedSessionTimeMs > 0L) && com.example.util.FocusTimerManager.pendingFocusReview.value == null
        if (hasActiveSession) {
            localActiveMs = accumulatedSessionTimeMs
        }

        val currentDeviceKey = com.example.util.DeviceIdProvider.getDeviceId(context)
        val myRankModel = leaderboard.find { it.isMe }
        val myLeaderboardTodayMs = myRankModel?.totalFocusMs ?: 0L

        // 2. Calculate "my" total ms for the selected period
        val myTotalMs = when (leaderboardPeriod) {
            "TODAY" -> {
                val completedTodaySecs = com.example.util.FocusTimerManager.focusRecords.value.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
                val pendingSecs = com.example.util.FocusTimerManager.pendingFocusReview.value?.let { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                val localCompletedMs = (completedTodaySecs + pendingSecs) * 1000L
                maxOf(localCompletedMs, myLeaderboardTodayMs) + localActiveMs
            }
            "PAST_7_DAYS" -> {
                val sevenDaysAgoMs = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
                val local7DaysMs = historyRecords.filter { it.start_time_ms >= sevenDaysAgoMs }.sumOf { it.total_focus_ms }
                maxOf(local7DaysMs, myRankModel?.totalFocusMs ?: 0L)
            }
            "PAST_30_DAYS" -> {
                val thirtyDaysAgoMs = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L
                val local30DaysMs = historyRecords.filter { it.start_time_ms >= thirtyDaysAgoMs }.sumOf { it.total_focus_ms }
                maxOf(local30DaysMs, myRankModel?.totalFocusMs ?: 0L)
            }
            else -> { // ALL_TIME
                val localAllTimeMs = historyRecords.sumOf { it.total_focus_ms }
                maxOf(localAllTimeMs, myRankModel?.totalFocusMs ?: 0L)
            }
        }

        val myAllTimeMs = run {
            val localAllTimeMs = historyRecords.sumOf { it.total_focus_ms }
            val myAllTimeLeaderboardMs = if (leaderboardPeriod == "ALL_TIME") (myRankModel?.totalFocusMs ?: 0L) else 0L
            maxOf(localAllTimeMs, myAllTimeLeaderboardMs)
        }
        val myLocalStreak = com.example.api.AnalyticsVaultEngine.calculateDailyConsistencyStreak(context, historyRecords)
        val myStreak = if (myLocalStreak > 0) myLocalStreak else (myRankModel?.activeStreak ?: 0)
        val myTopSub = myRankModel?.topSubject ?: "None"
        val myTodayFocusMs = run {
            val completedTodaySecs = com.example.util.FocusTimerManager.focusRecords.value.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
            val pendingSecs = com.example.util.FocusTimerManager.pendingFocusReview.value?.let { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
            val localCompletedMs = (completedTodaySecs + pendingSecs) * 1000L
            maxOf(localCompletedMs, myLeaderboardTodayMs) + localActiveMs
        }
        val myXp = myRankModel?.xpScore ?: com.example.api.ArenaLeaderboardEngine.calculateXp(myTotalMs, myStreak)

        list.add(
            ArenaRankModel(
                email = myEmail,
                displayName = myDisplayName,
                totalFocusMs = myTotalMs,
                activeStreak = myStreak,
                xpScore = myXp,
                topSubject = myTopSub,
                isMe = true,
                customEmoji = userEmoji ?: "👤"
            )
        )

        // 3. Add other peers
        filteredPeerUiCards.forEach { card ->
            val peer = card.peerState
            val activeSessionFocusMs = com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(peer.timeline, peer.status)
            val matchedLeaderboardPeer = leaderboard.find { it.email.lowercase().trim() == peer.userId.lowercase().trim() }
            val peerLeaderboardTodayMs = matchedLeaderboardPeer?.totalFocusMs ?: 0L

            val peerTotalMs = when (leaderboardPeriod) {
                "TODAY" -> {
                    maxOf(peer.todayFocusMs, peerLeaderboardTodayMs) + activeSessionFocusMs
                }
                "PAST_7_DAYS" -> {
                    (matchedLeaderboardPeer?.totalFocusMs ?: 0L) + activeSessionFocusMs
                }
                "PAST_30_DAYS" -> {
                    (matchedLeaderboardPeer?.totalFocusMs ?: 0L) + activeSessionFocusMs
                }
                else -> { // ALL_TIME
                    (matchedLeaderboardPeer?.totalFocusMs ?: 0L) + activeSessionFocusMs
                }
            }

            val peerAllTimeMs = (matchedLeaderboardPeer?.totalFocusMs ?: 0L) + activeSessionFocusMs

            val streakToUse = matchedLeaderboardPeer?.activeStreak ?: 0
            val subToUse = matchedLeaderboardPeer?.topSubject ?: "None"
            val peerTodayFocusMs = maxOf(peer.todayFocusMs, peerLeaderboardTodayMs) + activeSessionFocusMs
            val peerXp = matchedLeaderboardPeer?.xpScore ?: com.example.api.ArenaLeaderboardEngine.calculateXp(peerTotalMs, streakToUse)

            list.add(
                ArenaRankModel(
                    email = peer.userId,
                    displayName = peer.displayName,
                    totalFocusMs = peerTotalMs,
                    activeStreak = streakToUse,
                    xpScore = peerXp,
                    topSubject = subToUse,
                    isMe = false,
                    customEmoji = peer.customEmoji ?: "👤"
                )
            )
        }

        // 4. Sort and assign ranks with strict deduplication by email AND displayName
        val sortedList = list.sortedWith(
            compareByDescending<ArenaRankModel> { it.totalFocusMs }
                .thenByDescending { it.xpScore }
        )

        val seenEmails = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        val deduplicatedList = mutableListOf<ArenaRankModel>()

        fun normalizeEmail(email: String): String {
            val lower = email.lowercase().trim()
            val beforeAt = lower.substringBefore("@")
            return beforeAt.replace(".", "").replace("_", "").replace("-", "")
        }

        fun normalizeName(name: String): String {
            return name.lowercase().trim()
                .replace(" ", "")
                .replace(".", "")
                .replace("_", "")
                .replace("-", "")
        }

        for (item in sortedList) {
            val normEmail = normalizeEmail(item.email)
            val normName = normalizeName(item.displayName)
            
            if (normEmail.isNotEmpty() && seenEmails.contains(normEmail)) continue
            if (normName.isNotEmpty() && seenNames.contains(normName)) continue
            
            seenEmails.add(normEmail)
            seenNames.add(normName)
            deduplicatedList.add(item)
        }

        deduplicatedList.mapIndexed { index, model ->
            model.copy(rank = index + 1)
        }
    }

    val todayRanks = remember(dynamicLeaderboard) {
        dynamicLeaderboard.map {
            TodayRankModel(
                email = it.email,
                displayName = it.displayName,
                todayFocusMs = it.totalFocusMs,
                isMe = it.isMe,
                customEmoji = it.customEmoji
            )
        }
    }

    val masteryStats = remember(historyRecords, masteryPeriod) {
        getSyllabusMasteryForPeriod(historyRecords, masteryPeriod)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16161A))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tab 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTabSelection == 0) Color(0xFFFFB300).copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.dp, 
                            if (activeTabSelection == 0) Color(0xFFFFB300).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { activeTabSelection = 0 }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ACCOUNTABILITY ARENA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTabSelection == 0) Color(0xFFFFB300) else Color.Gray,
                        letterSpacing = 1.sp
                    )
                }

                // Tab 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTabSelection == 1) Color(0xFF00C853).copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.dp, 
                            if (activeTabSelection == 1) Color(0xFF00C853).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { activeTabSelection = 1 }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SYLLABUS SKILL TREE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTabSelection == 1) Color(0xFF00C853) else Color.Gray,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (activeTabSelection == 0) {
                // ACCOUNTABILITY ARENA VIEW
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Elegant Header Block
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF1A1A24), Color(0xFF0F0F11))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CA INTER ARENA",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFFB300),
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Weekly Accountability Ring",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // AI Formula Explanation Button
                                    IconButton(
                                        onClick = { showAiCalcDialog = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF64B5F6).copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "AI Formula",
                                            tint = Color(0xFF64B5F6),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Logs Button
                                    IconButton(
                                        onClick = { showLogDialog = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.1f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "Logs",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Standard Trophy
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFFB300).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Arena Trophy",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Real-time study metrics computed 100% locally. Gifting Streak Shields protects friends' daily focus loops.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Leaderboard Period Dropdown Selector
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(8.dp))
                                        .clickable { isDropdownExpanded = true }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Period: ",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = when (leaderboardPeriod) {
                                            "TODAY" -> "Today"
                                            "PAST_7_DAYS" -> "Past 7 Days"
                                            "PAST_30_DAYS" -> "Past 30 Days"
                                            "ALL_TIME" -> "All Time"
                                            else -> "Today"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB300)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Expand Options",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF16161A))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Today", color = Color.White) },
                                        onClick = {
                                            leaderboardPeriod = "TODAY"
                                            isDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Past 7 Days", color = Color.White) },
                                        onClick = {
                                            leaderboardPeriod = "PAST_7_DAYS"
                                            isDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Past 30 Days", color = Color.White) },
                                        onClick = {
                                            leaderboardPeriod = "PAST_30_DAYS"
                                            isDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("All Time", color = Color.White) },
                                        onClick = {
                                            leaderboardPeriod = "ALL_TIME"
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }

                            // Interactive Personal Streak & Active Shields Panel
                            val myRank = dynamicLeaderboard.find { it.isMe }
                            val myStreak = myRank?.activeStreak ?: 0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                    .clickable {
                                        showShieldsBottomSheet = true
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🔥",
                                        fontSize = 24.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Your Streak",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$myStreak Days",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (activeShields.isNotEmpty()) Color(0xFF0288D1).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Streak Shield",
                                        tint = if (activeShields.isNotEmpty()) Color(0xFF03A9F4) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shields: ${activeShields.size}/2",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeShields.isNotEmpty()) Color(0xFF03A9F4) else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Open Details",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Podium Section (Adaptive size matching study group size, min 4)
                    if (dynamicLeaderboard.isNotEmpty()) {
                        item {
                            val podiumSize = if (dynamicLeaderboard.size < 4) 4 else dynamicLeaderboard.size
                            val topN = dynamicLeaderboard.take(podiumSize)
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.widthIn(max = if (podiumSize > 4) 650.dp else 500.dp)) {
                                    ArenaPodium(
                                        topN = topN,
                                        viewModel = viewModel,
                                        activeShields = activeShields, 
                                        onGiftShieldClick = {
                                            giftingPeer = it
                                            showGiftConfirmDialog = true
                                        },
                                        onShowShieldsBottomSheet = {
                                            showShieldsBottomSheet = true
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.02f))
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFFFB300), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Loading Arena Rankings...",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // TODAY'S LEADERBOARD
                    if (dynamicLeaderboard.isNotEmpty()) {
                        item {
                            Text(
                                text = "TODAY'S LEADERBOARD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }

                        items(dynamicLeaderboard) { peer ->
                            LeaderboardRow(
                                peer = peer,
                                viewModel = viewModel,
                                activeShields = activeShields, 
                                onGiftShieldClick = {
                                    giftingPeer = it
                                    showGiftConfirmDialog = true
                                },
                                onShowShieldsBottomSheet = {
                                    showShieldsBottomSheet = true
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Subject Mastery Breakdown section
                    item {
                        Divider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SUBJECT MASTERY INDEX",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00C853),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = when (masteryPeriod) {
                                        "TODAY" -> "Today's Syllabus Focus"
                                        "MONTHLY" -> "30-Day Syllabus Target"
                                        else -> "Weekly Study Targets"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // 3-Option Toggle Option (TODAY, WEEKLY, MONTHLY)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (masteryPeriod == "TODAY") Color(0xFF00C853) else Color.Transparent)
                                        .clickable { masteryPeriod = "TODAY" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "TODAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (masteryPeriod == "TODAY") Color.Black else Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (masteryPeriod == "WEEKLY") Color(0xFF00C853) else Color.Transparent)
                                        .clickable { masteryPeriod = "WEEKLY" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "WEEKLY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (masteryPeriod == "WEEKLY") Color.Black else Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (masteryPeriod == "MONTHLY") Color(0xFF00C853) else Color.Transparent)
                                        .clickable { masteryPeriod = "MONTHLY" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "MONTHLY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (masteryPeriod == "MONTHLY") Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    if (masteryPeriod == "TODAY") {
                        item {
                            Text(
                                text = "YOUR SUBJECT-WISE STUDY DETAILS (TODAY)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00C853),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(masteryStats) { subjectStats ->
                        SubjectMasteryProgressBar(stats = subjectStats)
                    }
                }
            } else {
                // SYLLABUS SKILL TREE VIEW
                SyllabusTreeScreen(viewModel = viewModel)
            }
        }
    }

    // Modal Bottom Sheet Showing Gifting Details & Gap Bridging History
    if (showShieldsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShieldsBottomSheet = false },
            containerColor = Color(0xFF16161A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF03A9F4).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF03A9F4).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "2-Way Realtime Cloud Sync Active",
                        tint = Color(0xFF03A9F4),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2-Way Realtime Cloud Sync Active • Synced across all your devices",
                        fontSize = 11.sp,
                        color = Color(0xFF81D4FA),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ACTIVE STREAK SHIELDS (${activeShields.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF03A9F4),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Shields automatically protect your daily study streak when you miss a daily target. They are synchronized across all your devices.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (activeShields.isEmpty()) {
                    Text(
                        text = "No active shields remaining. Ask a peer to gift you one!",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                } else {
                    activeShields.forEach { shield ->
                        val dateFormatted = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(java.util.Date(shield.granted_timestamp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Gifted by: ${shield.donor_name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Email: ${shield.donor_email}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = dateFormatted,
                                fontSize = 12.sp,
                                color = Color(0xFF03A9F4),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SHIELD USAGE & GAP BRIDGING HISTORY (${consumedShields.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFB74D),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Audit log of shields consumed to bridge study gaps (e.g., 19/07, 22/07) and safeguard your active streak across devices.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (consumedShields.isEmpty()) {
                    Text(
                        text = "No shields consumed yet. Whenever a gap day occurs (e.g., 19/07 or 22/07), shields automatically consume to protect your streak.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                } else {
                    consumedShields.forEach { shield ->
                        val gapDate = shield.consumed_date ?: "Gap Day"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFB74D).copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Shield Donor: ${shield.donor_name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Donor: ${shield.donor_email}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✓ Bridged $gapDate",
                                    fontSize = 11.sp,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showShieldsBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Gift Confirmation Dialog
    if (showGiftConfirmDialog && giftingPeer != null) {
        AlertDialog(
            onDismissRequest = { 
                if (!isGiftingInProgress) {
                    showGiftConfirmDialog = false
                    giftingPeer = null
                    giftErrorMsg = null
                    giftSuccessMsg = null
                }
            },
            title = { Text("Gift Streak Shield?", color = Color.White) },
            text = { 
                Column {
                    Text(
                        "Would you like to gift a Streak Shield to ${giftingPeer?.displayName}?\n\nThis will deduct 20 XP from your total score to prevent spamming.",
                        color = Color.LightGray
                    )
                    if (isGiftingInProgress) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color(0xFFFFB300), modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    giftErrorMsg?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    giftSuccessMsg?.let { success ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(success, color = Color(0xFF00C853), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                if (giftSuccessMsg == null) {
                    TextButton(
                        enabled = !isGiftingInProgress,
                        onClick = {
                            isGiftingInProgress = true
                            giftErrorMsg = null
                            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            val username = prefs.getString("current_username", "Guest") ?: "Guest"
                            val cachedNickname = prefs.getString("user_nickname_$username", "") ?: ""
                            val cachedName = prefs.getString("user_name_$username", "") ?: ""
                            val resolvedName = if (cachedNickname.isNotEmpty()) cachedNickname else if (cachedName.isNotEmpty()) cachedName else username

                            StreakShieldManager.giftShield(
                                context = context,
                                senderEmail = email,
                                senderName = resolvedName,
                                friendEmail = giftingPeer!!.email,
                                onSuccess = {
                                    isGiftingInProgress = false
                                    giftSuccessMsg = "Shield successfully gifted! 20 XP deducted."
                                    // Trigger stats update to subtract XP locally
                                    StreakShieldManager.fetchAndSyncShields(context, email)
                                },
                                onFailure = { ex ->
                                    isGiftingInProgress = false
                                    giftErrorMsg = ex.message ?: "An error occurred"
                                }
                            )
                        }
                    ) {
                        Text("Confirm (-20 XP)", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        onClick = {
                            showGiftConfirmDialog = false
                            giftingPeer = null
                            giftSuccessMsg = null
                        }
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (giftSuccessMsg == null) {
                    TextButton(
                        enabled = !isGiftingInProgress,
                        onClick = { showGiftConfirmDialog = false; giftingPeer = null }
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    val dialogContext = LocalContext.current

    if (showAiCalcDialog) {
        AlertDialog(
            onDismissRequest = { showAiCalcDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6)
                    )
                    Text(
                        text = "AI Formula & Calculations",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🎯 Daily Focus Thresholds",
                                    color = Color(0xFF64B5F6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• 6 Hours: Daily Streak Target. Falling short consumes 1 Streak Shield.\n" +
                                           "• 8 Hours: Standard High-Focus Target.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "✨ XP & Study Credits Rules",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Base Study Rate: 15 minutes of focus = 1 XP.\n" +
                                           "• Excess Study Accumulator: Any remainders under 15 mins (e.g. 12 mins) are not lost! They are saved and added separately once they accumulate to >= 15 minutes.\n" +
                                           "• Over-Performance (Above 8 Hours): Gained rate improves to 10 minutes = 1 XP.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ Deficit & Deductions",
                                    color = Color(0xFFE57373),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Fair Focus Reward: XP is strictly additive based on your study time and streak.\n" +
                                           "• Zero Daily Penalties: Daily reset of focus time will never decrease or penalize your total earned XP score.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🛡️ Streak Shield Milestones",
                                    color = Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Startup Reward: 2 free shields granted at start.\n" +
                                           "• Endurance Milestone: Study for 10+ hours in a single day to earn 1 free Shield.\n" +
                                           "• Gift Giver: Gift a Streak Shield to a friend in need for 20 XP.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🔥 Streak Multiplier Bonus",
                                    color = Color(0xFFFF8A65),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• +10% extra XP multiplier for every day of your active study streak!",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiCalcDialog = false }) {
                    Text("Understood", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0E101A)
        )
    }

    if (showLogDialog) {
        val focusLogs = remember(showLogDialog) { com.example.api.FocusLogManager.getLogs(dialogContext) }
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "Focus Credit & Shield Logs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            com.example.api.FocusLogManager.clearLogs(dialogContext)
                            showLogDialog = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear Logs",
                            tint = Color.Gray
                        )
                    }
                }
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (focusLogs.isEmpty() || (focusLogs.size == 1 && focusLogs[0].startsWith("No focus log"))) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No focus transaction logs yet.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(focusLogs) { logLine ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131524)),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = logLine,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0E101A)
        )
    }
}

data class PodiumSpot(val rank: Int, val peer: ArenaRankModel?)

@Composable
fun ArenaPodium(
    topN: List<ArenaRankModel>,
    viewModel: AppViewModel,
    activeShields: List<LocalShieldsVault>,
    onGiftShieldClick: (ArenaRankModel) -> Unit,
    onShowShieldsBottomSheet: () -> Unit
) {
    val podiumOrder = remember(topN) {
        val list = java.util.LinkedList<PodiumSpot>()
        // We want a minimum of 4 spots, or the study group size (topN.size)
        val maxRank = if (topN.size < 4) 4 else topN.size
        for (rank in 1..maxRank) {
            val peer = if (rank <= topN.size) topN[rank - 1] else null
            val spot = PodiumSpot(rank, peer)
            if (rank == 1) {
                list.add(spot)
            } else if (rank % 2 == 0) {
                list.addFirst(spot) // 2nd, 4th, 6th... left side
            } else {
                list.addLast(spot) // 3rd, 5th, 7th... right side
            }
        }
        list.toList()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (podiumOrder.size > 4) {
                    Modifier.horizontalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = if (podiumOrder.size > 4) Arrangement.spacedBy(12.dp) else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        podiumOrder.forEach { spot ->
            val rank = spot.rank
            val peer = spot.peer

            val podiumHeight = when (rank) {
                1 -> 280.dp
                2 -> 245.dp
                3 -> 210.dp
                4 -> 180.dp
                else -> maxOf(140, 280 - (rank - 1) * 35).dp
            }
            val medalColor = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFBDC3C7)
                3 -> Color(0xFFCD7F32)
                4 -> Color(0xFF4DB6AC) // Teal for 4th place
                else -> Color(0xFF78909C)
            }
            val rankText = when (rank) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${rank}th"
            }

            Box(
                modifier = Modifier
                    .then(
                        if (podiumOrder.size > 4) {
                            Modifier.width(85.dp)
                        } else {
                            Modifier.weight(1f)
                        }
                    )
                    .height(podiumHeight)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (rank == 1) {
                                listOf(Color(0xFF2C2512), Color(0xFF14141A))
                            } else {
                                listOf(Color(0xFF1E1E24), Color(0xFF111116))
                            }
                        )
                    )
                    .border(
                        width = if (peer?.isMe == true) 2.dp else 1.dp,
                        brush = if (peer?.isMe == true) {
                            Brush.linearGradient(listOf(Color(0xFF00C853), Color(0xFF00E676)))
                        } else {
                            Brush.linearGradient(listOf(medalColor.copy(alpha = 0.6f), Color.Transparent))
                        },
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (peer != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        // Badge / Rank Label
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(medalColor.copy(alpha = 0.2f))
                                .border(1.dp, medalColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = rankText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = medalColor
                              )
                        }

                        // Initials Avatar
                        LaunchedEffect(peer.email) {
                            if (!viewModel.firestoreAvatars.containsKey(peer.email)) {
                                viewModel.fetchUserAvatarFromFirestore(peer.email)
                            }
                        }

                        val resolvedEmoji = remember(peer.customEmoji) {
                            if (!peer.customEmoji.isNullOrEmpty()) {
                                peer.customEmoji
                            } else {
                                "👤"
                            }
                        }
                        val avatarSize = if (rank == 1) 46.dp else if (rank == 2) 40.dp else 36.dp
                        val avatarFontSize = if (rank == 1) 14.sp else if (rank == 2) 12.sp else 11.sp
                        UserAvatar(
                            emojiOrBase64 = resolvedEmoji,
                            size = avatarSize,
                            fontSize = avatarFontSize,
                            fallback = peer.displayName.take(2).uppercase(),
                            modifier = Modifier.border(1.5.dp, medalColor, CircleShape)
                        )

                        // User Info
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = peer.displayName,
                                fontSize = if (rank == 1) 13.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (peer.isMe) Color(0xFF00C853) else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = formatFocusMsToHours(peer.totalFocusMs),
                                fontSize = if (rank == 1) 11.sp else 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.LightGray
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${peer.xpScore} XP",
                                fontSize = if (rank == 1) 11.sp else 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (peer.xpScore < 0) Color(0xFFEF4444) else Color(0xFFFFB300)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (peer.activeStreak > 0) {
                                    Text(
                                        text = "🔥 ${peer.activeStreak}d",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF7043)
                                    )
                                }

                                if (peer.isMe && activeShields.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Shield Active",
                                        tint = Color(0xFF03A9F4),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { onShowShieldsBottomSheet() }
                                      )
                                }
                            }

                            // Gift button removed from podium/bar graph
                        }
                    }
                } else {
                    // Empty Podium Spot
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = rankText,
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Invite Partner",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Unoccupied",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(
    peer: ArenaRankModel,
    viewModel: AppViewModel,
    activeShields: List<LocalShieldsVault>,
    onGiftShieldClick: (ArenaRankModel) -> Unit,
    onShowShieldsBottomSheet: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (peer.isMe) Color(0xFF16251B) else Color(0xFF16161A)
        ),
        border = if (peer.isMe) BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Rank, Avatar and Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val rankText = when (peer.rank) {
                    1 -> "🥇 1st"
                    2 -> "🥈 2nd"
                    3 -> "🥉 3rd"
                    else -> "🏅 ${peer.rank}th"
                }
                val rankColor = when (peer.rank) {
                    1 -> Color(0xFFFFB300)
                    2 -> Color(0xFFB0BEC5)
                    3 -> Color(0xFFFFAB91)
                    else -> Color.Gray
                }
                Text(
                    text = rankText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = rankColor,
                    modifier = Modifier.width(60.dp)
                )

                LaunchedEffect(peer.email) {
                    if (!viewModel.firestoreAvatars.containsKey(peer.email)) {
                        viewModel.fetchUserAvatarFromFirestore(peer.email)
                    }
                }

                val resolvedEmoji = remember(peer.customEmoji) {
                    if (!peer.customEmoji.isNullOrEmpty()) {
                        peer.customEmoji
                    } else {
                        "👤"
                    }
                }
                UserAvatar(
                    emojiOrBase64 = resolvedEmoji,
                    size = 36.dp,
                    fontSize = 12.sp,
                    fallback = peer.displayName.take(2).uppercase()
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = peer.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (peer.isMe) Color(0xFF00C853) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (peer.topSubject != "None") {
                        Text(
                            text = "Top: ${peer.topSubject}",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Stats and Gift Option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (peer.activeStreak > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF7043).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔥 ${peer.activeStreak}d",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF7043)
                            )
                        }
                    }

                    if (peer.isMe && activeShields.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Active",
                            tint = Color(0xFF03A9F4),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onShowShieldsBottomSheet() }
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatFocusMsToHours(peer.totalFocusMs),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${peer.xpScore} XP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (peer.xpScore < 0) Color(0xFFEF4444) else Color(0xFFFFB300)
                    )
                }

                if (!peer.isMe) {
                    IconButton(
                        onClick = { onGiftShieldClick(peer) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Gift Streak Shield",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectMasteryProgressBar(stats: SubjectMasteryStats) {
    val totalHours = stats.totalFocusMs / 3600000.0
    
    val barColor = remember(stats.subjectName) {
        when {
            stats.subjectName.contains("Paper 1") -> Color(0xFF42A5F5)
            stats.subjectName.contains("Paper 2") -> Color(0xFF9CCC65)
            stats.subjectName.contains("Paper 3") -> Color(0xFFAB47BC)
            stats.subjectName.contains("Paper 4") -> Color(0xFFFF7043)
            stats.subjectName.contains("Paper 5") -> Color(0xFF26A69A)
            else -> Color(0xFFEC407A)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(barColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stats.subjectName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val hours = stats.totalFocusMs / 3600000
            val minutes = (stats.totalFocusMs % 3600000) / 60000
            val formattedTime = String.format(Locale.US, "%02d:%02d", hours, minutes)
            Text(
                text = "$formattedTime studied",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
    }
}

@Composable
fun SyllabusSkillTreeCanvas(historyRecords: List<LocalHistoryVault>) {
    val skillTreeNodes = remember(historyRecords) {
        SyllabusSkillTreeEngine.calculateSyllabusMastery(historyRecords)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "CA INTER SYLLABUS MASTERY MAP",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00C853),
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Study 5 hours in any sub-topic to unlock its mastery node.",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(skillTreeNodes) { node ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Paper Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Paper ${node.subject.paperNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                                Text(
                                    text = node.subject.subjectName.substringAfter(": "),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            
                            val subjectHours = node.totalFocusMs / 3600000.0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f Hrs Total", subjectHours),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.White.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Subtopics Tree Nodes
                        Text(
                            text = "SYLLABUS CHAPTERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        node.subTopics.forEach { subTopic ->
                            val subTopicHours = subTopic.totalFocusMs / 3600000.0
                            val progress = (subTopicHours / 5.0).toFloat().coerceIn(0f, 1f) // Target: 5 Hours to Unlock

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (subTopic.isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = if (subTopic.isUnlocked) "Unlocked" else "Locked",
                                        tint = if (subTopic.isUnlocked) Color(0xFF00C853) else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = subTopic.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (subTopic.isUnlocked) Color(0xFF00C853) else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Simple Progress Bar
                                        LinearProgressIndicator(
                                            progress = progress,
                                            color = if (subTopic.isUnlocked) Color(0xFF00C853) else Color(0xFFFFB300),
                                            trackColor = Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f / 5.0 hrs", subTopicHours),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (subTopic.isUnlocked) Color(0xFF00C853) else Color.Gray
                                    )
                                    if (subTopic.isUnlocked) {
                                        Text(
                                            text = "EMERALD MASTERED",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00C853),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper formatting logic
private fun formatFocusMsToHours(ms: Long): String {
    if (ms <= 0L) return "00:00:00"
    val totalSeconds = maxOf(1L, ms / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun getSyllabusMasteryForPeriod(
    records: List<LocalHistoryVault>,
    period: String
): List<SubjectMasteryStats> {
    val calendar = Calendar.getInstance()
    
    // Calculate the cut-off timestamp
    val cutoffMs = when (period) {
        "TODAY" -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
        "MONTHLY" -> {
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            calendar.timeInMillis
        }
        else -> { // WEEKLY
            // Find Monday of current week
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
    }

    val periodRecords = records.filter { it.start_time_ms >= cutoffMs }

    // Map to CaMasterySubject and group
    val grouped = CaMasterySubject.entries.associateWith { 0L }.toMutableMap()
    
    for (record in periodRecords) {
        val mappedSubject = CaMasterySubject.fromTag(record.subject)
        if (mappedSubject != null) {
            val currentVal = grouped[mappedSubject] ?: 0L
            grouped[mappedSubject] = currentVal + record.total_focus_ms
        }
    }

    return grouped.map { (subject, totalMs) ->
        SubjectMasteryStats(
            subjectName = subject.subjectName,
            totalFocusMs = totalMs
        )
    }
}

data class TodayRankModel(
    val email: String,
    val displayName: String,
    val todayFocusMs: Long,
    val isMe: Boolean,
    val customEmoji: String
)

