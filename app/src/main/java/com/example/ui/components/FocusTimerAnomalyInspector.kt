package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.AnomalyReport
import com.example.util.AnomalyType
import com.example.util.FixAction
import com.example.util.FocusTimerAnomalyChecker
import kotlinx.coroutines.launch

@Composable
fun FocusTimerAnomalyCard(
    currentUsername: String,
    onRunFullReconciliation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeAnomaly by FocusTimerAnomalyChecker.lastDetectedAnomaly.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var currentReport by remember { mutableStateOf<AnomalyReport?>(null) }
    var actionResultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeAnomaly) {
        val isFocusing = com.example.util.FocusTimerManager.isTimerRunning.value || com.example.util.FocusTimerManager.isStopwatchActive.value
        if (!isFocusing && activeAnomaly != null && activeAnomaly?.anomalyType != AnomalyType.SYSTEM_HEALTHY) {
            currentReport = activeAnomaly
            showDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF16161E), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF2B2B3D), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Focus Timer Anomaly Inspector",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            TextButton(
                onClick = {
                    isChecking = true
                    actionResultMessage = null
                    scope.launch {
                        val report = FocusTimerAnomalyChecker.runDiagnosticCheck(context, isManualRequest = true)
                        currentReport = report
                        isChecking = false
                        showDialog = true
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color(0xFF64B5F6)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Checking...", fontSize = 11.sp, color = Color(0xFF64B5F6))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run Diagnostic", fontSize = 11.sp, color = Color(0xFF64B5F6))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Detects sudden increases or drops in focus time, checks multi-device adoption, and helps resolve session overlaps.",
            fontSize = 10.sp,
            color = Color(0xFFA0A0B0)
        )

        // Focus Drift Detector Status
        val isDriftDetected by com.example.util.FocusDriftDetector.isDriftDetected.collectAsStateWithLifecycle()
        val currentDriftSecs by com.example.util.FocusDriftDetector.currentDriftSeconds.collectAsStateWithLifecycle()
        val lastDriftReport by com.example.util.FocusDriftDetector.lastDriftReport.collectAsStateWithLifecycle()
        val lastSyncStatus by com.example.util.FocusDriftDetector.lastSyncStatus.collectAsStateWithLifecycle()

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDriftDetected) Color(0xFF2C1618) else Color(0xFF14241B),
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.dp,
                    if (isDriftDetected) Color(0xFFFF6B6B).copy(alpha = 0.6f) else Color(0xFF81C784).copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDriftDetected) "⚠️ Focus Drift Flagged (> 5s)" else "⏱️ Focus Drift Detector: In Sync",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDriftDetected) Color(0xFFFF6B6B) else Color(0xFF81C784)
                )
                Text(
                    text = if (isDriftDetected) {
                        "Heartbeat discrepancy: ${String.format(java.util.Locale.US, "%.2f", currentDriftSecs)}s (Auto-Resync: $lastSyncStatus)"
                    } else {
                        "Client heartbeat vs Firebase RTDB drift: ${String.format(java.util.Locale.US, "%.2f", currentDriftSecs)}s (Sync: $lastSyncStatus)"
                    },
                    fontSize = 9.5.sp,
                    color = Color(0xFFD0D0E0)
                )
            }

            Row {
                TextButton(
                    onClick = {
                        scope.launch {
                            val activeEmail = com.example.api.DynamicCommandManager.activeEmail
                            val success = com.example.util.FocusDriftDetector.resyncSession(context, activeEmail)
                            actionResultMessage = if (success) "⚡ Session resynced to Firebase RTDB!" else "❌ Resync failed."
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Resync", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                }

                if (isDriftDetected) {
                    TextButton(
                        onClick = {
                            com.example.util.FocusDriftDetector.recalibrateAndClearDrift(context)
                            actionResultMessage = "✅ Drift recalibrated and cleared."
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Recalibrate", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                    }
                }
            }
        }

        activeAnomaly?.let { report ->
            if (report.anomalyType != AnomalyType.SYSTEM_HEALTHY) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF281E12), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable {
                            currentReport = report
                            showDialog = true
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                        Text(
                            text = report.reasonExplanation,
                            fontSize = 9.5.sp,
                            color = Color(0xFFE0E0E0),
                            maxLines = 2
                        )
                    }
                    Text(
                        text = "Inspect",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D)
                    )
                }
            }
        }

        actionResultMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                fontSize = 10.sp,
                color = if (msg.startsWith("❌")) Color(0xFFFF6B6B) else Color(0xFF81C784),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101C12), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            )
        }
    }

    if (showDialog && currentReport != null) {
        val report = currentReport!!
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1C1C26),
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color) = when (report.anomalyType) {
                        AnomalyType.SYSTEM_HEALTHY -> Icons.Default.CheckCircle to Color(0xFF81C784)
                        AnomalyType.CROSS_DEVICE_ADOPTION -> Icons.Default.Info to Color(0xFF64B5F6)
                        else -> Icons.Default.Warning to Color(0xFFFFB74D)
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Text(
                        text = report.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = report.reasonExplanation,
                        fontSize = 12.sp,
                        color = Color(0xFFE0E0EC)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF12121A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "TECHNICAL DIAGNOSTICS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8888A0)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = report.technicalDetails,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB0B0C8)
                            )
                        }
                    }

                    if (report.recommendedFixes.isNotEmpty()) {
                        Text(
                            text = "SELF-SERVICE RESOLUTION ACTIONS:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8888A0)
                        )

                        report.recommendedFixes.forEach { fix ->
                            val buttonText = when (fix) {
                                FixAction.RUN_DEEP_RECONCILIATION -> "🔧 Sync & Recalibrate Focus"
                                FixAction.CLEAR_CROSS_DEVICE_ADOPTION -> "🧹 Reset Cross-Device Adoption Cache"
                                FixAction.RECALCULATE_LOCAL_VAULT -> "🔄 Re-index Local History Vault"
                                FixAction.RESYNC_SESSION_TO_RTDB -> "⚡ Resync Session to RTDB"
                                FixAction.DISMISS -> "❌ Dismiss Alert"
                            }

                            Button(
                                onClick = {
                                    if (fix == FixAction.RUN_DEEP_RECONCILIATION) {
                                        onRunFullReconciliation()
                                    }
                                    FocusTimerAnomalyChecker.applyFixAction(
                                        action = fix,
                                        context = context,
                                        username = currentUsername,
                                        scope = scope
                                    ) { result ->
                                        actionResultMessage = result
                                        showDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (fix == FixAction.DISMISS) Color(0xFF282836) else Color(0xFF2E3B52),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Text(buttonText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    FocusTimerAnomalyChecker.clearAndDismissAnomaly(context)
                    showDialog = false
                }) {
                    Text("Close", color = Color(0xFF64B5F6))
                }
            }
        )
    }
}
