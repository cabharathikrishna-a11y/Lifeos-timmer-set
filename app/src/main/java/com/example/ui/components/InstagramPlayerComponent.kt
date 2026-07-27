package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object InstagramUrlParser {
    data class InstagramLink(
        val originalUrl: String,
        val type: Type,
        val identifier: String
    ) {
        enum class Type {
            POST, REEL, PROFILE
        }
    }

    private val RESERVED_WORDS = setOf("p", "reel", "stories", "explore", "developer", "about", "blog", "press", "api", "jobs", "privacy", "terms")

    fun findInstagramLinks(text: String): List<InstagramLink> {
        // Regex to match instagram links
        val regex = "https?:\\/\\/(?:www\\.)?instagram\\.com\\/([a-zA-Z0-9_.-]+)\\/?([a-zA-Z0-9_.-]+)?\\/?".toRegex()
        return regex.findAll(text).mapNotNull { match ->
            val firstSegment = match.groupValues[1]
            val secondSegment = match.groupValues.getOrNull(2) ?: ""
            val original = match.value

            when {
                firstSegment == "p" && secondSegment.isNotEmpty() -> {
                    InstagramLink(original, InstagramLink.Type.POST, secondSegment)
                }
                firstSegment == "reel" && secondSegment.isNotEmpty() -> {
                    InstagramLink(original, InstagramLink.Type.REEL, secondSegment)
                }
                firstSegment.isNotEmpty() && !RESERVED_WORDS.contains(firstSegment.lowercase()) -> {
                    InstagramLink(original, InstagramLink.Type.PROFILE, firstSegment)
                }
                else -> null
            }
        }.distinctBy { it.originalUrl }.toList()
    }
}

@Composable
fun InstagramPreviewCard(
    link: InstagramUrlParser.InstagramLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Beautiful Instagram brand color gradient
    val instagramGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF833AB4), // Purple
                Color(0xFFFD1D1D), // Red
                Color(0xFFFCAF45)  // Yellow
            )
        )
    }

    Card(
        modifier = modifier
            .width(160.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141C))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1016))
        ) {
            // Instagram styled top bar gradient accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(instagramGradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(instagramGradient, shape = RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Text(
                        text = when (link.type) {
                            InstagramUrlParser.InstagramLink.Type.POST -> "Instagram Post"
                            InstagramUrlParser.InstagramLink.Type.REEL -> "Instagram Reel"
                            InstagramUrlParser.InstagramLink.Type.PROFILE -> "Instagram Profile"
                        },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Center descriptor
                Text(
                    text = when (link.type) {
                        InstagramUrlParser.InstagramLink.Type.POST,
                        InstagramUrlParser.InstagramLink.Type.REEL -> "Code: ${link.identifier}"
                        InstagramUrlParser.InstagramLink.Type.PROFILE -> "@${link.identifier}"
                    },
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                // Tiny action guide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap to load",
                        color = Color(0xFF00FFCC),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (link.type == InstagramUrlParser.InstagramLink.Type.REEL) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramLinkParserAndRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val links = remember(text) { InstagramUrlParser.findInstagramLinks(text) }
    if (links.isEmpty()) return

    var activeLink by remember { mutableStateOf<InstagramUrlParser.InstagramLink?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "Detected Instagram Content:",
            color = Color(0xFFFF5E62),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            links.forEach { link ->
                InstagramPreviewCard(
                    link = link,
                    onClick = { activeLink = link }
                )
            }
        }
    }

    activeLink?.let { link ->
        InstagramEmbedDialog(
            link = link,
            onDismiss = { activeLink = null }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramEmbedDialog(
    link: InstagramUrlParser.InstagramLink,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF13141C),
            border = BorderStroke(1.dp, Color(0xFFFF5E62).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        text = when (link.type) {
                            InstagramUrlParser.InstagramLink.Type.POST -> "Instagram Post Viewer"
                            InstagramUrlParser.InstagramLink.Type.REEL -> "Instagram Reel Viewer"
                            InstagramUrlParser.InstagramLink.Type.PROFILE -> "Instagram Profile"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
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

                // WebView embedding the video/post in a container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                                loadUrl(link.originalUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Loaded securely within your session",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
