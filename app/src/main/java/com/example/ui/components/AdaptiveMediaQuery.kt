package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// COMPOSE UI FLAGS & CONFIGURATION
// ============================================================================

object ComposeUiFlags {
    var isMediaQueryIntegrationEnabled: Boolean = true
}

// ============================================================================
// UI MEDIA SCOPE INTERFACE & TYPES
// ============================================================================

@RequiresOptIn(message = "mediaQuery API is experimental and subject to change.")
annotation class ExperimentalMediaQueryApi

interface UiMediaScope {
    enum class Posture { Flat, Tabletop, Book }
    enum class PointerPrecision { Fine, Coarse, Blunt, None }
    enum class KeyboardKind { Physical, Virtual, None }
    enum class ViewingDistance { Near, Medium, Far }

    val windowWidth: Dp
    val windowHeight: Dp
    val windowPosture: Posture
    val pointerPrecision: PointerPrecision
    val keyboardKind: KeyboardKind
    val hasCamera: Boolean
    val hasMicrophone: Boolean
    val viewingDistance: ViewingDistance
}

data class MutableUiMediaScope(
    override var windowWidth: Dp = 390.dp,
    override var windowHeight: Dp = 844.dp,
    override var windowPosture: UiMediaScope.Posture = UiMediaScope.Posture.Flat,
    override var pointerPrecision: UiMediaScope.PointerPrecision = UiMediaScope.PointerPrecision.Fine,
    override var keyboardKind: UiMediaScope.KeyboardKind = UiMediaScope.KeyboardKind.Virtual,
    override var hasCamera: Boolean = true,
    override var hasMicrophone: Boolean = true,
    override var viewingDistance: UiMediaScope.ViewingDistance = UiMediaScope.ViewingDistance.Near
) : UiMediaScope

val LocalUiMediaScope = staticCompositionLocalOf<UiMediaScope> {
    MutableUiMediaScope()
}

// ============================================================================
// MEDIA QUERY FUNCTIONS
// ============================================================================

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
fun <T> mediaQuery(query: UiMediaScope.() -> T): T {
    val scope = LocalUiMediaScope.current
    return scope.query()
}

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
fun <T> derivedMediaQuery(query: UiMediaScope.() -> T): State<T> {
    val scope = LocalUiMediaScope.current
    return remember(scope) {
        derivedStateOf { scope.query() }
    }
}

// ============================================================================
// WINDOW SIZE CLASS CONSTANTS
// ============================================================================

object WindowSizeClass {
    const val WIDTH_DP_COMPACT_UPPER_BOUND = 600
    const val WIDTH_DP_MEDIUM_LOWER_BOUND = 600
    const val WIDTH_DP_MEDIUM_UPPER_BOUND = 840
    const val WIDTH_DP_EXPANDED_LOWER_BOUND = 840
    const val WIDTH_DP_LARGE_LOWER_BOUND = 1200
    const val WIDTH_DP_EXTRA_LARGE_LOWER_BOUND = 1600

    const val HEIGHT_DP_COMPACT_UPPER_BOUND = 480
    const val HEIGHT_DP_MEDIUM_LOWER_BOUND = 480
    const val HEIGHT_DP_MEDIUM_UPPER_BOUND = 900
    const val HEIGHT_DP_EXPANDED_LOWER_BOUND = 900

    fun getWidthCategory(widthDp: Dp): String {
        val w = widthDp.value.toInt()
        return when {
            w < WIDTH_DP_MEDIUM_LOWER_BOUND -> "Compact (< 600dp)"
            w < WIDTH_DP_EXPANDED_LOWER_BOUND -> "Medium (600dp - 840dp)"
            w < WIDTH_DP_LARGE_LOWER_BOUND -> "Expanded (840dp - 1200dp)"
            w < WIDTH_DP_EXTRA_LARGE_LOWER_BOUND -> "Large (1200dp - 1600dp)"
            else -> "Extra Large (>= 1600dp)"
        }
    }

    fun getHeightCategory(heightDp: Dp): String {
        val h = heightDp.value.toInt()
        return when {
            h < HEIGHT_DP_MEDIUM_LOWER_BOUND -> "Compact (< 480dp)"
            h < HEIGHT_DP_EXPANDED_LOWER_BOUND -> "Medium (480dp - 900dp)"
            else -> "Expanded (>= 900dp)"
        }
    }
}
