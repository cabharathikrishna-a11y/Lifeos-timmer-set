package com.example.ui.components

import com.example.ui.components.YouTubeLinkParserAndRenderer
import com.example.util.rememberVideoThumbnail
import com.example.util.rememberPdfFirstPagePreview
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.JournalEntry
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import com.example.util.MediaCompressionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.BiasAlignment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.gms.maps.CameraUpdateFactory

data class LocalMediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val type: String, // "image", "video", "audio"
    val duration: String? = null,
    val dateAdded: Long
)

private fun formatLocalMediaDuration(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val remainingSec = sec % 60
    return String.format(java.util.Locale.US, "%d:%02d", min, remainingSec)
}

private fun checkLocalMediaPermissions(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun resolveAuthorName(context: android.content.Context): String {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val currentUsername = prefs.getString("username", "") ?: ""
    val cachedNickname = if (currentUsername.isNotEmpty()) prefs.getString("user_nickname_$currentUsername", "") ?: "" else ""
    val directNickname = prefs.getString("user_nickname", "") ?: ""
    val cachedName = if (currentUsername.isNotEmpty()) prefs.getString("user_name_$currentUsername", "") ?: "" else ""
    val directName = prefs.getString("user_name", "") ?: ""

    val nickname = listOf(cachedNickname, directNickname, cachedName, directName)
        .firstOrNull { !it.isNullOrBlank() }

    if (!nickname.isNullOrBlank()) {
        return nickname
    }

    val currentAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
    val googleName = currentAccount?.displayName
    if (!googleName.isNullOrBlank()) {
        return googleName
    }
    val googleEmail = currentAccount?.email
    if (!googleEmail.isNullOrBlank()) {
        return googleEmail
    }

    return "Study Group Member"
}

fun createAndPackageSharedJournalFolder(
    context: android.content.Context,
    groupId: String,
    entry: JournalEntry,
    authorName: String
): Pair<String, String> {
    val sanitizeGroupId = groupId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val folderDir = java.io.File(context.filesDir, "shared_journal_folders/$sanitizeGroupId/entry_${entry.id}")
    if (!folderDir.exists()) {
        folderDir.mkdirs()
    }

    // 1. Create main text file containing text, date, and author metadata
    val textFile = java.io.File(folderDir, "entry_details.txt")
    val sb = StringBuilder()
    sb.append("TITLE: ").append(entry.title).append("\n")
    sb.append("DATE: ").append(entry.dateString).append("\n")
    sb.append("TIMESTAMP: ").append(entry.timestamp).append("\n")
    sb.append("AUTHOR: ").append(authorName).append("\n")
    sb.append("GROUP: ").append(sanitizeGroupId).append("\n")
    sb.append("ATTACHMENTS: ").append(entry.attachmentsJson).append("\n")
    sb.append("--- BODY TEXT ---\n")
    sb.append(entry.text)
    textFile.writeText(sb.toString())

    // 2. Create structured meta.json for quick folder indexing
    val metaFile = java.io.File(folderDir, "meta.json")
    val safeTitle = entry.title.replace("\"", "\\\"").replace("\n", " ")
    val safeAuthor = authorName.replace("\"", "\\\"").replace("\n", " ")
    val safeAttachments = entry.attachmentsJson.replace("\"", "\\\"")
    val metaJson = """
        {
          "id": ${entry.id},
          "title": "$safeTitle",
          "dateString": "${entry.dateString}",
          "timestamp": ${entry.timestamp},
          "author": "$safeAuthor",
          "groupId": "$sanitizeGroupId",
          "attachmentsJson": "$safeAttachments",
          "folderPath": "shared_journal_folders/$sanitizeGroupId/entry_${entry.id}"
        }
    """.trimIndent()
    metaFile.writeText(metaJson)

    // 3. Ensure attachments directory exists inside entry folder
    val attachmentsDir = java.io.File(folderDir, "attachments")
    if (!attachmentsDir.exists()) {
        attachmentsDir.mkdirs()
    }

    val folderLink = "file://${folderDir.absolutePath}"
    val relativeFolderPath = "shared_journal_folders/$sanitizeGroupId/entry_${entry.id}"
    return Pair(folderLink, relativeFolderPath)
}

fun syncSharedJournalEntryToRtdb(
    context: android.content.Context,
    groupId: String,
    entry: JournalEntry
) {
    if (groupId == "Personal Journal") return
    try {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val dbUrl = prefs.getString("custom_firebase_db_url", com.example.api.FirebaseConfig.DATABASE_URL) ?: com.example.api.FirebaseConfig.DATABASE_URL
        val sanitizeGroupId = groupId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
        
        val authorName = resolveAuthorName(context)
        
        // Package entry into a structured folder with files (entry_details.txt, meta.json, attachments/)
        val (folderLink, relativeFolderPath) = createAndPackageSharedJournalFolder(context, groupId, entry, authorName)

        val attachmentsWithAuthor = if (!entry.attachmentsJson.contains("author:")) {
            if (entry.attachmentsJson.isNotEmpty()) "${entry.attachmentsJson};;author:$authorName" else "author:$authorName"
        } else {
            entry.attachmentsJson
        }

        val attachmentsWithFolder = if (!attachmentsWithAuthor.contains("folderLink:")) {
            if (attachmentsWithAuthor.isNotEmpty()) "$attachmentsWithAuthor;;folderLink:$folderLink" else "folderLink:$folderLink"
        } else attachmentsWithAuthor

        val snippet = if (entry.text.length > 80) entry.text.take(80) + "..." else entry.text
        
        // Store lightweight metadata & folder link reference in RTDB instead of long data branches
        val map = mapOf(
            "id" to entry.id,
            "title" to entry.title,
            "text" to snippet, // Short snippet stored in RTDB node
            "textSnippet" to snippet,
            "dateString" to entry.dateString,
            "timestamp" to entry.timestamp,
            "attachmentsJson" to attachmentsWithFolder,
            "author" to authorName,
            "groupId" to sanitizeGroupId,
            "folderLink" to folderLink,
            "folderPath" to relativeFolderPath,
            "hasFolderPayload" to true
        )
        database.getReference("shared_journals/$sanitizeGroupId/entries/${entry.id}").setValue(map)
    } catch (e: Exception) {
        android.util.Log.e("JournalBookView", "Failed to sync to RTDB", e)
    }
}

fun deleteSharedJournalEntryFromRtdb(
    context: android.content.Context,
    groupId: String,
    entryId: Int
) {
    if (groupId == "Personal Journal") return
    try {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val dbUrl = prefs.getString("custom_firebase_db_url", com.example.api.FirebaseConfig.DATABASE_URL) ?: com.example.api.FirebaseConfig.DATABASE_URL
        val sanitizeGroupId = groupId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
        database.getReference("shared_journals/$sanitizeGroupId/entries/$entryId").removeValue()

        // Clean up local entry folder
        val folderDir = java.io.File(context.filesDir, "shared_journal_folders/$sanitizeGroupId/entry_$entryId")
        if (folderDir.exists()) {
            folderDir.deleteRecursively()
        }
    } catch (e: Exception) {
        android.util.Log.e("JournalBookView", "Failed to delete from RTDB", e)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalBookView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
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
    val localEntries by viewModel.journalEntries.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()
    var isOnThisDayReminderDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var selectedJournalScope by remember { mutableStateOf("Personal Journal") }
    var customGroupScopes by remember { mutableStateOf<List<String>>(listOf("Study Group Shared Journal")) }
    var showAddCustomGroupDialog by remember { mutableStateOf(false) }
    var newCustomGroupName by remember { mutableStateOf("") }

    val sharedRtdbEntries = remember { mutableStateListOf<JournalEntry>() }

    DisposableEffect(selectedJournalScope) {
        if (selectedJournalScope != "Personal Journal") {
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val dbUrl = prefs.getString("custom_firebase_db_url", com.example.api.FirebaseConfig.DATABASE_URL) ?: com.example.api.FirebaseConfig.DATABASE_URL
            val sanitizeGroupId = selectedJournalScope.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val ref = database.getReference("shared_journals/$sanitizeGroupId/entries")
                val listener = object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val list = mutableListOf<JournalEntry>()
                        for (child in snapshot.children) {
                            try {
                                val id = child.child("id").getValue(Long::class.java)?.toInt() 
                                    ?: child.key?.hashCode() ?: 0
                                val title = child.child("title").getValue(String::class.java) ?: ""
                                val rtdbText = child.child("text").getValue(String::class.java) ?: ""
                                val textSnippet = child.child("textSnippet").getValue(String::class.java) ?: ""
                                val dateString = child.child("dateString").getValue(String::class.java) ?: ""
                                val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                                val attachmentsJson = child.child("attachmentsJson").getValue(String::class.java) ?: ""
                                val author = child.child("author").getValue(String::class.java) ?: ""
                                val folderLink = child.child("folderLink").getValue(String::class.java) ?: ""
                                val folderPath = child.child("folderPath").getValue(String::class.java) ?: ""
                                
                                var fullText = if (rtdbText.isNotEmpty()) rtdbText else textSnippet

                                // Attempt to load complete entry body text from entry_details.txt inside folder
                                val targetFolder = if (folderPath.isNotEmpty()) {
                                    java.io.File(context.filesDir, folderPath)
                                } else if (folderLink.startsWith("file://")) {
                                    java.io.File(folderLink.removePrefix("file://"))
                                } else null

                                if (targetFolder != null && targetFolder.exists()) {
                                    val detailsFile = java.io.File(targetFolder, "entry_details.txt")
                                    if (detailsFile.exists()) {
                                        val fileContent = detailsFile.readText()
                                        val marker = "--- BODY TEXT ---\n"
                                        if (fileContent.contains(marker)) {
                                            fullText = fileContent.substringAfter(marker)
                                        } else {
                                            fullText = fileContent
                                        }
                                    }
                                } else if (targetFolder != null) {
                                    // Cache folder file locally for smooth viewing
                                    try {
                                        targetFolder.mkdirs()
                                        val detailsFile = java.io.File(targetFolder, "entry_details.txt")
                                        detailsFile.writeText("TITLE: $title\nDATE: $dateString\nAUTHOR: $author\n--- BODY TEXT ---\n$fullText")
                                    } catch (e: Exception) {
                                        android.util.Log.e("JournalBookView", "Failed to write local folder cache", e)
                                    }
                                }

                                val combinedAttachments = if (author.isNotEmpty() && !attachmentsJson.contains("author:")) {
                                    if (attachmentsJson.isNotEmpty()) "$attachmentsJson;;author:$author" else "author:$author"
                                } else attachmentsJson

                                val attachmentsWithFolder = if (folderLink.isNotEmpty() && !combinedAttachments.contains("folderLink:")) {
                                    if (combinedAttachments.isNotEmpty()) "$combinedAttachments;;folderLink:$folderLink" else "folderLink:$folderLink"
                                } else combinedAttachments

                                list.add(JournalEntry(id, title, fullText, dateString, timestamp, attachmentsWithFolder))
                            } catch (e: Exception) {
                                android.util.Log.e("JournalBookView", "Error parsing shared entry", e)
                            }
                        }
                        sharedRtdbEntries.clear()
                        sharedRtdbEntries.addAll(list.sortedByDescending { it.timestamp })
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                }
                ref.addValueEventListener(listener)
                onDispose {
                    ref.removeEventListener(listener)
                }
            } catch (e: Exception) {
                onDispose {}
            }
        } else {
            onDispose {}
        }
    }

    val entries = if (selectedJournalScope == "Personal Journal") {
        localEntries
    } else {
        sharedRtdbEntries
    }

    var isSidebarExpanded by remember { mutableStateOf(false) }
    val defaultJournalView by viewModel.defaultJournalView.collectAsState()
    var currentJournalTab by remember(defaultJournalView) { mutableStateOf(defaultJournalView) }

    var selectedOnThisDayDayMonth by remember { mutableStateOf(SimpleDateFormat("MM-dd", Locale.US).format(Date())) }
    var mapScrollToDate by remember { mutableStateOf<String?>(null) }

    // Custom Full-Screen Editor State
    var showEditorScreen by remember { mutableStateOf(false) }
    var activeEditingEntryId by remember { mutableStateOf<Int?>(null) }
    var editingTitle by remember { mutableStateOf("") }
    var editingTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var editingDate by remember { mutableStateOf("") }
    var editingTime by remember { mutableStateOf("") }
    var editingAttachments by remember { mutableStateOf<List<String>>(emptyList()) }

    // Rich Text Editor Extra States
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showWebLinkDialog by remember { mutableStateOf(false) }
    var showJournalLinkDialog by remember { mutableStateOf(false) }
    var showVoiceJournalDialog by remember { mutableStateOf(false) }
    var isDialogRecording by remember { mutableStateOf(false) }
    var dialogAudioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var dialogRecordingFile by remember { mutableStateOf<File?>(null) }
    var dialogRecordingSeconds by remember { mutableStateOf(0) }
    var isTranscribingVoiceNote by remember { mutableStateOf(false) }
    var isEditorPreviewMode by remember { mutableStateOf(false) }

    var viewingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    val timelineListState = rememberLazyListState()
    val monthlyListState = rememberLazyListState(initialFirstVisibleItemIndex = 60)

    val extSelectedJournalId by viewModel.selectedJournalId.collectAsState()
    LaunchedEffect(extSelectedJournalId) {
        extSelectedJournalId?.let { idVal ->
            val entry = entries.find { it.id == idVal }
            if (entry != null) {
                activeEditingEntryId = entry.id
                editingTitle = entry.title
                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                editingDate = entry.dateString
                editingTime = try {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                } catch (e: Exception) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                }
                editingAttachments = if (entry.attachmentsJson.isNotEmpty()) {
                    entry.attachmentsJson.split(";;")
                } else {
                    emptyList()
                }
                showEditorScreen = true
                viewingEntry = null
                viewModel.clearSelectedJournalId()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = showEditorScreen || viewingEntry != null) {
        if (showEditorScreen) {
            showEditorScreen = false
        } else if (viewingEntry != null) {
            viewingEntry = null
        }
    }

    // Helpers to manage recordings
    var audioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var currentAudioRecordingFile by remember { mutableStateOf<File?>(null) }

    // Launches to request missing camera/mic/location permissions
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordOk = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraOk = permissions[Manifest.permission.CAMERA] ?: false
        val locationOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
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
            editingAttachments = editingAttachments + "photo:${optimizedFile.absolutePath}"
        }
    }

    // Capture Video helper launcher
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && activeVideoFile != null) {
            editingAttachments = editingAttachments + "video:${activeVideoFile!!.absolutePath}"
        }
    }

    // Attach File helper launcher
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val copiedFile = copyFileToInternalSandbox(context, uri)
            if (copiedFile != null) {
                editingAttachments = editingAttachments + "file:${copiedFile.name}|path:${copiedFile.absolutePath}"
            }
        }
    }

    // Google Photos selection state, launcher and dialog
    var showGooglePhotosDialog by remember { mutableStateOf(false) }
    val googlePhotosList by viewModel.googlePhotosList.collectAsState()
    val googlePhotosLoading by viewModel.googlePhotosLoading.collectAsState()

    var showGooglePhotosByDateDialog by remember { mutableStateOf(false) }
    val googlePhotosDateList by viewModel.googlePhotosDateList.collectAsState()
    val googlePhotosDateLoading by viewModel.googlePhotosDateLoading.collectAsState()
    var datePhotosSelectedIndex by remember { mutableStateOf(0) }

    val photosAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.fetchGooglePhotos(context)
        if (editingDate.isNotEmpty()) {
            viewModel.fetchGooglePhotosByDate(context = context, dateStr = editingDate)
        }
    }

    // Google Photos Picker API States & Launchers
    var showPickerSessionDialog by remember { mutableStateOf(false) }
    val pickerSession by viewModel.pickerSession.collectAsState()
    val pickerPhotosList by viewModel.pickerPhotosList.collectAsState()
    val pickerPhotosLoading by viewModel.pickerPhotosLoading.collectAsState()

    val pickerAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.createPickerSession(context)
    }

    LaunchedEffect(showEditorScreen, editingDate) {
        if (showEditorScreen && editingDate.isNotEmpty()) {
            viewModel.fetchGooglePhotosByDate(
                context = context,
                dateStr = editingDate,
                onAuthResolutionRequired = { intent ->
                    try {
                        photosAuthLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("JournalBookView", "Failed to launch photos auth", e)
                    }
                }
            )
        }
    }

    val localPhotos = remember { mutableStateListOf<LocalMediaItem>() }
    val localVideos = remember { mutableStateListOf<LocalMediaItem>() }
    val localAudios = remember { mutableStateListOf<LocalMediaItem>() }
    var localMediaLoading by remember { mutableStateOf(false) }
    var localMediaTypeTab by remember { mutableStateOf("Photos") } // "Photos", "Videos", "Audios"

    var hasLocalPermissionsState by remember {
        mutableStateOf(checkLocalMediaPermissions(context))
    }

    val localMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocalPermissionsState = checkLocalMediaPermissions(context)
        if (hasLocalPermissionsState) {
            Toast.makeText(context, "Local Media permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions are required to view and select local media.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(currentJournalTab, hasLocalPermissionsState) {
        if (currentJournalTab == "Local Media" && hasLocalPermissionsState) {
            localMediaLoading = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val tempPhotos = mutableListOf<LocalMediaItem>()
                val tempVideos = mutableListOf<LocalMediaItem>()
                val tempAudios = mutableListOf<LocalMediaItem>()

                // 1. Load Images
                try {
                    val imageUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val imageProjection = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Images.Media.DATE_ADDED
                    )
                    context.contentResolver.query(
                        imageUri,
                        imageProjection,
                        null,
                        null,
                        "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                        val dateCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_ADDED)
                        var count = 0
                        while (cursor.moveToNext() && count < 60) {
                            if (idCol != -1 && nameCol != -1 && dateCol != -1) {
                                val id = cursor.getLong(idCol)
                                val name = cursor.getString(nameCol) ?: "Image_$id"
                                val dateAdded = cursor.getLong(dateCol)
                                val contentUri = android.content.ContentUris.withAppendedId(imageUri, id)
                                tempPhotos.add(LocalMediaItem(id, contentUri.toString(), name, "image", null, dateAdded))
                            }
                            count++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Load Videos
                try {
                    val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val videoProjection = arrayOf(
                        android.provider.MediaStore.Video.Media._ID,
                        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Video.Media.DURATION,
                        android.provider.MediaStore.Video.Media.DATE_ADDED
                    )
                    context.contentResolver.query(
                        videoUri,
                        videoProjection,
                        null,
                        null,
                        "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(android.provider.MediaStore.Video.Media._ID)
                        val nameCol = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                        val durationCol = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
                        val dateCol = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATE_ADDED)
                        var count = 0
                        while (cursor.moveToNext() && count < 60) {
                            if (idCol != -1 && nameCol != -1 && dateCol != -1) {
                                val id = cursor.getLong(idCol)
                                val name = cursor.getString(nameCol) ?: "Video_$id"
                                val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                                val dateAdded = cursor.getLong(dateCol)
                                val contentUri = android.content.ContentUris.withAppendedId(videoUri, id)
                                val durationStr = formatLocalMediaDuration(duration)
                                tempVideos.add(LocalMediaItem(id, contentUri.toString(), name, "video", durationStr, dateAdded))
                            }
                            count++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. Load Audios
                try {
                    val audioUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val audioProjection = arrayOf(
                        android.provider.MediaStore.Audio.Media._ID,
                        android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Audio.Media.DURATION,
                        android.provider.MediaStore.Audio.Media.DATE_ADDED
                    )
                    context.contentResolver.query(
                        audioUri,
                        audioProjection,
                        null,
                        null,
                        "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                        val nameCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                        val durationCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                        val dateCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                        var count = 0
                        while (cursor.moveToNext() && count < 60) {
                            if (idCol != -1 && nameCol != -1 && dateCol != -1) {
                                val id = cursor.getLong(idCol)
                                val name = cursor.getString(nameCol) ?: "Audio_$id"
                                val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                                val dateAdded = cursor.getLong(dateCol)
                                val contentUri = android.content.ContentUris.withAppendedId(audioUri, id)
                                val durationStr = formatLocalMediaDuration(duration)
                                tempAudios.add(LocalMediaItem(id, contentUri.toString(), name, "audio", durationStr, dateAdded))
                            }
                            count++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    localPhotos.clear()
                    localPhotos.addAll(tempPhotos)
                    localVideos.clear()
                    localVideos.addAll(tempVideos)
                    localAudios.clear()
                    localAudios.addAll(tempAudios)
                    localMediaLoading = false
                }
            }
        }
    }

    if (showGooglePhotosDialog) {
        AlertDialog(
            onDismissRequest = { showGooglePhotosDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null, tint = WaterBlue)
                    Text("Import from Google Photos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (googlePhotosLoading) {
                        CircularProgressIndicator(color = WaterBlue)
                    } else if (googlePhotosList.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No photos found or Google Photos integration is not authorized.",
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.fetchGooglePhotos(context) { intent ->
                                        try {
                                            photosAuthLauncher.launch(intent)
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            Toast.makeText(context, "No web browser or application found to handle Google login.", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to launch login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                            ) {
                                Text("Connect / Refresh", color = Color.Black)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val columns = 2
                            val chunkedPhotos = googlePhotosList.chunked(columns)
                            items(chunkedPhotos) { rowPhotos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPhotos.forEach { photo ->
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1.1f)
                                                .clickable {
                                                    val highResUrl = "${photo.baseUrl}=w1024"
                                                    editingAttachments = editingAttachments + "photo:$highResUrl"
                                                    showGooglePhotosDialog = false
                                                    Toast.makeText(context, "Photo attached successfully!", Toast.LENGTH_SHORT).show()
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = photo.baseUrl,
                                                    contentDescription = photo.description ?: "Google Photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (!photo.description.isNullOrBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .align(Alignment.BottomStart)
                                                            .background(Color.Black.copy(alpha = 0.6f))
                                                            .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = photo.description ?: "",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowPhotos.size < columns) {
                                        repeat(columns - rowPhotos.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGooglePhotosDialog = false }) {
                    Text("Close", color = WaterBlue)
                }
            },
            containerColor = Color(0xFF13141C)
        )
    }

    if (showGooglePhotosByDateDialog) {
        AlertDialog(
            onDismissRequest = { showGooglePhotosByDateDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val radius = size.minDimension / 4f
                        drawArc(color = Color(0xFF4285F4), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius, 0f), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                        drawArc(color = Color(0xFFEA4335), startAngle = 270f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius * 2, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                        drawArc(color = Color(0xFFFBBC05), startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius, radius * 2), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                        drawArc(color = Color(0xFF34A853), startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(0f, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                    }
                    Text("Photos for $editingDate", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (googlePhotosDateLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = WaterBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Searching Google Photos for $editingDate...", color = Color.LightGray, fontSize = 14.sp)
                        }
                    } else if (googlePhotosDateList.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No photos found on Google Photos for $editingDate.",
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else if (datePhotosSelectedIndex >= googlePhotosDateList.size) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "All photos processed for this date!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "You've gone through all ${googlePhotosDateList.size} photos.",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val photo = googlePhotosDateList[datePhotosSelectedIndex]
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Photo ${datePhotosSelectedIndex + 1} of ${googlePhotosDateList.size}",
                                color = WaterBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = photo.baseUrl,
                                        contentDescription = photo.description ?: "Google Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (!photo.description.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomStart)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = photo.description ?: "",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Do you want to add this photo to this entry?",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!googlePhotosDateLoading && googlePhotosDateList.isNotEmpty() && datePhotosSelectedIndex < googlePhotosDateList.size) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                datePhotosSelectedIndex++
                            }
                        ) {
                            Text("No, Skip", color = Color.LightGray)
                        }
                        
                        Button(
                            onClick = {
                                val photo = googlePhotosDateList[datePhotosSelectedIndex]
                                val highResUrl = "${photo.baseUrl}=w1024"
                                if (!editingAttachments.contains("photo:$highResUrl")) {
                                    editingAttachments = editingAttachments + "photo:$highResUrl"
                                }
                                datePhotosSelectedIndex++
                                Toast.makeText(context, "Photo attached!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                        ) {
                            Text("Yes, Attach", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showGooglePhotosByDateDialog = false }
                    ) {
                        Text("Close", color = WaterBlue)
                    }
                }
            },
            containerColor = Color(0xFF13141C)
        )
    }

    if (showPickerSessionDialog) {
        AlertDialog(
            onDismissRequest = { showPickerSessionDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Secure Photos Picker", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pickerPhotosLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = WaterBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading Picker Session...", color = Color.LightGray, fontSize = 14.sp)
                        }
                    } else if (pickerSession == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No active photos picker session found. Let's create one!",
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    viewModel.createPickerSession(context) { intent ->
                                        try {
                                            pickerAuthLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to launch login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                            ) {
                                Text("Create Session", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val session = pickerSession!!
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Session Active!",
                                color = Color.Green,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "1. Tap to select photos in Google Photos securely:",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(session.pickerUri + "/autoclose"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open Picker URI: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Photo Picker", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            if (!session.mediaItemsSet) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Selecting media... Click below once done picking!",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.pollPickerSession(context, session.id) { intent ->
                                                try {
                                                    pickerAuthLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error checking status: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E38))
                                    ) {
                                        Text("Check Selection Status", color = Color.White)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    Text(
                                        "2. Select photos to attach:",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    if (pickerPhotosList.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No photos selected, or fetching photos...", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(pickerPhotosList) { photo ->
                                                val highResUrl = "${photo.baseUrl}=w1024"
                                                val isAttached = editingAttachments.contains("photo:$highResUrl")
                                                
                                                Card(
                                                    modifier = Modifier
                                                        .aspectRatio(1f)
                                                        .clickable {
                                                            if (isAttached) {
                                                                editingAttachments = editingAttachments.filter { it != "photo:$highResUrl" }
                                                            } else {
                                                                editingAttachments = editingAttachments + "photo:$highResUrl"
                                                            }
                                                        },
                                                    border = if (isAttached) BorderStroke(2.dp, WaterBlue) else null
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        AsyncImage(
                                                            model = photo.baseUrl,
                                                            contentDescription = "Selected Photo",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        if (isAttached) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(Color.Black.copy(alpha = 0.4f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Selected",
                                                                    tint = WaterBlue,
                                                                    modifier = Modifier.size(24.dp)
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
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            viewModel.clearPickerSession()
                            showPickerSessionDialog = false
                        }
                    ) {
                        Text("Reset & Close", color = Color.Red.copy(alpha = 0.8f))
                    }

                    TextButton(
                        onClick = { showPickerSessionDialog = false }
                    ) {
                        Text("Done", color = WaterBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color(0xFF13141C)
        )
    }

    // Real-Time Auto-Save triggering block
    LaunchedEffect(editingTitle, editingTextValue.text, editingDate, editingTime, editingAttachments) {
        val entryId = activeEditingEntryId ?: return@LaunchedEffect
        delay(550) // Debounce frequency
        val parsedTimestamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$editingDate $editingTime")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val authorName = resolveAuthorName(context)

        val attachmentsList = editingAttachments.toMutableList()
        if (!attachmentsList.any { it.trim().startsWith("author:") }) {
            attachmentsList.add("author:$authorName")
        }

        val updatedEntry = JournalEntry(
            id = entryId,
            title = editingTitle,
            text = editingTextValue.text,
            dateString = editingDate,
            timestamp = parsedTimestamp,
            attachmentsJson = attachmentsList.joinToString(";;")
        )
        viewModel.updateJournalEntry(updatedEntry)
        if (selectedJournalScope != "Personal Journal") {
            syncSharedJournalEntryToRtdb(context, selectedJournalScope, updatedEntry)
        }
    }

    if (showEditorScreen && activeEditingEntryId != null) {
        // Fullscreen Advanced Diary Editor screen
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Charcoal
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showEditorScreen = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Timeline", tint = Color.White)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
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

                        // Google Doc Export option
                        IconButton(
                            onClick = {
                                val tempEntry = com.example.data.JournalEntry(
                                    id = activeEditingEntryId ?: 0,
                                    title = editingTitle,
                                    text = editingTextValue.text,
                                    dateString = editingDate,
                                    timestamp = System.currentTimeMillis()
                                )
                                viewModel.exportJournalToGoogleDoc(context, tempEntry) { success, link ->
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Journal exported successfully!", android.widget.Toast.LENGTH_SHORT).show()
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
                            modifier = Modifier.testTag("export_journal_doc_btn")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Share,
                                contentDescription = "Export to Google Doc",
                                tint = WaterBlue
                            )
                        }

                        // Trash option
                        IconButton(onClick = {
                            val activeId = activeEditingEntryId
                            if (activeId != null) {
                                val found = entries.find { it.id == activeId }
                                if (found != null) {
                                    viewModel.deleteJournalEntry(found)
                                }
                            }
                            showEditorScreen = false
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Discard Journal Entry", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Line 1: Heading/Title
                TextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    placeholder = { Text("Heading", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.fillMaxWidth().testTag("journal_heading_input")
                )

                // Customizable DateTime Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = java.util.Calendar.getInstance()
                                if (editingDate.isNotEmpty()) {
                                    try {
                                        val parts = editingDate.split("-")
                                        if (parts.size == 3) {
                                            calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                                            calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                                            calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.DatePickerDialog(
                                    dialogContext,
                                    { _, year, month, dayOfMonth ->
                                        editingDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    calendar.get(java.util.Calendar.YEAR),
                                    calendar.get(java.util.Calendar.MONTH),
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        TextField(
                            value = editingDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Custom Date (YYYY-MM-DD)", fontSize = 9.sp) },
                            placeholder = { Text("Pick Date") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = WaterBlue,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = java.util.Calendar.getInstance()
                                if (editingTime.isNotEmpty()) {
                                    try {
                                        val parts = editingTime.split(":")
                                        if (parts.size == 2) {
                                            calendar.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                                            calendar.set(java.util.Calendar.MINUTE, parts[1].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.TimePickerDialog(
                                    dialogContext,
                                    { _, hourOfDay, minute ->
                                        editingTime = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
                                    },
                                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                    calendar.get(java.util.Calendar.MINUTE),
                                    true
                                ).show()
                            }
                    ) {
                        TextField(
                            value = editingTime,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Custom Time (HH:MM)", fontSize = 9.sp) },
                            placeholder = { Text("Pick Time") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = WaterBlue,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }
                }

                if (googlePhotosDateList.isNotEmpty() && showEditorScreen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val radius = size.minDimension / 4f
                                    drawArc(color = Color(0xFF4285F4), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius, 0f), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                                    drawArc(color = Color(0xFFEA4335), startAngle = 270f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius * 2, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                                    drawArc(color = Color(0xFFFBBC05), startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(radius, radius * 2), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                                    drawArc(color = Color(0xFF34A853), startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(0f, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                                }
                                Column {
                                    Text("Photos from this day!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Found ${googlePhotosDateList.size} Google Photos on $editingDate", color = Color.LightGray, fontSize = 10.sp)
                                }
                            }
                            
                            Button(
                                onClick = {
                                    datePhotosSelectedIndex = 0
                                    showGooglePhotosByDateDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Review & Attach", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // RICH TEXT FORMATTING TOOLBAR
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                    border = BorderStroke(1.dp, Color(0xFF2D2D38)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bold
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "**", "**", "bold")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("B", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            // Italic
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "*", "*", "italic")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            // Underline
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "<u>", "</u>", "underline")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("U", textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            // Strikethrough
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "~~", "~~", "strikethrough")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("S", textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            
                            // Divider
                            item {
                                Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))
                            }

                            // Text Color Toggle
                            item {
                                IconButton(
                                    onClick = {
                                        showTextColorPicker = !showTextColorPicker
                                        showBgColorPicker = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Text Color",
                                        tint = if (showTextColorPicker) WaterBlue else Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Background Highlight Toggle
                            item {
                                IconButton(
                                    onClick = {
                                        showBgColorPicker = !showBgColorPicker
                                        showTextColorPicker = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFFE082)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("A", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }

                            // Divider
                            item {
                                Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))
                            }

                            // Bulleted List
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "\n- ", "", "Bullet point")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.FormatListBulleted, contentDescription = "Bulleted List", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Ordered List
                            item {
                                IconButton(
                                    onClick = {
                                        editingTextValue = applyFormattingToTextFieldValue(editingTextValue, "\n1. ", "", "Ordered item")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = "Ordered List", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Divider
                            item {
                                Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))
                            }

                            // Web Link
                            item {
                                IconButton(
                                    onClick = { showWebLinkDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = "Web Link", tint = WaterBlue, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Journal Entry Link
                            item {
                                IconButton(
                                    onClick = { showJournalLinkDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Book, contentDescription = "Link Journal Entry", tint = Color(0xFF81D4FA), modifier = Modifier.size(20.dp))
                                }
                            }

                            // Divider
                            item {
                                Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))
                            }

                            // Rich Preview Mode Chip
                            item {
                                FilterChip(
                                    selected = isEditorPreviewMode,
                                    onClick = { isEditorPreviewMode = !isEditorPreviewMode },
                                    label = { Text(if (isEditorPreviewMode) "Edit Raw" else "Rich Preview", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isEditorPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WaterBlue,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.Transparent,
                                        labelColor = Color.LightGray
                                    ),
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }

                        // Text Color Selection Row
                        if (showTextColorPicker) {
                            HorizontalDivider(color = Color(0xFF2D2D38))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Color:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                val textColors = listOf(
                                    "Red" to "#EF5350",
                                    "Blue" to "#42A5F5",
                                    "Green" to "#66BB6A",
                                    "Yellow" to "#FFCA28",
                                    "Orange" to "#FFA726",
                                    "Purple" to "#AB47BC",
                                    "Pink" to "#EC407A",
                                    "WaterBlue" to "#4DD0E1",
                                    "White" to "#FFFFFF"
                                )
                                textColors.forEach { (name, hex) ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(parseRichColor(hex))
                                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            .clickable {
                                                editingTextValue = applyFormattingToTextFieldValue(
                                                    editingTextValue,
                                                    "<color:$hex>",
                                                    "</color>",
                                                    "colored text"
                                                )
                                                showTextColorPicker = false
                                            }
                                    )
                                }
                            }
                        }

                        // Background Highlight Selection Row
                        if (showBgColorPicker) {
                            HorizontalDivider(color = Color(0xFF2D2D38))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Highlight:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                val bgColors = listOf(
                                    "Yellow" to "#FFE082",
                                    "Green" to "#C8E6C9",
                                    "Cyan" to "#B2EBF2",
                                    "Lavender" to "#E1BEE7",
                                    "Orange" to "#FFE0B2",
                                    "Pink" to "#FFCDD2"
                                )
                                bgColors.forEach { (name, hex) ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(parseRichColor(hex))
                                            .clickable {
                                                editingTextValue = applyFormattingToTextFieldValue(
                                                    editingTextValue,
                                                    "<bg:$hex>",
                                                    "</bg>",
                                                    "highlighted"
                                                )
                                                showBgColorPicker = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Unlimited Description text field tracking selection values (Scrollable column with inline media preview)
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        if (isEditorPreviewMode) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp)
                                    .padding(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181E)),
                                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("RICH TEXT PREVIEW", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    RichTextDisplay(
                                        text = editingTextValue.text,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        entries = entries,
                                        onJournalEntryClick = { target ->
                                            viewingEntry = target
                                        }
                                    )
                                }
                            }
                        } else {
                            TextField(
                                value = editingTextValue,
                                onValueChange = { editingTextValue = it },
                                placeholder = { Text("Write your journal entry...", color = Color.Gray, fontSize = 14.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).testTag("journal_body_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        YouTubeLinkParserAndRenderer(text = editingTextValue.text)
                        InstagramLinkParserAndRenderer(text = editingTextValue.text)

                        Spacer(modifier = Modifier.height(16.dp))

                        // RENDER DETAILED MEDIA PREVIEW RIGHT IN EDITOR AS REQUESTED!
                        val mediaAttachments = editingAttachments.filter { it.startsWith("photo:") || it.startsWith("video:") || it.startsWith("audio:") || it.startsWith("file:") }
                        if (mediaAttachments.isNotEmpty()) {
                            Text("MEDIA FILES ATTACHED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            mediaAttachments.forEach { attach ->
                                JournalMediaItem(
                                    context = context,
                                    attach = attach,
                                    isEditing = true,
                                    onDelete = {
                                        editingAttachments = editingAttachments.filter { it != attach }
                                    }
                                )
                            }
                        }
                    }

                    // Autocomplete drop down suggestions for @ contact mentions
                    val typedWord = remember(editingTextValue.text, editingTextValue.selection) {
                        try {
                            val text = editingTextValue.text
                            val cursor = editingTextValue.selection.end
                            if (cursor in 1..text.length) {
                                val sub = text.substring(0, cursor)
                                val spaceIdx = sub.lastIndexOf(' ')
                                val word = if (spaceIdx != -1) sub.substring(spaceIdx + 1) else sub
                                if (word.startsWith("@")) word else ""
                            } else ""
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    if (typedWord.isNotEmpty()) {
                        val term = typedWord.removePrefix("@").lowercase()
                        val matchingContacts = contacts.filter {
                            it.firstName.lowercase().contains(term) || it.lastName.lowercase().contains(term)
                        }

                        if (matchingContacts.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .heightIn(max = 120.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.5f))
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    items(matchingContacts) { contact ->
                                        val contactName = "${contact.firstName}${contact.lastName}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val completed = "@$contactName "
                                                    val text = editingTextValue.text
                                                    val cursor = editingTextValue.selection.end
                                                    val sub = text.substring(0, cursor)
                                                    val spaceIdx = sub.lastIndexOf(' ')
                                                    val prefix = if (spaceIdx != -1) text.substring(0, spaceIdx + 1) else ""
                                                    val suffix = text.substring(cursor)
                                                    val resultText = prefix + completed + suffix
                                                    val newCursor = (prefix + completed).length
                                                    editingTextValue = TextFieldValue(
                                                        text = resultText,
                                                        selection = TextRange(newCursor)
                                                    )
                                                }
                                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("${contact.firstName} ${contact.lastName}", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // In-editor rich attachments indicators row
                if (editingAttachments.isNotEmpty()) {
                    Text("Attachments (${editingAttachments.size}):", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        editingAttachments.forEachIndexed { index, attach ->
                            val label = when {
                                attach.startsWith("photo:") -> "📷 Photo"
                                attach.startsWith("video:") -> "🎥 Video"
                                attach.startsWith("audio:") -> "🎙️ Voice Record"
                                attach.startsWith("loc:") -> "📍 Map Tag"
                                else -> "📁 Doc Attached"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        // Option to remove attachment
                                        editingAttachments = editingAttachments.filterIndexed { idx, _ -> idx != index }
                                        Toast.makeText(context, "Attachment removed", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }

                // Toolbar panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
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
                            val selStart = editingTextValue.selection.start
                            val selEnd = editingTextValue.selection.end
                            val original = editingTextValue.text
                            val newText = if (selStart != selEnd) {
                                original.substring(0, selStart) + "**" + original.substring(selStart, selEnd) + "**" + original.substring(selEnd)
                            } else {
                                original.substring(0, selStart) + "**bold**" + original.substring(selStart)
                            }
                            editingTextValue = TextFieldValue(text = newText, selection = TextRange(selStart + 2, selEnd + 6))
                        }) {
                            Text("B", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        // Point wise list option
                        IconButton(onClick = {
                            val selStart = editingTextValue.selection.start
                            val original = editingTextValue.text
                            val newText = original.substring(0, selStart) + "\n• " + original.substring(selStart)
                            editingTextValue = TextFieldValue(text = newText, selection = TextRange(selStart + 3))
                        }) {
                            Text("• List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // Get Device Location option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
                                triggerFetchLocation(context) { lat, lng, city ->
                                    val locAttach = "loc:$city|coords:$lat,$lng"
                                    editingAttachments = editingAttachments + locAttach
                                    Toast.makeText(context, "Added Geotag: $city", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Add Location", tint = WaterBlue)
                        }

                        // Snaps direct internal photo option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outPhotoFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "journal_photo_${System.currentTimeMillis()}.jpg")
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

                        // Import from Google Photos library (Custom Google Photos Pinwheel Logo)
                        IconButton(onClick = {
                            if (editingDate.isEmpty()) {
                                Toast.makeText(context, "Please select or set a valid entry date first", Toast.LENGTH_SHORT).show()
                            } else {
                                datePhotosSelectedIndex = 0
                                showGooglePhotosByDateDialog = true
                                viewModel.fetchGooglePhotosByDate(
                                    context = context,
                                    dateStr = editingDate,
                                    onAuthResolutionRequired = { intent ->
                                        try {
                                            photosAuthLauncher.launch(intent)
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            Toast.makeText(context, "No web browser or application found to handle Google login.", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to launch login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        }) {
                            Canvas(modifier = Modifier.size(24.dp)) {
                                val d = size.minDimension / 2f
                                // Yellow (Top-Left)
                                drawArc(color = Color(0xFFFBBC05), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(d, d))
                                // Red (Top-Right)
                                drawArc(color = Color(0xFFEA4335), startAngle = 270f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(d, 0f), size = androidx.compose.ui.geometry.Size(d, d))
                                // Blue (Bottom-Right)
                                drawArc(color = Color(0xFF4285F4), startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(d, d), size = androidx.compose.ui.geometry.Size(d, d))
                                // Green (Bottom-Left)
                                drawArc(color = Color(0xFF34A853), startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(0f, d), size = androidx.compose.ui.geometry.Size(d, d))
                            }
                        }

                        // Snaps direct internal video option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outVideoFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "journal_video_${System.currentTimeMillis()}.mp4")
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
                                            editingAttachments = editingAttachments + "audio:${finalAudioFile.absolutePath}"
                                            Toast.makeText(context, "Voice memo attached! Transcribing with Gemini AI...", Toast.LENGTH_SHORT).show()
                                            viewModel.transcribeAudioFileOnly(
                                                context = context,
                                                audioFile = finalAudioFile,
                                                onSuccess = { transcribed ->
                                                    val current = editingTextValue.text
                                                    val newText = if (current.isBlank()) transcribed else "$current\n\n$transcribed"
                                                    editingTextValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
                                                    Toast.makeText(context, "✨ Voice note transcribed by Gemini AI!", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, "Voice memo attached.", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    } else {
                                        // Start recording code
                                        val recFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "voice_${System.currentTimeMillis()}.mp3")
                                        currentAudioRecordingFile = recFile
                                        try {
                                            audioRecorder = MediaRecorder().apply {
                                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                setAudioEncodingBitRate(32000) // 32 kbps voice codec compression
                                                setAudioSamplingRate(16000) // 16 kHz high compression speech sample rate
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

        if (showVoiceJournalDialog) {
            LaunchedEffect(isDialogRecording) {
                if (isDialogRecording) {
                    while (isDialogRecording) {
                        delay(1000L)
                        dialogRecordingSeconds++
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (!isTranscribingVoiceNote) {
                        if (isDialogRecording) {
                            try {
                                dialogAudioRecorder?.stop()
                                dialogAudioRecorder?.release()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            dialogAudioRecorder = null
                            isDialogRecording = false
                        }
                        showVoiceJournalDialog = false
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFFF5252)
                        )
                        Text(
                            text = "🎙️ AI Voice Journal Note",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Record your thoughts aloud. Gemini AI will automatically transcribe your speech, generate a title, and create a formatted journal entry.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        if (isTranscribingVoiceNote) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "✨ Gemini AI is transcribing your voice note...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Analyzing audio & generating structured journal entry...",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else if (isDialogRecording) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1418)),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.Red)
                                        )
                                        Text(
                                            text = "RECORDING LIVE",
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    val mins = dialogRecordingSeconds / 60
                                    val secs = dialogRecordingSeconds % 60
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Animated audio wave visualizer bars
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        val heights = listOf(12.dp, 20.dp, 8.dp, 24.dp, 16.dp, 22.dp, 10.dp)
                                        heights.forEachIndexed { idx, h ->
                                            val dynamicH = if (dialogRecordingSeconds % 2 == idx % 2) h else (h / 2)
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(dynamicH)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color(0xFFFF5252))
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Speak clearly into your microphone...",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            if (dialogRecordingFile != null && dialogRecordingFile!!.exists()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E23)),
                                    border = BorderStroke(1.dp, Color(0xFF34A853).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("✅ Voice note recorded!", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Ready for Gemini AI transcription and saving.", color = Color.LightGray, fontSize = 10.sp)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Tap the microphone below to begin recording.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Main Microphone Button inside dialog
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else {
                                    if (isDialogRecording) {
                                        try {
                                            dialogAudioRecorder?.stop()
                                            dialogAudioRecorder?.release()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        dialogAudioRecorder = null
                                        isDialogRecording = false
                                    } else {
                                        val recFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "voice_journal_${System.currentTimeMillis()}.mp3")
                                        dialogRecordingFile = recFile
                                        dialogRecordingSeconds = 0
                                        try {
                                            dialogAudioRecorder = MediaRecorder().apply {
                                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                setAudioEncodingBitRate(32000)
                                                setAudioSamplingRate(16000)
                                                setOutputFile(recFile.absolutePath)
                                                prepare()
                                                start()
                                            }
                                            isDialogRecording = true
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Failed to start microphone recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isTranscribingVoiceNote,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isDialogRecording) Color.Red else Color(0xFFFF5252))
                        ) {
                            Icon(
                                imageVector = if (isDialogRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isDialogRecording) "Stop Recording" else "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    if (dialogRecordingFile != null && dialogRecordingFile!!.exists() && !isDialogRecording && !isTranscribingVoiceNote) {
                        Button(
                            onClick = {
                                val recFile = dialogRecordingFile
                                if (recFile != null && recFile.exists()) {
                                    isTranscribingVoiceNote = true
                                    viewModel.transcribeVoiceNoteToJournal(
                                        context = context,
                                        audioFile = recFile,
                                        onSuccess = { title, content, entryId ->
                                            isTranscribingVoiceNote = false
                                            showVoiceJournalDialog = false
                                            dialogRecordingFile = null
                                            activeEditingEntryId = entryId
                                            editingTitle = title
                                            editingTextValue = TextFieldValue(content)
                                            editingAttachments = listOf("audio:${recFile.absolutePath}")
                                            showEditorScreen = true
                                            Toast.makeText(context, "✨ Voice note transcribed and saved as journal entry!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            isTranscribingVoiceNote = false
                                            Toast.makeText(context, "Transcription note: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                        ) {
                            Text("✨ Transcribe & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (isDialogRecording) {
                                try {
                                    dialogAudioRecorder?.stop()
                                    dialogAudioRecorder?.release()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                dialogAudioRecorder = null
                                isDialogRecording = false
                            }
                            showVoiceJournalDialog = false
                        },
                        enabled = !isTranscribingVoiceNote
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }

        if (showWebLinkDialog) {
            var linkTitle by remember { mutableStateOf("") }
            var linkUrl by remember { mutableStateOf("https://") }
            AlertDialog(
                onDismissRequest = { showWebLinkDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = WaterBlue)
                        Text("Insert Web Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = linkTitle,
                            onValueChange = { linkTitle = it },
                            label = { Text("Link Label / Display Text") },
                            placeholder = { Text("e.g. My Favorite Article") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            label = { Text("Web Address (URL)") },
                            placeholder = { Text("https://example.com") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val label = linkTitle.ifBlank { linkUrl }
                            val formatted = "[$label]($linkUrl)"
                            editingTextValue = applyFormattingToTextFieldValue(editingTextValue, formatted, "", "")
                            showWebLinkDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                    ) {
                        Text("Insert Link", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWebLinkDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }

        if (showJournalLinkDialog) {
            var searchQuery by remember { mutableStateOf("") }
            val otherEntries = remember(entries, activeEditingEntryId, searchQuery) {
                entries.filter { it.id != activeEditingEntryId && (searchQuery.isEmpty() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)) }
            }
            AlertDialog(
                onDismissRequest = { showJournalLinkDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = Color(0xFF81D4FA))
                        Text("Link to Journal Entry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search journal entries...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (otherEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No other journal entries found.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(otherEntries) { entryItem ->
                                    Card(
                                        onClick = {
                                            val title = entryItem.title.ifBlank { "Journal Entry #${entryItem.id}" }
                                            val formatted = "[$title](entry:${entryItem.id})"
                                            editingTextValue = applyFormattingToTextFieldValue(editingTextValue, formatted, "", "")
                                            showJournalLinkDialog = false
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF282830)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = entryItem.title.ifEmpty { "Untitled Entry" },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = entryItem.dateString,
                                                color = WaterBlue,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showJournalLinkDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
    } else {
        // Main Journal Dashboard Layout representation with Sidebar Toggle control
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar containing plus action only
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }) {
                            Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar Manager", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        var topScopeDropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B1B22))
                                    .border(1.dp, WaterBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { topScopeDropdownExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedJournalScope == "Personal Journal") Icons.Default.Person else Icons.Default.Group,
                                    contentDescription = null,
                                    tint = WaterBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedJournalScope,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Scope",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = topScopeDropdownExpanded,
                                onDismissRequest = { topScopeDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF181820))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Personal Journal", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        selectedJournalScope = "Personal Journal"
                                        topScopeDropdownExpanded = false
                                    }
                                )

                                customGroupScopes.forEach { groupName ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Group, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(groupName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        onClick = {
                                            selectedJournalScope = groupName
                                            topScopeDropdownExpanded = false
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("+ New Study Group Journal", color = WaterBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = {
                                        showAddCustomGroupDialog = true
                                        topScopeDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "- ${currentJournalTab.uppercase()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isSummarizing by remember { mutableStateOf(false) }
                        
                        IconButton(
                            onClick = {
                                isSummarizing = true
                                viewModel.summarizeDayIntoJournalEntry { outcome ->
                                    isSummarizing = false
                                    Toast.makeText(context, "AI Daily Summary Added to Journal!", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF0F2622))
                                .size(40.dp)
                                .testTag("summarize_today_btn")
                        ) {
                            if (isSummarizing) {
                                CircularProgressIndicator(
                                    color = Color(0xFF00BFA5),
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "AI Summarize Today",
                                    tint = Color(0xFF00BFA5)
                                )
                            }
                        }

                        // AI Voice Journal Note Button
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                                showVoiceJournalDialog = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252))
                                .size(40.dp)
                                .testTag("ai_voice_journal_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "AI Voice Journal Note", tint = Color.White)
                        }

                        // Large Plus icon triggering inserting new draft
                        IconButton(
                            onClick = {
                                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val nowStrDate = sdfDate.format(Date())
                                val nowStrTime = sdfTime.format(Date())
                                val initialText = ""
                                val autoLoc = viewModel.getAutoLocationGeotag()
                                val initialAttachments = if (autoLoc.isNotEmpty()) listOf(autoLoc) else emptyList()

                                scope.launch {
                                    val generatedId = viewModel.createJournalEntryWithId(
                                        title = "",
                                        text = initialText,
                                        dateString = nowStrDate,
                                        timestamp = System.currentTimeMillis(),
                                        attachments = initialAttachments.joinToString(";;")
                                    )
                                    activeEditingEntryId = generatedId
                                    editingTitle = ""
                                    editingTextValue = TextFieldValue(initialText)
                                    editingDate = nowStrDate
                                    editingTime = nowStrTime
                                    editingAttachments = initialAttachments
                                    showEditorScreen = true
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(WaterBlue)
                                .size(40.dp)
                                .testTag("create_diary_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Diary entry", tint = Color.Black)
                        }

                        // Calendar date picker button
                        IconButton(
                            onClick = {
                                val calendar = java.util.Calendar.getInstance()
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = java.util.Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth)
                                        }
                                        val selectedDateStr = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                        
                                        when (currentJournalTab) {
                                            "Timeline" -> {
                                                val index = entries.indexOfFirst { it.dateString == selectedDateStr }
                                                if (index != -1) {
                                                    scope.launch {
                                                        timelineListState.animateScrollToItem(index)
                                                    }
                                                    Toast.makeText(context, "Scrolled to entry for $selectedDateStr", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val closestIndex = entries.indices.minByOrNull { i ->
                                                        val entryDate = try {
                                                            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(entries[i].dateString)
                                                        } catch (e: Exception) { null }
                                                        if (entryDate != null) {
                                                            Math.abs(entryDate.time - selectedCal.timeInMillis)
                                                        } else {
                                                            Long.MAX_VALUE
                                                        }
                                                    } ?: -1
                                                    if (closestIndex != -1) {
                                                        scope.launch {
                                                            timelineListState.animateScrollToItem(closestIndex)
                                                        }
                                                        val closestDate = entries[closestIndex].dateString
                                                        Toast.makeText(context, "No entry on $selectedDateStr. Scrolled to closest ($closestDate)", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "No journal entries found.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            "Monthly" -> {
                                                val currentCal = java.util.Calendar.getInstance()
                                                val yearDiff = selectedCal.get(java.util.Calendar.YEAR) - currentCal.get(java.util.Calendar.YEAR)
                                                val monthDiff = selectedCal.get(java.util.Calendar.MONTH) - currentCal.get(java.util.Calendar.MONTH)
                                                val totalMonthOffset = yearDiff * 12 + monthDiff
                                                val targetIndex = totalMonthOffset + 60
                                                if (targetIndex in 0..72) {
                                                    scope.launch {
                                                        monthlyListState.animateScrollToItem(targetIndex)
                                                    }
                                                    val monthName = selectedCal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault()) ?: ""
                                                    Toast.makeText(context, "Scrolled to $monthName ${selectedCal.get(java.util.Calendar.YEAR)}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Selected month is outside the 5-year range.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            "On This Day" -> {
                                                selectedOnThisDayDayMonth = String.format(java.util.Locale.US, "%02d-%02d", month + 1, dayOfMonth)
                                                Toast.makeText(context, "Viewing anniversary for ${selectedOnThisDayDayMonth}", Toast.LENGTH_SHORT).show()
                                            }
                                            "Map View" -> {
                                                mapScrollToDate = selectedDateStr
                                            }
                                        }
                                    },
                                    calendar.get(java.util.Calendar.YEAR),
                                    calendar.get(java.util.Calendar.MONTH),
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                )
                                datePickerDialog.show()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF232D37))
                                .size(40.dp)
                                .testTag("journal_calendar_picker_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Date",
                                tint = WaterBlue
                            )
                        }
                    }
                }

                // Dashboard views switcher
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        when (currentJournalTab) {
                            "Timeline" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "TIMELINE CHRONOLOGY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    val currentDayMonth = remember(entries) { SimpleDateFormat("MM-dd", Locale.US).format(Date()) }
                                    val matchedAnniversaryEntries = remember(entries) { entries.filter { it.dateString.endsWith(currentDayMonth) } }

                                    if (onThisDayOnScreenEnabled && matchedAnniversaryEntries.isNotEmpty() && !isOnThisDayReminderDismissed) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .border(1.dp, WaterBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = "Anniversary Icon",
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "On This Day Reminder",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "You have ${matchedAnniversaryEntries.size} historic entries written on this day in history!",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "View Anniversary Entries",
                                                        color = WaterBlue,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.clickable {
                                                            currentJournalTab = "On This Day"
                                                            viewModel.updateDefaultJournalView("On This Day")
                                                        }
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { isOnThisDayReminderDismissed = true }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (entries.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text("No journal entries documented. Click the + to persist today's record.", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            LazyColumn(
                                                state = timelineListState,
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                            items(entries) { entry ->
                                                val photoPath = remember(entry) {
                                                    entry.attachmentsJson
                                                        .split(";;")
                                                        .find { it.trim().startsWith("photo:") }
                                                        ?.removePrefix("photo:")
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewingEntry = entry }
                                                        .background(SurfaceCard, RoundedCornerShape(12.dp))
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Box with Date and Month on Left Edge
                                                    Box(
                                                        modifier = Modifier
                                                            .width(68.dp)
                                                            .height(58.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (!photoPath.isNullOrEmpty()) Color.Transparent else WaterBlue)
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!photoPath.isNullOrEmpty()) {
                                                            AsyncImage(
                                                                model = if (photoPath.startsWith("http")) photoPath else java.io.File(photoPath),
                                                                contentDescription = "Background photo",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            val dateParts = try {
                                                                val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                                val d = inputSdf.parse(entry.dateString)
                                                                val outMonth = SimpleDateFormat("MMM", Locale.getDefault()).format(d)
                                                                val outDay = SimpleDateFormat("dd", Locale.US).format(d)
                                                                Pair(outMonth.uppercase(), outDay)
                                                            } catch (e: Exception) {
                                                                Pair("MEM", "??")
                                                            }
                                                            Text(
                                                                text = dateParts.first,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                            Text(
                                                                text = dateParts.second,
                                                                fontSize = 20.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    // Content with constraints
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = if (entry.title.isNotEmpty()) entry.title else "Untitled Journal Entry",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        // Strictly show just 1 or 2 lines for card description matching requirement
                                                        RichTextDisplay(
                                                            text = entry.text,
                                                            color = Color.LightGray,
                                                            fontSize = 12.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            lineHeight = 16.sp,
                                                            entries = entries,
                                                            onJournalEntryClick = { target ->
                                                                viewingEntry = target
                                                            }
                                                        )

                                                        // Check location tags
                                                        if (entry.attachmentsJson.contains("loc:")) {
                                                            val cleanLoc = entry.attachmentsJson
                                                                .split(";;")
                                                                .find { it.trim().startsWith("loc:") }
                                                                ?.removePrefix("loc:")
                                                                ?.split("|coords:")
                                                                ?.getOrNull(0) ?: ""
                                                            if (cleanLoc.isNotEmpty()) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(10.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(cleanLoc, color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }

                                                        // Parse and display Hashtags and contact mentions on Card's very last line
                                                        val hashTags = remember(entry.text) {
                                                            Regex("""#\w+""").findAll(entry.text).map { it.value }.toList()
                                                        }
                                                        val contactTags = remember(entry.text) {
                                                            Regex("""@\w+""").findAll(entry.text).map { it.value }.toList()
                                                        }

                                                        if (hashTags.isNotEmpty() || contactTags.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                                            ) {
                                                                contactTags.forEach { contact ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(Color(0xFF2E4057))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(text = contact, color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                                hashTags.forEach { tag ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(Color(0xFF1D2C42))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(text = tag, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val authorTag = remember(entry.attachmentsJson) {
                                                            entry.attachmentsJson
                                                                .split(";;")
                                                                .find { it.trim().startsWith("author:") }
                                                                ?.removePrefix("author:")
                                                        }
                                                        if (!authorTag.isNullOrEmpty()) {
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.Person, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(11.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Entered by: $authorTag",
                                                                    color = Color.LightGray.copy(alpha = 0.85f),
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))


                                                }
                                            }
                                        }

                                        val showButton by remember {
                                                derivedStateOf {
                                                    timelineListState.firstVisibleItemIndex > 0
                                                }
                                            }
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = showButton,
                                                enter = fadeIn() + expandIn(),
                                                exit = fadeOut() + shrinkOut(),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp)
                                            ) {
                                                FloatingActionButton(
                                                    onClick = {
                                                        scope.launch {
                                                            timelineListState.animateScrollToItem(0)
                                                        }
                                                    },
                                                    containerColor = WaterBlue,
                                                    contentColor = Color.Black,
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = "Scroll to top"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Monthly" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val monthOffsets = remember { (-60..12).toList() }
                                    val todayCal = remember { java.util.Calendar.getInstance() }
                                    val todayDateStr = remember {
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                    }

                                    // Calendar Header Actions
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "MONTHLY PHOTO CALENDAR",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )

                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    monthlyListState.animateScrollToItem(60)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Today,
                                                    contentDescription = null,
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Jump to Today",
                                                    color = WaterBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        state = monthlyListState,
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    ) {
                                        items(monthOffsets) { offset ->
                                            // Get month configuration
                                            val monthData = remember(offset) {
                                                val cal = java.util.Calendar.getInstance()
                                                cal.add(java.util.Calendar.MONTH, offset)
                                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                                val monthName = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault()) ?: ""
                                                val year = cal.get(java.util.Calendar.YEAR)
                                                val monthValue = cal.get(java.util.Calendar.MONTH)
                                                val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                                val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                                                val yearMonthStr = String.format(java.util.Locale.US, "%04d-%02d", year, monthValue + 1)

                                                val numBlanks = firstDayOfWeek - 1
                                                val cellsList = mutableListOf<Int?>()
                                                repeat(numBlanks) { cellsList.add(null) }
                                                for (d in 1..daysInMonth) { cellsList.add(d) }
                                                while (cellsList.size % 7 != 0) { cellsList.add(null) }

                                                Triple(monthName.uppercase(), year, yearMonthStr to cellsList)
                                            }

                                            val (monthName, year, cellsPair) = monthData
                                            val (yearMonthStr, cellsList) = cellsPair
                                            val weeks = remember(cellsList) { cellsList.chunked(7) }

                                            val isCurrentMonthYear = remember(monthName, year) {
                                                monthName.uppercase() == todayCal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault())?.uppercase() &&
                                                        year == todayCal.get(java.util.Calendar.YEAR)
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.5f)),
                                                border = if (isCurrentMonthYear) BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)) else null,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    // Month Header inside the card
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "$monthName $year",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCurrentMonthYear) WaterBlue else Color.White
                                                        )
                                                        if (isCurrentMonthYear) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(WaterBlue.copy(alpha = 0.2f))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("Current Month", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                                                            }
                                                        }
                                                    }

                                                    // Days of Week labels
                                                    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 6.dp)
                                                    ) {
                                                        daysOfWeek.forEach { label ->
                                                            Text(
                                                                text = label,
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }

                                                    // Month Calendar Grid Rows
                                                    weeks.forEach { week ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            week.forEach { day ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f)
                                                                ) {
                                                                    if (day != null) {
                                                                        val targetDateString = String.format(java.util.Locale.US, "%s-%02d", yearMonthStr, day)
                                                                        val isDayToday = (targetDateString == todayDateStr)

                                                                        val matchEntriesForDay = remember(entries, targetDateString) {
                                                                            entries.filter { it.dateString == targetDateString }
                                                                        }
                                                                        val matchEntry = matchEntriesForDay.firstOrNull()
                                                                        val photoPath = remember(matchEntry) {
                                                                            matchEntry?.attachmentsJson
                                                                                ?.split(";;")
                                                                                ?.find { it.trim().startsWith("photo:") }
                                                                                ?.removePrefix("photo:")
                                                                        }

                                                                        Box(
                                                                            modifier = Modifier
                                                                                .fillMaxSize()
                                                                                .clip(RoundedCornerShape(8.dp))
                                                                                .background(
                                                                                    if (matchEntry != null) {
                                                                                        if (!photoPath.isNullOrEmpty()) Color.Transparent else WaterBlue.copy(alpha = 0.15f)
                                                                                    } else {
                                                                                        SurfaceCard
                                                                                    }
                                                                                )
                                                                                .border(
                                                                                    width = if (isDayToday) 2.dp else (if (matchEntry != null) 1.dp else 0.dp),
                                                                                    color = if (isDayToday) WaterBlue else (if (matchEntry != null) WaterBlue.copy(alpha = 0.6f) else Color.Transparent),
                                                                                    shape = RoundedCornerShape(8.dp)
                                                                                )
                                                                                .clickable {
                                                                                    if (matchEntry != null) {
                                                                                        viewingEntry = matchEntry
                                                                                    } else {
                                                                                        // Open creator with targetDateString pre-populated!
                                                                                        val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                                                                        val nowStrTime = sdfTime.format(java.util.Date())
                                                                                        val targetInitialText = ""
                                                                                        val autoLoc = viewModel.getAutoLocationGeotag()
                                                                                        val initialAttachments = if (autoLoc.isNotEmpty()) listOf(autoLoc) else emptyList()

                                                                                        val parsedDate = try {
                                                                                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(targetDateString)
                                                                                        } catch (e: Exception) {
                                                                                            null
                                                                                        }
                                                                                        val entryTimestamp = parsedDate?.time ?: System.currentTimeMillis()

                                                                                        scope.launch {
                                                                                            val generatedId = viewModel.createJournalEntryWithId(
                                                                                                title = "",
                                                                                                text = targetInitialText,
                                                                                                dateString = targetDateString,
                                                                                                timestamp = entryTimestamp,
                                                                                                attachments = initialAttachments.joinToString(";;")
                                                                                            )
                                                                                            activeEditingEntryId = generatedId
                                                                                            editingTitle = ""
                                                                                            editingTextValue = androidx.compose.ui.text.input.TextFieldValue(targetInitialText)
                                                                                            editingDate = targetDateString
                                                                                            editingTime = nowStrTime
                                                                                            editingAttachments = initialAttachments
                                                                                            showEditorScreen = true
                                                                                        }
                                                                                        Toast.makeText(context, "Drafting journal entry for $targetDateString...", Toast.LENGTH_SHORT).show()
                                                                                    }
                                                                                },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            if (!photoPath.isNullOrEmpty()) {
                                                                                AsyncImage(
                                                                                    model = if (photoPath.startsWith("http")) photoPath else java.io.File(photoPath),
                                                                                    contentDescription = "Memory illustration for Day $day",
                                                                                    contentScale = ContentScale.Crop,
                                                                                    modifier = Modifier.fillMaxSize()
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                                                )
                                                                            }

                                                                            Column(
                                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                                verticalArrangement = Arrangement.Center
                                                                            ) {
                                                                                Text(
                                                                                    text = day.toString(),
                                                                                    color = if (!photoPath.isNullOrEmpty()) {
                                                                                        Color.White
                                                                                    } else {
                                                                                        if (isDayToday) WaterBlue else (if (matchEntry != null) WaterBlue else Color.White)
                                                                                    },
                                                                                    fontSize = 13.sp,
                                                                                    fontWeight = if (isDayToday || matchEntry != null) FontWeight.ExtraBold else FontWeight.Medium,
                                                                                    textAlign = TextAlign.Center
                                                                                )

                                                                                if (matchEntriesForDay.isNotEmpty()) {
                                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                                    Row(
                                                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                                        verticalAlignment = Alignment.CenterVertically
                                                                                    ) {
                                                                                        repeat(matchEntriesForDay.size.coerceAtMost(3)) {
                                                                                            Box(
                                                                                                modifier = Modifier
                                                                                                    .size(4.dp)
                                                                                                    .clip(CircleShape)
                                                                                                    .background(WaterBlue)
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
                                    }
                                }
                            }

                            "On This Day" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DOCUMENTED ON ANNIVERSARY ($selectedOnThisDayDayMonth)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                        
                                        val todayDayMonth = remember { SimpleDateFormat("MM-dd", Locale.US).format(Date()) }
                                        if (selectedOnThisDayDayMonth != todayDayMonth) {
                                            TextButton(
                                                onClick = {
                                                    selectedOnThisDayDayMonth = todayDayMonth
                                                    Toast.makeText(context, "Reset to Today's anniversary", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Reset to Today's anniversary",
                                                        tint = WaterBlue,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Reset to Today",
                                                        color = WaterBlue,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val matchedAnniversaryEntries = entries.filter { it.dateString.endsWith(selectedOnThisDayDayMonth) }

                                    if (matchedAnniversaryEntries.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text("No historical records documented on anniversary day ($selectedOnThisDayDayMonth).", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(matchedAnniversaryEntries) { entry ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewingEntry = entry },
                                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(entry.title, fontWeight = FontWeight.ExtraBold, color = WaterBlue, fontSize = 13.sp)
                                                            Text(entry.dateString, color = Color.Gray, fontSize = 10.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        RichTextDisplay(
                                                            text = entry.text,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis,
                                                            entries = entries,
                                                            onJournalEntryClick = { target ->
                                                                viewingEntry = target
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Map View" -> {
                                JournalMapView(
                                    viewModel = viewModel,
                                    entries = entries,
                                    onEntryClick = { entry ->
                                        viewingEntry = entry
                                    },
                                    scrollToDate = mapScrollToDate,
                                    onScrollToDateHandled = {
                                        mapScrollToDate = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            "Google Photos" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "GOOGLE PHOTOS GALLERY",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = "Tap a photo to document a journal entry",
                                                fontSize = 10.sp,
                                                color = WaterBlue
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.fetchGooglePhotos(context) { intent ->
                                                    try {
                                                        photosAuthLauncher.launch(intent)
                                                    } catch (e: android.content.ActivityNotFoundException) {
                                                        Toast.makeText(context, "No web browser or application found to handle Google login.", Toast.LENGTH_LONG).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Failed to launch login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.2f), contentColor = WaterBlue),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Text("Refresh", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (googlePhotosLoading) {
                                            CircularProgressIndicator(color = WaterBlue)
                                        } else if (googlePhotosList.isEmpty()) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Collections,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "No Google Photos found or Google Photos integration is not authorized.",
                                                    color = Color.LightGray,
                                                    textAlign = TextAlign.Center,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(
                                                    onClick = {
                                                        viewModel.fetchGooglePhotos(context) { intent ->
                                                            try {
                                                                photosAuthLauncher.launch(intent)
                                                            } catch (e: android.content.ActivityNotFoundException) {
                                                                Toast.makeText(context, "No web browser or application found to handle Google login.", Toast.LENGTH_LONG).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Failed to launch login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                                                ) {
                                                    Text("Connect Google Photos", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            val columns = 3
                                            val chunkedPhotos = googlePhotosList.chunked(columns)
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(chunkedPhotos) { rowPhotos ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        rowPhotos.forEach { photo ->
                                                            var showConfirmDialog by remember { mutableStateOf(false) }

                                                            if (showConfirmDialog) {
                                                                AlertDialog(
                                                                    onDismissRequest = { showConfirmDialog = false },
                                                                    title = { Text("Write about this Photo?", color = Color.White) },
                                                                    text = { Text("Would you like to create a new Journal entry with this Google Photo pre-attached?", color = Color.LightGray) },
                                                                    confirmButton = {
                                                                        TextButton(
                                                                            onClick = {
                                                                                showConfirmDialog = false
                                                                                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                                                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                                                val nowStrDate = sdfDate.format(Date())
                                                                                val nowStrTime = sdfTime.format(Date())
                                                                                val photoUrl = photo.baseUrl
                                                                                val initialText = "Reflecting on this memory from Google Photos:\n\n"
                                                                                val attachmentStr = "photo:$photoUrl"

                                                                                scope.launch {
                                                                                    val generatedId = viewModel.createJournalEntryWithId(
                                                                                        title = photo.description ?: "Google Photo Memory",
                                                                                        text = initialText,
                                                                                        dateString = nowStrDate,
                                                                                        timestamp = System.currentTimeMillis(),
                                                                                        attachments = attachmentStr
                                                                                    )
                                                                                    activeEditingEntryId = generatedId
                                                                                    editingTitle = photo.description ?: "Google Photo Memory"
                                                                                    editingTextValue = TextFieldValue(initialText)
                                                                                    editingDate = nowStrDate
                                                                                    editingTime = nowStrTime
                                                                                    editingAttachments = listOf(attachmentStr)
                                                                                    showEditorScreen = true
                                                                                }
                                                                            }
                                                                        ) {
                                                                            Text("Yes, Create", color = WaterBlue)
                                                                        }
                                                                    },
                                                                    dismissButton = {
                                                                        TextButton(onClick = { showConfirmDialog = false }) {
                                                                            Text("Cancel", color = Color.Gray)
                                                                        }
                                                                    },
                                                                    containerColor = Color(0xFF13141C)
                                                                )
                                                            }

                                                            Card(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .aspectRatio(1f)
                                                                    .clickable {
                                                                        showConfirmDialog = true
                                                                    },
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Box(modifier = Modifier.fillMaxSize()) {
                                                                    AsyncImage(
                                                                        model = photo.baseUrl,
                                                                        contentDescription = photo.description ?: "Google Photo",
                                                                        contentScale = ContentScale.Crop,
                                                                        modifier = Modifier.fillMaxSize()
                                                                    )
                                                                    if (!photo.description.isNullOrBlank()) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .align(Alignment.BottomStart)
                                                                                .background(Color.Black.copy(alpha = 0.6f))
                                                                                .padding(4.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = photo.description ?: "",
                                                                                color = Color.White,
                                                                                fontSize = 8.sp,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        if (rowPhotos.size < columns) {
                                                            repeat(columns - rowPhotos.size) {
                                                                Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Local Media" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Row of Media Type Tabs
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Photos", "Videos", "Audios").forEach { type ->
                                            val isSelected = localMediaTypeTab == type
                                            val bg = if (isSelected) WaterBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
                                            val fg = if (isSelected) WaterBlue else Color.LightGray
                                            val borderClr = if (isSelected) WaterBlue else Color.Transparent

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bg)
                                                    .border(1.dp, borderClr, RoundedCornerShape(8.dp))
                                                    .clickable { localMediaTypeTab = type }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = type.uppercase(),
                                                    color = fg,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (!hasLocalPermissionsState) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(60.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Local Media Access",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Grant permission to browse, select, and link your local device photos, videos, and audios to your Journal.",
                                                color = Color.LightGray,
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Button(
                                                onClick = {
                                                    val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                        arrayOf(
                                                            android.Manifest.permission.READ_MEDIA_IMAGES,
                                                            android.Manifest.permission.READ_MEDIA_VIDEO,
                                                            android.Manifest.permission.READ_MEDIA_AUDIO
                                                        )
                                                    } else {
                                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                                    }
                                                    localMediaPermissionLauncher.launch(permissionsToRequest)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                                            ) {
                                                Text("Grant Access", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (localMediaLoading) {
                                                CircularProgressIndicator(color = WaterBlue)
                                            } else {
                                                val currentList = when (localMediaTypeTab) {
                                                    "Photos" -> localPhotos
                                                    "Videos" -> localVideos
                                                    else -> localAudios
                                                }

                                                if (currentList.isEmpty()) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier.padding(16.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Text(
                                                            text = "No local " + localMediaTypeTab.lowercase() + " found on this device.",
                                                            color = Color.LightGray,
                                                            textAlign = TextAlign.Center,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                } else {
                                                    if (localMediaTypeTab == "Audios") {
                                                        LazyColumn(
                                                            modifier = Modifier.fillMaxSize(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            items(currentList) { item ->
                                                                var showConfirmDialog by remember { mutableStateOf(false) }

                                                                if (showConfirmDialog) {
                                                                    AlertDialog(
                                                                        onDismissRequest = { showConfirmDialog = false },
                                                                        title = { Text("Link Audio to Journal Entry?", color = Color.White) },
                                                                        text = { Text("Would you like to create a new Journal entry with this local audio file pre-attached?", color = Color.LightGray) },
                                                                        confirmButton = {
                                                                            TextButton(
                                                                                onClick = {
                                                                                    showConfirmDialog = false
                                                                                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                                                    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                                                    val nowStrDate = sdfDate.format(Date())
                                                                                    val nowStrTime = sdfTime.format(Date())
                                                                                    val attachmentStr = "audio:" + item.uri
                                                                                    val initialText = "Linked local audio journal entry:\n[" + item.displayName + "]\n\n"

                                                                                    scope.launch {
                                                                                        val generatedId = viewModel.createJournalEntryWithId(
                                                                                            title = item.displayName.substringBeforeLast("."),
                                                                                            text = initialText,
                                                                                            dateString = nowStrDate,
                                                                                            timestamp = System.currentTimeMillis(),
                                                                                            attachments = attachmentStr
                                                                                        )
                                                                                        activeEditingEntryId = generatedId
                                                                                        editingTitle = item.displayName.substringBeforeLast(".")
                                                                                        editingTextValue = TextFieldValue(initialText)
                                                                                        editingDate = nowStrDate
                                                                                        editingTime = nowStrTime
                                                                                        editingAttachments = listOf(attachmentStr)
                                                                                        showEditorScreen = true
                                                                                    }
                                                                                }
                                                                            ) {
                                                                                Text("Yes, Create", color = WaterBlue)
                                                                            }
                                                                        },
                                                                        dismissButton = {
                                                                            TextButton(onClick = { showConfirmDialog = false }) {
                                                                                Text("Cancel", color = Color.Gray)
                                                                            }
                                                                        },
                                                                        containerColor = Color(0xFF13141C)
                                                                    )
                                                                }

                                                                Card(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clickable { showConfirmDialog = true },
                                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(12.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(40.dp)
                                                                                .clip(RoundedCornerShape(6.dp))
                                                                                .background(WaterBlue.copy(alpha = 0.15f)),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Audiotrack,
                                                                                contentDescription = null,
                                                                                tint = WaterBlue,
                                                                                modifier = Modifier.size(20.dp)
                                                                            )
                                                                        }
                                                                        Spacer(modifier = Modifier.width(12.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = item.displayName,
                                                                                color = Color.White,
                                                                                fontSize = 13.sp,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                                fontWeight = FontWeight.SemiBold
                                                                            )
                                                                            Text(
                                                                                text = "Local Audio File" + (if (item.duration is String) " • " + item.duration else ""),
                                                                                color = Color.Gray,
                                                                                fontSize = 11.sp
                                                                            )
                                                                        }
                                                                        Icon(
                                                                            imageVector = Icons.Default.AddCircle,
                                                                            contentDescription = "Link",
                                                                            tint = WaterBlue,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        val columns = 3
                                                        val chunkedMedia = currentList.chunked(columns)
                                                        LazyColumn(
                                                            modifier = Modifier.fillMaxSize(),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            items(chunkedMedia) { rowItems ->
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                ) {
                                                                    rowItems.forEach { item ->
                                                                        var showConfirmDialog by remember { mutableStateOf(false) }

                                                                        if (showConfirmDialog) {
                                                                            AlertDialog(
                                                                                onDismissRequest = { showConfirmDialog = false },
                                                                                title = { Text("Link Media to Journal Entry?", color = Color.White) },
                                                                                text = { Text("Would you like to create a new Journal entry with this local " + item.type + " pre-attached?", color = Color.LightGray) },
                                                                                confirmButton = {
                                                                                    TextButton(
                                                                                        onClick = {
                                                                                            showConfirmDialog = false
                                                                                            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                                                            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                                                            val nowStrDate = sdfDate.format(Date())
                                                                                            val nowStrTime = sdfTime.format(Date())
                                                                                            val attachmentStr = item.type + ":" + item.uri
                                                                                            val initialText = "Linked local " + item.type + " journal entry:\n\n"

                                                                                            scope.launch {
                                                                                                val generatedId = viewModel.createJournalEntryWithId(
                                                                                                    title = item.displayName.substringBeforeLast("."),
                                                                                                    text = initialText,
                                                                                                    dateString = nowStrDate,
                                                                                                    timestamp = System.currentTimeMillis(),
                                                                                                    attachments = attachmentStr
                                                                                                )
                                                                                                activeEditingEntryId = generatedId
                                                                                                editingTitle = item.displayName.substringBeforeLast(".")
                                                                                                editingTextValue = TextFieldValue(initialText)
                                                                                                editingDate = nowStrDate
                                                                                                editingTime = nowStrTime
                                                                                                editingAttachments = listOf(attachmentStr)
                                                                                                showEditorScreen = true
                                                                                            }
                                                                                        }
                                                                                    ) {
                                                                                        Text("Yes, Create", color = WaterBlue)
                                                                                    }
                                                                                },
                                                                                dismissButton = {
                                                                                    TextButton(onClick = { showConfirmDialog = false }) {
                                                                                        Text("Cancel", color = Color.Gray)
                                                                                    }
                                                                                },
                                                                                containerColor = Color(0xFF13141C)
                                                                            )
                                                                        }

                                                                        Card(
                                                                            modifier = Modifier
                                                                                .weight(1f)
                                                                                .aspectRatio(1f)
                                                                                .clickable { showConfirmDialog = true },
                                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
                                                                            shape = RoundedCornerShape(10.dp)
                                                                        ) {
                                                                            Box(modifier = Modifier.fillMaxSize()) {
                                                                                AsyncImage(
                                                                                    model = item.uri,
                                                                                    contentDescription = item.displayName,
                                                                                    contentScale = ContentScale.Crop,
                                                                                    modifier = Modifier.fillMaxSize()
                                                                                )
                                                                                if (item.type == "video") {
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .fillMaxSize()
                                                                                            .background(Color.Black.copy(alpha = 0.2f)),
                                                                                        contentAlignment = Alignment.Center
                                                                                    ) {
                                                                                        Icon(
                                                                                            imageVector = Icons.Default.PlayCircle,
                                                                                            contentDescription = "Video",
                                                                                            tint = Color.White.copy(alpha = 0.8f),
                                                                                            modifier = Modifier.size(24.dp)
                                                                                        )
                                                                                    }
                                                                                    if (item.duration is String) {
                                                                                        Box(
                                                                                            modifier = Modifier
                                                                                                .align(Alignment.BottomEnd)
                                                                                                .padding(4.dp)
                                                                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                                        ) {
                                                                                            Text(
                                                                                                text = item.duration,
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
                                                                    if (rowItems.size < columns) {
                                                                        repeat(columns - rowItems.size) {
                                                                            Spacer(modifier = Modifier.weight(1f))
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
                    }
                }
            }

            // Floating Custom Navigation Drawer Sidebar Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarExpanded,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isSidebarExpanded = false }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarExpanded,
                enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
                exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.fillMaxHeight().width(300.dp).align(Alignment.CenterStart)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(end = 12.dp)
                        .shadow(12.dp, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "JOURNAL",
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterBlue,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Scope Selector Dropdown Box above Timeline View
                        var sidebarScopeDropdownExpanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF16161D))
                                    .border(1.dp, WaterBlue.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .clickable { sidebarScopeDropdownExpanded = true }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (selectedJournalScope == "Personal Journal") Icons.Default.Person else Icons.Default.Group,
                                        contentDescription = null,
                                        tint = WaterBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Journal Mode", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Text(selectedJournalScope, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.LightGray)
                            }

                            DropdownMenu(
                                expanded = sidebarScopeDropdownExpanded,
                                onDismissRequest = { sidebarScopeDropdownExpanded = false },
                                modifier = Modifier
                                    .width(260.dp)
                                    .background(Color(0xFF14141A))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Personal Journal", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        selectedJournalScope = "Personal Journal"
                                        sidebarScopeDropdownExpanded = false
                                    }
                                )

                                customGroupScopes.forEach { groupName ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Group, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(groupName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        onClick = {
                                            selectedJournalScope = groupName
                                            sidebarScopeDropdownExpanded = false
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("+ New Study Group Journal", color = WaterBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = {
                                        showAddCustomGroupDialog = true
                                        sidebarScopeDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        val categories = listOf(
                            Triple("Timeline", "📅", "Timeline View"),
                            Triple("Monthly", "📆", "Monthly Calendar"),
                            Triple("On This Day", "🎉", "On This Day"),
                            Triple("Map View", "📍", "Locations Map"),
                            Triple("Google Photos", "🖼️", "Google Photos"),
                            Triple("Local Media", "📱", "Local Media")
                        )

                        categories.forEach { (tabId, icon, label) ->
                            val isSelected = currentJournalTab == tabId
                            val itemBg = if (isSelected) WaterBlue.copy(alpha = 0.15f) else Color.Transparent
                            val itemBorder = if (isSelected) 1.dp to WaterBlue else 0.dp to Color.Transparent
                            val textColor = if (isSelected) WaterBlue else Color.White

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(itemBg)
                                    .border(itemBorder.first, itemBorder.second, RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentJournalTab = tabId
                                        viewModel.updateDefaultJournalView(tabId)
                                        isSidebarExpanded = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    // Immersive Fullscreen Reading View Page to inspect entry details with fully active multi-format inbuilt players
    if (viewingEntry != null) {
        val entry = viewingEntry!!
        val attachments = remember(entry.attachmentsJson) {
            if (entry.attachmentsJson.isNotEmpty()) entry.attachmentsJson.split(";;") else emptyList()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Charcoal
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar (Top bar with Back control, and edit/delete in top right corner!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewingEntry = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }

                    // Top Right buttons: edit (pencil) and delete (trash)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            try {
                                android.util.Log.d("JournalBookView", "Pencil click initiated for entry ID: ${entry.id}")
                                activeEditingEntryId = entry.id
                                editingTitle = entry.title
                                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                                editingDate = entry.dateString
                                
                                val parsedTime = try {
                                    val entrySdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    entrySdfTime.format(Date(entry.timestamp))
                                } catch (ex: Exception) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                }
                                editingTime = parsedTime

                                editingAttachments = if (entry.attachmentsJson.isNotEmpty()) {
                                    entry.attachmentsJson.split(";;")
                                } else {
                                    emptyList()
                                }
                                
                                showEditorScreen = true
                                viewingEntry = null
                                android.util.Log.d("JournalBookView", "Pencil edit mode activated successfully. showEditorScreen = true")
                            } catch (e: Exception) {
                                android.util.Log.e("JournalBookView", "Failure switching pencil edit mode: ${e.message}", e)
                                // Resilient failover path
                                activeEditingEntryId = entry.id
                                editingTitle = entry.title
                                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                                editingDate = entry.dateString
                                editingTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                editingAttachments = emptyList()
                                showEditorScreen = true
                                viewingEntry = null
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Journal Entry",
                                tint = WaterBlue
                            )
                        }

                        IconButton(onClick = {
                            viewModel.deleteJournalEntry(entry)
                            viewingEntry = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Memory",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable details column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header title/headline
                    Text(
                        text = if (entry.title.isNotEmpty()) entry.title else "Untitled memory",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    // Date & Time, Location tags, etc.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val entrySdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val formattedTimeStr = entrySdfTime.format(Date(entry.timestamp))

                        val authorTag = entry.attachmentsJson
                            .split(";;")
                            .find { it.trim().startsWith("author:") }
                            ?.removePrefix("author:")

                        val subtitleText = if (!authorTag.isNullOrEmpty()) {
                            "${entry.dateString} at $formattedTimeStr • By $authorTag"
                        } else {
                            "${entry.dateString} at $formattedTimeStr"
                        }

                        Text(
                            text = subtitleText,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // If any location exists, show location
                        if (entry.attachmentsJson.contains("loc:")) {
                            val cleanLoc = entry.attachmentsJson
                                .split(";;")
                                .find { it.trim().startsWith("loc:") }
                                ?.removePrefix("loc:")
                                ?.split("|coords:")
                                ?.getOrNull(0) ?: ""
                            if (cleanLoc.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        try {
                                            val fullLocStr = entry.attachmentsJson
                                                .split(";;")
                                                .find { it.trim().startsWith("loc:") } ?: ""
                                            val parts = fullLocStr.removePrefix("loc:").split("|coords:")
                                            val address = parts.getOrNull(0)?.trim() ?: ""
                                            val coords = parts.getOrNull(1)?.trim() ?: ""
                                            
                                            val uriString = if (coords.isNotEmpty()) {
                                                "https://www.google.com/maps/search/?api=1&query=$coords"
                                            } else {
                                                "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(address)}"
                                            }
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("JournalBookView", "Failed to open Google Maps", e)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = WaterBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = cleanLoc,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                    // Description text wordings
                    if (entry.text.isNotEmpty()) {
                        RichTextDisplay(
                            text = entry.text,
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            entries = entries,
                            onJournalEntryClick = { target ->
                                viewingEntry = target
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        YouTubeLinkParserAndRenderer(text = entry.text)
                        InstagramLinkParserAndRenderer(text = entry.text)
                    } else {
                        // User requested "if no wording just leave a space ..."
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media and files showing up AFTER the description wordings!
                    attachments.forEach { attach ->
                        JournalMediaItem(
                            context = context,
                            attach = attach,
                            isEditing = false,
                            onDelete = {
                                val newList = attachments.filter { it != attach }
                                val updated = entry.copy(attachmentsJson = newList.joinToString(";;"))
                                viewModel.updateJournalEntry(updated)
                                viewingEntry = updated
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddCustomGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomGroupDialog = false },
            title = { Text("New Study Group Journal", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter Study Group name to sync shared entries and media with group members in real time:", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCustomGroupName,
                        onValueChange = { newCustomGroupName = it },
                        placeholder = { Text("e.g. Physics Study Group", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCustomGroupName.trim()
                        if (trimmed.isNotEmpty()) {
                            if (!customGroupScopes.contains(trimmed)) {
                                customGroupScopes = customGroupScopes + trimmed
                            }
                            selectedJournalScope = trimmed
                        }
                        newCustomGroupName = ""
                        showAddCustomGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Create / Join Group", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomGroupDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF16161D)
        )
    }
}

// Location lookup helper
fun getCityNameFromCoords(context: Context, latitude: Double, longitude: Double): String {
    val isDemoAustin = Math.abs(latitude - 30.2672) < 0.1 && Math.abs(longitude - (-97.7431)) < 0.1
    if (isDemoAustin) {
        return "Guntur, Andhra Pradesh"
    }

    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                val state = address.adminArea
                if (city != null) {
                    return if (state != null) "$city, $state" else city
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Guntur, Andhra Pradesh"
}

private fun triggerFetchLocation(context: Context, onLocationFound: (latitude: Double, longitude: Double, name: String) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        return
    }

    try {
        val providers = locationManager.getProviders(true)
        var lastLoc: Location? = null
        for (provider in providers) {
            val loc = locationManager.getLastKnownLocation(provider) ?: continue
            if (lastLoc == null || loc.accuracy < lastLoc.accuracy) {
                lastLoc = loc
            }
        }

        if (lastLoc != null) {
            val cityName = getCityNameFromCoords(context, lastLoc.latitude, lastLoc.longitude)
            onLocationFound(lastLoc.latitude, lastLoc.longitude, cityName)
        } else {
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val cityName = getCityNameFromCoords(context, location.latitude, location.longitude)
                        onLocationFound(location.latitude, location.longitude, cityName)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                },
                null
            )
            onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        }
    } catch (e: SecurityException) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
    } catch (e: Exception) {
        onLocationFound(11.6643, 78.1460, "Salem, Tamil Nadu")
    }
}

// Document sandbox copy utility
private fun copyFileToInternalSandbox(context: Context, uri: Uri): File? {
    return com.example.util.StorageHelper.copyFileToInternalSandbox(context, uri)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun JournalMediaItem(
    context: android.content.Context,
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
            else -> ""
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
            file.name.substringAfter("doc_").substringAfter("_")
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
                                Toast.makeText(context, "Saved successfully to Downloads folder!", Toast.LENGTH_SHORT).show()
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
            containerColor = SurfaceCard
        )
    }

    when {
        attach.startsWith("photo:") -> {
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
                                        setDataAndType(uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open Photo"))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Box {
                    AsyncImage(
                        model = if (isWebUrl) path else file,
                        contentDescription = "Attached photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        attach.startsWith("video:") -> {
            var requestedVideoPlay by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            requestedVideoPlay = true
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Box {
                    if (!requestedVideoPlay || isEditing) {
                        val thumbnailBitmap = rememberVideoThumbnail(path)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap,
                                    contentDescription = "Video Thumbnail Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    } else {
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        var videoPosition by remember { mutableStateOf(0) }
                        var isBackgrounded by remember { mutableStateOf(false) }
                        var videoViewReference by remember { mutableStateOf<VideoView?>(null) }

                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                                    videoViewReference?.let { vv ->
                                        if (vv.isPlaying) {
                                            videoPosition = vv.currentPosition
                                            vv.pause()
                                            isBackgrounded = true
                                            com.example.util.BackgroundMediaManager.play(path, videoPosition)
                                        }
                                    }
                                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                                    if (isBackgrounded) {
                                        isBackgrounded = false
                                        val bgPath = com.example.util.BackgroundMediaManager.currentPlayingPath.value
                                        if (bgPath == path) {
                                            val currentBgPos = com.example.util.BackgroundMediaManager.getCurrentPosition()
                                            com.example.util.BackgroundMediaManager.stop()
                                            videoViewReference?.let { vv ->
                                                vv.seekTo(currentBgPos)
                                                vv.start()
                                            }
                                        }
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(path)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    videoViewReference = this
                                    start()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        attach.startsWith("audio:") -> {
            val globalPlayingPath by com.example.util.BackgroundMediaManager.currentPlayingPath.collectAsState()
            val globalIsPlaying by com.example.util.BackgroundMediaManager.isPlaying.collectAsState()

            val playPath = remember(path) {
                val sourceFile = File(path)
                if (sourceFile.exists() && MediaCompressionHelper.isGzipFile(sourceFile)) {
                    val tempPlayFile = File(context.cacheDir, "play_decompressed_${sourceFile.name.removeSuffix(".gz")}")
                    if (!tempPlayFile.exists() || tempPlayFile.length() == 0L) {
                        MediaCompressionHelper.decompressFileGzip(sourceFile, tempPlayFile)
                    }
                    tempPlayFile.absolutePath
                } else {
                    path
                }
            }

            val isPlayingMusic = (globalPlayingPath == playPath && globalIsPlaying)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            if (isPlayingMusic) {
                                com.example.util.BackgroundMediaManager.pause()
                            } else {
                                if (globalPlayingPath == playPath) {
                                    com.example.util.BackgroundMediaManager.resume()
                                } else {
                                    com.example.util.BackgroundMediaManager.play(playPath)
                                }
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2C42))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPlayingMusic) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )

                        Column {
                            Text("Audio Voice Note Playback", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(displayName, color = Color.LightGray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
            }
        }

        attach.startsWith("file:") -> {
            val isPdf = remember(displayName) { displayName.lowercase().endsWith(".pdf") }
            val pdfBitmap = if (isPdf) rememberPdfFirstPagePreview(path) else null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            try {
                                val authority = "${context.packageName}.fileprovider"
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open File"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2C42))
            ) {
                Column {
                    if (isPdf && pdfBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = pdfBitmap,
                                contentDescription = "PDF Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.InsertDriveFile else Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = WaterBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${file.length() / 1024} KB • Hold for options", color = Color.LightGray, fontSize = 9.sp)
                            }
                        }

                        if (isEditing) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveFileToDownloads(context: android.content.Context, sourceFile: java.io.File, displayName: String): Boolean {
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
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            true
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = java.io.File(downloadsDir, displayName)
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Extract all entries with location coordinates
data class EntryMarker(
    val entry: JournalEntry,
    val title: String,
    val cityName: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String? = null
)

data class MarkerCluster(
    var centerLat: Double,
    var centerLng: Double,
    val markers: MutableList<EntryMarker>
)

fun clusterMarkers(markers: List<EntryMarker>, zoomLevel: Float): List<MarkerCluster> {
    val clusters = mutableListOf<MarkerCluster>()
    // Adjust cluster radius dynamically based on map zoom level
    val radius = if (zoomLevel >= 15f) {
        0.0003
    } else if (zoomLevel >= 12f) {
        0.005
    } else if (zoomLevel >= 9f) {
        0.08
    } else if (zoomLevel >= 6f) {
        0.6
    } else {
        3.5
    }

    for (marker in markers) {
        val targetCluster = clusters.find { cluster ->
            val dLat = Math.abs(cluster.centerLat - marker.lat)
            val dLng = Math.abs(cluster.centerLng - marker.lng)
            dLat < radius && dLng < radius
        }

        if (targetCluster != null) {
            targetCluster.markers.add(marker)
            targetCluster.centerLat = targetCluster.markers.map { it.lat }.average()
            targetCluster.centerLng = targetCluster.markers.map { it.lng }.average()
        } else {
            clusters.add(
                MarkerCluster(
                    centerLat = marker.lat,
                    centerLng = marker.lng,
                    markers = mutableListOf(marker)
                )
            )
        }
    }
    return clusters
}

fun createPhotoClusterBitmap(
    context: android.content.Context,
    count: Int,
    imageBitmap: android.graphics.Bitmap? = null
): com.google.android.gms.maps.model.BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val bubbleDp = if (count >= 50) 66 else if (count >= 10) 60 else 54
    val size = (bubbleDp * density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)

    val centerX = size / 2f
    val centerY = size / 2f
    val radius = (size / 2f) - (4 * density)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    // 1. Drop shadow / elevation glow
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#50000000")
    canvas.drawCircle(centerX, centerY + (2 * density), radius + (2f * density), paint)

    // 2. Draw Image or Fallback Scenery inside Circle Clip
    val clipPath = android.graphics.Path().apply {
        addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clipPath)

    if (imageBitmap != null && !imageBitmap.isRecycled) {
        val srcWidth = imageBitmap.width
        val srcHeight = imageBitmap.height
        val minDim = Math.min(srcWidth, srcHeight)
        val srcRect = android.graphics.Rect(
            (srcWidth - minDim) / 2,
            (srcHeight - minDim) / 2,
            (srcWidth + minDim) / 2,
            (srcHeight + minDim) / 2
        )
        val destRect = android.graphics.RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawBitmap(imageBitmap, srcRect, destRect, paint)
    } else {
        // Gradient fallback representing scenery
        val gradientColors = when (count % 4) {
            0 -> intArrayOf(
                android.graphics.Color.parseColor("#1B2A47"),
                android.graphics.Color.parseColor("#3A7BD5"),
                android.graphics.Color.parseColor("#00D2FF")
            )
            1 -> intArrayOf(
                android.graphics.Color.parseColor("#4A00E0"),
                android.graphics.Color.parseColor("#8E2DE2"),
                android.graphics.Color.parseColor("#F80759")
            )
            2 -> intArrayOf(
                android.graphics.Color.parseColor("#11998E"),
                android.graphics.Color.parseColor("#38EF7D"),
                android.graphics.Color.parseColor("#0575E6")
            )
            else -> intArrayOf(
                android.graphics.Color.parseColor("#FF416C"),
                android.graphics.Color.parseColor("#FF4B2B"),
                android.graphics.Color.parseColor("#F9D423")
            )
        }
        val shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            gradientColors, null, android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null
    }

    // 3. Vignette overlay for text legibility
    paint.color = android.graphics.Color.parseColor("#66000000")
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(centerX, centerY, radius, paint)

    canvas.restore()

    // 4. White outer stroke border
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 3.5f * density
    canvas.drawCircle(centerX, centerY, radius, paint)

    // 5. Centered White Total Count Text
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = (if (count > 99) 13 else if (count > 9) 16 else 18) * density
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.isFakeBoldText = true
    paint.setShadowLayer(4f * density, 0f, 1f * density, android.graphics.Color.BLACK)

    val textHeight = paint.descent() - paint.ascent()
    val textOffset = textHeight / 2 - paint.descent()
    canvas.drawText(count.toString(), centerX, centerY + textOffset, paint)

    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(androidBitmap)
}

fun createClusterBitmap(context: android.content.Context, count: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    return createPhotoClusterBitmap(context, count, null)
}

fun createSinglePinBitmap(context: android.content.Context): com.google.android.gms.maps.model.BitmapDescriptor {
    return createPhotoClusterBitmap(context, 1, null)
}

fun createMyLocationPinBitmap(context: android.content.Context): com.google.android.gms.maps.model.BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#10FA70")
        style = android.graphics.Paint.Style.FILL
    }
    paint.alpha = 60
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)

    paint.alpha = 255
    paint.color = android.graphics.Color.parseColor("#10FA70")
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(androidBitmap)
}

@Composable
fun JournalMapView(
    viewModel: AppViewModel,
    entries: List<JournalEntry>,
    onEntryClick: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier,
    scrollToDate: String? = null,
    onScrollToDateHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    var userLocationLatLng by remember { mutableStateOf<LatLng?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseOk = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineOk || coarseOk) {
            Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
            triggerFetchLocation(context) { lat, lng, city ->
                val userLoc = LatLng(lat, lng)
                userLocationLatLng = userLoc
                Toast.makeText(context, "Centered at $city", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedLocationEntries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var showLocationEntriesDialog by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var mapStyleMode by remember { mutableStateOf("Terrain") } // "Terrain", "Dark", "Satellite"

    val markers = remember(entries) {
        entries.mapNotNull { entry ->
            if (entry.attachmentsJson.contains("loc:")) {
                val part = entry.attachmentsJson.split(";;").find { it.trim().startsWith("loc:") }
                if (part != null) {
                    val cleanLoc = part.removePrefix("loc:")
                    val parts = cleanLoc.split("|coords:")
                    val cityName = parts.getOrNull(0) ?: ""
                    val coords = parts.getOrNull(1)?.split(",")
                    val lat = coords?.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords?.getOrNull(1)?.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        val photoPart = entry.attachmentsJson.split(";;").find { it.trim().startsWith("photo:") }
                        val photoUrl = photoPart?.removePrefix("photo:")?.trim()
                        EntryMarker(entry, entry.title, cityName, lat, lng, photoUrl)
                    } else null
                } else null
            } else null
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        if (markers.isNotEmpty()) {
            val first = markers.first()
            position = CameraPosition.fromLatLngZoom(LatLng(first.lat, first.lng), 6f)
        } else {
            position = CameraPosition.fromLatLngZoom(LatLng(37.0902, -95.7129), 4f)
        }
    }

    LaunchedEffect(scrollToDate, markers) {
        if (!scrollToDate.isNullOrEmpty() && markers.isNotEmpty()) {
            val matchedMarker = markers.find { it.entry.dateString == scrollToDate }
            if (matchedMarker != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(matchedMarker.lat, matchedMarker.lng), 14f)
                )
                Toast.makeText(context, "Centered map on entry from $scrollToDate", Toast.LENGTH_SHORT).show()
            } else {
                val closestMarker = markers.minByOrNull { m ->
                    val mDate = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(m.entry.dateString)
                    } catch (e: Exception) { null }
                    val targetDate = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(scrollToDate)
                    } catch (e: Exception) { null }
                    if (mDate != null && targetDate != null) {
                        Math.abs(mDate.time - targetDate.time)
                    } else {
                        Long.MAX_VALUE
                    }
                }
                if (closestMarker != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(closestMarker.lat, closestMarker.lng), 14f)
                    )
                    Toast.makeText(context, "Centered map on closest entry (${closestMarker.entry.dateString})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No location-tagged entries found.", Toast.LENGTH_SHORT).show()
                }
            }
            onScrollToDateHandled()
        }
    }

    val mapStyleJson = """
    [
      {
        "elementType": "geometry",
        "stylers": [
          { "color": "#121214" }
        ]
      },
      {
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#757575" }
        ]
      },
      {
        "elementType": "labels.text.stroke",
        "stylers": [
          { "color": "#212121" }
        ]
      },
      {
        "featureType": "administrative",
        "elementType": "geometry",
        "stylers": [
          { "color": "#757575" }
        ]
      },
      {
        "featureType": "water",
        "elementType": "geometry",
        "stylers": [
          { "color": "#0a1120" }
        ]
      }
    ]
    """.trimIndent()

    val mapStyleOptions = remember {
        com.google.android.gms.maps.model.MapStyleOptions(mapStyleJson)
    }

    val mapProperties = remember(mapStyleMode) {
        when (mapStyleMode) {
            "Satellite" -> MapProperties(
                mapType = MapType.SATELLITE,
                isMyLocationEnabled = false
            )
            "Dark" -> MapProperties(
                mapType = MapType.NORMAL,
                mapStyleOptions = mapStyleOptions,
                isMyLocationEnabled = false
            )
            else -> MapProperties(
                mapType = MapType.TERRAIN,
                isMyLocationEnabled = false
            )
        }
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    }

    val zoomLevel = cameraPositionState.position.zoom
    val clusters = remember(markers, zoomLevel) {
        clusterMarkers(markers, zoomLevel)
    }

    val bitmapCache = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }

    LaunchedEffect(clusters) {
        clusters.forEach { cluster ->
            val photoUrl = cluster.markers.mapNotNull { it.photoUrl }.firstOrNull()
            if (!photoUrl.isNullOrEmpty() && !bitmapCache.containsKey(photoUrl)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val loader = coil.ImageLoader(context)
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(photoUrl)
                            .allowHardware(false)
                            .build()
                        val res = loader.execute(req)
                        if (res is coil.request.SuccessResult) {
                            val bitmap = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    bitmapCache[photoUrl] = bitmap
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore fail, fall back to gradient
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings
            ) {
                clusters.forEach { cluster ->
                    val photoUrl = cluster.markers.mapNotNull { it.photoUrl }.firstOrNull()
                    val cachedBitmap = photoUrl?.let { bitmapCache[it] }
                    val count = cluster.markers.size
                    val iconDesc = remember(cluster, cachedBitmap, count) {
                        createPhotoClusterBitmap(context, count, cachedBitmap)
                    }

                    Marker(
                        state = rememberMarkerState(position = LatLng(cluster.centerLat, cluster.centerLng)),
                        icon = iconDesc,
                        onClick = {
                            val entriesList = cluster.markers.map { it.entry }
                            val name = cluster.markers.firstOrNull()?.cityName ?: "Cluster"
                            selectedLocationEntries = entriesList
                            selectedLocationName = name
                            showLocationEntriesDialog = true

                            // Smoothly zoom in towards the cluster on tap
                            coroutineScope.launch {
                                val currentZoom = cameraPositionState.position.zoom
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(cluster.centerLat, cluster.centerLng),
                                        Math.min(currentZoom + 2.5f, 18f)
                                    )
                                )
                            }
                            true
                        }
                    )
                }

                val userLoc = userLocationLatLng
                if (userLoc != null) {
                    Marker(
                        state = rememberMarkerState(position = userLoc),
                        icon = createMyLocationPinBitmap(context),
                        onClick = {
                            Toast.makeText(context, "You are here!", Toast.LENGTH_SHORT).show()
                            true
                        }
                    )
                }
            }

            // Top Floating Map Style Mode Selector
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Terrain", "Dark", "Satellite").forEach { style ->
                    val isSelected = mapStyleMode == style
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) WaterBlue else Color.Transparent)
                            .clickable { mapStyleMode = style }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = style,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Floating Zoom Controls (+ / -)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val current = cameraPositionState.position.zoom
                            cameraPositionState.animate(CameraUpdateFactory.zoomTo(current + 1.5f))
                        }
                    },
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val current = cameraPositionState.position.zoom
                            cameraPositionState.animate(CameraUpdateFactory.zoomTo(Math.max(current - 1.5f, 1f)))
                        }
                    },
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
            }

            // My Location Button Overlay on Map
            FloatingActionButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
                        triggerFetchLocation(context) { lat, lng, city ->
                            val userLoc = LatLng(lat, lng)
                            userLocationLatLng = userLoc
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(userLoc, 14f)
                                )
                            }
                            Toast.makeText(context, "Centered at $city", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                containerColor = WaterBlue,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("map_my_location_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location"
                )
            }
        }

        if (markers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "DOCUMENTED PLACES (${markers.size})",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                items(markers) { marker ->
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .clickable {
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(marker.lat, marker.lng), 14f)
                                    )
                                }
                            }
                            .testTag("map_marker_card_${marker.entry.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = WaterBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = marker.cityName,
                                    color = WaterBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = marker.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = marker.entry.dateString,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No journal entries have locations tagged yet.\nWrite a journal entry and click the GPS pin icon to add a location!",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showLocationEntriesDialog && selectedLocationEntries.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showLocationEntriesDialog = false },
            title = {
                Column {
                    Text(
                        text = "Journal Entries",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (selectedLocationName.isNotEmpty()) {
                        Text(
                            text = selectedLocationName,
                            color = WaterBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(selectedLocationEntries) { entry ->
                        Card(
                            onClick = {
                                showLocationEntriesDialog = false
                                onEntryClick(entry)
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = entry.title.ifEmpty { "Untitled" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.dateString,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.text,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationEntriesDialog = false }) {
                    Text("CLOSE", color = WaterBlue)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }
}

fun applyFormattingToTextFieldValue(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    defaultPlaceholder: String = "text"
): TextFieldValue {
    val text = value.text
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)

    return if (start in 0..text.length && end in 0..text.length && start != end) {
        val selectedText = text.substring(start, end)
        val replacement = "$prefix$selectedText$suffix"
        val newText = text.replaceRange(start, end, replacement)
        val newCursor = start + replacement.length
        TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
    } else {
        val safeStart = start.coerceIn(0, text.length)
        val replacement = "$prefix$defaultPlaceholder$suffix"
        val newText = text.replaceRange(safeStart, safeStart, replacement)
        val selectStart = safeStart + prefix.length
        val selectEnd = selectStart + defaultPlaceholder.length
        TextFieldValue(
            text = newText,
            selection = TextRange(selectStart, selectEnd)
        )
    }
}

private fun parseRichColor(colorStr: String): Color {
    val trimmed = colorStr.trim().lowercase()
    return when (trimmed) {
        "red" -> Color(0xFFEF5350)
        "blue" -> Color(0xFF42A5F5)
        "green" -> Color(0xFF66BB6A)
        "yellow" -> Color(0xFFFFCA28)
        "orange" -> Color(0xFFFFA726)
        "purple" -> Color(0xFFAB47BC)
        "pink" -> Color(0xFFEC407A)
        "waterblue", "cyan" -> WaterBlue
        "white" -> Color.White
        "black" -> Color.Black
        else -> {
            try {
                if (trimmed.startsWith("#")) {
                    val hex = trimmed.removePrefix("#")
                    val longVal = if (hex.length == 6) "FF$hex".toLong(16) else hex.toLong(16)
                    Color(longVal)
                } else {
                    WaterBlue
                }
            } catch (e: Exception) {
                WaterBlue
            }
        }
    }
}

private fun isLightColor(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance > 0.5f
}

fun parseRichText(text: String, entries: List<JournalEntry> = emptyList()): AnnotatedString {
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

            var isBullet = false
            var isOrdered = false
            var listPrefix = ""

            if (!isHeader) {
                val bulletMatch = Regex("""^\s*[-*•]\s+(.*)$""").matchEntire(currentLine)
                if (bulletMatch != null) {
                    isBullet = true
                    currentLine = bulletMatch.groupValues[1]
                } else {
                    val orderedMatch = Regex("""^\s*(\d+\.)\s+(.*)$""").matchEntire(currentLine)
                    if (orderedMatch != null) {
                        isOrdered = true
                        listPrefix = orderedMatch.groupValues[1] + " "
                        currentLine = orderedMatch.groupValues[2]
                    }
                }
            }

            currentLine = currentLine.replace("[ ]", "☐ ").replace("[x]", "☑ ").replace("[X]", "☑ ")

            if (currentLine.trim() == "---" || currentLine.trim() == "----" || currentLine.trim() == "...") {
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
            } else if (isBullet) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = WaterBlue))
                append(" • ")
                pop()
            } else if (isOrdered) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = WaterBlue))
                append(" $listPrefix")
                pop()
            }

            parseInlineMarkup(currentLine, entries)

            if (isHeader) {
                pop()
            }

            if (i < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.parseInlineMarkup(text: String, entries: List<JournalEntry> = emptyList()) {
    val regex = Regex("""\[([^\]]+)\]\((entry:\d+|journal:\d+|https?://[^\)]+)\)|<color:([^>]+)>(.*?)</color>|<bg:([^>]+)>(.*?)</bg>|<u>(.*?)</u>|__(.*?)__|~~(.*?)~~|<s>(.*?)</s>|\*\*\*(.*?)\*\*\*|\*\*(.*?)\*\*|<b>(.*?)</b>|\*(.*?)\*|<i>(.*?)</i>""")

    var lastIndex = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }

        val linkTitle = match.groups[1]?.value
        val linkTarget = match.groups[2]?.value
        val textColorHex = match.groups[3]?.value
        val textColorBody = match.groups[4]?.value
        val bgColorHex = match.groups[5]?.value
        val bgColorBody = match.groups[6]?.value
        val uBody = match.groups[7]?.value ?: match.groups[8]?.value
        val sBody = match.groups[9]?.value ?: match.groups[10]?.value
        val biBody = match.groups[11]?.value
        val bBody = match.groups[12]?.value ?: match.groups[13]?.value
        val iBody = match.groups[14]?.value ?: match.groups[15]?.value

        when {
            linkTitle != null && linkTarget != null -> {
                if (linkTarget.startsWith("entry:") || linkTarget.startsWith("journal:")) {
                    val entryIdStr = linkTarget.substringAfter(":")
                    pushStringAnnotation(tag = "JOURNAL_LINK", annotation = entryIdStr)
                    pushStyle(SpanStyle(color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
                    append("📖 $linkTitle")
                    pop()
                    pop()
                } else {
                    pushStringAnnotation(tag = "URL", annotation = linkTarget)
                    pushStyle(SpanStyle(color = WaterBlue, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
                    append(linkTitle)
                    pop()
                    pop()
                }
            }
            textColorHex != null && textColorBody != null -> {
                val parsedColor = parseRichColor(textColorHex)
                pushStyle(SpanStyle(color = parsedColor))
                parseInlineMarkup(textColorBody, entries)
                pop()
            }
            bgColorHex != null && bgColorBody != null -> {
                val parsedBg = parseRichColor(bgColorHex)
                val textColor = if (isLightColor(parsedBg)) Color.Black else Color.White
                pushStyle(SpanStyle(background = parsedBg, color = textColor))
                parseInlineMarkup(bgColorBody, entries)
                pop()
            }
            uBody != null -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                parseInlineMarkup(uBody, entries)
                pop()
            }
            sBody != null -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                parseInlineMarkup(sBody, entries)
                pop()
            }
            biBody != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                parseInlineMarkup(biBody, entries)
                pop()
            }
            bBody != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                parseInlineMarkup(bBody, entries)
                pop()
            }
            iBody != null -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                parseInlineMarkup(iBody, entries)
                pop()
            }
            else -> {
                append(match.value)
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
fun RichTextDisplay(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    entries: List<JournalEntry> = emptyList(),
    onJournalEntryClick: ((JournalEntry) -> Unit)? = null
) {
    val context = LocalContext.current
    val annotatedString = remember(text, entries) {
        parseRichText(text, entries)
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = LocalTextStyle.current.copy(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight
        ),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "JOURNAL_LINK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val entryId = annotation.item.toIntOrNull()
                    if (entryId != null) {
                        val target = entries.find { it.id == entryId }
                        if (target != null && onJournalEntryClick != null) {
                            onJournalEntryClick(target)
                        } else {
                            Toast.makeText(context, "Linked Journal entry #${annotation.item} not found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val targetByTitle = entries.find { it.title.equals(annotation.item, ignoreCase = true) }
                        if (targetByTitle != null && onJournalEntryClick != null) {
                            onJournalEntryClick(targetByTitle)
                        }
                    }
                    return@ClickableText
                }

            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        val urlStr = if (!annotation.item.startsWith("http://") && !annotation.item.startsWith("https://")) {
                            "https://${annotation.item}"
                        } else {
                            annotation.item
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open link: ${annotation.item}", Toast.LENGTH_SHORT).show()
                    }
                    return@ClickableText
                }
        }
    )
}

private fun performGeocoding(
    context: Context,
    query: String,
    onResult: (Double, Double, String) -> Unit
) {
    if (query.trim().isEmpty()) return
    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                onResult(addr.latitude, addr.longitude, addr.locality ?: addr.featureName ?: query)
            } else {
                Toast.makeText(context, "Location \"$query\" not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Geocoder is not present, check if it's coordinates "lat,lng" format
            val parts = query.split(",")
            val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lat != null && lng != null) {
                onResult(lat, lng, query)
            } else {
                Toast.makeText(context, "Geocoder unavailable on this emulator", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        // Fallback to lat,lng parsing if search is formatted as coordinate
        val parts = query.split(",")
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (lat != null && lng != null) {
            onResult(lat, lng, query)
        } else {
            Toast.makeText(context, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

