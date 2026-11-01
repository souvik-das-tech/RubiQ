// Colours and theme, matching the web app's dark indigo/cyan look.
package com.bbcl.rubiq

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppColors {
    val bgPrimary = Color(0xFF070B14)
    val bgSecondary = Color(0xFF0D1527)
    val bgTertiary = Color(0xFF131F38)
    val panelBorder = Color(0x336366F1)
    val textMain = Color(0xFFF1F5F9)
    val textMuted = Color(0xFF94A3B8)
    val textDim = Color(0xFF64748B)
    val cyan = Color(0xFF06B6D4)
    val blue = Color(0xFF3B82F6)
    val indigo = Color(0xFF6366F1)
    val purple = Color(0xFFA855F7)
    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val rose = Color(0xFFF43F5E)

    val accentGradient = Brush.horizontalGradient(listOf(indigo, cyan))
}

/** Sticker colours by face letter ('X' = unpainted). */
val cubeColors = mapOf(
    'U' to Color(0xFFF8FAFC), // White
    'D' to Color(0xFFFACC15), // Yellow
    'F' to Color(0xFF22C55E), // Green
    'B' to Color(0xFF2563EB), // Blue
    'R' to Color(0xFFEF4444), // Red
    'L' to Color(0xFFF97316), // Orange
    'X' to Color(0xFF334155), // Unpainted
)

fun stickerColor(code: Char): Color = cubeColors[code] ?: cubeColors.getValue('X')

private val scheme = darkColorScheme(
    primary = AppColors.indigo,
    onPrimary = Color.White,
    secondary = AppColors.cyan,
    secondaryContainer = Color(0xFF26305A),
    onSecondaryContainer = AppColors.textMain,
    background = AppColors.bgPrimary,
    onBackground = AppColors.textMain,
    surface = AppColors.bgSecondary,
    onSurface = AppColors.textMain,
    surfaceContainer = AppColors.bgTertiary,
    onSurfaceVariant = AppColors.textMuted,
    outline = AppColors.panelBorder,
)

@Composable
fun RubiQTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
