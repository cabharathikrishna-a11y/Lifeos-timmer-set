package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Create a modern set of Compose premium graphics and animation helpers
object PremiumEffects {
    // 1. Premium bouncy spring press reaction with 500ms default debounce protection
    fun Modifier.bouncyClick(
        stiffness: Float = Spring.StiffnessMediumLow,
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        debounceIntervalMs: Long = 500L,
        onClick: () -> Unit
    ): Modifier = composed {
        var isPressed by remember { mutableStateOf(false) }
        var lastClickTime by remember { mutableStateOf(0L) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.94f else 1.0f,
            animationSpec = spring(
                dampingRatio = dampingRatio,
                stiffness = stiffness
            ),
            label = "bouncy_press_scale"
        )
        
        this
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime >= debounceIntervalMs) {
                            lastClickTime = currentTime
                            onClick()
                        }
                    }
                )
            }
    }

    // 2. Premium glowing pulse outer light helper
    fun Modifier.pulseGlow(
        color: Color = WaterBlue,
        enabled: Boolean = true,
        durationMillis: Int = 1800
    ): Modifier = composed {
        if (!enabled) return@composed this
        
        val transition = rememberInfiniteTransition(label = "pulse_glow_transition")
        val glowScale by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_scale"
        )
        val alpha by transition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )
        
        this.drawWithContent {
            drawContent()
            // Draw a subtle outer diffused breathing border / glow overlay
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = size.maxDimension * 0.52f * glowScale,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }

    // 3. Futuristic glassmorphism card modifier
    fun Modifier.glassmorphicCard(
        shape: Shape = RoundedCornerShape(16.dp),
        borderWidth: Dp = 1.dp,
        borderColor: Color = Color(0x18FFFFFF),
        backgroundColor: Color = Color(0xCC08080C)
    ): Modifier = this
        .clip(shape)
        .background(backgroundColor)
        .border(width = borderWidth, color = borderColor, shape = shape)
}
