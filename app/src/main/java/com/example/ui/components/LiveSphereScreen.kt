package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.BellSenderEngine
import com.example.api.PeerUiCardModel
import com.example.ui.AppViewModel
import com.example.ui.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSphereScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }
    val peerUiCards by viewModel.peerUiCards.collectAsStateWithLifecycle()
    val leaderboard by com.example.api.ArenaLeaderboardEngine.leaderboardFlow.collectAsStateWithLifecycle(emptyList())
    val historyRecords by viewModel.allHistoryVault.collectAsStateWithLifecycle(emptyList())
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userNickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userEmoji by viewModel.userEmoji.collectAsStateWithLifecycle()

    // Dynamically derive my email and display name for Bell sending
    val isTimerRunning by com.example.util.FocusTimerManager.isTimerRunning.collectAsStateWithLifecycle()
    val isStopwatchActive by com.example.util.FocusTimerManager.isStopwatchActive.collectAsStateWithLifecycle()
    val isFocusPhase by com.example.util.FocusTimerManager.isFocusPhase.collectAsStateWithLifecycle()
    val stopwatchSeconds by com.example.util.FocusTimerManager.stopwatchSeconds.collectAsStateWithLifecycle()
    val timerSecondsLeft by com.example.util.FocusTimerManager.timerSecondsLeft.collectAsStateWithLifecycle()
    val attachedTag by com.example.util.FocusTimerManager.attachedTag.collectAsStateWithLifecycle()
    val attachedTask by com.example.util.FocusTimerManager.attachedTask.collectAsStateWithLifecycle()
    val isPaused by com.example.util.FocusTimerManager.isPaused.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by com.example.util.FocusTimerManager.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val accumulatedSessionTimeMs by com.example.util.FocusTimerManager.accumulatedSessionTimeMs.collectAsStateWithLifecycle()

    val myEmail = remember(userEmail, currentUsername) {
        if (userEmail.isNotEmpty()) {
            userEmail
        } else if (currentUsername?.contains("@") == true) {
            currentUsername ?: ""
        } else {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("user_email_$currentUsername", "") ?: ""
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(myEmail) {
        if (myEmail.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.api.DevicePresenceManager.updateDeviceFocusStats(context, myEmail)
                com.example.api.WeeklyStatsUpdater.updateWeeklyStats(context, myEmail, 0L, "")
            }
            com.example.api.ArenaLeaderboardEngine.startListening(context, myEmail, "TODAY")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.example.api.ArenaLeaderboardEngine.stopListening()
        }
    }

    val myDisplayName = remember(userName, userNickname, currentUsername, myEmail) {
        val base = if (userNickname.isNotEmpty()) userNickname else if (userName.isNotEmpty()) userName else currentUsername ?: ""
        if (base.isEmpty() || base == "Anonymous") {
            if (myEmail.isNotEmpty()) myEmail.substringBefore("@") else "Anonymous"
        } else {
            base
        }
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
        
        val oneWeekAgo = com.example.util.StableTime.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)

        peerUiCards.filter { card ->
            val peerIdClean = card.peerState.userId.lowercase().trim()
            val normalizedPeerId = normalize(peerIdClean)
            
            val isEmailMatch = cleanMyEmail.isNotEmpty() && (peerIdClean == cleanMyEmail || normalizedPeerId == normalizedMyEmail || peerIdClean == sanitizedMyEmail)
            val isUsernameMatch = cleanMyUsername.isNotEmpty() && (peerIdClean == cleanMyUsername || normalizedPeerId == normalizedMyUsername)
            val isMe = isEmailMatch || isUsernameMatch
                       
            val isStale = card.peerState.lastUpdated < oneWeekAgo
            !isMe && !isStale
        }
    }

    val myTodayFocusMs = remember(peerUiCards, isTimerRunning, isStopwatchActive, isFocusPhase, stopwatchSeconds, timerSecondsLeft, isPaused, accumulatedSessionTimeMs, leaderboard) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySecs = com.example.util.FocusTimerManager.focusRecords.value.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
        val pendingSecs = com.example.util.FocusTimerManager.pendingFocusReview.value?.let { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
        val localCompletedMs = (completedTodaySecs + pendingSecs) * 1000L

        val myCard = peerUiCards.find { it.peerState.userId.lowercase().trim() == myEmail.lowercase().trim() }
        val baseCompletedMs = maxOf(localCompletedMs, myCard?.peerState?.todayFocusMs ?: 0L)

        var localActiveMs = 0L
        val hasActiveSession = (isTimerRunning || isStopwatchActive || accumulatedSessionTimeMs > 0L) && com.example.util.FocusTimerManager.pendingFocusReview.value == null
        if (hasActiveSession) {
            localActiveMs = accumulatedSessionTimeMs
        }
        baseCompletedMs + localActiveMs
    }

    val myFormattedTime = remember(myTodayFocusMs) {
        com.example.api.TimelineSyncEngine.formatTimeMsToHhMmSs(myTodayFocusMs)
    }

    val allParticipantsSorted = remember(filteredPeerUiCards, myTodayFocusMs, myEmail, myDisplayName, userEmoji, leaderboard, historyRecords) {
        data class TodayRankWithXpModel(
            val email: String,
            val displayName: String,
            val todayFocusMs: Long,
            val isMe: Boolean,
            val customEmoji: String,
            val xp: Int
        )

        val list = mutableListOf<TodayRankWithXpModel>()
        
        // 1. Add me with dynamic XP
        val myLocalStreak = com.example.api.AnalyticsVaultEngine.calculateDailyConsistencyStreak(context, historyRecords)
        val myLeaderboardPeer = leaderboard.find { it.isMe || it.email.lowercase().trim() == myEmail.lowercase().trim() }
        val myStreak = if (myLocalStreak > 0) myLocalStreak else (myLeaderboardPeer?.activeStreak ?: 0)
        val myXp = (myLeaderboardPeer?.xpScore ?: 0) + (myTodayFocusMs / 60000L / 15L).toInt()
        
        list.add(
            TodayRankWithXpModel(
                email = myEmail,
                displayName = myDisplayName,
                todayFocusMs = myTodayFocusMs,
                isMe = true,
                customEmoji = userEmoji ?: "👤",
                xp = myXp
            )
        )
        
        // 2. Add other unique peers with dynamic XP
        filteredPeerUiCards.forEach { card ->
            val peerEmail = card.peerState.userId
            val matchedPeer = leaderboard.find {
                val email1Norm = it.email.lowercase().replace(".", "").replace("_", "").trim()
                val email2Norm = peerEmail.lowercase().replace(".", "").replace("_", "").trim()
                email1Norm == email2Norm
            }
            val peerStreak = matchedPeer?.activeStreak ?: 0
            val peerXp = (matchedPeer?.xpScore ?: 0) + (card.rawElapsedMs / 60000L / 15L).toInt()
            
            list.add(
                TodayRankWithXpModel(
                    email = peerEmail,
                    displayName = card.peerState.displayName,
                    todayFocusMs = card.rawElapsedMs,
                    isMe = false,
                    customEmoji = card.peerState.customEmoji ?: "👤",
                    xp = peerXp
                )
            )
        }
        
        val sorted = list.distinctBy { it.email.lowercase().replace(".", "").replace("_", "").trim() }
            .sortedWith(
                compareByDescending<TodayRankWithXpModel> { it.todayFocusMs }
                    .thenByDescending { it.xp }
            )
            
        sorted.map {
            TodayRankModel(
                email = it.email,
                displayName = it.displayName,
                todayFocusMs = it.todayFocusMs,
                isMe = it.isMe,
                customEmoji = it.customEmoji
            )
        }
    }

    val myRank = remember(allParticipantsSorted) {
        val index = allParticipantsSorted.indexOfFirst { it.isMe }
        if (index != -1) index + 1 else 1
    }

    val sortedFriends = remember(filteredPeerUiCards, allParticipantsSorted) {
        filteredPeerUiCards.sortedBy { card ->
            val peerEmail = card.peerState.userId.lowercase().trim()
            val rankIndex = allParticipantsSorted.indexOfFirst {
                val email1Norm = it.email.lowercase().replace(".", "").replace("_", "").trim()
                val email2Norm = peerEmail.replace(".", "").replace("_", "")
                email1Norm == email2Norm
            }
            if (rankIndex != -1) rankIndex else Int.MAX_VALUE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Friends Focus Details",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Collaborative Study Feed",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.TIMER) },
                        modifier = Modifier.testTag("live_sphere_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Timer",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogDialog = true },
                        modifier = Modifier.testTag("focus_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Study Credit & Shield Logs",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            val myUserEmail = myEmail
                            if (myUserEmail.isNotEmpty()) {
                                viewModel.fetchUserAvatarFromFirestore(myUserEmail, forceRefresh = true)
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.api.DevicePresenceManager.syncAndReviseTodayFocusMsIfIdle(context, myUserEmail)
                                    com.example.api.PeerLiveSphereManager.startListeningToFriends(context, myUserEmail)
                                    viewModel.syncMyRecordsToAllPeers()
                                    viewModel.triggerHistoryPullAndSync(context)
                                }
                            }
                            filteredPeerUiCards.forEach { peerCard ->
                                if (peerCard.peerState.userId.isNotEmpty()) {
                                    viewModel.fetchUserAvatarFromFirestore(peerCard.peerState.userId, forceRefresh = true)
                                }
                            }
                            Toast.makeText(context, "Refreshed profile pictures and synchronized focus timer!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("live_sphere_refresh_avatars_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Profile Pictures & Focus Timer",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF06070D)
                )
            )
        },
        containerColor = Color(0xFF06070D),
        modifier = modifier.testTag("live_sphere_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. My Current Status Card
            Text(
                text = "My Status",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            val myStreak = remember(historyRecords, leaderboard, myEmail) {
                val myLocalStreak = com.example.api.AnalyticsVaultEngine.calculateDailyConsistencyStreak(context, historyRecords)
                val myLeaderboardPeer = leaderboard.find { it.isMe || it.email.lowercase().trim() == myEmail.lowercase().trim() }
                if (myLocalStreak > 0) myLocalStreak else (myLeaderboardPeer?.activeStreak ?: 0)
            }
            val myXp = remember(myTodayFocusMs, myStreak, leaderboard) {
                val myLeaderboardPeer = leaderboard.find { it.isMe || it.email.lowercase().trim() == myEmail.lowercase().trim() }
                (myLeaderboardPeer?.xpScore ?: 0) + (myTodayFocusMs / 60000L / 15L).toInt()
            }

            MyStatusCard(
                myDisplayName = myDisplayName,
                myEmail = myEmail,
                isTimerRunning = isTimerRunning,
                isStopwatchActive = isStopwatchActive,
                isFocusPhase = isFocusPhase,
                displayTime = myFormattedTime,
                attachedTag = attachedTag,
                attachedTaskName = attachedTask?.title ?: "",
                userEmoji = userEmoji,
                isPaused = isPaused,
                wasStartedFromStopwatch = wasStartedFromStopwatch,
                myRank = myRank,
                myXp = myXp,
                myStreak = myStreak
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Peers in Friends Focus Details",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (sortedFriends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Empty Friends Focus Details",
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your Friends Focus list is quiet",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add friends and start focus sessions to see them here!",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("live_sphere_peer_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(sortedFriends, key = { it.peerState.userId }) { cardModel ->
                        val peerRank = allParticipantsSorted.indexOfFirst {
                            val email1Norm = it.email.lowercase().replace(".", "").replace("_", "").trim()
                            val email2Norm = cardModel.peerState.userId.lowercase().replace(".", "").replace("_", "").trim()
                            email1Norm == email2Norm
                        } + 1
                        val matchedPeer = leaderboard.find {
                            val email1Norm = it.email.lowercase().replace(".", "").replace("_", "").trim()
                            val email2Norm = cardModel.peerState.userId.lowercase().replace(".", "").replace("_", "").trim()
                            email1Norm == email2Norm
                        }
                        val peerStreak = matchedPeer?.activeStreak ?: 0
                        val peerXp = (matchedPeer?.xpScore ?: 0) + (cardModel.rawElapsedMs / 60000L / 15L).toInt()
                        PeerStatusCard(
                            cardModel = cardModel,
                            viewModel = viewModel,
                            peerRank = peerRank,
                            peerXp = peerXp,
                            peerStreak = peerStreak,
                            onBellClick = {
                                BellSenderEngine.sendBell(
                                    context = context,
                                    myEmail = myEmail,
                                    senderDisplayName = myDisplayName,
                                    friendEmail = cardModel.peerState.userId,
                                    peerStatus = cardModel.peerState.status,
                                    onSuccess = {
                                        Toast.makeText(context, "🔔 Bell sent to ${cardModel.peerState.displayName}!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        if (err != "Cooldown active") {
                                            Toast.makeText(context, "Failed to send Bell: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val dialogContext = LocalContext.current

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

@Composable
fun PeerStatusCard(
    cardModel: PeerUiCardModel,
    viewModel: AppViewModel,
    peerRank: Int,
    peerXp: Int,
    peerStreak: Int,
    onBellClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peer = cardModel.peerState
    val status = peer.status.lowercase()
    val isRelaxing = !(status.contains("focusing") || status.contains("study") || status.contains("work") || status.contains("paused") || status.contains("break") || status.contains("breaking"))

    // Theme values depending on peer status
    val (accentColor, backgroundColor, statusLabel) = when {
        status.contains("focusing") || status.contains("study") || status.contains("work") -> {
            Triple(
                Color(0xFF10B981), // Emerald/Green
                Color(0xFF10B981).copy(alpha = 0.08f),
                "Focusing"
            )
        }
        status.contains("paused") || status.contains("break") || status.contains("breaking") -> {
            Triple(
                Color(0xFFF59E0B), // Amber/Yellow
                Color(0xFFF59E0B).copy(alpha = 0.08f),
                "Paused / Break"
            )
        }
        else -> {
            Triple(
                Color(0xFF64748B), // Muted Slate
                Color(0xFF64748B).copy(alpha = 0.05f),
                "Relaxing / Idle"
            )
        }
    }

    val rankSuffix = when (peerRank) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${peerRank}th"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("peer_card_${peer.userId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11131E)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .background(backgroundColor)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            val cleanPeerId = peer.userId.lowercase().trim()
            val sanitizedPeerId = com.example.api.DevicePresenceManager.sanitizeEmail(cleanPeerId)
            val unsanitizedPeerId = if (cleanPeerId.contains("@")) {
                val parts = cleanPeerId.split("@", limit = 2)
                parts[0] + "@" + parts[1].replace("_", ".")
            } else {
                cleanPeerId
            }

            val resolvedEmoji = remember(peer.customEmoji, viewModel.firestoreAvatars.size, peer.userId) {
                val raw = peer.customEmoji
                if (!raw.isNullOrEmpty() && raw != "👤") {
                    raw
                } else {
                    viewModel.firestoreAvatars[peer.userId]
                        ?: viewModel.firestoreAvatars[cleanPeerId]
                        ?: viewModel.firestoreAvatars[sanitizedPeerId]
                        ?: viewModel.firestoreAvatars[unsanitizedPeerId]
                        ?: "👤"
                }
            }

            LaunchedEffect(peer.userId) {
                val hasAvatar = viewModel.firestoreAvatars.containsKey(peer.userId) ||
                                viewModel.firestoreAvatars.containsKey(cleanPeerId) || 
                                viewModel.firestoreAvatars.containsKey(sanitizedPeerId) || 
                                viewModel.firestoreAvatars.containsKey(unsanitizedPeerId)
                                
                if (!hasAvatar) {
                    viewModel.fetchUserAvatarFromFirestore(peer.userId)
                }
            }

            UserAvatar(
                emojiOrBase64 = resolvedEmoji,
                size = 44.dp,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                fallback = peer.displayName.take(2).uppercase()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                // Name & Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = peer.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Rank Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rankSuffix,
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Styled Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tag Badge & Current Task
                if (peer.currentTag.isNotEmpty() && !isRelaxing) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = peer.currentTag,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (peer.currentTask.isNotEmpty() && !peer.currentTask.equals("Relaxing", ignoreCase = true)) {
                    Text(
                        text = peer.currentTask,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "✨ $peerXp XP",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🔥 $peerStreak",
                        color = Color(0xFFFF5722),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Ticking Time
                Text(
                    text = cardModel.formattedLiveTime,
                    color = accentColor,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Interactive Bell / Nudge button
            IconButton(
                onClick = onBellClick,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .testTag("bell_nudge_button_${peer.userId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Send Bell",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MyStatusCard(
    myDisplayName: String,
    myEmail: String,
    isTimerRunning: Boolean,
    isStopwatchActive: Boolean,
    isFocusPhase: Boolean,
    displayTime: String,
    attachedTag: String,
    attachedTaskName: String,
    userEmoji: String,
    isPaused: Boolean,
    wasStartedFromStopwatch: Boolean,
    myRank: Int,
    myXp: Int,
    myStreak: Int
) {
    val isRunning = isTimerRunning || isStopwatchActive
    val (accentColor, backgroundColor, statusLabel) = when {
        isPaused -> {
            Triple(
                Color(0xFFF59E0B), // Amber
                Color(0xFFF59E0B).copy(alpha = 0.08f),
                "Paused (Me)"
            )
        }
        isRunning && isFocusPhase -> {
            Triple(
                Color(0xFF10B981), // Green
                Color(0xFF10B981).copy(alpha = 0.08f),
                "Focusing (Me)"
            )
        }
        isRunning && !isFocusPhase -> {
            Triple(
                Color(0xFFF59E0B), // Amber
                Color(0xFFF59E0B).copy(alpha = 0.08f),
                "On Break (Me)"
            )
        }
        else -> {
            Triple(
                Color(0xFF64748B), // Slate
                Color(0xFF64748B).copy(alpha = 0.05f),
                "Relaxing (Me)"
            )
        }
    }

    val rankSuffix = when (myRank) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${myRank}th"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("my_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11131E)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .background(backgroundColor)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            val cleanEmail = myEmail.lowercase().trim()
            val sanitizedEmail = com.example.api.DevicePresenceManager.sanitizeEmail(cleanEmail)
            val resolvedMyEmoji = remember(userEmoji, myEmail) {
                if (userEmoji.isNotEmpty() && userEmoji != "👤") {
                    userEmoji
                } else {
                    "👤"
                }
            }

            UserAvatar(
                emojiOrBase64 = resolvedMyEmoji,
                size = 44.dp,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                fallback = myDisplayName.take(2).uppercase()
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = myDisplayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // My Rank Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00C853).copy(alpha = 0.15f))
                            .border(0.5.dp, Color(0xFF00C853).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rankSuffix,
                            color = Color(0xFF00C853),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (myEmail.isNotEmpty()) {
                    Text(
                        text = myEmail,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val isLocalRelaxing = !isPaused && !isRunning
                if (attachedTag.isNotEmpty() && !isLocalRelaxing) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = attachedTag,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (attachedTaskName.isNotEmpty() && !attachedTaskName.equals("Relaxing", ignoreCase = true)) {
                    Text(
                        text = attachedTaskName,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "✨ $myXp XP",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🔥 $myStreak",
                        color = Color(0xFFFF5722),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = displayTime,
                    color = accentColor,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
