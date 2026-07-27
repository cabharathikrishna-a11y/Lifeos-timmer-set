package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.AppViewModel
import com.example.ui.SharedFileIntentData
import com.example.ui.theme.WaterBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SharedFileReceiverOverlay(
    viewModel: AppViewModel,
    sharedIntent: SharedFileIntentData
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    when (sharedIntent.flowType) {
        "journal" -> {
            val isPhotoOrVideo = sharedIntent.mimeType.startsWith("image/") || sharedIntent.mimeType.startsWith("video/")
            if (isPhotoOrVideo) {
                // Auto-save flow for photos/videos
                LaunchedEffect(sharedIntent) {
                    isProcessing = true
                    try {
                        val targetDate = getMediaDateString(context, sharedIntent.uri)
                        val finalPath = viewModel.getUploadedOrLocalPath(context, sharedIntent.uri, sharedIntent.name, sharedIntent.mimeType)
                        val prefix = if (sharedIntent.mimeType.startsWith("image/")) "photo:" else "video:"
                        val attachmentStr = "$prefix$finalPath"

                        val entries = viewModel.journalEntries.value
                        val existing = entries.find { it.dateString == targetDate }
                        if (existing != null) {
                            val currentAttachments = if (existing.attachmentsJson.isNotEmpty()) {
                                existing.attachmentsJson.split(";;")
                            } else {
                                emptyList()
                            }
                            val updatedAttachments = currentAttachments + attachmentStr
                            val updated = existing.copy(attachmentsJson = updatedAttachments.joinToString(";;"))
                            viewModel.updateJournalEntry(updated)
                        } else {
                            viewModel.createJournalEntryWithId(
                                title = "Journal Entry",
                                text = "Shared Media",
                                dateString = targetDate,
                                timestamp = System.currentTimeMillis(),
                                attachments = attachmentStr
                            )
                        }
                        Toast.makeText(context, "Successfully saved media to Journal for $targetDate!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("SharedFile", "Failed to auto-save shared media to journal", e)
                        Toast.makeText(context, "Error saving to Journal: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isProcessing = false
                        viewModel.setSharedFileIntent(null)
                    }
                }

                // Show loading overlay
                Dialog(onDismissRequest = {}) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color(0xFF101014), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = WaterBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Saving media...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Show custom Calendar picker for other file types
                if (isProcessing) {
                    Dialog(onDismissRequest = {}) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .background(Color(0xFF101014), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = WaterBlue)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Attaching file...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    CustomCalendarPicker(
                        onDateSelected = { targetDate ->
                            scope.launch {
                                isProcessing = true
                                try {
                                    val finalPath = viewModel.getUploadedOrLocalPath(context, sharedIntent.uri, sharedIntent.name, sharedIntent.mimeType)
                                    val attachmentStr = "file:${sharedIntent.name}|path:$finalPath"

                                    val entries = viewModel.journalEntries.value
                                    val existing = entries.find { it.dateString == targetDate }
                                    if (existing != null) {
                                        val currentAttachments = if (existing.attachmentsJson.isNotEmpty()) {
                                            existing.attachmentsJson.split(";;")
                                        } else {
                                            emptyList()
                                        }
                                        val updatedAttachments = currentAttachments + attachmentStr
                                        val updated = existing.copy(attachmentsJson = updatedAttachments.joinToString(";;"))
                                        viewModel.updateJournalEntry(updated)
                                    } else {
                                        viewModel.createJournalEntryWithId(
                                            title = "Journal Entry",
                                            text = "Attached File",
                                            dateString = targetDate,
                                            timestamp = System.currentTimeMillis(),
                                            attachments = attachmentStr
                                        )
                                    }
                                    Toast.makeText(context, "Successfully attached to Journal for $targetDate!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Log.e("SharedFile", "Failed to save file to journal", e)
                                    Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isProcessing = false
                                    viewModel.setSharedFileIntent(null)
                                }
                            }
                        },
                        onDismiss = {
                            viewModel.setSharedFileIntent(null)
                        }
                    )
                }
            }
        }

        "shared_folder" -> {
            var currentFolderPath by remember { mutableStateOf("") }
            val pathStack = remember { mutableStateListOf<String>() }
            val dbFilesState by viewModel.files.collectAsState()

            // Calculate active list based on pathStack
            val currentPath = if (pathStack.isEmpty()) "" else pathStack.joinToString("/")

            val foldersList = remember(currentPath, dbFilesState) {
                if (currentPath.isEmpty()) {
                    listOf("General", "Friends", "Journal", "Tasks", "Contacts")
                } else {
                    dbFilesState.filter {
                        it.mimeType == "inode/directory" && it.path == currentPath
                    }.map { it.name }
                }
            }

            val filesList = remember(currentPath, dbFilesState) {
                if (currentPath.isEmpty()) {
                    emptyList()
                } else {
                    dbFilesState.filter {
                        it.mimeType != "inode/directory" && it.path == currentPath
                    }
                }
            }

            Dialog(onDismissRequest = { viewModel.setSharedFileIntent(null) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Save to Shared Folder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            IconButton(onClick = { viewModel.setSharedFileIntent(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Path / Breadcrumb with back navigation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pathStack.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = WaterBlue,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            pathStack.removeLast()
                                        }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "Root" + (if (currentPath.isEmpty()) "" else " / $currentPath"),
                                color = WaterBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Folder and Files List
                        Box(modifier = Modifier.weight(1f)) {
                            if (foldersList.isEmpty() && filesList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("This folder is empty", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // List Directories
                                    foldersList.forEach { folderName ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF16161B))
                                                .clickable {
                                                    pathStack.add(folderName)
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Folder",
                                                tint = Color(0xFFFFB74D),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = folderName,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // List Files (Non-clickable/disabled, for context only)
                                    filesList.forEach { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF16161B).copy(alpha = 0.5f))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.InsertDriveFile,
                                                contentDescription = "File",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    color = Color.LightGray,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = file.mimeType,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = WaterBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected Folder Actions
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        viewModel.addFile(
                                            name = sharedIntent.name,
                                            path = currentPath,
                                            size = 0L,
                                            mimeType = sharedIntent.mimeType,
                                            uriString = sharedIntent.uri.toString()
                                        )
                                        Toast.makeText(context, "Uploaded successfully to: ${currentPath.ifEmpty { "Root" }}", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Log.e("SharedFile", "Failed to upload to shared folder", e)
                                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                        viewModel.setSharedFileIntent(null)
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "SELECT THIS FOLDER & UPLOAD",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        "private_note" -> {
            LaunchedEffect(sharedIntent) {
                isProcessing = true
                try {
                    val finalPath = viewModel.getUploadedOrLocalPath(context, sharedIntent.uri, sharedIntent.name, sharedIntent.mimeType)
                    viewModel.insertKeepNote(
                        title = sharedIntent.name,
                        content = "Attachment: $finalPath"
                    )
                    Toast.makeText(context, "Private Note created with shared attachment!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("SharedFile", "Failed to save private note attachment", e)
                    Toast.makeText(context, "Error saving Private Note: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                    viewModel.setSharedFileIntent(null)
                }
            }

            // Show progress
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color(0xFF101014), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = WaterBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Creating Note...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomCalendarPicker(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCal by remember { mutableStateOf(Calendar.getInstance()) }
    var displayedMonthCal by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Journal Date",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Month/Year navigation header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    IconButton(onClick = {
                        displayedMonthCal = (displayedMonthCal.clone() as Calendar).apply {
                            add(Calendar.MONTH, -1)
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Prev Month", tint = Color.White)
                    }
                    Text(
                        text = monthFormat.format(displayedMonthCal.time),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = {
                        displayedMonthCal = (displayedMonthCal.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                    }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = Color.White)
                    }
                }

                // Days of week header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val days = listOf("S", "M", "T", "W", "T", "F", "S")
                    days.forEach { day ->
                        Text(
                            text = day,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days grid
                val daysInMonth = displayedMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayOfWeek = displayedMonthCal.get(Calendar.DAY_OF_WEEK) - 1

                val totalCells = daysInMonth + firstDayOfWeek
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - firstDayOfWeek + 1
                            if (dayNum in 1..daysInMonth) {
                                val isSelected = selectedCal.get(Calendar.YEAR) == displayedMonthCal.get(Calendar.YEAR) &&
                                        selectedCal.get(Calendar.MONTH) == displayedMonthCal.get(Calendar.MONTH) &&
                                        selectedCal.get(Calendar.DAY_OF_MONTH) == dayNum

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color.Transparent)
                                        .clickable {
                                            selectedCal = (displayedMonthCal.clone() as Calendar).apply {
                                                set(Calendar.DAY_OF_MONTH, dayNum)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    onDateSelected(sdf.format(selectedCal.time))
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
            ) {
                Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF101014),
        shape = RoundedCornerShape(16.dp)
    )
}

fun getMediaDateString(context: Context, uri: Uri): String {
    var dateStr = ""
    try {
        val proj = arrayOf(
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
            val takenIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            if (cursor.moveToFirst()) {
                var millis = 0L
                if (takenIdx != -1) {
                    millis = cursor.getLong(takenIdx)
                }
                if (millis <= 0 && modIdx != -1) {
                    millis = cursor.getLong(modIdx) * 1000L
                }
                if (millis > 0) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    dateStr = sdf.format(Date(millis))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("SharedFile", "Failed to query media date from MediaStore", e)
    }
    if (dateStr.isEmpty()) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateStr = sdf.format(Date())
    }
    return dateStr
}
