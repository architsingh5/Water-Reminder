package com.jaimatadi.waterreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Water-blue palette shared by the app screens. The chart bar colors were
// validated for lightness band, chroma, and 3:1 surface contrast in both modes.
object WaterColors {
    val ChartBarLight = Color(0xFF1E88D2)
    val ChartBarDark = Color(0xFF3E9CE0)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6FB5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E9F9),
    onPrimaryContainer = Color(0xFF0A3A5E),
    secondary = Color(0xFF4A6B8A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE9F4),
    onSecondaryContainer = Color(0xFF16344E),
    tertiary = Color(0xFF00838F),
    background = Color(0xFFF4F9FE),
    onBackground = Color(0xFF0B2A4A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B2A4A),
    surfaceVariant = Color(0xFFE4EEF7),
    onSurfaceVariant = Color(0xFF44607A),
    outline = Color(0xFF7D99B3),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC1F4),
    onPrimary = Color(0xFF06263F),
    primaryContainer = Color(0xFF124A72),
    onPrimaryContainer = Color(0xFFD3E9F9),
    secondary = Color(0xFFA8C6E0),
    onSecondary = Color(0xFF10293F),
    secondaryContainer = Color(0xFF2A455E),
    onSecondaryContainer = Color(0xFFDCE9F4),
    tertiary = Color(0xFF7BD2DC),
    background = Color(0xFF0C1924),
    onBackground = Color(0xFFE4F1FB),
    surface = Color(0xFF12212F),
    onSurface = Color(0xFFE4F1FB),
    surfaceVariant = Color(0xFF1C3247),
    onSurfaceVariant = Color(0xFF9FBBD3),
    outline = Color(0xFF64809B),
    error = Color(0xFFFFB4AB),
)

@Composable
fun WaterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}

val chartBarColor: Color
    @Composable get() = if (isSystemInDarkTheme()) WaterColors.ChartBarDark else WaterColors.ChartBarLight
