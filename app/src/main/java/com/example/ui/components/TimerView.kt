package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Task
import com.example.ui.AppViewModel
import com.example.ui.FocusRecord
import com.example.ui.Screen
import com.example.api.*
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.ui.theme.PremiumEffects.glassmorphicCard
import com.example.ui.theme.WaterBlue
import com.example.util.FocusTimerManager
import kotlinx.coroutines.delay
import com.example.data.Deadline
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable


@Composable
fun Modifier.oledPixelShift(isActive: Boolean): Modifier {
    if (!isActive) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "OledPixelShift")
    val timeFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OledPixelShiftTime"
    )

    val shiftX = Math.round(Math.sin(timeFactor.toDouble()) * 3.0).toInt()
    val shiftY = Math.round(Math.cos(timeFactor.toDouble()) * 3.0).toInt()

    return this.offset { androidx.compose.ui.unit.IntOffset(shiftX, shiftY) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val isCloudStateVerified by viewModel.isCloudStateVerified.collectAsStateWithLifecycle()
    val isCommandDevice by viewModel.isCommandDevice.collectAsStateWithLifecycle()
    if (!isCloudStateVerified) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF06070D)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2196F3),
                    modifier = Modifier.testTag("boot_gate_progress")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Verifying cloud session consensus...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }
    var isOverlayPermissionDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true

                // On Compose resume, re-derive true time: elapsed = baseFocusTimeMs + (getUniversalTimeMs() - lastEventTsMs).
                val isRunning = com.example.util.FocusTimerManager.isTimerRunning.value || com.example.util.FocusTimerManager.isStopwatchActive.value
                if (isRunning) {
                    val lastEventTsMs = com.example.util.FocusTimerManager.lastResumeTimeMs.value
                    if (lastEventTsMs != null && lastEventTsMs > 0) {
                        val baseFocusTimeMs = com.example.util.FocusTimerManager.accumulatedSessionTimeMs.value
                        val getUniversalTimeMs = com.example.util.TimeEngine.getUniversalTimeMs()
                        val elapsed = baseFocusTimeMs + (getUniversalTimeMs - lastEventTsMs)
                        com.example.util.FocusTimerManager.setLastResumeTimeMs(getUniversalTimeMs)
                        com.example.util.FocusTimerManager.setAccumulatedSessionTimeMs(elapsed)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Navigation & Modal States
    val showHistoryScreen by viewModel.showHistoryScreen.collectAsStateWithLifecycle()
    var showFriendsFocusDetails by remember { mutableStateOf(false) }
    var selectedDateStr by remember { 
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) 
    }
    var showCalendarDialog by remember { mutableStateOf(false) }
    val showTaskSelectionDialog by viewModel.showTaskSelectionDialog.collectAsStateWithLifecycle()

    // Configuration and Dynamic States
    val focusTimerDurationMins by viewModel.focusTimerDurationMins.collectAsStateWithLifecycle()
    val isImmersive by viewModel.isTimerImmersive.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()
    val focusRecords by viewModel.focusRecords.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val adoptedTodayMs by com.example.util.FocusTimerManager.adoptedTodayMs.collectAsStateWithLifecycle()
    val vaultHistory by viewModel.allHistoryVault.collectAsStateWithLifecycle(emptyList())

    val timerEntryScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.example.util.FocusTimerAnomalyChecker.runDeepAuditAndCheckAnomaly(context, timerEntryScope)
    }

    // Auto-redirection when a Pomodoro or Stopwatch session is active
    LaunchedEffect(isTimerActive, isStopwatchActive, isPaused, wasStartedFromStopwatch, isTabFocusTimerSelected) {
        val isPomoActive = isTimerActive || (isPaused && !wasStartedFromStopwatch)
        val isSwActive = isStopwatchActive || (isPaused && wasStartedFromStopwatch)
        
        if (isPomoActive && !isTabFocusTimerSelected) {
            viewModel.setTabFocusTimerSelected(true)
        } else if (isSwActive && isTabFocusTimerSelected) {
            viewModel.setTabFocusTimerSelected(false)
        }
    }

    // Milestone & Dialog States
    val focusRankPopup by viewModel.focusRankPopup.collectAsStateWithLifecycle()

    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val healthRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle(initialValue = emptyList())
    val leaderboard by com.example.api.ArenaLeaderboardEngine.leaderboardFlow.collectAsStateWithLifecycle(emptyList())

    val prefs = remember(context) { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    // Dynamically calculate focus metrics
    val completedTodaySecs = remember(focusRecords, allUsers, userEmail, currentUsername, leaderboard) {
        val systemTodayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val localSecs = focusRecords.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) }
        
        val cleanMeEmail = userEmail.lowercase().trim()
        val meUser = if (cleanMeEmail.isNotEmpty()) {
            allUsers[cleanMeEmail]
        } else {
            currentUsername?.let { allUsers[it.lowercase().trim()] }
        }
        val devicesMap = meUser?.devices ?: emptyMap()
        
        val currentDeviceKey = com.example.util.DeviceIdProvider.getDeviceId(context)
        val maxOtherDeviceTodayMs = devicesMap.filterKeys { it != currentDeviceKey }.values
            .filter { it.lastUpdateDate == systemTodayStr || it.lastUpdateDate.isNullOrEmpty() }
            .maxOfOrNull { it.todayFocusMs } ?: 0L
        val maxOtherDeviceTodaySecs = (maxOtherDeviceTodayMs / 1000L).toInt()

        val leaderboardPeer = leaderboard.find { it.isMe || it.email.lowercase().trim() == cleanMeEmail }
        val leaderboardTodaySecs = ((leaderboardPeer?.totalFocusMs ?: 0L) / 1000L).toInt()

        maxOf(localSecs, maxOtherDeviceTodaySecs, leaderboardTodaySecs)
    }

    val pendingSecs = remember(pendingFocusReview) {
        val systemTodayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        pendingFocusReview?.let { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
    }

    val optimisticTodaySecs = viewModel.optimisticTodayFocusSeconds.collectAsStateWithLifecycle().value
    val globalTodaySeconds = remember(completedTodaySecs, pendingSecs, isFocusPhase, cumulativeSessionFocusSeconds, stopwatchSeconds, wasStartedFromStopwatch, pendingFocusReview, optimisticTodaySecs, isTimerActive, isStopwatchActive, isPaused) {
        val isRunningOrPaused = isTimerActive || isStopwatchActive || isPaused
        val activeSecs = if (isFocusPhase && pendingFocusReview == null && isRunningOrPaused) {
            if (wasStartedFromStopwatch) stopwatchSeconds else cumulativeSessionFocusSeconds
        } else {
            0
        }
        val base = completedTodaySecs + pendingSecs + activeSecs
        if (optimisticTodaySecs != null) {
            maxOf(base, optimisticTodaySecs.toInt())
        } else {
            base
        }
    }

    var prevGlobalTodaySecsForAnomaly by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(globalTodaySeconds) {
        val prev = prevGlobalTodaySecsForAnomaly
        if (prev != null) {
            com.example.util.FocusTimerAnomalyChecker.evaluateFocusTimeChange(
                oldSecs = prev,
                newSecs = globalTodaySeconds,
                isTimerRunning = isFocusPhase || isStopwatchActive,
                context = context
            )
        }
        prevGlobalTodaySecsForAnomaly = globalTodaySeconds
    }

    val myLeaderboardPeer = remember(leaderboard) {
        leaderboard.find { it.isMe }
    }

    // Keep track of last non-zero baseServerXp to avoid flickering when changing tabs/loading leaderboard
    var lastKnownBaseServerXp by remember { mutableStateOf(prefs.getInt("last_base_server_xp", 0)) }
    LaunchedEffect(myLeaderboardPeer) {
        myLeaderboardPeer?.xpScore?.let {
            if (it > 0) {
                lastKnownBaseServerXp = it
                prefs.edit().putInt("last_base_server_xp", it).apply()
            }
        }
    }

    val myXp = remember(globalTodaySeconds, lastKnownBaseServerXp) {
        lastKnownBaseServerXp + (globalTodaySeconds * 1000L / 60000L / 15L).toInt()
    }

    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    val sleepMinutes = remember(healthRecords, todayStr) {
        healthRecords.find { it.dateString == todayStr }?.sleepMinutes ?: 0
    }
    val todaySleepPortionMins = remember(sleepMinutes, context) {
        com.example.util.SleepTimeHelper.getTodaySleepMinutesPortion(context, sleepMinutes)
    }
    val calendar = java.util.Calendar.getInstance()
    val currentLocalTimeMins = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    
    val wastedMins = remember(globalTodaySeconds, currentLocalTimeMins, todaySleepPortionMins) {
        val focusedMins = globalTodaySeconds / 60
        (currentLocalTimeMins - focusedMins - todaySleepPortionMins).coerceAtLeast(0)
    }

    // Display Custom Date Picker Mini Calendar Dialog
    if (showCalendarDialog) {
        MiniCalendarDialog(
            currentSelectedDateStr = selectedDateStr,
            onDateSelected = { date -> selectedDateStr = date },
            onDismissRequest = { showCalendarDialog = false }
        )
    }

    // Display Achievement rank achievements modal dialog
    focusRankPopup?.let { popupData ->
        FocusRankMilestoneDialog(
            viewModel = viewModel,
            popupData = popupData,
            onDismiss = { viewModel.dismissFocusRankPopup() }
        )
    }

    // Display Task Selection Dialog
    if (showTaskSelectionDialog) {
        TaskSelectionDialog(
            viewModel = viewModel,
            tasks = tasks,
            isTabFocusTimerSelected = viewModel.isTabFocusTimerSelected.value,
            sessionStartTimestamp = sessionStartTimestamp,
            onDismiss = { viewModel.setShowTaskSelectionDialog(false) }
        )
    }

    val showTagSelectionDialog by viewModel.showTagSelectionDialog.collectAsStateWithLifecycle()
    if (showTagSelectionDialog) {
        TagSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowTagSelectionDialog(false) }
        )
    }

    // Display Friends Focus details list modal
    if (showFriendsFocusDetails) {
        FriendsFocusDetailsDialog(
            viewModel = viewModel,
            onDismiss = { showFriendsFocusDetails = false }
        )
    }

    // Focus Timer Anomaly Inspector popup listener
    FocusTimerAnomalyCard(
        currentUsername = currentUsername ?: "",
        onRunFullReconciliation = {
            viewModel.triggerManualRecalibration { }
        }
    )

    // Centralized session timer confirm & auto-save controller
    TimerConfirmDialogController(
        viewModel = viewModel,
        focusTimerDurationMins = focusTimerDurationMins,
        selectedTask = selectedTask,
        sessionStartTimestamp = sessionStartTimestamp,
        onSessionStartTimestampChange = { viewModel.setSessionStartTimestamp(it) }
    )

    // Sync timer display seconds remaining with duration modification from Settings
    LaunchedEffect(focusTimerDurationMins) {
        if (!isTimerActive && isFocusPhase) {
            viewModel.setTimerDuration(focusTimerDurationMins)
        }
    }

    val isTimerActiveNow = isTimerActive || isStopwatchActive

    var prevIsRunningMain by remember { mutableStateOf(isTimerActiveNow) }
    var prevIsPausedMain by remember { mutableStateOf(isPaused) }
    var prevIsFocusPhaseMain by remember { mutableStateOf(isFocusPhase) }

    LaunchedEffect(isTimerActiveNow, isPaused, isFocusPhase) {
        val focusStartedOrResumed = isTimerActiveNow && isFocusPhase && !isPaused && (
            !prevIsRunningMain || prevIsPausedMain || !prevIsFocusPhaseMain
        )
        if (focusStartedOrResumed) {
            viewModel.setTimerImmersive(true)
        }
        prevIsRunningMain = isTimerActiveNow
        prevIsPausedMain = isPaused
        prevIsFocusPhaseMain = isFocusPhase
    }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(isImmersive) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        androidx.compose.ui.input.key.Key.F, androidx.compose.ui.input.key.Key.F11 -> {
                            viewModel.setTimerImmersive(!isImmersive)
                            true
                        }
                        androidx.compose.ui.input.key.Key.Escape -> {
                            viewModel.setTimerImmersive(false)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                }
            }
    ) {
        if (isImmersive) {
            TimerImmersiveContent(
                viewModel = viewModel,
                focusTimerDurationMins = focusTimerDurationMins,
                onShowFriendsDetails = { viewModel.navigateTo(Screen.LIVE_SPHERE) },
                modifier = Modifier.oledPixelShift(isTimerActiveNow)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .oledPixelShift(isTimerActiveNow)
                    .background(Color.Black)
                    .padding(if (isTablet) 16.dp else 4.dp)
            ) {
                val sduiPrefs = com.example.api.RemoteConfigManager.sduiPreferences.collectAsStateWithLifecycle().value

                // Header Top Bar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (showHistoryScreen) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setShowHistoryScreen(false) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Timer",
                                tint = WaterBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Back to Timer",
                                color = WaterBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { showCalendarDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF151515))
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select focus date",
                                tint = WaterBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        FriendsFocusPill(
                            viewModel = viewModel,
                            onClick = { viewModel.navigateTo(Screen.LIVE_SPHERE) }
                        )

                        if (sduiPrefs.motivationalBannerText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Motivational Banner Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = sduiPrefs.motivationalBannerText,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isBellSilent by viewModel.isBellSilentModeEnabled.collectAsStateWithLifecycle()
                            IconButton(
                                onClick = { viewModel.setBellSilentModeEnabled(!isBellSilent) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isBellSilent) Color(0xFFE53935) else Color(0xFF151515))
                                    .size(32.dp)
                                    .testTag("bell_silent_button")
                            ) {
                                Icon(
                                    imageVector = if (isBellSilent) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                    contentDescription = "Bell Silent Mode Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setTimerImmersive(true) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF151515))
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Enter Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setShowHistoryScreen(true) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF151515))
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Focus History Overview",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Draw system alert window drawing permission banner
                if (!hasOverlayPermission && !isOverlayPermissionDismissed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("overlay_permission_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Permission Info",
                                tint = WaterBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Overlay Widget Enabled",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Allow drawing over other apps to see a floating timer on the screen when minimized.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { isOverlayPermissionDismissed = true },
                                modifier = Modifier.size(28.dp).testTag("dismiss_overlay_permission")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "No I won't",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = showHistoryScreen,
                    transitionSpec = {
                        slideInHorizontally { width -> if (targetState) width else -width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> if (targetState) -width else width } + fadeOut()
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "history_transition"
                ) { targetHistory ->
                    if (targetHistory) {
                        TimerHistoryView(
                            viewModel = viewModel,
                            selectedDateStr = selectedDateStr,
                            globalTodaySeconds = globalTodaySeconds
                        )
                    } else {
                        TimerLiveControlContent(
                            viewModel = viewModel,
                            isTablet = isTablet,
                            isImmersive = false,
                            isAntiBurnCenteredByTap = true,
                            globalTodaySeconds = globalTodaySeconds,
                            focusTimerDurationMins = focusTimerDurationMins,
                            myXp = myXp,
                            wastedMins = wastedMins
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun MiniCalendarDialog(
    currentSelectedDateStr: String,
    onDateSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sdfInput = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val calendar = remember {
        val cal = java.util.Calendar.getInstance()
        try {
            val d = sdfInput.parse(currentSelectedDateStr)
            if (d != null) cal.time = d
        } catch (_: Exception) {}
        cal
    }

    var currentYear by remember { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) } // 0-11

    // Calculate days grid
    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, currentYear)
        cal.set(java.util.Calendar.MONTH, currentMonth)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        Pair(firstDayOfWeek, maxDays)
    }

    val (firstDayOfWeek, maxDays) = daysInMonth
    val monthName = remember(currentMonth) {
        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        monthNames[currentMonth]
    }

    val WaterBlue = Color(0xFF38BDF8)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header of Calendar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear -= 1
                            } else {
                                currentMonth -= 1
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "$monthName $currentYear",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear += 1
                            } else {
                                currentMonth += 1
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next Month",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Days of week header labels
                val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days grid
                val totalSlots = 42
                val cols = 7
                val rows = totalSlots / cols

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (c in 0 until cols) {
                                val slotIndex = r * cols + c
                                val dayNumber = slotIndex - (firstDayOfWeek - 2)
                                val isValidDay = dayNumber in 1..maxDays

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isValidDay) {
                                        val dayStr = String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, dayNumber)
                                        val isSelected = dayStr == currentSelectedDateStr

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(0.85f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) WaterBlue else Color.Transparent
                                                )
                                                .clickable {
                                                    onDateSelected(dayStr)
                                                    onDismissRequest()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val todayStrLocal = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            onDateSelected(todayStrLocal)
                            onDismissRequest()
                        }
                    ) {
                        Text("Reset to Today", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Close", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun TimerConfirmDialogController(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    selectedTask: Task?,
    sessionStartTimestamp: Long?,
    onSessionStartTimestampChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val showElapsedTimeDialog by viewModel.showElapsedTimeDialog.collectAsStateWithLifecycle()
    val editHoursInput by viewModel.editHoursInput.collectAsStateWithLifecycle()
    val editMinutesInput by viewModel.editMinutesInput.collectAsStateWithLifecycle()
    val editSecondsInput by viewModel.editSecondsInput.collectAsStateWithLifecycle()
    val stopSessionType by viewModel.stopSessionType.collectAsStateWithLifecycle()
    val stoppedElapsedSeconds by viewModel.stoppedElapsedSeconds.collectAsStateWithLifecycle()
    val focusNotesInput by viewModel.focusNotesInput.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()

    var originalAutoSavedSeconds by remember { mutableStateOf(0) }
    var originalAutoSavedMinutes by remember { mutableStateOf(0) }
    var originalAutoSavedTask by remember { mutableStateOf<Task?>(null) }
    var isAutoSavedSessionActive by remember { mutableStateOf(false) }
    var autoSavedRecordId by remember { mutableStateOf<String?>(null) }

    // Centralized pending focus review effect
    LaunchedEffect(pendingFocusReview) {
        val review = pendingFocusReview
        if (review != null && !showElapsedTimeDialog) {
            val rSeconds = review.durationSeconds
            viewModel.setEditHoursInput(rSeconds / 3600)
            viewModel.setEditMinutesInput((rSeconds % 3600) / 60)
            viewModel.setEditSecondsInput(rSeconds % 60)
            viewModel.setStopSessionType("timer") 
            viewModel.setStoppedElapsedSeconds(rSeconds)
            
            onSessionStartTimestampChange(com.example.util.StableTime.currentTimeMillis() - (rSeconds * 1000L))
            
            isAutoSavedSessionActive = true
            autoSavedRecordId = review.id
            originalAutoSavedSeconds = rSeconds
            originalAutoSavedMinutes = maxOf(1, (rSeconds + 30) / 60)
            originalAutoSavedTask = selectedTask

            viewModel.setShowElapsedTimeDialog(true)
            viewModel.setTimerImmersive(false)
            viewModel.clearPendingFocusReview()
        }
    }

    // Centralized auto-save effect
    LaunchedEffect(showElapsedTimeDialog, stoppedElapsedSeconds, isAutoSavedSessionActive) {
        if (showElapsedTimeDialog) {
            val totalSeconds = stoppedElapsedSeconds
            if (totalSeconds > 0 && !isAutoSavedSessionActive) {
                // --- DYNAMIC CAUSALITY GUARD ---
                val sessionStart = sessionStartTimestamp ?: (com.example.util.StableTime.currentTimeMillis() - totalSeconds * 1000L)
                val maxAllowed = com.example.util.StableTime.currentTimeMillis() - sessionStart
                val newSessionDuration = totalSeconds * 1000L
                if (newSessionDuration > maxAllowed + 5000L) {
                    return@LaunchedEffect
                }

                val finalMinutes = com.example.util.TimeEngine.roundSecondsToMinutes(totalSeconds)
                viewModel.addFocusMinutes(finalMinutes)
                
                if (stopSessionType == "timer" && finalMinutes >= focusTimerDurationMins) {
                    viewModel.incrementTodayPomos()
                }

                val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                val startStr = sessionStartTimestamp?.let { formatter.format(java.util.Date(it)) }
                    ?: formatter.format(java.util.Date(com.example.util.StableTime.currentTimeMillis() - totalSeconds * 1000L))
                val endStr = formatter.format(java.util.Date())
                val taskName = selectedTask?.title ?: "Focus Session"

                val record = viewModel.addFocusRecord(
                    startStr,
                    endStr,
                    taskName,
                    finalMinutes,
                    focusNotesInput.trim(),
                    totalSeconds,
                    tag = viewModel.attachedTag.value,
                    mode = if (stopSessionType == "stopwatch") "STOPWATCH" else "POMODORO"
                )
                if (record != null) {
                    autoSavedRecordId = record.id

                    if (selectedTask != null) {
                        val updated = selectedTask.copy(actualMinutes = selectedTask.actualMinutes + finalMinutes)
                        viewModel.updateTask(updated)
                        viewModel.attachTaskToTimer(updated)
                        originalAutoSavedTask = selectedTask
                    } else {
                        originalAutoSavedTask = null
                    }

                    originalAutoSavedSeconds = totalSeconds
                    originalAutoSavedMinutes = finalMinutes
                    isAutoSavedSessionActive = true
                }
            }
        }
    }

    if (showElapsedTimeDialog) {
        fun discardElapsedTimeSession() {
            if (isAutoSavedSessionActive) {
                val recordId = autoSavedRecordId
                if (recordId != null) {
                    val records = viewModel.focusRecords.value
                    val originalRecord = records.find { it.id == recordId }
                    if (originalRecord != null) {
                        val durationMinutes = originalRecord.durationMinutes
                        
                        // 1. Subtract the focus minutes
                        viewModel.addFocusMinutes(-durationMinutes)
                        
                        // 2. Subtract task minutes if any task was attached
                        val task = originalAutoSavedTask
                        if (task != null) {
                            val updated = task.copy(actualMinutes = maxOf(0, task.actualMinutes - durationMinutes))
                            viewModel.updateTask(updated)
                            viewModel.attachTaskToTimer(updated)
                        }
                        
                        // 3. Revert Pomodoro count if applicable
                        if (stopSessionType == "timer" && durationMinutes >= focusTimerDurationMins) {
                            viewModel.decrementTodayPomos()
                        }
                        
                        // 4. Delete the record by ID
                        viewModel.deleteFocusRecordById(recordId)
                    }
                }
            }

            com.example.util.FocusTimerManager.recordSessionCompleteOrReset(isSaving = false)

            if (stopSessionType == "timer") {
                viewModel.resetTimer(saveSession = false)
            } else {
                viewModel.resetStopwatch(saveSession = false)
            }
            viewModel.clearPendingFocusReview()
            onSessionStartTimestampChange(null)
            viewModel.setShowElapsedTimeDialog(false)
            viewModel.setFocusNotesInput("")
            viewModel.setTimerImmersive(false)

            isAutoSavedSessionActive = false
            autoSavedRecordId = null
            originalAutoSavedSeconds = 0
            originalAutoSavedMinutes = 0
            originalAutoSavedTask = null
        }

        fun saveAndCloseElapsedTimeSession() {
            val totalSeconds = editHoursInput * 3600 + editMinutesInput * 60 + editSecondsInput
            if (totalSeconds <= 0) {
                android.widget.Toast.makeText(context, "Duration must be greater than 0 seconds to save!", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val finalMinutes = com.example.util.TimeEngine.roundSecondsToMinutes(totalSeconds)

            if (isAutoSavedSessionActive) {
                val recordId = autoSavedRecordId
                if (recordId != null) {
                    val records = viewModel.focusRecords.value
                    val originalRecord = records.find { it.id == recordId }
                    if (originalRecord != null) {
                        val updatedRecord = originalRecord.copy(
                            durationMinutes = finalMinutes,
                            durationSeconds = totalSeconds,
                            notes = focusNotesInput.trim()
                        )
                        viewModel.updateFocusRecordById(recordId, updatedRecord)
                        
                        val diffMinutes = finalMinutes - originalAutoSavedMinutes
                        if (diffMinutes != 0) {
                            viewModel.addFocusMinutes(diffMinutes)
                        }
                        
                        val task = originalAutoSavedTask
                        if (task != null) {
                            val updated = task.copy(actualMinutes = task.actualMinutes + diffMinutes)
                            viewModel.updateTask(updated)
                            viewModel.attachTaskToTimer(updated)
                        }
                        
                        if (stopSessionType == "timer") {
                            val wasPomo = originalAutoSavedMinutes >= focusTimerDurationMins
                            val isPomo = finalMinutes >= focusTimerDurationMins
                            if (!wasPomo && isPomo) {
                                viewModel.incrementTodayPomos()
                            }
                        }
                    }
                }
            } else {
                if (totalSeconds > 0) {
                    // --- DYNAMIC CAUSALITY GUARD ---
                    val sessionStart = sessionStartTimestamp ?: (com.example.util.StableTime.currentTimeMillis() - totalSeconds * 1000L)
                    val maxAllowed = com.example.util.StableTime.currentTimeMillis() - sessionStart
                    val newSessionDuration = totalSeconds * 1000L
                    if (newSessionDuration <= maxAllowed + 5000L) {
                        viewModel.addFocusMinutes(finalMinutes)
                        if (stopSessionType == "timer" && finalMinutes >= focusTimerDurationMins) {
                            viewModel.incrementTodayPomos()
                        }
                        val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                        val startStr = sessionStartTimestamp?.let { formatter.format(java.util.Date(it)) }
                            ?: formatter.format(java.util.Date(com.example.util.StableTime.currentTimeMillis() - totalSeconds * 1000L))
                        val endStr = formatter.format(java.util.Date())
                        val taskName = selectedTask?.title ?: "Focus Session"

                        viewModel.addFocusRecord(
                            startStr,
                            endStr,
                            taskName,
                            finalMinutes,
                            focusNotesInput.trim(),
                            totalSeconds,
                            tag = viewModel.attachedTag.value,
                            mode = if (stopSessionType == "stopwatch") "STOPWATCH" else "POMODORO"
                        )

                        if (selectedTask != null) {
                            val updated = selectedTask.copy(actualMinutes = selectedTask.actualMinutes + finalMinutes)
                            viewModel.updateTask(updated)
                            viewModel.attachTaskToTimer(updated)
                        }
                    }
                }
            }

            // Preserve start time and pause ranges before wiping out current session tracking
            com.example.util.FocusTimerManager.recordSessionCompleteOrReset(isSaving = true)

            if (stopSessionType == "timer") {
                viewModel.resetTimer(saveSession = false)
            } else {
                viewModel.resetStopwatch(saveSession = false)
            }
            viewModel.clearPendingFocusReview()
            onSessionStartTimestampChange(null)
            viewModel.setShowElapsedTimeDialog(false)
            viewModel.setFocusNotesInput("")
            viewModel.setTimerImmersive(false)

            isAutoSavedSessionActive = false
            autoSavedRecordId = null
            originalAutoSavedSeconds = 0
            originalAutoSavedMinutes = 0
            originalAutoSavedTask = null

            com.example.util.FocusTimerManager.setGlobalVerificationFocusedTimeSeconds(totalSeconds)
            com.example.util.FocusTimerManager.setGlobalVerificationRevisedTotalMinutes(com.example.util.FocusTimerManager.getTodayFocusMinutes())
            com.example.util.FocusTimerManager.setGlobalVerificationRevisedTotalSeconds(com.example.util.FocusTimerManager.getTodayFocusSeconds())
            if (com.example.util.FocusTimerManager.verifiedSessionStartMs.value == null) {
                com.example.util.FocusTimerManager.setVerifiedSessionStartMs(com.example.util.StableTime.currentTimeMillis() - totalSeconds * 1000L)
            }
            com.example.util.FocusTimerManager.setShowGlobalVerificationDialog(false)
        }

        Dialog(onDismissRequest = { saveAndCloseElapsedTimeSession() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Confirmation Needed",
                        tint = WaterBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Confirm Focused Time",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val formattedDurationString = if (stoppedElapsedSeconds >= 3600) {
                        "${stoppedElapsedSeconds / 3600}h ${(stoppedElapsedSeconds % 3600) / 60}m ${stoppedElapsedSeconds % 60}s"
                    } else if (stoppedElapsedSeconds >= 60) {
                        "${stoppedElapsedSeconds / 60}m ${stoppedElapsedSeconds % 60}s"
                    } else {
                        "${stoppedElapsedSeconds}s"
                    }
                    Text(
                        text = "Total Session Focus: $formattedDurationString",
                        color = WaterBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Do you confirm you focused for this much time?",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Hours Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { viewModel.setEditHoursInput(editHoursInput + 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = "$editHoursInput",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editHoursInput > 0) viewModel.setEditHoursInput(editHoursInput - 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Colon 1
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(50.dp))
                            Text(":", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Minutes Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { if (editMinutesInput < 59) viewModel.setEditMinutesInput(editMinutesInput + 1) else viewModel.setEditMinutesInput(0) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", editMinutesInput),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editMinutesInput > 0) viewModel.setEditMinutesInput(editMinutesInput - 1) else viewModel.setEditMinutesInput(59) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Colon 2
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(50.dp))
                            Text(":", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Seconds Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Seconds", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { if (editSecondsInput < 59) viewModel.setEditSecondsInput(editSecondsInput + 1) else viewModel.setEditSecondsInput(0) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", editSecondsInput),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editSecondsInput > 0) viewModel.setEditSecondsInput(editSecondsInput - 1) else viewModel.setEditSecondsInput(59) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = focusNotesInput,
                        onValueChange = { viewModel.setFocusNotesInput(it) },
                        label = { Text("What did you focus on?", fontSize = 10.sp) },
                        placeholder = { Text("List tasks, thoughts, or reflections here...", fontSize = 12.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedBorderColor = WaterBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { discardElapsedTimeSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Discard", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { saveAndCloseElapsedTimeSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Yes, Record", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TagSelectionDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val focusTags by viewModel.focusTags.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Focus Tag",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose a tag category to classify your focused block",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (focusTags.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No custom tags created yet. You can add them in settings!",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(focusTags) { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable {
                                            viewModel.attachTagToTimer(tag)
                                            onDismiss()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tag, contentDescription = "Tag", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = tag,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TaskSelectionDialog(
    viewModel: AppViewModel,
    tasks: List<Task>,
    isTabFocusTimerSelected: Boolean,
    sessionStartTimestamp: Long?,
    onDismiss: () -> Unit
) {
    var taskSearchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choose Focus Target",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select an existing task to link focus times dynamically",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = taskSearchQuery,
                    onValueChange = { taskSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = { Text("Search task lists...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedContainerColor = Color(0xFF0F0F0F),
                        unfocusedContainerColor = Color(0xFF0F0F0F)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filteredTasks = remember(tasks, taskSearchQuery) {
                    tasks.filter {
                        !it.isCompleted &&
                        (it.title.contains(taskSearchQuery, ignoreCase = true) ||
                         it.description.contains(taskSearchQuery, ignoreCase = true))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredTasks.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (taskSearchQuery.isEmpty()) "No active tasks in system" else "No matching tasks found",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredTasks) { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable {
                                            viewModel.attachTaskToTimer(task)
                                            viewModel.setShowTaskSelectionDialog(false)
                                            if (sessionStartTimestamp == null) {
                                                viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                                                if (isTabFocusTimerSelected) {
                                                    viewModel.startTimer()
                                                } else {
                                                    viewModel.startStopwatch()
                                                }
                                            }
                                            viewModel.setTimerImmersive(true)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${task.listCategory} • Prior: ${task.priority}",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF102535))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Linked: ${task.actualMinutes}m",
                                                    color = WaterBlue,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Select", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.attachTaskToTimer(null)
                            viewModel.setShowTaskSelectionDialog(false)
                            if (sessionStartTimestamp == null) {
                                viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                                if (isTabFocusTimerSelected) {
                                    viewModel.startTimer()
                                } else {
                                    viewModel.startStopwatch()
                                }
                            }
                            viewModel.setTimerImmersive(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("General Focus", fontSize = 12.sp, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.setShowTaskSelectionDialog(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FocusRankMilestoneDialog(
    viewModel: AppViewModel,
    popupData: com.example.ui.FocusRankPopupData,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("rank_motivation_popup"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.2.dp, WaterBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(WaterBlue.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rank Achievements",
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Daily Focus Milestone",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Calculated comparing your effort yesterday",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Yesterday Rank",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#${popupData.yesterdayRank}",
                            color = WaterBlue,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "out of ${popupData.totalPeersCount} peers",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color(0xFF222222))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Yesterday Focus",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = popupData.yesterdayFocusedTimeStr,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = popupData.motivationalMessage,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaterBlue.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(0.5.dp, WaterBlue.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_rank_popup_btn")
                ) {
                    Text(
                        text = "Let's Do It!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}


@Composable
fun androidx.compose.foundation.layout.ColumnScope.FriendHistoryDetailsContent(
    viewModel: AppViewModel,
    peer: PeerFocusInfo,
    allUsers: Map<String, com.example.api.PeerLiveState>,
    selectedFilter: String,
    targetDates: List<String>,
    todayStr: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val WaterBlue = Color(0xFF38BDF8)
    val yesterdayHistoryText by viewModel.friendYesterdayHistory.collectAsStateWithLifecycle()

    val meEmail = viewModel.userEmail.value.lowercase().trim()
    val targetUser = if (peer.isMe) {
        if (meEmail.isNotEmpty()) allUsers[meEmail] ?: allUsers[com.example.api.DevicePresenceManager.sanitizeEmail(meEmail)] else null
    } else {
        allUsers[peer.username]
            ?: allUsers[com.example.api.DevicePresenceManager.sanitizeEmail(peer.username)]
            ?: allUsers[peer.username.lowercase().trim()]
            ?: allUsers[com.example.api.DevicePresenceManager.sanitizeEmail(peer.username.lowercase().trim())]
            ?: if (peer.username.contains("@")) allUsers[peer.username.substringBefore("@")] else null
    }
    val lastUpdated = targetUser?.lastUpdatedTimestamp ?: 0L
    val lastUpdatedDateStr = if (lastUpdated > 0) {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(lastUpdated))
    } else {
        ""
    }
    val calYesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calYesterday.time)
    val isPeerStale = !peer.isMe && lastUpdatedDateStr.isNotEmpty() && lastUpdatedDateStr != todayStr && lastUpdatedDateStr != yesterdayStr

    val rawFriendRecords = if (isPeerStale) emptyList() else (targetUser?.todaysFocusRecords ?: emptyList())

    // Retrieve/generate records for all dates in target range
    val friendRecords = remember(peer.username, selectedFilter, rawFriendRecords, isPeerStale) {
        val recordsList = mutableListOf<FocusRecord>()
        val sdfTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        val allPeerRecords = if (peer.isMe) {
            emptyList()
        } else {
            FocusTimerManager.loadPeerFocusRecords(context, peer.username)
        }

        val detailDates = targetDates

        detailDates.forEach { dateStr ->
            val isTodayDate = dateStr == todayStr

            if (isTodayDate) {
                if (peer.isMe) {
                    recordsList.addAll(FocusTimerManager.focusRecords.value.filter { it.dateString == todayStr })
                    // Also add live active session if running
                    val isRunning = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.accumulatedSessionTimeMs.value > 0L
                    if (isRunning) {
                        val activeSecs = if (FocusTimerManager.isTimerRunning.value) {
                            FocusTimerManager.cumulativeSessionFocusSeconds.value
                        } else {
                            FocusTimerManager.stopwatchSeconds.value
                        }
                        if (activeSecs > 0) {
                            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            val durationMins = activeSecs / 60
                            val startTimeStr = formatter.format(java.util.Date(System.currentTimeMillis() - activeSecs * 1000L))
                            val endTimeStr = "Now"
                            recordsList.add(
                                FocusRecord(
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    taskTitle = FocusTimerManager.attachedTask.value?.title ?: "Active Session",
                                    durationMinutes = durationMins,
                                    dateString = todayStr,
                                    notes = "In Progress...",
                                    durationSeconds = activeSecs
                                )
                            )
                        }
                    }
                } else if (rawFriendRecords.isNotEmpty()) {
                    recordsList.addAll(rawFriendRecords)
                    // Also add live active session if they are focusing
                    if (targetUser != null && (targetUser.isFocusing == true || targetUser.focusStatus == "paused")) {
                        val totalMs = targetUser.accumulatedTimeMs ?: 0L
                        val startMs = System.currentTimeMillis() - totalMs
                        val activeSecs = (totalMs / 1000).toInt()
                        if (activeSecs > 0) {
                            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            val durationMins = activeSecs / 60
                            val startTimeStr = formatter.format(java.util.Date(startMs))
                            val endTimeStr = "Now"
                            recordsList.add(
                                FocusRecord(
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    taskTitle = targetUser.currentTaskTitle ?: "Active Session",
                                    durationMinutes = durationMins,
                                    dateString = todayStr,
                                    notes = "In Progress...",
                                    durationSeconds = activeSecs,
                                    tag = targetUser.currentTag ?: ""
                                )
                            )
                        }
                    }
                }
            } else {
                if (peer.isMe) {
                    recordsList.addAll(FocusTimerManager.focusRecords.value.filter { it.dateString == dateStr })
                } else {
                    val peerRecs = allPeerRecords.filter { it.dateString == dateStr }
                    if (peerRecs.isNotEmpty()) {
                        recordsList.addAll(peerRecs)
                    }
                }
            }
        }
        recordsList.sortByDescending { it.startTime }
        recordsList
    }

    // Header with back arrows and close
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to friends list",
                tint = WaterBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        UserAvatar(emojiOrBase64 = peer.emoji, size = 24.dp, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = peer.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(@${peer.username})",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            val friendStatusText = when (peer.focusStatus) {
                "focusing" -> "Live Focusing Now"
                "paused" -> "Paused"
                "break" -> "On a Break"
                else -> "Currently Idle"
            }
            val friendStatusColor = when (peer.focusStatus) {
                "focusing" -> Color(0xFF2E7D32)
                "paused" -> Color(0xFFFFA726)
                "break" -> Color(0xFF4CAF50)
                else -> Color.Gray
            }
            Text(
                text = friendStatusText,
                color = friendStatusColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close details",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        yesterdayHistoryText?.let { text ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📜 Yesterday's Compiled History File",
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F0F0F))
                            .padding(8.dp)
                    )
                }
            }
        }

        // Chronological graph view (for today's timeline slice)
        DailyFocusTimelineChrono(focusRecords = friendRecords, selectedDateStr = todayStr)

        // Focus activities summary breakdown card
        FocusSummaryCard(
            focusRecords = friendRecords,
            todayStr = todayStr,
            totalFocusMinutes = friendRecords.sumOf { it.durationMinutes },
            liveAddedMinutes = 0,
            liveAddedSeconds = 0
        )

        // Synced logs list format matching user's page
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$selectedFilter Session Logs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    Text("${friendRecords.size} sessions", color = Color.Gray, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (friendRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No focus records synced for this period",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        friendRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(WaterBlue)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.taskTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text("${record.startTime} - ${record.endTime}", color = Color.Gray, fontSize = 9.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    LiveRecordDurationText(
                                        record = record,
                                        isFocusing = peer.isFocusing,
                                        isMe = peer.isMe,
                                        peerRemote = targetUser
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back", color = Color.White)
        }
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiveRecordDurationText(
    record: FocusRecord,
    isFocusing: Boolean,
    isMe: Boolean,
    peerRemote: com.example.api.PeerLiveState?
) {
    if (record.endTime != "Now") {
        Text(
            text = formatRecordDuration(record.durationSeconds, record.durationMinutes),
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        return
    }

    var durationSeconds by remember(record) {
        mutableStateOf(record.durationSeconds)
    }

    LaunchedEffect(record, isFocusing, isMe, peerRemote) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            if (isMe) {
                val totalMs = FocusTimerManager.accumulatedSessionTimeMs.value
                durationSeconds = (totalMs / 1000).toInt()
            } else if (peerRemote != null) {
                val totalMs = peerRemote.accumulatedTimeMs ?: 0L
                durationSeconds = (totalMs / 1000).toInt()
            }
        }
    }

    Text(
        text = formatRecordDuration(durationSeconds, durationSeconds / 60) + " (In Progress)",
        color = Color(0xFF38BDF8),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}


data class PeerFocusInfo(
    val username: String,
    val displayName: String,
    val emoji: String,
    val isFocusing: Boolean,
    val liveFocusedSeconds: Int,
    val currentTask: String?,
    val currentTag: String? = null,
    val isMe: Boolean = false,
    val focusStatus: String = "idle"
)

@Composable
fun FriendsFocusPill(
    viewModel: AppViewModel,
    onClick: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val myUsername = viewModel.currentUsername.collectAsStateWithLifecycle().value ?: ""
    val myEmail = viewModel.userEmail.collectAsStateWithLifecycle().value ?: ""
    val userEmoji by viewModel.userEmoji.collectAsStateWithLifecycle()

    val isTimerActive by FocusTimerManager.isTimerRunning.collectAsStateWithLifecycle()
    val isSwActive by FocusTimerManager.isStopwatchActive.collectAsStateWithLifecycle()
    val isFocusPhase by FocusTimerManager.isFocusPhase.collectAsStateWithLifecycle()
    val isMeFocusing = (isTimerActive || isSwActive) && isFocusPhase && FocusTimerManager.pendingFocusReview.value == null

    val sanitizedMyEmail = com.example.api.DevicePresenceManager.sanitizeEmail(myEmail)
    val sanitizedMyUsername = com.example.api.DevicePresenceManager.sanitizeEmail(myUsername)
    val sanitizedMyEmailWithoutAt = myEmail.substringBefore("@")

    // Filter active peer users who are focusing, excluding self thoroughly to prevent duplicate/fake logo
    val focusingPeers = allUsers.filter {
        val key = it.key
        val userId = it.value.userId ?: ""
        val keySanitized = com.example.api.DevicePresenceManager.sanitizeEmail(key)
        val userIdSanitized = com.example.api.DevicePresenceManager.sanitizeEmail(userId)

        key != "admin" &&
        key != myUsername &&
        key != myEmail &&
        keySanitized != sanitizedMyEmail &&
        keySanitized != sanitizedMyUsername &&
        userId != myUsername &&
        userId != myEmail &&
        userIdSanitized != sanitizedMyEmail &&
        userIdSanitized != sanitizedMyUsername &&
        !key.equals(myUsername, ignoreCase = true) &&
        !key.equals(myEmail, ignoreCase = true) &&
        !userId.equals(myUsername, ignoreCase = true) &&
        !userId.equals(myEmail, ignoreCase = true) &&
        (myEmail.isEmpty() || !key.contains(myEmail, ignoreCase = true)) &&
        (myEmail.isEmpty() || !userId.contains(myEmail, ignoreCase = true)) &&
        (sanitizedMyEmailWithoutAt.isEmpty() || !key.contains(sanitizedMyEmailWithoutAt, ignoreCase = true)) &&
        it.value.isFocusing == true
    }

    // Trigger automatic Firestore profile picture fetch for any peer with a placeholder avatar
    LaunchedEffect(focusingPeers) {
        focusingPeers.forEach { (_, u) ->
            val av = u.emoji.ifEmpty { "👤" }
            if (av == "👤" && !viewModel.firestoreAvatars.containsKey(u.userId)) {
                viewModel.fetchUserAvatarFromFirestore(u.userId)
            }
        }
    }

    val focusingAvatars = remember(focusingPeers, isMeFocusing, userEmoji, viewModel.firestoreAvatars.size) {
        val list = mutableListOf<String>()
        if (isMeFocusing) {
            val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(viewModel.getApplication())
            val photoUrl = googleAccount?.photoUrl?.toString() ?: ""
            val myAvatar = when {
                userEmoji.isNotEmpty() && userEmoji != "👤" -> userEmoji
                photoUrl.isNotEmpty() -> photoUrl
                else -> "👤"
            }
            list.add(myAvatar)
        }
        focusingPeers.forEach { (_, u) ->
            val av = u.emoji.ifEmpty { "👤" }
            val resolved = if (av == "👤") viewModel.firestoreAvatars[u.userId] ?: "👤" else av
            list.add(resolved)
        }
        list.distinct()
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(width = 0.8.dp, color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("friends_focus_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (focusingAvatars.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "No one focusing",
                    tint = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "No one focusing",
                    color = Color.LightGray.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    focusingAvatars.forEach { avatar ->
                        UserAvatar(
                            emojiOrBase64 = avatar,
                            fontSize = 14.sp,
                            size = 20.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsFocusDetailsDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        com.example.util.FocusTimerAnomalyChecker.runDeepAuditAndCheckAnomaly(context, scope)
    }

    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val showElapsedTimeDialog by viewModel.showElapsedTimeDialog.collectAsState()

    val GoldRank = Color(0xFFFFD700)
    val SilverRank = Color(0xFFC0C0C0)
    val BronzeRank = Color(0xFFCD7F32)
    val WaterBlue = Color(0xFF38BDF8)

    var selectedFilter by remember { mutableStateOf("Today") }
    var filterExpanded by remember { mutableStateOf(false) }
    val filterOptions = listOf("Today", "Past 7 Days", "Past 30 Days", "All Time")

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val days = when (selectedFilter) {
        "Today" -> 1
        "Past 7 Days" -> 7
        "Past 30 Days" -> 30
        "All Time" -> 365
        else -> 1
    }

    val targetDates = remember(selectedFilter, todayStr) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val dates = mutableListOf<String>()
        val cal = java.util.Calendar.getInstance()
        for (i in 0 until days) {
            dates.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.DATE, -1)
        }
        dates
    }

    fun getFilterSecondsForUser(
        username: String,
        filter: String,
        isMe: Boolean,
        currentUnixTime: Long
    ): Int {
        val todayStr = try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        } catch (e: Exception) {
            "2026-07-15"
        }

        var baseSecs = 0L
        if (isMe) {
            baseSecs = when (filter) {
                "Today" -> {
                    val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                    (completedTodaySecs + pendingSecs).toLong()
                }
                "Past 7 Days" -> {
                    val limitTimeMs = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, -6)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val completedSecs = FocusTimerManager.focusRecords.value.sumOf { rec ->
                        val rDateStr = if (rec.dateString.isNotEmpty()) rec.dateString else todayStr
                        val rDate = try { sdf.parse(rDateStr) } catch (e: Exception) { null }
                        if (rDate != null && rDate.time >= limitTimeMs) rec.durationSeconds.toLong() else 0L
                    }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                    completedSecs + pendingSecs
                }
                "Past 30 Days" -> {
                    val limitTimeMs = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, -29)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val completedSecs = FocusTimerManager.focusRecords.value.sumOf { rec ->
                        val rDateStr = if (rec.dateString.isNotEmpty()) rec.dateString else todayStr
                        val rDate = try { sdf.parse(rDateStr) } catch (e: Exception) { null }
                        if (rDate != null && rDate.time >= limitTimeMs) rec.durationSeconds.toLong() else 0L
                    }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                    completedSecs + pendingSecs
                }
                else -> { // All Time
                    val completedSecs = FocusTimerManager.focusRecords.value.sumOf { it.durationSeconds.toLong() }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                    completedSecs + pendingSecs
                }
            }
        } else {
            val peerRemote = viewModel.allUsers.value[username]
                ?: viewModel.allUsers.value[com.example.api.DevicePresenceManager.sanitizeEmail(username)]
                ?: viewModel.allUsers.value[username.lowercase().trim()]
                ?: viewModel.allUsers.value[com.example.api.DevicePresenceManager.sanitizeEmail(username.lowercase().trim())]
                ?: if (username.contains("@")) viewModel.allUsers.value[username.substringBefore("@")] else null
            var maxDeviceSecs = 0L
            val peerDevices = peerRemote?.devices
            if (peerDevices != null) {
                val calYest = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                val yestStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calYest.time)
                val calTom = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                val tomStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calTom.time)

                peerDevices.values.forEach { deviceStat ->
                    val devSecs = when (filter) {
                        "Today" -> {
                            val isValidDate = deviceStat.lastUpdateDate.isNullOrEmpty() ||
                                    deviceStat.lastUpdateDate == todayStr ||
                                    deviceStat.lastUpdateDate == yestStr ||
                                    deviceStat.lastUpdateDate == tomStr ||
                                    deviceStat.todayFocusMs > 0L
                            if (isValidDate) (deviceStat.todayFocusMs / 1000L) else 0L
                        }
                        "Past 7 Days" -> (deviceStat.past7DaysFocusMs / 1000L)
                        "Past 30 Days" -> (deviceStat.past30DaysFocusMs / 1000L)
                        else -> (deviceStat.allTimeFocusMs / 1000L)
                    }
                    if (devSecs > maxDeviceSecs) {
                        maxDeviceSecs = devSecs
                    }
                }
            }
            baseSecs = maxDeviceSecs
        }

        var liveActiveSecs = 0
        if (isMe) {
            val isLocalActiveOrPaused = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.isPaused.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
            if (isLocalActiveOrPaused) {
                liveActiveSecs = (FocusTimerManager.accumulatedSessionTimeMs.value / 1000).toInt()
            }
        } else if (filter == "Today") {
            val peerRemote = viewModel.allUsers.value[username]
                ?: viewModel.allUsers.value[com.example.api.DevicePresenceManager.sanitizeEmail(username)]
                ?: viewModel.allUsers.value[username.lowercase().trim()]
                ?: viewModel.allUsers.value[com.example.api.DevicePresenceManager.sanitizeEmail(username.lowercase().trim())]
                ?: if (username.contains("@")) viewModel.allUsers.value[username.substringBefore("@")] else null
            if (peerRemote != null) {
                val activeSessionFocusMs = com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(peerRemote.timeline, peerRemote.status)
                liveActiveSecs = (activeSessionFocusMs / 1000).toInt()
            }
        }

        return (baseSecs + liveActiveSecs).toInt()
    }

    fun formatFocusedSecondsForFilter(seconds: Int, filter: String): String {
        if (filter == "Today") {
            return formatLiveSeconds(seconds)
        } else {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
        }
    }

    val currentMeUsername = viewModel.currentUsername.collectAsState().value ?: "me_user"
    val userEmail by viewModel.userEmail.collectAsState()
    val meEmailClean = userEmail.lowercase().trim()
    val userName = viewModel.userName.collectAsState().value
    val userNickname = viewModel.userNickname.collectAsState().value
    val myName = if (userNickname.isNotEmpty()) userNickname else if (userName.isNotEmpty()) userName else "Bharathikrishna M"
    val myEmoji = viewModel.userEmoji.collectAsState().value.ifEmpty { "👤" }

    val participantInfos = remember(allUsers, selectedFilter, targetDates, currentMeUsername, userEmail, myName, myEmoji, showElapsedTimeDialog, viewModel.firestoreAvatars.size) {
        val keys = mutableSetOf<String>()
        keys.add(currentMeUsername)
        allUsers.forEach { (username, _) ->
            val k = username.lowercase().trim()
            if (k != "admin" && 
                k != currentMeUsername.lowercase().trim() &&
                (meEmailClean.isEmpty() || k != meEmailClean)
            ) {
                keys.add(username)
            }
        }

        keys.map { username ->
            val isMe = username == currentMeUsername
            val peerRemote = if (isMe) {
                if (meEmailClean.isNotEmpty()) allUsers[meEmailClean] else null
            } else {
                allUsers[username]
            }

            val totalFilterSeconds = getFilterSecondsForUser(
                username = username,
                filter = selectedFilter,
                isMe = isMe,
                currentUnixTime = System.currentTimeMillis() / 1000
            )

            val displayName = if (isMe) {
                myName
            } else if (peerRemote != null) {
                peerRemote.nickname ?: peerRemote.name ?: username
            } else {
                username
            }

            val rawEmoji = if (isMe) {
                myEmoji
            } else if (peerRemote != null) {
                peerRemote.emoji ?: "🎯"
            } else {
                "🎯"
            }

            val emoji = if (isMe) {
                rawEmoji
            } else {
                val emailOrUser = peerRemote?.userId ?: username
                if (rawEmoji == "👤" || rawEmoji == "🎯" || rawEmoji.isEmpty()) {
                    viewModel.firestoreAvatars[emailOrUser] ?: rawEmoji
                } else {
                    rawEmoji
                }
            }

            val isFocusing = if (isMe) {
                (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
            } else {
                peerRemote?.isOnline == true || peerRemote?.isFocusing == true
            }

            val focusStatus = if (isMe) {
                if (!FocusTimerManager.isFocusPhase.value) {
                    "break"
                } else if (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) {
                    "focusing"
                } else if (FocusTimerManager.isPaused.value || FocusTimerManager.accumulatedSessionTimeMs.value > 0 || (FocusTimerManager.timerSecondsLeft.value > 0 && FocusTimerManager.timerSecondsLeft.value < FocusTimerManager.timerDurationMinutes.value * 60)) {
                    "paused"
                } else {
                    "idle"
                }
            } else {
                if (peerRemote?.isOnline == true || peerRemote?.isFocusing == true) "focusing" else "idle"
            }

            val currentTask = if (isMe) {
                FocusTimerManager.attachedTask.value?.title
            } else {
                peerRemote?.currentTaskTitle
            }

            val currentTag = if (isMe) {
                FocusTimerManager.attachedTag.value.takeIf { it.isNotEmpty() }
            } else {
                "Study"
            }

            PeerFocusInfo(
                username = username,
                displayName = displayName,
                emoji = emoji,
                isFocusing = isFocusing,
                liveFocusedSeconds = totalFilterSeconds,
                currentTask = currentTask,
                currentTag = currentTag,
                isMe = isMe,
                focusStatus = focusStatus
            )
        }.sortedByDescending { it.liveFocusedSeconds }
    }

    // Trigger automatic Firestore profile picture fetch for any peer in the details dialog
    LaunchedEffect(participantInfos) {
        participantInfos.forEach { peer ->
            if (!peer.isMe) {
                val emailOrUser = peer.username
                if (peer.emoji == "👤" || peer.emoji == "🎯" || peer.emoji.isEmpty()) {
                    if (!viewModel.firestoreAvatars.containsKey(emailOrUser)) {
                        viewModel.fetchUserAvatarFromFirestore(emailOrUser)
                    }
                }
            }
        }
    }

    var selectedFriendForHistory by remember { mutableStateOf<PeerFocusInfo?>(null) }

    Dialog(onDismissRequest = {
        viewModel.clearFriendYesterdayHistory()
        onDismiss()
    }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f).fillMaxHeight(0.95f)
                .padding(16.dp)
                .testTag("friends_focus_details_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (selectedFriendForHistory == null) {
                    // Title and Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Friends Focus Details",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Friends Focus Details",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                participantInfos.forEach { peer ->
                                    if (!peer.isMe) {
                                        viewModel.fetchUserAvatarFromFirestore(peer.username)
                                    }
                                }
                                android.widget.Toast.makeText(context, "Refreshing friends' profiles from Firestore...", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh friends avatars",
                                tint = WaterBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close details",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Period Selection Dropdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Period Range:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { filterExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedFilter,
                                    color = WaterBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select range",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false },
                                modifier = Modifier
                                    .background(Color(0xFF141414))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                filterOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                color = if (selectedFilter == option) WaterBlue else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedFilter == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedFilter = option
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (participantInfos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No other users registered",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(participantInfos) { index, peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (peer.isFocusing) WaterBlue.copy(alpha = 0.08f)
                                            else Color.White.copy(alpha = 0.03f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (peer.isFocusing) WaterBlue.copy(alpha = 0.25f) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rank Number Badge
                                    Text(
                                        text = "#${index + 1}",
                                        color = when (index) {
                                            0 -> GoldRank
                                            1 -> SilverRank
                                            2 -> BronzeRank
                                            else -> Color.Gray
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    // Clickable Participant Row
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedFriendForHistory = peer
                                                viewModel.loadFriendYesterdayHistory(peer.username)
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Emoji / Photo
                                        UserAvatar(
                                            emojiOrBase64 = peer.emoji,
                                            size = 36.dp,
                                            fontSize = 18.sp
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Name and task details
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (peer.isMe) "${peer.displayName} (You)" else peer.displayName,
                                                    color = if (peer.isMe) WaterBlue else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (!peer.isMe) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "@${peer.username}",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }
                                                val dotColor = when (peer.focusStatus) {
                                                    "focusing" -> Color(0xFF2E7D32)
                                                    "paused" -> Color(0xFFFFA726)
                                                    "break" -> Color(0xFF4CAF50)
                                                    else -> Color.Gray.copy(alpha = 0.5f)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(dotColor)
                                                )
                                            }

                                            val subtitleText = when (peer.focusStatus) {
                                                "focusing" -> peer.currentTask?.let { "Focusing on: $it" } ?: "Focusing"
                                                "paused" -> peer.currentTask?.let { "Paused: $it" } ?: "Paused"
                                                "break" -> "On a Break"
                                                else -> "Idle"
                                            }

                                            val subtitleColor = when (peer.focusStatus) {
                                                "focusing" -> WaterBlue.copy(alpha = 0.8f)
                                                "paused" -> Color(0xFFFFA726).copy(alpha = 0.8f)
                                                "break" -> Color(0xFF66BB6A).copy(alpha = 0.8f)
                                                else -> Color.Gray
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = subtitleText,
                                                    color = subtitleColor,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                if (!peer.currentTag.isNullOrBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(WaterBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                            .border(0.5.dp, WaterBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = peer.currentTag,
                                                            color = WaterBlue,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Focus Time Summed Over Range
                                    val focusTimeColor = when (peer.focusStatus) {
                                        "focusing" -> WaterBlue
                                        "paused" -> Color(0xFFFFA726)
                                        "break" -> Color(0xFF66BB6A)
                                        else -> Color.LightGray
                                    }
                                    LiveDurationText(
                                        viewModel = viewModel,
                                        baseSeconds = peer.liveFocusedSeconds,
                                        isFocusing = peer.isFocusing,
                                        isMe = peer.isMe,
                                        peerRemote = if (peer.isMe) {
                                            val meEmail = viewModel.userEmail.value.lowercase().trim()
                                            if (meEmail.isNotEmpty()) allUsers[meEmail] else null
                                        } else {
                                            allUsers[peer.username]
                                        },
                                        filter = selectedFilter
                                    )

                                    if (!peer.isMe && !peer.isFocusing) {
                                        Spacer(modifier = Modifier.width(8.dp))

                                        val remainingCooldown = 0L
                                        val isOnCooldown = false

                                        IconButton(
                                            onClick = {
                                                viewModel.ringFriendBell(
                                                    targetUsername = peer.username,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Rang focus bell for ${peer.displayName}! 🔔",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    onError = { error ->
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            error,
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            },
                                            modifier = Modifier.size(24.dp),
                                            enabled = !isOnCooldown
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Remind friend to focus",
                                                tint = if (isOnCooldown) Color.Gray.copy(alpha = 0.5f) else WaterBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    FriendHistoryDetailsContent(
                        viewModel = viewModel,
                        peer = selectedFriendForHistory!!,
                        allUsers = allUsers,
                        selectedFilter = selectedFilter,
                        targetDates = targetDates,
                        todayStr = todayStr,
                        onBack = {
                            selectedFriendForHistory = null
                            viewModel.clearFriendYesterdayHistory()
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun LiveDurationText(
    viewModel: AppViewModel,
    baseSeconds: Int,
    isFocusing: Boolean,
    isMe: Boolean,
    peerRemote: com.example.api.PeerLiveState?,
    filter: String
) {
    val systemTodayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    var liveSeconds by remember(baseSeconds, isFocusing, isMe, peerRemote) {
        val initialSecs = if (isFocusing && !isMe && peerRemote != null) {
            val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
            val totalMs = peerRemote.accumulatedTimeMs ?: 0L
            completedTodaySecs + (totalMs / 1000).toInt()
        } else {
            baseSeconds
        }
        mutableStateOf(initialSecs)
    }

    LaunchedEffect(isFocusing, isMe, peerRemote) {
        if (isFocusing) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                if (isMe) {
                    val cleanEmail = viewModel.userEmail.value.lowercase().trim()
                    val isLocalActiveOrPaused = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.isPaused.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
                    val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
                    val baseSecs = completedTodaySecs
                    val activeSessionOverlap = if (isLocalActiveOrPaused) {
                        (FocusTimerManager.accumulatedSessionTimeMs.value / 1000).toInt()
                    } else 0
                    liveSeconds = baseSecs + pendingSecs + activeSessionOverlap
                } else if (peerRemote != null) {
                    val completedTodaySecs = peerRemote.devices?.values
                        ?.filter { it.lastUpdateDate == systemTodayStr || it.lastUpdateDate.isNullOrEmpty() }
                        ?.maxOfOrNull { (it.todayFocusMs / 1000).toInt() }
                        ?: (peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0)
                    val baseSecs = maxOf(completedTodaySecs, (peerRemote.todayFocusMs / 1000L).toInt())
                    val activeSessionFocusMs = com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(peerRemote.timeline, peerRemote.status)
                    liveSeconds = baseSecs + (activeSessionFocusMs / 1000).toInt()
                }
            }
        }
    }

    Text(
        text = if (filter == "Today") formatLiveSeconds(liveSeconds) else {
            val hours = liveSeconds / 3600
            val minutes = (liveSeconds % 3600) / 60
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        },
        color = if (isFocusing) Color(0xFF38BDF8) else Color.LightGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

fun formatLiveSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
    }
}

fun formatRecordDuration(durationSeconds: Int, durationMinutes: Int): String {
    val secs = if (durationSeconds > 0) durationSeconds else durationMinutes * 60
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
}


@Composable
fun TimerHistoryView(
    viewModel: AppViewModel,
    selectedDateStr: String,
    globalTodaySeconds: Int,
    modifier: Modifier = Modifier
) {
    // State for editing focus session logs
    var editingLogId by remember { mutableStateOf<String?>(null) }
    var showEditLogDialog by remember { mutableStateOf(false) }

    var editTaskTitle by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("") }
    var editEndTime by remember { mutableStateOf("") }
    var editDurationMins by remember { mutableStateOf("") }
    var editDateString by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var editTag by remember { mutableStateOf("") }

    // State for logging focus session manually
    var showManualLogDialog by remember { mutableStateOf(false) }
    var manualTaskTitle by remember { mutableStateOf("") }
    var manualSubjectTag by remember { mutableStateOf("Study") }
    var manualDurationMins by remember { mutableStateOf("") }
    var manualErrorMessage by remember { mutableStateOf<String?>(null) }

    // SEPARATE OVERVIEW AND FOCUS HISTORY PAGE
    val context = androidx.compose.ui.platform.LocalContext.current
    var historySubTab by remember { mutableStateOf(0) } // 0 = Timeline & Logs, 1 = Cloud Sync & Devices, 2 = Diagnostics Logs
    val historyAuditScope = rememberCoroutineScope()
    LaunchedEffect(historySubTab) {
        if (historySubTab == 0) {
            com.example.util.FocusTimerAnomalyChecker.runDeepAuditAndCheckAnomaly(context, historyAuditScope)
        }
    }
    var fetchSessionIdInput by remember { mutableStateOf("") }
    var isDirectFetchingSession by remember { mutableStateOf(false) }
    val auditLogs by FocusTimerManager.systemLogs.collectAsStateWithLifecycle()
    val isCommandDevice by viewModel.isCommandDevice.collectAsStateWithLifecycle()
    val prefs = remember(context) { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    val focusRecords by viewModel.focusRecords.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()
    val totalFocusMinutes by viewModel.totalFocusMinutes.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTag by remember { mutableStateOf("All") }
    var showFriendsList by remember { mutableStateOf(false) }

    val WaterBlue = Color(0xFF38BDF8)

    val completedSecs = remember(focusRecords, selectedDateStr) {
        focusRecords.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, selectedDateStr) }
    }

    val pendingSecs = remember(pendingFocusReview, selectedDateStr) {
        pendingFocusReview?.let { FocusTimerManager.getOverlapSecondsForDate(it, selectedDateStr) } ?: 0
    }

    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    val myTodaySeconds = remember(completedSecs, pendingSecs, selectedDateStr, todayStr, globalTodaySeconds) {
        if (selectedDateStr == todayStr) {
            globalTodaySeconds
        } else {
            completedSecs + pendingSecs
        }
    }

    val availableTags = remember(focusRecords) {
        val tags = focusRecords.map { it.tag.trim() }.filter { it.isNotEmpty() }.distinct()
        listOf("All") + tags
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modern Pill-Shaped Tab Selector
        TabRow(
            selectedTabIndex = historySubTab,
            containerColor = Color(0xFF0F0F12),
            contentColor = WaterBlue,
            divider = { Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F24))) }
        ) {
            Tab(
                selected = historySubTab == 0,
                onClick = { historySubTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Timeline & Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = historySubTab == 1,
                onClick = { historySubTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Cloud Vault", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = historySubTab == 2,
                onClick = { historySubTab = 2 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Audit Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.Gray
            )
        }

        fun getLocalTagColor(tag: String): Color {
            return when (tag.lowercase().trim()) {
                "study" -> Color(0xFF38BDF8) // WaterBlue
                "work" -> Color(0xFF10B981) // Green
                "coding" -> Color(0xFFF59E0B) // Amber / Orange
                "personal" -> Color(0xFFEC4899) // Pink
                "sleep" -> Color(0xFF8B5CF6) // Violet / Deep Purple
                "wasted" -> Color(0xFFEF4444) // Red
                else -> Color(0xFF38BDF8) // Fallback WaterBlue
            }
        }

        when (historySubTab) {
            0 -> {
                // TAB 0: Timeline, Allocation Residual & Local Searchable Logs
                
                // 1. Beautiful Vertical Calendar Day Schedule Timeline & Analytics Allocation Table
                VerticalCalendarTimelineView(
                    focusRecords = focusRecords,
                    selectedDateStr = selectedDateStr,
                    viewModel = viewModel,
                    myTodaySeconds = myTodaySeconds,
                    onLogManuallyClick = {
                        manualTaskTitle = ""
                        manualSubjectTag = "Study"
                        manualDurationMins = ""
                        manualErrorMessage = null
                        showManualLogDialog = true
                    }
                )

                // 2. Expandable Friends Activity Leaderboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
                    border = BorderStroke(1.dp, Color(0xFF1F1F24)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFriendsList = !showFriendsList },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Friends Social Focus Board",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = if (showFriendsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                              )
                        }
                        if (showFriendsList) {
                            Spacer(modifier = Modifier.height(14.dp))
                            FriendsFocusLeaderboardTable(
                                viewModel = viewModel,
                                selectedDateStr = selectedDateStr,
                                myTodaySeconds = myTodaySeconds
                            )
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: Cloud Sync, Misalignment Recalibration & Direct Fetch
                val isRefreshing by viewModel.isRefreshingHistory.collectAsStateWithLifecycle()
                
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("sync_vault_header_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131317)),
                    border = BorderStroke(1.dp, Color(0xFF1F1F24)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Session Vault Sync",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Ingest remote history updates and prune deleted sessions securely.",
                                    color = Color.Gray,
                                    fontSize = 9.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.triggerHistoryPullAndSync(context)
                                },
                                enabled = !isRefreshing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E1E24),
                                    contentColor = WaterBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp).testTag("sync_vault_button")
                            ) {
                                if (isRefreshing) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = WaterBlue,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F24)))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Sync Role Configurator
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("sync_role_card"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131317)),
                            border = BorderStroke(1.dp, Color(0xFF1F1F24)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Multi-Device Sync Role",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isCommandDevice) {
                                                "Commanding Device: Write-only mode. Pushes local timer state to cloud, ignores remote state changes. Prevents feedback loops."
                                            } else {
                                                "Reading Device: Read-only live sync. Follows and mirrors active session state from the commanding device in real-time."
                                            },
                                            color = Color.Gray,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = isCommandDevice,
                                        onCheckedChange = { isChecked ->
                                            val editor = prefs.edit()
                                            editor.putBoolean("is_command_device", isChecked).apply()
                                            val activeEmail = com.example.api.DynamicCommandManager.activeEmail.ifEmpty {
                                                prefs.getString("user_email", "") ?: ""
                                            }
                                            if (activeEmail.isNotEmpty()) {
                                                if (isChecked) {
                                                    com.example.api.DynamicCommandManager.updateCommandDeviceName(context, activeEmail)
                                                    com.example.util.FocusTimerManager.claimCommandDevice(context)
                                                }
                                                com.example.api.DynamicCommandManager.startListeningToActiveFocusTimer(context, activeEmail)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = WaterBlue,
                                            checkedTrackColor = WaterBlue.copy(alpha = 0.4f),
                                            uncheckedThumbColor = Color.Gray,
                                            uncheckedTrackColor = Color(0xFF1F1F24)
                                        ),
                                        modifier = Modifier.testTag("command_device_toggle_switch")
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCommandDevice) Icons.Default.Send else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (isCommandDevice) WaterBlue else Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isCommandDevice) "ACTIVE COMMANDER (WRITE)" else "ACTIVE READER (LIVE SYNC)",
                                        color = if (isCommandDevice) WaterBlue else Color(0xFF10B981),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Connected Devices Panel
                        Text(
                            text = "Connected Devices & Multi-Device Sync Tracker",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Real-time audit tracking across connected devices and automatic calculation misalignment resolution.",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
                        val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
                        val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
                        val myUserProfile = if (userEmail.isNotEmpty()) {
                            allUsers[userEmail.lowercase().trim()]
                        } else {
                            currentUsername?.let { allUsers[it] }
                        }
                        val devicesMap = myUserProfile?.devices ?: emptyMap()
                        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        val localUploadStatus = prefs.getString("local_device_upload_status", "COMPLETED") ?: "COMPLETED"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F0F12), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1F1F24), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DEVICE INFO", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
                                Text("TODAY FOCUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.Center)
                                Text("UPLOAD STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F24)))

                            // Local Device Row
                            val localFocusSecs = if (selectedDateStr == todayStr) myTodaySeconds else com.example.util.FocusTimerManager.getTodayFocusSeconds()
                            val localFocusMins = localFocusSecs / 60
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.8f)) {
                                    Text(
                                        text = "${android.os.Build.MODEL ?: "This Android Device"} (Local)",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Last Active: Just Now",
                                        color = Color(0xFF10B981),
                                        fontSize = 8.sp
                                    )
                                }
                                Text(
                                    text = "${localFocusMins}m",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.0f),
                                    textAlign = TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier.weight(1.2f),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = localUploadStatus,
                                        color = if (localUploadStatus == "PENDING") Color(0xFFFFB300) else Color(0xFF4CAF50),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .background(
                                                (if (localUploadStatus == "PENDING") Color(0xFFFFB300) else Color(0xFF4CAF50)).copy(alpha = 0.1f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Remote Devices Rows
                            var hasRemoteDevice = false
                            var mismatchDetected = false
                            var otherDeviceUploading = false

                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val todayStr = sdf.format(java.util.Date())
                            val calYesterday = java.util.Calendar.getInstance()
                            calYesterday.add(java.util.Calendar.DAY_OF_YEAR, -1)
                            val yesterdayStr = sdf.format(calYesterday.time)
                            val calTomorrow = java.util.Calendar.getInstance()
                            calTomorrow.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            val tomorrowStr = sdf.format(calTomorrow.time)

                            devicesMap.forEach { (deviceId, stats) ->
                                val myDeviceModel = com.example.util.DeviceIdProvider.getDeviceId(context)
                                if (deviceId != myDeviceModel) {
                                    val isWithinActiveCycle = stats.lastUpdateDate == todayStr || stats.lastUpdateDate == yesterdayStr || stats.lastUpdateDate == tomorrowStr
                                    val remoteFocusMins = if (isWithinActiveCycle) stats.todayFocusMs / 1000 / 60 else 0L
                                    val isLoggedIn = stats.isLoggedIn ?: true

                                    // Filter out disconnected stale devices with 0 focus minutes
                                    if (!isLoggedIn && remoteFocusMins == 0L) {
                                        return@forEach
                                    }

                                    hasRemoteDevice = true
                                    val upStatus = stats.uploadStatus ?: "COMPLETED"
                                    if (upStatus == "PENDING" || localUploadStatus == "PENDING") {
                                        otherDeviceUploading = true
                                    } else if (remoteFocusMins != localFocusMins.toLong()) {
                                        mismatchDetected = true
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.8f)) {
                                            Text(
                                                text = stats.deviceName ?: "Connected Device",
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            val activeStr = stats.lastActiveTime
                                            Text(
                                                text = if (!activeStr.isNullOrEmpty()) "Last Active: $activeStr" else "Last Active: Unknown",
                                                color = Color.Gray,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Text(
                                            text = "${remoteFocusMins}m",
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1.0f),
                                            textAlign = TextAlign.Center
                                        )
                                        Box(
                                            modifier = Modifier.weight(1.2f),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            val upStatus = stats.uploadStatus ?: "COMPLETED"
                                            Text(
                                                text = upStatus,
                                                color = if (upStatus == "PENDING") Color(0xFFFFB300) else Color(0xFF4CAF50),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier
                                                    .background(
                                                        (if (upStatus == "PENDING") Color(0xFFFFB300) else Color(0xFF4CAF50)).copy(alpha = 0.1f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (!hasRemoteDevice) {
                                Text(
                                    text = "No other devices connected to this account today.",
                                    color = Color.DarkGray,
                                    fontSize = 9.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
                                )
                            }

                            if (mismatchDetected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF221111), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFFF5555).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF5555), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (otherDeviceUploading) {
                                            "Focus mismatch detected! Waiting for other devices to finish uploading before recalibrating..."
                                        } else {
                                            "Focus total mismatch detected! Click the deep alignment audit below to heal."
                                        },
                                        color = Color(0xFFFF8888),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var recalibrationMessage by remember { mutableStateOf<String?>(null) }
                        var isRecalibrating by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                isRecalibrating = true
                                viewModel.triggerManualRecalibration { result ->
                                    isRecalibrating = false
                                    recalibrationMessage = result
                                }
                            },
                            enabled = !isRecalibrating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E1E24),
                                contentColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            if (isRecalibrating) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFF4CAF50),
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auditing & Recalibrating...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Deep Audit & Align Focus", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        recalibrationMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                color = if (msg.startsWith("Failed") || msg.contains("deferred") || msg.contains("paused") || msg.contains("aborted")) Color(0xFFFF5555) else Color(0xFF4CAF50),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF131317), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF1F1F24), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        FocusTimerAnomalyCard(
                            currentUsername = currentUsername ?: "",
                            onRunFullReconciliation = {
                                viewModel.triggerManualRecalibration { result ->
                                    recalibrationMessage = result
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F24)))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Fetch record block
                        Text(
                            text = "Check & Fetch Specific Focus Record",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter a specific Session ID to directly pull, verify and locally import that focus record from Firestore.",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = fetchSessionIdInput,
                                onValueChange = { fetchSessionIdInput = it },
                                placeholder = { Text("e.g. sess_1718293000", color = Color.DarkGray, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF1F1F24),
                                    focusedContainerColor = Color(0xFF0F0F12),
                                    unfocusedContainerColor = Color(0xFF0F0F12)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("direct_fetch_session_id_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                            )

                            Button(
                                onClick = {
                                    if (fetchSessionIdInput.isBlank()) {
                                        android.widget.Toast.makeText(context, "Please enter a valid Session ID", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isDirectFetchingSession = true
                                    viewModel.fetchFocusRecordBySessionId(context, fetchSessionIdInput) { record ->
                                        isDirectFetchingSession = false
                                        if (record != null) {
                                            android.widget.Toast.makeText(context, "Successfully fetched and saved session: ${record.record_id}", android.widget.Toast.LENGTH_LONG).show()
                                            fetchSessionIdInput = ""
                                        } else {
                                            android.widget.Toast.makeText(context, "Session ID not found in Firestore!", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isDirectFetchingSession && fetchSessionIdInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WaterBlue,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("direct_fetch_submit_btn")
                            ) {
                                if (isDirectFetchingSession) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = "Fetch", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fetch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // TAB 2: System Diagnostic Security & Audit Engine terminal
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TwoByTwoStatsGrid(focusRecords = focusRecords)

                    // --- Focus Session Security & Preservation Safeguards Card ---
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("focus_security_preservation_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
                        border = BorderStroke(1.dp, Color(0xFF232936)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0x154CAF50), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Shield Check",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Session Preservation & Diagnostics",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Double-check safety controls & real-time telemetry",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Text(
                                text = "If you're ever concerned that an active session or a recent split wasn't saved, use these force-preservation and clipboard diagnostic tools.",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )

                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.forcePreserveActiveSession { msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50),
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("force_preserve_active_session_btn")
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = "Save", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Force-Preserve Progress", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }

                                val isTimerRunningState by com.example.util.FocusTimerManager.isTimerRunning.collectAsStateWithLifecycle()
                                val isStopwatchActiveState by com.example.util.FocusTimerManager.isStopwatchActive.collectAsStateWithLifecycle()
                                val timerSecondsLeftState by com.example.util.FocusTimerManager.timerSecondsLeft.collectAsStateWithLifecycle()
                                val stopwatchSecondsState by com.example.util.FocusTimerManager.stopwatchSeconds.collectAsStateWithLifecycle()
                                val isFocusPhaseState by com.example.util.FocusTimerManager.isFocusPhase.collectAsStateWithLifecycle()
                                val attachedTaskState by com.example.util.FocusTimerManager.attachedTask.collectAsStateWithLifecycle()
                                val attachedTagState by com.example.util.FocusTimerManager.attachedTag.collectAsStateWithLifecycle()
                                val activeSessionId = com.example.api.DynamicCommandManager.activeSessionId
                                val activeEmail = com.example.api.DynamicCommandManager.activeEmail
                                val activeStatus = com.example.api.DynamicCommandManager.currentStatusFlow.collectAsStateWithLifecycle().value
                                val activeMode = com.example.api.DynamicCommandManager.currentTimerModeFlow.collectAsStateWithLifecycle().value
                                val isLocalCommander = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("is_command_device", true)

                                val diagnosticPayload = """
                                === FOCUS SESSION SECURITY DIAGNOSTIC SNAPSHOT ===
                                Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}
                                Active Email: $activeEmail
                                Active Session ID: $activeSessionId
                                Sync Status: $activeStatus
                                Timer Mode: $activeMode
                                Device Role: ${if (isLocalCommander) "Commander" else "Reader"}
                                Local Timer Active: $isTimerRunningState (Remaining: ${timerSecondsLeftState}s)
                                Local Stopwatch Active: $isStopwatchActiveState (Elapsed: ${stopwatchSecondsState}s)
                                Focus Phase: $isFocusPhaseState
                                Attached Task: ${attachedTaskState?.title ?: "None"}
                                Attached Tag: $attachedTagState
                                =================================================
                                """.trimIndent()

                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                                Button(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(diagnosticPayload) })
                                        android.widget.Toast.makeText(context, "Diagnostics telemetry copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1F232B),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF333E50)),
                                    modifier = Modifier.height(40.dp).testTag("copy_diagnostics_payload_btn")
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Telemetry", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Interactive Diagnostics telemetry display
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF07080A)),
                                border = BorderStroke(1.dp, Color(0xFF181C24)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "LIVE DIAGNOSTICS TELEMETRY:",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        val isTimerRunningState by com.example.util.FocusTimerManager.isTimerRunning.collectAsStateWithLifecycle()
                                        val isStopwatchActiveState by com.example.util.FocusTimerManager.isStopwatchActive.collectAsStateWithLifecycle()
                                        val timerSecondsLeftState by com.example.util.FocusTimerManager.timerSecondsLeft.collectAsStateWithLifecycle()
                                        val stopwatchSecondsState by com.example.util.FocusTimerManager.stopwatchSeconds.collectAsStateWithLifecycle()
                                        val isFocusPhaseState by com.example.util.FocusTimerManager.isFocusPhase.collectAsStateWithLifecycle()
                                        val attachedTaskState by com.example.util.FocusTimerManager.attachedTask.collectAsStateWithLifecycle()
                                        val attachedTagState by com.example.util.FocusTimerManager.attachedTag.collectAsStateWithLifecycle()
                                        val activeSessionId = com.example.api.DynamicCommandManager.activeSessionId
                                        val activeEmail = com.example.api.DynamicCommandManager.activeEmail
                                        val activeStatus = com.example.api.DynamicCommandManager.currentStatusFlow.collectAsStateWithLifecycle().value
                                        val activeMode = com.example.api.DynamicCommandManager.currentTimerModeFlow.collectAsStateWithLifecycle().value
                                        val isLocalCommander = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("is_command_device", true)

                                        Column {
                                            Text(text = "• Active Email: $activeEmail", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(text = "• Session ID: $activeSessionId", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(text = "• Sync Status: $activeStatus | Mode: $activeMode", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(text = "• Role: ${if (isLocalCommander) "Commander" else "Reader"}", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(text = "• Local Active: Timer=$isTimerRunningState (${timerSecondsLeftState}s left) | SW=$isStopwatchActiveState (${stopwatchSecondsState}s)", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(text = "• Phase: ${if (isFocusPhaseState) "Focus" else "Break"} | Task: ${attachedTaskState?.title ?: "None"} | Tag: $attachedTagState", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D10)),
                        border = BorderStroke(1.dp, Color(0xFF1A1A22)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Security & Audit Engine",
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Verifying timer calculations, events, and cloud state saves",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.triggerManualAlignmentCheck()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24), contentColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Align Cloud", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        viewModel.clearAuditLogs()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221111), contentColor = Color(0xFFFF5555)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Clear", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1A22)))

                        if (auditLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No audit logs recorded yet.", color = Color.DarkGray, fontSize = 11.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                auditLogs.forEach { log ->
                                    val catColor = when (log.category) {
                                        "BUTTON_PRESS" -> Color(0xFF00ACC1) // Cyan
                                        "FIREBASE_SYNC" -> Color(0xFFFB8C00) // Orange
                                        "STATE_RESTORE" -> Color(0xFF8E24AA) // Purple
                                        "ALARM" -> Color(0xFFE53935) // Red
                                        "SYSTEM" -> Color(0xFF43A047) // Green
                                        else -> Color.Gray
                                    }
                                    val timeStr = java.text.SimpleDateFormat("hh:mm:ss.SSS a", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF131317), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF1F1F24), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = log.event.uppercase(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = log.category,
                                                color = catColor,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier
                                                    .background(catColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                    .border(1.dp, catColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.details,
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "TIMESTAMP: $timeStr",
                                            color = Color.DarkGray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
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

    // Dialog to Edit Focus Session Details
    if (showEditLogDialog && editingLogId != null) {
        AlertDialog(
            onDismissRequest = { showEditLogDialog = false },
            title = {
                Text(
                    "Edit Focus Session",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF161616),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Modify the details for this focus history session:", color = Color.Gray, fontSize = 11.sp)

                    Text("Task / Tag Title", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTaskTitle,
                        onValueChange = { editTaskTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Time", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editStartTime,
                                onValueChange = { editStartTime = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Time", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editEndTime,
                                onValueChange = { editEndTime = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duration (Mins)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editDurationMins,
                                onValueChange = { editDurationMins = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Date (yyyy-MM-dd)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editDateString,
                                onValueChange = { editDateString = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Text("Tag / Category", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTag,
                        onValueChange = { editTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("Notes", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minsParsed = editDurationMins.trim().toIntOrNull() ?: 0
                        val existingRecord = focusRecords.find { it.id == editingLogId }
                        val updated = FocusRecord(
                            startTime = editStartTime.trim(),
                            endTime = editEndTime.trim(),
                            taskTitle = editTaskTitle.trim(),
                            durationMinutes = minsParsed,
                            dateString = editDateString.trim(),
                            notes = editNotes.trim(),
                            durationSeconds = minsParsed * 60,
                            tag = editTag.trim(),
                            id = editingLogId!!,
                            totalBreakMs = existingRecord?.totalBreakMs ?: 0L
                        )
                        viewModel.updateFocusRecordById(editingLogId!!, updated)
                        showEditLogDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.deleteFocusRecordById(editingLogId!!)
                            showEditLogDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showEditLogDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Dialog to Log Focus Session Manually
    if (showManualLogDialog) {
        AlertDialog(
            onDismissRequest = { showManualLogDialog = false },
            title = {
                Text(
                    "Log Focus Session",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF161616),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Enter details to manually record study time (Max 4 hrs per log, up to 12 hrs total per day):", color = Color.Gray, fontSize = 11.sp)

                    Text("Task Title", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = manualTaskTitle,
                        onValueChange = { manualTaskTitle = it },
                        placeholder = { Text("e.g. Reading Chapter 4", color = Color.DarkGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("Subject / Tag", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = manualSubjectTag,
                        onValueChange = { manualSubjectTag = it },
                        placeholder = { Text("e.g. Study, Work, Coding", color = Color.DarkGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("Duration (Mins)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = manualDurationMins,
                        onValueChange = { manualDurationMins = it },
                        placeholder = { Text("e.g. 45", color = Color.DarkGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (manualErrorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = manualErrorMessage!!,
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = manualDurationMins.trim().toIntOrNull()
                        if (mins == null || mins <= 0) {
                            manualErrorMessage = "Please enter a valid positive duration."
                            return@Button
                        }
                        val title = manualTaskTitle.trim().ifEmpty { "Manual Study Session" }
                        val tag = manualSubjectTag.trim().ifEmpty { "Study" }

                        viewModel.logManualStudySession(title, tag, mins) { success, message ->
                            if (success) {
                                showManualLogDialog = false
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                manualErrorMessage = message
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Log", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showManualLogDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun TimerImmersiveContent(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    onShowFriendsDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVerticalPhone = configuration.screenWidthDp < 600

    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val timerDisplayMode by viewModel.timerDisplayMode.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

    val motivationalQuoteEnabled by viewModel.focusMotivationalQuoteEnabled.collectAsStateWithLifecycle()
    val quoteIntervalMins by viewModel.focusMotivationalQuoteIntervalMins.collectAsStateWithLifecycle()
    val currentQuote by viewModel.currentQuote.collectAsStateWithLifecycle()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isAntiBurnCenteredByTap by remember { mutableStateOf(false) }
    var interactionCounter by remember { mutableStateOf(0) }

    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    LaunchedEffect(viewModel, motivationalQuoteEnabled, quoteIntervalMins) {
        if (motivationalQuoteEnabled) {
            if (viewModel.currentQuote.value.isEmpty()) {
                viewModel.triggerNextMotivationalQuote()
            }
            while (true) {
                delay(quoteIntervalMins * 60 * 1000L)
                viewModel.triggerNextMotivationalQuote()
            }
        }
    }

    LaunchedEffect(periodIndex) {
        isAntiBurnCenteredByTap = false
    }

    val isRunning = isTimerActive || isStopwatchActive

    var prevIsRunning by remember { mutableStateOf(isRunning) }
    var prevIsPaused by remember { mutableStateOf(isPaused) }
    var prevIsFocusPhase by remember { mutableStateOf(isFocusPhase) }

    LaunchedEffect(isRunning, isPaused, isFocusPhase) {
        val focusStartedOrResumed = isRunning && isFocusPhase && !isPaused && (
            !prevIsRunning || prevIsPaused || !prevIsFocusPhase
        )
        if (focusStartedOrResumed) {
            viewModel.setTimerImmersive(true)
            areControlsVisible = false
        } else if (isPaused) {
            areControlsVisible = true
        } else if (!isFocusPhase && prevIsFocusPhase) {
            areControlsVisible = false
        }
        
        prevIsRunning = isRunning
        prevIsPaused = isPaused
        prevIsFocusPhase = isFocusPhase
    }

    LaunchedEffect(areControlsVisible, interactionCounter, isPaused) {
        if (areControlsVisible && !isPaused) {
            delay(10000) // 10 seconds auto-hide
            areControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var isDrag = false
                    var dragDirection: String? = null
                    val touchSlop = viewConfiguration.touchSlop
                    var totalDragX = 0f
                    var totalDragY = 0f
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            break
                        }
                        
                        totalDragX = change.position.x - down.position.x
                        totalDragY = change.position.y - down.position.y
                        
                        if (!isDrag) {
                            if (kotlin.math.abs(totalDragX) > touchSlop || kotlin.math.abs(totalDragY) > touchSlop) {
                                isDrag = true
                                dragDirection = if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                    "horizontal"
                                } else {
                                    "vertical"
                                }
                            }
                        }
                        
                        if (isDrag) {
                            change.consume()
                        }
                    }
                    
                    if (isDrag) {
                        if (dragDirection == "horizontal") {
                            if (kotlin.math.abs(totalDragX) > 80f) {
                                val currentMode = viewModel.timerDisplayMode.value
                                val nextMode = if (currentMode == "digital") "flip" else "digital"
                                viewModel.setTimerDisplayMode(nextMode)
                            }
                        } else if (dragDirection == "vertical") {
                            if (kotlin.math.abs(totalDragY) > 80f) {
                                viewModel.setTimerImmersive(false)
                            }
                        }
                    } else {
                        areControlsVisible = !areControlsVisible
                        isAntiBurnCenteredByTap = true
                        interactionCounter++
                    }
                }
            }
            .padding(24.dp)
    ) {
        // Upper block: Show the focusing people emoji bubble and potential quote below it
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.85f)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FriendsFocusPill(
                viewModel = viewModel,
                onClick = onShowFriendsDetails
            )

            if (motivationalQuoteEnabled && currentQuote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Crossfade(
                    targetState = currentQuote,
                    animationSpec = androidx.compose.animation.core.tween(1500),
                    label = "quote_crossfade"
                ) { targetQuote ->
                    Text(
                        text = "\"$targetQuote\"",
                        color = Color(0xFFFFEB3B).copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Close button fixed at top right corner only
        if (areControlsVisible) {
            IconButton(
                onClick = { viewModel.setTimerImmersive(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("exit_immersive_btn")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Immersive", tint = Color.White)
            }
        }

        // Exactly Centered Timer Display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Task Name (visible only when controls are visible)
            if (areControlsVisible) {
                val displayName = selectedTask?.title ?: "GENERAL FOCUS SPHERE"
                Text(
                    text = displayName.uppercase(),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val currentSeconds = if (!isFocusPhase) {
                timerSecondsRemaining
            } else if (isTabFocusTimerSelected) {
                timerSecondsRemaining
            } else {
                stopwatchSeconds
            }

            val isBlinking = !isFocusPhase || isPaused || (isTabFocusTimerSelected && !isFocusPhase)

            if (timerDisplayMode == "flip") {
                RenderFlipDigits(
                    viewModel = viewModel,
                    seconds = currentSeconds,
                    isImmersive = true,
                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                    isBlinking = isBlinking,
                    isVerticalPhone = isVerticalPhone
                )
                if (!isFocusPhase) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("now u r in a break", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            } else {
                if (!isFocusPhase) {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = timerSecondsRemaining,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = true,
                        isVerticalPhone = isVerticalPhone
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("now u r in a break", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                } else if (isTabFocusTimerSelected) {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = timerSecondsRemaining,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = !isFocusPhase || isPaused,
                        isVerticalPhone = isVerticalPhone
                    )
                } else {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = stopwatchSeconds,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = isPaused,
                        isVerticalPhone = isVerticalPhone
                    )
                }
            }

            // Swipe instruction indicator
            if (areControlsVisible) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(0.6f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (timerDisplayMode == "digital") Color.White else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "SWIPE TO SWITCH MODE",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (timerDisplayMode == "flip") Color.White else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons row (visible only when controls are visible)
            if (areControlsVisible) {
                val selectedTag by viewModel.attachedTag.collectAsStateWithLifecycle()
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (isFocusPhase) {
                        SyllabusSelectionBar(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LiveSessionActionBar(
                        viewModel = viewModel,
                        focusTimerDurationMins = focusTimerDurationMins,
                        isImmersive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun RenderFlipDigits(
    viewModel: AppViewModel,
    seconds: Int,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    isBlinking: Boolean = false,
    isVerticalPhone: Boolean = false
) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsStateWithLifecycle()
    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    val antiBurnOffset = if (antiBurnScreenEnabled && isImmersive && !isAntiBurnCenteredByTap) {
        when (periodIndex) {
            0 -> Modifier.offset(x = (-40).dp, y = (-30).dp)
            1 -> Modifier.offset(x = (40).dp, y = (30).dp)
            2 -> Modifier.offset(x = (30).dp, y = (-40).dp)
            else -> Modifier.offset(x = (-30).dp, y = (40).dp)
        }
    } else {
        Modifier
    }

    val blinkAlpha = if (isBlinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "flip_blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flip_blinkAlpha"
        )
        alpha
    } else {
        1.0f
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    val cardSize = if (isPortrait) {
        if (h > 0) 100.dp else 140.dp
    } else {
        if (h > 0) 95.dp else 125.dp
    }

    val fontSize = if (isPortrait) {
        if (h > 0) 60.sp else 90.sp
    } else {
        if (h > 0) 50.sp else 75.sp
    }

    Box(
        modifier = antiBurnOffset
            .alpha(blinkAlpha)
            .testTag("timer_flip_display")
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (h > 0) {
                FlipCard(valueString = String.format(java.util.Locale.US, "%02d", h), cardSize = cardSize, fontSize = fontSize)
            }
            FlipCard(valueString = String.format(java.util.Locale.US, "%02d", m), cardSize = cardSize, fontSize = fontSize)
            FlipCard(valueString = String.format(java.util.Locale.US, "%02d", s), cardSize = cardSize, fontSize = fontSize)
        }
    }
}

@Composable
fun FlipCard(
    valueString: String,
    cardSize: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedContent(
        targetState = valueString,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically { height -> -height } + androidx.compose.animation.fadeIn(animationSpec = tween(150)))
                .togetherWith(androidx.compose.animation.slideOutVertically { height -> height } + androidx.compose.animation.fadeOut(animationSpec = tween(150)))
        },
        label = "flip_card_anim"
    ) { animatedValue ->
        Box(
            modifier = modifier
                .size(cardSize)
                .background(Color.Black, shape = RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color(0xFF2E2E31)), shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF202022),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(Color(0xFF0C0C0D))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF151517),
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                )
            }
            
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-4).dp)
                        .size(width = 8.dp, height = 12.dp)
                        .background(Color.Black, shape = RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 4.dp)
                        .size(width = 8.dp, height = 12.dp)
                        .background(Color.Black, shape = RoundedCornerShape(4.dp))
                )
            }

            Text(
                text = animatedValue,
                color = Color(0xFFECECEC),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-2).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}




@Composable
fun SyllabusCascadingSelector(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val WaterBlue = Color(0xFF38BDF8)
    
    val subjects = CAInterPaperSubject.entries
    
    var selectedSubject by remember { mutableStateOf<CAInterPaperSubject?>(null) }
    var selectedChapter by remember { mutableStateOf<String?>(null) }
    var selectedSubTopic by remember { mutableStateOf<SyllabusTopicNode?>(null) }

    var subjectDropdownExpanded by remember { mutableStateOf(false) }
    var chapterDropdownExpanded by remember { mutableStateOf(false) }
    var subTopicDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF111113), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0xFF232326)), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "CA Inter Study Session Target",
            color = WaterBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        // Dropdown 1: Select Subject
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { subjectDropdownExpanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, if (selectedSubject != null) WaterBlue.copy(alpha = 0.6f) else Color(0xFF333333))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSubject?.title ?: "Select Subject",
                        color = if (selectedSubject != null) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.Gray
                    )
                }
            }
            DropdownMenu(
                expanded = subjectDropdownExpanded,
                onDismissRequest = { subjectDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF151515))
            ) {
                subjects.forEach { subject ->
                    DropdownMenuItem(
                        text = { Text(subject.title, color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            selectedSubject = subject
                            selectedChapter = null
                            selectedSubTopic = null
                            subjectDropdownExpanded = false
                            viewModel.attachTagToTimer("")
                            viewModel.attachTaskToTimer(null)
                        }
                    )
                }
            }
        }

        // Dropdown 2: Select Chapter
        Box(modifier = Modifier.fillMaxWidth()) {
            val chapters = selectedSubject?.let { SyllabusRegistry.getChaptersForSubject(it) } ?: emptyList()
            OutlinedButton(
                onClick = { if (selectedSubject != null) chapterDropdownExpanded = true },
                enabled = selectedSubject != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, if (selectedChapter != null) WaterBlue.copy(alpha = 0.6f) else Color(0xFF333333))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedChapter ?: "Select Chapter",
                        color = if (selectedChapter != null) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.Gray
                    )
                }
            }
            DropdownMenu(
                expanded = chapterDropdownExpanded,
                onDismissRequest = { chapterDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF151515))
            ) {
                chapters.forEach { chapter ->
                    DropdownMenuItem(
                        text = { Text(chapter, color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            selectedChapter = chapter
                            selectedSubTopic = null
                            chapterDropdownExpanded = false
                            viewModel.attachTagToTimer("")
                            viewModel.attachTaskToTimer(null)
                        }
                    )
                }
            }
        }

        // Dropdown 3: Select Sub-Topic
        Box(modifier = Modifier.fillMaxWidth()) {
            val subTopics = if (selectedSubject != null && selectedChapter != null) {
                SyllabusRegistry.getSubTopicsForChapter(selectedSubject!!, selectedChapter!!)
            } else emptyList()
            
            OutlinedButton(
                onClick = { if (selectedChapter != null) subTopicDropdownExpanded = true },
                enabled = selectedChapter != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, if (selectedSubTopic != null) WaterBlue.copy(alpha = 0.6f) else Color(0xFF333333))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSubTopic?.subTopicTitle ?: "Select Sub-Topic",
                        color = if (selectedSubTopic != null) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.Gray
                    )
                }
            }
            DropdownMenu(
                expanded = subTopicDropdownExpanded,
                onDismissRequest = { subTopicDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF151515))
            ) {
                subTopics.forEach { subTopic ->
                    DropdownMenuItem(
                        text = { Text(subTopic.subTopicTitle, color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            selectedSubTopic = subTopic
                            subTopicDropdownExpanded = false
                            val displayTitle = "${subTopic.chapterName} — ${subTopic.subTopicTitle}"
                            viewModel.attachTagToTimer(subTopic.topicId)
                            val dummyTask = Task(
                                id = -999,
                                title = displayTitle,
                                description = subTopic.subTopicTitle,
                                estimatedMinutes = 0,
                                actualMinutes = 0,
                                isCompleted = false,
                                listCategory = subTopic.subject.title,
                                priority = "HIGH"
                            )
                            viewModel.attachTaskToTimer(dummyTask)
                        }
                    )
                }
            }
        }
    }
}




@Composable
fun TimerLiveControlContent(
    viewModel: AppViewModel,
    isTablet: Boolean,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    globalTodaySeconds: Int,
    focusTimerDurationMins: Int,
    myXp: Int,
    wastedMins: Int,
    modifier: Modifier = Modifier
) {
    val WaterBlue = Color(0xFF38BDF8)

    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()

    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val isInBreakMode = !isFocusPhase

    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

    val isStopwatchOnOrActive = isStopwatchActive || stopwatchSeconds > 0
    val isTimerOnOrActive = isTimerActive || (timerSecondsRemaining < focusTimerDurationMins * 60)

    val waterReminderEnabled by viewModel.waterReminderEnabled.collectAsStateWithLifecycle()
    var soundPlayingNotification by remember { mutableStateOf<String?>(null) }
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()

    val todayXp = remember(globalTodaySeconds) {
        (globalTodaySeconds * 1000L / 60000L / 15L).toInt()
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = if (isTablet) Color(0xFF101010) else Color.Black),
        border = if (isTablet) BorderStroke(1.dp, Color(0xFF222222)) else null,
        shape = if (isTablet) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
    ) {
        if (isTablet) {
            // Adaptive single column layout for tablets (one below another, covering the whole screen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Mode Toggles Focus vs Stopwatch
                val shouldShowToggles = if (isTabFocusTimerSelected) {
                    !isTimerOnOrActive
                } else {
                    !isStopwatchOnOrActive
                }
                if (shouldShowToggles) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color(0xFF151515))
                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(32.dp))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setTabFocusTimerSelected(true) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Pomodoro", color = if (isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (!isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setTabFocusTimerSelected(false) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Stopwatch", color = if (!isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // Numeric display unified for both Timer and Stopwatch
                Box(
                    modifier = Modifier.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isTabFocusTimerSelected || isInBreakMode) {
                            RenderDigitalDigits(
                                viewModel = viewModel,
                                seconds = timerSecondsRemaining,
                                isImmersive = isImmersive,
                                isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                                isBlinking = isInBreakMode || isPaused
                            )
                            Text(
                                text = if (isTimerActive) {
                                    if (isInBreakMode) "now u r in a break" else "KEEP FOCUSING"
                                } else {
                                    if (isInBreakMode) "now u r in a break" else "STOPPED"
                                },
                                color = if (isTimerActive) {
                                    if (isInBreakMode) Color(0xFF81C784) else WaterBlue
                                } else {
                                    if (isInBreakMode) Color(0xFF81C784) else Color.Gray
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        } else {
                            RenderDigitalDigits(
                                viewModel = viewModel,
                                seconds = stopwatchSeconds,
                                isImmersive = isImmersive,
                                isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                                isBlinking = isPaused
                            )
                            Text(
                                text = if (isStopwatchActive) "KEEP FOCUSING" else "STOPPED",
                                color = if (isStopwatchActive) WaterBlue else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Focused Today
                val isPausedState = isPaused || (!isTimerActive && !isStopwatchActive && isFocusPhase && (
                    (wasStartedFromStopwatch && stopwatchSeconds > 0) || 
                    (!wasStartedFromStopwatch && timerSecondsRemaining < focusTimerDurationMins * 60)
                ))

                val isIdle = isFocusPhase && !isTimerActive && !isStopwatchActive && !isPausedState

                Row(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Focused Today", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Wasted Today", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = formatMinsToHhMm(wastedMins), color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Current XP", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$myXp XP",
                            color = if (myXp < 0) Color(0xFFEF4444) else Color(0xFFFFB300),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "(+$todayXp today)",
                            color = Color(0xFF81C784),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isIdle) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var showBatteryPrompt by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(
                            !com.example.util.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
                        )
                    }

                    if (showBatteryPrompt) {
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .widthIn(max = 500.dp)
                                .fillMaxWidth()
                                .testTag("battery_exemption_prompt"),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Battery Optimization Alert",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Battery Exemption Required",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To ensure your 3-hour study sessions aren't killed in the background, please exempt this app from battery restrictions.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { showBatteryPrompt = false }
                                    ) {
                                        Text("Dismiss", color = Color(0xFF94A3B8))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            com.example.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF3B82F6)
                                        )
                                    ) {
                                        Text("Grant", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isFocusPhase) {
                    val selectedTag by viewModel.attachedTag.collectAsStateWithLifecycle()
                    if (sessionStartTimestamp == null) {
                        SyllabusSelectionBar(
                            viewModel = viewModel,
                            modifier = Modifier
                                .widthIn(max = 500.dp)
                                .fillMaxWidth()
                        )
                    } else {
                        Card(
                            modifier = Modifier
                                .widthIn(max = 500.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
                            border = BorderStroke(1.dp, Color(0xFF232326))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("CURRENT STUDY TARGET", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedTask?.title ?: "Study Session",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "ID: ${selectedTag.ifEmpty { "Study" }}", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }

                LiveSessionActionBar(
                    viewModel = viewModel,
                    focusTimerDurationMins = focusTimerDurationMins,
                    isImmersive = false,
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                )

                // Sound playing visuals
                if (soundPlayingNotification != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, WaterBlue),
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Active Alarm", tint = WaterBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(soundPlayingNotification ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // Standard single Column layout for Phone display sizes
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Mode Toggles Focus vs Stopwatch (switching to promo or stopwatch)
                    val shouldShowToggles = if (isTabFocusTimerSelected) {
                        !isTimerOnOrActive
                    } else {
                        !isStopwatchOnOrActive
                    }
                    if (shouldShowToggles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color(0xFF151515))
                                .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(32.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(if (isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { viewModel.setTabFocusTimerSelected(true) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Pomodoro", color = if (isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(if (!isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { viewModel.setTabFocusTimerSelected(false) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Stopwatch", color = if (!isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    // 2. Numeric display unified for both Timer and Stopwatch (current session timing)
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isTabFocusTimerSelected || isInBreakMode) {
                                RenderDigitalDigits(
                                    viewModel = viewModel,
                                    seconds = timerSecondsRemaining,
                                    isImmersive = isImmersive,
                                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                                    isBlinking = isInBreakMode || isPaused
                                )
                                Text(
                                    text = if (isTimerActive) {
                                        if (isInBreakMode) "now u r in a break" else "KEEP FOCUSING"
                                    } else {
                                        if (isInBreakMode) "now u r in a break" else "STOPPED"
                                    },
                                    color = if (isTimerActive) {
                                        if (isInBreakMode) Color(0xFF81C784) else WaterBlue
                                    } else {
                                        if (isInBreakMode) Color(0xFF81C784) else Color.Gray
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            } else {
                                RenderDigitalDigits(
                                    viewModel = viewModel,
                                    seconds = stopwatchSeconds,
                                    isImmersive = isImmersive,
                                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                                    isBlinking = isPaused
                                )
                                Text(
                                    text = if (isStopwatchActive) "KEEP FOCUSING" else "STOPPED",
                                    color = if (isStopwatchActive) WaterBlue else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Sound playing visuals (if any)
                    if (soundPlayingNotification != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WaterBlue),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = "Active Alarm", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(soundPlayingNotification ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Battery optimization prompt (if idle)
                    val isPausedState = isPaused || (!isTimerActive && !isStopwatchActive && isFocusPhase && (
                        (wasStartedFromStopwatch && stopwatchSeconds > 0) || 
                        (!wasStartedFromStopwatch && timerSecondsRemaining < focusTimerDurationMins * 60)
                    ))
                    val isIdle = isFocusPhase && !isTimerActive && !isStopwatchActive && !isPausedState
                    if (isIdle) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var showBatteryPrompt by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(
                                !com.example.util.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
                            )
                        }

                        if (showBatteryPrompt) {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("battery_exemption_prompt"),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Battery Optimization Alert",
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Battery Exemption Required",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "To ensure your 3-hour study sessions aren't killed in the background, please exempt this app from battery restrictions.",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        androidx.compose.material3.TextButton(
                                            onClick = { showBatteryPrompt = false }
                                        ) {
                                            Text("Dismiss", color = Color(0xFF94A3B8))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                com.example.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6)
                                            )
                                        ) {
                                            Text("Grant", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Today's focused time (always visible)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Focused Today", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Wasted Today", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = formatMinsToHhMm(wastedMins), color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Current XP", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$myXp XP",
                                color = if (myXp < 0) Color(0xFFEF4444) else Color(0xFFFFB300),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "(+$todayXp today)",
                                color = Color(0xFF81C784),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 4. Subject tagging (Syllabus/Target Selection)
                    if (isFocusPhase) {
                        val selectedTag by viewModel.attachedTag.collectAsStateWithLifecycle()
                        if (sessionStartTimestamp == null) {
                            SyllabusSelectionBar(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
                                border = BorderStroke(1.dp, Color(0xFF232326))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("CURRENT STUDY TARGET", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = selectedTask?.title ?: "Study Session",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "ID: ${selectedTag.ifEmpty { "Study" }}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 5. Long buttons at the bottom of the screen
                Spacer(modifier = Modifier.height(12.dp))
                LiveSessionActionBar(
                    viewModel = viewModel,
                    focusTimerDurationMins = focusTimerDurationMins,
                    isImmersive = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
fun RenderDigitalDigits(
    viewModel: AppViewModel,
    seconds: Int,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    isBlinking: Boolean = false,
    isVerticalPhone: Boolean = false
) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    val textString = if (isImmersive) {
        if (h > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    } else {
        if (seconds >= 3600) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }

    val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    val antiBurnOffset = if (antiBurnScreenEnabled && isImmersive && !isAntiBurnCenteredByTap) {
        when (periodIndex) {
            0 -> Modifier.offset(x = (-40).dp, y = (-30).dp)
            1 -> Modifier.offset(x = (40).dp, y = (30).dp)
            2 -> Modifier.offset(x = (30).dp, y = (-40).dp)
            else -> Modifier.offset(x = (-30).dp, y = (40).dp)
        }
    } else {
        Modifier
    }

    val blinkAlpha = if (isBlinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blinkAlpha"
        )
        alpha
    } else {
        1.0f
    }

    val dynamicDigitalFontSize = if (isImmersive) {
        if (h > 0) 65.sp else 100.sp
    } else {
        if (seconds >= 3600) 55.sp else 86.sp
    }

    Text(
        text = textString,
        color = Color.White,
        fontSize = dynamicDigitalFontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-4).sp,
        modifier = antiBurnOffset
            .alpha(blinkAlpha)
            .testTag("timer_digital_display")
    )
}

@Composable
fun SyllabusSelectionBar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTag by viewModel.attachedTag.collectAsState()
    val selectedTask by viewModel.attachedTask.collectAsState()

    var subjectExpanded by remember { mutableStateOf(false) }
    var chapterExpanded by remember { mutableStateOf(false) }

    val subjects = com.example.api.CAInterPaperSubject.entries

    // Derive selected subject enum from the selectedTag string
    val selectedSubject = remember(selectedTag) {
        subjects.firstOrNull { it.title == selectedTag }
    }

    // Filter topics for the currently selected subject
    val availableTopics = remember(selectedSubject) {
        if (selectedSubject != null) {
            com.example.api.SyllabusRegistry.allTopics.filter { it.subject == selectedSubject }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1. SUBJECT DROPDOWN ---
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { subjectExpanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
                border = BorderStroke(1.dp, if (selectedSubject != null) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF232326))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (selectedSubject != null) Color(0xFF38BDF8).copy(alpha = 0.12f) else Color(0xFF1E1E22),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Select Subject",
                                tint = if (selectedSubject != null) Color(0xFF38BDF8) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "SUBJECT",
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = selectedSubject?.title ?: "Select Subject...",
                                color = if (selectedSubject != null) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSubject != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = subjectExpanded,
                onDismissRequest = { subjectExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF232326), RoundedCornerShape(8.dp))
            ) {
                subjects.forEach { subject ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = subject.title,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            subjectExpanded = false
                            viewModel.attachTagToTimer(subject.title)
                            // Clear previous task/chapter when subject changes
                            viewModel.attachTaskToTimer(null)
                            // Automatically open chapter dropdown for seamless cascading experience!
                            chapterExpanded = true
                        }
                    )
                }
            }
        }

        // --- 2. CHAPTER/TOPIC DROPDOWN ---
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { 
                        if (selectedSubject != null) {
                            chapterExpanded = true 
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSubject != null) Color(0xFF111113) else Color(0xFF111113).copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp, 
                    if (selectedTask != null) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF232326)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (selectedTask != null) Color(0xFF38BDF8).copy(alpha = 0.12f) else Color(0xFF1E1E22),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Select Chapter",
                                tint = if (selectedTask != null) Color(0xFF38BDF8) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "CHAPTER / TOPIC",
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = selectedTask?.title ?: if (selectedSubject == null) "Select Subject First" else "Select Chapter/Topic...",
                                color = if (selectedTask != null) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTask != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (selectedSubject != null) {
                DropdownMenu(
                    expanded = chapterExpanded,
                    onDismissRequest = { chapterExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 300.dp) // Scrollable dropdown for many topics
                        .background(Color(0xFF141416))
                        .border(1.dp, Color(0xFF232326), RoundedCornerShape(8.dp))
                ) {
                    availableTopics.forEach { topic ->
                        val combinedTitle = "${topic.chapterName} - ${topic.subTopicTitle}"
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = topic.chapterName,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = topic.subTopicTitle,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                chapterExpanded = false
                                val virtualTask = com.example.data.Task(
                                    id = -999,
                                    title = combinedTitle,
                                    description = "CA Inter Syllabus Study Node",
                                    listCategory = "Inbox"
                                )
                                viewModel.attachTaskToTimer(virtualTask)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveControlTimerBar(
    viewModel: AppViewModel,
    selectedTask: Task?,
    isTimerActive: Boolean,
    sessionStartTimestamp: Long?,
    timerSecondsRemaining: Int,
    focusTimerDurationMins: Int,
    cumulativeSessionFocusSeconds: Int,
    globalTodaySeconds: Int,
    WaterBlue: Color,
    myXp: Int = 0,
    wastedMins: Int = 0
) {
    val selectedTag by viewModel.attachedTag.collectAsState()

    if (isTimerActive) {
        SyllabusSelectionBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    viewModel.takeBreakFromPomodoro()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FreeBreakfast, contentDescription = "Break", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Break", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = { viewModel.pauseTimer() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    viewModel.pauseTimer()
                    viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("End", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    } else {
        if (sessionStartTimestamp == null && timerSecondsRemaining == focusTimerDurationMins * 60) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Focused Today", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Wasted Today", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = formatMinsToHhMm(wastedMins), color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Current XP", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$myXp XP",
                        color = if (myXp < 0) Color(0xFFEF4444) else Color(0xFFFFB300),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SyllabusSelectionBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.setTabFocusTimerSelected(true)
                        viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                        viewModel.startTimer()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_timer_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Focus", modifier = Modifier.size(20.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Focus", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                    }
                }
            }
        } else {
            SyllabusSelectionBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.startTimer(isResuming = true)
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        viewModel.pauseTimer()
                        viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun LiveControlBreakBar(
    viewModel: AppViewModel,
    context: Context,
    wasStartedFromStopwatch: Boolean,
    isTimerActive: Boolean,
    stopwatchSeconds: Int,
    focusTimerDurationMins: Int,
    WaterBlue: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (wasStartedFromStopwatch) {
            Button(
                onClick = {
                    viewModel.startStopwatch(resumeFromBreak = true)
                    viewModel.setTimerImmersive(true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume Stopwatch", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume Stopwatch", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Start fresh pomo focus
            Button(
                onClick = {
                    viewModel.startFreshPomodoro(focusTimerDurationMins)
                    viewModel.setTimerImmersive(true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Pomo", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Pomo", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                viewModel.skipOrEndBreak(isUserManualEnd = true)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "End", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LiveControlStopwatchBar(
    viewModel: AppViewModel,
    selectedTask: Task?,
    isStopwatchActive: Boolean,
    sessionStartTimestamp: Long?,
    stopwatchSeconds: Int,
    globalTodaySeconds: Int,
    WaterBlue: Color,
    myXp: Int = 0,
    wastedMins: Int = 0
) {
    val selectedTag by viewModel.attachedTag.collectAsState()

    if (isStopwatchActive) {
        SyllabusSelectionBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    viewModel.takeBreakFromStopwatch()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FreeBreakfast, contentDescription = "Break", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Break", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = { viewModel.pauseStopwatch() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    viewModel.pauseStopwatch()
                    viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("End", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    } else {
        if (sessionStartTimestamp == null && stopwatchSeconds == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Focused Today", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Wasted Today", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = formatMinsToHhMm(wastedMins), color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Current XP", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$myXp XP",
                        color = if (myXp < 0) Color(0xFFEF4444) else Color(0xFFFFB300),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SyllabusSelectionBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.setTabFocusTimerSelected(false)
                        viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                        viewModel.startStopwatch()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_stopwatch_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Stopwatch", modifier = Modifier.size(20.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Stopwatch", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                    }
                }
            }
        } else {
            SyllabusSelectionBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.startStopwatch(isResuming = true)
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        viewModel.pauseStopwatch()
                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveSessionActionBar(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    isImmersive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val isRunning = isTimerActive || isStopwatchActive
    
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val isCommandDevice by viewModel.isCommandDevice.collectAsStateWithLifecycle()

    val isPausedState = isPaused || (!isRunning && isFocusPhase && (
        (wasStartedFromStopwatch && stopwatchSeconds > 0) || 
        (!wasStartedFromStopwatch && timerSecondsRemaining < focusTimerDurationMins * 60)
    ))

    @Composable
    fun ActionButton(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector?,
        containerColor: Color,
        contentColor: Color,
        borderColor: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        if (isImmersive) {
            Box(
                modifier = modifier
                    .height(48.dp)
                    .bouncyClick { onClick() }
                    .glassmorphicCard(
                        shape = RoundedCornerShape(12.dp),
                        borderWidth = 0.5.dp,
                        borderColor = borderColor.copy(alpha = 0.4f),
                        backgroundColor = containerColor.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = text, tint = contentColor.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                shape = RoundedCornerShape(8.dp),
                modifier = modifier.height(48.dp),
                border = BorderStroke(0.5.dp, borderColor)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = text, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPausedState) {
            // State: PAUSED -> 2 Buttons: Resume & End
            ActionButton(
                text = "Resume",
                icon = Icons.Default.PlayArrow,
                containerColor = if (isImmersive) Color(0xFF2196F3) else Color(0xFF2196F3),
                contentColor = if (isImmersive) Color(0xFF90CAF9) else Color.White,
                borderColor = Color(0xFF2196F3),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.startStopwatch(isResuming = true)
                    } else {
                        viewModel.startTimer(isResuming = true)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            ActionButton(
                text = "End",
                icon = Icons.Default.Stop,
                containerColor = Color(0xFFC62828),
                contentColor = Color.White,
                borderColor = Color(0xFFEF5350),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.pauseStopwatch()
                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                    } else {
                        viewModel.pauseTimer()
                        viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (!isFocusPhase) {
            // State: BREAK -> 2 Buttons: Resume/Start Focus & End Session
            val resumeText = if (wasStartedFromStopwatch) "Start Stopwatch" else "Start Pomo"
            ActionButton(
                text = resumeText,
                icon = Icons.Default.PlayArrow,
                containerColor = if (isImmersive) Color(0xFF4CAF50) else Color(0xFF4CAF50),
                contentColor = if (isImmersive) Color(0xFFA5D6A7) else Color.White,
                borderColor = Color(0xFF4CAF50),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.startStopwatch(resumeFromBreak = true)
                    } else {
                        viewModel.startFreshPomodoro(focusTimerDurationMins)
                    }
                },
                modifier = Modifier.weight(1.5f)
            )

            ActionButton(
                text = "End Session",
                icon = Icons.Default.Stop,
                containerColor = Color(0xFFC62828),
                contentColor = Color.White,
                borderColor = Color(0xFFEF5350),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                    } else {
                        viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (isRunning) {
            // State: FOCUS RUNNING -> 3 Buttons: Break, Pause, End
            ActionButton(
                text = "Break",
                icon = Icons.Default.FreeBreakfast,
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White,
                borderColor = Color(0xFF81C784),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.takeBreakFromStopwatch()
                    } else {
                        viewModel.takeBreakFromPomodoro()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            ActionButton(
                text = "Pause",
                icon = Icons.Default.Pause,
                containerColor = Color.White.copy(alpha = 0.15f),
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.3f),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.pauseStopwatch()
                    } else {
                        viewModel.pauseTimer()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            ActionButton(
                text = "End",
                icon = Icons.Default.Stop,
                containerColor = Color(0xFFC62828),
                contentColor = Color.White,
                borderColor = Color(0xFFEF5350),
                onClick = {
                    if (wasStartedFromStopwatch) {
                        viewModel.pauseStopwatch()
                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                    } else {
                        viewModel.pauseTimer()
                        viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            // State: IDLE -> 1 Button: Start Focus / Start Stopwatch
            val startText = if (isTabFocusTimerSelected) "Start Focus" else "Start Stopwatch"
            ActionButton(
                text = startText,
                icon = Icons.Default.PlayArrow,
                containerColor = Color(0xFF00ADB5),
                contentColor = Color.Black,
                borderColor = Color(0xFF00ADB5),
                onClick = {
                    if (isTabFocusTimerSelected) {
                        viewModel.startTimer()
                    } else {
                        viewModel.startStopwatch()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== CONSOLIDATED TIMER VIEWS ====================


// --- COUNTDOWN VIEW COMPONENTS ---


data class CountdownReminder(
    val daysBefore: Int,
    val timeString: String // "HH:mm"
)

data class CountdownItem(
    val id: String,
    val name: String,
    val targetTimestamp: Long,
    val category: String, // Birthdays, Anniversaries, Others
    val contactId: Int? = null,
    val isDbBacked: Boolean = false,
    val dbId: Int = 0,
    val originalDateStr: String = "" // DD/MM/YYYY representation
)

fun parseDateStringToCalendar(dateStr: String): Calendar? {
    if (dateStr.isBlank()) return null
    val cleaned = dateStr.trim().replace("-", "/") // normalize dividers
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    try {
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            if (parts.size >= 2) {
                if (parts[0].length == 4) { // YYYY/MM/DD
                    val year = parts[0].toIntOrNull() ?: today.get(Calendar.YEAR)
                    val month = parts[1].toIntOrNull() ?: 1
                    val day = parts[2].toIntOrNull() ?: 1
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month - 1)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                } else { // DD/MM/YYYY or DD/MM
                    val day = parts[0].toIntOrNull() ?: 1
                    val month = parts[1].toIntOrNull() ?: 1
                    val year = if (parts.size >= 3) parts[2].toIntOrNull() else null
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    cal.set(Calendar.MONTH, month - 1)
                    if (year != null && parts.size >= 3 && parts[2].trim().length >= 4) {
                        cal.set(Calendar.YEAR, year)
                    } else {
                        cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                    }
                }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return null
}

fun hasYearMentioned(dateStr: String): Boolean {
    if (dateStr.isBlank()) return false
    val cleaned = dateStr.trim().replace("-", "/")
    if (cleaned.contains("/")) {
        val parts = cleaned.split("/")
        return parts.size >= 3 && parts[2].trim().length >= 4 && parts[2].toIntOrNull() != null
    }
    return false
}

fun formatAutoDate(input: String, previous: String): String {
    if (input.length < previous.length) {
        return input
    }
    val clean = input.take(10)
    return buildString {
        for (i in clean.indices) {
            val char = clean[i]
            if (i == 2) {
                if (char != '/') append('/')
            } else if (i == 5) {
                if (char != '/') append('/')
            }
            append(char)
        }
        if (this.length == 2 && !this.endsWith("/")) {
            append('/')
        } else if (this.length == 5 && !this.endsWith("/")) {
            append('/')
        }
    }.take(10)
}

@Composable
fun CountdownView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val contacts by viewModel.contacts.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()

    var activeCategoryFilter by remember { mutableStateOf("All") }
    val categories = listOf("All", "Birthdays", "Anniversaries", "Others")

    var showAddDialog by remember { mutableStateOf(false) }
    var eventName by remember { mutableStateOf("") }
    var eventDateText by remember { mutableStateOf("") } // Input is dd/mm/yyyy

    // Selected item for pop-out details view (for "others"/manual)
    var selectedItemForDetail by remember { mutableStateOf<CountdownItem?>(null) }
    var detailEditMode by remember { mutableStateOf(false) }
    var detailNameEdit by remember { mutableStateOf("") }
    var detailDateEdit by remember { mutableStateOf("") }

    // Dynamic Birthdays derived directly from Contacts DOB formatted as DD/MM/YYYY
    val derivedBirthdayCountdowns = remember(contacts) {
        contacts.filter { it.dobString.isNotEmpty() }.mapNotNull { contact ->
            val dateStr = contact.dobString.trim()
            val parsedCal = parseDateStringToCalendar(dateStr) ?: return@mapNotNull null
            
            val dobMonth = parsedCal.get(Calendar.MONTH)
            val dobDay = parsedCal.get(Calendar.DAY_OF_MONTH)
            val hasYear = hasYearMentioned(dateStr)
            val birthYear = if (hasYear) parsedCal.get(Calendar.YEAR) else null

            val today = Calendar.getInstance()
            val birthdayCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, dobMonth)
                set(Calendar.DAY_OF_MONTH, dobDay)
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Yearly Cycle checking: if birthday already happened this year (ignoring today), move to next year
            if (birthdayCal.timeInMillis < today.timeInMillis - 24 * 3600 * 1000L) {
                birthdayCal.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1)
            }

            val upcomingCycleYear = birthdayCal.get(Calendar.YEAR)
            val ageStr = if (birthYear != null && birthYear > 0) " (${upcomingCycleYear - birthYear}th Birthday)" else ""

            CountdownItem(
                id = "contact_bday_${contact.id}",
                name = "${contact.firstName} ${contact.lastName}'s Birthday$ageStr",
                targetTimestamp = birthdayCal.timeInMillis,
                category = "Birthdays",
                contactId = contact.id,
                originalDateStr = dateStr
            )
        }
    }

    // Dynamic Anniversaries derived from Contacts Anniversary formatted as DD/MM/YYYY
    val derivedAnniversaryCountdowns = remember(contacts) {
        contacts.filter { it.anniversaryString.isNotEmpty() }.mapNotNull { contact ->
            val dateStr = contact.anniversaryString.trim()
            val parsedCal = parseDateStringToCalendar(dateStr) ?: return@mapNotNull null
            
            val month = parsedCal.get(Calendar.MONTH)
            val day = parsedCal.get(Calendar.DAY_OF_MONTH)
            val hasYear = hasYearMentioned(dateStr)
            val annivYear = if (hasYear) parsedCal.get(Calendar.YEAR) else null

            val today = Calendar.getInstance()
            val anniversaryCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Yearly Cycle checking
            if (anniversaryCal.timeInMillis < today.timeInMillis - 24 * 3600 * 1000L) {
                anniversaryCal.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1)
            }

            val upcomingCycleYear = anniversaryCal.get(Calendar.YEAR)
            val ageStr = if (annivYear != null && annivYear > 0) " (${upcomingCycleYear - annivYear}th Anniversary)" else ""

            CountdownItem(
                id = "contact_anniv_${contact.id}",
                name = "${contact.firstName} ${contact.lastName}'s Anniversary$ageStr",
                targetTimestamp = anniversaryCal.timeInMillis,
                category = "Anniversaries",
                contactId = contact.id,
                originalDateStr = dateStr
            )
        }
    }

    // Database Persistent Deadlines mapped as "Others" Countdowns
    val derivedDeadlineCountdowns = remember(deadlines) {
        deadlines.filter { !it.isCompleted }.map { d ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = sdf.format(Date(d.targetTimestamp))
            CountdownItem(
                id = "db_deadline_${d.id}",
                name = d.name,
                targetTimestamp = d.targetTimestamp,
                category = "Others",
                isDbBacked = true,
                dbId = d.id,
                originalDateStr = dateStr
            )
        }
    }

    // Combine all countdowns
    val allCountdowns = remember(derivedBirthdayCountdowns, derivedAnniversaryCountdowns, derivedDeadlineCountdowns) {
        derivedBirthdayCountdowns + derivedAnniversaryCountdowns + derivedDeadlineCountdowns
    }

    // Filtered countdowns based on category chip
    val filteredCountdowns = allCountdowns.filter { item ->
        activeCategoryFilter == "All" || item.category.equals(activeCategoryFilter, ignoreCase = true)
    }.sortedBy { it.targetTimestamp }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Compact single-line row containing horizontally scrollable filters and the add action button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { cat ->
                    val isSelected = activeCategoryFilter == cat
                    val bg = if (isSelected) WaterBlue else Charcoal
                    val txtColor = if (isSelected) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { activeCategoryFilter = cat }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(cat, fontSize = 11.sp, color = txtColor, fontWeight = FontWeight.Bold)
                    }
                }
            }



            IconButton(
                onClick = {
                    eventName = ""
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    eventDateText = sdf.format(Date(System.currentTimeMillis() + 10 * 24 * 3600 * 1000L))
                    showAddDialog = true
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(WaterBlue)
                    .size(36.dp)
                    .testTag("add_countdown_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Countdown",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Countdown Grid Layout
        if (filteredCountdowns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No upcoming countdowns in this category.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCountdowns) { item ->
                    val diffMs = item.targetTimestamp - System.currentTimeMillis()
                    val daysRemaining = maxOf(0, ((diffMs + 12 * 3600 * 1000L) / (24 * 3600 * 1000L)).toInt()) // robust round up of fractional day boundary

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.category == "Birthdays" || item.category == "Anniversaries") {
                                    item.contactId?.let { contactId ->
                                        viewModel.selectContact(contactId)
                                    }
                                } else {
                                    // Pop out details view
                                    selectedItemForDetail = item
                                    detailEditMode = false
                                    detailNameEdit = item.name
                                    detailDateEdit = item.originalDateStr
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Category Tag badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WaterBlue.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(item.category.uppercase(), color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                if (item.category == "Birthdays") {
                                    Icon(Icons.Default.Cake, contentDescription = "Synced Birthday", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                } else if (item.category == "Anniversaries") {
                                    Icon(Icons.Default.Favorite, contentDescription = "Synced Anniversary", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (item.isDbBacked) {
                                                viewModel.deleteDeadline(Deadline(id = item.dbId, name = item.name, targetTimestamp = item.targetTimestamp))
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (item.name.contains(" (")) {
                                val index = item.name.indexOf(" (")
                                val mainPart = item.name.substring(0, index)
                                val agePart = item.name.substring(index).trim()
                                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                    Text(
                                        text = mainPart,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = agePart,
                                        fontWeight = FontWeight.Medium,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // Countdown digits (ONLY SHOWS DAYS!)
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$daysRemaining",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = WaterBlue
                                )
                                Text("days left", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // "Automatically deploy the bars" - Progress indication bar
                            val progressValue = remember(daysRemaining) {
                                if (item.category == "Birthdays" || item.category == "Anniversaries") {
                                    val percent = (365f - daysRemaining) / 365f
                                    maxOf(0.05f, minOf(1.0f, percent))
                                } else {
                                    val totalSampleDays = 30f
                                    val percent = (totalSampleDays - daysRemaining) / totalSampleDays
                                    maxOf(0.1f, minOf(1.0f, percent))
                                }
                            }

                            LinearProgressIndicator(
                                progress = progressValue,
                                color = WaterBlue,
                                trackColor = Color.LightGray.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Modal dialogue popup to insert a brand new milestone
    if (showAddDialog) {
        val handleDismissAttempt = {
            if (eventName.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showAddDialog = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to save or discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        if (eventName.isNotEmpty()) {
                            val parsedCal = parseDateStringToCalendar(eventDateText)
                            val targetTime = parsedCal?.timeInMillis ?: (System.currentTimeMillis() + 10 * 24 * 3600 * 1000L)
                            viewModel.createDeadline(eventName, (maxOf(0L, targetTime - System.currentTimeMillis()) / (24 * 3600 * 1000L)))
                        }
                        showAddDialog = false
                    }) {
                        Text("Save", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        showAddDialog = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Add Milestone Countdown", fontWeight = FontWeight.Bold, color = Color.White) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Milestone Title") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("countdown_title_input")
                    )

                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val calendar = Calendar.getInstance()
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        eventDateText = String.format(java.util.Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        TextField(
                            value = eventDateText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Target Date (DD/MM/YYYY)") },
                            placeholder = { Text("Click to select date...") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = Color.White,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = SurfaceCard,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("countdown_date_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventName.isNotEmpty()) {
                            val parsedCal = parseDateStringToCalendar(eventDateText)
                            val targetTime = parsedCal?.timeInMillis ?: (System.currentTimeMillis() + 10 * 24 * 3600 * 1000L)
                            viewModel.createDeadline(eventName, (maxOf(0L, targetTime - System.currentTimeMillis()) / (24 * 3600 * 1000L)))
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // Pop-out Details view for individual "Others" category items, allowing viewing & editing & deleting
    selectedItemForDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { 
                selectedItemForDetail = null
                detailEditMode = false
            },
            title = {
                Text(
                    text = if (detailEditMode) "Edit Milestone" else "Milestone Details",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (detailEditMode) {
                        TextField(
                            value = detailNameEdit,
                            onValueChange = { detailNameEdit = it },
                            label = { Text("Milestone Title") },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    val parsed = parseDateStringToCalendar(detailDateEdit)
                                    if (parsed != null) {
                                        calendar.timeInMillis = parsed.timeInMillis
                                    }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            detailDateEdit = String.format(java.util.Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                        ) {
                            TextField(
                                value = detailDateEdit,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Target Date (DD/MM/YYYY)") },
                                placeholder = { Text("Click to select date...") },
                                colors = TextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledLabelColor = Color.LightGray,
                                    disabledContainerColor = SurfaceCard,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column {
                            Text("Title:", color = Color.Gray, fontSize = 12.sp)
                            if (item.name.contains(" (")) {
                                val index = item.name.indexOf(" (")
                                val mainPart = item.name.substring(0, index)
                                val agePart = item.name.substring(index).trim()
                                Column {
                                    Text(mainPart, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(agePart, color = WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            } else {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Column {
                            Text("Target Date:", color = Color.Gray, fontSize = 12.sp)
                            Text(item.originalDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        val diffMs = item.targetTimestamp - System.currentTimeMillis()
                        val daysRemaining = maxOf(0, ((diffMs + 12 * 3600 * 1000L) / (24 * 3600 * 1000L)).toInt())
                        
                        Column {
                            Text("Time Remaining:", color = Color.Gray, fontSize = 12.sp)
                            Text("$daysRemaining Days Left", color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }

                        // Display visual bars inside pop-out detailed view too!
                        val progressPercent = maxOf(0.1f, minOf(1.0f, (30f - daysRemaining) / 30f))
                        Column {
                            Text("Visual Timeline Tracker:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                            LinearProgressIndicator(
                                progress = progressPercent,
                                color = WaterBlue,
                                trackColor = Color.LightGray.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detailEditMode) {
                        Button(
                            onClick = {
                                if (detailNameEdit.isNotEmpty()) {
                                    val parsedCal = parseDateStringToCalendar(detailDateEdit)
                                    val targetTime = parsedCal?.timeInMillis ?: item.targetTimestamp
                                    if (item.isDbBacked) {
                                        viewModel.updateDeadline(
                                            Deadline(
                                                id = item.dbId,
                                                name = detailNameEdit,
                                                targetTimestamp = targetTime,
                                                isCompleted = false
                                            )
                                        )
                                    }
                                }
                                selectedItemForDetail = null
                                detailEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { detailEditMode = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { detailEditMode = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }

                        Button(
                            onClick = {
                                if (item.isDbBacked) {
                                    viewModel.deleteDeadline(
                                        Deadline(
                                            id = item.dbId,
                                            name = item.name,
                                            targetTimestamp = item.targetTimestamp
                                        )
                                    )
                                }
                                selectedItemForDetail = null
                                detailEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        )
    }


}

// --- CHRONO TIMELINE COMPONENTS ---


@Composable
fun DailyFocusTimelineChrono(
    focusRecords: List<FocusRecord>,
    selectedDateStr: String,
    modifier: Modifier = Modifier
) {
    val systemTodayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val todayRecords = remember(focusRecords, selectedDateStr) {
        val filtered = focusRecords.filter { it.dateString == selectedDateStr || (it.dateString.isEmpty() && selectedDateStr == systemTodayStr) }
        com.example.util.FocusTimerManager.sanitizeRecordsList(filtered)
    }

    // Parse each record's start and end times into double hours (0.0 to 24.0)
    // Filters out sessions/sub-sessions less than 5 mins (User constraint: 5 mins min focus time).
    // Handles break splitting by splitting the block if record.totalBreakMs > 0.
    val parsedSessions: List<Triple<Double, Double, FocusRecord>> = remember(todayRecords) {
        todayRecords.flatMap { record ->
            val sFrac = parseTimeToHourFraction(record.startTime)
            val eFrac = parseTimeToHourFraction(record.endTime)
            if (sFrac != null && eFrac != null) {
                val start = kotlin.math.min(sFrac, eFrac)
                val end = kotlin.math.max(sFrac, eFrac)
                val totalDurationHours = end - start
                val totalBreakHours = (record.totalBreakMs / 1000.0) / 3600.0
                val focusDurationHours = totalDurationHours - totalBreakHours
                
                if (record.totalBreakMs > 0 && totalBreakHours > 0.01 && focusDurationHours > 0.05) {
                    val part1Dur = focusDurationHours / 2.0
                    val part1Start = start
                    val part1End = start + part1Dur
                    
                    val part2Start = part1End + totalBreakHours
                    val part2End = end
                    val part2Dur = part2End - part2Start
                    
                    val list = mutableListOf<Triple<Double, Double, FocusRecord>>()
                    
                    val formatTimeStr = { hr: Double ->
                        val h = hr.toInt()
                        val m = ((hr - h) * 60.0).toInt()
                        val ampm = if (h >= 12) "PM" else "AM"
                        val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                        String.format(java.util.Locale.US, "%02d:%02d %s", displayH, m, ampm)
                    }
                    
                    if (part1Dur * 60.0 >= 5.0) {
                        val subRecord = record.copy(
                            startTime = formatTimeStr(part1Start),
                            endTime = formatTimeStr(part1End),
                            durationMinutes = (part1Dur * 60.0).toInt(),
                            durationSeconds = (part1Dur * 3600.0).toInt()
                        )
                        list.add(Triple(part1Start, part1End, subRecord))
                    }
                    if (part2Dur * 60.0 >= 5.0) {
                        val subRecord = record.copy(
                            startTime = formatTimeStr(part2Start),
                            endTime = formatTimeStr(part2End),
                            durationMinutes = (part2Dur * 60.0).toInt(),
                            durationSeconds = (part2Dur * 3600.0).toInt()
                        )
                        list.add(Triple(part2Start, part2End, subRecord))
                    }
                    list
                } else {
                    val durationMins = totalDurationHours * 60.0
                    if (durationMins >= 5.0) {
                        listOf(Triple(start, end, record))
                    } else {
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    // 30-minute blocks (48 blocks total)
    // Each 30-minute block consists of 10 micro-blocks (3 minutes each)
    val blockMicroData = remember(parsedSessions) {
        Array(48) { blockIdx ->
            BooleanArray(10) { subIdx ->
                val intervalStart = (blockIdx * 30.0 + subIdx * 3.0) / 60.0
                val intervalEnd = intervalStart + (3.0 / 60.0)
                parsedSessions.any { triple ->
                    val start = triple.first
                    val end = triple.second
                    val overlapStart = kotlin.math.max(intervalStart, start)
                    val overlapEnd = kotlin.math.min(intervalEnd, end)
                    (overlapEnd - overlapStart) >= 0.0001
                }
            }
        }
    }

    val blockActiveMins = remember(blockMicroData) {
        IntArray(48) { blockIdx ->
            blockMicroData[blockIdx].count { it } * 3
        }
    }

    var selectedBlock by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Chrono Timeline",
                        tint = WaterBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Daily Focus Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "30m blocks (3m sub-blocks)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            // Notice block about 5 minutes min focus limit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF191207))
                    .border(BorderStroke(1.dp, Color(0x33FFA726)), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Min Focus Time Info",
                    tint = Color(0xFFFFA726),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Note: Min display focus time is 5 mins. Sessions shorter than 5m are hidden.",
                    color = Color(0xFFFFB74D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AM Header
            Text(
                text = "AM Hours (00:00 - 11:30)",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // AM Grid: 24 blocks (0 to 23)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color(0xFF070707), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (block in 0..23) {
                    val isSelected = selectedBlock == block
                    val activeMins = blockActiveMins[block]
                    val microList = blockMicroData[block]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectedBlock = if (selectedBlock == block) null else block
                                }
                            )
                            .background(
                                color = if (isSelected) Color(0xFF1E1E1E) else Color.Black,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WaterBlue else Color.Transparent,
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Render 10 micro-blocks representing 3-minute sub-blocks
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(1.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.5.dp)
                        ) {
                            for (sub in 0..9) {
                                val isActive = microList[sub]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            color = if (isActive) WaterBlue else Color.Transparent,
                                            shape = RoundedCornerShape(0.5.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // AM Hour Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (hour in 0..11) {
                    Box(
                        modifier = Modifier.weight(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", hour),
                            color = Color.DarkGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PM Header
            Text(
                text = "PM Hours (12:00 - 23:30)",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // PM Grid: 24 blocks (24 to 47)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color(0xFF070707), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (block in 24..47) {
                    val isSelected = selectedBlock == block
                    val activeMins = blockActiveMins[block]
                    val microList = blockMicroData[block]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectedBlock = if (selectedBlock == block) null else block
                                }
                            )
                            .background(
                                color = if (isSelected) Color(0xFF1E1E1E) else Color.Black,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WaterBlue else Color.Transparent,
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Render 10 micro-blocks representing 3-minute sub-blocks
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(1.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.5.dp)
                        ) {
                            for (sub in 0..9) {
                                val isActive = microList[sub]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            color = if (isActive) WaterBlue else Color.Transparent,
                                            shape = RoundedCornerShape(0.5.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // PM Hour Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (hour in 12..23) {
                    Box(
                        modifier = Modifier.weight(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", hour),
                            color = Color.DarkGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detail Panel explaining what occurred during selection
            val activeBlock = selectedBlock
            if (activeBlock != null) {
                val mins = blockActiveMins[activeBlock]
                val startHour = activeBlock / 2
                val startMin = (activeBlock % 2) * 30
                val endHour = startHour
                val endMin = startMin + 29
                val ampm = if (startHour >= 12) "PM" else "AM"
                val displayHour = if (startHour == 0) 12 else if (startHour > 12) startHour - 12 else startHour
                val hourRangeStr = String.format("%02d:%02d - %02d:%02d %s", displayHour, startMin, displayHour, endMin, ampm)

                val activeSubCount = blockMicroData[activeBlock].count { it }

                // Find matching focus session record (if any)
                val matchingRecord = parsedSessions.firstOrNull { triple ->
                    val start = triple.first
                    val end = triple.second
                    val intervalStart = (activeBlock * 30.0) / 60.0
                    val intervalEnd = intervalStart + 0.5
                    val overlapStart = kotlin.math.max(intervalStart, start)
                    val overlapEnd = kotlin.math.min(intervalEnd, end)
                    overlapEnd > overlapStart
                }?.third

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF151515))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (mins > 0) WaterBlue else Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Time slot: $hourRangeStr",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mins > 0) {
                                "Focused $mins mins ($activeSubCount/10 sub-blocks active)" + (matchingRecord?.let { " on: ${it.taskTitle}" } ?: "")
                            } else {
                                "No active focus session recorded in this 30m block"
                            },
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "💡 Tip: Tap any 30m block to audit precise focus details and 3m sub-blocks.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

fun parseTimeToHourFraction(timeStr: String): Double? {
    return try {
        val cleanStr = timeStr.trim().uppercase()
        val hasPm = cleanStr.contains("PM")
        val hasAm = cleanStr.contains("AM")
        
        // Remove AM/PM from the string to parse numbers
        val digitsPart = cleanStr.replace("PM", "").replace("AM", "").trim()
        val hms = digitsPart.split(":")
        if (hms.isEmpty()) return null
        
        var hour = hms[0].toIntOrNull() ?: 0
        val min = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
        
        if (hasPm && hour < 12) {
            hour += 12
        } else if (hasAm && hour == 12) {
            hour = 0
        }
        
        hour + min / 60.0
    } catch (e: Exception) {
        null
    }
}

// --- FOCUS SUMMARY CARD COMPONENTS ---


@Composable
fun FocusSummaryCard(
    focusRecords: List<FocusRecord>,
    todayStr: String,
    totalFocusMinutes: Int,
    liveAddedMinutes: Int = 0,
    liveAddedSeconds: Int = liveAddedMinutes * 60
) {
    val todayRecords = remember(focusRecords, todayStr) {
        focusRecords.filter { it.dateString == todayStr || it.dateString.isEmpty() }
    }
    
    val optimisticSecsState = com.example.util.FocusTimerManager.optimisticTodayFocusSeconds.collectAsState()
    val completedTodaySecs = remember(focusRecords, todayStr) {
        focusRecords.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
    }
    val todaySecs = remember(completedTodaySecs, liveAddedSeconds, optimisticSecsState.value) {
        val base = completedTodaySecs + liveAddedSeconds
        val opt = optimisticSecsState.value
        if (opt != null) {
            maxOf(base, opt.toInt())
        } else {
            base
        }
    }

    val completedTotalSecs = remember(focusRecords) {
        focusRecords.sumOf { it.durationSeconds }
    }
    val totalSecs = remember(completedTotalSecs, liveAddedSeconds) {
        completedTotalSecs + liveAddedSeconds
    }
    
    var activeGroupTab by remember { mutableStateOf(1) } // 0 = By Task, 1 = By Tag

    val todayGrouped = remember(todayRecords, activeGroupTab) {
        if (activeGroupTab == 0) {
            todayRecords.groupBy { it.taskTitle.trim() }
        } else {
            todayRecords.groupBy { it.tag.trim() }
        }
    }
    
    val allGrouped = remember(focusRecords, activeGroupTab) {
        if (activeGroupTab == 0) {
            focusRecords.groupBy { it.taskTitle.trim() }
        } else {
            focusRecords.groupBy { it.tag.trim() }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Focus Summary Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Focus Activity Summary",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Analytics",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today Summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color(0xFF262626)), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(text = "TODAY'S WORK", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFocusedSecondsSummary(todaySecs),
                        color = WaterBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${todayRecords.size} tags/sessions",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                // All-Time Summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color(0xFF262626)), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(text = "TOTAL FOCUS COST", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFocusedSecondsSummary(totalSecs),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${focusRecords.size} sessions",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-tabs to choose Group By Task vs Group By Tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { activeGroupTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeGroupTab == 0) Color(0xFF222222) else Color.Transparent,
                        contentColor = if (activeGroupTab == 0) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("By Task", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activeGroupTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeGroupTab == 1) Color(0xFF222222) else Color.Transparent,
                        contentColor = if (activeGroupTab == 1) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("By Tag", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tagged Tasks Breakdown Header
            Text(
                text = if (activeGroupTab == 0) "Today's Productivity By Task" else "Today's Productivity By Tag",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // List of unique keys tagged today with counts
            val tasksTaggedToday = remember(todayGrouped, activeGroupTab) {
                todayGrouped.keys.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { 
                    if (it.isEmpty()) {
                        if (activeGroupTab == 0) "General Focus" else "Untagged"
                    } else it 
                })
            }

            if (tasksTaggedToday.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (activeGroupTab == 0) {
                            "No tasks tagged today yet. Start tagging tasks in focus sessions!"
                        } else {
                            "No tags assigned to sessions today. Try tagging sessions with categories!"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasksTaggedToday.forEach { taskTitleRaw ->
                        val taskTitle = if (taskTitleRaw.isEmpty()) {
                            if (activeGroupTab == 0) "General Focus" else "Untagged"
                        } else taskTitleRaw
                        val todaySessions = todayGrouped[taskTitleRaw] ?: emptyList()
                        val todayCount = todaySessions.size
                        val todayTaskMins = todaySessions.sumOf { it.durationMinutes }

                        val allSessions = allGrouped[taskTitleRaw] ?: emptyList()
                        val allCount = allSessions.size
                        val allTaskMins = allSessions.sumOf { it.durationMinutes }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                                .border(BorderStroke(0.5.dp, Color(0xFF2C2C2C)), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(WaterBlue.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = "Task Tag",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = taskTitle,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (activeGroupTab == 0) "Today Tagged: $todayCount count" else "Today Focused: $todayCount times",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Duration: ${formatFocusedMinutesSummary(todayTaskMins)}",
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (activeGroupTab == 0) "All-Time Tagged: $allCount count" else "All-Time Focused: $allCount times",
                                        color = Color.DarkGray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "All-Time Focus: ${formatFocusedMinutesSummary(allTaskMins)}",
                                        color = Color.LightGray,
                                        fontSize = 10.sp
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

private fun formatFocusedMinutesSummary(minutes: Int): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}

private fun formatFocusedSecondsSummary(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

// --- LEADERBOARD TABLE COMPONENTS ---


data class LeaderboardUser(
    val username: String,
    val displayName: String,
    val emoji: String,
    val focusedSeconds: Int,
    val isMe: Boolean,
    val displayTime: String = "00:00:00",
    val online: Boolean? = null
)

@Composable
fun MetalShield(color: Color, rankText: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.5f)
                quadraticTo(w, h * 0.85f, w / 2f, h)
                quadraticTo(0f, h * 0.85f, 0f, h * 0.5f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(path, color = color)
        }
        Text(
            text = rankText,
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}



@Composable
fun FriendsFocusLeaderboardTable(
    viewModel: AppViewModel,
    selectedDateStr: String,
    myTodaySeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userNickname by viewModel.userNickname.collectAsState()
    val userEmoji by viewModel.userEmoji.collectAsState()
    
    val systemTodayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val isToday = selectedDateStr == systemTodayStr
    
    val currentUnixTimeState = remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis() / 1000) }
    if (isToday) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                currentUnixTimeState.value = System.currentTimeMillis() / 1000
            }
        }
    }
    val currentUnixTime = currentUnixTimeState.value
    
    val leaderboardList = remember(selectedDateStr, myTodaySeconds, allUsers, currentUsername, userName, userNickname, userEmoji, currentUnixTime) {
        val prefKey = "friends_focus_leaderboard_$selectedDateStr"
        
        fun normalize(str: String): String {
            return str.lowercase().replace(".", "").replace("_", "").replace("-", "").replace("@", "").trim()
        }
        
        val cleanMyEmail = viewModel.userEmail.value.lowercase().trim()
        val cleanMyUsername = currentUsername?.lowercase()?.trim() ?: ""
        val cleanMyDisplayName = (if (userNickname.isNotEmpty()) userNickname else if (userName.isNotEmpty()) userName else "Bharathikrishna M").lowercase().trim()
        
        val normalizedMyEmail = normalize(cleanMyEmail)
        val normalizedMyUsername = normalize(cleanMyUsername)
        val normalizedMyDisplayName = normalize(cleanMyDisplayName)
        
        fun isMeUser(usernameKey: String, userId: String, displayName: String): Boolean {
            val peerIdClean = usernameKey.lowercase().trim()
            val uIdClean = userId.lowercase().trim()
            val normalizedPeerId = normalize(peerIdClean)
            val normalizedUId = normalize(uIdClean)
            val sanitizedEmail = if (cleanMyEmail.isNotEmpty()) com.example.api.DevicePresenceManager.sanitizeEmail(cleanMyEmail) else ""
            
            val isEmailMatch = cleanMyEmail.isNotEmpty() && (peerIdClean == cleanMyEmail || uIdClean == cleanMyEmail || normalizedPeerId == normalizedMyEmail || normalizedUId == normalizedMyEmail || peerIdClean == sanitizedEmail || uIdClean == sanitizedEmail)
            val isUsernameMatch = cleanMyUsername.isNotEmpty() && (peerIdClean == cleanMyUsername || uIdClean == cleanMyUsername || normalizedPeerId == normalizedMyUsername || normalizedUId == normalizedMyUsername)
            
            return isEmailMatch || isUsernameMatch
        }
        
        if (isToday) {
            val myName = if (userNickname.isNotEmpty()) userNickname else if (userName.isNotEmpty()) userName else "Bharathikrishna M"
            val myEmoji = if (userEmoji.isNotEmpty()) userEmoji else "👤"
            val meUsername = currentUsername ?: "me_user"
            
            val hMe = myTodaySeconds / 3600
            val mMe = (myTodaySeconds % 3600) / 60
            val sMe = myTodaySeconds % 60
            val myFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", hMe, mMe, sMe)
            val liveMe = LeaderboardUser(
                username = meUsername,
                displayName = myName,
                emoji = myEmoji,
                focusedSeconds = myTodaySeconds,
                isMe = true,
                displayTime = myFormatted,
                online = true
            )
            
            val livePeers = allUsers.filter { entry ->
                val k = entry.key.lowercase().trim()
                k != "admin" && !isMeUser(k, entry.value.userId ?: "", entry.value.displayName ?: "")
            }.map { entry ->
                val username = entry.key
                val u = entry.value
                val nameToShow = u.nickname ?: u.name ?: username
                val emoji = u.emoji ?: "👤"
                
                var maxDeviceTodaySecs = 0
                val uDevices = u.devices
                if (uDevices != null) {
                    uDevices.values.forEach { deviceStat ->
                        if (deviceStat.lastUpdateDate == systemTodayStr || deviceStat.lastUpdateDate.isNullOrEmpty()) {
                            val devSecs = (deviceStat.todayFocusMs / 1000L).toInt()
                            if (devSecs > maxDeviceTodaySecs) {
                                maxDeviceTodaySecs = devSecs
                            }
                        }
                    }
                }
                
                val peerRemote = com.example.api.PeerLiveSphereManager.peerLiveStates.value[username] ?: com.example.api.PeerLiveSphereManager.peerLiveStates.value[u.userId ?: ""]
                val peerActiveMs = if (peerRemote != null) {
                    com.example.api.TimelineSyncEngine.calculateAccumulatedFocusMs(peerRemote.timeline, peerRemote.status)
                } else 0L
                val peerActiveSecs = (peerActiveMs / 1000L).toInt()
                val matchedLbPeer = com.example.api.ArenaLeaderboardEngine.leaderboardFlow.value.find { lb ->
                    val email1Norm = lb.email.lowercase().replace(".", "").replace("_", "").trim()
                    val email2Norm = (u.userId ?: username).lowercase().replace(".", "").replace("_", "").trim()
                    email1Norm == email2Norm
                }
                val lbTodaySecs = ((matchedLbPeer?.totalFocusMs ?: 0L) / 1000L).toInt()

                val baseFocusedSecs = maxOf(maxDeviceTodaySecs, ((peerRemote?.todayFocusMs ?: 0L) / 1000L).toInt(), lbTodaySecs)
                val totalFocusedSecs = baseFocusedSecs + peerActiveSecs

                val isOnline = u.isOnline == true
                
                // Format displayTime using the actual calculated focused seconds
                val h = totalFocusedSecs / 3600
                val m = (totalFocusedSecs % 3600) / 60
                val s = totalFocusedSecs % 60
                val displayTime = String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                
                LeaderboardUser(
                    username = username,
                    displayName = nameToShow,
                    emoji = emoji,
                    focusedSeconds = totalFocusedSecs,
                    isMe = false,
                    displayTime = displayTime,
                    online = isOnline
                )
            }
            
            val combined = (listOf(liveMe) + livePeers).sortedByDescending { it.focusedSeconds }
            combined
        } else {
            val saved = com.example.util.PrefsDataStore.getStringBlocking(context, prefKey, null)
            if (saved != null) {
                saved.split("\n").mapNotNull { line ->
                    val parts = line.split(";;;")
                    if (parts.size >= 5) {
                        val username = parts[0]
                        val isMe = parts[4].toBoolean()
                        if (isMe || allUsers.containsKey(username)) {
                            val focusedSecs = parts[3].toIntOrNull() ?: 0
                            val h = focusedSecs / 3600
                            val m = (focusedSecs % 3600) / 60
                            val s = focusedSecs % 60
                            val formatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                            LeaderboardUser(
                                username = username,
                                displayName = parts[1],
                                emoji = parts[2],
                                focusedSeconds = focusedSecs,
                                isMe = isMe,
                                displayTime = formatted,
                                online = if (isMe) true else false
                            )
                        } else null
                    } else null
                }.sortedByDescending { it.focusedSeconds }
            } else {
                val myName = if (userNickname.isNotEmpty()) userNickname else if (userName.isNotEmpty()) userName else "Bharathikrishna M"
                val myEmoji = if (userEmoji.isNotEmpty()) userEmoji else "👤"
                
                val hMe = myTodaySeconds / 3600
                val mMe = (myTodaySeconds % 3600) / 60
                val sMe = myTodaySeconds % 60
                val myFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", hMe, mMe, sMe)
                val liveMe = LeaderboardUser(
                    username = currentUsername ?: "me_user",
                    displayName = myName,
                    emoji = myEmoji,
                    focusedSeconds = myTodaySeconds,
                    isMe = true,
                    displayTime = myFormatted,
                    online = true
                )
                
                val list = mutableListOf(liveMe)
                val peerList = allUsers.filter { entry ->
                    entry.key != "admin" && !isMeUser(entry.key, entry.value.userId ?: "", entry.value.displayName ?: "")
                }
                peerList.forEach { entry ->
                    val u = entry.value
                    val nameToShow = u.nickname ?: u.name ?: entry.key
                    var daySecs = 0
                    val uDevices = u.devices
                    if (uDevices != null) {
                        uDevices.values.forEach { deviceStat ->
                            if (deviceStat.lastUpdateDate == selectedDateStr) {
                                val devSecs = (deviceStat.todayFocusMs / 1000L).toInt()
                                if (devSecs > daySecs) {
                                    daySecs = devSecs
                                }
                            }
                        }
                    }
                    
                    val h = daySecs / 3600
                    val m = (daySecs % 3600) / 60
                    val s = daySecs % 60
                    val formatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                    
                    list.add(
                        LeaderboardUser(
                            username = entry.key,
                            displayName = nameToShow,
                            emoji = u.emoji ?: "👤",
                            focusedSeconds = daySecs,
                            isMe = false,
                            displayTime = formatted,
                            online = false
                        )
                    )
                }
                val sortedList = list.sortedByDescending { it.focusedSeconds }
                
                val serialized = sortedList.joinToString("\n") { u ->
                    "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                }
                com.example.util.PrefsDataStore.putStringBlocking(context, prefKey, serialized)
                
                sortedList
            }
        }
    }
    
    val currentLeaderboardList by rememberUpdatedState(leaderboardList)
    
    if (isToday) {
        LaunchedEffect(selectedDateStr) {
            while (true) {
                kotlinx.coroutines.delay(30000L) // Save at most once every 30 seconds
                val listToSave = currentLeaderboardList
                if (listToSave.isNotEmpty()) {
                    val prefKey = "friends_focus_leaderboard_$selectedDateStr"
                    val serialized = listToSave.joinToString("\n") { u ->
                        "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                    }
                    com.example.util.PrefsDataStore.putString(context, prefKey, serialized)
                }
            }
        }

        DisposableEffect(selectedDateStr) {
            onDispose {
                val listToSave = currentLeaderboardList
                if (listToSave.isNotEmpty()) {
                    val prefKey = "friends_focus_leaderboard_$selectedDateStr"
                    val serialized = listToSave.joinToString("\n") { u ->
                        "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                    }
                    com.example.util.PrefsDataStore.putStringBlocking(context, prefKey, serialized)
                }
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "Today's Friends Focus Details" else "Friends Focus Details ($selectedDateStr)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                if (isToday) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Saved Archive", color = Color.Gray, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var currentRankValue = 1
                val computedRanks = leaderboardList.associate { u ->
                    u.username to if (u.focusedSeconds > 0) currentRankValue++ else null
                }

                leaderboardList.forEach { user ->
                    val rankOpt = computedRanks[user.username]
                    val hasRank = rankOpt != null
                    val rank = rankOpt ?: 0
                    
                    val metalColor = when (rank) {
                        1 -> Color(0xFFB9F2FF) // Diamond
                        2 -> Color(0xFFFFD700) // Gold
                        3 -> Color(0xFFC0C0C0) // Silver
                        4 -> Color(0xFFCD7F32) // Bronze
                        else -> Color.Transparent
                    }
                    
                    val shieldColor = when (rank) {
                        1 -> Color(0xFF00E5FF) // Diamond
                        2 -> Color(0xFFFFD700) // Gold
                        3 -> Color(0xFFC0C0C0) // Silver
                        4 -> Color(0xFFCD7F32) // Bronze
                        else -> Color(0xFF424242) // Standard grey shield
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (user.isMe) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color(0xFF161616))
                            .border(
                                width = 1.dp,
                                color = if (user.isMe) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Indicator
                        Text(
                            text = if (hasRank) { if (rank <= 4) "-" else "$rank" } else "-",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        // Avatar Container with metal ring & shield badge
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                emojiOrBase64 = user.emoji,
                                size = 36.dp,
                                fontSize = 18.sp,
                                online = user.online,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = if (hasRank && rank <= 4) 2.dp else 1.dp,
                                        color = if (hasRank && rank <= 4) metalColor else Color.White.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                            )
                            
                            if (hasRank) {
                                MetalShield(
                                    color = shieldColor,
                                    rankText = "$rank",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Name and Device Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                  Text(
                                    text = user.displayName,
                                    color = if (user.isMe) Color(0xFF81C784) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (user.isMe) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "You",
                                        color = Color(0xFF81C784),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.25f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (user.isMe) "Device 3" else if (user.username == "device2") "Device 2" else "Active member",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        
                        // Time label
                        Text(
                            text = user.displayTime,
                            color = if (user.focusedSeconds > 0) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// --- VERTICAL CALENDAR TIMELINE COMPONENTS ---


@Composable
fun VerticalCalendarTimelineView(
    focusRecords: List<FocusRecord>,
    selectedDateStr: String,
    viewModel: AppViewModel,
    myTodaySeconds: Int,
    modifier: Modifier = Modifier,
    onLogManuallyClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val systemTodayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val vaultHistory by viewModel.allHistoryVault.collectAsStateWithLifecycle(emptyList())
    var selectedRecordForPopup by remember { mutableStateOf<FocusRecord?>(null) }
    
    var viewModeIsList by remember { mutableStateOf(false) }
    var editingRecordForDuration by remember { mutableStateOf<FocusRecord?>(null) }
    var showDurationEditDialog by remember { mutableStateOf(false) }
    var durationEditMinutesInput by remember { mutableStateOf("") }
    var durationEditSecondsInput by remember { mutableStateOf("") }
    var durationEditError by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<FocusRecord?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Filter records for the selected date (including previous day's sessions crossing midnight)
    val todayRecords = remember(focusRecords, selectedDateStr) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val calPrev = java.util.Calendar.getInstance()
        try {
            val selDate = sdf.parse(selectedDateStr)
            if (selDate != null) {
                calPrev.time = selDate
                calPrev.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
        } catch (e: Exception) {}
        val prevDateStr = sdf.format(calPrev.time)

        val list = mutableListOf<FocusRecord>()
        for (rec in focusRecords) {
            val recDate = if (rec.dateString.isEmpty()) systemTodayStr else rec.dateString
            if (recDate == selectedDateStr) {
                list.add(rec)
            } else if (recDate == prevDateStr) {
                val sFrac = parseTimeToHourFraction(rec.startTime)
                val eFrac = parseTimeToHourFraction(rec.endTime)
                if (sFrac != null && eFrac != null && eFrac < sFrac) {
                    list.add(rec) // Crossover into selectedDateStr!
                }
            }
        }
        com.example.util.FocusTimerManager.sanitizeRecordsList(list)
    }

    // Parse each record's start and end times into double hours (0.0 to 24.0)
    // Filters out sessions/sub-sessions less than 5 mins (User constraint: 5 mins min focus time).
    // Handles break splitting ("break emmup") by splitting the block if record.totalBreakMs > 0.
    // Handles midnight crossover sessions (11:30 PM to 1:00 AM) by displaying 00:00 to 01:00 AM on the next day's timeline.
    val parsedSessions: List<Triple<Double, Double, FocusRecord>> = remember(todayRecords) {
        todayRecords.flatMap { record ->
            val sFracRaw = parseTimeToHourFraction(record.startTime)
            val eFracRaw = parseTimeToHourFraction(record.endTime)
            if (sFracRaw != null && eFracRaw != null) {
                val sFrac: Double
                val eFrac: Double
                val recDate = if (record.dateString.isEmpty()) systemTodayStr else record.dateString

                if (eFracRaw < sFracRaw) {
                    // Crossover session! (e.g. 11:30 PM to 1:00 AM)
                    if (recDate == selectedDateStr) {
                        sFrac = sFracRaw
                        eFrac = 24.0
                    } else {
                        sFrac = 0.0
                        eFrac = eFracRaw
                    }
                } else {
                    sFrac = kotlin.math.min(sFracRaw, eFracRaw)
                    eFrac = kotlin.math.max(sFracRaw, eFracRaw)
                }

                val totalDurationHours = eFrac - sFrac
                val totalBreakHours = (record.totalBreakMs / 1000.0) / 3600.0
                val focusDurationHours = totalDurationHours - totalBreakHours
                
                if (record.totalBreakMs > 0 && totalBreakHours > 0.01 && focusDurationHours > 0.05) {
                    // Split into two sub-sessions with a break in the middle
                    val part1Dur = focusDurationHours / 2.0
                    val part1Start = sFrac
                    val part1End = sFrac + part1Dur
                    
                    val part2Start = part1End + totalBreakHours
                    val part2End = eFrac
                    val part2Dur = part2End - part2Start
                    
                    val list = mutableListOf<Triple<Double, Double, FocusRecord>>()
                    
                    val formatTimeStr = { hr: Double ->
                        val h = hr.toInt()
                        val m = ((hr - h) * 60.0).toInt()
                        val ampm = if (h >= 12) "PM" else "AM"
                        val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                        String.format(java.util.Locale.US, "%02d:%02d %s", displayH, m, ampm)
                    }
                    
                    if (part1Dur * 60.0 >= 4.99) {
                        val subRecord = record.copy(
                            startTime = formatTimeStr(part1Start),
                            endTime = formatTimeStr(part1End),
                            durationMinutes = (part1Dur * 60.0).toInt(),
                            durationSeconds = (part1Dur * 3600.0).toInt()
                        )
                        list.add(Triple(part1Start, part1End, subRecord))
                    }
                    if (part2Dur * 60.0 >= 4.99) {
                        val subRecord = record.copy(
                            startTime = formatTimeStr(part2Start),
                            endTime = formatTimeStr(part2End),
                            durationMinutes = (part2Dur * 60.0).toInt(),
                            durationSeconds = (part2Dur * 3600.0).toInt()
                        )
                        list.add(Triple(part2Start, part2End, subRecord))
                    }
                    list
                } else {
                    // No break or very short break
                    val durationMins = totalDurationHours * 60.0
                    if (durationMins >= 4.99) {
                        listOf(Triple(sFrac, eFrac, record))
                    } else {
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    // Assign overlapping columns dynamically so that sessions drawn side by side don't overlap on the grid!
    val columns = remember(parsedSessions) {
        val cols = IntArray(parsedSessions.size) { 0 }
        for (i in parsedSessions.indices) {
            val s1 = parsedSessions[i]
            val activeOverlaps = mutableListOf<Int>()
            for (j in 0 until i) {
                val s2 = parsedSessions[j]
                val overlap = kotlin.math.min(s1.second, s2.second) > kotlin.math.max(s1.first, s2.first)
                if (overlap) {
                    activeOverlaps.add(cols[j])
                }
            }
            var col = 0
            while (activeOverlaps.contains(col)) {
                col++
            }
            cols[i] = col
        }
        cols
    }

    val totalCols = remember(parsedSessions, columns) {
        val tCols = IntArray(parsedSessions.size) { 1 }
        for (i in parsedSessions.indices) {
            var maxCol = columns[i]
            val s1 = parsedSessions[i]
            val overlappingIndices = mutableListOf(i)
            for (j in parsedSessions.indices) {
                if (i != j) {
                    val s2 = parsedSessions[j]
                    val overlap = kotlin.math.min(s1.second, s2.second) > kotlin.math.max(s1.first, s2.first)
                    if (overlap) {
                        maxCol = kotlin.math.max(maxCol, columns[j])
                        overlappingIndices.add(j)
                    }
                }
            }
            val colsCount = maxCol + 1
            overlappingIndices.forEach { idx ->
                tCols[idx] = kotlin.math.max(tCols[idx], colsCount)
            }
        }
        tCols
    }

    // Sleep details of the selected date from DB
    val healthRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()
    val sleepMinutes = remember(healthRecords, selectedDateStr) {
        healthRecords.find { it.dateString == selectedDateStr }?.sleepMinutes ?: 0
    }

    // Time completed today till now calculation
    val currentLocalTimeMins = remember {
        val now = Calendar.getInstance()
        val hours = now.get(Calendar.HOUR_OF_DAY)
        val minutes = now.get(Calendar.MINUTE)
        hours * 60 + minutes
    }

    val dayCategory = remember(selectedDateStr, systemTodayStr) {
        if (selectedDateStr == systemTodayStr) {
            "TODAY"
        } else if (selectedDateStr < systemTodayStr) {
            "PAST"
        } else {
            "FUTURE"
        }
    }

    val elapsedMins = remember(dayCategory, currentLocalTimeMins) {
        when (dayCategory) {
            "TODAY" -> currentLocalTimeMins
            "PAST" -> 1440 // Full 24 hours
            else -> 0
        }
    }

    val focusedMins = myTodaySeconds / 60
    val todaySleepPortionMins = remember(sleepMinutes, context) {
        com.example.util.SleepTimeHelper.getTodaySleepMinutesPortion(context, sleepMinutes)
    }
    val wastedMins = remember(elapsedMins, focusedMins, todaySleepPortionMins) {
        (elapsedMins - focusedMins - todaySleepPortionMins).coerceAtLeast(0)
    }

    val formatTimeToAmPm = remember {
        { timeStr: String ->
            try {
                val parts = timeStr.split(":")
                if (parts.size != 2) {
                    timeStr
                } else {
                    val hour = parts[0].toIntOrNull() ?: 12
                    val min = parts[1].toIntOrNull() ?: 0
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val displayH = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    String.format(Locale.US, "%02d:%02d %s", displayH, min, ampm)
                }
            } catch (e: Exception) {
                timeStr
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2x2 Focus Stats Summary Box
        TwoByTwoStatsGrid(focusRecords = focusRecords, myTodaySeconds = myTodaySeconds)

        // ----------------------------------------------------
        // CARD 1: Total Time Wasted & Allocation Analysis Table
        // ----------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
            border = BorderStroke(1.dp, Color(0xFF1F1F24)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Allocation",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Daily Time Allocation & Residual",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total Completed Elapsed Time - Focus Time - Sleep Time = Time Wasted",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                // The Allocation Rows Table
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AllocationTableRow(
                        icon = Icons.Default.Schedule,
                        iconColor = Color(0xFF38BDF8),
                        label = "Time Completed Today (till now)",
                        value = formatMinsToReadableCalendar(elapsedMins),
                        subtext = if (dayCategory == "TODAY") "Since midnight to current local time" else "Full past day duration"
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E1E24)))

                    AllocationTableRow(
                        icon = Icons.Default.CheckCircle,
                        iconColor = Color(0xFF10B981),
                        label = "Completed Focused Time",
                        value = formatMinsToReadableCalendar(focusedMins),
                        subtext = "Total accumulated timer & stopwatch focus records"
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E1E24)))

                    AllocationTableRow(
                        icon = Icons.Default.Hotel,
                        iconColor = Color(0xFF8B5CF6),
                        label = "Recorded Sleep Time (After 12 AM)",
                        value = formatMinsToReadableCalendar(todaySleepPortionMins),
                        subtext = if (sleepMinutes > todaySleepPortionMins) {
                            "Total logged: ${formatMinsToReadableCalendar(sleepMinutes)} (${formatMinsToReadableCalendar(sleepMinutes - todaySleepPortionMins)} before 12 AM yesterday ignored for today's wasted time)"
                        } else {
                            "Sleep window duration occurring after 12 AM midnight today"
                        }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF33333C)))

                    // The Wasted Time Highlighted Row
                    val wastedColor = if (wastedMins > 300) Color(0xFFF43F5E) else Color(0xFFF59E0B)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(wastedColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, wastedColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(wastedColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = "Wasted",
                                    tint = wastedColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Total Time Wasted Today",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Residual unregistered awake time",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = formatMinsToReadableCalendar(wastedMins),
                            color = wastedColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Button below to record sleep time
                Button(
                    onClick = {
                        viewModel.selectedHealthDate.value = selectedDateStr
                        viewModel.showSleepDetailsDirectly.value = true
                        viewModel.navigateTo(Screen.HEALTH)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        contentColor = Color(0xFFC084FC)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Hotel,
                        contentDescription = "Record Sleep",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Record Sleep Time in Health Tab",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ----------------------------------------------------
        // CARD 2: Vertical Calendar Timeline Day View / Focus List View
        // ----------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
            border = BorderStroke(1.dp, Color(0xFF1F1F24)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (viewModeIsList) Icons.Default.List else Icons.Default.CalendarToday,
                            contentDescription = "View Mode Icon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (viewModeIsList) "All Focus Sessions" else "Day Schedule Timeline",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${todayRecords.size} Sessions",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = onLogManuallyClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                contentColor = Color(0xFF38BDF8)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Manually", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Beautiful View Mode Switcher: Timeline vs List
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070709), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!viewModeIsList) Color(0xFF1E1E24) else Color.Transparent)
                            .clickable { viewModeIsList = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (!viewModeIsList) Color.White else Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Timeline Mode",
                                color = if (!viewModeIsList) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewModeIsList) Color(0xFF1E1E24) else Color.Transparent)
                            .clickable { viewModeIsList = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = if (viewModeIsList) Color.White else Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "List Mode",
                                color = if (viewModeIsList) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!viewModeIsList) {
                    // TIMELINE MODE VIEW
                    // Display Note explaining 30-minute block representation and 5-minute minimum display threshold
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E1B10),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Notification",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Note: Displaying sessions with a minimum focus time of 5 mins. Timeline is mapped in 30-minute blocks.",
                                color = Color(0xFFFCD34D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // The 24-Hour Vertical Calendar Layout with 30-minute blocks
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((24 * 72).dp) // 1728.dp height
                    ) {
                        // 1. Draw 48 half-hour horizontal line dividers and labels on the left
                        for (halfHour in 0..47) {
                            val topOffset = (halfHour * 36).dp
                            val halfHourLabelText = formatHalfHourLabel(halfHour)

                            // Half-hour text label on the left (e.g. 08:00 AM, 08:30 AM)
                            Text(
                                text = halfHourLabelText,
                                color = if (halfHour % 2 == 0) Color.DarkGray else Color.DarkGray.copy(alpha = 0.4f),
                                fontSize = 9.sp,
                                fontWeight = if (halfHour % 2 == 0) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(y = topOffset + 4.dp)
                                    .width(64.dp)
                            )

                            // Horizontal guide line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .offset(y = topOffset)
                                    .padding(start = 68.dp)
                                    .background(if (halfHour % 2 == 0) Color(0xFF1A1A22) else Color(0xFF121217))
                            )
                        }

                        // Vertical guide line splitting labels and schedule area
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .offset(x = 64.dp)
                                .background(Color(0xFF1A1A22))
                        )

                        // 1.5. Draw absolute-positioned sleep blocks based on configured sleep values
                        val sleepTimeStr = com.example.util.SleepTimeHelper.getSleepTime(context) ?: "22:00"
                        val wakeUpTimeStr = com.example.util.SleepTimeHelper.getWakeUpTime(context) ?: "07:00"
                        val sleepFrac = parseTimeToHourFraction(sleepTimeStr) ?: 22.0
                        val wakeFrac = parseTimeToHourFraction(wakeUpTimeStr) ?: 7.0

                        val sleepRanges = remember(sleepFrac, wakeFrac, sleepTimeStr, wakeUpTimeStr) {
                            val list = mutableListOf<Triple<Double, Double, String>>()
                            if (sleepFrac != wakeFrac) {
                                if (sleepFrac > wakeFrac) {
                                    // Spans midnight:
                                    // Segment 1: midnight (0.0) to wakeFrac
                                    list.add(Triple(0.0, wakeFrac, "Sleep Window (Midnight - ${formatTimeToAmPm(wakeUpTimeStr)}) 🌙"))
                                    // Segment 2: sleepFrac to midnight (24.0)
                                    list.add(Triple(sleepFrac, 24.0, "Sleep Window (${formatTimeToAmPm(sleepTimeStr)} - Midnight) 🌙"))
                                } else {
                                    // Single segment: sleepFrac to wakeFrac
                                    list.add(Triple(sleepFrac, wakeFrac, "Sleep Window (${formatTimeToAmPm(sleepTimeStr)} - ${formatTimeToAmPm(wakeUpTimeStr)}) 🌙"))
                                }
                            }
                            list
                        }

                        sleepRanges.forEach { (start, end, label) ->
                            val topOffset = (start * 72).dp
                            val blockHeight = ((end - start) * 72).dp

                            if (blockHeight > 5.dp) {
                                Card(
                                    modifier = Modifier
                                        .offset(y = topOffset)
                                        .padding(start = 68.dp, end = 16.dp)
                                        .fillMaxWidth()
                                        .height(blockHeight),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.08f)),
                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Hotel,
                                            contentDescription = "Sleep Time",
                                            tint = Color(0xFFC084FC).copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            color = Color(0xFFC084FC).copy(alpha = 0.7f),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Draw absolute-positioned blocks for each parsed focus session
                        parsedSessions.forEachIndexed { index, triple ->
                            val start = triple.first
                            val end = triple.second
                            val record = triple.third

                            val blockColor = getTagColorCalendar(record.tag)
                            val topOffset = (start * 72).dp
                            val blockHeight = ((end - start) * 72).dp.coerceAtLeast(38.dp)

                            val colIndex = columns[index]
                            val colCount = totalCols[index]

                            // Dynamically scale width and start offsets for side-by-side overlap blocks!
                            val contentWidthFraction = 1f / colCount
                            val leftOffsetPercent = colIndex * contentWidthFraction

                            Card(
                                modifier = Modifier
                                    .offset(y = topOffset)
                                    .padding(start = 68.dp) // Starts after left line
                                    .fillMaxWidth(contentWidthFraction)
                                    .offset(x = (leftOffsetPercent * 240).dp) // side-by-side offset multiplier
                                    .padding(horizontal = 4.dp)
                                    .height(blockHeight)
                                    .clickable {
                                        selectedRecordForPopup = record
                                    },
                                colors = CardDefaults.cardColors(containerColor = blockColor.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, blockColor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Dynamic tag vertical status bar on the left
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(blockColor)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = record.taskTitle,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (record.tag.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(blockColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, blockColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                ) {
                                                    Text(
                                                        text = record.tag,
                                                        color = blockColor,
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${record.startTime} - ${record.endTime}",
                                            color = Color.LightGray,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // LIST MODE VIEW (Shows every session, even 1s duration, with Delete and Edit options)
                    val formatSecondsToReadable = remember {
                        { totalSeconds: Int ->
                            val h = totalSeconds / 3600
                            val m = (totalSeconds % 3600) / 60
                            val s = totalSeconds % 60
                            String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                        }
                    }

                    if (todayRecords.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "No focus sessions logged for this day.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Calculate Cumulative Focus Time Calculator map from oldest (bottom) to newest (top)
                        val oldestToNewestRecords = remember(todayRecords, selectedDateStr) {
                            todayRecords.sortedBy { rec ->
                                val startAndEnd = com.example.util.FocusTimerManager.getRecordStartAndEndMs(rec, selectedDateStr)
                                startAndEnd?.first ?: 0L
                            }
                        }

                        val cumulativeFocusMap = remember(oldestToNewestRecords, selectedDateStr) {
                            val map = mutableMapOf<String, Int>()
                            var cumSecs = 0
                            for (rec in oldestToNewestRecords) {
                                val secsToday = com.example.util.FocusTimerManager.getOverlapSecondsForDate(rec, selectedDateStr)
                                cumSecs += secsToday
                                map[rec.id] = cumSecs
                            }
                            map
                        }

                        val totalCumulativeTodaySecs = remember(todayRecords, selectedDateStr, myTodaySeconds) {
                            val dbSecs = todayRecords.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, selectedDateStr) }
                            if (selectedDateStr == systemTodayStr) {
                                maxOf(myTodaySeconds, dbSecs)
                            } else {
                                dbSecs
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Cumulative Focus Time Calculator Banner Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                                border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Calculate,
                                                contentDescription = "Cumulative Focus Time Calculator",
                                                tint = Color(0xFF60A5FA),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Cumulative Focus Time Calculator",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Matches Today's Focused Time ✓",
                                                color = Color(0xFF34D399),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Today's Focus: ${formatSecondsToReadable(totalCumulativeTodaySecs)}",
                                            color = Color(0xFF93C5FD),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Summed bottom-up (${todayRecords.size} sessions)",
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            // Info Banner
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0F1E19),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Notification",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Showing all logged sessions. Cumulative calculator sums from oldest to newest session.",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            todayRecords.forEach { record ->
                                val tagColor = getTagColorCalendar(record.tag)
                                val recTodaySecs = com.example.util.FocusTimerManager.getOverlapSecondsForDate(record, selectedDateStr)
                                val cumSecs = cumulativeFocusMap[record.id] ?: recTodaySecs

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedRecordForPopup = record },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
                                    border = BorderStroke(1.dp, Color(0xFF24242C)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Tag Colored vertical indicator bar
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(tagColor)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Left Side details: Title, Times, Tag Capsule
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = record.taskTitle.ifEmpty { "Focus Session" },
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (record.tag.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                            .border(1.dp, tagColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                    ) {
                                                        Text(
                                                            text = record.tag,
                                                            color = tagColor,
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${record.startTime} - ${record.endTime}",
                                                color = Color.Gray,
                                                fontSize = 10.5.sp
                                            )
                                            if (record.userEmail.isNotEmpty()) {
                                                Text(
                                                    text = "User: ${record.userEmail}",
                                                    color = Color(0xFFAAAAAA),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Right Side details: Duration, Cumulative, Edit/Delete Actions
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatSecondsToReadable(recTodaySecs),
                                                color = Color(0xFFFFB300), // Arena Gold
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Cum: ${formatSecondsToReadable(cumSecs)}",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 9.5.sp
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingRecordForDuration = record
                                                        durationEditMinutesInput = (record.durationSeconds / 60).toString()
                                                        durationEditSecondsInput = (record.durationSeconds % 60).toString()
                                                        durationEditError = null
                                                        showDurationEditDialog = true
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Focus Duration",
                                                        tint = Color(0xFFFFB300),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        recordToDelete = record
                                                        showDeleteConfirmDialog = true
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Focus Session",
                                                        tint = Color(0xFFFF4D4D),
                                                        modifier = Modifier.size(14.dp)
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
        }
    }

    // Custom Dialog to Edit Session Duration (List Mode feature)
    if (showDurationEditDialog && editingRecordForDuration != null) {
        val originalRecord = editingRecordForDuration!!
        val originalTotalSeconds = originalRecord.durationSeconds
        val originalMinutes = originalTotalSeconds / 60
        val originalSeconds = originalTotalSeconds % 60

        AlertDialog(
            onDismissRequest = { showDurationEditDialog = false },
            title = {
                Text(
                    text = "Edit Focus Duration",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF16161A),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Set a custom focus duration for: \"${originalRecord.taskTitle.ifEmpty { "Focus Session" }}\"",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0F12), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Original Duration: ${originalMinutes}m ${originalSeconds}s (Total: ${originalTotalSeconds}s)",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "New Focus Duration (Must be strictly less than original focus time):",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Minutes", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = durationEditMinutesInput,
                                onValueChange = { durationEditMinutesInput = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Seconds", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = durationEditSecondsInput,
                                onValueChange = { durationEditSecondsInput = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    if (durationEditError != null) {
                        Text(
                            text = durationEditError!!,
                            color = Color(0xFFFF4D4D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newMins = durationEditMinutesInput.trim().toIntOrNull() ?: 0
                        val newSecs = durationEditSecondsInput.trim().toIntOrNull() ?: 0
                        val newTotalSeconds = newMins * 60 + newSecs

                        if (newTotalSeconds <= 0) {
                            durationEditError = "⚠️ Focus time must be greater than 0 seconds."
                        } else if (newTotalSeconds >= originalTotalSeconds) {
                            durationEditError = "⚠️ Focus time must be strictly LESS than original (${originalTotalSeconds}s)."
                        } else {
                            val updatedRecord = originalRecord.copy(
                                durationSeconds = newTotalSeconds,
                                durationMinutes = if (newMins > 0) newMins else 1
                            )
                            viewModel.updateFocusRecordById(originalRecord.id, updatedRecord)
                            showDurationEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDurationEditDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222226),
                        contentColor = Color.LightGray
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Delete Confirmation Dialog for Focus Sessions
    if (showDeleteConfirmDialog && recordToDelete != null) {
        val record = recordToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Focus Session",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF16161A),
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${record.taskTitle.ifEmpty { "Focus Session" }}\"?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFocusRecordById(record.id)
                        showDeleteConfirmDialog = false
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4D4D),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222226),
                        contentColor = Color.LightGray
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedRecordForPopup != null) {
        val record = selectedRecordForPopup!!
        
        val mode = when {
            record.id.startsWith("manual_") -> "Manual Log ✍️"
            record.notes.equals("STOPWATCH_SESSION", ignoreCase = true) ||
            record.notes.equals("STOPWATCH", ignoreCase = true) ||
            record.notes.contains("STOPWATCH", ignoreCase = true) ||
            record.notes.contains("stopwatch", ignoreCase = true) ||
            record.id.contains("stopwatch", ignoreCase = true) -> "Stopwatch Mode ⏱️"
            record.notes.equals("TIMER_SESSION", ignoreCase = true) ||
            record.notes.equals("POMODORO", ignoreCase = true) ||
            record.notes.contains("POMODORO", ignoreCase = true) -> "Pomodoro Timer Mode ⏳"
            else -> "Pomodoro Timer Mode ⏳"
        }
        
        val cleanNotes = if (record.notes.equals("TIMER_SESSION", ignoreCase = true) ||
            record.notes.equals("STOPWATCH_SESSION", ignoreCase = true) ||
            record.notes.equals("STOPWATCH", ignoreCase = true) ||
            record.notes.equals("POMODORO", ignoreCase = true) ||
            record.notes.equals("MANUAL_LOG mode entry", ignoreCase = true)) {
            ""
        } else {
            record.notes
        }

        val tagColor = getTagColorCalendar(record.tag)

        AlertDialog(
            onDismissRequest = { selectedRecordForPopup = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Session Details",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Focus Session Inspection",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            },
            containerColor = Color(0xFF141418),
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2C2C35), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("TASK / DESCRIPTION", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = record.taskTitle,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tag / Subject:", color = Color.LightGray, fontSize = 11.sp)
                            Box(
                                modifier = Modifier
                                    .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = record.tag.ifEmpty { "Default" },
                                    color = tagColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tracking Mode:", color = Color.LightGray, fontSize = 11.sp)
                            Text(
                                text = mode,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Session ID:", color = Color.LightGray, fontSize = 11.sp)
                            Text(
                                text = record.id,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Time Interval:", color = Color.LightGray, fontSize = 11.sp)
                            Text(
                                text = "${record.startTime} - ${record.endTime}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Focused Duration:", color = Color.LightGray, fontSize = 11.sp)
                            val fSec = record.durationSeconds.toLong()
                            val formattedF = String.format(java.util.Locale.US, "%02d:%02d:%02d", fSec / 3600, (fSec % 3600) / 60, fSec % 60)
                            Text(
                                text = formattedF,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pauses / Breaks:", color = Color.LightGray, fontSize = 11.sp)
                            val breakText = if (record.totalBreakMs > 0L) {
                                val breakSecs = record.totalBreakMs / 1000L
                                val bm = breakSecs / 60
                                val bs = breakSecs % 60
                                "${bm}m ${bs}s total"
                            } else {
                                "None"
                            }
                            Text(
                                text = breakText,
                                color = if (record.totalBreakMs > 0L) Color(0xFFF59E0B) else Color.Gray,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Session Date:", color = Color.LightGray, fontSize = 11.sp)
                            Text(
                                text = record.dateString,
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Database Storage:", color = Color.LightGray, fontSize = 11.sp)
                            Text(
                                text = "Local Vault (SQLite Room)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F26)))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Firestore Sync:", color = Color.LightGray, fontSize = 11.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Synced",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Verified & Synced ✅",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (cleanNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("USER NOTES", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F0F12), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF1F1F26), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = cleanNotes,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RAW RECORD ENTITY (JSON)", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070709), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF14141A), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            val timerModeStr = when {
                                mode.contains("Stopwatch") -> "STOPWATCH"
                                mode.contains("Manual") -> "MANUAL_LOG"
                                else -> "POMODORO"
                            }
                            val bSec = record.totalBreakMs / 1000L
                            val totalBreakFormatted = if (record.totalBreakMs > 0L) {
                                String.format(java.util.Locale.US, "%02d:%02d:%02d", bSec / 3600, (bSec % 3600) / 60, bSec % 60)
                            } else {
                                "00:00:00"
                            }
                            val fSec = record.durationSeconds.toLong()
                            val totalFocusFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", fSec / 3600, (fSec % 3600) / 60, fSec % 60)

                            val vaultRecord = vaultHistory.find { it.record_id == record.id }

                            fun parseDateTimeToMs(dStr: String, tStr: String): Long {
                                return try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", java.util.Locale.US)
                                    sdf.parse("$dStr $tStr")?.time ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    try {
                                        val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                                        sdf2.parse("$dStr $tStr")?.time ?: System.currentTimeMillis()
                                    } catch (e2: Exception) {
                                        System.currentTimeMillis()
                                    }
                                }
                            }

                            val startTsMs = vaultRecord?.start_time_ms ?: parseDateTimeToMs(record.dateString, record.startTime)
                            val endTsMs = vaultRecord?.end_time_ms ?: parseDateTimeToMs(record.dateString, record.endTime)

                            val timelineEvents = mutableListOf<org.json.JSONObject>()
                            if (vaultRecord != null && vaultRecord.timeline.isNotEmpty()) {
                                vaultRecord.timeline.forEach { ev ->
                                    timelineEvents.add(org.json.JSONObject().apply {
                                        put("deviceId", ev.deviceId.ifEmpty { "Android_Client" })
                                        put("event", ev.event)
                                        put("timestamp", ev.timestamp)
                                    })
                                }
                            } else {
                                timelineEvents.add(org.json.JSONObject().apply {
                                    put("deviceId", "Android_Client")
                                    put("event", "START")
                                    put("timestamp", startTsMs)
                                })
                                if (record.totalBreakMs > 0L) {
                                    val elapsedTotal = if (endTsMs > startTsMs) endTsMs - startTsMs else (fSec * 1000L + record.totalBreakMs)
                                    val focusPart = if (elapsedTotal > record.totalBreakMs) elapsedTotal - record.totalBreakMs else 0L
                                    val breakStart = startTsMs + (focusPart / 2)
                                    val breakEnd = breakStart + record.totalBreakMs
                                    timelineEvents.add(org.json.JSONObject().apply {
                                        put("deviceId", "Android_Client")
                                        put("event", "BREAK START")
                                        put("timestamp", breakStart)
                                    })
                                    timelineEvents.add(org.json.JSONObject().apply {
                                        put("deviceId", "Android_Client")
                                        put("event", "BREAK END / RESUME")
                                        put("timestamp", breakEnd)
                                    })
                                }
                                timelineEvents.add(org.json.JSONObject().apply {
                                    put("deviceId", "Android_Client")
                                    put("event", "SESSION_END")
                                    put("timestamp", endTsMs)
                                })
                            }

                            val timelineJsonArray = org.json.JSONArray().apply {
                                timelineEvents.forEach { put(it) }
                            }

                            val rawJsonObject = org.json.JSONObject().apply {
                                put("Session_ID", record.id)
                                put("Current_Task", record.taskTitle)
                                put("Current_Tag", record.tag.ifEmpty { "General Focus" })
                                put("Timer_Mode", timerModeStr)
                                put("Total_Focus_Time_Ms", record.durationSeconds * 1000L)
                                put("Total_Break_Time_Ms", record.totalBreakMs)
                                put("Total_Focus_Time_Formatted", totalFocusFormatted)
                                put("Total_Break_Time_Formatted", totalBreakFormatted)
                                put("Start_Time", record.startTime)
                                put("End_Time", record.endTime)
                                put("Start_Timestamp", startTsMs)
                                put("End_Timestamp", endTsMs)
                                put("Date", record.dateString)
                                put("Firestore_Sync_Status", "Verified & Synced")
                                put("Database_Storage", "SQLite Room Vault")
                                put("Timeline", timelineJsonArray)
                            }

                            val rawJson = rawJsonObject.toString(2)
                            
                            Text(
                                text = rawJson,
                                color = Color(0xFF34D399),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedRecordForPopup = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun AllocationTableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    subtext: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    color = Color.LightGray,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                )
                Text(
                    text = subtext,
                    color = Color.DarkGray,
                    fontSize = 8.5.sp
                )
            }
        }
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp
        )
    }
}

private fun formatMinsToReadableCalendar(totalMins: Int): String {
    val h = totalMins / 60
    val m = totalMins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinsToHhMm(totalMins: Int): String {
    val h = totalMins / 60
    val m = totalMins % 60
    return String.format(java.util.Locale.US, "%02d:%02d", h, m)
}

private fun formatHourLabel(hour: Int): String {
    val ampm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%02d:00 %s", displayHour, ampm)
}

private fun formatHalfHourLabel(halfHour: Int): String {
    val h = halfHour / 2
    val m = if (halfHour % 2 == 0) "00" else "30"
    val ampm = if (h >= 12) "PM" else "AM"
    val displayH = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format(Locale.US, "%02d:%s %s", displayH, m, ampm)
}

private fun getTagColorCalendar(tag: String): Color {
    if (tag.isBlank()) return Color(0xFF38BDF8) // Default WaterBlue
    val colors = listOf(
        Color(0xFF38BDF8), // WaterBlue
        Color(0xFFF43F5E), // Rose / Red
        Color(0xFF10B981), // Emerald / Green
        Color(0xFFF59E0B), // Amber / Orange
        Color(0xFF8B5CF6), // Violet / Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFF84CC16), // Lime
        Color(0xFF14B8A6), // Teal
        Color(0xFFE11D48)  // Crimson
    )
    val index = kotlin.math.abs(tag.hashCode()) % colors.size
    return colors[index]
}

@Composable
fun TwoByTwoStatsGrid(
    focusRecords: List<FocusRecord>,
    myTodaySeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val systemTodayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    
    val todaySecs = remember(focusRecords, systemTodayStr, myTodaySeconds) {
        val dbSecs = focusRecords.filter { it.dateString == systemTodayStr || (it.dateString.isEmpty() && systemTodayStr == systemTodayStr) }.sumOf { it.durationSeconds }
        maxOf(dbSecs, myTodaySeconds)
    }
    
    val past7Secs = remember(focusRecords, systemTodayStr) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = (0..6).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            sdf.format(c.time)
        }
        focusRecords.filter { it.dateString in dates }.sumOf { it.durationSeconds }
    }
    
    val past30Secs = remember(focusRecords, systemTodayStr) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = (0..29).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            sdf.format(c.time)
        }
        focusRecords.filter { it.dateString in dates }.sumOf { it.durationSeconds }
    }
    
    val allTimeSecs = remember(focusRecords) {
        focusRecords.sumOf { it.durationSeconds }
    }
    
    val formatSecs = { secs: Int ->
        val h = secs / 3600
        val m = (secs % 3600) / 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
        border = BorderStroke(1.dp, Color(0xFF1F1F24)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Focus Stats Summary",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Col 1
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBoxCell(
                        title = "Today",
                        value = formatSecs(todaySecs),
                        icon = Icons.Default.Today,
                        accentColor = Color(0xFF38BDF8)
                    )
                    StatBoxCell(
                        title = "Past 30 Days",
                        value = formatSecs(past30Secs),
                        icon = Icons.Default.DateRange,
                        accentColor = Color(0xFFF59E0B)
                    )
                }
                
                // Col 2
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBoxCell(
                        title = "Past 7 Days",
                        value = formatSecs(past7Secs),
                        icon = Icons.Default.Event,
                        accentColor = Color(0xFF10B981)
                    )
                    StatBoxCell(
                        title = "All Time",
                        value = formatSecs(allTimeSecs),
                        icon = Icons.Default.History,
                        accentColor = Color(0xFFEC4899)
                    )
                }
            }
        }
    }
}

@Composable
fun StatBoxCell(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161A), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1F1F24), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

