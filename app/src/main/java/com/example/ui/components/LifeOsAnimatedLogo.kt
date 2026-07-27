package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
@Composable
fun LifeOsAnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fontSize: TextUnit = 18.sp,
    showSubtext: Boolean = true
) {
    // 1. Infinite rotation for background neon halo ring
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 2. Pulse scale for inner core
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 3. Shimmer offset for gradient text
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val gradientColors = listOf(
        Color(0xFF38BDF8), // Cyan
        Color(0xFF818CF8), // Indigo
        Color(0xFFC084FC), // Purple
        Color(0xFFF43F5E), // Rose
        Color(0xFF38BDF8)  // Cyan loop
    )

    val animatedBrush = Brush.linearGradient(
        colors = gradientColors,
        start = androidx.compose.ui.geometry.Offset(shimmerOffset, 0f),
        end = androidx.compose.ui.geometry.Offset(shimmerOffset + 300f, 300f)
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Logo Emblem
        Box(
            modifier = Modifier
                .size(size)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Outer Ring Halo
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotationAngle }
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF818CF8),
                                Color(0xFFEC4899),
                                Color(0xFF38BDF8)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Inner Glowing Core
            Box(
                modifier = Modifier
                    .size(size * 0.78f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E1B4B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    style = TextStyle(
                        brush = animatedBrush,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.42f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                )
            }
        }

        // Text Badge
        Column(verticalArrangement = Arrangement.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "LIFE OS",
                    style = TextStyle(
                        brush = animatedBrush,
                        fontWeight = FontWeight.Black,
                        fontSize = fontSize,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                )
                
                // Small Pro / Active Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "v2.5",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showSubtext) {
                Text(
                    text = "INTEGRATED FOCUS & COMMUNITY HUB",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
