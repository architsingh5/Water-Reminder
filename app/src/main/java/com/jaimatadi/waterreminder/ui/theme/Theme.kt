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
// Two hand-tuned palettes from the Claude Design handoff: "Aqua Day" (clean
// off-white) and "OLED Night" (true black with a cyan glow). Both share the
// same teal/cyan hue family so the app reads as one brand in either mode.
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A7C8E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCF3F8),
    onPrimaryContainer = Color(0xFF003640),
    secondary = Color(0xFF4F6B72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F4F6),
    onSecondaryContainer = Color(0xFF0B1F24),
    tertiary = Color(0xFF1B6B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4F5EA),
    onTertiaryContainer = Color(0xFF1B6B3A),
    background = Color(0xFFF6FAFB),
    onBackground = Color(0xFF0F1F23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1F23),
    surfaceVariant = Color(0xFFE6F4F6),
    onSurfaceVariant = Color(0xFF5B7378),
    outline = Color(0xFF5B7378),
    outlineVariant = Color(0xFFE3ECEE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2EDDF5),
    onPrimary = Color(0xFF04242A),
    primaryContainer = Color(0xFF0A3F4A),
    onPrimaryContainer = Color(0xFFBDF3FA),
    secondary = Color(0xFFA7C9D1),
    onSecondary = Color(0xFF10292F),
    secondaryContainer = Color(0xFF0F2A30),
    onSecondaryContainer = Color(0xFFD9EBEF),
    tertiary = Color(0xFF4FD48A),
    onTertiary = Color(0xFF003320),
    tertiaryContainer = Color(0xFF0B1F13),
    onTertiaryContainer = Color(0xFF4FD48A),
    // Pure black background so OLED pixels switch off; cards sit just above it.
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F7F8),
    surface = Color(0xFF0D0F10),
    onSurface = Color(0xFFF2F7F8),
    surfaceVariant = Color(0xFF0F2A30),
    onSurfaceVariant = Color(0xFF8FA6AB),
    outline = Color(0xFF8FA6AB),
    outlineVariant = Color(0xFF1C2224),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colors outside the Material scheme: hero gradient, water, glows, borders. */
data class WaterPalette(
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    /** Progress arc and the wave crest line inside the ring. */
    val ring: Color,
    val waterFront: Color,
    val waterBack: Color,
    /** "Log water" button on the hero. */
    val logButtonBg: Color,
    val logButtonFg: Color,
    /** Shadow/glow under the log button and selected chart bubble. */
    val glow: Color,
    /** 1 px card outline; transparent in light where cards use a soft shadow instead. */
    val cardBorder: Color,
    val cardShadow: Color,
    val streakFlame: Color,
    val chartBar: Color,
)

private val LightPalette = WaterPalette(
    heroStart = Color(0xFF0B7A8C),
    heroEnd = Color(0xFF06525F),
    onHero = Color(0xFFFFFFFF),
    ring = Color(0xFF9BEAF5),
    waterFront = Color(0xFF03404B),
    waterBack = Color(0xFF0A5C6B),
    logButtonBg = Color(0xFFFEFEFE),
    logButtonFg = Color(0xFF0A7C8E),
    glow = Color(0x480A7C8E),
    cardBorder = Color.Transparent,
    cardShadow = Color(0x1A0A7C8E),
    streakFlame = Color(0xFFFFB35C),
    chartBar = Color(0xFF0A7C8E),
)

private val DarkPalette = WaterPalette(
    heroStart = Color(0xFF041E24),
    heroEnd = Color(0xFF0A3F4A),
    onHero = Color(0xFFFFFFFF),
    ring = Color(0xFF2EDDF5),
    waterFront = Color(0xFF053A44),
    waterBack = Color(0xFF0F6E7C),
    logButtonBg = Color(0xFF2EDDF5),
    logButtonFg = Color(0xFF04242A),
    glow = Color(0x592EDDF5),
    cardBorder = Color(0xFF1C2224),
    cardShadow = Color.Transparent,
    streakFlame = Color(0xFFFFB35C),
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
        useDynamic -> dynamicPalette(target, dark)
        dark -> DarkPalette
        else -> LightPalette
    }

    CompositionLocalProvider(LocalWaterPalette provides palette.animated()) {
        MaterialTheme(
            colorScheme = target.animated(),
            typography = WaterTypography,
            content = content,
        )
    }
}

/** Derives the extra colors from a Material You scheme so the layout still reads right. */
private fun dynamicPalette(scheme: ColorScheme, dark: Boolean) = WaterPalette(
    heroStart = if (dark) scheme.primaryContainer else scheme.primary,
    heroEnd = if (dark) lerp(scheme.primaryContainer, Color.Black, 0.5f) else lerp(scheme.primary, Color.Black, 0.3f),
    onHero = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
    ring = if (dark) scheme.primary else scheme.primaryContainer,
    waterFront = lerp(scheme.primary, Color.Black, 0.55f),
    waterBack = lerp(scheme.primary, Color.Black, 0.3f),
    logButtonBg = if (dark) scheme.primary else scheme.surface,
    logButtonFg = if (dark) scheme.onPrimary else scheme.primary,
    glow = scheme.primary.copy(alpha = 0.35f),
    cardBorder = if (dark) scheme.outlineVariant else Color.Transparent,
    cardShadow = if (dark) Color.Transparent else scheme.primary.copy(alpha = 0.1f),
    streakFlame = Color(0xFFFFB35C),
    chartBar = scheme.primary,
)

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
    ring = anim(ring),
    waterFront = anim(waterFront),
    waterBack = anim(waterBack),
    logButtonBg = anim(logButtonBg),
    logButtonFg = anim(logButtonFg),
    glow = anim(glow),
    cardBorder = anim(cardBorder),
    cardShadow = anim(cardShadow),
    streakFlame = streakFlame,
    chartBar = anim(chartBar),
)

@Composable
private fun anim(color: Color): Color {
    val value by animateColorAsState(color, animationSpec = tween(durationMillis = 450), label = "themeColor")
    return value
}

val chartBarColor: Color
    @Composable get() = LocalWaterPalette.current.chartBar

/** 150° diagonal like the design's `linear-gradient(150deg, …)`. */
val heroGradient: Brush
    @Composable get() {
        val palette = LocalWaterPalette.current
        return Brush.linearGradient(
            colors = listOf(palette.heroStart, palette.heroEnd),
            start = Offset(0f, 0f),
            end = Offset(600f, 1040f),
        )
    }
