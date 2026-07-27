package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.collectAsState

private val DarkColorScheme =
  darkColorScheme(
    primary = WaterBlue,
    onPrimary = Color(0xFF000000),
    secondary = WaterBlueAccent,
    background = DeepSlate,
    surface = Charcoal,
    surfaceVariant = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextPrimary
  )

@Composable
fun MyApplicationTheme(
  // Force Ultra-Dark mode as requested
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always use the beautiful, custom-designed Accountability Arena color scheme
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
