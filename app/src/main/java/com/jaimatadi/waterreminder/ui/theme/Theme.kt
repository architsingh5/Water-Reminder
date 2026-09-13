package com.jaimatadi.waterreminder.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.jaimatadi.waterreminder.data.ThemeMode

// ---------------------------------------------------------------------------
// Two hand-tuned palettes: "Aqua Day" (clean off-white) and "OLED Night"
// (true black with a cyan glow). Both share the same teal/cyan hue family so
// the app reads as one brand when the user flips between them.
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A7C8E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCF3F8),
    onPrimaryContainer = Color(0xFF003640),
    secondary = Color(0xFF4F6B72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9EBEF),
    onSecondaryContainer = Color(0xFF0B1F24),
    tertiary = Color(0xFF0F8A5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8F5E0),
    onTertiaryContainer = Color(0xFF00341F),
    background = Color(0xFFF6FAFB),
    onBackground = Color(0xFF0F1B1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1B1E),
    surfaceVariant = Color(0xFFE6EFF2),
    onSurfaceVariant = Color(0xFF4B6168),
    outline = Color(0xFF7B929A),
    outlineVariant = Color(0xFFCBD9DE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2EDDF5),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF0A3F4A),
    onPrimaryContainer = Color(0xFFBDF3FA),
    secondary = Color(0xFFA7C9D1),
    onSecondary = Color(0xFF10292F),
    secondaryContainer = Color(0xFF223A41),
    onSecondaryContainer = Color(0xFFD9EBEF),
    tertiary = Color(0xFF4ADE9C),
    onTertiary = Color(0xFF003320),
    tertiaryContainer = Color(0xFF0B4A32),
    onTertiaryContainer = Color(0xFFC8F5E0),
    // Pure black background so OLED pixels switch off; cards sit just above it.
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F5F6),
    surface = Color(0xFF0D0F10),
    onSurface = Color(0xFFF2F5F6),
    surfaceVariant = Color(0xFF1A1E20),
    onSurfaceVariant = Color(0xFF9AA9AE),
    outline = Color(0xFF4A565A),
    outlineVariant = Color(0xFF262C2F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colors outside the Material scheme: the hero card gradient and chart accent. */
data class WaterPalette(
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    /** Ring/check color once the daily goal is met. */
    val goalAccent: Color,
    val chartBar: Color,
)

private val LightPalette = WaterPalette(
    heroStart = Color(0xFF0B6B7C),
    heroEnd = Color(0xFF1096AD),
    onHero = Color(0xFFFFFFFF),
    goalAccent = Color(0xFFA7F3D0),
    chartBar = Color(0xFF0A7C8E),
)

private val DarkPalette = WaterPalette(
    heroStart = Color(0xFF041E24),
    heroEnd = Color(0xFF0A3F4A),
    onHero = Color(0xFFD8F8FD),
    goalAccent = Color(0xFF4ADE9C),
    chartBar = Color(0xFF2EDDF5),
)

val LocalWaterPalette = staticCompositionLocalOf { LightPalette }

/** Resolves the user's theme preference to an actual light/dark decision. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * App theme. [themeMode] picks light/dark (or follows the system);
 * [dynamicColor] swaps in the wallpaper-derived Material You palette on
 * Android 12+. Colors cross-fade when either changes.
 */
@Composable
fun WaterTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val useDynamic = dynamicColor && supportsDynamicColor()
    val target: ColorScheme = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val palette = when {
        useDynamic -> WaterPalette(
            heroStart = target.primary,
            heroEnd = lerp(target.primary, target.primaryContainer, 0.38f),
            onHero = target.onPrimary,
            goalAccent = target.tertiaryContainer,
            chartBar = target.primary,
        )
        dark -> DarkPalette
        else -> LightPalette
    }

    CompositionLocalProvider(LocalWaterPalette provides palette.animated()) {
        MaterialTheme(colorScheme = target.animated(), content = content)
    }
}

// Animate the handful of colors that cover most of the screen so switching
// theme feels like a fade rather than a flash.
@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = anim(primary),
    onPrimary = anim(onPrimary),
    primaryContainer = anim(primaryContainer),
    onPrimaryContainer = anim(onPrimaryContainer),
    secondaryContainer = anim(secondaryContainer),
    onSecondaryContainer = anim(onSecondaryContainer),
    tertiary = anim(tertiary),
    tertiaryContainer = anim(tertiaryContainer),
    onTertiaryContainer = anim(onTertiaryContainer),
    background = anim(background),
    onBackground = anim(onBackground),
    surface = anim(surface),
    onSurface = anim(onSurface),
    surfaceVariant = anim(surfaceVariant),
    onSurfaceVariant = anim(onSurfaceVariant),
    outline = anim(outline),
    outlineVariant = anim(outlineVariant),
)

@Composable
private fun WaterPalette.animated(): WaterPalette = WaterPalette(
    heroStart = anim(heroStart),
    heroEnd = anim(heroEnd),
    onHero = anim(onHero),
    goalAccent = anim(goalAccent),
    chartBar = anim(chartBar),
)

@Composable
private fun anim(color: Color): Color {
    val value by animateColorAsState(color, animationSpec = tween(durationMillis = 450), label = "themeColor")
    return value
}

val chartBarColor: Color
    @Composable get() = LocalWaterPalette.current.chartBar

val heroGradient: Brush
    @Composable get() {
        val palette = LocalWaterPalette.current
        return Brush.linearGradient(
            colors = listOf(palette.heroStart, palette.heroEnd),
            start = Offset(0f, 0f),
            end = Offset(900f, 1400f),
        )
    }
