package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

object YouTubeUrlParser {
    /**
     * Finds and extracts YouTube video IDs from a block of text.
     */
    fun findYouTubeVideoIds(text: String): List<String> {
        val regex = "(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)\\/(?:watch\\?v=|embed\\/|shorts\\/|v\\/)?([a-zA-Z0-9_-]{11})".toRegex()
        return regex.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }
}

@Composable
fun YouTubeThumbnailView(
    videoId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Standard YouTube high-quality thumbnail URL
    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    Card(
        modifier = modifier
            .width(160.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141C))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Video Thumbnail Image
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "YouTube Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            // Play icon in the center
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.Red.copy(alpha = 0.85f), shape = RoundedCornerShape(50))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play YouTube Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Small Youtube badge in bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "YouTube",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun YouTubeLinkParserAndRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val videoIds = remember(text) { YouTubeUrlParser.findYouTubeVideoIds(text) }
    if (videoIds.isEmpty()) return

    var activeVideoId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "Detected YouTube Video${if (videoIds.size > 1) "s" else ""}:",
            color = Color(0xFF00FFCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Row of detected videos
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            videoIds.forEach { videoId ->
                YouTubeThumbnailView(
                    videoId = videoId,
                    onClick = { activeVideoId = videoId }
                )
            }
        }
    }

    activeVideoId?.let { videoId ->
        YouTubePlayerDialog(
            videoId = videoId,
            onDismiss = { activeVideoId = null }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerDialog(
    videoId: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Custom responsive sizing
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF13141C),
            border = BorderStroke(1.dp, Color(0xFF00C6FF).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "In-App YouTube Player",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = Color.White
                        )
                    }
                }

                // WebView embedding the video in a 16:9 box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&fs=1")
                            }
                        },
                        update = { webView ->
                            // Optional: update logic if videoId changes
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Playing directly within your secure workspace",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
