package com.jaimatadi.waterreminder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jaimatadi.waterreminder.R

/**
 * Manrope for all UI text (variable font, one file covers 400–800) and
 * DM Serif Display for the big numerals — the serif against a geometric sans
 * is what gives the design its personality.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.Medium),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
    Font(R.font.manrope_variable, FontWeight.ExtraBold),
)

val SerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))

private val base = Typography()

private fun TextStyle.manrope() = copy(fontFamily = Manrope)

val WaterTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SerifDisplay),
    displayMedium = base.displayMedium.copy(fontFamily = SerifDisplay),
    displaySmall = base.displaySmall.copy(fontFamily = SerifDisplay),
    headlineLarge = base.headlineLarge.manrope().copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.manrope().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.manrope().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.manrope().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.manrope().copy(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    titleSmall = base.titleSmall.manrope().copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.manrope().copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
    bodyMedium = base.bodyMedium.manrope().copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
    bodySmall = base.bodySmall.manrope().copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.manrope().copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.manrope().copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.manrope().copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)
