package com.example.ui.components

import com.example.ui.components.YouTubeLinkParserAndRenderer
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.util.StorageHelper
import com.example.util.MediaPreviewBox
import com.example.util.MediaCompressionHelper
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.KeepNote
import com.example.ui.AppViewModel
import com.example.ui.theme.WaterBlue
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KeepNotesView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notes by viewModel.keepNotes.collectAsState()
    val syncStatus by viewModel.keepNotesSyncStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showEditorScreen by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<KeepNote?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    // Full-screen editor state (options matching Journal editor, optimized for note)
    var inputTitle by remember { mutableStateOf("") }
    var inputContentValue by remember { mutableStateOf(TextFieldValue("")) }
    var inputColorHex by remember { mutableStateOf("#202124") }
    var inputIsPinned by remember { mutableStateOf(false) }
    var inputAttachments by remember { mutableStateOf<List<String>>(emptyList()) }

    // Auto sync on screen open if user has connected Google Account
    LaunchedEffect(Unit) {
        if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) {
            viewModel.syncKeepNotes(context)
        }
    }

    // Helpers to manage recordings
    var audioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var currentAudioRecordingFile by remember { mutableStateOf<File?>(null) }

    // Permissions Request launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordOk = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraOk = permissions[Manifest.permission.CAMERA] ?: false
        if (recordOk) {
            Toast.makeText(context, "Microphone enabled", Toast.LENGTH_SHORT).show()
        }
    }

    // Capture Photo helper launcher
    var activePhotoFile by remember { mutableStateOf<File?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && activePhotoFile != null) {
            val optimizedFile = MediaCompressionHelper.compressImageFile(context, activePhotoFile!!)
            inputAttachments = inputAttachments + "photo:${optimizedFile.absolutePath}"
            Toast.makeText(context, "Photo attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Capture Video helper launcher
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && activeVideoFile != null) {
            inputAttachments = inputAttachments + "video:${activeVideoFile!!.absolutePath}"
            Toast.makeText(context, "Video attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Attach File helper launcher
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val copiedFile = StorageHelper.copyFileToInternalSandbox(context, uri)
            if (copiedFile != null) {
                inputAttachments = inputAttachments + "file:${copiedFile.name}|path:${copiedFile.absolutePath}"
                Toast.makeText(context, "Attached document: ${copiedFile.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Save and Close helper function
    val saveAndClose = {
        val contentWithAttachments = if (inputAttachments.isNotEmpty()) {
            "${inputContentValue.text}\n[Attachments: ${inputAttachments.joinToString(";;")}]"
        } else {
            inputContentValue.text
        }

        if (inputTitle.isNotBlank() || inputContentValue.text.isNotBlank() || inputAttachments.isNotEmpty()) {
            val currentEdit = noteToEdit
            if (currentEdit == null) {
                viewModel.insertKeepNote(
                    title = inputTitle,
                    content = contentWithAttachments,
                    colorHex = inputColorHex,
                    isPinned = inputIsPinned
                )
            } else {
                viewModel.updateKeepNote(
                    currentEdit.copy(
                        title = inputTitle,
                        content = contentWithAttachments,
                        colorHex = inputColorHex,
                        isPinned = inputIsPinned
                    )
                )
            }
        }
        showEditorScreen = false
    }

    // Handle system Back button press
    BackHandler(enabled = showEditorScreen) {
        saveAndClose()
    }

    // Standard dark mode palette
    val keepColors = listOf(
        "#202124" to "Charcoal",
        "#5c2b29" to "Red",
        "#614a19" to "Orange",
        "#635d19" to "Yellow",
        "#345920" to "Green",
        "#16504b" to "Teal",
        "#2d555e" to "Blue",
        "#1e3a5f" to "Dark Blue",
        "#42275e" to "Purple",
        "#5b2245" to "Pink",
        "#442f19" to "Brown",
        "#3c3f41" to "Grey"
    )

    // Filtered lists
    val filteredNotes = notes.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
    }

    val pinnedNotes = filteredNotes.filter { it.isPinned }
    val otherNotes = filteredNotes.filter { !it.isPinned }

    if (showEditorScreen) {
        // FULL SCREEN WHOLE DISPLAY NOTE EDITOR (Optimized: No date and time)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF06070D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { saveAndClose() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Save and Back", tint = Color.White)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Autosaved indicator block
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Green)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Autosaved", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Floating Pin option
                        IconButton(onClick = { inputIsPinned = !inputIsPinned }) {
                            Icon(
                                imageVector = if (inputIsPinned) Icons.Default.PushPin else Icons.Default.PinDrop,
                                contentDescription = "Pin Note",
                                tint = if (inputIsPinned) WaterBlue else Color.White.copy(alpha = 0.4f)
                            )
                        }

                        // Export dropdown trigger
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export options",
                                    tint = WaterBlue
                                )
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false },
                                modifier = Modifier.background(Color(0xFF13141C))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export to Google Doc 📄", color = Color.White) },
                                    onClick = {
                                        showExportMenu = false
                                        val tempNote = KeepNote(
                                            id = noteToEdit?.id ?: 0,
                                            title = inputTitle,
                                            content = if (inputAttachments.isNotEmpty()) {
                                                "${inputContentValue.text}\n[Attachments: ${inputAttachments.joinToString(";;")}]"
                                            } else {
                                                inputContentValue.text
                                            },
                                            timestamp = noteToEdit?.timestamp ?: System.currentTimeMillis(),
                                            isPinned = inputIsPinned,
                                            colorHex = inputColorHex,
                                            isSynced = noteToEdit?.isSynced ?: false
                                        )
                                        viewModel.exportNoteToGoogleDoc(context, tempNote) { success, link ->
                                            if (success) {
                                                Toast.makeText(context, "Doc created successfully!", Toast.LENGTH_SHORT).show()
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Link: $link", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Export failed: $link", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to Google Sheet 📊", color = Color.White) },
                                    onClick = {
                                        showExportMenu = false
                                        val tempNote = KeepNote(
                                            id = noteToEdit?.id ?: 0,
                                            title = inputTitle,
                                            content = if (inputAttachments.isNotEmpty()) {
                                                "${inputContentValue.text}\n[Attachments: ${inputAttachments.joinToString(";;")}]"
                                            } else {
                                                inputContentValue.text
                                            },
                                            timestamp = noteToEdit?.timestamp ?: System.currentTimeMillis(),
                                            isPinned = inputIsPinned,
                                            colorHex = inputColorHex,
                                            isSynced = noteToEdit?.isSynced ?: false
                                        )
                                        viewModel.exportNoteToGoogleSheet(context, tempNote) { success, link ->
                                            if (success) {
                                                Toast.makeText(context, "Sheet created successfully!", Toast.LENGTH_SHORT).show()
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Link: $link", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Export failed: $link", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Trash option
                        IconButton(onClick = {
                            val currentEdit = noteToEdit
                            if (currentEdit != null) {
                                viewModel.deleteKeepNote(currentEdit)
                            }
                            showEditorScreen = false
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Discard Note", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Heading/Title
                    TextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        placeholder = { Text("Title", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )

                    // Note content body TextField Value
                    TextField(
                        value = inputContentValue,
                        onValueChange = { inputContentValue = it },
                        placeholder = { Text("Take a note...", color = Color.Gray, fontSize = 15.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                            .testTag("note_content_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    YouTubeLinkParserAndRenderer(text = inputContentValue.text)
                    InstagramLinkParserAndRenderer(text = inputContentValue.text)

                    // Real-Time Checkable Checklist Preview inside Note editor if checklist markdown is found
                    val hasChecklist = remember(inputContentValue.text) {
                        inputContentValue.text.contains(Regex("""^\s*[-*]?\s*\[[ xX]\]""", RegexOption.MULTILINE))
                    }
                    if (hasChecklist) {
                        Text("INTERACTIVE CHECKLIST", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                RenderChecklistOrText(
                                    content = inputContentValue.text,
                                    onContentChange = { updated ->
                                        inputContentValue = inputContentValue.copy(text = updated)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // RENDER DETAILED MEDIA ATTACHMENTS PREVIEW RIGHT IN EDITOR AS REQUESTED
                    if (inputAttachments.isNotEmpty()) {
                        Text("ATTACHMENTS (${inputAttachments.size})", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        inputAttachments.forEach { attach ->
                            KeepNoteMediaItem(
                                context = context,
                                attach = attach,
                                isEditing = true,
                                onDelete = {
                                    inputAttachments = inputAttachments.filter { it != attach }
                                }
                            )
                        }
                    }
                }

                // Choose Card Background Color row
                Text("Card Background Color", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keepColors.forEach { (colorHexCode, name) ->
                        val isSelected = inputColorHex.equals(colorHexCode, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHexCode)))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) WaterBlue else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { inputColorHex = colorHexCode }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toolbar panel (Same rich options as journal, optimized for no date and time)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13141C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold editor format text option
                        IconButton(onClick = {
                            val selStart = inputContentValue.selection.start
                            val selEnd = inputContentValue.selection.end
                            val original = inputContentValue.text
                            val newText = if (selStart != selEnd) {
                                original.substring(0, selStart) + "**" + original.substring(selStart, selEnd) + "**" + original.substring(selEnd)
                            } else {
                                original.substring(0, selStart) + "**bold**" + original.substring(selStart)
                            }
                            inputContentValue = TextFieldValue(text = newText, selection = TextRange(selStart + 2, selEnd + 6))
                        }) {
                            Text("B", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        // Point wise list option
                        IconButton(onClick = {
                            val selStart = inputContentValue.selection.start
                            val original = inputContentValue.text
                            val newText = original.substring(0, selStart) + "\n• " + original.substring(selStart)
                            inputContentValue = TextFieldValue(text = newText, selection = TextRange(selStart + 3))
                        }) {
                            Text("• List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // Checklist format option
                        IconButton(onClick = {
                            val selStart = inputContentValue.selection.start
                            val original = inputContentValue.text
                            val newText = original.substring(0, selStart) + "\n- [ ] " + original.substring(selStart)
                            inputContentValue = TextFieldValue(text = newText, selection = TextRange(selStart + 7))
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckBox, contentDescription = "Add Checklist Item", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Checklist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }

                        // Snaps direct internal photo option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outPhotoFile = File(StorageHelper.getAppFilesDir(context), "note_photo_${System.currentTimeMillis()}.jpg")
                                activePhotoFile = outPhotoFile
                                val photoUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outPhotoFile)
                                try {
                                    takePhotoLauncher.launch(photoUri)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, "No camera application found to take photos.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to open camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Snapper", tint = Color.White)
                        }

                        // Snaps direct internal video option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outVideoFile = File(StorageHelper.getAppFilesDir(context), "note_video_${System.currentTimeMillis()}.mp4")
                                activeVideoFile = outVideoFile
                                val videoUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outVideoFile)
                                try {
                                    captureVideoLauncher.launch(videoUri)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, "No video recorder application found to record videos.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to open video recorder: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Recorder", tint = Color.White)
                        }

                        // Record voice audio option
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else {
                                    if (isRecordingAudio) {
                                        // Stop recording
                                        try {
                                            audioRecorder?.stop()
                                            audioRecorder?.release()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        audioRecorder = null
                                        isRecordingAudio = false
                                        val rec = currentAudioRecordingFile
                                        if (rec != null) {
                                            val compressedFile = File(rec.parentFile, "${rec.name}.gz")
                                            val compressSuccess = MediaCompressionHelper.compressFileGzip(rec, compressedFile)
                                            val finalAudioFile = if (compressSuccess) {
                                                rec.delete()
                                                compressedFile
                                            } else {
                                                rec
                                            }
                                            inputAttachments = inputAttachments + "audio:${finalAudioFile.absolutePath}"
                                            Toast.makeText(context, "Voice memo attached & compressed!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Start recording code
                                        val recFile = File(StorageHelper.getAppFilesDir(context), "note_voice_${System.currentTimeMillis()}.mp3")
                                        currentAudioRecordingFile = recFile
                                        try {
                                            audioRecorder = MediaRecorder().apply {
                                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                setAudioEncodingBitRate(32000)
                                                setAudioSamplingRate(16000)
                                                setOutputFile(recFile.absolutePath)
                                                prepare()
                                                start()
                                            }
                                            isRecordingAudio = true
                                            Toast.makeText(context, "🎙️ Recording audio (Tap mic to stop)...", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Failure preparing recorder", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice Memo Record",
                                tint = if (isRecordingAudio) Color.Red else Color.LightGray
                            )
                        }

                        // Document selector attachment option
                        IconButton(onClick = {
                            pickDocumentLauncher.launch("*/*")
                        }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    } else {
        // NOTES DASHBOARD GRID LIST VIEW
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF06070D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Notes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Personal Local-First Notes",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (syncStatus.isNotEmpty() && syncStatus != "Idle") {
                            Text(
                                text = if (syncStatus.length > 25) syncStatus.take(25) + "..." else syncStatus,
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.syncKeepNotes(context)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Notes",
                                tint = WaterBlue
                            )
                        }
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your notes...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("notes_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "No Notes",
                                tint = WaterBlue.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isEmpty()) "No Notes Yet" else "No matching notes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (searchQuery.isEmpty()) "Tap + to capture checklists, voice recordings, and live photos!" else "Try searching for another keyword",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                                Text(
                                    "PINNED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(pinnedNotes) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = {
                                        noteToEdit = note
                                        inputTitle = note.title
                                        inputColorHex = note.colorHex
                                        inputIsPinned = note.isPinned

                                        // Parse attachments list from content
                                        val match = Regex("""\[Attachments: ([^\]]+)\]""").find(note.content)
                                        val attachmentsList = if (match != null) {
                                            match.groupValues[1].split(";;").filter { it.isNotEmpty() }
                                        } else {
                                            val oldMatch = Regex("""\[Attachment: ([^\]]+)\]""").find(note.content)
                                            val oldAtt = oldMatch?.groupValues?.get(1)?.trim() ?: ""
                                            if (oldAtt.isNotEmpty()) {
                                                val lower = oldAtt.lowercase()
                                                val typePrefix = when {
                                                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "photo:"
                                                    lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".mkv") -> "video:"
                                                    lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".aac") -> "audio:"
                                                    else -> "file:${oldAtt}|path:"
                                                }
                                                val sandboxFile = java.io.File(context.filesDir, oldAtt)
                                                if (sandboxFile.exists()) {
                                                    listOf("${typePrefix}${sandboxFile.absolutePath}")
                                                } else {
                                                    listOf("${typePrefix}${oldAtt}")
                                                }
                                            } else {
                                                emptyList()
                                            }
                                        }
                                        inputAttachments = attachmentsList

                                        // Clean content
                                        val cleanContent = note.content
                                            .replace(Regex("""\n?\[Attachments: [^\]]+\]"""), "")
                                            .replace(Regex("""\n?\[Attachment: [^\]]+\]"""), "")
                                            .trim()

                                        inputContentValue = TextFieldValue(text = cleanContent)
                                        showEditorScreen = true
                                    },
                                    onPinClick = {
                                        viewModel.updateKeepNote(note.copy(isPinned = !note.isPinned))
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteKeepNote(note)
                                    },
                                    onNoteUpdate = { updatedNote ->
                                        viewModel.updateKeepNote(updatedNote)
                                    }
                                )
                            }
                        }

                        if (otherNotes.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                                Text(
                                    if (pinnedNotes.isNotEmpty()) "OTHERS" else "NOTES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(otherNotes) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = {
                                        noteToEdit = note
                                        inputTitle = note.title
                                        inputColorHex = note.colorHex
                                        inputIsPinned = note.isPinned

                                        // Parse attachments list from content
                                        val match = Regex("""\[Attachments: ([^\]]+)\]""").find(note.content)
                                        val attachmentsList = if (match != null) {
                                            match.groupValues[1].split(";;").filter { it.isNotEmpty() }
                                        } else {
                                            val oldMatch = Regex("""\[Attachment: ([^\]]+)\]""").find(note.content)
                                            val oldAtt = oldMatch?.groupValues?.get(1)?.trim() ?: ""
                                            if (oldAtt.isNotEmpty()) {
                                                val lower = oldAtt.lowercase()
                                                val typePrefix = when {
                                                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "photo:"
                                                    lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".mkv") -> "video:"
                                                    lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".aac") -> "audio:"
                                                    else -> "file:${oldAtt}|path:"
                                                }
                                                val sandboxFile = java.io.File(context.filesDir, oldAtt)
                                                if (sandboxFile.exists()) {
                                                    listOf("${typePrefix}${sandboxFile.absolutePath}")
                                                } else {
                                                    listOf("${typePrefix}${oldAtt}")
                                                }
                                            } else {
                                                emptyList()
                                            }
                                        }
                                        inputAttachments = attachmentsList

                                        // Clean content
                                        val cleanContent = note.content
                                            .replace(Regex("""\n?\[Attachments: [^\]]+\]"""), "")
                                            .replace(Regex("""\n?\[Attachment: [^\]]+\]"""), "")
                                            .trim()

                                        inputContentValue = TextFieldValue(text = cleanContent)
                                        showEditorScreen = true
                                    },
                                    onPinClick = {
                                        viewModel.updateKeepNote(note.copy(isPinned = !note.isPinned))
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteKeepNote(note)
                                    },
                                    onNoteUpdate = { updatedNote ->
                                        viewModel.updateKeepNote(updatedNote)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // FAB to add new Note
            FloatingActionButton(
                onClick = {
                    noteToEdit = null
                    inputTitle = ""
                    inputContentValue = TextFieldValue("")
                    inputAttachments = emptyList()
                    inputColorHex = "#202124"
                    inputIsPinned = false
                    showEditorScreen = true
                },
                containerColor = WaterBlue,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_note_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    }
}

@Composable
fun NoteCard(
    note: KeepNote,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNoteUpdate: ((KeepNote) -> Unit)? = null
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(note.colorHex))
    } catch (e: Exception) {
        Color(0xFF202124)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.1f))
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Website preview custom banner (from original code, extremely responsive!)
            if (!note.customLogoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black.copy(alpha = 0.15f))
                ) {
                    AsyncImage(
                        model = note.customLogoUrl,
                        contentDescription = "Website Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    val isYouTube = note.websiteUrl?.contains("youtube", ignoreCase = true) == true ||
                            note.websiteUrl?.contains("youtu.be", ignoreCase = true) == true

                    if (isYouTube) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube Link",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    note.websiteUrl?.let { url ->
                        val domain = try {
                            android.net.Uri.parse(url).host ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        if (domain.isNotEmpty()) {
                            Text(
                                text = domain,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header row of Card (Title + Pin action)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (note.title.isNotEmpty()) {
                        Text(
                            text = note.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.PinDrop,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) WaterBlue else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Parse attachments list from content
                val parsedAttachments = remember(note.content) {
                    val match = Regex("""\[Attachments: ([^\]]+)\]""").find(note.content)
                    if (match != null) {
                        match.groupValues[1].split(";;").filter { it.isNotEmpty() }
                    } else {
                        val oldMatch = Regex("""\[Attachment: ([^\]]+)\]""").find(note.content)
                        val oldAtt = oldMatch?.groupValues?.get(1)?.trim() ?: ""
                        if (oldAtt.isNotEmpty()) listOf(oldAtt) else emptyList()
                    }
                }

                val cleanContent = remember(note.content) {
                    note.content
                        .replace(Regex("""\n?\[Attachments: [^\]]+\]"""), "")
                        .replace(Regex("""\n?\[Attachment: [^\]]+\]"""), "")
                        .trim()
                }

                // Render note text/checklist inside the card
                if (cleanContent.isNotEmpty()) {
                    RenderChecklistOrText(
                        content = cleanContent,
                        onContentChange = if (onNoteUpdate != null) { updatedClean ->
                            val match = Regex("""(\n?\[Attachments: [^\]]+\]|\n?\[Attachment: [^\]]+\])""").find(note.content)
                            val attachmentSuffix = match?.value ?: ""
                            val updatedFullContent = if (attachmentSuffix.isNotEmpty()) {
                                "$updatedClean\n$attachmentSuffix"
                            } else {
                                updatedClean
                            }
                            onNoteUpdate(note.copy(content = updatedFullContent, timestamp = System.currentTimeMillis()))
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                YouTubeLinkParserAndRenderer(text = cleanContent)
                InstagramLinkParserAndRenderer(text = cleanContent)

                // Render first attachment as high quality hero card inside grid
                if (parsedAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val firstAttach = parsedAttachments.first()
                    val previewType = remember(firstAttach) {
                        val nameLower = firstAttach.lowercase()
                        when {
                            nameLower.contains("photo:") || nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") -> "image"
                            nameLower.contains("video:") || nameLower.endsWith(".mp4") || nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv") -> "video"
                            nameLower.contains("audio:") || nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac") -> "audio"
                            else -> "others"
                        }
                    }
                    val path = remember(firstAttach) {
                        when {
                            firstAttach.startsWith("photo:") -> firstAttach.removePrefix("photo:")
                            firstAttach.startsWith("video:") -> firstAttach.removePrefix("video:")
                            firstAttach.startsWith("audio:") -> firstAttach.removePrefix("audio:")
                            firstAttach.startsWith("file:") -> {
                                val filePart = firstAttach.removePrefix("file:").split("|path:")
                                filePart.getOrNull(1) ?: ""
                            }
                            else -> firstAttach
                        }
                    }
                    MediaPreviewBox(
                        pathOrName = path,
                        type = previewType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )

                    if (parsedAttachments.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${parsedAttachments.size - 1} more attachments",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row with Delete actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenderChecklistOrText(
    content: String,
    onContentChange: ((String) -> Unit)? = null
) {
    val lines = content.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { index, line ->
            val checkboxMatch = Regex("""^\s*([-*]|\d+\.)?\s*\[([ xX])\]\s*(.*)$""").find(line)
            if (checkboxMatch != null) {
                val isChecked = checkboxMatch.groupValues[2].trim().lowercase() == "x"
                val text = checkboxMatch.groupValues[3]

                val toggleItem: () -> Unit = {
                    if (onContentChange != null) {
                        val newChar = if (isChecked) " " else "x"
                        val newLine = line.replaceFirst(Regex("""\[([ xX])\]"""), "[$newChar]")
                        val newLines = lines.toMutableList().apply { set(index, newLine) }
                        onContentChange(newLines.joinToString("\n"))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onContentChange != null) {
                                Modifier.clickable { toggleItem() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = if (onContentChange != null) { { toggleItem() } } else null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = WaterBlue,
                            uncheckedColor = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        color = if (isChecked) Color.Gray else Color.White,
                        fontSize = 14.sp,
                        style = LocalTextStyle.current.copy(
                            textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                        )
                    )
                }
            } else {
                if (line.trim().isNotEmpty()) {
                    Text(
                        text = line,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeepNoteMediaItem(
    context: Context,
    attach: String,
    isEditing: Boolean,
    onDelete: () -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    val path = remember(attach) {
        when {
            attach.startsWith("photo:") -> attach.removePrefix("photo:")
            attach.startsWith("video:") -> attach.removePrefix("video:")
            attach.startsWith("audio:") -> attach.removePrefix("audio:")
            attach.startsWith("file:") -> {
                val filePart = attach.removePrefix("file:").split("|path:")
                filePart.getOrNull(1) ?: ""
            }
            else -> attach
        }
    }
    val isWebUrl = remember(path) { path.startsWith("http://") || path.startsWith("https://") }
    val file = remember(path, isWebUrl) { if (isWebUrl) java.io.File("") else java.io.File(path) }
    val displayName = remember(file, attach, isWebUrl) {
        if (attach.startsWith("file:")) {
            val filePart = attach.removePrefix("file:").split("|path:")
            filePart.getOrNull(0) ?: "Attached Document"
        } else if (isWebUrl) {
            path.substringAfterLast("/").substringBefore("?")
        } else {
            file.name.substringAfter("note_").substringAfter("photo_").substringAfter("video_").substringAfter("voice_")
        }
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Attachment Options", color = Color.White) },
            text = { Text("Choose action for '$displayName':", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showOptionsDialog = false
                        if (isWebUrl) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val success = saveFileToDownloads(context, file, displayName)
                            if (success) {
                                Toast.makeText(context, "Saved to Downloads!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text(if (isWebUrl) "Open in Browser" else "Save to Device")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOptionsDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            containerColor = Color(0xFF13141C)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    try {
                        if (isWebUrl) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            val authority = "${context.packageName}.fileprovider"
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                val extension = file.extension.lowercase()
                                val type = when (extension) {
                                    "jpg", "jpeg", "png", "webp" -> "image/*"
                                    "mp4", "mov", "3gp", "mkv" -> "video/*"
                                    "mp3", "wav", "m4a", "gz" -> "audio/*"
                                    "pdf" -> "application/pdf"
                                    else -> "*/*"
                                }
                                setDataAndType(uri, type)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open Attachment"))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClick = {
                    showOptionsDialog = true
                }
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val type = when {
                attach.startsWith("photo:") -> "image"
                attach.startsWith("video:") -> "video"
                attach.startsWith("audio:") -> "audio"
                else -> "others"
            }
            MediaPreviewBox(
                pathOrName = path,
                type = type,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = type.uppercase(),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            if (isEditing) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}

private fun saveFileToDownloads(context: Context, sourceFile: File, displayName: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "3gp" -> "video/3gp"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val downloadsUri = android.net.Uri.parse("content://media/external/downloads")
            resolver.insert(downloadsUri, contentValues)
        } else {
            null
        }
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
