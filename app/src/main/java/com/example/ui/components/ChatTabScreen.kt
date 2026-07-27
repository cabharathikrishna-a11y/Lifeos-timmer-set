package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.ChatMessage
import com.example.ui.AppViewModel
import com.example.ui.chat.ChatDateRangeFilter
import com.example.ui.chat.ChatOption
import com.example.ui.chat.ChatOptionType
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.*
import com.example.util.VideoPlayerDialog
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class VoiceRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File? {
        try {
            outputFile = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            outputFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            outputFile?.absolutePath
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }
}

fun downloadMediaFile(context: Context, mediaUrlOrPath: String, fileName: String) {
    try {
        if (mediaUrlOrPath.startsWith("http://") || mediaUrlOrPath.startsWith("https://")) {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(mediaUrlOrPath))
                .setTitle(fileName)
                .setDescription("Downloading media file from Google Drive")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } else {
            val srcFile = File(mediaUrlOrPath)
            if (srcFile.exists()) {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                srcFile.copyTo(destFile, overwrite = true)
                Toast.makeText(context, "Saved to Downloads: ${destFile.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Media saved to device storage", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Saved to device storage: $fileName", Toast.LENGTH_SHORT).show()
    }
}

fun shareMediaContent(context: Context, textOrUrl: String, mimeType: String = "text/plain") {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, textOrUrl)
            if (!textOrUrl.startsWith("http") && File(textOrUrl).exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    File(textOrUrl)
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share media", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatTabScreen(
    appViewModel: AppViewModel? = null,
    chatViewModel: ChatViewModel = remember { ChatViewModel() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val messages by chatViewModel.messages.collectAsState()
    val filteredMessages by chatViewModel.filteredMessages.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val selectedSenderFilter by chatViewModel.selectedSenderFilter.collectAsState()
    val selectedDateRangeFilter by chatViewModel.selectedDateRangeFilter.collectAsState()
    val isSearchActive by chatViewModel.isSearchActive.collectAsState()
    val availableSenders by chatViewModel.availableSenders.collectAsState()
    val editingMessage by chatViewModel.editingMessage.collectAsState()
    val pinnedMessages by chatViewModel.pinnedMessages.collectAsState()
    val onlineUsers by chatViewModel.onlineUsers.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()
    val replyingToMessage by chatViewModel.replyingToMessage.collectAsState()

    val isMultiSelectActive by chatViewModel.isMultiSelectActive.collectAsState()
    val selectedMessageIds by chatViewModel.selectedMessageIds.collectAsState()

    val selectedChatOption by chatViewModel.selectedChatOption.collectAsState()
    val showChatOptionsSheet by chatViewModel.showChatOptionsSheet.collectAsState()
    val studyGroups by chatViewModel.studyGroups.collectAsState()
    val directMessageMembers by chatViewModel.directMessageMembers.collectAsState()

    val isLoading by chatViewModel.isLoading.collectAsState()
    val isLoadingMore by chatViewModel.isLoadingMore.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val currentUserId = chatViewModel.currentUserId

    val unreadDmFlow = remember(appViewModel) { appViewModel?.unreadDmCount ?: kotlinx.coroutines.flow.MutableStateFlow(0) }
    val unreadDmCount by unreadDmFlow.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "dm_blink_transition")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dm_blink_alpha"
    )

    var textInput by remember { mutableStateOf("") }
    var reactionPickerMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedInfoMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showPinnedMessagesPage by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val voiceRecorderHelper = remember(context) { VoiceRecorderHelper(context) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        chatViewModel.markAllUnreadIncomingMessagesAsRead()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatViewModel.markAllUnreadIncomingMessagesAsRead()
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
            }
        }
    }

    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            textInput = editingMessage?.text ?: ""
        }
    }

    // Photo Capture Activity Launcher
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                chatViewModel.onSendMediaMessage("photo", photoFile.absolutePath)
                Toast.makeText(context, "Photo captured & uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to process captured photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Media File Picker Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "file"
                val mediaType = when {
                    mime.startsWith("image/") -> "photo"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "voice"
                    else -> "file"
                }
                val inputStream = context.contentResolver.openInputStream(uri)
                val destFile = File(context.cacheDir, "picker_media_${System.currentTimeMillis()}")
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    chatViewModel.onSendMediaMessage(mediaType, destFile.absolutePath)
                    Toast.makeText(context, "Media attached & uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to process picked file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val headerBg = Charcoal
    val screenBg = DeepSlate
    val outgoingBubbleColor = Color(0xFF2E240D)
    val incomingBubbleColor = SurfaceCard
    val textColorOutgoing = Color(0xFFFFF8E1)
    val textColorIncoming = TextPrimary
    val sendButtonBg = WaterBlue

    val totalItems = filteredMessages.size
    val lastVisibleIndex = remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(lastVisibleIndex.value, firstVisibleIndex.value, totalItems) {
        if (totalItems >= 50 && !isLoadingMore && !isLoading && searchQuery.isEmpty()) {
            val remainingEnd = totalItems - 1 - lastVisibleIndex.value
            val remainingStart = firstVisibleIndex.value
            if (remainingEnd <= 50 || remainingStart <= 50) {
                chatViewModel.loadMoreMessages()
            }
        }
    }

    var lastScrolledOptionId by remember { mutableStateOf<String?>(null) }
    var lastScrolledMessageCount by remember { mutableIntStateOf(-1) }

    @OptIn(ExperimentalLayoutApi::class)
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(selectedChatOption.id) {
        if (filteredMessages.isNotEmpty()) {
            listState.scrollToItem(filteredMessages.size - 1)
            lastScrolledOptionId = selectedChatOption.id
            lastScrolledMessageCount = filteredMessages.size
        }
    }

    LaunchedEffect(filteredMessages.size, isSearchActive, searchQuery) {
        if (filteredMessages.isNotEmpty() && !isSearchActive && searchQuery.isEmpty()) {
            val targetIndex = filteredMessages.size - 1
            if (lastScrolledOptionId != selectedChatOption.id || lastScrolledMessageCount <= 0) {
                listState.scrollToItem(targetIndex)
                lastScrolledOptionId = selectedChatOption.id
            } else {
                listState.scrollToItem(targetIndex)
            }
            lastScrolledMessageCount = filteredMessages.size
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && filteredMessages.isNotEmpty() && !isSearchActive && searchQuery.isEmpty()) {
            listState.scrollToItem(filteredMessages.size - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            chatViewModel.clearError()
        }
    }

    val isFilterActive = searchQuery.isNotEmpty() || selectedSenderFilter != null || selectedDateRangeFilter != ChatDateRangeFilter.ALL

    Scaffold(
        modifier = modifier.testTag("chat_tab_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isMultiSelectActive) {
                // Multi-Select Top Bar Header
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B),
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = { chatViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Multi-Select", tint = Color.White)
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedMessageIds.size} Selected",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    actions = {
                        // Copy Selected
                        IconButton(onClick = {
                            val text = chatViewModel.getSelectedMessagesText(context)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copied selected messages", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected", tint = Color.White)
                        }
                        // Delete Selected
                        IconButton(onClick = {
                            chatViewModel.deleteSelectedMessages()
                            Toast.makeText(context, "Deleted selected messages", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFFF5252))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerBg,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(
                            modifier = Modifier
                                .clickable { chatViewModel.openChatOptionsSheet() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UserAvatar(
                                emojiOrBase64 = selectedChatOption.iconEmoji,
                                size = 38.dp,
                                fallback = selectedChatOption.name.take(1).uppercase()
                            )
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedChatOption.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch Chat Options",
                                        tint = WaterBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (selectedChatOption.type == ChatOptionType.DIRECT_MESSAGE) Color(0xFFAB47BC) else SuccessGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            selectedChatOption.type == ChatOptionType.DIRECT_MESSAGE -> "Direct Message 🔒 • Private 1-on-1"
                                            typingUsers.isNotEmpty() -> "${typingUsers.joinToString(", ") { it.replace("_", " ") }} typing..."
                                            else -> "${selectedChatOption.memberCount} Members • Active Group"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                color = if (unreadDmCount > 0) Color(0xFFE040FB).copy(alpha = 0.25f * blinkAlpha + 0.15f) else WaterBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = if (unreadDmCount > 0) BorderStroke(1.5.dp, Color(0xFFE040FB).copy(alpha = blinkAlpha)) else null,
                                modifier = Modifier.clickable { chatViewModel.openChatOptionsSheet() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch Chat",
                                        tint = if (unreadDmCount > 0) Color(0xFFE040FB).copy(alpha = 0.5f + 0.5f * blinkAlpha) else WaterBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (unreadDmCount > 0) "Switch 💬 ($unreadDmCount DM)" else "Switch 💬",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (unreadDmCount > 0) Color(0xFFE040FB).copy(alpha = 0.5f + 0.5f * blinkAlpha) else WaterBlue
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { chatViewModel.toggleSearchActive() },
                            modifier = Modifier.testTag("search_chat_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search Chat",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { chatViewModel.loadInitialMessagesAndSubscribe() },
                            modifier = Modifier.testTag("refresh_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextPrimary
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(screenBg)
        ) {
            // Quick Horizontal Chat Options Switcher Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    AssistChip(
                        onClick = { chatViewModel.openChatOptionsSheet() },
                        label = { Text("💬 All Channels & DMs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WaterBlue) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = WaterBlue.copy(alpha = 0.15f))
                    )
                }

                items(studyGroups) { group ->
                    val isSelected = selectedChatOption.id == group.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { chatViewModel.selectChatOption(group) },
                        label = { Text("${group.iconEmoji} ${group.name}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WaterBlue,
                            selectedLabelColor = Color.White,
                            containerColor = Charcoal.copy(alpha = 0.6f),
                            labelColor = TextPrimary
                        )
                    )
                }

                items(directMessageMembers) { member ->
                    val isSelected = selectedChatOption.id == member.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { chatViewModel.selectChatOption(member) },
                        label = { Text("🔒 ${member.name}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8E24AA),
                            selectedLabelColor = Color.White,
                            containerColor = Charcoal.copy(alpha = 0.6f),
                            labelColor = TextPrimary
                        )
                    )
                }
            }
            // Expandable Search & Filter Panel
            if (isSearchActive || isFilterActive) {
                Surface(
                    color = Charcoal,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = sendButtonBg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { chatViewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text("Search keywords or senders...", fontSize = 13.sp, color = TextSecondary)
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_input_field")
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { chatViewModel.setSearchQuery("") },
                                        modifier = Modifier.size(28.dp).testTag("clear_search_input")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Date Filter Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Date:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ChatDateRangeFilter.values()) { range ->
                                    val isSelected = selectedDateRangeFilter == range
                                    Surface(
                                        onClick = { chatViewModel.setDateRangeFilter(range) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) sendButtonBg else SurfaceCard,
                                        modifier = Modifier.testTag("date_filter_${range.name}")
                                    ) {
                                        Text(
                                            text = range.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF0F0F11) else TextPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pinned Messages Banner
            if (pinnedMessages.isNotEmpty()) {
                val latestPinned = pinnedMessages.last()
                Surface(
                    color = Color(0xFF221D12),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPinnedMessagesPage = true }
                        .testTag("pinned_messages_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned Message",
                                tint = sendButtonBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Pinned Messages (${pinnedMessages.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = sendButtonBg
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Tap to view all",
                                        fontSize = 10.sp,
                                        color = WaterBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = latestPinned.text,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { chatViewModel.togglePinMessage(latestPinned.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unpin",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Chat Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && messages.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = sendButtonBg)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading chat history...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                } else if (filteredMessages.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .align(Alignment.Center),
                        colors = CardDefaults.cardColors(containerColor = Charcoal),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isFilterActive) Icons.Default.SearchOff else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = sendButtonBg,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isFilterActive) "No messages match search filters." else "No messages yet.\nSend a message or media file to start!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = TextPrimary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("messages_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(filteredMessages) { index, message ->
                            val isOutgoing = message.senderId == currentUserId
                            val isFirstInGroup = (index == 0 || filteredMessages[index - 1].senderId != message.senderId)
                            val showSenderDetails = isFirstInGroup && !isOutgoing
                            val isSelected = selectedMessageIds.contains(message.id)

                            Spacer(modifier = Modifier.height(if (isFirstInGroup && index > 0) 8.dp else 2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMultiSelectActive) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { chatViewModel.toggleMessageSelection(message.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = WaterBlue,
                                            uncheckedColor = Color.Gray
                                        ),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    ChatBubble(
                                        message = message,
                                        isOutgoing = isOutgoing,
                                        showSenderDetails = showSenderDetails,
                                        outgoingBg = outgoingBubbleColor,
                                        incomingBg = incomingBubbleColor,
                                        textOutgoing = textColorOutgoing,
                                        textIncoming = textColorIncoming,
                                        currentUserId = currentUserId,
                                        isMultiSelectActive = isMultiSelectActive,
                                        appViewModel = appViewModel,
                                        onEditClick = { chatViewModel.startEditingMessage(it) },
                                        onLongClickMessage = {
                                            if (isMultiSelectActive) {
                                                chatViewModel.toggleMessageSelection(it.id)
                                            } else {
                                                reactionPickerMessage = it
                                            }
                                        },
                                        onToggleReaction = { msgId, emoji -> chatViewModel.toggleReaction(msgId, emoji) },
                                        onSwipeReply = { chatViewModel.setReplyingTo(it) },
                                        onShowMessageInfo = { selectedInfoMessage = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reaction Picker & Message Options Dialog
            if (reactionPickerMessage != null) {
                val targetMsg = reactionPickerMessage!!
                AlertDialog(
                    onDismissRequest = { reactionPickerMessage = null },
                    containerColor = Charcoal,
                    title = {
                        Text(
                            text = "Message Actions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "\"${targetMsg.text}\"",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Quick Emojis
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉")
                                val parsed = targetMsg.parseReactions()
                                quickEmojis.forEach { emoji ->
                                    val hasReacted = parsed[emoji]?.contains(currentUserId) == true
                                    Surface(
                                        onClick = {
                                            chatViewModel.toggleReaction(targetMsg.id, emoji)
                                            reactionPickerMessage = null
                                        },
                                        shape = CircleShape,
                                        color = if (hasReacted) sendButtonBg.copy(alpha = 0.25f) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (hasReacted) sendButtonBg else SurfaceCard.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(text = emoji, fontSize = 18.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Detailed Delivery & Read Status Report Button
                            OutlinedButton(
                                onClick = {
                                    selectedInfoMessage = targetMsg
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg),
                                modifier = Modifier.fillMaxWidth().testTag("view_message_info_report_btn")
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Delivery Info", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detailed Delivery & Read Report 📊", fontSize = 13.sp, color = sendButtonBg, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Copy Text Button
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(targetMsg.text))
                                    Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Text", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Select Multiple
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.startMultiSelect(targetMsg.id)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Checklist, contentDescription = "Select Multiple", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Multiple Messages", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reply Button
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.setReplyingTo(targetMsg)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reply to Message", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pin / Delete
                            val isCurrentlyPinned = targetMsg.isPinned
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.togglePinMessage(targetMsg.id)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isCurrentlyPinned) "Unpin Message" else "Pin Message", fontSize = 13.sp, color = sendButtonBg)
                            }

                            if (targetMsg.senderId == currentUserId) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        chatViewModel.deleteMessage(targetMsg.id)
                                        reactionPickerMessage = null
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Message", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { reactionPickerMessage = null }) {
                            Text("Cancel", color = sendButtonBg)
                        }
                    }
                )
            }

            // Detailed Message Delivery & Read Report Dialog
            if (selectedInfoMessage != null) {
                val allCommunityMembers = remember(directMessageMembers, availableSenders, onlineUsers, selectedInfoMessage, currentUserId, selectedChatOption) {
                    val dmMemberNames = directMessageMembers.map { if (!it.memberUserId.isNullOrBlank()) it.memberUserId!! else it.name }
                    val combined = (dmMemberNames + availableSenders + onlineUsers + listOfNotNull(selectedInfoMessage?.senderId, currentUserId))
                        .filter { it.isNotBlank() }
                        .distinct()
                    val targetCount = maxOf(selectedChatOption.memberCount, combined.size)
                    val result = combined.toMutableList()
                    var counter = 1
                    while (result.size < targetCount) {
                        val placeholder = "Group Member $counter"
                        if (!result.contains(placeholder)) {
                            result.add(placeholder)
                        }
                        counter++
                    }
                    result
                }

                MessageDeliveryReportDialog(
                    message = selectedInfoMessage!!,
                    communityMembers = allCommunityMembers,
                    currentUserId = currentUserId,
                    onDismiss = { selectedInfoMessage = null }
                )
            }

            // Replying Banner
            val currentReplyMsg = replyingToMessage
            if (currentReplyMsg != null) {
                Surface(
                    color = SurfaceCard,
                    border = BorderStroke(0.5.dp, sendButtonBg.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(sendButtonBg)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Replying to ${currentReplyMsg.senderId.replace("_", " ")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sendButtonBg
                                )
                                Text(
                                    text = currentReplyMsg.text,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { chatViewModel.cancelReply() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Bottom Input Toolbar
            Surface(
                color = Charcoal,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val performChatSend = {
                        val trimmed = textInput.trim()
                        if (trimmed.isNotEmpty()) {
                            val currentEditing = editingMessage
                            if (currentEditing != null) {
                                chatViewModel.onEditMessage(currentEditing.id, trimmed)
                            } else {
                                chatViewModel.onSendMessage(trimmed)
                            }
                            textInput = ""
                            chatViewModel.onUserTypingChanged(false)
                        }
                    }

                    if (isRecordingVoice) {
                        // Voice Note Recording UI
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Recording Voice Note... %02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        voiceRecorderHelper.cancelRecording()
                                        isRecordingVoice = false
                                        Toast.makeText(context, "Voice note cancelled", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel Recording", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Voice Note Button
                        FloatingActionButton(
                            onClick = {
                                val recordedPath = voiceRecorderHelper.stopRecording()
                                isRecordingVoice = false
                                if (recordedPath != null) {
                                    chatViewModel.onSendMediaMessage("voice", recordedPath)
                                    Toast.makeText(context, "Voice Note uploaded to Google Drive & sent!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            containerColor = sendButtonBg,
                            contentColor = Color.Black,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Note", tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // Normal Text Input UI
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                // Camera Button
                                IconButton(
                                    onClick = {
                                        try {
                                            takePhotoLauncher.launch(null)
                                        } catch (e: Throwable) {
                                            Toast.makeText(context, "Cannot open camera: ${e.localizedMessage ?: "App or permission missing"}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Take Photo",
                                        tint = sendButtonBg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                com.example.util.RichContentInputField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        chatViewModel.onUserTypingChanged(it.isNotEmpty())
                                    },
                                    hint = if (editingMessage != null) "Edit message..." else "Type a message, paste image, or drop media...",
                                    textColor = TextPrimary,
                                    backgroundColor = Color.Transparent,
                                    borderColor = Color.Transparent,
                                    onSend = { performChatSend() },
                                    onRichContentReceived = { uris, text, source ->
                                        if (!text.isNullOrBlank()) {
                                            textInput = if (textInput.isBlank()) text else "$textInput $text"
                                        }
                                        for (uri in uris) {
                                            try {
                                                val mime = context.contentResolver.getType(uri) ?: "image/*"
                                                val mediaType = when {
                                                    mime.startsWith("image/") -> "photo"
                                                    mime.startsWith("video/") -> "video"
                                                    mime.startsWith("audio/") -> "voice"
                                                    else -> "file"
                                                }
                                                val inputStream = context.contentResolver.openInputStream(uri)
                                                val destFile = File(context.cacheDir, "rich_content_${System.currentTimeMillis()}")
                                                if (inputStream != null) {
                                                    destFile.outputStream().use { outputStream ->
                                                        inputStream.copyTo(outputStream)
                                                    }
                                                    chatViewModel.onSendMediaMessage(mediaType, destFile.absolutePath)
                                                    Toast.makeText(context, "Rich content inserted & shared!", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("message_input_field")
                                )

                                // Attachment Button
                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach Media",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (textInput.trim().isEmpty()) {
                            // Mic Button for Recording Voice Notes
                            FloatingActionButton(
                                onClick = {
                                    val temp = voiceRecorderHelper.startRecording()
                                    if (temp != null) {
                                        isRecordingVoice = true
                                    } else {
                                        Toast.makeText(context, "Unable to start microphone recording", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                containerColor = SurfaceCard,
                                contentColor = sendButtonBg,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = sendButtonBg, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            // Send Text Message Button
                            FloatingActionButton(
                                onClick = { performChatSend() },
                                containerColor = sendButtonBg,
                                contentColor = Color.Black,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag(if (editingMessage != null) "confirm_edit_button" else "send_message_button")
                            ) {
                                Icon(
                                    imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (editingMessage != null) "Save Edit" else "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachment Chooser Sheet / Dialog
    if (showAttachmentSheet) {
        AlertDialog(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = Charcoal,
            title = {
                Text("Attach Media via Google Drive", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a media type to upload and share via Google Drive link:", fontSize = 12.sp, color = TextSecondary)

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            try {
                                takePhotoLauncher.launch(null)
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Cannot open camera: ${e.localizedMessage ?: "App or permission missing"}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Camera Photo", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("image/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photo from Gallery", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("video/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Video from Gallery", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("*/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick File / Document", color = sendButtonBg)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentSheet = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showChatOptionsSheet) {
        ChatOptionsBottomSheetDialog(
            selectedOption = selectedChatOption,
            studyGroups = studyGroups,
            directMessageMembers = directMessageMembers,
            onSelectOption = { chatViewModel.selectChatOption(it) },
            onCreateStudyGroup = { name, desc -> chatViewModel.createCustomStudyGroup(name, desc) },
            onCreateContactDM = { first, last, phone -> chatViewModel.createCustomContactDM(first, last, "", phone) },
            onDismiss = { chatViewModel.closeChatOptionsSheet() }
        )
    }

    if (showPinnedMessagesPage) {
        Dialog(
            onDismissRequest = { showPinnedMessagesPage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DeepSlate
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Surface(
                        color = Charcoal,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showPinnedMessagesPage = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(sendButtonBg.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned Messages",
                                    tint = sendButtonBg,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pinned Messages 📌",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${pinnedMessages.size} message${if (pinnedMessages.size == 1) "" else "s"} pinned in this chat",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    if (pinnedMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Pinned Messages",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pinned messages will appear here like a dedicated chat log.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pinnedMessages, key = { it.id }) { message ->
                                val isOutgoing = message.senderId == currentUserId || message.senderId == "me"
                                val dateHeader = remember(message.createdAt, message.timestamp) {
                                    formatChatFullDateTime(message.createdAt, message.timestamp)
                                }

                                Surface(
                                    color = SurfaceCard.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                reactionPickerMessage = message
                                            }
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null,
                                                    tint = sendButtonBg,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "PINNED MESSAGE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = sendButtonBg,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                            Text(
                                                text = dateHeader,
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        ChatBubble(
                                            message = message,
                                            isOutgoing = isOutgoing,
                                            showSenderDetails = true,
                                            outgoingBg = outgoingBubbleColor,
                                            incomingBg = incomingBubbleColor,
                                            textOutgoing = textColorOutgoing,
                                            textIncoming = textColorIncoming,
                                            currentUserId = currentUserId,
                                            appViewModel = appViewModel,
                                            onEditClick = { chatViewModel.startEditingMessage(it) },
                                            onLongClickMessage = {
                                                reactionPickerMessage = it
                                            },
                                            onToggleReaction = { msgId, emoji -> chatViewModel.toggleReaction(msgId, emoji) },
                                            onSwipeReply = { chatViewModel.setReplyingTo(it) },
                                            onShowMessageInfo = { selectedInfoMessage = it }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    showSenderDetails: Boolean,
    outgoingBg: Color,
    incomingBg: Color,
    textOutgoing: Color,
    textIncoming: Color,
    currentUserId: String = "",
    isMultiSelectActive: Boolean = false,
    appViewModel: AppViewModel? = null,
    onEditClick: ((ChatMessage) -> Unit)? = null,
    onLongClickMessage: ((ChatMessage) -> Unit)? = null,
    onToggleReaction: ((Long, String) -> Unit)? = null,
    onSwipeReply: ((ChatMessage) -> Unit)? = null,
    onShowMessageInfo: ((ChatMessage) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleBg = if (isOutgoing) outgoingBg else incomingBg
    val textColor = if (isOutgoing) textOutgoing else textIncoming

    val senderNameFormatted = remember(message.senderId) {
        message.senderId.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    val senderAvatarColor = remember(message.senderId) {
        val colors = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
            Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFB8C00)
        )
        colors[abs(message.senderId.hashCode()) % colors.size]
    }

    val senderInitial = remember(senderNameFormatted) {
        senderNameFormatted.firstOrNull()?.uppercase() ?: "U"
    }

    val shape = if (isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(),
        label = "swipe_reply_offset"
    )

    // Media & Action Embed Detection
    val voiceTag = remember(message.text) { extractMediaTag(message.text, "VOICE") }
    val imageTag = remember(message.text) { extractMediaTag(message.text, "IMAGE") }
    val videoTag = remember(message.text) { extractMediaTag(message.text, "VIDEO") }
    val fileTag = remember(message.text) { extractMediaTag(message.text, "FILE") }
    val fileActionPath = remember(message.text) { extractFileActionTag(message.text) }
    val journalActionId = remember(message.text) { extractJournalActionTag(message.text) }
    val taskActionId = remember(message.text) { extractTaskActionTag(message.text) }
    val syllabusActionTopicId = remember(message.text) { extractSyllabusActionTag(message.text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}"),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (showSenderDetails) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(senderAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderInitial,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = senderNameFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = senderAvatarColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 70f) {
                                onSwipeReply?.invoke(message)
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0 || offsetX > 0) {
                                offsetX = (offsetX + dragAmount * 0.5f).coerceIn(0f, 140f)
                            }
                        }
                    )
                }
        ) {
            if (animatedOffsetX > 10f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Swipe Reply",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) },
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    if (isOutgoing && onEditClick != null && voiceTag == null && imageTag == null && videoTag == null) {
                        IconButton(
                            onClick = { onEditClick(message) },
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Message",
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        color = bubbleBg,
                        shape = shape,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(start = if (!isOutgoing && !showSenderDetails) 36.dp else 0.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectActive) {
                                        onLongClickMessage?.invoke(message)
                                    }
                                },
                                onLongClick = { onLongClickMessage?.invoke(message) }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Quoted Message Box
                            if (!message.replyToText.isNullOrBlank()) {
                                Surface(
                                    color = if (isOutgoing) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isOutgoing) WaterBlue else WaterBlueAccent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            val replySenderName = message.replyToSender?.replace("_", " ") ?: "User"
                                            Text(
                                                text = replySenderName.split(" ").joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOutgoing) WaterBlue else WaterBlueAccent
                                            )
                                            Text(
                                                text = message.replyToText,
                                                fontSize = 11.sp,
                                                color = textColor.copy(alpha = 0.9f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Render Embedded Drive Media Players or Text
                            when {
                                voiceTag != null -> {
                                    val (path, drive) = voiceTag
                                    ChatAudioPlayer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                imageTag != null -> {
                                    val (path, drive) = imageTag
                                    ChatImageViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                videoTag != null -> {
                                    val (path, drive) = videoTag
                                    ChatVideoViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                fileTag != null -> {
                                    val (path, drive) = fileTag
                                    ChatFileViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                else -> {
                                    Column {
                                        Text(
                                            text = cleanMessageText(message.text),
                                            fontSize = 14.sp,
                                            color = textColor,
                                            lineHeight = 19.sp
                                        )
                                        if (fileActionPath != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    appViewModel?.openFileLocation(fileActionPath)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("go_to_file_location_btn_${message.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = "Go to file location",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "GO TO FILE LOCATION",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (journalActionId != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    appViewModel?.selectJournal(journalActionId)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0), contentColor = Color.White),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("open_journal_entry_btn_${message.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Book,
                                                    contentDescription = "Open journal entry",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "OPEN JOURNAL ENTRY",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (taskActionId != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    appViewModel?.selectTask(taskActionId)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("open_task_info_btn_${message.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Task,
                                                    contentDescription = "Open task info",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "OPEN TASK INFO",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (syllabusActionTopicId != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    appViewModel?.openSyllabusLocation(syllabusActionTopicId)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("go_to_syllabus_tree_btn_${message.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountTree,
                                                    contentDescription = "Go to syllabus tree",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "GO TO SYLLABUS TREE",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (message.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                Text(
                                    text = formatChatTimestamp(message.createdAt, message.timestamp),
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.65f)
                                )

                                 if (isOutgoing) {
                                    Surface(
                                        onClick = { onShowMessageInfo?.invoke(message) },
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.testTag("status_ticks_${message.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (message.status == "PENDING") Icons.Default.AccessTime else Icons.Default.DoneAll,
                                                contentDescription = message.status,
                                                tint = if (message.status == "READ") WaterBlueAccent else if (message.status == "PENDING") TextSecondary else TextSecondary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val parsedReactions = remember(message.reactions) { message.parseReactions() }
                            if (parsedReactions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.align(if (isOutgoing) Alignment.End else Alignment.Start),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    parsedReactions.forEach { (emoji, userList) ->
                                        val hasReacted = userList.contains(currentUserId)
                                        Surface(
                                            onClick = { onToggleReaction?.invoke(message.id, emoji) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (hasReacted) WaterBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, if (hasReacted) WaterBlue else Color.Transparent)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(text = emoji, fontSize = 12.sp)
                                                if (userList.size > 1) {
                                                    Text(
                                                        text = "${userList.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textColor
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
}

// Helper to extract [TAG:localPath|driveUrl] tags from text
private fun extractMediaTag(text: String, tag: String): Pair<String, String>? {
    val pattern = "\\[$tag:(.*?)\\]".toRegex()
    val match = pattern.find(text) ?: return null
    val content = match.groupValues.getOrNull(1) ?: return null
    val parts = content.split("|")
    val local = parts.getOrNull(0) ?: ""
    val drive = parts.getOrNull(1) ?: ""
    return Pair(local, drive)
}

@Composable
fun ChatAudioPlayer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(mediaPathOrUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    currentPos = mediaPlayer?.currentPosition ?: 0
                    duration = mediaPlayer?.duration ?: 0
                } catch (e: Exception) { }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    val togglePlay = {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    if (mediaPathOrUrl.startsWith("http://") || mediaPathOrUrl.startsWith("https://")) {
                        setDataSource(mediaPathOrUrl)
                    } else {
                        setDataSource(context, android.net.Uri.parse(mediaPathOrUrl))
                    }
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPos = 0
                    }
                }
            } else {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error playing audio note", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isOutgoing) Color(0xFF382C10) else Color(0xFF25252A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaterBlue.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
                Text("Google Drive Voice Note", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { togglePlay() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WaterBlue)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = if (duration > 0) currentPos.toFloat() / duration else 0f,
                        onValueChange = { frac ->
                            mediaPlayer?.let { mp ->
                                val target = (frac * duration).toInt()
                                mp.seekTo(target)
                                currentPos = target
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = WaterBlue,
                            activeTrackColor = WaterBlue,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMs(currentPos.toLong()), fontSize = 10.sp, color = TextSecondary)
                        Text(formatMs(duration.toLong()), fontSize = 10.sp, color = TextSecondary)
                    }
                }

                // Download Button
                IconButton(
                    onClick = {
                        val fileName = "Voice_Note_${System.currentTimeMillis()}.m4a"
                        downloadMediaFile(context, mediaPathOrUrl, fileName)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Share Button
                IconButton(
                    onClick = {
                        val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                        shareMediaContent(context, shareText, "audio/*")
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatImageViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var showFullscreen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WaterBlue.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
            Text("Google Drive Photo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable { showFullscreen = true }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = if (mediaPathOrUrl.startsWith("http")) mediaPathOrUrl else File(mediaPathOrUrl),
                    contentDescription = "Photo Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val fileName = "Photo_${System.currentTimeMillis()}.jpg"
                            downloadMediaFile(context, mediaPathOrUrl, fileName)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                            shareMediaContent(context, shareText, "image/*")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showFullscreen) {
        Dialog(onDismissRequest = { showFullscreen = false }) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = if (mediaPathOrUrl.startsWith("http")) mediaPathOrUrl else File(mediaPathOrUrl),
                        contentDescription = "Fullscreen Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IconButton(
                        onClick = { showFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatVideoViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var showPlayerDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WaterBlue.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
            Text("Google Drive Video", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable { showPlayerDialog = true }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WaterBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.Black, modifier = Modifier.size(32.dp))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val fileName = "Video_${System.currentTimeMillis()}.mp4"
                            downloadMediaFile(context, mediaPathOrUrl, fileName)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                            shareMediaContent(context, shareText, "video/*")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showPlayerDialog) {
        VideoPlayerDialog(filePath = mediaPathOrUrl, onDismiss = { showPlayerDialog = false })
    }
}

@Composable
fun ChatFileViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    val fileName = remember(mediaPathOrUrl) { File(mediaPathOrUrl).name }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isOutgoing) Color(0xFF382C10) else Color(0xFF25252A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaterBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = WaterBlue)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Google Drive Document", fontSize = 10.sp, color = WaterBlue)
            }
            IconButton(
                onClick = { downloadMediaFile(context, mediaPathOrUrl, fileName) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                    shareMediaContent(context, shareText, "*/*")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

fun formatChatTimestamp(isoDate: String?, timestampMs: Long = 0L): String {
    val instant = try {
        if (!isoDate.isNullOrBlank() && isoDate != "Just now") {
            Instant.parse(isoDate)
        } else if (timestampMs > 0L) {
            Instant.ofEpochMilli(timestampMs)
        } else null
    } catch (e: Exception) {
        if (timestampMs > 0L) Instant.ofEpochMilli(timestampMs) else null
    }

    if (instant == null) return "Just now"

    val zone = ZoneId.systemDefault()
    val msgDateTime = LocalDateTime.ofInstant(instant, zone)
    val nowDateTime = LocalDateTime.now(zone)

    val msgDate = msgDateTime.toLocalDate()
    val nowDate = nowDateTime.toLocalDate()

    val timeStr = msgDateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))

    val diffSeconds = Math.abs(java.time.Duration.between(instant, Instant.now()).seconds)
    if (diffSeconds < 60 && msgDate == nowDate) {
        return "Just now"
    }

    return when {
        msgDate == nowDate -> timeStr
        msgDate == nowDate.minusDays(1) -> "Yesterday $timeStr"
        msgDate.year == nowDate.year -> msgDateTime.format(DateTimeFormatter.ofPattern("MMM dd, hh:mm a"))
        else -> msgDateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a"))
    }
}

fun formatChatFullDateTime(isoDate: String?, timestampMs: Long = 0L): String {
    val instant = try {
        if (!isoDate.isNullOrBlank() && isoDate != "Just now") {
            Instant.parse(isoDate)
        } else if (timestampMs > 0L) {
            Instant.ofEpochMilli(timestampMs)
        } else null
    } catch (e: Exception) {
        if (timestampMs > 0L) Instant.ofEpochMilli(timestampMs) else null
    }

    if (instant == null) return "Today • Just now"

    val zone = ZoneId.systemDefault()
    val msgDateTime = LocalDateTime.ofInstant(instant, zone)
    val nowDate = java.time.LocalDate.now(zone)

    val timeStr = msgDateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    val dateStr = msgDateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

    val msgDate = msgDateTime.toLocalDate()
    val dayPrefix = when {
        msgDate == nowDate -> "Today"
        msgDate == nowDate.minusDays(1) -> "Yesterday"
        else -> dateStr
    }

    return "$dayPrefix • $timeStr"
}

data class UserDeliveryReport(
    val userId: String,
    val displayName: String,
    val isSender: Boolean,
    val status: String,
    val deliveredAt: String,
    val readAt: String?
)

fun generateMessageDeliveryReport(
    message: ChatMessage,
    communityMembers: List<String>,
    currentUserId: String,
    context: Context? = null
): List<UserDeliveryReport> {
    val prefs = context?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val userEmail = prefs?.getString("user_email", "")?.trim()?.lowercase() ?: ""
    val emailPrefix = if (userEmail.contains("@")) userEmail.substringBefore("@") else ""
    val username = prefs?.getString("logged_in_username", "")?.trim()?.lowercase() ?: ""

    fun isSelf(u: String): Boolean {
        val s = u.trim().lowercase()
        if (s.isBlank()) return false
        val cur = currentUserId.trim().lowercase()
        if (s == cur || s == "user_me" || s == "user me" || s == "me" || s == "myself") return true
        if (userEmail.isNotBlank() && s == userEmail) return true
        if (emailPrefix.isNotBlank() && s == emailPrefix) return true
        if (username.isNotBlank() && s == username) return true
        return false
    }

    val rawMembers = (communityMembers + listOfNotNull(message.senderId, currentUserId)).filter { it.isNotBlank() }
    val normalizedMembers = mutableListOf<String>()
    var addedSelf = false

    for (m in rawMembers) {
        if (isSelf(m)) {
            if (!addedSelf) {
                normalizedMembers.add("User Me")
                addedSelf = true
            }
        } else {
            if (!normalizedMembers.contains(m)) {
                normalizedMembers.add(m)
            }
        }
    }

    val baseTimeMillis = try {
        if (!message.createdAt.isNullOrBlank()) {
            Instant.parse(message.createdAt).toEpochMilli()
        } else {
            System.currentTimeMillis()
        }
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • hh:mm:ss a")
        .withZone(ZoneId.systemDefault())

    val realTimeStr = dateFormatter.format(Instant.ofEpochMilli(baseTimeMillis))

    val isMsgFromSelf = isSelf(message.senderId)

    return normalizedMembers.map { userId ->
        val isSender = (userId == "User Me" && isMsgFromSelf) || (userId == message.senderId)
        val displayName = if (userId == "User Me") {
            if (isSender) "User Me (Sender)" else "User Me"
        } else {
            val name = userId.replace("_", " ")
                .split(" ")
                .joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            if (isSender) "$name (Sender)" else name
        }

        if (isSender) {
            UserDeliveryReport(
                userId = userId,
                displayName = displayName,
                isSender = true,
                status = "SENT",
                deliveredAt = realTimeStr,
                readAt = realTimeStr
            )
        } else {
            val status = when (message.status) {
                "PENDING" -> "PENDING"
                "SENT", "DELIVERED" -> "DELIVERED"
                "READ" -> "READ"
                else -> "DELIVERED"
            }

            val deliveredAtStr = if (status == "PENDING") "Pending..." else realTimeStr
            val readAtStr = if (status == "READ") realTimeStr else null

            UserDeliveryReport(
                userId = userId,
                displayName = displayName,
                isSender = false,
                status = status,
                deliveredAt = deliveredAtStr,
                readAt = readAtStr
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDeliveryReportDialog(
    message: ChatMessage,
    communityMembers: List<String>,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val fullReport = remember(message, communityMembers) {
        generateMessageDeliveryReport(message, communityMembers, currentUserId, context)
    }

    val totalCount = fullReport.size
    val readCount = fullReport.count { it.status == "READ" || it.isSender }
    val deliveredCount = fullReport.count { it.status == "DELIVERED" || it.status == "READ" || it.isSender }
    val pendingCount = fullReport.count { it.status == "PENDING" }

    val filteredReport = remember(fullReport, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        fullReport.filter { item ->
            val matchesSearch = q.isEmpty() || item.displayName.lowercase().contains(q) || item.userId.lowercase().contains(q)
            val matchesFilter = when (selectedFilter) {
                "READ" -> item.status == "READ" || item.isSender
                "DELIVERED" -> item.status == "DELIVERED"
                "PENDING" -> item.status == "PENDING"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Charcoal,
        modifier = Modifier.fillMaxWidth().testTag("message_delivery_report_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = WaterBlue)
                Column {
                    Text(
                        text = "Message Delivery Report",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Community Member Read & Delivery Status",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Message Snippet Card
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Message Content",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterBlue
                            )
                            Text(
                                text = "Sent: ${formatChatTimestamp(message.createdAt, message.timestamp)}",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${message.text}\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // High-level Stats Overview Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = WaterBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("👁️ Read", fontSize = 10.sp, color = WaterBlueAccent, fontWeight = FontWeight.Bold)
                            Text("$readCount / $totalCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📥 Delivered", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text("$deliveredCount / $totalCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⏳ Pending", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text("$pendingCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                // Member Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter community members...", fontSize = 12.sp, color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = SurfaceCard
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterOptions = listOf(
                        "ALL" to "All ($totalCount)",
                        "READ" to "Read ($readCount)",
                        "DELIVERED" to "Delivered Only (${deliveredCount - readCount})",
                        "PENDING" to "Pending ($pendingCount)"
                    )
                    items(filterOptions) { (key, label) ->
                        val isSelected = selectedFilter == key
                        Surface(
                            onClick = { selectedFilter = key },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) WaterBlue else SurfaceCard,
                            modifier = Modifier.testTag("filter_chip_$key")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Individual Community Member Status (${filteredReport.size}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterBlue
                )

                // Detailed Member Status Cards List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredReport) { item ->
                        val userAvatarColor = remember(item.userId) {
                            val colors = listOf(
                                Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
                                Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
                                Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B)
                            )
                            colors[abs(item.userId.hashCode()) % colors.size]
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(userAvatarColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.displayName.firstOrNull()?.uppercase() ?: "U",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = item.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = if (item.isSender) "Message Sender" else "Community Member",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    // Status Badge
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (item.status) {
                                            "READ" -> WaterBlue.copy(alpha = 0.2f)
                                            "DELIVERED" -> Color.White.copy(alpha = 0.1f)
                                            "PENDING" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                            else -> WaterBlue.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (item.status) {
                                                    "PENDING" -> Icons.Default.AccessTime
                                                    "SENT" -> Icons.Default.Done
                                                    else -> Icons.Default.DoneAll
                                                },
                                                contentDescription = null,
                                                tint = when (item.status) {
                                                    "READ" -> WaterBlueAccent
                                                    "PENDING" -> Color(0xFFFF9800)
                                                    else -> TextSecondary
                                                },
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = item.status,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (item.status) {
                                                    "READ" -> WaterBlueAccent
                                                    "PENDING" -> Color(0xFFFF9800)
                                                    else -> TextPrimary
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Detailed Timestamps Table
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("📥 Delivered To Member", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                        Text(item.deliveredAt, fontSize = 11.sp, color = TextPrimary)
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text("👁️ Read By Member", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = item.readAt ?: "Not read yet",
                                            fontSize = 11.sp,
                                            fontWeight = if (item.readAt != null) FontWeight.Medium else FontWeight.Normal,
                                            color = if (item.readAt != null) WaterBlueAccent else TextSecondary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val reportText = buildString {
                            appendLine("📊 COMMUNITY MESSAGE DELIVERY & READ REPORT")
                            appendLine("Message: \"${message.text}\"")
                            appendLine("Sent At: ${formatChatTimestamp(message.createdAt, message.timestamp)}")
                            appendLine("Summary: Read by $readCount/$totalCount • Delivered to $deliveredCount/$totalCount")
                            appendLine("----------------------------------------")
                            fullReport.forEach { item ->
                                appendLine("• ${item.displayName}: ${item.status}")
                                appendLine("  - Delivered: ${item.deliveredAt}")
                                appendLine("  - Read: ${item.readAt ?: "Not Read Yet"}")
                            }
                        }
                        clipboardManager.setText(AnnotatedString(reportText))
                        Toast.makeText(context, "Detailed delivery report copied!", Toast.LENGTH_SHORT).show()
                    },
                    border = BorderStroke(1.dp, WaterBlue)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Report", fontSize = 12.sp, color = WaterBlue)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

fun extractFileActionTag(text: String): String? {
    val regex = Regex("\\[FILE_ACTION:([^\\]]+)\\]")
    val match = regex.find(text)
    return match?.groupValues?.get(1)
}

fun extractJournalActionTag(text: String): Int? {
    val regex = Regex("\\[JOURNAL_ACTION:([^\\]]+)\\]")
    val match = regex.find(text)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

fun extractTaskActionTag(text: String): Int? {
    val regex = Regex("\\[TASK_ACTION:([^\\]]+)\\]")
    val match = regex.find(text)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

fun extractSyllabusActionTag(text: String): String? {
    val regex = Regex("\\[SYLLABUS_ACTION:([^\\]]+)\\]")
    val match = regex.find(text)
    return match?.groupValues?.get(1)
}

fun cleanMessageText(text: String): String {
    return text.replace(Regex("\n?\\[GROUP:[^\\]]+\\]"), "")
               .replace(Regex("\n?\\[DM:[^\\]]+\\]"), "")
               .replace(Regex("\n?\\[FILE_ACTION:[^\\]]+\\]"), "")
               .replace(Regex("\n?\\[JOURNAL_ACTION:[^\\]]+\\]"), "")
               .replace(Regex("\n?\\[TASK_ACTION:[^\\]]+\\]"), "")
               .replace(Regex("\n?\\[SYLLABUS_ACTION:[^\\]]+\\]"), "")
               .trim()
}

@Composable
fun ChatOptionsBottomSheetDialog(
    selectedOption: ChatOption,
    studyGroups: List<ChatOption>,
    directMessageMembers: List<ChatOption>,
    onSelectOption: (ChatOption) -> Unit,
    onCreateStudyGroup: (String, String) -> Unit = { _, _ -> },
    onCreateContactDM: (String, String, String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Study Groups, 1 = Private DMs
    var searchQuery by remember { mutableStateOf("") }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactFirst by remember { mutableStateOf("") }
    var newContactLast by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = Charcoal,
            title = { Text("🎓 Create Study Group", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name *", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WaterBlue, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newGroupDesc,
                        onValueChange = { newGroupDesc = it },
                        label = { Text("Description / Topic", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WaterBlue, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            onCreateStudyGroup(newGroupName, newGroupDesc)
                            newGroupName = ""
                            newGroupDesc = ""
                            showCreateGroupDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Create Group", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            containerColor = Charcoal,
            title = { Text("🔒 Add Study Group Member DM", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newContactFirst,
                        onValueChange = { newContactFirst = it },
                        label = { Text("Member Display Name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WaterBlue, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("Member Email (Study Group User) *", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WaterBlue, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newContactFirst.isNotBlank() || newContactPhone.isNotBlank()) {
                            onCreateContactDM(newContactFirst, newContactLast, newContactPhone)
                            newContactFirst = ""
                            newContactLast = ""
                            newContactPhone = ""
                            showAddContactDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0), contentColor = Color.White)
                ) {
                    Text("Add Member DM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Charcoal,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WaterBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💬", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "Chat Options & Channels",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Switch between study groups & private chats",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) WaterBlue else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎓 Study Groups (${studyGroups.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) Color.Black else TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) Color(0xFF9C27B0) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔒 Members DM (${directMessageMembers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color.White else TextPrimary
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (selectedTab == 0) "Available Study Groups" else "Direct Messages",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    TextButton(
                        onClick = {
                            if (selectedTab == 0) showCreateGroupDialog = true else showAddContactDialog = true
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (selectedTab == 0) "+ New Study Group" else "+ Add Group Member",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) WaterBlue else Color(0xFFAB47BC)
                        )
                    }
                }

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedTab == 0) "Search study groups..." else "Search community members...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 0) {
                        // Study Groups
                        val filteredGroups = studyGroups.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.description.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredGroups.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No study groups found matching \"$searchQuery\"", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }

                        items(filteredGroups) { group ->
                            val isSelected = selectedOption.id == group.id
                            Surface(
                                color = if (isSelected) WaterBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) WaterBlue else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectOption(group) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    UserAvatar(
                                        emojiOrBase64 = group.iconEmoji,
                                        size = 44.dp,
                                        fallback = group.name.take(1).uppercase()
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = group.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Surface(
                                                color = WaterBlue.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "${group.memberCount} members",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = WaterBlue,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = group.description,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Active Group", tint = WaterBlue)
                                    } else {
                                        OutlinedButton(
                                            onClick = { onSelectOption(group) },
                                            border = BorderStroke(1.dp, WaterBlue),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Enter", fontSize = 11.sp, color = WaterBlue)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Direct Message Members
                        val filteredMembers = directMessageMembers.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.description.contains(searchQuery, ignoreCase = true) ||
                                    (it.memberUserId?.contains(searchQuery, ignoreCase = true) == true)
                        }

                        if (filteredMembers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No community members found matching \"$searchQuery\"", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }

                        items(filteredMembers) { member ->
                            val isSelected = selectedOption.id == member.id
                            Surface(
                                color = if (isSelected) Color(0xFF9C27B0).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFAB47BC) else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectOption(member) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.size(44.dp)) {
                                        UserAvatar(
                                            emojiOrBase64 = member.iconEmoji,
                                            size = 44.dp,
                                            fallback = member.name.take(1).uppercase()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(if (member.isOnline) SuccessGreen else Color.Gray)
                                                .border(1.5.dp, Charcoal, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = member.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            if (member.roleTitle.isNotEmpty()) {
                                                Surface(
                                                    color = Color(0xFF8E24AA).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = member.roleTitle,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFFE1BEE7),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${member.description} • ${if (member.isOnline) "Online" else "Offline"}",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Active Chat", tint = Color(0xFFAB47BC))
                                    } else {
                                        Button(
                                            onClick = { onSelectOption(member) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Chat 🔒", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
            ) {
                Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}
