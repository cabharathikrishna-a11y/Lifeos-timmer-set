package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.ui.AppViewModel
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.WaterBlue
import com.example.util.UrgentNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationStudioView(
    viewModel: AppViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(UrgentNotificationHelper.checkNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    // Form inputs for testing
    var highPriorityTitle by remember { mutableStateOf("URGENT: TIME-SENSITIVE EVENT") }
    var highPriorityText by remember { mutableStateOf("Immediate attention required for active alarm or task deadline!") }
    var highPriorityCategory by remember { mutableStateOf(NotificationCompat.CATEGORY_RECOMMENDATION) }

    var customLayoutTitle by remember { mutableStateOf("Custom Decorated Header") }
    var customLayoutBody by remember { mutableStateOf("Expanded custom layout leveraging DecoratedCustomViewStyle with RemoteViews.") }

    var ongoingTitle by remember { mutableStateOf("Active Focus Timer & Alarm") }
    var ongoingBody by remember { mutableStateOf("Session in progress • Elapsed 25m 00s") }
    var ongoingSubtext by remember { mutableStateOf("Live Update") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C14))
            .statusBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Notification Studio",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Urgent Alerts, Channels & Custom Decorated Views",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = WaterBlue.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Notifications Studio",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Urgent Notification Architecture",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Demonstrates POST_NOTIFICATIONS runtime permissions, system Notification Channels, high-priority CATEGORY_RECOMMENDATION/ALARM, ongoing Live Updates, and DecoratedCustomViewStyle RemoteViews.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Section 1: Manage Notification Permissions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
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
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permission Status",
                                tint = if (hasNotificationPermission) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Text(
                                "Notification Permission",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (hasNotificationPermission) Color(0xFF065F46) else Color(0xFF78350F)
                        ) {
                            Text(
                                text = if (hasNotificationPermission) "GRANTED" else "PERMISSION REQUIRED",
                                color = if (hasNotificationPermission) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "Android 13 (API 33) requires explicit user approval for POST_NOTIFICATIONS permission before posting notifications."
                        } else {
                            "Device running Android 12 or earlier. System notification status check active."
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                hasNotificationPermission = UrgentNotificationHelper.checkNotificationPermission(context)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasNotificationPermission) Color(0xFF334155) else WaterBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (hasNotificationPermission) Icons.Default.Check else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (hasNotificationPermission) Color.White else Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasNotificationPermission) "Permission Verified" else "Request Notification Permission",
                            color = if (hasNotificationPermission) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 2: Configured Channels Overview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Configured Notification Channels",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Created in Application.onCreate() for user preferences in System App Info settings:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    ChannelItemRow("1. High Priority Notifications", "IMPORTANCE_HIGH • High priority alerts & recommendations")
                    ChannelItemRow("2. Urgent Time-Sensitive Alerts", "IMPORTANCE_HIGH • Ongoing alarms, phone calls & live updates")
                    ChannelItemRow("3. Custom Styled Notifications", "IMPORTANCE_HIGH • RemoteViews DecoratedCustomViewStyle")
                }
            }

            // Section 3: High Priority Notification Tester
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
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
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = null,
                            tint = Color(0xFFEF4444)
                        )
                        Text(
                            "High-Priority Notification",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedTextField(
                        value = highPriorityTitle,
                        onValueChange = { highPriorityTitle = it },
                        label = { Text("Notification Title") },
                        modifier = Modifier.fillMaxWidth().testTag("high_priority_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = highPriorityText,
                        onValueChange = { highPriorityText = it },
                        label = { Text("Notification Content") },
                        modifier = Modifier.fillMaxWidth().testTag("high_priority_text_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    Button(
                        onClick = {
                            UrgentNotificationHelper.showHighPriorityNotification(
                                context = context,
                                title = highPriorityTitle,
                                content = highPriorityText,
                                category = highPriorityCategory,
                                notificationId = 1001
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("show_high_priority_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Show High Priority Notification",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 4: Decorated Custom View Style Layouts
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
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
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6)
                        )
                        Text(
                            "Custom Decorated Notification Layout",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        "Uses NotificationCompat.DecoratedCustomViewStyle with RemoteViews (notification_small.xml & notification_large.xml) for system-decorated title and body.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = customLayoutTitle,
                        onValueChange = { customLayoutTitle = it },
                        label = { Text("Custom Layout Title") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customLayoutBody,
                        onValueChange = { customLayoutBody = it },
                        label = { Text("Custom Layout Body") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_body_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    Button(
                        onClick = {
                            UrgentNotificationHelper.showCustomDecoratedNotification(
                                context = context,
                                title = customLayoutTitle,
                                body = customLayoutBody,
                                notificationId = 1002
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("show_custom_layout_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Show Custom Decorated Notification",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 5: Ongoing Time-Sensitive / Live Update Alert
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
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
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4)
                        )
                        Text(
                            "Ongoing Time-Sensitive & Live Update",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        "Ongoing notifications linked with time-critical events or foreground services, complete with action buttons.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = ongoingTitle,
                        onValueChange = { ongoingTitle = it },
                        label = { Text("Ongoing Title") },
                        modifier = Modifier.fillMaxWidth().testTag("ongoing_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = ongoingBody,
                        onValueChange = { ongoingBody = it },
                        label = { Text("Ongoing Detail") },
                        modifier = Modifier.fillMaxWidth().testTag("ongoing_body_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                UrgentNotificationHelper.showOngoingTimeSensitiveNotification(
                                    context = context,
                                    title = ongoingTitle,
                                    body = ongoingBody,
                                    subtext = ongoingSubtext,
                                    notificationId = 1003
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_ongoing_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Start Ongoing",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                UrgentNotificationHelper.cancelNotification(context, 1003)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dismiss_ongoing_button"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Dismiss",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChannelItemRow(
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}
