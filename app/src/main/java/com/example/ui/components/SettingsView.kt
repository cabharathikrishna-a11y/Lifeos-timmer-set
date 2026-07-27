package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import com.example.ui.theme.WaterBlue
import com.example.util.AppBlockHelper
import com.example.util.AppLockHelper
import com.example.util.AppUpdateManager
import com.example.util.GoogleContactsSyncManager
import com.example.util.GoogleDriveSyncManager
import com.example.util.GoogleFitSyncManager
import com.example.util.GoogleTasksSyncManager
import com.example.util.UpdateStatus
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Application
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AlertRed
import com.example.ui.theme.Charcoal
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import java.io.ByteArrayOutputStream
import kotlin.random.Random



val WaterBlue = Color(0x0FF38B0F2) // Shared WaterBlue theme accent color

data class SettingsRowData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val action: () -> Unit
)

data class SettingsCategoryData(
    val title: String,
    val tabId: Int, // 1: System & AI, 2: Productivity, 3: Logs & Finance, 4: Security, 5: Account
    val items: List<SettingsRowData>
)

@Composable
fun SettingsPageScope(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
fun SettingsView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val directToBlocks = remember {
        val shared = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val value = shared.getBoolean("direct_to_blocks", false)
        if (value) {
            shared.edit().putBoolean("direct_to_blocks", false).apply()
        }
        value
    }
    var activePage by remember { mutableStateOf(if (directToBlocks) 14 else 0) }
    val showUninstallConfirm by viewModel.showUninstallConfirm.collectAsState()
    val showDeleteAllDataConfirm by viewModel.showDeleteAllDataConfirm.collectAsState()

    val vmActivePage by viewModel.settingsActivePage.collectAsState()
    LaunchedEffect(vmActivePage) {
        if (activePage != vmActivePage) {
            activePage = vmActivePage
        }
    }
    LaunchedEffect(activePage) {
        if (activePage != vmActivePage) {
            viewModel.updateSettingsActivePage(activePage)
        }
    }
    val isAdminUser by viewModel.isAdmin.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isKeyboardConnected = remember(configuration) {
        val configKeyboard = configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY ||
                configuration.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO
        if (configKeyboard) {
            true
        } else {
            var hasHardwareKeyboard = false
            try {
                val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
                if (inputManager != null) {
                    val deviceIds = android.view.InputDevice.getDeviceIds()
                    for (id in deviceIds) {
                        val device = android.view.InputDevice.getDevice(id)
                        if (device != null && !device.isVirtual && (device.sources and android.view.InputDevice.SOURCE_KEYBOARD) == android.view.InputDevice.SOURCE_KEYBOARD) {
                            if (device.keyboardType == 2) {
                                hasHardwareKeyboard = true
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore exception
            }
            hasHardwareKeyboard
        }
    }

    val allCategories = remember(isKeyboardConnected) {
        listOf(
            SettingsCategoryData(
                title = "Core Systems & AI",
                tabId = 1,
                items = buildList {
                    add(SettingsRowData("1. GENERAL SYSTEM", "Tab alignment, navigation bar reordering, style configurations", Icons.Default.Settings, Color(0xFF2196F3)) { activePage = 1 })
                    add(SettingsRowData("DIAGNOSTICS & BACKGROUND", "Fix stopwatch lockscreen freeze & background recording on Samsung/Oppo/Lenovo/Moto", Icons.Default.Info, Color(0xFFE53935)) { activePage = 17 })
                    add(SettingsRowData("APP UPDATE CENTER", "Check for updates, manage background downloads, authenticate tester", Icons.Default.Refresh, Color(0xFF4CAF50)) { activePage = 16 })
                    add(SettingsRowData("2. DEEPA AI BRAIN", "Offline model caching, memories vault management", Icons.Default.Face, Color(0xFF00E5FF)) { activePage = 11 })
                    add(SettingsRowData("3. BACKUP & RESTORE", "JSON manual database import & security exports", Icons.Default.Refresh, Color(0xFFFFB300)) { activePage = 12 })
                    if (isKeyboardConnected) {
                        add(SettingsRowData("KEYBOARD SHORTCUTS HELP", "View all connected physical keyboard shortcuts & mappings", Icons.Default.Keyboard, Color(0xFF9C27B0)) { activePage = 99 })
                    }
                }
            ),
            SettingsCategoryData(
                title = "Productivity Core",
                tabId = 2,
                items = listOf(
                    SettingsRowData("4. TIMER CONFIGURATION", "Session periods, default break times, vibration style toggles", Icons.Default.PlayArrow, Color(0xFFFF3D00)) { activePage = 2 },
                    SettingsRowData("STUDY GROUPS (FOCUS LOCKER)", "Multiplayer study groups. Form peer circles to study together offline-first", Icons.Default.Group, Color(0xFFFF3D00)) { viewModel.navigateTo(Screen.FOCUS_LOCKER) },
                    SettingsRowData("5. TASKS ENGINE", "Reminder frequencies, custom vibrators, default lists", Icons.Default.List, Color(0xFF4CAF50)) { activePage = 3 },
                    SettingsRowData("6. CALENDAR PLANNER", "Style layouts, display settings, timeline filters", Icons.Default.DateRange, Color(0xFF9C27B0)) { activePage = 4 },
                    SettingsRowData("7. HABITS TRACKER", "Streak calculations, automatic midnight reset triggers", Icons.Default.Refresh, Color(0xFFFF8F00)) { activePage = 5 },
                    SettingsRowData("8. SLEEP & WAKE-UP ALARM", "Bedtime reminders, wake-up alarms, snooze & alarm states", Icons.Default.Star, Color(0xFF3F51B5)) { activePage = 21 }
                )
            ),
            SettingsCategoryData(
                title = "Logs & Utilities",
                tabId = 3,
                items = listOf(
                    SettingsRowData("8. COUNTDOWNS & ALERTS", "Background notifications, custom alert parameters", Icons.Default.Notifications, Color(0xFF00E676)) { activePage = 6 },
                    SettingsRowData("9. LIFE JOURNAL", "Storage usage indexers, backup matching constraints", Icons.Default.Book, Color(0xFFE91E63)) { activePage = 7 },
                    SettingsRowData("10. CONTACTS DIRECTORY", "Full syncing filters, categories pairing, anniversaries", Icons.Default.AccountBox, Color(0xFF03A9F4)) { activePage = 8 }
                )
            ),
            SettingsCategoryData(
                title = "File & Financials",
                tabId = 3,
                items = listOf(
                    SettingsRowData("11. FILE EXPLORER", "Workspace directories, index preferred storage", Icons.Default.Folder, Color(0xFF8D6E63)) { activePage = 9 },
                    SettingsRowData("12. FINANCIAL LEDGER", "Accounts, custom family members, categories reporting", Icons.Default.MonetizationOn, Color(0xFF4CAF50)) { activePage = 10 }
                )
            ),
            SettingsCategoryData(
                title = "Security & Privacy Settings",
                tabId = 4,
                items = listOf(
                    SettingsRowData("13. SECURE APP LOCK", "Verify code settings, PIN setups, recover questions", Icons.Default.Lock, Color(0xFFE91E63)) { activePage = 13 },
                    SettingsRowData("14. BLOCKS & SCREEN LIMITS", "Establish application constraints, usage warnings", Icons.Default.Block, Color(0xFFD32F2F)) { activePage = 14 },
                    SettingsRowData("15. PERMISSIONS & API CONNECTIONS", "Manage system permissions, Google Drive, and Keep Notes", Icons.Default.CheckCircle, Color(0xFF4CAF50)) { activePage = 19 }
                )
            ),
            SettingsCategoryData(
                title = "Account & Sync",
                tabId = 5,
                items = listOf(
                    SettingsRowData("17. USER INFO PROFILE", "Configure profile name, nickname, and custom avatar", Icons.Default.Person, Color(0xFF4CAF50)) { activePage = 15 },
                    SettingsRowData("HOW TO USE MANUAL", "View user guide and manual instructions at https://lifeosca.web.app/", Icons.Default.Book, Color(0xFF2196F3)) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lifeosca.web.app/"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open manual link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    SettingsRowData("16. DEEP LINKS & SHORTCUTS", "Copy application deep links, automation URI routes & assets", Icons.Default.Share, Color(0xFF03A9F4)) { activePage = 18 },
                    SettingsRowData("LOGOUT", "Sign out from the current online account securely", Icons.Default.ExitToApp, Color(0xFFD32F2F)) { viewModel.logout() },
                    SettingsRowData("DELETE ALL DATA FROM CLOUD TO SYSTEM INTERNALLY", "Permanently delete all data from cloud and system internally, resetting you as a fresh new user", Icons.Default.Delete, Color(0xFFD32F2F)) { viewModel.setShowDeleteAllDataConfirm(true) },
                    SettingsRowData("UNINSTALL & DE-REGISTER", "Securely wipe local data, notify peers on Firebase, and uninstall app", Icons.Default.Delete, Color(0xFFD32F2F)) { viewModel.setShowUninstallConfirm(true) }
                )
            )
        )
    }

    // Dynamic filtering
    val filteredCategories = remember(selectedTab, searchQuery) {
        allCategories.mapNotNull { category ->
            // Filter categories by tab first (only if searchQuery is empty)
            if (searchQuery.isEmpty() && selectedTab != 0 && category.tabId != selectedTab) {
                null
            } else {
                // Filter items by searchQuery
                val matchedItems = if (searchQuery.isEmpty()) {
                    category.items
                } else {
                    category.items.filter { item ->
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.subtitle.contains(searchQuery, ignoreCase = true)
                    }
                }
                if (matchedItems.isNotEmpty()) {
                    category.copy(items = matchedItems)
                } else {
                    null
                }
            }
        }
    }

    when (activePage) {
        0 -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Centered Welcome Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(WaterBlue.copy(alpha = 0.12f), Color.Transparent)
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WaterBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "SETTINGS CENTER",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Configure and personalize your localized Life OS experience.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Modern Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search settings...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF1E1E22),
                        focusedContainerColor = Color(0xFF09090C),
                        unfocusedContainerColor = Color(0xFF09090C),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = TextStyle(fontSize = 14.sp)
                )

                // Quick Category Pills
                if (searchQuery.isEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf("All", "System & AI", "Productivity", "Logs & Finance", "Security", "Account")
                        items(tabs.size) { index ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) WaterBlue else Color(0xFF0C0C0E))
                                    .border(1.dp, if (isSelected) WaterBlue else Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tabs[index],
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.LightGray
                                )
                            }
                        }
                    }
                } else {
                    // Active search indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Results for \"$searchQuery\"",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // LazyColumn for dynamic settings categories
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredCategories.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "No Results",
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No settings matched \"$searchQuery\"",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Check the spelling or try another query",
                                        color = Color.DarkGray,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    } else {
                        filteredCategories.forEach { category ->
                            item {
                                SettingsCategoryGroup(title = category.title) {
                                    category.items.forEachIndexed { idx, item ->
                                        SettingsRowItem(
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            icon = item.icon,
                                            iconBgColor = item.iconBgColor,
                                            onClick = item.action
                                        )
                                        if (idx < category.items.size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFF1E1E22),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(start = 56.dp, end = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        1 -> {
            SettingsGeneralSystemPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        2 -> {
            SettingsTimerConfigurationPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        3 -> {
            SettingsTasksPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        4 -> {
            SettingsSubpageWorkspace(
                title = "Calendar Planner Settings",
                description = "Custom calendar display preferences and layout rules.",
                onBack = { activePage = 0 }
            ) {
                CalendarSettingsSection(viewModel = viewModel)
            }
        }

        5 -> {
            SettingsHabitsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        6 -> {
            SettingsCountdownAlertsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        7 -> {
            SettingsJournalPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        8 -> {
            SettingsContactsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        9 -> {
            SettingsFileExplorerPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        10 -> {
            SettingsFinancialsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        11 -> {
            SettingsDeepaAIPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        12 -> {
            SettingsSubpageWorkspace(
                title = "Backup & Restore",
                description = "Export and import your entire Life OS data via simple JSON backup.",
                onBack = { activePage = 0 }
            ) {
                LifeOSBackupSection(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                GoogleCalendarAndTasksSyncSection(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                FirebaseConfigurationSection(viewModel = viewModel)
            }
        }

        13 -> {
            SettingsSubpageWorkspace(
                title = "Secure App Lock",
                description = "Configure fingerprint/face biometric unlock, secure multi-digit PIN, or alphanumeric Password protection along with backup recovery.",
                onBack = { activePage = 0 }
            ) {
                AppLockSettingsSection()
            }
        }

        14 -> {
            SettingsSubpageWorkspace(
                title = "Blocks & Screen Limits",
                description = "Configure daily tracked limit quotas for Instagram, Facebook, Snapchat, or other manual apps.",
                onBack = { activePage = 0 }
            ) {
                AppBlocksSettingsSection()
            }
        }
        
        15 -> {
            SettingsUserInfoPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }
        
        16 -> {
            SettingsUpdatesPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        17 -> {
            SettingsBackgroundDiagnosticsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        19 -> {
            SettingsPermissionsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        18 -> {
            SettingsDeepLinksPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        20 -> {
            SettingsFitnessSyncTrendsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        21 -> {
            SettingsSubpageWorkspace(
                title = "Sleep & Wake-up Alarm",
                description = "Manage bedtime reminders, morning alarms, snooze duration, and wake-up status logs.",
                onBack = { activePage = 0 }
            ) {
                SettingsSleepWakePage(viewModel = viewModel)
            }
        }

        22 -> {
            SettingsSubpageWorkspace(
                title = "Recompose Firebase Database",
                description = "Verify data structure and clean up non-Google registered user nodes.",
                onBack = { activePage = 0 }
            ) {
                SettingsRecomposeFirebasePage(viewModel = viewModel)
            }
        }

        23 -> {
            com.example.ui.components.FocusLockerScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onBack = { activePage = 0 }
            )
        }

        99 -> {
            SettingsSubpageWorkspace(
                title = "Keyboard Shortcuts Help",
                description = "View and manage keyboard shortcut mappings for physical keyboards.",
                onBack = { activePage = 0 }
            ) {
                SettingsKeyboardShortcutsPage()
            }
        }
    }

    if (showDeleteAllDataConfirm) {
        var isDeleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) viewModel.setShowDeleteAllDataConfirm(false)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Data (Cloud & System)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column {
                    Text(
                        "This action will permanently delete all your data:\n\n" +
                        "1. Cloud Databases: All records in Firestore & Realtime DB (focus logs, tasks, habits, journals, notes, ledger, presence) will be purged.\n" +
                        "2. System Internally: All local Room database tables, application preferences, and cache will be cleared.\n" +
                        "3. Complete Reset: You will be logged out and returned to the Login screen as a fresh new user.\n\n" +
                        "Do you want to proceed?",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    if (isDeleting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFD32F2F))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Deleting cloud & system data...", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        viewModel.setShowDeleteAllDataConfirm(false)
                        viewModel.deleteAllCloudAndSystemData(context)
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete All Data & Reset as New User", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!isDeleting) {
                    TextButton(onClick = { viewModel.setShowDeleteAllDataConfirm(false) }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF141416)
        )
    }

    if (showUninstallConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowUninstallConfirm(false) },
            title = { Text("Secure De-register & Uninstall", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "This action will:\n" +
                    "1. Mark your status as 'uninstalled' on the remote Firebase database so your name automatically and immediately disappears from your friends' focus list and details.\n" +
                    "2. Securely wipe all local databases, task lists, tracking history, and preferences.\n" +
                    "3. Request the Android system uninstallation dialog to delete the app.\n\n" +
                    "Do you want to proceed?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowUninstallConfirm(false)
                        viewModel.deregisterAndUninstall(context) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("De-register & Delete App", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowUninstallConfirm(false) }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }
}

@Composable
fun SettingsCategoryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconBgColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Arrow",
            tint = Color.DarkGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsSubpageWorkspace(
    title: String,
    description: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun CalendarSettingsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val syncStatus by viewModel.calendarSyncStatus.collectAsState()
    val tasksSyncStatus by viewModel.googleTasksSyncStatus.collectAsState()

    val tasksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleTasks(context) { }
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = (permissions[android.Manifest.permission.READ_CALENDAR] ?: false) &&
                        (permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false)
    }

    // Prefs
    val prefs = remember { context.getSharedPreferences("app_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedAccount by remember { mutableStateOf(prefs.getString("selected_calendar_account", null)) }
    var selectedName by remember { mutableStateOf(prefs.getString("selected_calendar_name", null)) }
    var selectedId by remember { mutableStateOf(prefs.getLong("selected_calendar_id", -1L)) }

    // Query calendars if permission is granted
    val calendars = remember(hasPermission) {
        if (hasPermission) {
            com.example.util.GoogleCalendarSyncHelper.getAvailableCalendars(context)
        } else {
            emptyList()
        }
    }

    // Dropdown states
    var accountExpanded by remember { mutableStateOf(false) }
    var nameExpanded by remember { mutableStateOf(false) }

    val uniqueAccounts = remember(calendars) {
        calendars.map { it.accountName }.distinct()
    }

    val filteredNames = remember(calendars, selectedAccount) {
        if (selectedAccount == null) calendars else calendars.filter { it.accountName == selectedAccount }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
            border = BorderStroke(1.dp, Color(0xFF222225)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "GCal",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Google Calendar Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Enable bidirectional background synchronization with Google Calendar. Whenever you open the Calendar or modify tasks, synchronization occurs silently and automatically.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (!hasPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Calendar Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "✓ Calendar Permissions Granted",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Synchronized Account & Calendar",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Dropdown for Google Account
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Account", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { accountExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedAccount ?: "Choose Google Account (Default: First GCal)",
                                    color = if (selectedAccount != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = accountExpanded,
                                onDismissRequest = { accountExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (First Google Account)", color = Color.White) },
                                    onClick = {
                                        selectedAccount = null
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_account")
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        accountExpanded = false
                                    }
                                )
                                uniqueAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc, color = Color.White) },
                                        onClick = {
                                            selectedAccount = acc
                                            // Reset selectedName if not belonging to this account
                                            if (calendars.none { it.accountName == acc && it.displayName == selectedName }) {
                                                selectedName = null
                                                selectedId = -1L
                                            }
                                            prefs.edit()
                                                .putString("selected_calendar_account", acc)
                                                .putString("selected_calendar_name", selectedName)
                                                .putLong("selected_calendar_id", selectedId)
                                                .apply()
                                            accountExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown for Calendar Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Calendar Name", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { nameExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedName ?: "Choose Calendar (Default: Main)",
                                    color = if (selectedName != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = nameExpanded,
                                onDismissRequest = { nameExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (Primary Calendar)", color = Color.White) },
                                    onClick = {
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        nameExpanded = false
                                    }
                                )
                                filteredNames.forEach { cal ->
                                    DropdownMenuItem(
                                        text = { Text(cal.displayName, color = Color.White) },
                                        onClick = {
                                            selectedName = cal.displayName
                                            selectedId = cal.id
                                            prefs.edit()
                                                .putString("selected_calendar_name", cal.displayName)
                                                .putLong("selected_calendar_id", cal.id)
                                                .apply()
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                            Text(syncStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.syncGoogleCalendar(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
        val defaultEmail = googleAccount?.email ?: "cabharathikrishan@gmail.com"
        var selectedTasksAccount by remember { mutableStateOf(prefs.getString("selected_tasks_account", defaultEmail)) }

        val tasksAccountLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    val email = account?.email ?: ""
                    if (email.isNotEmpty()) {
                        selectedTasksAccount = email
                        prefs.edit().putString("selected_tasks_account", email).apply()
                        android.widget.Toast.makeText(context, "Connected to: $email", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Google Account selection failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
            border = BorderStroke(1.dp, Color(0xFF222225)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "GTasks",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Google Tasks Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Enable bidirectional background synchronization with Google Tasks. Tasks without date or time are automatically kept in sync with Google Tasks.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Google Account Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Connected Google Account", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                .clickable {
                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .build()
                                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                    client.signOut().addOnCompleteListener {
                                        tasksAccountLauncher.launch(client.signInIntent)
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedTasksAccount ?: "No Account Connected",
                                color = if (selectedTasksAccount != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                        Text(tasksSyncStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.syncGoogleTasks(context) { intent ->
                                tasksAuthLauncher.launch(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSleepWakePage(viewModel: AppViewModel) {
    val context = LocalContext.current
    val dialogContext = remember(context) {
        var cur = context
        while (cur is android.content.ContextWrapper) {
            if (cur is android.app.Activity) {
                return@remember cur
            }
            cur = cur.baseContext
        }
        context
    }
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    
    var bedtimeEnabled by remember { mutableStateOf(prefs.getBoolean("bedtime_reminder_enabled", true)) }
    var wakeupEnabled by remember { mutableStateOf(prefs.getBoolean("wakeup_alarm_enabled", false)) }
    
    var bedtimeStr by remember { mutableStateOf(com.example.util.SleepTimeHelper.getSleepTime(context) ?: "22:00") }
    var wakeupStr by remember { mutableStateOf(com.example.util.SleepTimeHelper.getWakeUpTime(context) ?: "07:00") }
    
    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    var lastWokeUpTime by remember { mutableStateOf(prefs.getString("actual_wake_up_time_$todayStr", null)) }

    // Format HH:mm string to a nice AM/PM string for display
    fun formatToAmPm(timeStr: String): String {
        return try {
            if (timeStr == "LATE") return "Late Wake-up"
            val parts = timeStr.split(":")
            if (parts.size != 2) return timeStr
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val ampm = if (h >= 12) "PM" else "AM"
            val displayHour = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(java.util.Locale.US, "%d:%02d %s", displayHour, m, ampm)
        } catch (e: Exception) {
            timeStr
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bedtime Reminder Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Bedtime Reminder",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Bedtime Reminder",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Receive a screen notification at your scheduled sleep time reminding you to wind down for bed.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Bedtime Reminder", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = bedtimeEnabled,
                            onCheckedChange = { isChecked ->
                                bedtimeEnabled = isChecked
                                prefs.edit().putBoolean("bedtime_reminder_enabled", isChecked).apply()
                                com.example.util.AlarmScheduler.scheduleBedtimeReminder(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    if (bedtimeEnabled) {
                        HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parts = bedtimeStr.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    android.app.TimePickerDialog(dialogContext, { _, hour, minute ->
                                        val newTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                        bedtimeStr = newTime
                                        com.example.util.SleepTimeHelper.setSleepTime(context, newTime)
                                        com.example.util.AlarmScheduler.scheduleBedtimeReminder(context)
                                    }, h, m, false).show()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Scheduled Bedtime", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to change via clock pop-up", color = Color.Gray, fontSize = 11.sp)
                            }
                            Text(
                                text = formatToAmPm(bedtimeStr),
                                color = WaterBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        // Morning Wake-up Alarm Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Morning Wake-up Alarm",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Morning Wake-up Alarm",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Configure a full-screen morning alarm that wakes you up with custom snooze, dismiss, or literal wake-up logs.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Wake-up Alarm", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = wakeupEnabled,
                            onCheckedChange = { isChecked ->
                                wakeupEnabled = isChecked
                                prefs.edit().putBoolean("wakeup_alarm_enabled", isChecked).apply()
                                com.example.util.AlarmScheduler.scheduleWakeUpAlarm(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    if (wakeupEnabled) {
                        HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parts = wakeupStr.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    android.app.TimePickerDialog(dialogContext, { _, hour, minute ->
                                        val newTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                        wakeupStr = newTime
                                        com.example.util.SleepTimeHelper.setWakeUpTime(context, newTime)
                                        com.example.util.AlarmScheduler.scheduleWakeUpAlarm(context)
                                    }, h, m, false).show()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Scheduled Wake-up Time", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to change via clock pop-up", color = Color.Gray, fontSize = 11.sp)
                            }
                            Text(
                                text = formatToAmPm(wakeupStr),
                                color = WaterBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        // Alarm Logs / Statistics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sleep Logs",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Today's Sleep Log",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Actual Wake Up Logged", color = Color.Gray, fontSize = 13.sp)
                        Text(
                            text = if (lastWokeUpTime != null) formatToAmPm(lastWokeUpTime!!) else "No wake-up logged yet",
                            color = if (lastWokeUpTime != null) Color.Green else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
    }
}

@Composable
fun SettingsRecomposeFirebasePage(viewModel: AppViewModel) {
    val logs by viewModel.recomposeLogs.collectAsState()
    val status by viewModel.recomposeStatus.collectAsState()
    val lazyListState = rememberLazyListState()

    // Auto scroll logs to the bottom as they are added
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                border = BorderStroke(1.dp, Color(0xFF33333C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Firebase Database Integrity",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recomposing the database verifies the data structure of all active records and cleans up unverified, legacy, or invalid user nodes from Firebase (such as mock usernames and non-Google registered accounts). This optimizes query speeds and protects privacy.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

        // Action & Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                border = BorderStroke(1.dp, Color(0xFF33333C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Operations Status",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusText = when (status) {
                                "idle" -> "System Idle - Ready"
                                "running" -> "Executing Database Audit..."
                                "success" -> "Optimization Completed"
                                "error" -> "Process Interrupted"
                                else -> "Unknown"
                            }
                            val statusColor = when (status) {
                                "idle" -> Color(0xFF90A4AE)
                                "running" -> Color(0xFF29B6F6)
                                "success" -> Color(0xFF66BB6A)
                                "error" -> Color(0xFFEF5350)
                                else -> Color.White
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        when (status) {
                            "running" -> {
                                CircularProgressIndicator(
                                    color = Color(0xFF00796B),
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                            "success" -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            "error" -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Idle",
                                    tint = Color(0xFF90A4AE),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.recomposeFirebase() },
                        enabled = status != "running",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00796B),
                            disabledContainerColor = Color(0xFF004D40).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("recompose_firebase_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status == "running") "AUDITING FIREBASE..." else "RECOMPOSE FIREBASE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

        // Live Console Logs
            Text(
                text = "Live Execution Logs",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0C0F))
                    .border(1.dp, Color(0xFF222226), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Console waiting. Click 'RECOMPOSE FIREBASE' above to initiate process.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs.size) { idx ->
                            val log = logs[idx]
                            val textColor = when {
                                log.contains("WARNING") -> Color(0xFFFFB300)
                                log.contains("Error") || log.contains("Exception") -> Color(0xFFEF5350)
                                log.contains("Successfully") || log.contains("Complete") || log.contains("Successfully deleted") -> Color(0xFF81C784)
                                else -> Color(0xFFCFD8DC)
                            }
                            Text(
                                text = log,
                                color = textColor,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
    }
}


@Composable
fun SettingsBackgroundDiagnosticsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isIgnoringOptimizations by remember { mutableStateOf(checkBatteryOptimizations(context)) }
    var selectedBrandTab by remember { mutableStateOf("samsung") }
    var fcmToken by remember { mutableStateOf("Fetching FCM Token...") }
    var isFcmWorking by remember { mutableStateOf<Boolean?>(null) }

    // Re-check optimization status when entering or active
    LaunchedEffect(Unit) {
        isIgnoringOptimizations = checkBatteryOptimizations(context)
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fcmToken = task.result ?: "Token is null"
                    isFcmWorking = true
                } else {
                    fcmToken = "Error: ${task.exception?.message ?: "Unknown error"}"
                    isFcmWorking = false
                }
            }
        } catch (e: Exception) {
            fcmToken = "FCM Error or not initialized: ${e.message}"
            isFcmWorking = false
        }
    }

    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "Diagnostics & Background Sync",
            description = "Fix stopwatch freezing and background focus recording issues on Samsung, Oppo, Lenovo, and Motorola.",
            onBack = onBack
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Status Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, if (isIgnoringOptimizations) Color(0xFF2E7D32) else Color(0xFFC62828)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isIgnoringOptimizations) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    else Color(0xFFC62828).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isIgnoringOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Status Icon",
                                tint = if (isIgnoringOptimizations) Color(0xFF4CAF50) else Color(0xFFE53935),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isIgnoringOptimizations) "BACKGROUND CONFIGURATION: OPTIMAL" else "BACKGROUND CONFIGURATION: RESTRICTED",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIgnoringOptimizations) Color(0xFF4CAF50) else Color(0xFFE53935),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isIgnoringOptimizations) {
                                "Your device allows Life OS to operate with unrestricted background access. Stopwatches and focus timers will tick continuously, even when your screen is locked."
                            } else {
                                "Android's battery-saving system is active for Life OS. On many devices (especially Samsung, Oppo, Lenovo, Motorola), the system freezes background processes or terminates timers when the screen locks."
                            },
                            fontSize = 11.5.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                requestIgnoreBatteryOptimizations(context)
                                // Refresh status slightly after launching
                                isIgnoringOptimizations = checkBatteryOptimizations(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIgnoringOptimizations) Color(0xFF2E7D32) else WaterBlue,
                                contentColor = if (isIgnoringOptimizations) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Battery Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isIgnoringOptimizations) "Open Battery Settings Again" else "Disable Battery Optimization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. Explanations of why it freezes
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070709)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E22)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WHY TIMERS & STOPWATCHES FREEZE",
                            color = WaterBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. CPU Standby: When the screen turns off, Android suspends CPU cores to save power, freezing local clocks.\n" +
                                    "2. Aggressive Custom Skins: Samsung (OneUI), Oppo (ColorOS), Lenovo, and Motorola utilize aggressive OEM managers that kill background services or suppress periodic network/local state sync requests.\n" +
                                    "3. Network Restrictions: In sleep mode, background cellular and Wi-Fi sync are deferred, meaning focus data updates aren't logged in real-time until you unlock.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 3. OEM Specific Workaround Tabs
                Text(
                    text = "OEM-SPECIFIC FIXES",
                    color = WaterBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0E), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "samsung" to "Samsung",
                        "oppo" to "Oppo",
                        "lenovo" to "Lenovo",
                        "motorola" to "Moto",
                        "others" to "Others"
                    ).forEach { (key, label) ->
                        val isSelected = selectedBrandTab == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue else Color.Transparent)
                                .clickable { selectedBrandTab = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Workaround Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E22)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedBrandTab) {
                            "samsung" -> {
                                Text("Samsung (One UI) Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Set Battery to Unrestricted:\n" +
                                            "   • Long press the Life OS app icon -> Tap \"App info\" (or Go to Settings -> Apps -> Life OS).\n" +
                                            "   • Tap \"Battery\".\n" +
                                            "   • Select \"Unrestricted\" (the default is usually Optimized or Restricted).\n\n" +
                                            "2. Add to Never Sleeping Apps:\n" +
                                            "   • Go to Settings -> Battery and device care -> Battery -> Background usage limits.\n" +
                                            "   • Tap \"Never sleeping apps\".\n" +
                                            "   • Click \"+\" in the top right, select \"Life OS\" and click Add.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "oppo" -> {
                                Text("Oppo / OnePlus / Realme (ColorOS) Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Enable Background Execution:\n" +
                                            "   • Settings -> Apps -> App management -> Life OS.\n" +
                                            "   • Tap \"Battery usage\".\n" +
                                            "   • Toggle ON \"Allow background activity\" and \"Allow auto-launch\".\n\n" +
                                            "2. Lock in Recent Apps:\n" +
                                            "   • Swipe up to open your Recent Apps screen.\n" +
                                            "   • Tap the three-dots/options icon above the Life OS card.\n" +
                                            "   • Select \"Lock\" to prevent the ColorOS memory sweeper from cleaning the background thread.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "lenovo" -> {
                                Text("Lenovo Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Disable App Restriction:\n" +
                                            "   • Open Settings -> Apps -> Life OS.\n" +
                                            "   • Tap \"Battery\".\n" +
                                            "   • Select \"Unrestricted\".\n\n" +
                                            "2. Lenovo Power Management (ZUI):\n" +
                                            "   • Open the built-in ZUI Security / Power Manager app.\n" +
                                            "   • Look for \"Auto-start\" or \"Background app management\".\n" +
                                            "   • Allow Life OS to start and stay active in the background.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "motorola" -> {
                                Text("Motorola Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Disable Battery Saver Optimization:\n" +
                                            "   • Go to Settings -> Apps & notifications -> Special app access -> Battery optimization.\n" +
                                            "   • Filter by \"All apps\", select \"Life OS\" and choose \"Don't optimize\".\n\n" +
                                            "2. Enable Unrestricted Usage:\n" +
                                            "   • Go to Settings -> Apps -> Life OS -> Battery.\n" +
                                            "   • Select \"Unrestricted\" to grant standard foreground service background permissions without system suspension.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "others" -> {
                                Text("General & Other Devices (Xiaomi, Vivo, etc.)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Xiaomi (MIUI / HyperOS):\n" +
                                            "   • Settings -> Apps -> Manage apps -> Life OS -> Battery saver -> Select \"No restrictions\". Also enable \"Autostart\".\n\n" +
                                            "2. Vivo (FuntouchOS):\n" +
                                            "   • Settings -> Battery -> High background power consumption -> Enable \"Life OS\".\n\n" +
                                            "3. General Troubleshooting:\n" +
                                            "   • Ensure your device is not in system-wide \"Power Saver Mode\" or \"Ultra Battery Saver\".\n" +
                                            "   • Keep the background notification for the \"KeepAliveService\" active. It prevents the system from categorizing Life OS as idle.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 4. Test Keep-Alive Service Running Status
                val isServiceActive = isKeepAliveServiceRunning(context)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0C)),
                    border = BorderStroke(1.dp, Color(0xFF222226)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FOREGROUND KEEP-ALIVE SERVICE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceActive) "Active and holding a background notification channel." else "Not active or has been suspended.",
                                color = if (isServiceActive) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 10.5.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isServiceActive) Color(0xFF4CAF50) else Color(0xFFD32F2F))
                        )
                    }
                }

                // 5. Firebase Cloud Messaging (FCM) Status
                val clipboardManager = LocalClipboardManager.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0C)),
                    border = BorderStroke(1.dp, if (isFcmWorking == true) Color(0xFF2E7D32) else Color(0xFF222226)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FIREBASE CLOUD MESSAGING (FCM)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (isFcmWorking) {
                                        true -> "FCM Service initialized and active."
                                        false -> "FCM failed or Google Play Services missing."
                                        else -> "Retrieving push notification status..."
                                    },
                                    color = if (isFcmWorking == true) Color(0xFF4CAF50) else Color.Gray,
                                    fontSize = 10.5.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (isFcmWorking) {
                                            true -> Color(0xFF4CAF50)
                                            false -> Color(0xFFD32F2F)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "FCM REGISTRATION TOKEN",
                            color = Color(0xFF03A9F4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF15151A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF22222A), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = fcmToken,
                                color = Color.LightGray,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isFcmWorking == true) {
                                    clipboardManager.setText(AnnotatedString(fcmToken))
                                    Toast.makeText(context, "FCM Token copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "FCM Token is not available to copy.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFcmWorking == true) Color(0xFF0288D1) else Color(0xFF222226),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text(
                                text = "Copy FCM Token",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun checkBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Battery settings not required on this Android version.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Could not open settings automatically.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun isKeepAliveServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
        if ("com.example.service.KeepAliveService" == service.service.className) {
            return true
        }
    }
    return false
}


@Composable
fun LifeOSBackupSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf<String?>(null) }
    
    // Google Drive Integration State
    var hasDrivePermission by remember { mutableStateOf(GoogleDriveSyncManager.hasDrivePermission(context)) }
    var isOperating by remember { mutableStateOf(false) }
    var gdMessage by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var lastSyncTs by remember { mutableStateOf(prefs.getLong("gd_all_last_sync_timestamp", 0L)) }

    var optTasks by remember { mutableStateOf(prefs.getBoolean("backup_option_tasks", true)) }
    var optHabits by remember { mutableStateOf(prefs.getBoolean("backup_option_habits", true)) }
    var optJournal by remember { mutableStateOf(prefs.getBoolean("backup_option_journal", true)) }
    var optFinances by remember { mutableStateOf(prefs.getBoolean("backup_option_finances", true)) }
    var optContacts by remember { mutableStateOf(prefs.getBoolean("backup_option_contacts", true)) }
    var optFiles by remember { mutableStateOf(prefs.getBoolean("backup_option_files", true)) }
    var optSettings by remember { mutableStateOf(prefs.getBoolean("backup_option_settings", true)) }
    var optHealth by remember { mutableStateOf(prefs.getBoolean("backup_option_health", true)) }
    var optNotes by remember { mutableStateOf(prefs.getBoolean("backup_option_notes", true)) }
    var optFocus by remember { mutableStateOf(prefs.getBoolean("backup_option_focus", true)) }

    fun updateBackupOption(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            statusText = "Exporting data..."
            viewModel.exportBackup(context, uri) { success ->
                statusText = if (success) "Export completed successfully!" else "Failed to export data."
            }
        }
    }

    val htmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            statusText = "Exporting offline archive..."
            viewModel.exportHtmlZip(context, uri) { success ->
                statusText = if (success) "HTML Archive exported successfully! (Offline companion ready)" else "Failed to export HTML archive."
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            statusText = "Importing and reconciling..."
            viewModel.importBackup(context, uri) { success ->
                statusText = if (success) "Import completed successfully! Life OS data synced." else "Failed to import backup."
            }
        }
    }

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        if (result.resultCode == Activity.RESULT_OK) {
            gdMessage = "Google Drive successfully authorized! Tap Backup or Restore to align your app data."
        } else {
            gdMessage = "Google Drive authorization declined."
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: ""
                    val displayName = account.displayName ?: ""
                    val idToken = account.idToken
                    val username = email.substringBefore("@")
                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                    
                    prefs.edit()
                        .putString("selected_tasks_account", email)
                        .putString("selected_file_backup_account", email)
                        .apply()
                    
                    hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
                }
            } catch (e: Exception) {
                Log.e("SettingsView", "Google Sign-In failed", e)
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        // --- GOOGLE DRIVE CLOUD BACKUP & RESTORE CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
            border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Google Drive Sync",
                            tint = WaterBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Google Drive Backup",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    if (!hasDrivePermission) {
                        Button(
                            onClick = {
                                val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                                if (googleAccount == null) {
                                    try {
                                        val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                        val driveScopeFile = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")
                                        val webClientId = try {
                                            context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                                        } catch (e: Exception) {
                                            ""
                                        }
                                        val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                            .requestEmail()
                                            .requestScopes(driveScope, driveScopeFile)
                                        if (webClientId.isNotEmpty()) {
                                            gsoBuilder.requestIdToken(webClientId)
                                        }
                                        val gso = gsoBuilder.build()
                                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    scope.launch {
                                        GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                            authResolutionLauncher.launch(intent)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).testTag("connect_drive_settings_btn")
                        ) {
                            Text("CONNECT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B5E20).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CONNECTED",
                                color = Color.Green,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "Encrypts and uploads your complete Life OS workspace—including nested tasks, completed habits, transaction history logs, journal texts, and media files—directly to your secure, private Google Drive AppData folder.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                if (lastSyncTs > 0L) {
                    Text(
                        text = "Last synced: " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTs)),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                if (hasDrivePermission) {
                    if (isOperating) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accessing Google Drive...", color = Color.LightGray, fontSize = 11.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isOperating = true
                                    gdMessage = "Pulling latest data from Google Drive..."
                                    viewModel.restoreAllDataFromGoogleDrive(context, { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }) { restoreSuccess, restoreMsg ->
                                        if (restoreSuccess || restoreMsg.contains("No full app data backup found") || restoreMsg.contains("No existing backup files found")) {
                                            gdMessage = "Pushing latest data to Google Drive..."
                                            viewModel.backupAllDataToGoogleDrive(context, { intent ->
                                                authResolutionLauncher.launch(intent)
                                            }) { backupSuccess, backupMsg ->
                                                isOperating = false
                                                if (backupSuccess) {
                                                    gdMessage = "Sync completed! Pulled and pushed all data successfully."
                                                    lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                                } else {
                                                    gdMessage = "Pull succeeded, but Push failed: $backupMsg"
                                                }
                                            }
                                        } else {
                                            isOperating = false
                                            gdMessage = "Pull failed: $restoreMsg"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp).testTag("drive_full_sync_btn")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Full Sync (Pull & Push)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isOperating = true
                                        gdMessage = "Initiating cloud backup..."
                                        viewModel.backupAllDataToGoogleDrive(context, { intent ->
                                            authResolutionLauncher.launch(intent)
                                        }) { success, msg ->
                                            isOperating = false
                                            gdMessage = msg
                                            if (success) {
                                                lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("drive_backup_all_btn")
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp), tint = WaterBlue)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cloud Backup", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                                }

                                Button(
                                    onClick = {
                                        isOperating = true
                                        gdMessage = "Initiating cloud restore..."
                                        viewModel.restoreAllDataFromGoogleDrive(context, { intent ->
                                            authResolutionLauncher.launch(intent)
                                        }) { success, msg ->
                                            isOperating = false
                                            gdMessage = msg
                                            if (success) {
                                                lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("drive_restore_all_btn")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cloud Restore", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                gdMessage?.let {
                    Text(
                        text = it,
                        color = if (it.contains("Successfully") || it.contains("authorized")) Color.Green else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // --- BACKUP CATEGORIES SELECTION ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
            border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Backup Options",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Configure Backup Options",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "Select which categories should be exported in manual snapshots or synchronized to Google Drive:",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                val items = listOf(
                    Triple("Tasks, Custom Lists & Deadlines", optTasks) { v: Boolean -> optTasks = v; updateBackupOption("backup_option_tasks", v) },
                    Triple("Habits & Streak Completions", optHabits) { v: Boolean -> optHabits = v; updateBackupOption("backup_option_habits", v) },
                    Triple("Journal Texts & Entries", optJournal) { v: Boolean -> optJournal = v; updateBackupOption("backup_option_journal", v) },
                    Triple("Finance Accounts, Ledger & Transactions", optFinances) { v: Boolean -> optFinances = v; updateBackupOption("backup_option_finances", v) },
                    Triple("Contacts & Folders", optContacts) { v: Boolean -> optContacts = v; updateBackupOption("backup_option_contacts", v) },
                    Triple("Local Files & Documents (PDF/Word/Excel)", optFiles) { v: Boolean -> optFiles = v; updateBackupOption("backup_option_files", v) },
                    Triple("App Configuration & Shared Preferences", optSettings) { v: Boolean -> optSettings = v; updateBackupOption("backup_option_settings", v) },
                    Triple("Health & Fitness Records (Steps, Water, Food Diary)", optHealth) { v: Boolean -> optHealth = v; updateBackupOption("backup_option_health", v) },
                    Triple("Pinned & Personal Keep Notes", optNotes) { v: Boolean -> optNotes = v; updateBackupOption("backup_option_notes", v) },
                    Triple("Focus Session History Log", optFocus) { v: Boolean -> optFocus = v; updateBackupOption("backup_option_focus", v) }
                )

                items.forEach { (label, value, onValueChange) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueChange(!value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = Color.LightGray, fontSize = 12.sp)
                        Switch(
                            checked = value,
                            onCheckedChange = onValueChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // --- MANUAL SNAPSHOTS ---
        Text("Manage Manual Snapshots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Export or restore your localized databases, settings, task lists, and history records securely.", color = Color.Gray, fontSize = 11.sp)
        
        Button(
            onClick = {
                exportLauncher.launch("life_os_backup_${System.currentTimeMillis()}.zip")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Manual Backup (ZIP)")
        }
        
        Button(
            onClick = {
                importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "application/x-zip-compressed"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B1E), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import and Reconcile Backup (ZIP/JSON)")
        }

        Button(
            onClick = {
                htmlExportLauncher.launch("life_os_offline_dashboard_${System.currentTimeMillis()}.zip")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3D2E), contentColor = Color.Green),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = Color.Green)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Offline HTML Companion (ZIP)", color = Color.Green)
        }
        
        statusText?.let {
            Text(it, color = if (it.contains("successfully")) Color.Green else Color.Red, fontSize = 12.sp)
        }
    }
}

@Composable
fun AppLockSettingsSection() {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AppLockHelper.isAppLockEnabled(context)) }
    var lockType by remember { mutableStateOf(AppLockHelper.getLockType(context)) }
    var code by remember { mutableStateOf(AppLockHelper.getLockCode(context) ?: "") }
    var biometricsEnabled by remember { mutableStateOf(AppLockHelper.isBiometricsEnabled(context)) }
    var showSetupDialog by remember { mutableStateOf(false) }
    
    // Recovery Questions State
    val questions = remember { AppLockHelper.getSecurityQuestions(context) }
    var q1 by remember { mutableStateOf(questions[0].first) }
    var a1 by remember { mutableStateOf(questions[0].second) }
    var q2 by remember { mutableStateOf(questions[1].first) }
    var a2 by remember { mutableStateOf(questions[1].second) }
    var q3 by remember { mutableStateOf(questions[2].first) }
    var a3 by remember { mutableStateOf(questions[2].second) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Lock Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable App Lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Require PIN or password when opening Life OS", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showSetupDialog = true
                    } else {
                        AppLockHelper.setAppLockEnabled(context, false)
                        AppLockHelper.setLockCode(context, null)
                        isEnabled = false
                        code = ""
                    }
                }
            )
        }
        
        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lock Type", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Select authentication mode", color = Color.Gray, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lockType == "pin",
                        onClick = {
                            lockType = "pin"
                            AppLockHelper.setLockType(context, "pin")
                        },
                        label = { Text("PIN") }
                    )
                    FilterChip(
                        selected = lockType == "password",
                        onClick = {
                            lockType = "password"
                            AppLockHelper.setLockType(context, "password")
                        },
                        label = { Text("Password") }
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Unlock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Allow face or fingerprint unlock if supported", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = biometricsEnabled,
                    onCheckedChange = {
                        biometricsEnabled = it
                        AppLockHelper.setBiometricsEnabled(context, it)
                    }
                )
            }
        }
        
        if (showSetupDialog) {
            AlertDialog(
                onDismissRequest = { showSetupDialog = false },
                title = { Text("Setup Secure Lock") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Configure your security code and security questions to enable recovery in case you forget it.", color = Color.Gray, fontSize = 11.sp)
                        
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(if (lockType == "pin") "Enter PIN (Digits)" else "Enter Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = if (lockType == "pin") KeyboardType.Number else KeyboardType.Password)
                        )
                        
                        Text("Recovery Security Questions", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q1, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a1,
                                onValueChange = { a1 = it },
                                label = { Text("Answer 1") },
                                singleLine = true
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q2, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a2,
                                onValueChange = { a2 = it },
                                label = { Text("Answer 2") },
                                singleLine = true
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q3, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a3,
                                onValueChange = { a3 = it },
                                label = { Text("Answer 3") },
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (code.isNotBlank() && a1.isNotBlank() && a2.isNotBlank() && a3.isNotBlank()) {
                                AppLockHelper.setLockCode(context, code)
                                AppLockHelper.setAppLockEnabled(context, true)
                                AppLockHelper.saveSecurityQuestions(context, q1, a1, q2, a2, q3, a3)
                                AppLockHelper.setSecuritySetupComplete(context, true)
                                isEnabled = true
                                showSetupDialog = false
                            }
                        }
                    ) {
                        Text("Enable App Lock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AppBlocksSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(AppBlockHelper.hasUsageStatsPermission(context)) }
    var blockedApps by remember { mutableStateOf(AppBlockHelper.getBlockedApps(context)) }
    var selectedAppForLimit by remember { mutableStateOf<String?>(null) }
    var limitMinutesText by remember { mutableStateOf("30") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppPackage by remember { mutableStateOf("") }
    
    // State for all installed apps on device
    var installedApps by remember { mutableStateOf<List<com.example.util.AppBlockHelper.AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Refresh permission status when entering and load apps asynchronously
    LaunchedEffect(Unit) {
        hasPermission = AppBlockHelper.hasUsageStatsPermission(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            installedApps = com.example.util.AppBlockHelper.getInstalledApps(context)
            isLoadingApps = false
        }
    }

    // Format usage helper
    fun formatUsageTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Blocks & Usage Limits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Set daily tracked screen-time limit quotas for distracting applications.", color = Color.Gray, fontSize = 11.sp)
        
        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Usage Stats Permission Required", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Life OS requires the Usage Access permission to track open times and enforce screen limits.", color = Color.LightGray, fontSize = 11.sp)
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Grant Permission", fontSize = 12.sp)
                    }
                }
            }
        }
        
        // Quick App Toggles for Instagram and YouTube
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("QUICK APP TOGGLES", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                
                // Instagram Toggle
                val isInstaBlocked = blockedApps.contains("com.instagram.android")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppListIcon(pkg = "com.instagram.android")
                        }
                    }
                    Switch(
                        checked = isInstaBlocked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                AppBlockHelper.addBlockedApp(context, "com.instagram.android")
                            } else {
                                AppBlockHelper.removeBlockedApp(context, "com.instagram.android")
                            }
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                // YouTube Toggle
                val isYoutubeBlocked = blockedApps.contains("com.google.android.youtube")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppListIcon(pkg = "com.google.android.youtube")
                        }
                    }
                    Switch(
                        checked = isYoutubeBlocked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                AppBlockHelper.addBlockedApp(context, "com.google.android.youtube")
                            } else {
                                AppBlockHelper.removeBlockedApp(context, "com.google.android.youtube")
                            }
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }

        // Instagram Advanced Blocker Settings Card
        var useSelectiveIg by remember { mutableStateOf(AppBlockHelper.isIgSelectiveBlockingEnabled(context)) }
        var isIgReelsBlocked by remember { mutableStateOf(AppBlockHelper.isIgReelsBlocked(context)) }
        var isIgStoriesBlocked by remember { mutableStateOf(AppBlockHelper.isIgStoriesBlocked(context)) }
        var isIgExploreBlocked by remember { mutableStateOf(AppBlockHelper.isIgExploreBlocked(context)) }
        var isIgAllowSharedReels by remember { mutableStateOf(AppBlockHelper.isIgAllowSharedReels(context)) }
        var isIgFeedScrollLimit by remember { mutableStateOf(AppBlockHelper.isIgFeedScrollLimit(context)) }
        var isIgReelsMuteAudio by remember { mutableStateOf(AppBlockHelper.isIgReelsMuteAudio(context)) }

        // YouTube Advanced Blocker Settings Card
        var useSelectiveYt by remember { mutableStateOf(AppBlockHelper.isYtSelectiveBlockingEnabled(context)) }
        var isYtShortsBlocked by remember { mutableStateOf(AppBlockHelper.isYtShortsBlocked(context)) }
        var isYtSearchBlocked by remember { mutableStateOf(AppBlockHelper.isYtSearchBlocked(context)) }
        var isYtCommentsBlocked by remember { mutableStateOf(AppBlockHelper.isYtCommentsBlocked(context)) }
        var isYtOnlyAllowApprovedChannels by remember { mutableStateOf(AppBlockHelper.isYtOnlyAllowApprovedChannels(context)) }
        var ytApprovedChannels by remember { mutableStateOf(AppBlockHelper.getYtApprovedChannels(context)) }

        // Snapchat Advanced Blocker Settings Card
        var useSelectiveSnap by remember { mutableStateOf(AppBlockHelper.isSnapSelectiveBlockingEnabled(context)) }
        var isSnapSpotlightBlocked by remember { mutableStateOf(AppBlockHelper.isSnapSpotlightBlocked(context)) }
        var isSnapMapBlocked by remember { mutableStateOf(AppBlockHelper.isSnapMapBlocked(context)) }
        var isSnapDiscoverBlocked by remember { mutableStateOf(AppBlockHelper.isSnapDiscoverBlocked(context)) }

        // Facebook Advanced Blocker Settings Card
        var useSelectiveFb by remember { mutableStateOf(AppBlockHelper.isFbSelectiveBlockingEnabled(context)) }
        var isFbReelsBlocked by remember { mutableStateOf(AppBlockHelper.isFbReelsBlocked(context)) }
        var isFbWatchBlocked by remember { mutableStateOf(AppBlockHelper.isFbWatchBlocked(context)) }
        var isFbStoriesBlocked by remember { mutableStateOf(AppBlockHelper.isFbStoriesBlocked(context)) }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE1306C).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("instagram_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF833AB4).copy(alpha = 0.08f), Color(0xFFE1306C).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title and Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF833AB4), Color(0xFFF77737))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Instagram Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "INSTAGRAM SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Instagram features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle: Use Selective blocking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow Chats while enforcing specific limits below.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveIg,
                        onCheckedChange = { checked ->
                            useSelectiveIg = checked
                            AppBlockHelper.setIgSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFE1306C),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("ig_selective_blocking_switch")
                    )
                }

                if (useSelectiveIg) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Surgical Reels Blocker", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents scrolling or viewing Reels completely. Instantly closes or reverts back.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgReelsBlocked,
                            onCheckedChange = { checked ->
                                isIgReelsBlocked = checked
                                AppBlockHelper.setIgReelsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_reels_switch")
                        )
                    }

                    // 3. Block Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Hides/interdicts user Stories completely. Story viewer is closed on click.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgStoriesBlocked,
                            onCheckedChange = { checked ->
                                isIgStoriesBlocked = checked
                                AppBlockHelper.setIgStoriesBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_stories_switch")
                        )
                    }

                    // 4. Block Explore Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Explore & Search Tab", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Redirects away from the Explore search grid to prevent visual doomscrolling.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgExploreBlocked,
                            onCheckedChange = { checked ->
                                isIgExploreBlocked = checked
                                AppBlockHelper.setIgExploreBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_explore_switch")
                        )
                    }

                    // 5. Allow Shared Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Allow Shared Reel in Messages", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Enables viewing a single reel sent in DMs. Scrolling/swiping to next is instantly blocked & returns to chat.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgAllowSharedReels,
                            onCheckedChange = { checked ->
                                isIgAllowSharedReels = checked
                                AppBlockHelper.setIgAllowSharedReels(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_allow_shared_reels_switch")
                        )
                    }

                    // 6. Feed Scroll Limiter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Feed Scroll Limiter", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Quota: maximum of 5 scroll gestures in main feed per Focus phase before closure.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgFeedScrollLimit,
                            onCheckedChange = { checked ->
                                isIgFeedScrollLimit = checked
                                AppBlockHelper.setIgFeedScrollLimit(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_feed_scroll_limit_switch")
                        )
                    }

                    // 7. Auto-Mute Reels Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-Mute Reels Audio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Eliminates auditory attention traps by forcing Reel mutes on launch.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgReelsMuteAudio,
                            onCheckedChange = { checked ->
                                isIgReelsMuteAudio = checked
                                AppBlockHelper.setIgReelsMuteAudio(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_mute_reels_audio_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // YouTube Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("youtube_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF0000).copy(alpha = 0.08f), Color(0xFF990000).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF0000), Color(0xFF990000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "YouTube Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "YOUTUBE SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific YouTube features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow regular video play while restricting specific distractions.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveYt,
                        onCheckedChange = { checked ->
                            useSelectiveYt = checked
                            AppBlockHelper.setYtSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFF0000),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("yt_selective_blocking_switch")
                    )
                }

                if (useSelectiveYt) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Shorts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block YouTube Shorts", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Instantly intercepts and blocks YouTube Shorts swipe-feeds.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtShortsBlocked,
                            onCheckedChange = { checked ->
                                isYtShortsBlocked = checked
                                AppBlockHelper.setYtShortsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_shorts_switch")
                        )
                    }

                    // 3. Block Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Search Feed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents manual searching of videos during active focus periods.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtSearchBlocked,
                            onCheckedChange = { checked ->
                                isYtSearchBlocked = checked
                                AppBlockHelper.setYtSearchBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_search_switch")
                        )
                    }

                    // 4. Block Comments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Hide Comments Section", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Blocks view of the YouTube comments pane to stop reading/writing distractions.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtCommentsBlocked,
                            onCheckedChange = { checked ->
                                isYtCommentsBlocked = checked
                                AppBlockHelper.setYtCommentsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_comments_switch")
                        )
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 5. Only Allow Whitelisted Channels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Only Allow Whitelisted Channels", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Blocks play/view of any video except those from your approved channels list below.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtOnlyAllowApprovedChannels,
                            onCheckedChange = { checked ->
                                isYtOnlyAllowApprovedChannels = checked
                                AppBlockHelper.setYtOnlyAllowApprovedChannels(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_only_allow_approved_channels_switch")
                        )
                    }

                    if (isYtOnlyAllowApprovedChannels) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Whitelisted Channel Names (comma separated):",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = ytApprovedChannels,
                                onValueChange = { newValue ->
                                    ytApprovedChannels = newValue
                                    AppBlockHelper.setYtApprovedChannels(context, newValue)
                                },
                                placeholder = {
                                    Text("e.g. Marques Brownlee, Kurzgesagt, TEDx", color = Color.DarkGray, fontSize = 11.sp)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("yt_approved_channels_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFF0000),
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedContainerColor = Color(0xFF141419),
                                    unfocusedContainerColor = Color(0xFF0F0F12),
                                    cursorColor = Color(0xFFFF0000)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = "Matches are case-insensitive and allow partial matches. Enter full channel names or keywords separated by commas.",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Snapchat Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFFFC00).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("snapchat_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFC00).copy(alpha = 0.05f), Color(0xFFFFFC00).copy(alpha = 0.02f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFFC00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Snapchat Blocker",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SNAPCHAT SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Snapchat features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow chat, viewing & uploading snaps while blocking feeds.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveSnap,
                        onCheckedChange = { checked ->
                            useSelectiveSnap = checked
                            AppBlockHelper.setSnapSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFFC00),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("snap_selective_blocking_switch")
                    )
                }

                if (useSelectiveSnap) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Spotlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Snapchat Spotlight", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents viewing or scrolling the Spotlight vertical video feeds.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapSpotlightBlocked,
                            onCheckedChange = { checked ->
                                isSnapSpotlightBlocked = checked
                                AppBlockHelper.setSnapSpotlightBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_spotlight_switch")
                        )
                    }

                    // 3. Block Map
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Snap Map", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Restricts access to the location map tab to keep focus private and active.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapMapBlocked,
                            onCheckedChange = { checked ->
                                isSnapMapBlocked = checked
                                AppBlockHelper.setSnapMapBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_map_switch")
                        )
                    }

                    // 4. Block Discover & Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories & Discover Feeds", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Hides user Stories, sub feeds, and show discoveries completely.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapDiscoverBlocked,
                            onCheckedChange = { checked ->
                                isSnapDiscoverBlocked = checked
                                AppBlockHelper.setSnapDiscoverBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_discover_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Facebook Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("facebook_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1877F2).copy(alpha = 0.08f), Color(0xFF1877F2).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Facebook Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FACEBOOK SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Facebook features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow messaging/posts while blocking feeds & videos.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveFb,
                        onCheckedChange = { checked ->
                            useSelectiveFb = checked
                            AppBlockHelper.setFbSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF1877F2),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("fb_selective_blocking_switch")
                    )
                }

                if (useSelectiveFb) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Facebook Reels", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents viewing or scrolling short vertical video Reels.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbReelsBlocked,
                            onCheckedChange = { checked ->
                                isFbReelsBlocked = checked
                                AppBlockHelper.setFbReelsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_reels_switch")
                        )
                    }

                    // 3. Block Watch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Watch & Video Feed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents entering the Watch tab or video panels entirely.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbWatchBlocked,
                            onCheckedChange = { checked ->
                                isFbWatchBlocked = checked
                                AppBlockHelper.setFbWatchBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_watch_switch")
                        )
                    }

                    // 4. Block Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Omit & hide Facebook Stories and story trays on the screen.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbStoriesBlocked,
                            onCheckedChange = { checked ->
                                isFbStoriesBlocked = checked
                                AppBlockHelper.setFbStoriesBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_stories_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Active Block Rules Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active Block Rules", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Manual Add App", tint = Color.LightGray)
                    }
                }
                
                if (blockedApps.isEmpty()) {
                    Text("No app limits configured. Enable blocks on any app in the directory below to restrict its usage.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    blockedApps.forEach { pkg ->
                        val limitMins = AppBlockHelper.getDailyLimitMinutes(context, pkg)
                        val dailyUsageSecs = AppBlockHelper.getDailyUsageSeconds(context, pkg)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppListIcon(pkg = pkg)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Limit: $limitMins min", color = Color(0xFFFFA726), fontSize = 10.sp)
                                    Text("•", color = Color.DarkGray, fontSize = 10.sp)
                                    Text("Today: ${formatUsageTime(dailyUsageSecs)}", color = if (dailyUsageSecs > 0) WaterBlue else Color.Gray, fontSize = 10.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    selectedAppForLimit = pkg
                                    limitMinutesText = limitMins.toString()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    AppBlockHelper.removeBlockedApp(context, pkg)
                                    blockedApps = AppBlockHelper.getBlockedApps(context)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Installed Apps & Screen Time Directory Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Installed Apps & Screen Time", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Lists all user-launchable apps installed. Updates automatically.", color = Color.Gray, fontSize = 10.sp)
                    }
                    IconButton(onClick = {
                        isLoadingApps = true
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                installedApps = com.example.util.AppBlockHelper.getInstalledApps(context)
                                isLoadingApps = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps List", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141419),
                        unfocusedContainerColor = Color(0xFF141419)
                    )
                )

                if (isLoadingApps) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val filteredApps = remember(searchQuery, installedApps) {
                        if (searchQuery.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter {
                                it.label.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Text("No apps match your search.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredApps.forEach { app ->
                                val isBlocked = blockedApps.contains(app.packageName)
                                val dailyUsageSecs = AppBlockHelper.getDailyUsageSeconds(context, app.packageName)
                                val limitMins = AppBlockHelper.getDailyLimitMinutes(context, app.packageName)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AppListIcon(pkg = app.packageName)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Today: ${formatUsageTime(dailyUsageSecs)}",
                                                color = if (dailyUsageSecs > 0) WaterBlue else Color.Gray,
                                                fontSize = 10.sp
                                            )
                                            if (isBlocked) {
                                                Text(
                                                    text = "Limit: $limitMins min",
                                                    color = Color(0xFFFFA726),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isBlocked) {
                                            IconButton(onClick = {
                                                selectedAppForLimit = app.packageName
                                                limitMinutesText = limitMins.toString()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Limit",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (isBlocked) {
                                                    AppBlockHelper.removeBlockedApp(context, app.packageName)
                                                } else {
                                                    AppBlockHelper.addBlockedApp(context, app.packageName)
                                                }
                                                blockedApps = AppBlockHelper.getBlockedApps(context)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBlocked) Color(0xFF2D1515) else Color(0xFF1E1E24),
                                                contentColor = if (isBlocked) Color.Red else Color.LightGray
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = if (isBlocked) "Blocked" else "Block",
                                                fontSize = 10.sp,
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
        
        selectedAppForLimit?.let { pkg ->
            AlertDialog(
                onDismissRequest = { selectedAppForLimit = null },
                title = { Text("Edit App Limit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pkg, color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = limitMinutesText,
                            onValueChange = { limitMinutesText = it },
                            label = { Text("Daily Limit (minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val mins = limitMinutesText.toIntOrNull() ?: 30
                            AppBlockHelper.setDailyLimitMinutes(context, pkg, mins)
                            selectedAppForLimit = null
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedAppForLimit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showAddAppDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppDialog = false },
                title = { Text("Add App via Package Name") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("For unlisted or system package names", color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = newAppPackage,
                            onValueChange = { newAppPackage = it },
                            label = { Text("Package Name (e.g. com.facebook.katana)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAppPackage.isNotBlank()) {
                                AppBlockHelper.addBlockedApp(context, newAppPackage.trim())
                                blockedApps = AppBlockHelper.getBlockedApps(context)
                                showAddAppDialog = false
                                newAppPackage = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAppDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun GoogleCalendarAndTasksSyncSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val calendarSyncStatus by viewModel.calendarSyncStatus.collectAsState()
    val googleTasksSyncStatus by viewModel.googleTasksSyncStatus.collectAsState()
    
    val googleAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    
    // Auth launcher for tasks sync
    val tasksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.syncGoogleTasks(context)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Cloud Backup Sync",
                    tint = WaterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Google Calendar & Tasks Cloud Sync",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            
            Text(
                text = "Securely synchronize your scheduled events directly with Google Calendar, and your task lists with Google Tasks. This provides a robust, visual cloud backup of all your activities and task lists.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            
            HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)

            // Google Calendar Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222225)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                            Text("Google Calendar Backup", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0288D1).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", color = Color(0xFF03A9F4), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        "Syncs your timed schedule blocks directly to your Google Calendar app for visual timeline backups.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(calendarSyncStatus, color = Color.LightGray, fontSize = 11.sp)
                        }
                        
                        Button(
                            onClick = {
                                viewModel.syncGoogleCalendar(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync Calendar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Google Tasks Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222225)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                            Text("Google Tasks Backup", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF388E3C).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        "Synchronizes all lists and tasks bidirectionally with the official Google Tasks cloud API.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(googleTasksSyncStatus, color = Color.LightGray, fontSize = 11.sp)
                        }
                        
                        Button(
                            onClick = {
                                viewModel.syncGoogleTasks(context) { intent ->
                                    tasksAuthLauncher.launch(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync Tasks", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirebaseConfigurationSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var dbUrl by remember { mutableStateOf(prefs.getString("custom_firebase_db_url", com.example.api.FirebaseConfig.DATABASE_URL) ?: com.example.api.FirebaseConfig.DATABASE_URL) }
    var projectId by remember { mutableStateOf(prefs.getString("custom_firebase_project_id", "lifeosca") ?: "lifeosca") }
    var appId by remember { mutableStateOf(prefs.getString("custom_firebase_app_id", "1:432934819080:android:919f6a7b8f1a2a56bcc8bd") ?: "1:432934819080:android:919f6a7b8f1a2a56bcc8bd") }
    var storageBucket by remember { mutableStateOf(prefs.getString("custom_firebase_storage_bucket", "lifeosca.firebasestorage.app") ?: "lifeosca.firebasestorage.app") }
    var firestoreDbId by remember { mutableStateOf(prefs.getString("custom_firestore_database_id", "(default)") ?: "(default)") }
    var realtimeSyncEnabled by remember { mutableStateOf(prefs.getBoolean("enable_firebase_realtime_sync", true)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.WaterBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = "Firebase Config",
                    tint = com.example.ui.theme.WaterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Firebase Realtime Database & Storage",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Text(
                text = "Configure your own Firebase endpoints below. When changed, the app client dynamically reinstantiates Retrofit connections to sync tasks, ledger, profile details, and peer focus sessions in real time.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)

            // Database URL input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Database URL", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = dbUrl,
                    onValueChange = { dbUrl = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Project ID input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Project ID", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // App ID input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase App ID", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Storage Bucket input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Storage Bucket", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = storageBucket,
                    onValueChange = { storageBucket = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Firestore Database ID input (Multiple Databases support)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firestore Database ID (Multiple Databases)", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = firestoreDbId,
                    onValueChange = { firestoreDbId = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("firestore_db_id_input"),
                    placeholder = { Text("(default)", color = Color.DarkGray) }
                )
            }

            // Real-time Sync Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .clickable {
                        realtimeSyncEnabled = !realtimeSyncEnabled
                        prefs.edit().putBoolean("enable_firebase_realtime_sync", realtimeSyncEnabled).apply()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Real-time Synchronization",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Bi-directional database updates are push-triggered in real time",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = realtimeSyncEnabled,
                    onCheckedChange = { value ->
                        realtimeSyncEnabled = value
                        prefs.edit().putBoolean("enable_firebase_realtime_sync", value).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = com.example.ui.theme.WaterBlue,
                        checkedTrackColor = com.example.ui.theme.WaterBlue.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        // Reset to defaults
                        dbUrl = com.example.api.FirebaseConfig.DATABASE_URL
                        projectId = "lifeosca"
                        appId = "1:432934819080:android:919f6a7b8f1a2a56bcc8bd"
                        storageBucket = "lifeosca.firebasestorage.app"
                        firestoreDbId = "(default)"
                        realtimeSyncEnabled = true

                        prefs.edit()
                            .remove("custom_firebase_db_url")
                            .remove("custom_firebase_project_id")
                            .remove("custom_firebase_app_id")
                            .remove("custom_firebase_storage_bucket")
                            .remove("custom_firestore_database_id")
                            .putBoolean("enable_firebase_realtime_sync", true)
                            .apply()

                        com.example.api.Firebase.activeUrl = com.example.api.FirebaseConfig.DATABASE_URL
                        Toast.makeText(context, "Reset to official Life OS Firebase defaults!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24), contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Text("Reset Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (dbUrl.isBlank() || !dbUrl.startsWith("http")) {
                            Toast.makeText(context, "Please enter a valid HTTP/S Firebase Database URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        prefs.edit()
                            .putString("custom_firebase_db_url", dbUrl)
                            .putString("custom_firebase_project_id", projectId)
                            .putString("custom_firebase_app_id", appId)
                            .putString("custom_firebase_storage_bucket", storageBucket)
                            .putString("custom_firestore_database_id", firestoreDbId)
                            .putBoolean("enable_firebase_realtime_sync", realtimeSyncEnabled)
                            .apply()

                        // Dynamically update the Retrofit service active URL
                        com.example.api.Firebase.activeUrl = dbUrl

                        Toast.makeText(context, "Settings saved. Please completely close and restart the app for changes to take effect.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Save Config", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
fun SettingsContactsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleContacts(context) { intent ->
                // Avoid looping
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_CONTACTS] ?: false
        val writeGranted = permissions[android.Manifest.permission.WRITE_CONTACTS] ?: false
        if (!readGranted || !writeGranted) {
            Toast.makeText(context, "Full system contact synchronization requires Contacts permission.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Contacts permissions verified!", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasContactsPermissions(): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val writePerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return readPerm && writePerm
    }

    SettingsPageScope {
        val isContactsSyncPaused by viewModel.isContactsSyncPaused.collectAsState()

        SettingsSubpageWorkspace(
            title = "Contacts Settings",
            description = "Anniversaries and device contact synchronization.",
            onBack = onBack
        ) {
            Text("Search algorithms index display names, emails, and phone indices. Anniversaries are linked to countdown reminders automatically.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Contacts System Sync",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Synchronize contacts created within Life OS with your Android device's native contacts application in real time.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .clickable {
                                if (isContactsSyncPaused) {
                                    if (!hasContactsPermissions()) {
                                        contactsPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CONTACTS,
                                                android.Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                    }
                                }
                                viewModel.setContactsSyncPaused(!isContactsSyncPaused)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pause Real-time Sync",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isContactsSyncPaused) "Real-time updates are PAUSED" else "Real-time updates are ACTIVE",
                                color = if (isContactsSyncPaused) Color.LightGray else WaterBlue,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isContactsSyncPaused,
                            onCheckedChange = { paused ->
                                if (!paused) {
                                    if (!hasContactsPermissions()) {
                                        contactsPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CONTACTS,
                                                android.Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                    }
                                }
                                viewModel.setContactsSyncPaused(paused)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (!hasContactsPermissions()) {
                                contactsPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CONTACTS,
                                        android.Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            } else {
                                viewModel.forceSyncAllContactsToDevice()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_sync_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Save App Contacts to Phone", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    Text(
                        text = "Notes: Syncing transfers First name, Last name, Phone, Email, and Profile picture to raw system contacts. Offline changes won't fetch system contact updates, but editing locally overrides whatever is on the device.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Account and Cloud Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cloud Google Contacts Sync",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Connect a Google account to save, sync and backup contacts with Google Contacts Cloud services.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
                    val defaultEmail = googleAccount?.email ?: "cabharathikrishan@gmail.com"
                    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                    var contactsAccount by remember { mutableStateOf(prefs.getString("selected_contacts_account", defaultEmail)) }
                    var contactsGroup by remember { mutableStateOf(prefs.getString("selected_contacts_group", "LifeOS Contacts Group")) }
                    var autoCloudBackup by remember { mutableStateOf(prefs.getBoolean("auto_cloud_contacts_backup", true)) }

                    LaunchedEffect(Unit) {
                        if (prefs.getString("selected_contacts_account", null) == null) {
                            prefs.edit().putString("selected_contacts_account", defaultEmail).apply()
                        }
                    }

                    val googleSignInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            try {
                                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                                val email = account?.email ?: ""
                                if (email.isNotEmpty()) {
                                    contactsAccount = email
                                    prefs.edit().putString("selected_contacts_account", email).apply()
                                    Toast.makeText(context, "Connected to: $email", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Google Account selection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    var groupDropdownExpanded by remember { mutableStateOf(false) }

                    // Google Account Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Account for Saving Contacts", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                            .requestEmail()
                                            .build()
                                        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                        client.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(client.signInIntent)
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contactsAccount ?: "No Google Account connected",
                                    color = if (contactsAccount != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    // Contacts Group Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Target Contacts Directory / Label", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable { groupDropdownExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contactsGroup ?: "Default My Contacts",
                                    color = if (contactsGroup != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.Gray
                                )
                            }

                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                val groups = listOf("LifeOS Contacts Group", "My Contacts", "Work Contacts", "Family & Friends Label")
                                groups.forEach { grp ->
                                    DropdownMenuItem(
                                        text = { Text(grp, color = Color.White) },
                                        onClick = {
                                            contactsGroup = grp
                                            prefs.edit().putString("selected_contacts_group", grp).apply()
                                            groupDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Auto cloud backup toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .clickable {
                                autoCloudBackup = !autoCloudBackup
                                prefs.edit().putBoolean("auto_cloud_contacts_backup", autoCloudBackup).apply()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Sync to Google Drive",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Automatically upload contact backups to connected Google account",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoCloudBackup,
                            onCheckedChange = { value ->
                                autoCloudBackup = value
                                prefs.edit().putBoolean("auto_cloud_contacts_backup", value).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val googleContactsSyncStatus by viewModel.googleContactsSyncStatus.collectAsState()

                    Button(
                        onClick = {
                            viewModel.syncGoogleContacts(context) { intent ->
                                googleAuthLauncher.launch(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("sync_google_contacts_now_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (googleContactsSyncStatus == "Syncing...") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing with Google...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Google Contacts Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (googleContactsSyncStatus.isNotEmpty() && googleContactsSyncStatus != "Ready") {
                        val isSuccess = googleContactsSyncStatus.contains("successful", ignoreCase = true)
                        Text(
                            text = "Sync Status: $googleContactsSyncStatus",
                            color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE57373),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsCountdownAlertsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("countdown_settings_prefs", android.content.Context.MODE_PRIVATE) }

        var onScreenReminderEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("on_screen_reminder", true))
        }
        var notificationReminderEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("notification_reminder", true))
        }
        var countdownSilentModeEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("countdown_silent_mode", false))
        }
        var countdownReminders by remember {
            mutableStateOf(
                run {
                    val jsonStr = sharedPrefs.getString("reminders_list_json", null)
                    if (!jsonStr.isNullOrEmpty()) {
                        try {
                            val arr = org.json.JSONArray(jsonStr)
                            val list = mutableListOf<CountdownReminder>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(
                                    CountdownReminder(
                                        daysBefore = obj.getInt("daysBefore"),
                                        timeString = obj.getString("timeString")
                                    )
                                )
                            }
                            list
                        } catch (e: Exception) {
                            mutableListOf(CountdownReminder(0, "09:00"))
                        }
                    } else {
                        mutableListOf(CountdownReminder(0, "09:00"))
                    }
                }
            )
        }

        fun saveCountdownSettings(onScreen: Boolean, notif: Boolean, silent: Boolean, list: List<CountdownReminder>) {
            val editor = sharedPrefs.edit()
            editor.putBoolean("on_screen_reminder", onScreen)
            editor.putBoolean("notification_reminder", notif)
            editor.putBoolean("countdown_silent_mode", silent)
            try {
                val arr = org.json.JSONArray()
                list.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("daysBefore", item.daysBefore)
                    obj.put("timeString", item.timeString)
                    arr.put(obj)
                }
                editor.putString("reminders_list_json", arr.toString())
            } catch (e: java.lang.Exception) {}
            editor.apply()
        }

        SettingsSubpageWorkspace(
            title = "Countdown Settings",
            description = "Configure custom reminder categories, schedules and systems.",
            onBack = onBack
        ) {
            // Warning Card description
            Text(
                text = "Warning cards calculate hours relative to deadlines and can trigger background alarms 24 hours prior.",
                color = Color.LightGray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // On-Screen Reminders Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On-Screen Reminders", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Show reminders on application launch screen", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = onScreenReminderEnabled,
                        onCheckedChange = { onScreenReminderEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notification Reminders Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notification Reminders", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Send system-wide push notifications", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = notificationReminderEnabled,
                        onCheckedChange = { notificationReminderEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Silent Mode Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silent Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Displays on-screen but has no sound / vibration", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = countdownSilentModeEnabled,
                        onCheckedChange = { countdownSilentModeEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("countdown_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reminders Interval List Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("REMINDER INTERVAL SCHEDULES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Add customized schedules to run before target events", color = Color.Gray, fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                countdownReminders = (countdownReminders + CountdownReminder(0, "09:00")).toMutableList()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(WaterBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (countdownReminders.isEmpty()) {
                        Text(
                            "No active alerts configured.",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        countdownReminders.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Days Before input
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Days Before", color = Color.Gray, fontSize = 9.sp)
                                    OutlinedTextField(
                                        value = item.daysBefore.toString(),
                                        onValueChange = { newVal ->
                                            val days = newVal.toIntOrNull() ?: 0
                                            val newList = countdownReminders.toMutableList()
                                            newList[index] = newList[index].copy(daysBefore = days)
                                            countdownReminders = newList
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = WaterBlue,
                                            unfocusedBorderColor = Color.DarkGray
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    if (item.daysBefore == 0) {
                                        Text("0 = Event day", color = WaterBlue, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Time Input
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("Time (HH:MM)", color = Color.Gray, fontSize = 9.sp)
                                    OutlinedTextField(
                                        value = item.timeString,
                                        onValueChange = { newVal ->
                                            val newList = countdownReminders.toMutableList()
                                            newList[index] = newList[index].copy(timeString = newVal)
                                            countdownReminders = newList
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = WaterBlue,
                                            unfocusedBorderColor = Color.DarkGray
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        val newList = countdownReminders.toMutableList()
                                        newList.removeAt(index)
                                        countdownReminders = newList
                                    },
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Reminder", tint = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Global Save Button for Countdown Settings
            Button(
                onClick = {
                    // Persist to SharedPreferences and inform user
                    saveCountdownSettings(onScreenReminderEnabled, notificationReminderEnabled, countdownSilentModeEnabled, countdownReminders)
                    Toast.makeText(context, "Countdown Settings Saved!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_countdown_settings_btn")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("SAVE COUNTDOWN SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDeepLinksPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INTEGRATIONS & DEEP LINKS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF09090C))
            )
        },
        containerColor = Color(0xFF09090C)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Integration & Shortcuts Center",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Life OS supports powerful custom URI deep linking. You can create widget buttons, custom shortcuts, or automation scripts to trigger app navigation or run silent background synchronizations directly from your external home screens.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // App Logo link Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Application Logo Assets",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Use these high-resolution launcher logo links to build shortcuts or launcher badges.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val logoUrl = "https://raw.githubusercontent.com/cabharathikrishan/Life.os/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF282830), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Official Logo URL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(logoUrl))
                                    Toast.makeText(context, "Copied Logo URL!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logo", tint = WaterBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = logoUrl,
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Screen Navigation Links
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Screen Deep Links",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Tap any link to copy it. When triggered externally, the app opens directly to that page.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val screens = listOf(
                        "Home Dashboard" to "lifeos://home",
                        "Tasks Engine" to "lifeos://tasks",
                        "Calendar Planner" to "lifeos://calendar",
                        "Timer focus" to "lifeos://timer",
                        "Habits Tracker" to "lifeos://habits",
                        "Life Journal" to "lifeos://journal",
                        "Contacts Directory" to "lifeos://contacts",
                        "File Explorer" to "lifeos://file_explorer",
                        "Financial Ledger" to "lifeos://finances",
                        "Countdown & Alerts" to "lifeos://countdown",
                        "Deepa AI Assistant" to "lifeos://ai_chat",
                        "Onboarding Screen" to "lifeos://onboarding",
                        "System Settings" to "lifeos://settings",
                        "Analytics Center" to "lifeos://analytics",
                        "Search Engine" to "lifeos://search"
                    )

                    screens.forEach { (name, link) ->
                        DeepLinkRow(name = name, uri = link, clipboardManager = clipboardManager, context = context)
                    }
                }
            }

            // Action Trigger Links
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Automation Action Triggers",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Launch these actions silently or navigate instantly to synchronize cloud backends.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val actions = listOf(
                        "Sync Google Calendar" to "lifeos://action/sync_calendar",
                        "Auto-backup Google Drive" to "lifeos://action/backup_drive",
                        "Force Contacts Device Sync" to "lifeos://action/force_contacts_sync",
                        "Check App Updates" to "lifeos://action/check_updates"
                    )

                    actions.forEach { (name, link) ->
                        DeepLinkRow(name = name, uri = link, clipboardManager = clipboardManager, context = context)
                    }
                }
            }

            // Integration Help Info
            Text(
                "Tip: You can use these deep links inside macro apps (like Tasker, Macrodroid, or Bixby Routines) or launcher shortcuts to trigger custom smart buttons in widgets on your Android desktop.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DeepLinkRow(
    name: String,
    uri: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF16161B))
            .clickable {
                clipboardManager.setText(AnnotatedString(uri))
                Toast.makeText(context, "Copied link for $name!", Toast.LENGTH_SHORT).show()
            }
            .border(1.dp, Color(0xFF1E1E22), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(uri, color = WaterBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy link",
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun SettingsDeepaAIPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aiMemories by viewModel.aiMemories.collectAsState()
    val autoAiUpdater by viewModel.autoAiUpdaterEnabled.collectAsState()

    // Local AI model states
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    val downloadingModelId by viewModel.downloadingModelId.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadSpeedMB by viewModel.downloadSpeedMB.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()

    // Dialog state
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var customUrlInput by remember { mutableStateOf("") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importLocalModelFile(uri, context, selectedModelId)
        }
    }

    val registeredFiles by viewModel.files.collectAsState()
    val jsonBackups = remember(registeredFiles) {
        registeredFiles.filter { it.name.endsWith(".json") && it.name.startsWith("ai_memories_") }
    }

    SettingsSubpageWorkspace(
        title = "Deepa AI Brain Settings",
        description = "Manage long-term AI memories, pick local models, and synchronize background upkeeps.",
        onBack = onBack
    ) {
        // --- 1. ONLINE GEMINI POWERED DEEPA AI ENGINE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⚡ ONLINE GEMINI POWERED DEEPA AI ENGINE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Deepa AI runs 100% online in the cloud powered by Google Gemini Models. Access image generation, Veo video creation, Lyria music synthesis, Search Grounding, Maps Grounding, and High Thinking Reasoning.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Capabilities Status Badges
                val capabilities = listOf(
                    "🧠 High Thinking Reasoning (gemini-3.1-pro-preview)",
                    "⚡ General & Fast Chat (gemini-3.5-flash & flash-lite)",
                    "🌐 Live Web Search Grounding (googleSearch Tool)",
                    "📍 Google Maps Places Grounding (googleMaps Tool)",
                    "🎨 Image Studio (gemini-3.1-flash-image-preview & pro)",
                    "🎬 Veo 3 Video Generation (veo-3.1-fast-generate-preview)",
                    "🎵 Lyria Music Synthesis (lyria-3-clip-preview & pro)",
                    "🎙️ Voice Live Audio Conversations (gemini-3.1-flash-live-preview)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    capabilities.forEach { item ->
                        Surface(
                            color = Color(0xFF161C24),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3B5E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- LOCAL AI MODEL CATALOG & HARDWARE SPEC EVALUATOR ---
        ModelCatalogScreen(viewModel = viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. AI SYSTEM STORAGE & DATA VAULT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💾 AI STORAGE & SYSTEM TOOLS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Configure secure local memory vaults, create offline backup logs, or completely purge AI traces.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.backupAiMemories(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Export Memories JSON", color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Restore Memories", color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Completely Delete AI and Data", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. LONG-TERM MEMORY VAULT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Memory Vault Icon",
                        tint = Color(0xFF4285F4)
                    )
                    Text(
                        text = "LONG-TERM MEMORY VAULT",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "This vault stores facts, constraints, and instructions you've explicitly instructed Deepa AI to remember during your conversations. These memories are parsed and securely injected into the AI's contextual reasoning space.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                if (aiMemories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your AI Memory Vault is currently empty.\nInstruct the chatbot to 'remember X' to register preferences.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        aiMemories.forEachIndexed { index, memory ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• \"$memory\"",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                IconButton(
                                    onClick = { viewModel.deleteAiMemory(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Memory",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3.5 PERSONAL VECTOR MEMORY DASHBOARD ---
        PersonalVectorMemoryDashboardCard(context = context)

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3.6 AI AUTOMATED ACTION LOG DASHBOARD ---
        AiActionLogDashboardCard(context = context)

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. AUTOMATED AI UPDATER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Auto Updater Icon",
                            tint = Color(0xFF34A853)
                        )
                        Text(
                            text = "AUTOMATED AI UPDATER",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Switch(
                        checked = autoAiUpdater,
                        onCheckedChange = { viewModel.updateAutoAiUpdaterEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ensures localized prompting instructions, offline language schemas, and response indices auto-update smoothly in the background without manual update prompts.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Engines Status:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (autoAiUpdater) "ACTIVE (IDLE)" else "DISABLED",
                            color = if (autoAiUpdater) Color(0xFF34A853) else Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Synchronization Cadence:", color = Color.Gray, fontSize = 11.sp)
                        Text("Every 12 hours", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Background Check:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (autoAiUpdater) "Today, 10:15 AM (Auto Worker)" else "N/A",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Prompt Core Schema version:", color = Color.Gray, fontSize = 11.sp)
                        Text("v2026.06.19", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. INSTANT OFFLINE AI COMMANDS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Offline Commands Icon",
                        tint = Color(0xFFFBBC05)
                    )
                    Text(
                        text = "INSTANT OFFLINE AI COMMANDS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Perform direct database operations instantly in milliseconds, even without an internet connection. The Offline Cognitive Engine parses your inputs locally and triggers immediate actions.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cheats = listOf(
                        Triple("📋 Tasks", "add task [title] [priority high/low] [30 mins] [list work]", "complete task [title] | delete task [title] | show tasks"),
                        Triple("🔥 Habits", "add habit [name]", "complete habit [name] | delete habit [name] | show habits"),
                        Triple("💵 Ledger", "add expense 150 for coffee | add income 5000", "delete transaction [note/amount] | show finance"),
                        Triple("⏱️ Focus", "start focus timer 25 mins for Study | start stopwatch", "pause focus | reset focus | show focus"),
                        Triple("📝 Journal", "add journal Title: [t] Content: [c]", "read journal [title] | delete journal [title] | list journals"),
                        Triple("👤 Profile", "set username [name]", "Changes your local display moniker instantly"),
                        Triple("☁️ Cloud", "backup database | sync drive", "Triggers secure SQLite backup / Google Drive upload")
                    )

                    cheats.forEach { (cat, cmd1, cmd2) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(cat, color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("👉 `$cmd1`", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("👉 `$cmd2`", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // JSON RESTORE DIALOG
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Text(
                    "Restore AI Memories",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Select a JSON memory backup file registered in your File Explorer documents directory:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (jsonBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No JSON backups found inside /docs.\nCreate a backup first or check file directory.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        ) {
                            jsonBackups.forEach { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                                    onClick = {
                                        viewModel.restoreAiMemories(context, file)
                                        showRestoreDialog = false
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(file.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Size: ${file.size} bytes • Path: ${file.path}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Close", color = Color(0xFF4285F4))
                }
            },
            containerColor = Color(0xFF141414)
        )
    }

    // PURGE CONFIRMATION DIALOG
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    "⚠️ Delete AI System & Memories?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "Are you absolutely sure you want to completely erase your AI model files, wipe the Long-Term Memory Vault, and clear all chatbot message history? This action is offline-permanent and cannot be undone.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completelyDeleteAiData(context)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Yes, Purge Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF141414)
        )
    }
}

// Simple data class for spec display
data class LocalAiModelSpec(
    val id: String,
    val name: String,
    val subtitle: String,
    val specs: String,
    val description: String
)


@Composable
fun SettingsFileExplorerPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isSdCardConnected by remember {
        mutableStateOf(com.example.util.StorageHelper.isExternalStorageConnected(context))
    }
    var preferredStorage by remember {
        mutableStateOf(com.example.util.StorageHelper.getPreferredStorage(context))
    }

    LaunchedEffect(Unit) {
        isSdCardConnected = com.example.util.StorageHelper.isExternalStorageConnected(context)
    }

    SettingsSubpageWorkspace(
        title = "File Explorer Settings",
        description = "Volume paths.",
        onBack = onBack
    ) {
        Text(
            text = "Secure physical file indices reside directly under context sandbox paths.",
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isSdCardConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Primary Data & Media Storage",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select preferred storage target for application records and media files.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    listOf(
                        Pair("internal", "Internal Device Storage"),
                        Pair("sd_card", "Removable SD Card Storage")
                    ).forEach { (key, label) ->
                        val isSelected = preferredStorage == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) WaterBlue else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    preferredStorage = key
                                    com.example.util.StorageHelper.setPreferredStorage(context, key)
                                    Toast.makeText(
                                        context,
                                        "Storage target updated to ${if (key == "sd_card") "SD Card" else "Internal Storage"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (key == "sd_card") Icons.Default.Build else Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isSelected) WaterBlue else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) WaterBlue else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferredStorage = key
                                    com.example.util.StorageHelper.setPreferredStorage(context, key)
                                    Toast.makeText(
                                        context,
                                        "Storage target updated to ${if (key == "sd_card") "SD Card" else "Internal Storage"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = WaterBlue,
                                    unselectedColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Drive File Synchronization Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Google Drive Cloud Save Settings",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure preferred Google account and target directory for file backups & attachments.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
                val defaultEmail = googleAccount?.email ?: "cabharathikrishan@gmail.com"
                val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                var fileBackupAccount by remember { mutableStateOf(prefs.getString("selected_file_backup_account", defaultEmail)) }
                var fileBackupDir by remember { mutableStateOf(prefs.getString("selected_file_backup_dir", "Root Directory (/)")) }
                var customDirInput by remember { mutableStateOf(prefs.getString("custom_file_backup_dir_path", "")) }

                val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                            val email = account?.email ?: ""
                            if (email.isNotEmpty()) {
                                fileBackupAccount = email
                                prefs.edit().putString("selected_file_backup_account", email).apply()
                                Toast.makeText(context, "Connected to: $email", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google Account selection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                var dirDropdownExpanded by remember { mutableStateOf(false) }

                // Google Account selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Connected Google Account", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .build()
                                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                    client.signOut().addOnCompleteListener {
                                        googleSignInLauncher.launch(client.signInIntent)
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fileBackupAccount ?: "No Account Chosen",
                                color = if (fileBackupAccount != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                // Google Drive folder selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target Google Drive Folder", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable { dirDropdownExpanded = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fileBackupDir ?: "Root Directory (/)",
                                color = if (fileBackupDir != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }

                        DropdownMenu(
                            expanded = dirDropdownExpanded,
                            onDismissRequest = { dirDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1B1B22))
                        ) {
                            val directories = listOf("Root Directory (/)", "LifeOS_Files", "Personal_Daily_Archive", "Work_Cloud_Backup", "Custom Path...")
                            directories.forEach { dir ->
                                DropdownMenuItem(
                                    text = { Text(dir, color = Color.White) },
                                    onClick = {
                                        fileBackupDir = dir
                                        prefs.edit().putString("selected_file_backup_dir", dir).apply()
                                        dirDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (fileBackupDir == "Custom Path...") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Custom Folder Path", color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = customDirInput ?: "",
                            onValueChange = { newValue ->
                                customDirInput = newValue
                                prefs.edit().putString("custom_file_backup_dir_path", newValue).apply()
                            },
                            placeholder = { Text("e.g. Backups/Attachments/2026", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = WaterBlue
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloud Viewing & Storage Strategy Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cloud Viewing & Storage Strategy",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure how your device saves internal storage by streaming historical data directly from the cloud.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                var cloudViewingStrategy by remember {
                    mutableStateOf(prefs.getString("cloud_viewing_strategy", "wholly_internal") ?: "wholly_internal")
                }
                var cloudPartialDays by remember {
                    mutableStateOf(prefs.getInt("cloud_partial_days", 30))
                }
                var partialDaysInput by remember {
                    mutableStateOf(cloudPartialDays.toString())
                }

                val strategies = listOf(
                    Triple("wholly_internal", "Wholly Internal (No Cloud)", "All records and media files are stored locally in internal storage. Completely offline-first mode."),
                    Triple("only_cloud", "Only Cloud (Direct Cloud-Viewing)", "Directly list, view, and stream files and journals from the cloud without occupying local device storage."),
                    Triple("partial", "Partial Cloud & Partial Internal (Hybrid)", "Store recent days of data locally on the device; older records are dynamically streamed from the cloud on-demand.")
                )

                strategies.forEach { (strategyKey, title, desc) ->
                    val isSelected = cloudViewingStrategy == strategyKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) WaterBlue.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WaterBlue else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                cloudViewingStrategy = strategyKey
                                prefs.edit().putString("cloud_viewing_strategy", strategyKey).apply()
                                Toast.makeText(
                                    context,
                                    "Strategy updated to: $title",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when (strategyKey) {
                                    "only_cloud" -> Icons.Default.Cloud
                                    "partial" -> Icons.Default.Sync
                                    else -> Icons.Default.Home
                                },
                                tint = if (isSelected) WaterBlue else Color.Gray,
                                modifier = Modifier.size(18.dp),
                                contentDescription = null
                            )
                            Column {
                                Text(
                                    text = title,
                                    color = if (isSelected) WaterBlue else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                cloudViewingStrategy = strategyKey
                                prefs.edit().putString("cloud_viewing_strategy", strategyKey).apply()
                                Toast.makeText(
                                    context,
                                    "Strategy updated to: $title",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = WaterBlue,
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                }

                if (cloudViewingStrategy == "partial") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                        border = BorderStroke(1.dp, Color(0xFF2E2E35)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How many days of data should be stored internally?",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = partialDaysInput,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() }) {
                                            partialDaysInput = newValue
                                            val days = newValue.toIntOrNull() ?: 30
                                            cloudPartialDays = days
                                            prefs.edit().putInt("cloud_partial_days", days).apply()
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = WaterBlue,
                                        unfocusedBorderColor = Color.Gray,
                                        cursorColor = WaterBlue
                                    ),
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("e.g. 30", color = Color.Gray) }
                                )
                                Button(
                                    onClick = {
                                        val days = partialDaysInput.toIntOrNull() ?: 30
                                        prefs.edit().putInt("cloud_partial_days", days).apply()
                                        Toast.makeText(context, "Duration set to $days days", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Apply", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "Records newer than $cloudPartialDays days will be cached locally. Older items will be streamed from cloud.",
                                color = Color.Gray,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsFinancialsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val familyMembers by viewModel.familyMembers.collectAsState()
    val categories by viewModel.financeCategories.collectAsState()
    
    var newMemberName by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedCategoryType by remember { mutableStateOf("EXPENSE") } // "EXPENSE" or "INCOME"

    SettingsSubpageWorkspace(
        title = "Financial Ledger Settings",
        description = "Configure family members and transaction categories.",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION 1: MANAGE FAMILY MEMBERS ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FAMILY MEMBERS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                    Text("Create personal entities to segment assets, liabilities, and transactions.", fontSize = 11.sp, color = Color.Gray)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            placeholder = { Text("Name (e.g. Alice, Bob)", fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newMemberName.isNotBlank()) {
                                    viewModel.createFamilyMember(newMemberName.trim())
                                    newMemberName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (familyMembers.isEmpty()) {
                        Text("No custom family members registered yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            familyMembers.forEach { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(member.name, color = Color.White, fontSize = 14.sp)
                                    IconButton(
                                        onClick = { viewModel.deleteFamilyMember(member) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete member", tint = AlertRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: MANAGE CATEGORIES ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TRANSACTION CATEGORIES", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                    Text("Add categories for segmenting income sources and expense destinations.", fontSize = 11.sp, color = Color.Gray)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EXPENSE", "INCOME").forEach { type ->
                            val isSelected = selectedCategoryType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) WaterBlue else SurfaceCard)
                                    .clickable { selectedCategoryType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("Category (e.g. Internet, Dining)", fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    viewModel.createFinanceCategory(newCategoryName.trim(), selectedCategoryType)
                                    newCategoryName = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WaterBlue),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add category", tint = Color.Black)
                        }
                    }

                    // Segmented lists of current custom categories
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("EXPENSES", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            val expCats = categories.filter { it.type == "EXPENSE" }
                            if (expCats.isEmpty()) {
                                Text("None", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                expCats.forEach { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cat.name, fontSize = 12.sp, color = Color.White)
                                        IconButton(onClick = { viewModel.deleteFinanceCategory(cat) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("INCOMES", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            val incCats = categories.filter { it.type == "INCOME" }
                            if (incCats.isEmpty()) {
                                Text("None", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                incCats.forEach { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cat.name, fontSize = 12.sp, color = Color.White)
                                        IconButton(onClick = { viewModel.deleteFinanceCategory(cat) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
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


@Composable
fun SettingsFitnessSyncTrendsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val googleFitSyncStatus by viewModel.googleFitSyncStatus.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Subpage Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "GOOGLE FIT SYNC",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Synchronize activity data directly with your Google Fit account",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        
        HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            GoogleSyncTab(
                statusMessage = googleFitSyncStatus,
                onConnectFit = {
                    viewModel.connectAndSyncGoogleFit(context)
                },
                onClearCache = {
                    coroutineScope.launch {
                        viewModel.updateHealthMetric(
                            steps = 0,
                            sleepMinutes = 0,
                            waterMl = 0,
                            caloriesBurned = 0,
                            activeMinutes = 0
                        )
                        Toast.makeText(context, "Local metrics reset to baseline.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}


@Composable
fun SettingsGeneralSystemPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val tabOrder by viewModel.tabOrder.collectAsState()
        val tabBarOrientation by viewModel.tabBarOrientation.collectAsState()
        val additionalReminderTimes by viewModel.additionalReminderTimes.collectAsState()
        val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
        val hiddenTabs by viewModel.hiddenTabs.collectAsState()
        val allDayNotificationEnabled by viewModel.allDayNotificationEnabled.collectAsState()
        val allDayNotificationTime by viewModel.allDayNotificationTime.collectAsState()
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()
        var tempOrder by remember(tabOrder) { mutableStateOf(tabOrder.filterNot { it == Screen.FOCUS_LOCKER || it == Screen.LIVE_SPHERE }) }

        // General System Page
        SettingsSubpageWorkspace(
            title = "General System Settings",
            description = "Configure core systems, tab layout orientation and app reordering.",
            onBack = onBack
        ) {
            // Alignment Options Column Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Navigation Bar Position",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Set the tab position: left (sidebar), right, top, bottom, or legacy profiles.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("left", "right").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("top", "bottom").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab order customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reorder navigation tabs",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move tabs up and down to customize their layout position.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tempOrder.forEachIndexed { index, screen ->
                            val label = when (screen) {
                                Screen.TASKS -> "Tasks"
                                Screen.CALENDAR -> "Calendar"
                                Screen.TIMER -> "Timer"
                                Screen.HABITS -> "Habits"
                                Screen.COUNTDOWN -> "Countdown"
                                Screen.JOURNAL -> "Journal"
                                Screen.KEEP_NOTES -> "Keep Notes"
                                Screen.CONTACTS -> "Contacts"
                                Screen.FILE_EXPLORER -> "File Explorer"
                                Screen.FINANCES -> "Finances"
                                Screen.DEEPA_AI -> "Deepa AI"
                                Screen.SEARCH -> "Search"
                                Screen.ANALYTICS -> "Analytics"
                                Screen.SETTINGS -> "Settings"
                                Screen.LOGIN -> "Login"
                                Screen.PROFILE_SETUP -> "Profile Setup"
                                Screen.PERMISSION_ONBOARDING -> "Permissions Onboarding"
                                Screen.CALENDAR_OPTIMIZATION_ONBOARDING -> "Calendar Optimization"
                                Screen.HEALTH -> "Health"
                                Screen.LIVE_SPHERE -> "Friends Focus Details"
                                Screen.ARENA -> "Arena"
                                Screen.FOCUS_LOCKER -> "Focus Locker"
                                Screen.MESSAGES -> "Messages"
                                Screen.FLEX_GRID_STUDIO -> "Layout Studio"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isTabHidden = hiddenTabs.contains(screen)
                                    IconButton(
                                        onClick = { viewModel.toggleTabVisibility(screen) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTabHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = if (isTabHidden) Color.Gray else WaterBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index - 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index < tempOrder.size - 1) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index + 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index < tempOrder.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (index < tempOrder.size - 1) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.saveTabOrder(tempOrder) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_tab_order_subpage_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Text("SAVE TAB ORDER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Silent Mode Card
            val masterSilentMode by viewModel.masterSilentModeEnabled.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MASTER SILENT MODE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("When enabled, all app reminders, sounds, and vibrations are completely silenced.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = masterSilentMode,
                        onCheckedChange = { viewModel.updateMasterSilentModeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("master_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Services Silent Mode Card
            val backgroundServicesSilentMode by viewModel.backgroundServicesSilentModeEnabled.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BACKGROUND SERVICES (SILENT SLEEP)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("When the app is closed, suspend all background services and OSD overlays to conserve 100% battery & internet. Calibrates instantly upon reopening.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = backgroundServicesSilentMode,
                        onCheckedChange = { viewModel.updateBackgroundServicesSilentMode(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("background_services_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Staging Mode Card
            val isStagingMode by viewModel.isStagingMode.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth().testTag("staging_mode_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Developer Staging Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Enable detailed staging debug logs and developer-level analytics.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isStagingMode,
                        onCheckedChange = {
                            viewModel.setStagingMode(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("staging_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reminder Display Background Image Card
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var useCustomReminderBg by remember { mutableStateOf(sharedPrefs.getBoolean("use_custom_reminder_bg", false)) }
            var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
            var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            
            // Cropper adjustments
            var zoomScale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            var isCroppingMode by remember { mutableStateOf(false) }
            
            // Live preview image state
            var previewBitmap by remember(useCustomReminderBg) {
                mutableStateOf<Bitmap?>(
                    if (useCustomReminderBg) {
                        try {
                            val file = File(context.filesDir, "reminder_bg.jpg")
                            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                )
            }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    selectedImageUri = uri
                    val bitmap = uriToBitmap(context, uri)
                    if (bitmap != null) {
                        loadedBitmap = bitmap
                        zoomScale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        isCroppingMode = true
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("reminder_background_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REMINDER BACKGROUND IMAGE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose and crop a custom portrait image to display on the full-screen reminder overlay.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use Custom Image", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = useCustomReminderBg,
                            onCheckedChange = { isChecked ->
                                useCustomReminderBg = isChecked
                                sharedPrefs.edit().putBoolean("use_custom_reminder_bg", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("use_custom_reminder_bg_switch")
                        )
                    }

                    if (useCustomReminderBg) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Thumbnail Preview
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 128.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141414))
                                    .border(BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "Background Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHOOSE IMAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (previewBitmap != null) {
                                    Button(
                                        onClick = {
                                            try {
                                                val file = File(context.filesDir, "reminder_bg.jpg")
                                                if (file.exists()) file.delete()
                                                previewBitmap = null
                                                useCustomReminderBg = false
                                                sharedPrefs.edit().putBoolean("use_custom_reminder_bg", false).apply()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET TO DEFAULT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // High-fidelity full screen cropping dialog
            if (isCroppingMode && loadedBitmap != null) {
                Dialog(
                    onDismissRequest = { isCroppingMode = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false
                    )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        val screenWidth = maxWidth
                        val screenHeight = maxHeight
                        val density = LocalDensity.current
                        
                        // Calculate portrait 9:16 cropping frame dynamically to fit safe screen boundaries
                        val cropWidthDp = minOf(screenWidth - 48.dp, (screenHeight - 180.dp) * (9f / 16f))
                        val cropHeightDp = cropWidthDp * (16f / 9f)

                        val cropWidthPx = with(density) { cropWidthDp.toPx() }
                        val cropHeightPx = with(density) { cropHeightDp.toPx() }

                        val bmWidth = loadedBitmap!!.width.toFloat()
                        val bmHeight = loadedBitmap!!.height.toFloat()

                        // Fit image base scale to completely cover the crop frame initially
                        val baseScale = maxOf(cropWidthPx / bmWidth, cropHeightPx / bmHeight)
                        val layoutWidthDp = with(density) { (bmWidth * baseScale).toDp() }
                        val layoutHeightDp = with(density) { (bmHeight * baseScale).toDp() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(loadedBitmap) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        
                                        val totalScale = baseScale * zoomScale
                                        val wz = bmWidth * totalScale
                                        val hz = bmHeight * totalScale
                                        
                                        val maxOffsetX = (wz - cropWidthPx) / 2f
                                        val maxOffsetY = (hz - cropHeightPx) / 2f
                                        
                                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = loadedBitmap!!.asImageBitmap(),
                                contentDescription = "Cropping Source",
                                modifier = Modifier
                                    .size(width = layoutWidthDp, height = layoutHeightDp)
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    ),
                                contentScale = ContentScale.Crop
                            )

                            // Semi-translucent mask overlay with high-contrast portrait cropping window
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
                                }
                                val cropPath = androidx.compose.ui.graphics.Path().apply {
                                    addRect(androidx.compose.ui.geometry.Rect(
                                        canvasWidth / 2f - cropWidthPx / 2f,
                                        canvasHeight / 2f - cropHeightPx / 2f,
                                        canvasWidth / 2f + cropWidthPx / 2f,
                                        canvasHeight / 2f + cropHeightPx / 2f
                                    ))
                                }
                                
                                val maskPath = androidx.compose.ui.graphics.Path.combine(
                                    androidx.compose.ui.graphics.PathOperation.Difference,
                                    path,
                                    cropPath
                                )
                                
                                drawPath(
                                    path = maskPath,
                                    color = Color.Black.copy(alpha = 0.75f)
                                )
                                
                                // White fine boundary outline for the 9:16 target window
                                drawRect(
                                    color = Color.White.copy(alpha = 0.8f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        canvasWidth / 2f - cropWidthPx / 2f,
                                        canvasHeight / 2f - cropHeightPx / 2f
                                    ),
                                    size = androidx.compose.ui.geometry.Size(cropWidthPx, cropHeightPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            // Dynamic instruction text at top of screen
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Position Background",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Drag to reposition • Pinch with two fingers to resize",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Interactive controls at bottom
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isCroppingMode = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                Button(
                                    onClick = {
                                        val cropped = cropBitmapToPortraitAndScale(
                                            loadedBitmap!!,
                                            zoomScale,
                                            offsetX,
                                            offsetY,
                                            cropWidthPx,
                                            cropHeightPx,
                                            baseScale
                                        )
                                        saveBitmapToFile(context, cropped)
                                        
                                        // Update preview & enable custom BG option
                                        useCustomReminderBg = true
                                        sharedPrefs.edit().putBoolean("use_custom_reminder_bg", true).apply()
                                        
                                        try {
                                            val file = File(context.filesDir, "reminder_bg.jpg")
                                            previewBitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                        } catch (e: Exception) {
                                            previewBitmap = null
                                        }
                                        
                                        isCroppingMode = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply & Crop", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper methods for Custom Reminder Background Bitmap crop & conversion
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmapToPortraitAndScale(
    bitmap: Bitmap,
    zoomScale: Float,
    offsetX: Float,
    offsetY: Float,
    rectWidthPx: Float,
    rectHeightPx: Float,
    baseScale: Float
): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    val totalScale = baseScale * zoomScale
    val cropWidthOnBitmap = rectWidthPx / totalScale
    val cropHeightOnBitmap = rectHeightPx / totalScale
    
    // Calculate top-left coordinates on the original bitmap
    val startX = bitmapWidth / 2f - (offsetX + rectWidthPx / 2f) / totalScale
    val startY = bitmapHeight / 2f - (offsetY + rectHeightPx / 2f) / totalScale
    
    val startXCoerced = startX.toInt().coerceIn(0, (bitmapWidth - cropWidthOnBitmap).toInt().coerceAtLeast(0))
    val startYCoerced = startY.toInt().coerceIn(0, (bitmapHeight - cropHeightOnBitmap).toInt().coerceAtLeast(0))
    
    val widthCoerced = cropWidthOnBitmap.toInt().coerceIn(50, bitmap.width - startXCoerced)
    val heightCoerced = cropHeightOnBitmap.toInt().coerceIn(50, bitmap.height - startYCoerced)
    
    val cropped = Bitmap.createBitmap(bitmap, startXCoerced, startYCoerced, widthCoerced, heightCoerced)
    
    // Scale to a high quality but memory friendly resolution (540 x 960)
    val scaled = Bitmap.createScaledBitmap(cropped, 540, 960, true)
    
    if (cropped != bitmap) {
        cropped.recycle()
    }
    return scaled
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.filesDir, "reminder_bg.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}



@Composable
fun SettingsHabitsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val habitSilentModeEnabled by viewModel.habitSilentModeEnabled.collectAsState()
        val habitOnScreen by viewModel.habitOnScreenReminderEnabled.collectAsState()
        val habitNotif by viewModel.habitNotifReminderEnabled.collectAsState()

        SettingsSubpageWorkspace(
            title = "Habits Tracker Settings",
            description = "Configure streaks, reminders and habit intervals.",
            onBack = onBack
        ) {
            Text("Streak recorders calculate active records continuously. Midnight resets evaluate complete boxes.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Habit Notification Core", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On-Screen Reminders", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Display reminders in application dashboard", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitOnScreen,
                            onCheckedChange = { viewModel.updateHabitOnScreenReminderEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Notifications", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Send push notifications at scheduled times", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitNotif,
                            onCheckedChange = { viewModel.updateHabitNotifReminderEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Silent Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Displays on-screen but has no sound / vibration.", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitSilentModeEnabled,
                            onCheckedChange = { viewModel.updateHabitSilentModeEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("habit_silent_mode_switch")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsJournalPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()

        SettingsSubpageWorkspace(
            title = "Life Journal Settings",
            description = "Configurations and notification preferences.",
            onBack = onBack
        ) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Journal records and metadata are completely local and private to your device.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On This Day Notifications", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sends a notification reminding you of historical journal entries written on this day in history.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayNotificationEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayNotificationEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_notification_switch")
                        )
                    }
                    
                    if (onThisDayNotificationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Notification Trigger Time", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = onThisDayNotificationTime,
                            onValueChange = { viewModel.updateOnThisDayNotificationTime(it) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF333333)
                            ),
                            placeholder = { Text("e.g. 09:00 AM or 18:30", color = Color.DarkGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("on_this_day_notification_time_input")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On Screen Reminders", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Displays an on-screen dialog when today's historic journal entries exist.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayOnScreenEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayOnScreenEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_onscreen_switch")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsPermissionsPage(viewModel: AppViewModel, onBack: () -> Unit) {
    SettingsSubpageWorkspace(
        title = "Permissions & API Connections",
        description = "Manage system permissions and external API authorizations.",
        onBack = onBack
    ) {
        PermissionsSettingsSection(viewModel = viewModel)
    }
}

fun hasGoogleScope(context: Context, scopeUri: String): Boolean {
    val scope = com.google.android.gms.common.api.Scope(scopeUri)
    val account = GoogleSignIn.getLastSignedInAccount(context)
    return account != null && GoogleSignIn.hasPermissions(account, scope)
}

@Composable
fun PermissionsSettingsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    
    var hasSystemCalendarPermission by remember { mutableStateOf(false) }
    var hasSystemContactsPermission by remember { mutableStateOf(false) }

    var hasDrivePermission by remember { mutableStateOf(false) }
    var hasGoogleContactsPermission by remember { mutableStateOf(false) }
    var hasGoogleTasksPermission by remember { mutableStateOf(false) }
    var hasGoogleFitPermission by remember { mutableStateOf(false) }
    var hasGooglePhotosPermission by remember { mutableStateOf(false) }
    var hasGoogleKeepPermission by remember { mutableStateOf(false) }
    
    var hasExactAlarmPermission by remember { mutableStateOf(false) }
    var hasNotificationListenerPermission by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val checkAllPermissions = {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasUsageStatsPermission = AppBlockHelper.hasUsageStatsPermission(context)
        
        hasSystemCalendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                      
        hasSystemContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                                      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        hasGoogleContactsPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/contacts")
        hasGoogleTasksPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/tasks")
        hasGoogleFitPermission = GoogleFitSyncManager.hasFitPermission(context)
        hasGooglePhotosPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/photoslibrary.readonly")
        hasGoogleKeepPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/drive.appdata")

        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val cn = android.content.ComponentName(context, com.example.service.NotificationBlockerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        hasNotificationListenerPermission = flat != null && flat.contains(cn.flattenToString())
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkAllPermissions()
            delay(1000)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
        }
    )
    
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ -> checkAllPermissions() }
    )

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        checkAllPermissions()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: ""
                    val displayName = account.displayName ?: ""
                    val idToken = account.idToken
                    val username = email.substringBefore("@")
                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                    
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("selected_tasks_account", email)
                        .putString("selected_file_backup_account", email)
                        .apply()
                    
                    checkAllPermissions()
                }
            } catch (e: Exception) {
                Log.e("SettingsView", "Google Sign-In failed", e)
            }
        }
    }

    val triggerGoogleSignInForScopes = { scopes: List<com.google.android.gms.common.api.Scope> ->
        try {
            val webClientId = try {
                context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
            } catch (e: Exception) {
                ""
            }
            val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
            scopes.forEach { gsoBuilder.requestScopes(it) }
            if (webClientId.isNotEmpty()) {
                gsoBuilder.requestIdToken(webClientId)
            }
            val gso = gsoBuilder.build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PermissionItem(
            title = "Notifications",
            description = "Required for focus timer alerts and reminders.",
            isGranted = hasNotificationPermission,
            onClick = {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!hasNotificationPermission) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Notification Blocker (Notification Access)",
            description = "Required to block and release incoming notifications during Focus phase.",
            isGranted = hasNotificationListenerPermission,
            onClick = {
                if (!hasNotificationListenerPermission) {
                    try {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("SettingsView", "Error starting notification listener settings: ${e.message}")
                    }
                }
            }
        )

        PermissionItem(
            title = "Battery Optimization",
            description = "Ignore battery optimization to keep timers running in background.",
            isGranted = isBatteryOptIgnored,
            onClick = {
                if (!isBatteryOptIgnored) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Exact Alarms",
            description = "Required for precise timer wakeups on Android 12+.",
            isGranted = hasExactAlarmPermission,
            onClick = {
                if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Display Over Other Apps",
            description = "Required to show strict mode blocking overlays.",
            isGranted = hasOverlayPermission,
            onClick = {
                if (!hasOverlayPermission) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Usage Access",
            description = "Required to track app usage for blocking limits.",
            isGranted = hasUsageStatsPermission,
            onClick = {
                if (!hasUsageStatsPermission) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )
        
        PermissionItem(
            title = "System Calendar",
            description = "Required to sync events from local device calendars.",
            isGranted = hasSystemCalendarPermission,
            onClick = {
                if (!hasSystemCalendarPermission) {
                    multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
            }
        )
        
        PermissionItem(
            title = "System Contacts",
            description = "Required to pick contacts from the device.",
            isGranted = hasSystemContactsPermission,
            onClick = {
                if (!hasSystemContactsPermission) {
                    multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                }
            }
        )

        PermissionItem(
            title = "Google Drive",
            description = "Required to backup and restore app data to the cloud.",
            isGranted = hasDrivePermission,
            onClick = {
                if (!hasDrivePermission) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        triggerGoogleSignInForScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata"),
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")
                        ))
                    } else {
                        scope.launch {
                            GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        )
        
        PermissionItem(
            title = "Google Contacts",
            description = "Sync with Google Contacts API.",
            isGranted = hasGoogleContactsPermission,
            onClick = {
                if (!hasGoogleContactsPermission) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        triggerGoogleSignInForScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts")
                        ))
                    } else {
                        scope.launch {
                            GoogleContactsSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        )
        

        PermissionItem(
            title = "Google Tasks",
            description = "Sync with Google Tasks API.",
            isGranted = hasGoogleTasksPermission,
            onClick = {
                if (!hasGoogleTasksPermission) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        triggerGoogleSignInForScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/tasks")
                        ))
                    } else {
                        scope.launch {
                            GoogleTasksSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        )
        
        PermissionItem(
            title = "Google Fit",
            description = "Sync with Google Fit API for health data.",
            isGranted = hasGoogleFitPermission,
            onClick = {
                if (!hasGoogleFitPermission) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        triggerGoogleSignInForScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/fitness.activity.read"),
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/fitness.body.read")
                        ))
                    } else {
                        scope.launch {
                            GoogleFitSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        )

        PermissionItem(
            title = "Google Photos",
            description = "Sync with Google Photos Library API to import visual memories into the Journal.",
            isGranted = hasGooglePhotosPermission,
            onClick = {
                if (!hasGooglePhotosPermission) {
                    scope.launch {
                        com.example.util.GooglePhotosSyncManager.getAccessToken(context) { intent ->
                            try {
                                authResolutionLauncher.launch(intent)
                            } catch (e: Exception) {
                                triggerGoogleSignInForScopes(listOf(
                                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/photoslibrary.readonly")
                                ))
                            }
                        }
                    }
                }
            }
        )

        PermissionItem(
            title = "Keep Notes Integration (Google Keep)",
            description = "Sync with Google Keep AppData file to keep personal notes and media attachments aligned.",
            isGranted = hasGoogleKeepPermission,
            onClick = {
                if (!hasGoogleKeepPermission) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        triggerGoogleSignInForScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                        ))
                    } else {
                        scope.launch {
                            com.example.util.GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        GoogleWorkspaceRestConsole(viewModel = viewModel)
    }
}

@Composable
fun GoogleWorkspaceRestConsole(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedApi by remember { mutableStateOf("Calendar v3") }
    var selectedMethod by remember { mutableStateOf("List CalendarList") }
    
    var inputParam1 by remember { mutableStateOf("primary") }
    var inputParam2 by remember { mutableStateOf("") }
    
    var executionResult by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    val methods = remember(selectedApi) {
        when (selectedApi) {
            "Calendar v3" -> listOf("List CalendarList", "List Events", "Quick Add Event", "Get Colors", "Clear Calendar")
            "Keep v1" -> listOf("List Notes", "Create Note", "Delete Note")
            "Docs v1" -> listOf("Create Document", "Get Document")
            else -> emptyList()
        }
    }

    LaunchedEffect(selectedApi) {
        selectedMethod = methods.firstOrNull() ?: ""
        if (selectedApi == "Calendar v3") {
            inputParam1 = "primary"
            inputParam2 = "Lunch meeting tomorrow at 1pm with developer team"
        } else if (selectedApi == "Keep v1") {
            inputParam1 = "notes/ENTER_NOTE_ID"
            inputParam2 = "{\n  \"title\": \"Direct REST Keep Note\",\n  \"body\": {\n    \"text\": \"Created via Keep v1 REST API!\"\n  }\n}"
        } else {
            inputParam1 = "ENTER_DOC_ID"
            inputParam2 = "{\n  \"title\": \"Direct REST Google Doc\"\n}"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161a)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2e2e34))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "REST API Console",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google REST API Console",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Direct REST execution for Google Calendar v3, Google Keep v1, and Google Docs v1 APIs.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Select API Service", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Calendar v3", "Keep v1", "Docs v1").forEach { api ->
                    val isSelected = selectedApi == api
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1E88E5) else Color(0xFF252529))
                            .clickable { selectedApi = api }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = api,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Select REST Method", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                methods.chunked(2).forEach { rowMethods ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowMethods.forEach { method ->
                            val isSelected = selectedMethod == method
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF1E1E22))
                                    .border(BorderStroke(1.dp, if (isSelected) Color(0xFF00E676) else Color.Transparent), RoundedCornerShape(6.dp))
                                    .clickable { selectedMethod = method }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = method,
                                    color = if (isSelected) Color(0xFF00E676) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowMethods.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val showParam1 = selectedMethod in listOf("List Events", "Quick Add Event", "Clear Calendar", "Delete Note", "Get Keep Note", "Get Document")
            val showParam2 = selectedMethod in listOf("Quick Add Event", "Create Note", "Create Document")
            
            if (showParam1) {
                val param1Label = when (selectedMethod) {
                    "List Events", "Clear Calendar" -> "Calendar ID (e.g. 'primary')"
                    "Delete Note", "Get Keep Note" -> "Note Resource Name (e.g. 'notes/123')"
                    "Get Document" -> "Document ID"
                    else -> "Resource ID / Parameter"
                }
                Text(param1Label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = inputParam1,
                    onValueChange = { inputParam1 = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color(0xFF3E3E42),
                        focusedContainerColor = Color(0xFF1E1E22),
                        unfocusedContainerColor = Color(0xFF1E1E22)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showParam2) {
                val param2Label = when (selectedMethod) {
                    "Quick Add Event" -> "Quick Add Text Parameter (e.g. 'Meeting at 3pm')"
                    "Create Note" -> "Note JSON Body Payload"
                    "Create Document" -> "Document JSON Body Payload"
                    else -> "Payload String / Parameter"
                }
                Text(param2Label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = inputParam2,
                    onValueChange = { inputParam2 = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color(0xFF3E3E42),
                        focusedContainerColor = Color(0xFF1E1E22),
                        unfocusedContainerColor = Color(0xFF1E1E22)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    isExecuting = true
                    executionResult = "Initializing credentials and resolving API OAuth scopes..."
                    scope.launch {
                        try {
                            val targetScopes = when (selectedApi) {
                                "Calendar v3" -> listOf("https://www.googleapis.com/auth/calendar", "https://www.googleapis.com/auth/calendar.events")
                                "Keep v1" -> listOf("https://www.googleapis.com/auth/keep")
                                "Docs v1" -> listOf("https://www.googleapis.com/auth/documents")
                                else -> emptyList()
                            }
                            
                            val token = com.example.util.GoogleRestApiHelper.getAccessTokenForScopes(context, targetScopes)
                            if (token == null) {
                                executionResult = "Error: Token retrieval returned null.\n\nPlease verify that you have logged in to your Google Account and granted API connections above."
                                isExecuting = false
                                return@launch
                            }

                            executionResult = "OAuth2 Access Token acquired successfully.\nSending secure REST Request to Google REST API Endpoint..."
                            
                            val outcome: Pair<Boolean, String> = when (selectedApi) {
                                "Calendar v3" -> {
                                    when (selectedMethod) {
                                        "List CalendarList" -> com.example.util.GoogleRestApiHelper.listCalendarList(token)
                                        "List Events" -> com.example.util.GoogleRestApiHelper.listEvents(token, inputParam1)
                                        "Quick Add Event" -> com.example.util.GoogleRestApiHelper.quickAddEvent(token, inputParam1, inputParam2)
                                        "Get Colors" -> com.example.util.GoogleRestApiHelper.getColors(token)
                                        "Clear Calendar" -> com.example.util.GoogleRestApiHelper.clearCalendar(token, inputParam1)
                                        else -> Pair(false, "Unknown Calendar Method")
                                    }
                                }
                                "Keep v1" -> {
                                    when (selectedMethod) {
                                        "List Notes" -> com.example.util.GoogleRestApiHelper.listKeepNotes(token)
                                        "Create Note" -> com.example.util.GoogleRestApiHelper.createKeepNote(token, inputParam2)
                                        "Delete Note" -> com.example.util.GoogleRestApiHelper.deleteKeepNote(token, inputParam1)
                                        else -> Pair(false, "Unknown Keep Method")
                                    }
                                }
                                "Docs v1" -> {
                                    when (selectedMethod) {
                                        "Create Document" -> com.example.util.GoogleRestApiHelper.createDocument(token, inputParam2)
                                        "Get Document" -> com.example.util.GoogleRestApiHelper.getDocument(token, inputParam1)
                                        else -> Pair(false, "Unknown Docs Method")
                                    }
                                }
                                else -> Pair(false, "Unknown API Service")
                            }

                            if (outcome.first) {
                                executionResult = "HTTP SUCCESS (200/201 OK):\n\n${outcome.second}"
                            } else {
                                executionResult = "HTTP ERROR RESPONSE:\n\n${outcome.second}"
                            }
                        } catch (e: Exception) {
                            executionResult = "EXCEPTION ENCOUNTERED DURING REST ACTION:\n\n${e.localizedMessage ?: e.message}"
                        } finally {
                            isExecuting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E88E5),
                    disabledContainerColor = Color(0xFF1E88E5).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isExecuting
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Executing Direct Call...", color = Color.White)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute REST Call", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            executionResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("API Console Response Logs", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF222226))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (result.contains("HTTP SUCCESS")) "SUCCESS LOG" else "CONSOLE LOG",
                                color = if (result.contains("HTTP SUCCESS")) Color(0xFF00E676) else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Clear",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { executionResult = null }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = result,
                                color = if (result.contains("Error") || result.contains("ERROR") || result.contains("EXCEPTION")) Color(0xFFFF8A80) else Color(0xFF00FF66),
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Grant", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
fun SettingsTasksPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val taskVibrationEnabled by viewModel.taskVibrationEnabled.collectAsState()
        val taskSilentModeEnabled by viewModel.taskSilentModeEnabled.collectAsState()
        val allDayNotificationEnabled by viewModel.allDayNotificationEnabled.collectAsState()
        val allDayNotificationTime by viewModel.allDayNotificationTime.collectAsState()
        val taskHighNotif by viewModel.taskHighNotifEnabled.collectAsState()
        val taskHighDisplay by viewModel.taskHighDisplayEnabled.collectAsState()
        val taskMediumNotif by viewModel.taskMediumNotifEnabled.collectAsState()
        val taskMediumDisplay by viewModel.taskMediumDisplayEnabled.collectAsState()
        val taskLowNotif by viewModel.taskLowNotifEnabled.collectAsState()
        val taskLowDisplay by viewModel.taskLowDisplayEnabled.collectAsState()
        val taskHighAlarmSound by viewModel.taskHighAlarmSoundEnabled.collectAsState()
        val taskMediumAlarmSound by viewModel.taskMediumAlarmSoundEnabled.collectAsState()
        val taskLowAlarmSound by viewModel.taskLowAlarmSoundEnabled.collectAsState()
        val additionalReminderTimes by viewModel.additionalReminderTimes.collectAsState()

        // Tasks Subpage
        SettingsSubpageWorkspace(
            title = "Tasks Settings",
            description = "Configure custom reminders, vibrations, priority schedules.",
            onBack = onBack
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Task Reminder Vibration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Vibrate device during custom relative reminder schedules.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = taskVibrationEnabled,
                    onCheckedChange = { viewModel.updateTaskVibrationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("task_reminder_vibrate_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Silent Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Displays on-screen but has no sound / vibration.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = taskSilentModeEnabled,
                    onCheckedChange = { viewModel.updateTaskSilentModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("task_reminder_silent_switch")
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Additional Reminder Offsets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Define comma-separated relative offsets in minutes inside schedules (ex: 5, 15, 30):", color = Color.Gray, fontSize = 10.sp)
                OutlinedTextField(
                    value = additionalReminderTimes,
                    onValueChange = { viewModel.updateAdditionalReminderTimes(it) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    placeholder = { Text("e.g. 5, 15, 30", color = Color.DarkGray, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("additional_reminders_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Priority Notification Settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // High Priority
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighNotif, onCheckedChange = { viewModel.updateTaskHighNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighDisplay, onCheckedChange = { viewModel.updateTaskHighDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighAlarmSound, onCheckedChange = { viewModel.updateTaskHighAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Medium Priority
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Medium Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumNotif, onCheckedChange = { viewModel.updateTaskMediumNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumDisplay, onCheckedChange = { viewModel.updateTaskMediumDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumAlarmSound, onCheckedChange = { viewModel.updateTaskMediumAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Low/No Priority Notification settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No / Low Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowNotif, onCheckedChange = { viewModel.updateTaskLowNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowDisplay, onCheckedChange = { viewModel.updateTaskLowDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowAlarmSound, onCheckedChange = { viewModel.updateTaskLowAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("All-Day Tasks Alerts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pending All-Day Tasks Alert", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Sends a notification showing how many all-day tasks are pending.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = allDayNotificationEnabled,
                                onCheckedChange = { viewModel.updateAllDayNotificationEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                                modifier = Modifier.testTag("all_day_notification_switch")
                            )
                        }
                        
                        if (allDayNotificationEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Alert Trigger Time", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = allDayNotificationTime,
                                onValueChange = { viewModel.updateAllDayNotificationTime(it) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF333333)
                                ),
                                placeholder = { Text("e.g. 09:00 AM or 18:30", color = Color.DarkGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("all_day_notification_time_input")
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun SettingsTimerConfigurationPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    SettingsPageScope {
        val focusTimerDurationMins by viewModel.focusTimerDurationMins.collectAsState()
        val breakDurationMins by viewModel.breakDurationMins.collectAsState()
        val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
        val showOverlayEnabled by viewModel.showOverlayEnabled.collectAsState()
        val floatingTimerSize by viewModel.floatingTimerSize.collectAsState()
        val keepNotificationEnabled by viewModel.keepNotificationEnabled.collectAsState()
        val chatNotificationsEnabled by viewModel.chatNotificationsEnabled.collectAsState()
        val chatSoundEnabled by viewModel.chatSoundEnabled.collectAsState()
        val autoRedirectSleepFirstLaunch by viewModel.autoRedirectSleepFirstLaunch.collectAsState()
        val backgroundActivityEnabled by viewModel.backgroundActivityEnabled.collectAsState()
        val dailyFocusHoursTarget by viewModel.dailyFocusHoursTarget.collectAsState()
        val focusMotivationalQuoteEnabled by viewModel.focusMotivationalQuoteEnabled.collectAsState()
        val focusMotivationalQuoteIntervalMins by viewModel.focusMotivationalQuoteIntervalMins.collectAsState()

        val timerSoundEnabled by viewModel.timerSoundEnabled.collectAsState()
        val timerAutoStartBreak by viewModel.timerAutoStartBreak.collectAsState()
        val timerAutoStartPomo by viewModel.timerAutoStartPomo.collectAsState()
        val stopwatchBreakDurationMinutes by viewModel.stopwatchBreakDurationMinutes.collectAsState()
        val autoStartStopwatchAfterBreak by viewModel.autoStartStopwatchAfterBreak.collectAsState()
        val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
        val batterySaverModeEnabled by viewModel.batterySaverModeEnabled.collectAsState()
        val shareFocusDetailsEnabled by viewModel.shareFocusDetailsEnabled.collectAsState()
        val shareFocusHistoryEnabled by viewModel.shareFocusHistoryEnabled.collectAsState()

        val isTimerRunning by viewModel.isTimerRunning.collectAsState()
        val isStopwatchActive by viewModel.isStopwatchActive.collectAsState()
        val isFocusPhase by viewModel.isFocusPhase.collectAsState()
        val isStrictBlocked = (isTimerRunning && isFocusPhase) || isStopwatchActive

        // Timer Configuration Page
        SettingsSubpageWorkspace(
            title = "Timer Configuration",
            description = "Configure Pomodoro, Stopwatch, and general timer preferences.",
            onBack = onBack
        ) {
            var focusInputText by remember(focusTimerDurationMins) { mutableStateOf(focusTimerDurationMins.toString()) }
            var breakInputText by remember(breakDurationMins) { mutableStateOf(breakDurationMins.toString()) }
            var stopwatchBreakInputText by remember(stopwatchBreakDurationMinutes) { mutableStateOf(stopwatchBreakDurationMinutes.toString()) }

            val strictPrefs = remember { context.getSharedPreferences("strict_mode_prefs", android.content.Context.MODE_PRIVATE) }
            var strictModeEnabled by remember {
                mutableStateOf(strictPrefs.getBoolean("strict_mode_enabled", true))
            }
            var blockedApps by remember {
                mutableStateOf(strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet())
            }
            var searchAppQuery by remember { mutableStateOf("") }

            var hasPermissionState by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    hasPermissionState = com.example.util.AppBlockHelper.hasUsageStatsPermission(context)
                    kotlinx.coroutines.delay(2000)
                }
            }

            // 1. POMODORO CONFIGURATION SECTION
            Text(
                text = "POMODORO (PROMO) CONFIGURATION",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Focus Session Duration (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = focusInputText,
                        onValueChange = { newValue ->
                            focusInputText = newValue
                            val parsed = newValue.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                viewModel.updateTimerDuration(parsed)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("focus_duration_input")
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Default Rest Break Period (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = breakInputText,
                        onValueChange = { newValue ->
                            breakInputText = newValue
                            val parsed = newValue.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                viewModel.updateBreakDuration(parsed)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("break_duration_input")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Timer End Sound Alerts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Play high-quality alerting sound when segments end", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = timerSoundEnabled,
                        onCheckedChange = { viewModel.setTimerSoundEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("timer_sound_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Start Break After Focus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Instantly trigger rest period when study focus timer completes", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = timerAutoStartBreak,
                        onCheckedChange = { viewModel.setTimerAutoStartBreak(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("timer_autostart_break_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Start Focus After Break", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Instantly trigger next focus session when rest break completes", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = timerAutoStartPomo,
                        onCheckedChange = { viewModel.setTimerAutoStartPomo(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("timer_autostart_pomo_switch")
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            // 2. STOPWATCH CONFIGURATION SECTION
            Text(
                text = "STOPWATCH CONFIGURATION",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Stopwatch Rest Break Period (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = stopwatchBreakInputText,
                        onValueChange = { newValue ->
                            stopwatchBreakInputText = newValue
                            val parsed = newValue.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                viewModel.setStopwatchBreakDuration(parsed)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stopwatch_break_duration_input")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Start Stopwatch After Break", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically resume stopwatch when stopwatch break completes", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = autoStartStopwatchAfterBreak,
                        onCheckedChange = { viewModel.setAutoStartStopwatchAfterBreak(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("stopwatch_autostart_after_break_switch")
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            // 3. COMMON TIMER SETTINGS SECTION
            Text(
                text = "COMMON TIMER CONFIGURATIONS",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tactile Vibration Alerts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Vibrate device when timer completes", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { viewModel.updateVibrationEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("timer_vibration_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anti-Burn Screen Protection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Slowly shifts digits to protect active AMOLED/OLED displays.", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = antiBurnScreenEnabled,
                        onCheckedChange = { viewModel.updateAntiBurnScreenEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("anti_burn_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery Saver Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Disables background sync/alerts on close. Resumes timer precisely on app launch.", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = batterySaverModeEnabled,
                        onCheckedChange = { viewModel.updateBatterySaverModeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("battery_saver_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Focus Target (hours)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Target daily focus hours logged (1–20 hours).", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (dailyFocusHoursTarget > 1) {
                                    viewModel.updateDailyFocusHoursTarget(dailyFocusHoursTarget - 1)
                                }
                            },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Decrease Focus Target",
                                tint = if (dailyFocusHoursTarget > 1) Color.White else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$dailyFocusHoursTarget",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 16.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = {
                                if (dailyFocusHoursTarget < 20) {
                                    viewModel.updateDailyFocusHoursTarget(dailyFocusHoursTarget + 1)
                                }
                            },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Increase Focus Target",
                                tint = if (dailyFocusHoursTarget < 20) Color.White else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display Overlay Widget", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Floating widget when leaving app", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = showOverlayEnabled,
                        onCheckedChange = { viewModel.updateShowOverlayEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("overlay_switch")
                    )
                }

                if (showOverlayEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Floating Timer Size", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Choose size (Small, Medium, Large) for the floating widget", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("small", "medium", "large").forEach { size ->
                                val isSelected = floatingTimerSize == size
                                val label = size.replaceFirstChar { it.uppercase() }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) WaterBlue else Color(0xFF1E1E1F),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.updateFloatingTimerSize(size) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Pin Home Screen Widgets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Pin interactive Pomodoro, Stopwatch & Focus Sphere widgets directly to your launcher", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                com.example.widget.WidgetUpdater.requestPinWidget(context, com.example.widget.PomodoroWidgetProvider::class.java)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1F)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
                        ) {
                            Text("Pin Pomodoro", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                com.example.widget.WidgetUpdater.requestPinWidget(context, com.example.widget.TimerStopwatchWidgetProvider::class.java)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1F)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
                        ) {
                            Text("Pin Stopwatch", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                com.example.widget.WidgetUpdater.requestPinWidget(context, com.example.widget.FriendsFocusWidgetProvider::class.java)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1F)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
                        ) {
                            Text("Pin Sphere", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Persistent Notification", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Status bar/lockscreen visibility helper", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = keepNotificationEnabled,
                        onCheckedChange = { viewModel.updateKeepNotificationEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("notification_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chat Push Notifications", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Get push alerts when new community messages arrive", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = chatNotificationsEnabled,
                        onCheckedChange = { viewModel.updateChatNotificationsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("chat_notifications_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chat Alert Sound", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Play notification ringtone sound for incoming chat messages", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = chatSoundEnabled,
                        onCheckedChange = { viewModel.updateChatSoundEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("chat_sound_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-redirect to Sleep Page", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Open Health Sleep tab automatically on first launch of the day", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = autoRedirectSleepFirstLaunch,
                        onCheckedChange = { viewModel.updateAutoRedirectSleepFirstLaunch(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("auto_redirect_sleep_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Activity", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Allow application processes to run in the background", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = backgroundActivityEnabled,
                        onCheckedChange = { viewModel.updateBackgroundActivityEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("background_activity_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Focus Details", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Share active focus details with friends on the cloud.", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = shareFocusDetailsEnabled,
                        onCheckedChange = { viewModel.updateShareFocusDetailsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("share_focus_details_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Focus Session History", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Share focus history records to Firebase. Only focus time is shared if off.", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = shareFocusHistoryEnabled,
                        onCheckedChange = { viewModel.updateShareFocusHistoryEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("share_focus_history_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Motivational Quote Overlay", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Display study, money, and life change quotes in full-screen mode.", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = focusMotivationalQuoteEnabled,
                        onCheckedChange = { viewModel.updateFocusMotivationalQuoteEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("focus_motivational_quote_switch")
                    )
                }

                if (focusMotivationalQuoteEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quote Rotation Interval", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Specify interval for rotating the motivational quote (minutes).", color = Color.Gray, fontSize = 10.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (focusMotivationalQuoteIntervalMins > 1) {
                                        viewModel.updateFocusMotivationalQuoteIntervalMins(focusMotivationalQuoteIntervalMins - 1)
                                    }
                                },
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Decrease Interval",
                                    tint = if (focusMotivationalQuoteIntervalMins > 1) Color.White else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "$focusMotivationalQuoteIntervalMins min",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    if (focusMotivationalQuoteIntervalMins < 60) {
                                        viewModel.updateFocusMotivationalQuoteIntervalMins(focusMotivationalQuoteIntervalMins + 1)
                                    }
                                },
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Increase Interval",
                                    tint = if (focusMotivationalQuoteIntervalMins < 60) Color.White else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            Text(
                text = "STRICT FOCUS SECURE GATE",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isStrictBlocked) {
                        Text(
                            text = "⚠️ Strict Mode settings are locked while the focus timer or stopwatch is running.",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Mode Trigger", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Intercept chosen apps when focus timer/stopwatch runs.", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = strictModeEnabled,
                            enabled = !isStrictBlocked,
                            onCheckedChange = { isEnabled ->
                                if (isStrictBlocked) {
                                    Toast.makeText(context, "Cannot change strict mode settings while a timer or stopwatch is running!", Toast.LENGTH_SHORT).show()
                                } else {
                                    strictModeEnabled = isEnabled
                                    strictPrefs.edit().putBoolean("strict_mode_enabled", isEnabled).apply()
                                    if (isEnabled && !hasPermissionState) {
                                        Toast.makeText(context, "Usage Permission is required for Strict Mode!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = WaterBlue,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray,
                                disabledCheckedTrackColor = WaterBlue.copy(alpha = 0.5f),
                                disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (strictModeEnabled) {
                        if (!hasPermissionState) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open settings automatically. Please search Settings for Usage Access.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Authorize Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = searchAppQuery,
                            onValueChange = { searchAppQuery = it },
                            enabled = !isStrictBlocked,
                            placeholder = { Text("Filter package or name...", color = Color.DarkGray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222),
                                disabledTextColor = Color.LightGray.copy(alpha = 0.5f),
                                disabledBorderColor = Color(0xFF222222).copy(alpha = 0.5f)
                            )
                        )

                        val launchableAppsList = remember {
                            try {
                                val pm = context.packageManager
                                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                }
                                pm.queryIntentActivities(intent, 0)
                                    .map { resolveInfo ->
                                        val label = resolveInfo.loadLabel(pm).toString()
                                        val pkg = resolveInfo.activityInfo.packageName
                                        Pair(label, pkg)
                                    }
                                    .distinctBy { it.second }
                                    .filter { it.second != context.packageName }
                                    .sortedBy { it.first.lowercase() }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        val filteredList = launchableAppsList.filter {
                            it.first.contains(searchAppQuery, ignoreCase = true) || 
                            it.second.contains(searchAppQuery, ignoreCase = true)
                        }

                        Text(
                            text = "BLOCK LIST (${blockedApps.size} apps selected)",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (filteredList.isEmpty()) {
                                    Text(
                                        text = "No packages match query",
                                        color = Color.DarkGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                } else {
                                    filteredList.forEach { (label, pkg) ->
                                        val isBlocked = blockedApps.contains(pkg)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isStrictBlocked) {
                                                    val updated = blockedApps.toMutableSet()
                                                    if (isBlocked) {
                                                        updated.remove(pkg)
                                                    } else {
                                                        updated.add(pkg)
                                                    }
                                                    blockedApps = updated
                                                    strictPrefs.edit().putStringSet("blocked_packages", updated).apply()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                AppListIcon(pkg = pkg)
                                            }
                                            Checkbox(
                                                checked = isBlocked,
                                                enabled = !isStrictBlocked,
                                                onCheckedChange = { checked ->
                                                    if (!isStrictBlocked) {
                                                        val updated = blockedApps.toMutableSet()
                                                        if (checked == true) {
                                                            updated.add(pkg)
                                                        } else {
                                                            updated.remove(pkg)
                                                        }
                                                        blockedApps = updated
                                                        strictPrefs.edit().putStringSet("blocked_packages", updated).apply()
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = WaterBlue,
                                                    checkmarkColor = Color.Black,
                                                    uncheckedColor = Color.Gray
                                                )
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsUpdatesPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()
    
    var autoDownload by remember { mutableStateOf(AppUpdateManager.isAutoUpdateEnabled(context)) }
    var pauseUpdates by remember { mutableStateOf(AppUpdateManager.isPauseUpdatesEnabled(context)) }
    var forceUpdate by remember { mutableStateOf(AppUpdateManager.isForceUpdateEnabled(context)) }
    
    var githubOwner by remember { mutableStateOf(AppUpdateManager.getGithubOwner(context)) }
    var githubRepo by remember { mutableStateOf(AppUpdateManager.getGithubRepo(context)) }
    var runningFirebaseCode by remember { mutableStateOf(AppUpdateManager.getRunningFirebaseVersion(context)) }

    // Check if there is an offline downloaded update ready to install
    val readyApkPath = remember(updateStatus) { AppUpdateManager.getReadyApkPath(context) }
    val offlineApkFile = remember(readyApkPath) { readyApkPath?.let { java.io.File(it) } }
    val isOfflineApkReady = remember(offlineApkFile) { 
        offlineApkFile != null && offlineApkFile.exists() && offlineApkFile.length() > 0 && AppUpdateManager.isValidAndNewerApk(context, offlineApkFile)
    }

    val currentVersionCode = remember { AppUpdateManager.getCurrentVersionCode(context) }
    val currentVersionName = remember { AppUpdateManager.getCurrentVersionName(context) }

    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "App Update Center",
            description = "Manage Firebase App Distribution updates, automated downloads, and restore backups.",
            onBack = onBack
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Smart Delta Updater Card
                val smartState = com.example.util.SmartUpdateManager.updateStatus.collectAsState().value
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SMART DELTA UPDATER (OTA)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "An ultra-low data delta patching system utilizing Gzip BSPatch to apply updates under 1MB.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val smartLabel = when (smartState) {
                            is com.example.util.SmartUpdateStatus.Idle -> "Idle"
                            is com.example.util.SmartUpdateStatus.Checking -> "Checking cloud version..."
                            is com.example.util.SmartUpdateStatus.SecuringData -> "Securing user vault data..."
                            is com.example.util.SmartUpdateStatus.NewVersionAvailable -> "New Update: Build ${smartState.versionNo} (${if (smartState.patchFileUrl != null) "Delta Patch" else "Full"})"
                            is com.example.util.SmartUpdateStatus.NoUpdateAvailable -> "System is fully patched!"
                            is com.example.util.SmartUpdateStatus.Downloading -> "Downloading: ${(smartState.progress * 100).toInt()}%"
                            is com.example.util.SmartUpdateStatus.Merging -> "Applying Gzip-BSPatch Delta Merge..."
                            is com.example.util.SmartUpdateStatus.ReadyToInstall -> "Patched Build Ready to Install!"
                            is com.example.util.SmartUpdateStatus.Error -> {
                                val cleanMsg = smartState.message.replace(" [ALLOW_FORCE]", "")
                                "Failed: $cleanMsg"
                            }
                        }
                        
                        Text(
                            text = "STATUS: ${smartLabel.uppercase()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        if (smartState is com.example.util.SmartUpdateStatus.Downloading) {
                            val progress = smartState.progress
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (progress >= 0f) progress else 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF1F1F24)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    com.example.util.SmartUpdateManager.checkForUpdates(context, manualCheck = true)
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Check", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            if (smartState is com.example.util.SmartUpdateStatus.NewVersionAvailable) {
                                Button(
                                    onClick = {
                                        com.example.util.SmartUpdateManager.triggerSmartUpdate(context, smartState)
                                    },
                                    modifier = Modifier.weight(1.5f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Apply Update", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (smartState is com.example.util.SmartUpdateStatus.Error && smartState.message.contains("[ALLOW_FORCE]")) {
                                val latestVer = com.example.util.SmartUpdateManager.latestAvailableVersion
                                if (latestVer != null) {
                                    Button(
                                        onClick = {
                                            com.example.util.SmartUpdateManager.triggerSmartUpdate(context, latestVer, force = true)
                                        },
                                        modifier = Modifier.weight(1.5f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Force Apply", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (smartState is com.example.util.SmartUpdateStatus.Error && smartState.message.contains("Patch update failed")) {
                                val latestVer = com.example.util.SmartUpdateManager.latestAvailableVersion
                                if (latestVer != null) {
                                    Button(
                                        onClick = {
                                            val fullOnlyVer = latestVer.copy(patchFileUrl = null)
                                            com.example.util.SmartUpdateManager.triggerSmartUpdate(context, fullOnlyVer)
                                        },
                                        modifier = Modifier.weight(1.5f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Install Full Update", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            if (smartState is com.example.util.SmartUpdateStatus.ReadyToInstall) {
                                Button(
                                    onClick = {
                                        com.example.util.SmartUpdateManager.installApk(context, smartState.apkFile)
                                    },
                                    modifier = Modifier.weight(1.5f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Install Now", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 1. Status Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(WaterBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (updateStatus) {
                                is UpdateStatus.Checking -> Icons.Default.Refresh
                                is UpdateStatus.Downloading -> Icons.Default.ArrowDropDown
                                is UpdateStatus.ReadyToInstall -> Icons.Default.CheckCircle
                                is UpdateStatus.NewVersionAvailable -> Icons.Default.Info
                                is UpdateStatus.Error -> Icons.Default.Warning
                                else -> Icons.Default.Settings
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Status Icon",
                                tint = WaterBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "CURRENT VERSION: $currentVersionName (BUILD $currentVersionCode) | FIREBASE BUILD: $runningFirebaseCode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val statusLabel = when (val state = updateStatus) {
                            is UpdateStatus.Idle -> "App is up to date"
                            is UpdateStatus.Checking -> "Checking for updates..."
                            is UpdateStatus.SecuringData -> "Securing user data and performing auto-backup..."
                            is UpdateStatus.Downloading -> "Downloading app update: ${(state.progress * 100).toInt()}%"
                            is UpdateStatus.ReadyToInstall -> "App Update Downloaded & Ready"
                            is UpdateStatus.NewVersionAvailable -> "New Update Available: Build ${state.versionId}"
                            is UpdateStatus.NoUpdateAvailable -> "Your app is up to date (Build ${state.localVersion})"
                            is UpdateStatus.Error -> "Check failed: ${state.message}"
                        }

                        Text(
                            text = statusLabel.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        if (updateStatus is UpdateStatus.Downloading) {
                            val progress = (updateStatus as UpdateStatus.Downloading).progress
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { if (progress >= 0f) progress else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = WaterBlue,
                                trackColor = Color(0xFF1F1F24)
                            )
                        }
                    }
                }

                // 2. Manual Action Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MANUAL ACTIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    AppUpdateManager.checkForUpdates(context, manualCheck = true)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("check_updates_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp),
                            enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        // Download & Install button if a new version is available but not yet downloaded
                        if (updateStatus is UpdateStatus.NewVersionAvailable) {
                            val state = updateStatus as UpdateStatus.NewVersionAvailable
                            Button(
                                onClick = {
                                    AppUpdateManager.startDownloadAndInstall(context, state.apkFileId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("download_updates_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Download", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Install Build ${state.versionId}", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Install button if update downloaded
                        val canInstall = updateStatus is UpdateStatus.ReadyToInstall || isOfflineApkReady
                        val apkFileToInstall = when {
                            updateStatus is UpdateStatus.ReadyToInstall -> (updateStatus as UpdateStatus.ReadyToInstall).apkFile
                            isOfflineApkReady -> offlineApkFile
                            else -> null
                        }

                        if (canInstall && apkFileToInstall != null) {
                            Button(
                                onClick = {
                                    AppUpdateManager.installApk(context, apkFileToInstall)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("install_updates_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Install", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Downloaded Update", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Force Redownload & Re-sync button
                        Button(
                            onClick = {
                                AppUpdateManager.forceRedownloadUpdate(context)
                                Toast.makeText(context, "Cleaning files & initiating fresh redownload...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("force_redownload_updates_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Redownload", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Redownload & Re-sync", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // App Distribution specific Tester authentication
                        Button(
                            onClick = {
                                try {
                                    val appDist = com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance()
                                    if (!appDist.isTesterSignedIn) {
                                        Toast.makeText(context, "Opening Firebase App Distribution Login...", Toast.LENGTH_SHORT).show()
                                        appDist.signInTester()
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Firebase Tester Sign-In Succeeded!", Toast.LENGTH_LONG).show()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Sign-In Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        Toast.makeText(context, "Firebase Tester is already signed in!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "App Distribution Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("app_dist_sign_in_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1E)),
                            border = BorderStroke(1.dp, Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Tester Sign In", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Firebase Tester Authentication", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Update Preferences Toggles
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PREFERENCES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Auto download toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoDownload = !autoDownload
                                    AppUpdateManager.setAutoUpdateEnabled(context, autoDownload)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Download Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Automatically download APKs silently in the background on startup.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = autoDownload,
                                onCheckedChange = {
                                    autoDownload = it
                                    AppUpdateManager.setAutoUpdateEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }

                        HorizontalDivider(color = Color(0xFF16161A), thickness = 0.5.dp)

                        // Pause updates toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pauseUpdates = !pauseUpdates
                                    AppUpdateManager.setPauseUpdatesEnabled(context, pauseUpdates)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pause Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Temporarily pause silent background updates checks.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = pauseUpdates,
                                onCheckedChange = {
                                    pauseUpdates = it
                                    AppUpdateManager.setPauseUpdatesEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }

                        HorizontalDivider(color = Color(0xFF16161A), thickness = 0.5.dp)

                        // Force updates toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    forceUpdate = !forceUpdate
                                    AppUpdateManager.setForceUpdateEnabled(context, forceUpdate)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Force App Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Disallow bypassing updates when a new critical build is available.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = forceUpdate,
                                onCheckedChange = {
                                    forceUpdate = it
                                    AppUpdateManager.setForceUpdateEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }
                    }
                }

                // 3.5. Firebase Running Version Override
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "FIREBASE VERSION CONFIGURATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = "To prevent infinite installation loops on GitHub automated builds, Life OS tracks your current running version code independently from the hardcoded package codebase. Adjust this value to bypass or enable app updates manually.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Running Firebase Version", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (runningFirebaseCode > 1) {
                                            runningFirebaseCode -= 1
                                            AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                            Toast.makeText(context, "Running Firebase version updated to Build $runningFirebaseCode", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.background(Color(0xFF1E1E24), RoundedCornerShape(4.dp)).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "Decrease Version",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = runningFirebaseCode.toString(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(
                                    onClick = {
                                        runningFirebaseCode += 1
                                        AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                        Toast.makeText(context, "Running Firebase version updated to Build $runningFirebaseCode", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.background(Color(0xFF1E1E24), RoundedCornerShape(4.dp)).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Increase Version",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                runningFirebaseCode = currentVersionCode
                                AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                Toast.makeText(context, "Running Firebase version reset to match package code ($currentVersionCode)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1E)),
                            border = BorderStroke(1.dp, Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("Reset to Package Build Code ($currentVersionCode)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. Advanced Configurations
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ADVANCED SOURCE SOURCES (GITHUB)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedTextField(
                            value = githubOwner,
                            onValueChange = {
                                githubOwner = it
                                AppUpdateManager.setGithubOwner(context, it)
                            },
                            label = { Text("GitHub Owner", color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = githubRepo,
                            onValueChange = {
                                githubRepo = it
                                AppUpdateManager.setGithubRepo(context, it)
                            },
                            label = { Text("GitHub Repository Name", color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


@Composable
fun SettingsUserInfoPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "User Info",
            description = "Update your profile name and nickname.",
            onBack = onBack
        ) {
            val currentName = viewModel.userName.collectAsState().value
            val currentNickname = viewModel.userNickname.collectAsState().value
            val currentEmoji = viewModel.userEmoji.collectAsState().value
            var name by remember(currentName) { mutableStateOf(currentName) }
            var nickname by remember(currentNickname) { mutableStateOf(currentNickname) }
            var emoji by remember(currentEmoji) { mutableStateOf(currentEmoji) }
            var statusMsg by remember { mutableStateOf<String?>(null) }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            ) {
                UserAvatar(
                    emojiOrBase64 = currentEmoji,
                    size = 100.dp,
                    fontSize = 44.sp,
                    fallback = nickname.take(2).uppercase().ifEmpty { name.take(2).uppercase() }
                )
                Text(
                    text = "Configure your profile name, nickname, and custom avatar image URL or emoji link below.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Profile Picture URL / Custom Emoji Link", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (statusMsg != null) {
                    Text(statusMsg!!, color = if (statusMsg!!.startsWith("Updated")) Color.Green else Color.Red, fontSize = 12.sp)
                }
                
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            statusMsg = "Name is mandatory"
                            return@Button
                        }
                        viewModel.completeProfileSetup(name, nickname, emoji)
                        statusMsg = "Updated Successfully!"
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Details")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Firebase Diagnostics",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test the Firebase Crashlytics reporting SDK integration dynamically by forcing a JVM runtime crash.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        throw RuntimeException("Test Crashlytics Setup: Life OS Forced Crash")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Trigger Test Crash (Crashlytics)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



// ==================== CONSOLIDATED SETTINGS & ONBOARDING COMPONENTS ====================



// ==================== CONSOLIDATED FROM: AppListIcon.kt ====================



@Composable
fun AppListIcon(pkg: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var appName by remember { mutableStateOf(pkg) }

    LaunchedEffect(pkg) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                appName = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(pkg)
                iconBitmap = icon.toBitmap(config = android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            } catch (e: Exception) {
                // Ignore exceptions (e.g. package not found)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = appName,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 8.dp)
            )
        }
        Text(
            text = appName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}



// ==================== CONSOLIDATED FROM: AppLockOverlay.kt ====================



@Composable
fun AppLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val lockType = remember { AppLockHelper.getLockType(context) }
    val correctCode = remember { AppLockHelper.getLockCode(context) ?: "" }
    val biometricsEnabled = remember { AppLockHelper.isBiometricsEnabled(context) }

    var currentInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBiometricsActiveBySession by remember { mutableStateOf(biometricsEnabled) }
    var biometricFailCount by remember { mutableStateOf(0) }
    var isBiometricDialogShown by remember { mutableStateOf(biometricsEnabled) }

    // Security Question Backup State
    var showForgotPasswordState by remember { mutableStateOf(false) }
    val savedQuestions = remember { AppLockHelper.getSecurityQuestions(context) }
    // Randomly select one security question out of the 3 configured during init/setup
    val randomQuestionIndex = remember { Random.nextInt(3) }
    val selectedQuestionPair = remember {
        if (savedQuestions.size >= 3) savedQuestions[randomQuestionIndex] else Pair("What is your pet's name?", "")
    }
    var securityAnswerInput by remember { mutableStateOf("") }
    var securityAnswerError by remember { mutableStateOf<String?>(null) }

    // Auto-prompt system biometrics or dialog if active
    LaunchedEffect(isBiometricsActiveBySession) {
        if (isBiometricsActiveBySession) {
            isBiometricDialogShown = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        if (showForgotPasswordState) {
            // Security Question Verification Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Forgot Lock",
                    tint = WaterBlue,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "SECURITY RECOVERY",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Answer the backup question preset during your secure lock setup.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Backup Question:",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = selectedQuestionPair.first,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = securityAnswerInput,
                            onValueChange = {
                                securityAnswerInput = it
                                securityAnswerError = null
                            },
                            placeholder = { Text("Enter answer", color = Color.DarkGray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222)
                            )
                        )

                        if (securityAnswerError != null) {
                            Text(
                                text = securityAnswerError!!,
                                color = Color.Red,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                val inputNormalized = securityAnswerInput.trim().lowercase()
                                val correctNormalized = selectedQuestionPair.second.trim().lowercase()
                                if (inputNormalized == correctNormalized && correctNormalized.isNotEmpty()) {
                                    Toast.makeText(context, "Recovery Successful: App Lock bypassed and deactivated.", Toast.LENGTH_LONG).show()
                                    // Deactivate lock so they can set a new one
                                    AppLockHelper.setAppLockEnabled(context, false)
                                    AppLockHelper.setLockCode(context, null)
                                    onUnlocked()
                                } else {
                                    securityAnswerError = "Incorrect answer. Please try again."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Verify & Unlock App", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                showForgotPasswordState = false
                                securityAnswerInput = ""
                                securityAnswerError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Unlock Screen", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // Main App Unlocking Interface
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(top = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Encrypted",
                        tint = WaterBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "LIFE OS SECURE GATE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lockType == "pin") "Enter PIN to access your vault" else "Enter Password to unlock",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Input Box / PIN dots Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f),
                    contentAlignment = Alignment.Center
                ) {
                    if (lockType == "pin") {
                        // Custom Row of 4-6 Dot Indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotCount = maxOf(4, correctCode.length)
                            for (i in 0 until dotCount) {
                                val isFilled = i < currentInput.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = if (isFilled) WaterBlue else Color(0xFF222222),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isFilled) WaterBlue else Color.DarkGray,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    } else {
                        // Password Text Field Block
                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = {
                                currentInput = it
                                errorMessage = null
                                // Auto check password if they type and press enter (handled by button)
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Star else Icons.Default.Lock,
                                        contentDescription = "Toggle Visibility",
                                        tint = Color.Gray
                                    )
                                }
                            },
                            placeholder = { Text("Password", color = Color.DarkGray, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222)
                            )
                        )
                    }
                }

                // Keyboard / Input Pad Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(3f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (lockType == "pin") {
                        // Numeric Keypad Layout
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("Forgot", "0", "Back")
                        )

                        keys.forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowKeys.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .background(
                                                color = if (key == "Forgot" || key == "Back") Color.Transparent else Color(0xFF0C0C0C),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (key == "Forgot" || key == "Back") Color.Transparent else Color(0xFF222222),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                when (key) {
                                                    "Forgot" -> {
                                                        showForgotPasswordState = true
                                                    }
                                                    "Back" -> {
                                                        if (currentInput.isNotEmpty()) {
                                                            currentInput = currentInput.dropLast(1)
                                                        }
                                                    }
                                                    else -> {
                                                        if (currentInput.length < correctCode.length) {
                                                            errorMessage = null
                                                            val newInput = currentInput + key
                                                            currentInput = newInput
                                                            
                                                            // Auto check PIN if complete
                                                            if (newInput.length == correctCode.length) {
                                                                if (newInput == correctCode) {
                                                                    onUnlocked()
                                                                } else {
                                                                    errorMessage = "Invalid PIN"
                                                                    currentInput = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (key == "Back") {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Backspace",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else if (key == "Forgot") {
                                            Text(
                                                text = "Forgot?",
                                                color = WaterBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Password Submit Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Button(
                                onClick = {
                                    if (currentInput == correctCode) {
                                        onUnlocked()
                                    } else {
                                        errorMessage = "Invalid Password"
                                        currentInput = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Unlock App", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showForgotPasswordState = true }) {
                                    Text("Forgot Password?", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isBiometricsActiveBySession && biometricsEnabled) {
                        // Display biometric scan fallback trigger
                        IconButton(
                            onClick = { isBiometricDialogShown = true },
                            modifier = Modifier
                                .background(Color(0xFF111111), CircleShape)
                                .border(1.dp, WaterBlue.copy(alpha = 0.5f), CircleShape)
                                .size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Trigger Biometric Scan",
                                tint = WaterBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // Biometric dialog / simulation layout overlay
        if (isBiometricDialogShown && isBiometricsActiveBySession) {
            BiometricVerificationOverlay(
                onSuccess = {
                    isBiometricDialogShown = false
                    onUnlocked()
                },
                onFail = {
                    biometricFailCount++
                    if (biometricFailCount >= 3) {
                        isBiometricsActiveBySession = false // Shut down fingerprint till next cycle
                        isBiometricDialogShown = false
                        Toast.makeText(context, "Fingerprint verification disabled after 3 failed attempts. Enter PIN/Password.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Scan Failed! Attempts: $biometricFailCount/3", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    isBiometricDialogShown = false
                }
            )
        }
    }
}

@Composable
fun BiometricVerificationOverlay(
    onSuccess: () -> Unit,
    onFail: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isHoldingDown by remember { mutableStateOf(false) }
    var touchProgress by remember { mutableStateOf(0f) }
    var scanStateText by remember { mutableStateOf("Hold your finger on the sensor below") }

    // Pulsing circle animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Touch holding loop simulation
    LaunchedEffect(isHoldingDown) {
        if (isHoldingDown) {
            scanStateText = "Scanning biological characteristics..."
            var progress = 0.0f
            while (progress < 1f) {
                delay(40)
                progress += 0.04f
                touchProgress = progress
            }
            touchProgress = 1.0f
            scanStateText = "Verifying biometric integrity..."
            delay(300)
            
            // Randomly succeed or fail to allow thorough simulation checking
            // We favor success (85%) but allow testing failures by letting go early or random 15% fail
            if (Random.nextFloat() < 0.85f) {
                scanStateText = "Access Authorized!"
                delay(300)
                onSuccess()
            } else {
                scanStateText = "Verification Failed"
                delay(300)
                onFail()
                touchProgress = 0f
                isHoldingDown = false
            }
        } else {
            touchProgress = 0f
            scanStateText = "Hold your finger on the sensor below"
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel PIN Bypass", color = Color.Gray, fontSize = 11.sp)
            }
        },
        containerColor = Color(0xFF0F0F0F),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Face, contentDescription = "Scanner", tint = WaterBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Biometric Scanner", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = scanStateText,
                    color = if (scanStateText.startsWith("Verification")) Color.Red else Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(if (isHoldingDown) 1.0f else pulseScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(WaterBlue.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Fingerprint Touch Area with visual holding-down ring
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000))
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        WaterBlue, 
                                        Color(0xFF03A9F4).copy(alpha = 0.5f), 
                                        WaterBlue.copy(alpha = touchProgress)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        try {
                                            isHoldingDown = true
                                            awaitRelease()
                                        } finally {
                                            if (touchProgress < 1.0f) {
                                                isHoldingDown = false
                                                touchProgress = 0f
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Hold Scan",
                            tint = if (isHoldingDown) WaterBlue else Color.Gray,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = "Press & Hold sensor area to verify Identity in streaming emulator.",
                    color = Color.DarkGray,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                // Simulated direct test buttons for quick navigation in browser
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSuccess() },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Green)
                    ) {
                        Text("Simulate OK", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { onFail() },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Simulate Fail", fontSize = 10.sp)
                    }
                }
            }
        }
    )
}



// ==================== CONSOLIDATED FROM: PermissionOnboardingView.kt ====================



@Composable
fun PermissionOnboardingView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Permission States
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var hasDrivePermission by remember { mutableStateOf(false) }
    var hasPackageInstallPermission by remember { mutableStateOf(false) }
    var hasExactAlarmPermission by remember { mutableStateOf(false) }

    // Drive automatic backup check & restore states
    var isCheckingDriveData by remember { mutableStateOf(false) }
    var driveCheckMessage by remember { mutableStateOf("") }
    var hasCheckedDriveData by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Check permissions helper
    val checkAllPermissions = {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasUsageStatsPermission = AppBlockHelper.hasUsageStatsPermission(context)
        hasAccessibilityPermission = AppBlockHelper.isAccessibilityServiceEnabled(context)
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        hasPackageInstallPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // Polling loop to dynamically detect system changes (e.g. when returning from Settings screen)
    LaunchedEffect(Unit) {
        while (true) {
            checkAllPermissions()
            delay(1000)
        }
    }

    // Auto-check Drive and retrieve if permission is acquired during installation
    LaunchedEffect(hasDrivePermission) {
        if (hasDrivePermission && !hasCheckedDriveData) {
            hasCheckedDriveData = true
            isCheckingDriveData = true
            driveCheckMessage = "🔍 Connecting to Google Drive to check for existing focus/db data..."
            try {
                val hasBackup = kotlinx.coroutines.withTimeoutOrNull(15000L) { GoogleDriveSyncManager.hasExistingBackupData(context) } ?: false
                if (hasBackup) {
                    driveCheckMessage = "📦 Existing backup data found on Google Drive! Retrieving and restoring data..."
                    delay(1500)
                    val (success, msg) = kotlinx.coroutines.withTimeoutOrNull(30000L) { GoogleDriveSyncManager.checkAndRetrieveDriveData(context, viewModel.appDatabase) } ?: Pair(false, "Timeout restoring data")
                    if (success) {
                        driveCheckMessage = "✅ Successfully restored backup data! Taking you to the user interface..."
                        delay(2000)
                        viewModel.navigateTo(viewModel.getDefaultScreen())
                    } else {
                        driveCheckMessage = "⚠️ Found backup but failed to restore: $msg. Proceeding..."
                        delay(2000)
                        isCheckingDriveData = false
                    }
                } else {
                    driveCheckMessage = "ℹ️ No existing backup found on Google Drive. Ready to start fresh!"
                    delay(1500)
                    isCheckingDriveData = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                driveCheckMessage = "⚠️ Error communicating with Google Drive: ${e.message}"
                delay(2000)
                isCheckingDriveData = false
            }
        }
    }

    // Launchers for permissions
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
        }
    )

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val email = account?.email ?: ""
                val displayName = account?.displayName ?: ""
                val idToken = account?.idToken
                if (email.isNotEmpty()) {
                    val username = email.substringBefore("@").replace(".", "_")
                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                    scope.launch {
                        GoogleDriveSyncManager.getAccessToken(context) { intent ->
                            authResolutionLauncher.launch(intent)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Google Sign-In failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSlate,
                        Color(0xFF0F111A),
                        Color(0xFF030305)
                    )
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header Section
            Icon(
                imageVector = Icons.Default.SettingsSuggest,
                contentDescription = "System Setup Required",
                tint = WaterBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "System Integration",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Life OS requires specific integrations to ensure offline synchronization, background timers, and app usage monitoring work reliably.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Mandatory Battery Optimization Section
            PermissionCard(
                title = "Battery Optimization (Mandatory)",
                description = "Android restricts apps running background daemons to save power. To keep focus timers and widget syncing active, this must be disabled.",
                isGranted = isBatteryOptIgnored,
                icon = Icons.Default.BatteryAlert,
                accentColor = if (isBatteryOptIgnored) SuccessGreen else AlertRed,
                buttonText = if (isBatteryOptIgnored) "Disabled (Optimal)" else "Request Disable",
                onButtonClick = {
                    if (!isBatteryOptIgnored) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                android.widget.Toast.makeText(context, "Please open Settings and disable battery optimization manually.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Notification Permission Section
            PermissionCard(
                title = "Push Notifications (Highly Recommended)",
                description = "Used to play alarm sounds, alert you when your Focus Session ends, and display daily reminders.",
                isGranted = hasNotificationPermission,
                icon = Icons.Default.NotificationsActive,
                accentColor = if (hasNotificationPermission) SuccessGreen else AccentOrange,
                buttonText = if (hasNotificationPermission) "Granted" else "Request Permission",
                onButtonClick = {
                    if (!hasNotificationPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Draw Over Other Apps Overlay Section
            PermissionCard(
                title = "System Overlay (Mandatory)",
                description = "Required to show the full-screen 'Focus Intercept' overlay when you attempt to open a blocked app (e.g. social media). Without this, Android background launch restrictions will silent-fail the app blocker.",
                isGranted = hasOverlayPermission,
                icon = Icons.Default.FlipToFront,
                accentColor = if (hasOverlayPermission) SuccessGreen else AlertRed,
                buttonText = if (hasOverlayPermission) "Enabled" else "Configure Overlay",
                onButtonClick = {
                    if (!hasOverlayPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Usage Statistics Permission Section
            PermissionCard(
                title = "App Usage Tracking (Mandatory)",
                description = "Required to monitor active foreground apps, enforce daily time limits, and gather real-time habit analytics.",
                isGranted = hasUsageStatsPermission,
                icon = Icons.Default.Timeline,
                accentColor = if (hasUsageStatsPermission) SuccessGreen else AlertRed,
                buttonText = if (hasUsageStatsPermission) "Enabled" else "Grant Usage Stats",
                onButtonClick = {
                    if (!hasUsageStatsPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4b. Accessibility Blocker Service Section (Mandatory)
            val accessibilityDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasAccessibilityPermission) {
                "Required to block Reels, Shorts, Spotlight, and specific app sections inside social media. Without this, selective app blocking is not possible.\n\n" +
                "⚠️ Android 13+ Restricted Settings:\n" +
                "If 'Life OS' is greyed out in Accessibility settings:\n" +
                "1. Go to phone Settings -> Apps -> Life OS.\n" +
                "2. Tap the three dots icon in the top right corner.\n" +
                "3. Tap 'Allow restricted settings' and confirm your PIN/pattern.\n" +
                "4. Return here and tap 'Enable Accessibility' again."
            } else {
                "Required to block Reels, Shorts, Spotlight, and specific app sections inside social media. Without this, selective app blocking is not possible."
            }

            PermissionCard(
                title = "Accessibility Blocker Service (Mandatory)",
                description = accessibilityDesc,
                isGranted = hasAccessibilityPermission,
                icon = Icons.Default.Accessibility,
                accentColor = if (hasAccessibilityPermission) SuccessGreen else AlertRed,
                buttonText = if (hasAccessibilityPermission) "Enabled" else "Enable Accessibility",
                onButtonClick = {
                    if (!hasAccessibilityPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            isCheckingDriveData = false
                            throw e
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically. Please go to Accessibility Settings and enable Life OS.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Google Drive Sync Authorization Section
            PermissionCard(
                title = "Google Drive Sync (Recommended)",
                description = "Securely backup and restore your focus sessions, metrics, and configurations in your personal Google Drive account.",
                isGranted = hasDrivePermission,
                icon = Icons.Default.CloudQueue,
                accentColor = if (hasDrivePermission) SuccessGreen else AccentOrange,
                buttonText = if (hasDrivePermission) "Authorized" else "Authorize Sync",
                onButtonClick = {
                    if (!hasDrivePermission) {
                        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                        if (googleAccount == null) {
                            try {
                                val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                val webClientId = try {
                                    context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                                } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                    ""
                                }
                                val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(driveScope)
                                if (webClientId.isNotEmpty()) {
                                    gsoBuilder.requestIdToken(webClientId)
                                }
                                val gso = gsoBuilder.build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            scope.launch {
                                GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                    authResolutionLauncher.launch(intent)
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Install Unknown Apps Permission Section (Mandatory)
            PermissionCard(
                title = "Install App Updates (Mandatory)",
                description = "Required to automatically install downloaded Life OS app updates. Without this, the app cannot update itself in-place.",
                isGranted = hasPackageInstallPermission,
                icon = Icons.Default.SystemUpdate,
                accentColor = if (hasPackageInstallPermission) SuccessGreen else AlertRed,
                buttonText = if (hasPackageInstallPermission) "Enabled" else "Configure Updates",
                onButtonClick = {
                    if (!hasPackageInstallPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(context, "Please open Settings -> Special App Access -> Install Unknown Apps to grant access.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Exact Alarm Permission Section (Mandatory)
            PermissionCard(
                title = "Exact Alarms Scheduling (Mandatory)",
                description = "Required to guarantee that focus timers and alarm notifications fire precisely when expected. Without this, Android 14+ will block exact scheduling and crash the app.",
                isGranted = hasExactAlarmPermission,
                icon = Icons.Default.Alarm,
                accentColor = if (hasExactAlarmPermission) SuccessGreen else AlertRed,
                buttonText = if (hasExactAlarmPermission) "Granted" else "Grant Exact Alarms",
                onButtonClick = {
                    if (!hasExactAlarmPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Please open Settings and grant Schedule Exact Alarm permission manually.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Optional integrations info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "All system integrations (Battery Optimization, Notifications, Overlay, Usage Tracking, Accessibility Service, Update Installation, and Exact Alarms) are fully optional. Feel free to configure any of them above or proceed to your dashboard immediately.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Bottom Action Button (Always Enabled)
            Button(
                onClick = {
                    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    appPrefs.edit().putBoolean("permissions_onboarding_shown", true).apply()
                    viewModel.navigateTo(viewModel.getDefaultScreen())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = WaterBlue,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_proceed_button")
            ) {
                Text(
                    text = "Proceed to Dashboard",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Sync runs silently in background without showing a popup box
    if (false && isCheckingDriveData) {
        // Suppressed popup box as per user preference
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isGranted) "Status: Enabled" else "Status: Restricted / Pending",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Pending",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) accentColor.copy(alpha = 0.15f) else accentColor,
                    contentColor = if (isGranted) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}



// ==================== CONSOLIDATED FROM: SocialOnboardingView.kt ====================



@Composable
fun SocialOnboardingView(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val isFirstTime = remember { !appPrefs.contains("social_blocker_settings_shown_version") }

    // Preferences for Social Apps
    // 1. Instagram
    var igBlocked by remember { mutableStateOf(if (isFirstTime) true else AppBlockHelper.isAppInBlockList(context, "com.instagram.android")) }
    var igSelective by remember { mutableStateOf(AppBlockHelper.isIgSelectiveBlockingEnabled(context)) }
    var igReels by remember { mutableStateOf(AppBlockHelper.isIgReelsBlocked(context)) }
    var igStories by remember { mutableStateOf(AppBlockHelper.isIgStoriesBlocked(context)) }
    var igExplore by remember { mutableStateOf(AppBlockHelper.isIgExploreBlocked(context)) }
    var igLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.instagram.android")) }

    // 2. Snapchat
    var snapBlocked by remember { mutableStateOf(if (isFirstTime) true else AppBlockHelper.isAppInBlockList(context, "com.snapchat.android")) }
    var snapSelective by remember { mutableStateOf(AppBlockHelper.isSnapSelectiveBlockingEnabled(context)) }
    var snapSpotlight by remember { mutableStateOf(AppBlockHelper.isSnapSpotlightBlocked(context)) }
    var snapMap by remember { mutableStateOf(AppBlockHelper.isSnapMapBlocked(context)) }
    var snapDiscover by remember { mutableStateOf(AppBlockHelper.isSnapDiscoverBlocked(context)) }
    var snapLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.snapchat.android")) }

    // 3. Facebook
    var fbBlocked by remember { mutableStateOf(AppBlockHelper.isAppInBlockList(context, "com.facebook.katana")) }
    var fbSelective by remember { mutableStateOf(AppBlockHelper.isFbSelectiveBlockingEnabled(context)) }
    var fbReels by remember { mutableStateOf(AppBlockHelper.isFbReelsBlocked(context)) }
    var fbWatch by remember { mutableStateOf(AppBlockHelper.isFbWatchBlocked(context)) }
    var fbStories by remember { mutableStateOf(AppBlockHelper.isFbStoriesBlocked(context)) }
    var fbLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.facebook.katana")) }

    // Permissions States
    var hasUsageStats by remember { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(false) }

    // Periodically check permissions state
    LaunchedEffect(Unit) {
        while (true) {
            hasUsageStats = AppBlockHelper.hasUsageStatsPermission(context)
            hasAccessibility = isAccessibilityServiceEnabled(context)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070D))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WaterBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Focus Guard Setup",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Configure your social media limits and app blocker defaults to secure your focus and study hours.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Notification / Alert Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "By default, Instagram and Snapchat have been added to your app blocker list. Bedtime / Wake-up alarm has been configured OFF by default.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // 1. Instagram Config Card
            SocialBlockerConfigCard(
                appName = "Instagram",
                packageName = "com.instagram.android",
                isBlocked = igBlocked,
                onBlockedChange = { igBlocked = it },
                useSelective = igSelective,
                onSelectiveChange = { igSelective = it },
                screenLimit = igLimit,
                onLimitChange = { igLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block Reels", igReels) { igReels = it },
                    SubToggleItem("Block Stories", igStories) { igStories = it },
                    SubToggleItem("Block Explore Feed", igExplore) { igExplore = it }
                )
            )

            // 2. Snapchat Config Card
            SocialBlockerConfigCard(
                appName = "Snapchat",
                packageName = "com.snapchat.android",
                isBlocked = snapBlocked,
                onBlockedChange = { snapBlocked = it },
                useSelective = snapSelective,
                onSelectiveChange = { snapSelective = it },
                screenLimit = snapLimit,
                onLimitChange = { snapLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block Spotlight", snapSpotlight) { snapSpotlight = it },
                    SubToggleItem("Block Snap Map", snapMap) { snapMap = it },
                    SubToggleItem("Block Discover", snapDiscover) { snapDiscover = it }
                )
            )

            // 3. Facebook Config Card
            SocialBlockerConfigCard(
                appName = "Facebook",
                packageName = "com.facebook.katana",
                isBlocked = fbBlocked,
                onBlockedChange = { fbBlocked = it },
                useSelective = fbSelective,
                onSelectiveChange = { fbSelective = it },
                screenLimit = fbLimit,
                onLimitChange = { fbLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block FB Reels", fbReels) { fbReels = it },
                    SubToggleItem("Block FB Watch", fbWatch) { fbWatch = it },
                    SubToggleItem("Block FB Stories", fbStories) { fbStories = it }
                )
            )

            // Permissions Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Charcoal),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "System Permissions Requirements",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                    // Usage Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Access", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tracks screen-time usage limit dynamically.", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (hasUsageStats) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Granted",
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Accessibility Service
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility Service", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Enforces fine-grained content blocking safely.", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (hasAccessibility) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Enabled",
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Save and Continue Button
            Button(
                onClick = {
                    // Apply Settings
                    // 1. Instagram Settings
                    if (igBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.instagram.android")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.instagram.android")
                    }
                    AppBlockHelper.setIgSelectiveBlockingEnabled(context, igSelective)
                    AppBlockHelper.setIgReelsBlocked(context, igReels)
                    AppBlockHelper.setIgStoriesBlocked(context, igStories)
                    AppBlockHelper.setIgExploreBlocked(context, igExplore)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.instagram.android", igLimit)

                    // 2. Snapchat Settings
                    if (snapBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.snapchat.android")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.snapchat.android")
                    }
                    AppBlockHelper.setSnapSelectiveBlockingEnabled(context, snapSelective)
                    AppBlockHelper.setSnapSpotlightBlocked(context, snapSpotlight)
                    AppBlockHelper.setSnapMapBlocked(context, snapMap)
                    AppBlockHelper.setSnapDiscoverBlocked(context, snapDiscover)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.snapchat.android", snapLimit)

                    // 3. Facebook Settings
                    if (fbBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.facebook.katana")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.facebook.katana")
                    }
                    AppBlockHelper.setFbSelectiveBlockingEnabled(context, fbSelective)
                    AppBlockHelper.setFbReelsBlocked(context, fbReels)
                    AppBlockHelper.setFbWatchBlocked(context, fbWatch)
                    AppBlockHelper.setFbStoriesBlocked(context, fbStories)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.facebook.katana", fbLimit)

                    // Default Morning Wakeup Alarm is off by default:
                    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    if (!appPrefs.contains("wakeup_alarm_enabled")) {
                        appPrefs.edit().putBoolean("wakeup_alarm_enabled", false).apply()
                    }

                    // Save that we showed this startup page
                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).let {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                it.longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION")
                                it.versionCode
                            }
                        }
                    } catch (e: Exception) {
                        1
                    }
                    appPrefs.edit()
                        .putInt("social_blocker_settings_shown_version", currentVersion)
                        .putBoolean("focus_guard_onboarding_shown_forever", true)
                        .apply()

                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Apply Settings & Launch Life OS",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class SubToggleItem(
    val title: String,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
fun SocialBlockerConfigCard(
    appName: String,
    packageName: String,
    isBlocked: Boolean,
    onBlockedChange: (Boolean) -> Unit,
    useSelective: Boolean,
    onSelectiveChange: (Boolean) -> Unit,
    screenLimit: Int,
    onLimitChange: (Int) -> Unit,
    subToggles: List<SubToggleItem>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Name and Master Block Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = appName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = packageName,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isBlocked,
                    onCheckedChange = onBlockedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WaterBlue,
                        checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                    )
                )
            }

            if (isBlocked) {
                HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Selective Content Blocker Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selective Content Blocking",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blocks specific sections instead of the whole app.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = useSelective,
                        onCheckedChange = onSelectiveChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WaterBlue,
                            checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                        )
                    )
                }

                // If selective is enabled, show the sub-toggles
                if (useSelective) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subToggles.forEach { toggleItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = toggleItem.title,
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                                Switch(
                                    checked = toggleItem.isChecked,
                                    onCheckedChange = toggleItem.onCheckedChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = WaterBlue,
                                        checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.scaleModifier(0.85f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Screen Time daily limit changing option
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Screen Time Limit",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Automatically block the app after limit expires.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (screenLimit > 0) "$screenLimit min" else "No Limit",
                            color = if (screenLimit > 0) WaterBlue else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Plus / Minus adjustments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (screenLimit >= 5) onLimitChange(screenLimit - 5) else onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("-5m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { if (screenLimit >= 15) onLimitChange(screenLimit - 15) else onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("-15m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(36.dp)
                        ) {
                            Text("Disable", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(screenLimit + 15) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("+15m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(screenLimit + 30) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("+30m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Simple modifier extension to safely scale custom Switches
fun Modifier.scaleModifier(scale: Float): Modifier = this.then(
    Modifier.scale(scale)
)

// Helper to check if accessibility service is enabled
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return AppBlockHelper.isAccessibilityServiceEnabled(context)
}



// ==================== CONSOLIDATED FROM: ProfilePicEditor.kt ====================



@Composable
fun ProfilePicEditor(
    initialValue: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var activeValue by remember { mutableStateOf(initialValue) }
    
    val isPhotoActive = activeValue.startsWith("base64:") || activeValue.startsWith("http://") || activeValue.startsWith("https://")

    // Gallery picker state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Cropper adjustments
    var zoomScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isCroppingMode by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val bitmap = uriToBitmapProfilePic(context, uri)
            if (bitmap != null) {
                loadedBitmap = bitmap
                zoomScale = 1f
                offsetX = 0f
                offsetY = 0f
                isCroppingMode = true
            }
        }
    }

    // Update parent whenever we have a confirmed new value
    val confirmValue = { newValue: String ->
        activeValue = newValue
        onValueChange(newValue)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current avatar preview
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                emojiOrBase64 = activeValue,
                size = 100.dp,
                fontSize = 48.sp,
                fallback = "👤"
            )
        }

        // Custom Photo Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPhotoActive) "Change Photo" else "Upload Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (isPhotoActive) {
                Text(
                    text = "✓ Profile photo is active",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "No custom photo uploaded yet",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // WhatsApp-style Full Screen Dialog Cropper
        if (isCroppingMode && loadedBitmap != null) {
            Dialog(
                onDismissRequest = { isCroppingMode = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight
                    
                    val circleDiameterDp = minOf(screenWidth, screenHeight) - 48.dp
                    val circleDiameterPx = with(density) { circleDiameterDp.toPx() }
                    
                    val bmWidth = loadedBitmap!!.width.toFloat()
                    val bmHeight = loadedBitmap!!.height.toFloat()
                    
                    // We want the image to fit so that it fully covers the circle initially
                    val baseScale = circleDiameterPx / minOf(bmWidth, bmHeight)
                    val layoutWidthDp = with(density) { (bmWidth * baseScale).toDp() }
                    val layoutHeightDp = with(density) { (bmHeight * baseScale).toDp() }

                    // Interactive touch-to-drag and pinch-to-zoom area (entire screen)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(loadedBitmap) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                    
                                    val totalScale = baseScale * zoomScale
                                    val wz = bmWidth * totalScale
                                    val hz = bmHeight * totalScale
                                    
                                    val maxOffsetX = (wz - circleDiameterPx) / 2f
                                    val maxOffsetY = (hz - circleDiameterPx) / 2f
                                    
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // The loaded image itself, responsive to zoom & pan offset
                        Image(
                            bitmap = loadedBitmap!!.asImageBitmap(),
                            contentDescription = "Upload Source",
                            modifier = Modifier
                                .size(width = layoutWidthDp, height = layoutHeightDp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Crop
                        )

                        // WhatsApp-style translucent background mask with clean circular hole
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            
                            val path = androidx.compose.ui.graphics.Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
                            }
                            val circlePath = androidx.compose.ui.graphics.Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(
                                    canvasWidth / 2f - circleDiameterPx / 2f,
                                    canvasHeight / 2f - circleDiameterPx / 2f,
                                    canvasWidth / 2f + circleDiameterPx / 2f,
                                    canvasHeight / 2f + circleDiameterPx / 2f
                                ))
                            }
                            
                            val maskPath = androidx.compose.ui.graphics.Path.combine(
                                androidx.compose.ui.graphics.PathOperation.Difference,
                                path,
                                circlePath
                            )
                            
                            drawPath(
                                path = maskPath,
                                color = Color.Black.copy(alpha = 0.75f)
                            )
                            
                            // Fine white boundary around circular crop zone
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = circleDiameterPx / 2f,
                                center = androidx.compose.ui.geometry.Offset(canvasWidth / 2f, canvasHeight / 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        // Text instructions at top of screen
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Move and Zoom",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Drag to reposition • Pinch with two fingers to resize",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        // WhatsApp-style Cancel and Apply actions at bottom of screen
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isCroppingMode = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }

                            Button(
                                onClick = {
                                    val cropped = cropBitmapToSquareAndScale(
                                        loadedBitmap!!,
                                        zoomScale,
                                        offsetX,
                                        offsetY,
                                        circleDiameterPx,
                                        baseScale
                                    )
                                    val base64 = bitmapToBase64(cropped)
                                    confirmValue(base64)
                                    isCroppingMode = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper methods for Bitmap crop & conversion
private fun uriToBitmapProfilePic(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmapToSquareAndScale(
    bitmap: Bitmap,
    zoomScale: Float,
    offsetX: Float,
    offsetY: Float,
    circleDiameterPx: Float,
    baseScale: Float
): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    val totalScale = baseScale * zoomScale
    val cropSizeOnBitmap = circleDiameterPx / totalScale
    
    // Calculate top-left coordinates on the original bitmap
    val startX = bitmapWidth / 2f - (offsetX + circleDiameterPx / 2f) / totalScale
    val startY = bitmapHeight / 2f - (offsetY + circleDiameterPx / 2f) / totalScale
    
    val startXCoerced = startX.toInt().coerceIn(0, (bitmapWidth - cropSizeOnBitmap).toInt().coerceAtLeast(0))
    val startYCoerced = startY.toInt().coerceIn(0, (bitmapHeight - cropSizeOnBitmap).toInt().coerceAtLeast(0))
    val sizeCoerced = cropSizeOnBitmap.toInt().coerceIn(50, minOf(bitmap.width, bitmap.height))
    
    val cropped = Bitmap.createBitmap(bitmap, startXCoerced, startYCoerced, sizeCoerced, sizeCoerced)
    val scaled = Bitmap.createScaledBitmap(cropped, 192, 192, true)
    
    if (cropped != bitmap) {
        cropped.recycle()
    }
    return scaled
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val bytes = outputStream.toByteArray()
    return "base64:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
}



// ==================== CONSOLIDATED FROM: ProfileSetupView.kt ====================



@Composable
fun ProfileSetupView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val currentName = viewModel.userName.collectAsState().value
    val currentNickname = viewModel.userNickname.collectAsState().value
    val currentEmoji = viewModel.userEmoji.collectAsState().value
    var name by remember(currentName) { mutableStateOf(currentName) }
    var nickname by remember(currentNickname) { mutableStateOf(currentNickname) }
    var emoji by remember(currentEmoji) { mutableStateOf(currentEmoji) }
    var roomId by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isCheckingCloud by remember { mutableStateOf(true) }

    // On enter, trigger a refresh of the user's remote profile from Firebase
    LaunchedEffect(Unit) {
        isCheckingCloud = true
        viewModel.refreshCurrentUserProfile()
        // Wait a small delay to allow network to return
        kotlinx.coroutines.delay(2000)
        isCheckingCloud = false
    }

    // Auto-advance if we find a complete profile details from Firebase
    LaunchedEffect(currentName, currentEmoji, currentNickname) {
        if (currentName.isNotEmpty() && currentEmoji.isNotEmpty()) {
            isCheckingCloud = false
            viewModel.completeProfileSetup(
                currentName,
                currentNickname,
                currentEmoji
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).fillMaxWidth(0.9f)
        ) {
            Text("Complete Your Profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please enter your details to register or continue", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (isCheckingCloud && currentName.isEmpty()) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking for existing profile in cloud...", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
            }

            UserAvatar(
                emojiOrBase64 = emoji,
                size = 100.dp,
                fontSize = 44.sp,
                fallback = nickname.take(2).uppercase().ifEmpty { name.take(2).uppercase() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Profile picture is configured under the Arena tab as a Custom Emoji Link.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (Mandatory)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = roomId,
                onValueChange = { roomId = it },
                label = { Text("Study Room ID (Optional)") },
                placeholder = { Text("ROOM_...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (errorMsg != null) {
                Text(errorMsg!!, color = Color.Red, modifier = Modifier.padding(bottom = 12.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.refreshCurrentUserProfile()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Sync Cloud")
                }
                
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMsg = "Name is mandatory"
                            return@Button
                        }
                        viewModel.completeProfileSetup(name, nickname, emoji)
                        if (roomId.isNotBlank()) {
                            val email = viewModel.userEmail.value
                            com.example.api.FocusLockerManager.joinRoom(context, email, roomId.trim())
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save & Continue")
                }
            }
        }
    }
}



// ==================== CONSOLIDATED FROM: UserAvatar.kt ====================

object AvatarCacheManager {
    fun getCachedAvatarOrDownload(context: android.content.Context, urlString: String, onBitmapReady: (android.graphics.Bitmap?) -> Unit) {
        val cacheDir = java.io.File(context.cacheDir, "avatar_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val filename = "avatar_" + urlString.hashCode() + ".jpg"
        val cachedFile = java.io.File(cacheDir, filename)
        
        // 24 hours cache limit
        val oneDayMs = 24L * 60 * 60 * 1000L
        val isCacheValid = cachedFile.exists() && (System.currentTimeMillis() - cachedFile.lastModified() < oneDayMs)
        
        if (isCacheValid) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null) {
                    onBitmapReady(bitmap)
                    return
                }
            } catch (e: Exception) {
                // fall through to download if decode fails
            }
        }
        
        // Asynchronously download the avatar and cache it
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var downloadedBitmap: android.graphics.Bitmap? = null
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    // Save to cachedFile
                    java.io.FileOutputStream(cachedFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    downloadedBitmap = bitmap
                }
            } catch (e: Exception) {
                android.util.Log.e("AvatarCacheManager", "Error downloading avatar", e)
            }
            
            // Deliver the bitmap back on Main dispatcher
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (downloadedBitmap != null) {
                    onBitmapReady(downloadedBitmap)
                } else if (cachedFile.exists()) {
                    try {
                        onBitmapReady(android.graphics.BitmapFactory.decodeFile(cachedFile.absolutePath))
                    } catch (e: Exception) {
                        onBitmapReady(null)
                    }
                } else {
                    onBitmapReady(null)
                }
            }
        }
    }
}


@Composable
fun UserAvatar(
    emojiOrBase64: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    size: Dp = 24.dp,
    fallback: String = "",
    online: Boolean? = null
) {
    val context = LocalContext.current
    val trimmed = emojiOrBase64?.trim() ?: ""
    val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains(".") || trimmed.contains("/")

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (trimmed.isEmpty() || trimmed == "👤") {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (fallback.isNotEmpty() && fallback != "👤") {
                    Text(
                        text = fallback,
                        fontSize = (size.value * 0.4).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Avatar Placeholder",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        } else if (trimmed.startsWith("base64:")) {
            val base64Data = trimmed.substringAfter("base64:")
            val bitmap = remember(base64Data) {
                try {
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Avatar Placeholder",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        } else if (isUrl) {
            var cachedBitmap by remember(trimmed) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(trimmed) {
                val fullUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    "https://$trimmed"
                } else {
                    trimmed
                }
                AvatarCacheManager.getCachedAvatarOrDownload(context, fullUrl) { bitmap ->
                    cachedBitmap = bitmap
                }
            }
            
            if (cachedBitmap != null) {
                Image(
                    bitmap = cachedBitmap!!.asImageBitmap(),
                    contentDescription = "User Avatar (Cached)",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val fullUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    "https://$trimmed"
                } else {
                    trimmed
                }
                coil.compose.AsyncImage(
                    model = fullUrl,
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            // It's a non-image string (possibly a legacy emoji, name, or placeholder)
            val firstChar = if (trimmed.isNotEmpty()) trimmed.substring(0, 1).uppercase() else ""
            val isLetterOrDigit = firstChar.isNotEmpty() && firstChar[0].isLetterOrDigit()
            
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLetterOrDigit) {
                    Text(
                        text = firstChar,
                        fontSize = (size.value * 0.45).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else if (trimmed.isNotEmpty()) {
                    Text(
                        text = trimmed,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Avatar Placeholder",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        }

        if (online != null) {
            Box(
                modifier = Modifier
                    .size(maxOf(8.dp, size / 3))
                    .clip(CircleShape)
                    .background(if (online) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color(0xFF101010), CircleShape)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}



// ==================== CONSOLIDATED FROM: WaterReminderBanner.kt ====================



@Composable
fun WaterReminderBanner(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val glasses by viewModel.waterGlassesToday.collectAsState()
    val intervalMins by viewModel.waterReminderIntervalMins.collectAsState()
    val startTime by viewModel.waterReminderStartTime.collectAsState()
    val endTime by viewModel.waterReminderEndTime.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // elegant slate background
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Opacity,
                        contentDescription = "Water reminder",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hydration Status",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Today: $glasses glasses (every ${intervalMins.toInt()}m, $startTime - $endTime)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = { viewModel.incrementWaterGlassesToday() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log water glass",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Log",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsKeyboardShortcutsPage() {
    val context = LocalContext.current
    val keyboardConnected = remember {
        val config = context.resources.configuration
        config.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY || 
        config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (keyboardConnected) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (keyboardConnected) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Keyboard Status",
                        tint = if (keyboardConnected) Color(0xFF4CAF50) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (keyboardConnected) "Physical Keyboard Connected" else "No Hardware Keyboard Detected",
                        color = if (keyboardConnected) Color(0xFF4CAF50) else Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (keyboardConnected) "All keyboard shortcuts are active and ready to use." else "Connect a hardware/Bluetooth keyboard to use these shortcuts.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Text(
            text = "GLOBAL SCREEN NAVIGATION",
            color = Color(0xFF38B0F2),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        val globalShortcuts = listOf(
            "Ctrl + Shift + S" to "Toggle / Open Settings Screen",
            "Ctrl + Shift + T" to "Go to Timer Screen",
            "Ctrl + Shift + C" to "Go to Calendar Screen",
            "Ctrl + Shift + K" to "Go to Keep Notes Screen",
            "Ctrl + Shift + J" to "Go to Life Journal Screen",
            "Ctrl + Shift + H" to "Go to Habits Tracker Screen",
            "Ctrl + Shift + F" to "Go to Financial Ledger Screen",
            "Ctrl + Shift + D" to "Go to Deepa AI Brain",
            "Ctrl + Shift + N" to "Go to Tasks Engine Screen",
            "Ctrl + Shift + A" to "Go to Analytics Dashboard",
            "Ctrl + Shift + L" to "Go to Live Sphere Screen",
            "Ctrl + Shift + O" to "Go to Focus Groups (Focus Locker)",
            "Ctrl + Shift + X" to "Go to File Explorer Screen",
            "Ctrl + Shift + G" to "Go to Contacts Directory",
            "Ctrl + Shift + R" to "Go to Arena Screen",
            "Ctrl + Shift + P" to "Go to Countdowns & Alerts",
            "Ctrl + Shift + U" to "Go to Health & Fitness Sync"
        )

        globalShortcuts.forEach { (keys, action) ->
            ShortcutItemRow(keys = keys, description = action)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SETTINGS SUBPAGES & ACTIONS (ALT KEY)",
            color = Color(0xFFFFB300),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        val settingsShortcuts = listOf(
            "Alt + G  (or Alt + 1)" to "Open General System Settings",
            "Alt + D  (or Alt + 2)" to "Open Diagnostics & Background",
            "Alt + U  (or Alt + 3)" to "Open App Update Center",
            "Alt + B  (or Alt + 4)" to "Open Deepa AI Brain Settings",
            "Alt + R  (or Alt + 5)" to "Open Backup & Restore Center",
            "Alt + T  (or Alt + 6)" to "Open Timer Configuration",
            "Alt + S  (or Alt + 7)" to "Open Study Groups (Focus Locker)",
            "Alt + K  (or Alt + 8)" to "Open Tasks Engine Settings",
            "Alt + C  (or Alt + 9)" to "Open Calendar Planner Settings",
            "Alt + H  (or Alt + 0)" to "Open Habits Tracker Settings",
            "Alt + W" to "Open Sleep & Wake-up Alarm",
            "Alt + A" to "Open Countdowns & Alerts Settings",
            "Alt + J" to "Open Life Journal Settings",
            "Alt + I" to "Open Contacts Directory Settings",
            "Alt + E" to "Open File Explorer Settings",
            "Alt + F" to "Open Financial Ledger Settings",
            "Alt + L" to "Open Secure App Lock Settings",
            "Alt + M" to "Open Blocks & Screen Limits Settings",
            "Alt + P" to "Open Permissions & API Connections",
            "Alt + O" to "Open Deep Links & Shortcuts Settings",
            "Alt + X" to "Logout securely",
            "Alt + Y" to "De-register and Uninstall confirmation",
            "Alt + Q" to "Return to Settings Main Menu"
        )

        settingsShortcuts.forEach { (keys, action) ->
            ShortcutItemRow(keys = keys, description = action)
        }
    }
}

@Composable
fun ShortcutItemRow(keys: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1F1F24))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = description,
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1C1C24))
                    .border(1.dp, Color(0xFF2C2C35), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = keys,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PersonalVectorMemoryDashboardCard(context: Context) {
    var vectorMemories by remember { mutableStateOf<List<com.example.util.MemorySnippet>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var speakerFilter by remember { mutableStateOf("ALL") } // "ALL", "USER", "AI"
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshMemories() {
        vectorMemories = com.example.util.UserMemoryManager.loadMemories(context)
    }

    LaunchedEffect(Unit) {
        refreshMemories()
    }

    val filteredMemories = remember(vectorMemories, searchQuery, speakerFilter) {
        var list = vectorMemories
        if (speakerFilter == "USER") {
            list = list.filter { it.speaker.equals("User", ignoreCase = true) }
        } else if (speakerFilter == "AI") {
            list = list.filter { it.speaker.equals("AI", ignoreCase = true) }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            val vectorSearchResults = com.example.util.UserMemoryManager.searchMemories(context, q, topK = 50, minSimilarityScore = 0.05f)
            if (vectorSearchResults.isNotEmpty()) {
                vectorSearchResults.map { it.snippet }
            } else {
                list.filter {
                    it.content.contains(q, ignoreCase = true) ||
                    it.speaker.contains(q, ignoreCase = true)
                }
            }
        } else {
            list.sortedByDescending { it.timestamp }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("personal_vector_memory_dashboard_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E12)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2D3A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF8E24AA), Color(0xFF3F51B5))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Vector Memory Icon",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PERSONAL VECTOR MEMORY DASHBOARD",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "128-D Dense Embeddings • ${vectorMemories.size} Snippets Indexed",
                            color = Color(0xFFAB47BC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            com.example.util.UserMemoryManager.autoIngestChatHistory(context)
                            refreshMemories()
                            statusMessage = "Ingested recent chat history into vector store!"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Ingest History",
                            tint = Color(0xFF42A5F5),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            refreshMemories()
                            statusMessage = "Vector index refreshed"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Memory Index",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "View, search, and manually prune long-term vector embeddings used by the AI for semantic chat memory recall and contextual answer grounding.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search vector memory embeddings...", color = Color.Gray, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Memory",
                        tint = Color(0xFFAB47BC),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vector_memory_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFAB47BC),
                    unfocusedBorderColor = Color(0xFF2A2D3A),
                    focusedContainerColor = Color(0xFF13151C),
                    unfocusedContainerColor = Color(0xFF13151C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Speaker Filter Pills & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "USER", "AI").forEach { filterTag ->
                        val isSelected = speakerFilter == filterTag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) Color(0xFF8E24AA) else Color(0xFF1C1E26)
                                )
                                .clickable { speakerFilter = filterTag }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = filterTag,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (vectorMemories.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Prune All Vectors",
                            color = Color(0xFFEF5350),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Memory Snippets List
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13151C), RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "No Vectors Found",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No vector snippets match your search query." else "No vector memory embeddings stored yet.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredMemories.take(15).forEach { snippet ->
                        VectorMemorySnippetRow(
                            snippet = snippet,
                            onDelete = {
                                com.example.util.UserMemoryManager.deleteMemory(context, snippet.id)
                                refreshMemories()
                                statusMessage = "Vector snippet pruned"
                            }
                        )
                    }
                    if (filteredMemories.size > 15) {
                        Text(
                            text = "+ ${filteredMemories.size - 15} more vector memory entries",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Prune All Vector Memories?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to permanently clear all stored local vector embeddings? The AI will lose long-term semantic chat recall.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.util.UserMemoryManager.clearAllMemories(context)
                        refreshMemories()
                        showClearConfirmDialog = false
                        statusMessage = "All vector memory embeddings erased!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Clear All Vectors", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E26)
        )
    }
}

@Composable
fun VectorMemorySnippetRow(
    snippet: com.example.util.MemorySnippet,
    onDelete: () -> Unit
) {
    val dateStr = remember(snippet.timestamp) {
        android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", snippet.timestamp).toString()
    }
    val speakerColor = if (snippet.speaker.equals("User", ignoreCase = true)) Color(0xFF42A5F5) else Color(0xFFAB47BC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF13151C), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF232634), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(speakerColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = snippet.speaker.uppercase(),
                        color = speakerColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = dateStr,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2D3A))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "128-D Vector",
                        color = Color.LightGray,
                        fontSize = 8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = snippet.content,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .testTag("prune_vector_button_${snippet.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Prune Vector Memory",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AiActionLogDashboardCard(context: Context) {
    var actionLogs by remember { mutableStateOf<List<com.example.util.AiActionLogEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("ALL") } // "ALL", "TASKS", "FINANCE", "HEALTH"
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshLogs() {
        actionLogs = com.example.util.AiActionLogManager.loadActionLogs(context)
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val filteredLogs = remember(actionLogs, searchQuery, filterCategory) {
        var list = actionLogs
        if (filterCategory == "TASKS") {
            list = list.filter { it.actionType.contains("TASK") || it.actionType.contains("SCHEDULE") }
        } else if (filterCategory == "FINANCE") {
            list = list.filter { it.actionType.contains("FINANCE") || it.actionType.contains("LEDGER") }
        } else if (filterCategory == "HEALTH") {
            list = list.filter { it.actionType.contains("HEALTH") || it.actionType.contains("WATER") || it.actionType.contains("STEP") }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.details.contains(q, ignoreCase = true) ||
                it.sourceQuery.contains(q, ignoreCase = true) ||
                it.actionType.contains(q, ignoreCase = true)
            }
        } else {
            list.sortedByDescending { it.timestamp }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("ai_action_log_dashboard_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E12)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2D3A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF00C853), Color(0xFF0288D1))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "AI Action Log Icon",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI AUTOMATED ACTION LOG",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Audit Trail • ${actionLogs.size} Recorded Operations",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = {
                        refreshLogs()
                        statusMessage = "Action log updated"
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Action Log",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "History of tasks, schedule updates, finance transactions, health logs, and journal entries automatically executed by the AI based on your natural language chat commands.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter action logs...", color = Color.Gray, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Log",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_log_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color(0xFF2A2D3A),
                    focusedContainerColor = Color(0xFF13151C),
                    unfocusedContainerColor = Color(0xFF13151C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Pills & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "TASKS", "FINANCE", "HEALTH").forEach { categoryTag ->
                        val isSelected = filterCategory == categoryTag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) Color(0xFF00A152) else Color(0xFF1C1E26)
                                )
                                .clickable { filterCategory = categoryTag }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = categoryTag,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (actionLogs.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Clear Log",
                            color = Color(0xFFEF5350),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Log List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13151C), RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "No Action Logs Found",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No action log records match your search." else "No automated AI action logs recorded yet.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredLogs.take(20).forEach { log ->
                        AiActionLogRow(
                            log = log,
                            onDelete = {
                                com.example.util.AiActionLogManager.deleteLog(context, log.id)
                                refreshLogs()
                                statusMessage = "Action log entry removed"
                            }
                        )
                    }
                    if (filteredLogs.size > 20) {
                        Text(
                            text = "+ ${filteredLogs.size - 20} more action log entries",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All AI Action Logs?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to permanently delete the audit history of automated AI actions?",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.util.AiActionLogManager.clearAllLogs(context)
                        refreshLogs()
                        showClearConfirmDialog = false
                        statusMessage = "Action logs cleared!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Clear All Logs", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E26)
        )
    }
}

@Composable
fun AiActionLogRow(
    log: com.example.util.AiActionLogEntry,
    onDelete: () -> Unit
) {
    val dateStr = remember(log.timestamp) {
        android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", log.timestamp).toString()
    }
    val badgeColor = when {
        log.actionType.contains("TASK") -> Color(0xFF42A5F5)
        log.actionType.contains("SCHEDULE") -> Color(0xFFAB47BC)
        log.actionType.contains("FINANCE") -> Color(0xFF66BB6A)
        log.actionType.contains("HEALTH") -> Color(0xFFFFA726)
        else -> Color(0xFF26A69A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF13151C), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF232634), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = log.actionType.replace("_", " "),
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = dateStr,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (log.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.details,
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }

            if (log.sourceQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💬 Command: \"${log.sourceQuery}\"",
                    color = Color(0xFF80CBC4),
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .testTag("delete_action_log_${log.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Action Log",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


