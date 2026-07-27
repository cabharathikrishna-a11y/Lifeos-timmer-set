package com.example.ui.components

import com.example.ui.components.YouTubeLinkParserAndRenderer
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.*
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.api.DeepaAiMode
import com.example.ui.AppViewModel
import com.example.ui.theme.*

// Helper function to parse markdown formatting
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            var currentLine = line
            var isHeader = false
            var headerLevel = 0

            val headerMatch = Regex("""^(#{1,6})\s*(.*)$""").matchEntire(currentLine)
            if (headerMatch != null) {
                isHeader = true
                headerLevel = headerMatch.groupValues[1].length
                currentLine = headerMatch.groupValues[2]
            }

            if (!isHeader) {
                if (currentLine.startsWith("* ")) {
                    currentLine = "• " + currentLine.substring(2)
                } else if (currentLine.startsWith("- ")) {
                    currentLine = "• " + currentLine.substring(2)
                }
            }

            currentLine = currentLine.replace("[ ]", "☐").replace("[x]", "☑").replace("[X]", "☑")

            if (currentLine.trim() == "---" || currentLine.trim() == "----" || currentLine.trim() == "...." || currentLine.trim() == "...") {
                currentLine = "────────────────────────"
            }

            if (isHeader) {
                val scale = when (headerLevel) {
                    1 -> 1.35f
                    2 -> 1.25f
                    3 -> 1.15f
                    else -> 1.05f
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (14 * scale).sp, color = WaterBlue))
            }

            val tripleParts = currentLine.split("***")
            for (bi in tripleParts.indices) {
                val biPart = tripleParts[bi]
                if (bi % 2 == 1) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(biPart)
                    }
                } else {
                    val boldParts = biPart.split("**")
                    for (b in boldParts.indices) {
                        val boldPart = boldParts[b]
                        if (b % 2 == 1) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(boldPart)
                            }
                        } else {
                            val italicParts = boldPart.split("*")
                            for (j in italicParts.indices) {
                                val italicPart = italicParts[j]
                                if (j % 2 == 1) {
                                    withStyle(style = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                        append(italicPart)
                                    }
                                } else {
                                    append(italicPart)
                                }
                            }
                        }
                    }
                }
            }

            if (isHeader) {
                pop()
            }
            if (i < lines.size - 1) {
                append("\n")
            }
        }
    }
}

@Composable
fun SmartChatView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages by viewModel.chatbotMessages.collectAsState()
    val isLoading by viewModel.chatbotLoading.collectAsState()

    val currentMode by viewModel.deepaAiMode.collectAsState()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val attachedMedia by viewModel.attachedMedia.collectAsState()

    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val welcomeGreeting by viewModel.welcomeGreeting.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.generateWelcomeGreeting()
    }

    @OptIn(ExperimentalLayoutApi::class)
    val isKeyboardVisible = WindowInsets.isImeVisible

    var lastScrolledMessageCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val targetIndex = messages.size - 1
            if (lastScrolledMessageCount <= 0) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
            }
            lastScrolledMessageCount = messages.size
        }
    }

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // Media attachment picker launcher
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    viewModel.setAttachedMedia(Pair(mimeType, base64))
                    android.widget.Toast.makeText(context, "Attached media file (${mimeType.take(20)})", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error attaching file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val bottomPadding = if (isKeyboardVisible) 4.dp else 12.dp

    var showMemoryVaultDialog by remember { mutableStateOf(false) }
    var memorySearchQuery by remember { mutableStateOf("") }
    var newMemoryInput by remember { mutableStateOf("") }
    val aiMemoriesList by viewModel.aiMemories.collectAsState()

    if (showMemoryVaultDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryVaultDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = WaterBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1-Year AI History & Memory Vault", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    Text(
                        text = "Deepa AI retains 365-day conversation context and personal user facts across all app modules.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Search Chat History
                    OutlinedTextField(
                        value = memorySearchQuery,
                        onValueChange = { memorySearchQuery = it },
                        placeholder = { Text("Search 1-year chat history...", fontSize = 12.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    if (memorySearchQuery.isNotBlank()) {
                        val searchResults = remember(memorySearchQuery) {
                            com.example.util.AiChatHistoryManager.searchChatHistory(context, memorySearchQuery)
                        }
                        Text("Found ${searchResults.size} history matches:", color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                            items(searchResults.take(15)) { msg ->
                                val sender = if (msg.isUser) "Ranker" else "Deepa AI"
                                val dateStr = android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", msg.timestamp).toString()
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("[$sender] • $dateStr", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(msg.text.take(140), color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("🧠 Personal Memory Facts (${aiMemoriesList.size}):", color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val performAddMemory = {
                                if (newMemoryInput.isNotBlank()) {
                                    viewModel.addAiMemory(newMemoryInput)
                                    newMemoryInput = ""
                                }
                            }
                            OutlinedTextField(
                                value = newMemoryInput,
                                onValueChange = { newMemoryInput = it },
                                placeholder = { Text("Add fact (e.g. Target weight 70kg)...", fontSize = 11.sp, color = Color.Gray) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { performAddMemory() }),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyUp && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                            performAddMemory()
                                            true
                                        } else false
                                    }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { performAddMemory() },
                                modifier = Modifier.size(40.dp).background(WaterBlue, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = Color.White)
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                            items(aiMemoriesList.size) { idx ->
                                val memory = aiMemoriesList[idx]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).background(Color(0xFF282828), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• $memory", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.deleteAiMemory(idx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoryVaultDialog = false }) {
                    Text("Close", color = WaterBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Deepa AI Online Assistant",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "GEMINI CLOUD",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Powered by Google Gemini 3.5 & Pro",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showMemoryVaultDialog = true },
                        modifier = Modifier.testTag("ai_memory_vault_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Memory & History",
                            tint = WaterBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (messages.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.exportChatToGoogleDoc(context, messages) { success, link ->
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Chat exported to Google Doc!", android.widget.Toast.LENGTH_SHORT).show()
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Link: $link", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Export failed: $link", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export_chat_doc_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Chat",
                                tint = WaterBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Export 📄",
                                color = WaterBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Deepa AI Modes Pills Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DeepaAiMode.values().forEach { mode ->
                    val isSelected = mode == currentMode
                    Surface(
                        color = if (isSelected) WaterBlue else SurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable { viewModel.setDeepaAiMode(mode) }
                            .testTag("mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = mode.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Mode Options Drawer (for Image Studio / Veo / Music)
            if (currentMode == DeepaAiMode.IMAGE_STUDIO || currentMode == DeepaAiMode.VEO_VIDEO) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Charcoal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "⚙️ ${currentMode.displayName} Settings",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Aspect Ratio Options
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text("Aspect Ratio: ", color = TextSecondary, fontSize = 10.sp)
                            val ratios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "2:3", "3:2", "21:9")
                            ratios.forEach { ratio ->
                                val isSelected = ratio == selectedAspectRatio
                                Surface(
                                    color = if (isSelected) SurfaceCard else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) WaterBlue else Charcoal),
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clickable { viewModel.setSelectedAspectRatio(ratio) }
                                ) {
                                    Text(
                                        text = ratio,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (currentMode == DeepaAiMode.IMAGE_STUDIO) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Quality/Size: ", color = TextSecondary, fontSize = 10.sp)
                                val resolutions = listOf("1K", "2K", "4K")
                                resolutions.forEach { res ->
                                    val isSelected = res == selectedResolution
                                    Surface(
                                        color = if (isSelected) SurfaceCard else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) WaterBlue else Charcoal),
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable { viewModel.setSelectedResolution(res) }
                                    ) {
                                        Text(
                                            text = res,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attached Media Preview Chip
            if (attachedMedia != null) {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Media attached (${attachedMedia?.first?.substringAfter("/")})",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove media",
                            tint = AlertRed,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.setAttachedMedia(null) }
                        )
                    }
                }
            }

            // Conversation Thread
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = welcomeGreeting ?: "Hi, welcome to Deepa Online AI!",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ask questions, generate images & videos, research live with Search/Maps grounding, or talk with Voice Live AI.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    val isUser = msg.isUser
                    val text = msg.text
                    val alignment = if (isUser) Alignment.End else Alignment.Start

                    Column(
                        modifier = Modifier.animateItem().fillMaxWidth(),
                        horizontalAlignment = alignment
                    ) {
                        val bubbleShape = RoundedCornerShape(20.dp)
                        val bubbleBackground = if (isUser) Color(0xFF2E240D) else SurfaceCard
                        val bubbleBorderColor = if (isUser) WaterBlue.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)

                        Box(
                            modifier = Modifier
                                .clip(bubbleShape)
                                .background(bubbleBackground)
                                .border(width = 1.dp, color = bubbleBorderColor, shape = bubbleShape)
                                .padding(14.dp)
                                .widthIn(max = 780.dp)
                        ) {
                            Column {
                                Text(
                                    text = parseMarkdown(text),
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                YouTubeLinkParserAndRenderer(text = text)
                                InstagramLinkParserAndRenderer(text = text)

                                if (msg.base64Image != null) {
                                    val bitmap = remember(msg.base64Image) {
                                        try {
                                            val decodedString = Base64.decode(msg.base64Image, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (bitmap != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "AI Generated Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 320.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val badgeInfo = if (msg.modelUsed != null) " (${msg.modelUsed})" else ""
                        Text(
                            text = (if (isUser) "You" else "Deepa AI Cloud") + badgeInfo,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                color = WaterBlue,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Deepa AI reasoning with Gemini (${currentMode.displayName})...",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attach Media Button
                IconButton(
                    onClick = { attachmentPicker.launch("*/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard)
                        .border(width = 1.dp, color = WaterBlue.copy(alpha = 0.3f), shape = CircleShape)
                        .testTag("attach_media_btn")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach media", tint = WaterBlue)
                }

                val performAiSend = {
                    if (inputMessage.trim().isNotEmpty() || attachedMedia != null) {
                        viewModel.sendMessageToAI(inputMessage)
                        inputMessage = ""
                    }
                }

                com.example.util.RichContentInputField(
                    value = inputMessage,
                    onValueChange = {
                        inputMessage = it
                        viewModel.detectAndRecognizeUrlInText(it)
                    },
                    hint = when (currentMode) {
                        DeepaAiMode.IMAGE_STUDIO -> "Describe image to create..."
                        DeepaAiMode.VEO_VIDEO -> "Describe video scene for Veo..."
                        DeepaAiMode.LYRIA_MUSIC -> "Describe music genre or mood for Lyria..."
                        DeepaAiMode.GOOGLE_SEARCH -> "Ask a live web search query..."
                        DeepaAiMode.GOOGLE_MAPS -> "Ask for locations or places..."
                        DeepaAiMode.HIGH_THINKING -> "Ask complex problem for deep reasoning..."
                        else -> "Chat, paste image, or drop content for Deepa AI..."
                    },
                    textColor = TextPrimary,
                    backgroundColor = SurfaceCard,
                    borderColor = WaterBlue.copy(alpha = 0.3f),
                    onSend = { performAiSend() },
                    onRichContentReceived = { uris, text, source ->
                        if (!text.isNullOrBlank()) {
                            inputMessage = if (inputMessage.isBlank()) text else "$inputMessage $text"
                        }
                        for (uri in uris) {
                            try {
                                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                val inputStream = context.contentResolver.openInputStream(uri)
                                if (inputStream != null) {
                                    val destFile = java.io.File(context.cacheDir, "rich_ai_attachment_${System.currentTimeMillis()}")
                                    destFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                    viewModel.setAttachedMedia(Pair(mimeType, destFile.absolutePath))
                                    android.widget.Toast.makeText(context, "Attached rich media to Deepa AI", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input")
                )

                IconButton(
                    onClick = { performAiSend() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WaterBlue)
                        .testTag("send_chat_btn")
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}
