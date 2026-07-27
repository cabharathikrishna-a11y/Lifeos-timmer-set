package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.util.FocusTimerManager
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.room.Room
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.data.AppDatabase
import com.example.data.LocalRepository
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WaterBlue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalRepository
    private val viewModel: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                    val handle = try {
                        extras.createSavedStateHandle()
                    } catch (e: Exception) {
                        androidx.lifecycle.SavedStateHandle()
                    }
                    val db = AppDatabase.getInstance(applicationContext)
                    val repo = LocalRepository(db, applicationContext)
                    @Suppress("UNCHECKED_CAST")
                    return AppViewModel(application, repo, handle) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    private var startupException: Throwable? = null
    private val isAppUnlockedState = androidx.compose.runtime.mutableStateOf(false)
    private val interceptedAppSessionQuery = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val isDbReady = androidx.compose.runtime.mutableStateOf(false)

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserInteraction(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        
        try {
            // Clear Shared Preferences Cache if structure v2 is not yet initialized
            val initPrefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            if (!initPrefs.getBoolean("is_structure_v2_initialized", false)) {
                initPrefs.edit().clear().apply()
                initPrefs.edit().putBoolean("is_structure_v2_initialized", true).apply()
            }

            // Dismiss stale notifications to prevent SystemUI asset loading crashes on app updates/reinstalls
            try {
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancelAll()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to cancel old notifications on startup: ${e.message}")
            }

            // Initialize monotonic StableTime
            com.example.util.StableTime.init()

            // Initialize NTP Universal Clock
            com.example.util.TimeEngine.initializeNtp(applicationContext)

            // Set the global app context for Retrofit intercepting wrapper
            com.example.api.Firebase.appContext = applicationContext

            // Initialize local Room Database and Repository immediately in non-blocking background
            try {
                database = AppDatabase.getInstance(applicationContext)
                repository = LocalRepository(database, applicationContext)
                FocusTimerManager.init(applicationContext)
                isDbReady.value = true
                // Trigger authoritative boot gate verification immediately after local DB is ready
                viewModel.verifyCloudStateAndReleaseGate(applicationContext)
            } catch (e: Throwable) {
                e.printStackTrace()
                startupException = e
                isDbReady.value = true
            }

            // Run all heavy background initializations asynchronously without blocking the UI
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Initialize default app blocks and strict mode lists
                    com.example.util.AppBlockHelper.initializeStrictAppsIfNeeded(applicationContext)
                    
                    // Initialize default update configuration in Firebase RTDB on first start
                    try {
                        com.example.util.AppUpdateManager.initializeDefaultUpdateConfigIfNeeded(applicationContext)
                        com.example.util.SmartUpdateManager.init(applicationContext)
                        com.example.util.SmartUpdateManager.checkForUpdates(applicationContext)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to initialize default update config", e)
                    }
                    
                    // Start the persistent keep alive daemon service if enabled
                    val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    val customDbUrl = prefs.getString("custom_firebase_db_url", null)
                    if (!customDbUrl.isNullOrBlank()) {
                        com.example.api.Firebase.activeUrl = customDbUrl
                    }
                    if (prefs.getBoolean("keep_notification_enabled", true)) {
                        com.example.service.KeepAliveService.start(applicationContext)
                    }

                    // Schedule bedtime reminder and wake-up alarm on app startup
                    com.example.util.AlarmScheduler.scheduleBedtimeReminder(applicationContext)
                    com.example.util.AlarmScheduler.scheduleWakeUpAlarm(applicationContext)

                    // Run authoritative Read-Before-Write Boot Sequence
                    try {
                        com.example.api.OutboxDrainer.executeSafeBootSequence(applicationContext)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Safe boot sequence failed", e)
                    }

                    // Start real-time database queue outbox drainer
                    com.example.api.OutboxDrainer.start(applicationContext)

                    // Check for previously exported backups on first start and auto-restore
                    try {
                        val restored = com.example.util.DatabaseBackupHelper.autoRestoreIfNeeded(applicationContext, database)
                        if (restored) {
                            android.util.Log.i("MainActivity", "Successfully verified and auto-restored database from public storage.")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Auto-restore logic failed", e)
                    }

                    // Auto-sync Pull (Restore) and then Push (Backup) when app opened
                    if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext)) {
                        try {
                            android.util.Log.i("MainActivity", "Auto Google Drive sync (Pull & Push) starting on app open...")
                            // Pull / Restore
                            val (pullSuccess, pullMsg) = com.example.util.GoogleDriveSyncManager.restoreAllAppData(applicationContext, database)
                            android.util.Log.i("MainActivity", "Auto Google Drive pull result on open: success=$pullSuccess, msg=$pullMsg")
                            // Push / Backup
                            val (pushSuccess, pushMsg) = com.example.util.GoogleDriveSyncManager.backupAllAppData(applicationContext, database)
                            android.util.Log.i("MainActivity", "Auto Google Drive push result on open: success=$pushSuccess, msg=$pushMsg")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Auto Google Drive sync failed on open: ${e.message}", e)
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Track screen changes dynamically to trigger/hide floating overlay
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    viewModel.currentScreen.collect { screen ->
                        FocusTimerManager.setTimerScreenActiveState(this@MainActivity, screen == Screen.TIMER)
                    }
                }
            }

            // Handle auto-navigation if launched with parameters
            checkNotificationNavigation(intent)
            checkTimerNavigation(intent)
            checkAppBlockInterceptions(intent)
            handleDeepLink(intent)
            handleIncomingShareIntent(intent)
            performLaunchRedirectionCheck()

            // Register Network Reconnection Event Listener
            try {
                val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val networkRequest = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(networkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
                    private var isFirstCallback = true
                    override fun onAvailable(network: android.net.Network) {
                        if (isFirstCallback) {
                            isFirstCallback = false
                            return // ignore the initial callback upon registration
                        }
                        android.util.Log.i("MainActivity", "Network Reconnection Event: Transited from OFFLINE to ONLINE status.")
                        triggerFocusReconciliation()
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to register network callback: ${e.message}", e)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            startupException = e
            isDbReady.value = true
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current

                val sharedFileIntentState by viewModel.sharedFileIntent.collectAsStateWithLifecycle()
                sharedFileIntentState?.let { intentData ->
                    com.example.ui.components.SharedFileReceiverOverlay(
                        viewModel = viewModel,
                        sharedIntent = intentData
                    )
                }
                
                val isLoggedInState by viewModel.isLoggedIn.collectAsStateWithLifecycle()

                var showSocialOnboarding by remember {
                    mutableStateOf(false)
                }
                LaunchedEffect(isLoggedInState) {
                    if (isLoggedInState) {
                        // Request Notification Permission on Android 13+ (API 33)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS
                            )
                            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                androidx.core.app.ActivityCompat.requestPermissions(
                                    this@MainActivity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101
                                )
                            }
                        }
                    }
                }
                
                // On-startup check for downloaded APK and silent background checking
                val readyApkPath = remember { com.example.util.AppUpdateManager.getReadyApkPath(context) }
                var showStartupUpdateDialog by remember {
                    mutableStateOf(
                        readyApkPath?.let { path ->
                            val file = java.io.File(path)
                            file.exists() && file.length() > 0 && com.example.util.AppUpdateManager.isValidAndNewerApk(context, file)
                        } ?: false
                    )
                }

                var showCelebrationDialog by remember { mutableStateOf(false) }
                var updatedVersionCode by remember { mutableStateOf(0) }
                val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                var showChatFeatureNoticeDialog by remember {
                    mutableStateOf(!prefs.getBoolean("has_seen_chat_feature_notice_v2", false))
                }

                LaunchedEffect(Unit) {
                    com.example.util.AppCrashRollbackManager.initialize(applicationContext)
                    val rollbackTriggered = com.example.util.AppCrashRollbackManager.checkAndExecuteRollback(applicationContext)
                    if (!rollbackTriggered) {
                        kotlinx.coroutines.delay(2000L)
                        com.example.util.AppCrashRollbackManager.markStartupSuccess(applicationContext)
                    }

                    val upgradedTo = com.example.util.AppUpdateManager.checkAndNotifyUpgradeComplete(context)
                    if (upgradedTo != null) {
                        updatedVersionCode = upgradedTo
                        showCelebrationDialog = true
                    }
                    if (!com.example.util.AppUpdateManager.isPauseUpdatesEnabled(context)) {
                        com.example.util.AppUpdateManager.checkForUpdates(context, manualCheck = false)
                    }
                }

                if (isLoggedInState && showCelebrationDialog) {
                    AlertDialog(
                        onDismissRequest = { showCelebrationDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success Icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Update Successful! 🎉", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Text(
                                "Life OS has been successfully updated to Build $updatedVersionCode.\n\n" +
                                "All your study statistics, task lists, and local backups remain completely safe and intact. Keep up the amazing focus!",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showCelebrationDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Awesome!", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF101014),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (isLoggedInState && showStartupUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { showStartupUpdateDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Update ready icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("App Update Ready", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Text(
                                "A new Life OS update is downloaded and ready to install. Your local database and settings will be securely backed up prior to the installation to guarantee zero data loss. Proceed with the update now?",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showStartupUpdateDialog = false
                                    readyApkPath?.let { path ->
                                        com.example.util.AppUpdateManager.installApk(context, java.io.File(path))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Install Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartupUpdateDialog = false }) {
                                Text("Later", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF101014),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (isLoggedInState && showChatFeatureNoticeDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            prefs.edit().putBoolean("has_seen_chat_feature_notice_v2", true).apply()
                            showChatFeatureNoticeDialog = false
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = "Chat Icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Try Out New Chat! 💬", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Column {
                                Text(
                                    "Chat is now conveniently placed as the 2nd tab for fast access!",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "• Access Full History: Whether you update late or join the community later, all past study group messages & DM history are automatically synchronized.\n" +
                                    "• Study Circles & DMs: Collaborate with peers, share file attachments, and record voice notes.\n" +
                                    "• Tap below to try out the new Chat feature right now!",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    prefs.edit().putBoolean("has_seen_chat_feature_notice_v2", true).apply()
                                    showChatFeatureNoticeDialog = false
                                    viewModel.navigateTo(Screen.MESSAGES)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Try Chat Now 💬", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    prefs.edit().putBoolean("has_seen_chat_feature_notice_v2", true).apply()
                                    showChatFeatureNoticeDialog = false
                                }
                            ) {
                                Text("Dismiss", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF14161F),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                val error = startupException
                val dbReady by isDbReady
                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error Logo",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "STARTUP FAILURE DETECTED",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Life OS failed to initialize. Details are documented below:",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = android.util.Log.getStackTraceString(error),
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().clear().apply()
                                    try {
                                        deleteDatabase("life_os_database")
                                    } catch (ex: Exception) {}
                                    val restartIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    startActivity(restartIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00))
                            ) {
                                Text("Factory Reset & Recovery Start", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (!dbReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF06070D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = com.example.ui.theme.WaterBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Initializing Database...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val forceUpdateRequired by com.example.util.SmartUpdateManager.isForceUpdateRequired.collectAsStateWithLifecycle()
                    if (forceUpdateRequired) {
                        val smartState by com.example.util.SmartUpdateManager.updateStatus.collectAsStateWithLifecycle()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF020617))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Forced Update Required",
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "MANDATORY UPDATE REQUIRED",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "To maintain synchronization consensus and data security, you must upgrade to the latest build to continue using Life OS.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                                               val label = when (val state = smartState) {
                                    is com.example.util.SmartUpdateStatus.Idle -> "Pending Download"
                                    is com.example.util.SmartUpdateStatus.Checking -> "Checking cloud version..."
                                    is com.example.util.SmartUpdateStatus.SecuringData -> "Securing your local database..."
                                    is com.example.util.SmartUpdateStatus.NewVersionAvailable -> "Ready to download update (Build ${state.versionNo})"
                                    is com.example.util.SmartUpdateStatus.NoUpdateAvailable -> "Up to date"
                                    is com.example.util.SmartUpdateStatus.Downloading -> "Downloading: ${(state.progress * 100).toInt()}%"
                                    is com.example.util.SmartUpdateStatus.Merging -> "Optimizing Delta Patch (BSPatch)..."
                                    is com.example.util.SmartUpdateStatus.ReadyToInstall -> "Ready to install!"
                                    is com.example.util.SmartUpdateStatus.Error -> "Failed: ${state.message}"
                                }
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label.uppercase(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        
                                        if (smartState is com.example.util.SmartUpdateStatus.Downloading) {
                                            val progress = (smartState as com.example.util.SmartUpdateStatus.Downloading).progress
                                            Spacer(modifier = Modifier.height(12.dp))
                                            LinearProgressIndicator(
                                                progress = { if (progress >= 0f) progress else 0f },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = Color(0xFF38BDF8),
                                                trackColor = Color(0xFF1E293B)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                ) {
                                    val config = com.example.util.SmartUpdateManager.activeForceUpdateConfig
                                    if (config != null && smartState !is com.example.util.SmartUpdateStatus.Downloading && smartState !is com.example.util.SmartUpdateStatus.Merging && smartState !is com.example.util.SmartUpdateStatus.ReadyToInstall) {
                                        Button(
                                            onClick = {
                                                com.example.util.SmartUpdateManager.triggerSmartUpdate(context, config)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Download & Apply Patch", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    if (smartState is com.example.util.SmartUpdateStatus.ReadyToInstall) {
                                        Button(
                                            onClick = {
                                                com.example.util.SmartUpdateManager.installApk(context, (smartState as com.example.util.SmartUpdateStatus.ReadyToInstall).apkFile)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Install Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isAppUnlocked by isAppUnlockedState
                        if (isAppUnlocked) {
                            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                    val activeNagTask by viewModel.activeNagTask.collectAsStateWithLifecycle()
                    val isSidebarOpen by viewModel.isLocalSidebarOpen.collectAsStateWithLifecycle()
                    val tabOrder by viewModel.tabOrder.collectAsStateWithLifecycle()
                    val hiddenTabs by viewModel.hiddenTabs.collectAsStateWithLifecycle()
                    val isTimerImmersive by viewModel.isTimerImmersive.collectAsStateWithLifecycle()
                    val isFileExplorerF11Active by viewModel.isFileExplorerF11Active.collectAsStateWithLifecycle()
                    val tabBarOrientation by viewModel.tabBarOrientation.collectAsStateWithLifecycle()
                    val showHistoryScreen by viewModel.showHistoryScreen.collectAsStateWithLifecycle()
                    val unreadChatCount by viewModel.unreadChatCount.collectAsStateWithLifecycle()
                    val navItems = getNavigationItems(tabOrder.filterNot { hiddenTabs.contains(it) })

                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    @OptIn(ExperimentalLayoutApi::class)
                    val isKeyboardVisible = WindowInsets.isImeVisible

                    // Back Navigation Control
                    if (currentScreen == Screen.DEEPA_AI) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                moveTaskToBack(true)
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && isTimerImmersive) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setTimerImmersive(false)
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && showHistoryScreen) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setShowHistoryScreen(false)
                            }
                        }
                    } else if (currentScreen == Screen.SETTINGS) {
                        val settingsActivePage by viewModel.settingsActivePage.collectAsStateWithLifecycle()
                        val previousScreenBeforeSettings by viewModel.previousScreenBeforeSettings.collectAsStateWithLifecycle()
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else if (settingsActivePage != 0) {
                                viewModel.updateSettingsActivePage(0)
                            } else {
                                val previous = previousScreenBeforeSettings
                                if (previous != null && previous != Screen.SETTINGS && previous != Screen.LOGIN && previous != Screen.PROFILE_SETUP && previous != Screen.PERMISSION_ONBOARDING && previous != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                    viewModel.navigateTo(previous)
                                } else {
                                    viewModel.navigateTo(Screen.DEEPA_AI)
                                }
                            }
                        }
                    } else {
                        // Other main tabs navigate back to Screen.DEEPA_AI (AI page again)
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.navigateTo(Screen.DEEPA_AI)
                            }
                        }
                    }

                    @Composable
                    fun MainScaffoldContent(scaffoldModifier: Modifier) {
                    Scaffold(
                        modifier = scaffoldModifier,
                        containerColor = Color.Transparent,
                        topBar = {}
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            PastedLinksHubView(viewModel = viewModel)

                            // Render screen container with premium fluid transition animations
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        val isForward = targetState.ordinal > initialState.ordinal
                                        val duration = 350
                                        (fadeIn(animationSpec = tween(duration)) +
                                         slideInHorizontally(
                                             initialOffsetX = { if (isForward) it / 6 else -it / 6 },
                                             animationSpec = tween(duration, easing = FastOutSlowInEasing)
                                         ) +
                                         scaleIn(initialScale = 0.97f, animationSpec = tween(duration)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(200)) +
                                            slideOutHorizontally(
                                                targetOffsetX = { if (isForward) -it / 6 else it / 6 },
                                                animationSpec = tween(duration, easing = FastOutSlowInEasing)
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "tab_screen_transition"
                                ) { targetScreen ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        when (targetScreen) {
                                            Screen.LOGIN -> LoginView(viewModel = viewModel)
                                            Screen.PROFILE_SETUP -> ProfileSetupView(viewModel = viewModel)
                                            Screen.PERMISSION_ONBOARDING -> PermissionOnboardingView(viewModel = viewModel)
                                            Screen.CALENDAR_OPTIMIZATION_ONBOARDING -> CalendarOptimizationOnboardingView(viewModel = viewModel)
                                            Screen.TASKS -> TaskEngineView(viewModel = viewModel)
                                            Screen.CALENDAR -> CalendarView(viewModel = viewModel)
                                            Screen.TIMER -> TimerView(viewModel = viewModel)
                                            Screen.LIVE_SPHERE -> com.example.ui.components.LiveSphereScreen(viewModel = viewModel)
                                            Screen.ARENA -> com.example.ui.components.ArenaScreen(viewModel = viewModel)
                                            Screen.FOCUS_LOCKER -> com.example.ui.components.FocusLockerScreen(viewModel = viewModel, onBack = { viewModel.navigateTo(Screen.SETTINGS) })
                                            Screen.HABITS -> HabitsView(viewModel = viewModel)
                                            Screen.COUNTDOWN -> CountdownView(viewModel = viewModel)
                                            Screen.JOURNAL -> JournalBookView(viewModel = viewModel)
                                            Screen.KEEP_NOTES -> KeepNotesView(viewModel = viewModel)
                                            Screen.CONTACTS -> ContactsView(viewModel = viewModel)
                                            Screen.MESSAGES -> GoogleChatView(viewModel = viewModel)
                                            Screen.FILE_EXPLORER -> FileExplorerView(viewModel = viewModel)
                                            Screen.FINANCES -> FinancialLedgerView(viewModel = viewModel)
                                            Screen.DEEPA_AI -> SmartChatView(viewModel = viewModel)
                                            Screen.SEARCH -> GlobalSearchView(viewModel = viewModel)
                                            Screen.ANALYTICS -> AnalyticsView(viewModel = viewModel)
                                            Screen.SETTINGS -> SettingsView(viewModel = viewModel)
                                            Screen.HEALTH -> com.example.ui.components.HealthView(viewModel = viewModel)
                                            Screen.FLEX_GRID_STUDIO -> com.example.ui.components.FlexGridStudioView(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentScreen == Screen.TIMER && isTimerImmersive) {
                    // Full Screen Immersive Mode: covers all the display, leaving absolutely no side navigation or safe drawer padding!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        TimerView(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTablet = maxWidth >= 600.dp
                        val outerModifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF141419),
                                        Color(0xFF0F0F11),
                                        Color(0xFF0A0A0C)
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)

                        val savedAlignMode = tabBarOrientation.lowercase(java.util.Locale.ROOT)
                        val alignMode = if (savedAlignMode == "vertical" || savedAlignMode == "left" || savedAlignMode.isEmpty()) {
                            if (isTablet) "left" else "bottom"
                        } else {
                            savedAlignMode
                        }
                        if (alignMode == "horizontal" || alignMode == "top") {
                        Column(modifier = outerModifier) {
                            if (!isKeyboardVisible && !isFileExplorerF11Active && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                // Top Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_top"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_top"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_top"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(WaterBlue.copy(alpha = bgAlpha))
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
                                                )
                                                if (item.screen == Screen.MESSAGES && unreadChatCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 6.dp, y = (-6).dp)
                                                            .size(if (unreadChatCount > 9) 16.dp else 12.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else if (alignMode == "bottom") {
                        Column(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())

                            if (!isKeyboardVisible && !isFileExplorerF11Active && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                // Bottom Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_bottom"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_bottom"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_bottom"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(WaterBlue.copy(alpha = bgAlpha))
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
                                                )
                                                if (item.screen == Screen.MESSAGES && unreadChatCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 6.dp, y = (-6).dp)
                                                            .size(if (unreadChatCount > 9) 16.dp else 12.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }
                        }
                    } else if (alignMode == "right") {
                        Row(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())

                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                            // Right-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_right"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_right"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_right"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 36.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(WaterBlue.copy(alpha = bgAlpha))
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label,
                                                        tint = tint,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .graphicsLayer {
                                                                scaleX = iconScale
                                                                scaleY = iconScale
                                                            }
                                                    )
                                                    if (item.screen == Screen.MESSAGES && unreadChatCount > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 6.dp, y = (-6).dp)
                                                                .size(if (unreadChatCount > 9) 16.dp else 12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFE53935)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString(),
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }
                        }
                    } else { // "left" or "vertical" or any other fallback
                        Row(modifier = outerModifier) {
                            if (!isKeyboardVisible && !isFileExplorerF11Active && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                            // Left-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_left"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_left"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_left"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 36.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(WaterBlue.copy(alpha = bgAlpha))
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label,
                                                        tint = tint,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .graphicsLayer {
                                                                scaleX = iconScale
                                                                scaleY = iconScale
                                                            }
                                                    )
                                                    if (item.screen == Screen.MESSAGES && unreadChatCount > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 6.dp, y = (-6).dp)
                                                                .size(if (unreadChatCount > 9) 16.dp else 12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFE53935)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString(),
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                }
                } else {
                    com.example.ui.components.AppLockOverlay(
                        onUnlocked = {
                            isAppUnlockedState.value = true
                        }
                    )
                }

                // App Interception Selector Prompt Overlay
                val interceptPkg by interceptedAppSessionQuery
                if (interceptPkg != null) {
                    AlertDialog(
                        onDismissRequest = {
                            // Non-dismissible by clicking outside to keep it a strict block
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Session Warning Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "LIFE OS APP MONITOR",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val appLabel = remember(interceptPkg) {
                                    try {
                                        val pm = packageManager
                                        val info = pm.getApplicationInfo(interceptPkg ?: "", 0)
                                        pm.getApplicationLabel(info).toString()
                                    } catch (e: Exception) {
                                        interceptPkg?.substringAfterLast('.') ?: "Social App"
                                    }
                                }
                                Text(
                                    text = "You are attempting to open $appLabel.",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Please allocate your session time usage below. Once the duration is over, Life OS will automatically block access.\n\nSelecting 'Close App' will safely exit and return to home.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 10, 15, 20).forEach { mins ->
                                    Button(
                                        onClick = {
                                            com.example.util.AppBlockHelper.startTemporarySession(applicationContext, interceptPkg ?: "", mins)
                                            interceptedAppSessionQuery.value = null
                                            // Relaunch the application safely
                                            try {
                                                val pm = packageManager
                                                val launchIntent = pm.getLaunchIntentForPackage(interceptPkg ?: "")
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(applicationContext, "Session set, but failed to launch app implicitly.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = WaterBlue,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Text(
                                            text = "Use for $mins minutes",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        interceptedAppSessionQuery.value = null
                                        // Minimize our app and route user to the home screen launcher
                                        try {
                                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_HOME)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(homeIntent)
                                        } catch (e: Exception) {
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF5252),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text(
                                        text = "Close App",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Global Focus Session Saved & Verified Confirmation Overlay
                val showGlobalVerification by FocusTimerManager.showGlobalVerificationDialog.collectAsStateWithLifecycle()
                val globalFocusedSecs by FocusTimerManager.globalVerificationFocusedTimeSeconds.collectAsStateWithLifecycle()
                val globalRevisedSecs by FocusTimerManager.globalVerificationRevisedTotalSeconds.collectAsStateWithLifecycle()
                val verifiedStartMs by FocusTimerManager.verifiedSessionStartMs.collectAsStateWithLifecycle()
                val verifiedPauseRanges by FocusTimerManager.verifiedSessionPauseRanges.collectAsStateWithLifecycle()

                if (showGlobalVerification) {
                    AlertDialog(
                        onDismissRequest = {
                            FocusTimerManager.setShowGlobalVerificationDialog(false)
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "SYSTEM VERIFICATION",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                fun formatSecondsToReadable(seconds: Int): String {
                                    val h = seconds / 3600
                                    val m = (seconds % 3600) / 60
                                    val s = seconds % 60
                                    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                                }

                                val formattedNow = formatSecondsToReadable(globalFocusedSecs)
                                val pastSeconds = maxOf(0, globalRevisedSecs - globalFocusedSecs)
                                val formattedPast = formatSecondsToReadable(pastSeconds)
                                val formattedRevised = formatSecondsToReadable(globalRevisedSecs)

                                val timeFormatter = remember { java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()) }
                                val startStr = verifiedStartMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"
                                
                                var totalBreakMs = 0L
                                verifiedPauseRanges.forEach { (pStart, pEnd) ->
                                    if (pEnd >= pStart) {
                                        totalBreakMs += (pEnd - pStart)
                                    }
                                }
                                val breakSeconds = (totalBreakMs / 1000).toInt()
                                val wallSeconds = globalFocusedSecs + breakSeconds
                                val computedEndMs = verifiedStartMs?.let { it + wallSeconds * 1000L }
                                val endStr = computedEndMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22222A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 1. Previously Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("PREVIOUSLY FOCUSED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedPast, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 2. Start Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("START TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(startStr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 3. End Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("END TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(endStr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 4. Current Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("CURRENT FOCUSED TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedNow, color = WaterBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 5. Revised Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("REVISED FOCUSED TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedRevised, color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    FocusTimerManager.setShowGlobalVerificationDialog(false)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text(
                                    text = "Confirm & Close",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                
                if (showSocialOnboarding) {
                    SocialOnboardingView(
                        onDismiss = { showSocialOnboarding = false }
                    )
                }
                }
            }
        }
    }
}

    override fun onStart() {
        super.onStart()
        FocusTimerManager.setAppBackgroundedState(this, false)
        if (!com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = true
        }
    }

    private fun triggerFocusReconciliation() {
        val currentUsername = viewModel.currentUsername.value
        if (!currentUsername.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    com.example.util.FocusReconciliationEngine.runReconciliation(applicationContext, currentUsername)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "FocusReconciliationEngine failed on event: ${e.message}", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.trackSleepFromDeviceUsage(applicationContext)
        triggerFocusReconciliation()
    }

    override fun onStop() {
        super.onStop()
        FocusTimerManager.setAppBackgroundedState(this, true)
        if (com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = false
        }

        // Auto-reconcile and Auto-backup to public storage before potential uninstall/force-stop
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (::database.isInitialized) {
                    // Ensure state consistency and flush memoryWAL files cleanly before backup
                    com.example.util.StateReconciliationHelper.runUnifiedReconciliation(applicationContext, database)
                    com.example.util.DatabaseBackupHelper.autoBackup(applicationContext, database)
                }
                
                // If the user has signed in and granted Drive permissions, auto-sync backup before potential uninstallation
                if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext) && ::database.isInitialized) {
                    android.util.Log.i("MainActivity", "Auto-backing up all app data to Google Drive on stop...")
                    val (success, msg) = com.example.util.GoogleDriveSyncManager.backupAllAppData(applicationContext, database)
                    android.util.Log.i("MainActivity", "Google Drive auto-backup all on stop result: success=$success, msg=$msg")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "State reconciliation, auto-backup, or Google Drive backup failed on stop", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = true
        }
        checkNotificationNavigation(intent)
        checkTimerNavigation(intent)
        checkAppBlockInterceptions(intent)
        handleDeepLink(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data
        if (uri == null) return
        
        android.util.Log.i("MainActivity", "handleDeepLink: action=$action, uri=$uri")
        try {
            val scheme = uri.scheme
            val host = uri.host
            if (scheme == "lifeos" || (host == "lifeos.app" && (scheme == "http" || scheme == "https"))) {
                val path = uri.path
                val targetHost = if (scheme == "lifeos") host else uri.lastPathSegment
                
                android.util.Log.i("MainActivity", "Deep Link routing: targetHost=$targetHost, path=$path")
                
                when (targetHost) {
                    "login" -> viewModel.navigateTo(Screen.LOGIN)
                    "profile_setup" -> viewModel.navigateTo(Screen.PROFILE_SETUP)
                    "onboarding" -> viewModel.navigateTo(Screen.PERMISSION_ONBOARDING)
                    "ai_chat" -> viewModel.navigateTo(Screen.DEEPA_AI)
                    "search" -> viewModel.navigateTo(Screen.SEARCH)
                    "tasks" -> viewModel.navigateTo(Screen.TASKS)
                    "calendar" -> viewModel.navigateTo(Screen.CALENDAR)
                    "timer" -> viewModel.navigateTo(Screen.TIMER)
                    "habits" -> viewModel.navigateTo(Screen.HABITS)
                    "countdown" -> viewModel.navigateTo(Screen.COUNTDOWN)
                    "journal" -> viewModel.navigateTo(Screen.JOURNAL)
                    "keep_notes", "notes" -> viewModel.navigateTo(Screen.KEEP_NOTES)
                    "contacts" -> viewModel.navigateTo(Screen.CONTACTS)
                    "messages" -> viewModel.navigateTo(Screen.MESSAGES)
                    "file_explorer" -> viewModel.navigateTo(Screen.FILE_EXPLORER)
                    "finances", "financials" -> viewModel.navigateTo(Screen.FINANCES)
                    "analytics" -> viewModel.navigateTo(Screen.ANALYTICS)
                    "settings" -> viewModel.navigateTo(Screen.SETTINGS)
                    "action" -> {
                        when (path) {
                            "/sync_calendar" -> viewModel.syncGoogleCalendar(applicationContext)
                            "/backup_drive" -> {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.util.GoogleDriveSyncManager.backupFocusData(applicationContext)
                                }
                            }
                            "/force_contacts_sync" -> viewModel.forceSyncAllContactsToDevice()
                            "/check_updates" -> {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.util.AppUpdateManager.checkForUpdates(applicationContext, manualCheck = true)
                                }
                            }
                        }
                    }
                    else -> {
                        // If path segments contain the screen name (e.g. /tasks or /calendar)
                        val segments = uri.pathSegments
                        if (segments.isNotEmpty()) {
                            when (segments.firstOrNull()?.lowercase()) {
                                "login" -> viewModel.navigateTo(Screen.LOGIN)
                                "profile_setup" -> viewModel.navigateTo(Screen.PROFILE_SETUP)
                                "onboarding" -> viewModel.navigateTo(Screen.PERMISSION_ONBOARDING)
                                "ai_chat" -> viewModel.navigateTo(Screen.DEEPA_AI)
                                "search" -> viewModel.navigateTo(Screen.SEARCH)
                                "tasks" -> viewModel.navigateTo(Screen.TASKS)
                                "calendar" -> viewModel.navigateTo(Screen.CALENDAR)
                                "timer" -> viewModel.navigateTo(Screen.TIMER)
                                "habits" -> viewModel.navigateTo(Screen.HABITS)
                                "countdown" -> viewModel.navigateTo(Screen.COUNTDOWN)
                                "journal" -> viewModel.navigateTo(Screen.JOURNAL)
                                "keep_notes", "notes" -> viewModel.navigateTo(Screen.KEEP_NOTES)
                                "contacts" -> viewModel.navigateTo(Screen.CONTACTS)
                                "messages" -> viewModel.navigateTo(Screen.MESSAGES)
                                "file_explorer" -> viewModel.navigateTo(Screen.FILE_EXPLORER)
                                "finances", "financials" -> viewModel.navigateTo(Screen.FINANCES)
                                "analytics" -> viewModel.navigateTo(Screen.ANALYTICS)
                                "settings" -> viewModel.navigateTo(Screen.SETTINGS)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing deep link", e)
        }
    }

    private fun checkNotificationNavigation(intent: Intent?) {
        if (intent == null) return
        val navigateTo = intent.getStringExtra("NAVIGATE_TO") ?: intent.getStringExtra("navigate_to")
        val isChatNotif = intent.getBooleanExtra("IS_CHAT_NOTIFICATION", false) ||
                navigateTo.equals("MESSAGES", ignoreCase = true) ||
                navigateTo.equals("chat", ignoreCase = true)

        val isAnyNotification = isChatNotif ||
                intent.getBooleanExtra("IS_NOTIFICATION", false) ||
                !navigateTo.isNullOrEmpty() ||
                intent.getBooleanExtra("SHOW_TIMER_PAGE", false) ||
                intent.getBooleanExtra("SHOW_FULL_SCREEN_TIMER", false) ||
                intent.getBooleanExtra("SHOW_TASKS_PAGE", false) ||
                intent.getBooleanExtra("SHOW_JOURNAL_PAGE", false) ||
                intent.getBooleanExtra("SHOW_BLOCKS_PAGE", false)

        if (!isAnyNotification) return

        android.util.Log.i("MainActivity", "checkNotificationNavigation: navigateTo=$navigateTo, isChatNotif=$isChatNotif")

        if (isChatNotif) {
            // Chat notification opens respective chat page
            viewModel.navigateTo(Screen.MESSAGES)
        } else if (navigateTo.equals("NOTIFICATIONS_STUDIO", ignoreCase = true)) {
            viewModel.navigateTo(Screen.SETTINGS)
            viewModel.updateSettingsActivePage(25)
        } else if (intent.getBooleanExtra("SHOW_TIMER_PAGE", false) || intent.getBooleanExtra("SHOW_FULL_SCREEN_TIMER", false)) {
            viewModel.navigateTo(Screen.TIMER)
        } else {
            // Any other notification other than chat directs to AI page of the app
            viewModel.navigateTo(Screen.DEEPA_AI)
        }
    }

    private fun performLaunchRedirectionCheck() {
        val currentIntent = intent
        val hasExplicitNotificationExtra = currentIntent?.hasExtra("NAVIGATE_TO") == true ||
                currentIntent?.hasExtra("navigate_to") == true ||
                currentIntent?.hasExtra("SHOW_TIMER_PAGE") == true ||
                currentIntent?.hasExtra("SHOW_FULL_SCREEN_TIMER") == true ||
                currentIntent?.hasExtra("SHOW_TASKS_PAGE") == true ||
                currentIntent?.hasExtra("SHOW_JOURNAL_PAGE") == true ||
                currentIntent?.hasExtra("SHOW_BLOCKS_PAGE") == true ||
                currentIntent?.data != null

        if (hasExplicitNotificationExtra) {
            android.util.Log.i("MainActivity", "Explicit notification intent extra present. Skipping performLaunchRedirectionCheck.")
            return
        }

        lifecycleScope.launch {
            // Wait briefly for FocusTimerManager to finish loading prefs or init states if necessary
            kotlinx.coroutines.delay(100)
            
            // 1. Prioritize active focus session (running, paused, break, etc.)
            val isTimerRunning = com.example.util.FocusTimerManager.isTimerRunning.value
            val isStopwatchActive = com.example.util.FocusTimerManager.isStopwatchActive.value
            val isPaused = com.example.util.FocusTimerManager.isPaused.value
            val isFocusPhase = com.example.util.FocusTimerManager.isFocusPhase.value
            val stopwatchSeconds = com.example.util.FocusTimerManager.stopwatchSeconds.value
            
            val isPromoSessionActive = (isTimerRunning || isPaused) && (!isFocusPhase || com.example.util.FocusTimerManager.timerSecondsLeft.value < com.example.util.FocusTimerManager.timerDurationMinutes.value * 60)
            val isStopwatchSessionActive = (isStopwatchActive || isPaused) && stopwatchSeconds > 0
            
            val hasActiveSession = isTimerRunning || isStopwatchActive || isPaused || isPromoSessionActive || isStopwatchSessionActive
            
            if (hasActiveSession) {
                android.util.Log.d("MainActivity", "Active focus session detected on launch. Redirecting to TIMER.")
                viewModel.navigateTo(Screen.TIMER)
            } else {
                // 2. Check for first launch of the day sleep redirect
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val autoRedirectEnabled = prefs.getBoolean("auto_redirect_sleep_first_launch", true)
                if (autoRedirectEnabled) {
                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    val lastRedirectDate = prefs.getString("last_redirect_sleep_date", "")
                    if (lastRedirectDate != todayStr) {
                        prefs.edit().putString("last_redirect_sleep_date", todayStr).apply()
                        android.util.Log.d("MainActivity", "First launch of the day today ($todayStr). Redirecting to HEALTH sleep tab.")
                        viewModel.navigateTo(Screen.HEALTH)
                        viewModel.showSleepDetailsDirectly.value = true
                    }
                }
            }
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_SEND) {
            val componentName = intent.component
            val className = componentName?.className ?: ""
            val flowType = when {
                className.contains("JournalShareActivity") -> "journal"
                className.contains("SharedFolderShareActivity") -> "shared_folder"
                className.contains("PrivateNoteShareActivity") -> "private_note"
                else -> null
            } ?: return

            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.getItemAt(0)?.uri
            val type = intent.type ?: ""

            if (uri != null) {
                var name = ""
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIdx)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to query display name from contentResolver", e)
                }
                if (name.isEmpty()) {
                    name = "shared_file_" + System.currentTimeMillis()
                }

                val data = com.example.ui.SharedFileIntentData(
                    uri = uri,
                    name = name,
                    mimeType = type,
                    flowType = flowType
                )
                viewModel.setSharedFileIntent(data)
                intent.action = null
            }
        }
    }

    private fun checkTimerNavigation(intent: Intent?) {
        if (intent?.getBooleanExtra("SHOW_TIMER_PAGE", false) == true || intent?.getBooleanExtra("SHOW_FULL_SCREEN_TIMER", false) == true) {
            val isChatNotif = intent.getBooleanExtra("IS_CHAT_NOTIFICATION", false)
            if (!isChatNotif) {
                viewModel.navigateTo(Screen.TIMER)
            } else {
                viewModel.navigateTo(Screen.MESSAGES)
            }
        }
    }

    private fun checkAppBlockInterceptions(intent: Intent?) {
        if (intent == null) return
        
        if (intent.getBooleanExtra("SHOW_BLOCKS_PAGE", false)) {
            viewModel.navigateTo(Screen.SETTINGS)
            intent.removeExtra("SHOW_BLOCKS_PAGE")
            getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("direct_to_blocks", true)
                .apply()
        }

        if (intent.getBooleanExtra("SHOW_INTERCEPT_PROMPT", false)) {
            val pkg = intent.getStringExtra("INTERCEPTED_PACKAGE")
            if (!pkg.isNullOrEmpty()) {
                interceptedAppSessionQuery.value = pkg
            }
            intent.removeExtra("SHOW_INTERCEPT_PROMPT")
            intent.removeExtra("INTERCEPTED_PACKAGE")
        }
    }

    private fun getNavigationItems(order: List<Screen>): List<NavigationItem> {
        val mapping = mapOf(
            Screen.TASKS to NavigationItem(Screen.TASKS, Icons.Default.List, "Tasks"),
            Screen.CALENDAR to NavigationItem(Screen.CALENDAR, Icons.Default.DateRange, "Calendar"),
            Screen.TIMER to NavigationItem(Screen.TIMER, Icons.Default.PlayArrow, "Timer"),
            Screen.LIVE_SPHERE to NavigationItem(Screen.LIVE_SPHERE, Icons.Default.Share, "Friends Focus Details"),
            Screen.ARENA to NavigationItem(Screen.ARENA, Icons.Default.EmojiEvents, "Arena"),
            Screen.FOCUS_LOCKER to NavigationItem(Screen.FOCUS_LOCKER, Icons.Default.Lock, "Locker"),
            Screen.HABITS to NavigationItem(Screen.HABITS, Icons.Default.CheckCircle, "Habits"),
            Screen.COUNTDOWN to NavigationItem(Screen.COUNTDOWN, Icons.Default.Notifications, "Countdown"),
            Screen.JOURNAL to NavigationItem(Screen.JOURNAL, Icons.Default.Book, "Journal"),
            Screen.KEEP_NOTES to NavigationItem(Screen.KEEP_NOTES, Icons.Default.Note, "Keep Notes"),
            Screen.CONTACTS to NavigationItem(Screen.CONTACTS, Icons.Default.AccountBox, "Contacts"),
            Screen.MESSAGES to NavigationItem(Screen.MESSAGES, Icons.Default.Forum, "Messages"),
            Screen.FILE_EXPLORER to NavigationItem(Screen.FILE_EXPLORER, Icons.Default.Folder, "File Explorer"),
            Screen.FINANCES to NavigationItem(Screen.FINANCES, Icons.Default.MonetizationOn, "Finances"),
            Screen.DEEPA_AI to NavigationItem(Screen.DEEPA_AI, Icons.Default.Face, "Deepa AI"),
            Screen.SEARCH to NavigationItem(Screen.SEARCH, Icons.Default.Search, "Search"),
            Screen.ANALYTICS to NavigationItem(Screen.ANALYTICS, Icons.Default.Star, "Analytics"),
            Screen.SETTINGS to NavigationItem(Screen.SETTINGS, Icons.Default.Settings, "Settings"),
            Screen.HEALTH to NavigationItem(Screen.HEALTH, Icons.Default.Favorite, "Health"),
            Screen.FLEX_GRID_STUDIO to NavigationItem(Screen.FLEX_GRID_STUDIO, Icons.Default.Dashboard, "Layout Studio")
        )
        return order.mapNotNull { mapping[it] }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            try {
                com.example.util.FocusTimerManager.stopAlarm()
                com.example.util.BackgroundMediaManager.stop()
                com.example.util.FocusTimerManager.setSoundEnabled(this, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val streams = intArrayOf(
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.STREAM_RING,
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.STREAM_VOICE_CALL
                )
                for (stream in streams) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            audioManager.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.setStreamMute(stream, true)
                        }
                    } catch (e: Exception) {
                        try {
                            audioManager.setStreamVolume(stream, 0, 0)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        }

        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            // Global Screen Shortcuts: Ctrl + Shift + [Key]
            if (event.isCtrlPressed && event.isShiftPressed) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_S -> {
                        val current = viewModel.currentScreen.value
                        if (current == Screen.SETTINGS) {
                            viewModel.updateSettingsActivePage(0)
                        } else {
                            viewModel.navigateTo(Screen.SETTINGS)
                        }
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_T -> { viewModel.navigateTo(Screen.TIMER); return true }
                    android.view.KeyEvent.KEYCODE_C -> { viewModel.navigateTo(Screen.CALENDAR); return true }
                    android.view.KeyEvent.KEYCODE_K -> { viewModel.navigateTo(Screen.KEEP_NOTES); return true }
                    android.view.KeyEvent.KEYCODE_J -> { viewModel.navigateTo(Screen.JOURNAL); return true }
                    android.view.KeyEvent.KEYCODE_H -> { viewModel.navigateTo(Screen.HABITS); return true }
                    android.view.KeyEvent.KEYCODE_F -> { viewModel.navigateTo(Screen.FINANCES); return true }
                    android.view.KeyEvent.KEYCODE_D -> { viewModel.navigateTo(Screen.DEEPA_AI); return true }
                    android.view.KeyEvent.KEYCODE_N -> { viewModel.navigateTo(Screen.TASKS); return true }
                    android.view.KeyEvent.KEYCODE_A -> { viewModel.navigateTo(Screen.ANALYTICS); return true }
                    android.view.KeyEvent.KEYCODE_L -> { viewModel.navigateTo(Screen.LIVE_SPHERE); return true }
                    android.view.KeyEvent.KEYCODE_O -> { viewModel.navigateTo(Screen.FOCUS_LOCKER); return true }
                    android.view.KeyEvent.KEYCODE_X -> { viewModel.navigateTo(Screen.FILE_EXPLORER); return true }
                    android.view.KeyEvent.KEYCODE_G -> { viewModel.navigateTo(Screen.CONTACTS); return true }
                    android.view.KeyEvent.KEYCODE_R -> { viewModel.navigateTo(Screen.ARENA); return true }
                    android.view.KeyEvent.KEYCODE_P -> { viewModel.navigateTo(Screen.COUNTDOWN); return true }
                    android.view.KeyEvent.KEYCODE_U -> { viewModel.navigateTo(Screen.HEALTH); return true }
                }
            }

            // Settings Pages & Actions: Alt + [Key]
            if (event.isAltPressed) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_1 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(1)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_D, android.view.KeyEvent.KEYCODE_2 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(17)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_3 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(16)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_4 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(11)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_5 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(12)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_6 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(2)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_7 -> {
                        viewModel.navigateTo(Screen.FOCUS_LOCKER)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_8 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(3)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_C, android.view.KeyEvent.KEYCODE_9 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(4)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_H, android.view.KeyEvent.KEYCODE_0 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(5)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_W -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(21)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_A -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(6)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_J -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(7)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_I -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(8)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_E -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(9)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_F -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(10)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_L -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(13)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_M -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(14)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_P -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(19)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_O -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(18)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_X -> {
                        viewModel.logout()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_Y -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(0)
                        viewModel.setShowUninstallConfirm(true)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_Q -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        viewModel.updateSettingsActivePage(0)
                        return true
                    }
                }
            }
        }

        val handled = super.dispatchKeyEvent(event)
        if (handled) return true

        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (viewModel.currentScreen.value == Screen.TIMER) {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_F || event.keyCode == android.view.KeyEvent.KEYCODE_F11) {
                    viewModel.setTimerImmersive(true)
                    return true
                } else if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
                    viewModel.setTimerImmersive(false)
                    return true
                }
            }
        }

        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::database.isInitialized && com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext)) {
            android.util.Log.i("MainActivity", "App destroying, triggering final blocking backup to Google Drive...")
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                    com.example.util.GoogleDriveSyncManager.backupAllAppData(applicationContext, database)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PastedLinksHubView(viewModel: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pastedLinks by viewModel.pastedLinks.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    // Dialog state for renaming
    var editingLink by remember { mutableStateOf<com.example.ui.PastedLinkItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    // Read clipboard (Disabled: URL recognition must happen only if typed inside the app, no top suggestion banner)
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Suggestion Banner
        if (clipboardUrl != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Clipboard link",
                        tint = WaterBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Copied URL Detected",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = clipboardUrl ?: "",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            val url = clipboardUrl
                            if (url != null) {
                                viewModel.addRecognizedLink(url)
                                clipboardUrl = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("RECOGNIZE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { clipboardUrl = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // 2. Horizontal Scroll of Bubbles
        if (pastedLinks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "LINKS",
                    color = WaterBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    items(pastedLinks, key = { it.id }) { item ->
                        val isZoom = item.isZoom
                        val isYouTube = item.url.contains("youtube", ignoreCase = true) || item.url.contains("youtu.be", ignoreCase = true)
                        
                        val bubbleBgColor = when {
                            isZoom -> Color(0xFF1B263B)
                            isYouTube -> Color(0xFF2C1B1B)
                            else -> Color(0xFF1E1E24)
                        }
                        
                        val bubbleBorderColor = when {
                            isZoom -> Color(0xFF29B6F6).copy(alpha = 0.4f)
                            isYouTube -> Color(0xFFEF5350).copy(alpha = 0.4f)
                            else -> Color.White.copy(alpha = 0.12f)
                        }

                        val bubbleIconTint = when {
                            isZoom -> Color(0xFF29B6F6)
                            isYouTube -> Color(0xFFEF5350)
                            else -> WaterBlue
                        }

                        val bubbleIcon = when {
                            isZoom -> Icons.Default.VideoCall
                            isYouTube -> Icons.Default.PlayCircle
                            else -> Icons.Default.Link
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = bubbleBgColor),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorderColor),
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLongClick = {
                                        editingLink = item
                                        renameText = item.heading
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Logo / Favicon
                                if (!item.logoUrl.isNullOrBlank() && !isZoom) {
                                    AsyncImage(
                                        model = item.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = bubbleIcon,
                                        contentDescription = null,
                                        tint = bubbleIconTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = item.heading,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )

                                IconButton(
                                    onClick = { viewModel.deletePastedLink(item.id) },
                                    modifier = Modifier.size(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Renaming Dialog
    if (editingLink != null) {
        AlertDialog(
            onDismissRequest = { editingLink = null },
            title = { Text("Edit Link Heading", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Heading / Title", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val link = editingLink
                        if (link != null && renameText.isNotBlank()) {
                            viewModel.updatePastedLinkHeading(link.id, renameText)
                        }
                        editingLink = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                ) {
                    Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLink = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF161618),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

data class NavigationItem(val screen: Screen, val icon: ImageVector, val label: String)
