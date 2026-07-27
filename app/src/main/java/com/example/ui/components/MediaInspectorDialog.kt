package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.Media3InspectorHelper
import com.example.util.MediaInspectionResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInspectorDialog(
    mediaPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isInspecting by remember { mutableStateOf(true) }
    var inspectionResult by remember { mutableStateOf<MediaInspectionResult?>(null) }

    LaunchedEffect(mediaPath) {
        isInspecting = true
        inspectionResult = Media3InspectorHelper.inspectMedia(context, mediaPath)
        isInspecting = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF14151F),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3142))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
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
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Media3 Inspector & Demuxer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "MetadataRetriever • FrameExtractor • MediaExtractor",
                                fontSize = 10.sp,
                                color = Color(0xFFA0A0B0)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isInspecting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF64B5F6))
                            Text(
                                text = "Demuxing tracks & extracting frames...",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                } else {
                    val result = inspectionResult
                    if (result != null) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Summary Cards
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InfoCard(
                                        title = "Duration",
                                        value = "${result.durationMs / 1000}s (${result.durationMs}ms)",
                                        icon = Icons.Default.Timer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoCard(
                                        title = "Tracks",
                                        value = "${result.trackCount} Tracks",
                                        icon = Icons.Default.Audiotrack,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Format Badges & Specifications
                            item {
                                Column {
                                    Text(
                                        text = "SPECIFICATION & FORMAT DETECTION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8888A0)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FormatBadge("Motion Photo 1.0", result.isMotionPhoto, Modifier.weight(1f))
                                        FormatBadge("Ultra HDR GainMap", result.isUltraHdrGainMap, Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FormatBadge("MP4-AT Aux Tracks", result.isMp4AuxiliaryTracks, Modifier.weight(1f))
                                        FormatBadge("Eclipsa HDR 2094-50", result.isEclipsaHdr, Modifier.weight(1f))
                                    }
                                }
                            }

                            // Extracted Frame Previews
                            if (result.thumbnailBitmap != null || result.frameAt5sBitmap != null) {
                                item {
                                    Text(
                                        text = "EXTRACTED FRAMES (FrameExtractor)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8888A0)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        result.thumbnailBitmap?.let { bmp ->
                                            FramePreviewItem(
                                                label = "Thumbnail Frame",
                                                bitmap = bmp,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        result.frameAt5sBitmap?.let { bmp ->
                                            FramePreviewItem(
                                                label = "Frame @ 5s",
                                                bitmap = bmp,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Cast & Output Switcher Info
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2333)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4062))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cast,
                                            contentDescription = null,
                                            tint = Color(0xFF4FC3F7),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "CastPlayer & Output Switcher Active",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "DefaultCastOptionsProvider registered. Media can be transferred to Chromecast / Android TV devices seamlessly.",
                                                fontSize = 10.sp,
                                                color = Color(0xFFB0BEC5)
                                            )
                                        }
                                    }
                                }
                            }

                            // Detailed Demux Log
                            item {
                                Text(
                                    text = "TECHNICAL DEMUX LOGS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8888A0)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0C0D12), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF222433), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = result.details.ifEmpty { "No demux log output available." },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF81C784)
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

@Composable
private fun InfoCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3142))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, fontSize = 9.sp, color = Color(0xFFA0A0B0))
                Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun FramePreviewItem(
    label: String,
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 10.sp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(4.dp))
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Color.Black, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF33354A), RoundedCornerShape(6.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
private fun FormatBadge(
    name: String,
    detected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (detected) Color(0xFF1B382B) else Color(0xFF1E202E),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (detected) Color(0xFF4CAF50) else Color(0xFF2E3142)
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (detected) Color(0xFF4CAF50) else Color(0xFF757575),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (detected) Color(0xFFE8F5E9) else Color(0xFF9E9E9E)
            )
        }
    }
}
