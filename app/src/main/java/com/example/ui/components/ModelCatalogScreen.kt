package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.util.DeviceSpecs
import com.example.util.DeviceSpecsManager
import com.example.util.ModelDownloadManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(
    viewModel: AppViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var deviceSpecs by remember { mutableStateOf(DeviceSpecsManager.getDeviceSpecs(context)) }
    val lowRamConfig = remember(deviceSpecs) { DeviceSpecsManager.getEngineConfigForSpecs(deviceSpecs) }

    val downloadState by ModelDownloadManager.downloadState.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()

    var downloadedModelIds by remember {
        mutableStateOf(
            ModelRepository.catalog.filter { ModelDownloadManager.isModelDownloaded(context, it) }.map { it.id }.toSet()
        )
    }

    // Refresh downloaded list
    fun refreshDownloadedList() {
        downloadedModelIds = ModelRepository.catalog.filter { ModelDownloadManager.isModelDownloaded(context, it) }.map { it.id }.toSet()
        deviceSpecs = DeviceSpecsManager.getDeviceSpecs(context)
    }

    var modelForWarningDialog by remember { mutableStateOf<LocalAiModel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(16.dp)
    ) {
        // --- 1. GEMINI NANO & AICORE SYSTEM SERVICE HEADER ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "GEMINI NANO • AICORE SYSTEM SERVICE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE ON-DEVICE",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "On-device AI powered by Google's Gemini Nano foundation model & ML Kit GenAI APIs. Zero server calls, zero network latency, and Private Compute Core data protection.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛡️ Privacy Safeguard",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Private Compute Core",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Network Usage",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "0 KB (100% Offline)",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧠 Model Architecture",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Gemini Nano + NPU",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 2. ML KIT GENAI APIS PLAYGROUND ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF27272A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "ML KIT GENAI APIS SUITE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var testInputText by remember { mutableStateOf("Life OS helps you organize tasks, manage focus sessions, track finances, and study intelligently with zero privacy compromises.") }
                var testOutputText by remember { mutableStateOf("Select an ML Kit GenAI API action below to run inference on-device using Gemini Nano:") }
                var isProcessing by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    label = { Text("Playground Input Text", fontSize = 10.sp, color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons for ML Kit GenAI Suite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                testOutputText = com.example.util.GeminiNanoManager.generatePrompt(context, testInputText)
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("⚡ Prompt", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                testOutputText = com.example.util.GeminiNanoManager.summarize(testInputText)
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("📝 Summarize", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                testOutputText = com.example.util.GeminiNanoManager.proofread(testInputText)
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("✏️ Proofread", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                testOutputText = com.example.util.GeminiNanoManager.rewrite(testInputText, com.example.util.GeminiNanoManager.RewriteTone.PROFESSIONAL)
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("🎨 Rewrite", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color(0xFF090A0F),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 1.5.dp
                                )
                            }
                            Text(
                                text = "GEMINI NANO ON-DEVICE RESULT:",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = testOutputText,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // --- 3. ANDROID 16 APPFUNCTIONS, COMPUTER CONTROL & ADK AGENTS ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
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
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "APPFUNCTIONS & ADK MCP AGENTS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = Color(0xFF312E81),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ANDROID 16 MCP",
                            color = Color(0xFFA5B4FC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Android 16 AppFunctions & Agent Development Kit (ADK) expose Life OS capabilities as local, zero-latency MCP tools for Gemini & system assistants.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Registered AppFunctions Tools
                Text(
                    text = "REGISTERED ON-DEVICE APPFUNCTION TOOLS:",
                    color = Color(0xFF818CF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                com.example.util.AppFunctionsMcpManager.registeredAppFunctions.forEach { tool ->
                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚡ ${tool.name}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tool.description,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "ACTIVE",
                                color = Color(0xFF34D399),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ADK Agent Execution Test Prompt
                var adkPrompt by remember { mutableStateOf("Remind me to review my budget at 6 PM today") }
                var adkResult by remember { mutableStateOf("Type a prompt or task request to test the ADK Agent Runner & AppFunction dispatch:") }
                var isAdkRunning by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = adkPrompt,
                    onValueChange = { adkPrompt = it },
                    label = { Text("ADK Agent Test Instruction", fontSize = 10.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isAdkRunning = true
                            adkResult = com.example.util.AppFunctionsMcpManager.runAdkAgent(context, adkPrompt)
                            isAdkRunning = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAdkRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("🤖 Run ADK Agent & Dispatch AppFunction", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFF090A0F),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF312E81)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ADK AGENT / COMPUTER CONTROL OUTPUT:",
                            color = Color(0xFFA5B4FC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = adkResult,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Text(
            text = "ON-DEVICE AI HARDWARE DIAGNOSTICS",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // --- 2. CATALOG MODEL LIST ---
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ModelRepository.catalog, key = { it.id }) { model ->
                val compatibility = remember(model, deviceSpecs) { model.evaluateCompatibility(deviceSpecs) }
                val isDownloaded = downloadedModelIds.contains(model.id)
                val isActive = activeModelId == model.id
                val isSelected = selectedModelId == model.id
                val isDownloadingThis = downloadState.isDownloading && downloadState.modelId == model.id

                ModelCatalogItemCard(
                    model = model,
                    compatibility = compatibility,
                    isDownloaded = isDownloaded,
                    isActive = isActive,
                    isSelected = isSelected,
                    isDownloadingThis = isDownloadingThis,
                    downloadState = downloadState,
                    onDownloadClick = {
                        if (compatibility.status == CompatibilityStatus.HARDWARE_UNSUPPORTED) {
                            modelForWarningDialog = model
                        } else {
                            if (compatibility.showsCautionToast) {
                                Toast.makeText(context, "💡 Tip: Close background apps for faster generation speed.", Toast.LENGTH_LONG).show()
                            }
                            ModelDownloadManager.startDownload(
                                context = context,
                                model = model,
                                coroutineScope = coroutineScope,
                                onComplete = { success, err ->
                                    refreshDownloadedList()
                                    if (success) {
                                        viewModel.selectModel(model.id)
                                        Toast.makeText(context, "✅ ${model.name} downloaded & ready!", Toast.LENGTH_SHORT).show()
                                    } else if (err != null) {
                                        Toast.makeText(context, "Download failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    },
                    onCancelDownload = {
                        ModelDownloadManager.cancelDownload()
                    },
                    onSelectAndRun = {
                        viewModel.selectModel(model.id)
                        Toast.makeText(context, "Activated ${model.name}", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteModel = {
                        ModelDownloadManager.deleteModel(context, model)
                        viewModel.deleteModel(model.id)
                        refreshDownloadedList()
                        Toast.makeText(context, "Deleted ${model.name} model files.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // --- 3. RISK ACKNOWLEDGMENT DIALOG ---
    modelForWarningDialog?.let { model ->
        UnsupportedModelDialog(
            model = model,
            deviceSpecs = deviceSpecs,
            onDismiss = { modelForWarningDialog = null },
            onConfirmDownloadAnyway = {
                val m = model
                modelForWarningDialog = null
                Toast.makeText(context, "Starting download anyway for ${m.name}...", Toast.LENGTH_SHORT).show()
                ModelDownloadManager.startDownload(
                    context = context,
                    model = m,
                    coroutineScope = coroutineScope,
                    onComplete = { success, err ->
                        refreshDownloadedList()
                        if (success) {
                            viewModel.selectModel(m.id)
                            Toast.makeText(context, "✅ Downloaded ${m.name}.", Toast.LENGTH_SHORT).show()
                        } else if (err != null) {
                            Toast.makeText(context, "Download failed: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        )
    }
}

@Composable
fun ModelCatalogItemCard(
    model: LocalAiModel,
    compatibility: CompatibilityResult,
    isDownloaded: Boolean,
    isActive: Boolean,
    isSelected: Boolean,
    isDownloadingThis: Boolean,
    downloadState: com.example.util.DownloadProgressState,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onSelectAndRun: () -> Unit,
    onDeleteModel: () -> Unit
) {
    val borderColor = when {
        isActive -> Color(0xFF34A853)
        isSelected -> Color(0xFF4285F4)
        compatibility.status == CompatibilityStatus.STORAGE_LOW -> Color(0xFF5C1D1D)
        compatibility.status == CompatibilityStatus.HARDWARE_UNSUPPORTED -> Color(0xFFEA8600)
        else -> Color(0xFF222226)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${model.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF121822) else Color(0xFF0D0D10)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Category & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1C1C24),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = model.category,
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                CompatibilityBadge(compatibility = compatibility)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Subtitle Specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "File: ${model.fileName} • Download Size: ${model.sizeGb} GB",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                if (isActive) {
                    Surface(
                        color = Color(0xFF1B5E20),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = Color(0xFF81C784),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = model.description,
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Hardware Requirement Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🧠 Min RAM: ${model.minRamGb} GB (Rec: ${model.recRamGb} GB)",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Text(
                    text = "💾 Free Storage: ${model.minStorageGb} GB",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download Progress Bar or Action Buttons
            if (isDownloadingThis) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = downloadState.statusText,
                            color = Color(0xFFFBBC05),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(downloadState.progress * 100).toInt()}% (${"%.1f".format(downloadState.speedMBps)} MB/s)",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFBBC05),
                        trackColor = Color(0xFF222226)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel Download", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloaded) {
                        if (!isActive) {
                            Button(
                                onClick = onSelectAndRun,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Select & Run", color = Color.White, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = onDeleteModel,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Model",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        when (compatibility.status) {
                            CompatibilityStatus.STORAGE_LOW -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = Color(0xFF321B1B),
                                        disabledContentColor = Color(0xFFFF8A8A)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Need ${String.format("%.1f", compatibility.storageDeficitGb)} GB More Storage",
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            CompatibilityStatus.HARDWARE_UNSUPPORTED -> {
                                OutlinedButton(
                                    onClick = onDownloadClick,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFFFB74D)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFEA8600)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Download Anyway", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            else -> {
                                Button(
                                    onClick = onDownloadClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Text("Download & Activate", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
fun CompatibilityBadge(compatibility: CompatibilityResult) {
    val (bgColor, textColor) = when (compatibility.status) {
        CompatibilityStatus.OPTIMAL -> Color(0xFF1B5E20) to Color(0xFF81C784)
        CompatibilityStatus.COMPATIBLE -> Color(0xFF1E3A5F) to Color(0xFF90CAF9)
        CompatibilityStatus.COMPATIBLE_WITH_CAUTION -> Color(0xFF3D3212) to Color(0xFFFFD54F)
        CompatibilityStatus.STORAGE_LOW -> Color(0xFF4A1818) to Color(0xFFFF8A8A)
        CompatibilityStatus.HARDWARE_UNSUPPORTED -> Color(0xFF422108) to Color(0xFFFFB74D)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = compatibility.badgeLabel,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun UnsupportedModelDialog(
    model: LocalAiModel,
    deviceSpecs: DeviceSpecs,
    onDismiss: () -> Unit,
    onConfirmDownloadAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Severe Performance Warning",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your device has ${deviceSpecs.totalPhysicalRamGb} GB physical memory, which is less than ${model.name} requires (${model.minRamGb} GB RAM minimum).",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Surface(
                    color = Color(0xFF2C1C16),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Running this model will likely cause:",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Application out-of-memory (OOM) crashes", color = Color.White, fontSize = 11.sp)
                        Text("• Extreme thermal throttling and overheating", color = Color.White, fontSize = 11.sp)
                        Text("• Very slow generation speeds (< 5 words/sec)", color = Color.White, fontSize = 11.sp)
                        Text("• Rapid battery drain", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirmDownloadAnyway
            ) {
                Text("I Understand the Risks, Download Anyway", color = Color(0xFFFFB74D), fontSize = 11.sp)
            }
        },
        containerColor = Color(0xFF1E1E24)
    )
}
