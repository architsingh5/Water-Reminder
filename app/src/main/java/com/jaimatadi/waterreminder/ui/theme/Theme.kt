package com.jaimatadi.waterreminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

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
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDEFF3),
    onTertiaryContainer = Color(0xFF00363B),
    background = Color(0xFFF2F7FC),
    onBackground = Color(0xFF0B2A4A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B2A4A),
    surfaceVariant = Color(0xFFE4EEF7),
    onSurfaceVariant = Color(0xFF44607A),
    outline = Color(0xFF7D99B3),
    outlineVariant = Color(0xFFC5D6E6),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
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
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF004F55),
    onTertiaryContainer = Color(0xFFBDEFF3),
    background = Color(0xFF0C1924),
    onBackground = Color(0xFFE4F1FB),
    surface = Color(0xFF12212F),
    onSurface = Color(0xFFE4F1FB),
    surfaceVariant = Color(0xFF1C3247),
    onSurfaceVariant = Color(0xFF9FBBD3),
    outline = Color(0xFF64809B),
    outlineVariant = Color(0xFF2E4A63),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * App theme. [dynamicColor] switches to the wallpaper-derived Material You
 * palette on Android 12+; otherwise (and on older phones) the water-blue brand
 * palette is used.
 */
@Composable
fun WaterTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Bars in the weekly chart. Primary keeps ≥3:1 against surface in both palettes. */
val chartBarColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/**
 * Hero card background. Stays close enough to `primary` that `onPrimary` text
 * keeps its contrast everywhere on the card, in both light and dark palettes.
 */
val heroGradient: Brush
    @Composable get() {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            colors = listOf(
                scheme.primary,
                lerp(scheme.primary, scheme.primaryContainer, 0.38f),
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 1400f),
        )
    }
