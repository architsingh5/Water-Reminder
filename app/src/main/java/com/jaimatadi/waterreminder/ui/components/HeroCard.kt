package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaimatadi.waterreminder.R
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.ui.PauseOption
import com.jaimatadi.waterreminder.ui.TimeFormat
import com.jaimatadi.waterreminder.ui.theme.LocalWaterPalette
import com.jaimatadi.waterreminder.ui.theme.SerifDisplay
import com.jaimatadi.waterreminder.ui.theme.heroGradient
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun HeroCard(
    state: ReminderState,
    onToggle: (Boolean) -> Unit,
    onLogWater: () -> Unit,
    onPause: (PauseOption) -> Unit,
    onResume: () -> Unit,
    onResetToday: () -> Unit,
) {
    val palette = LocalWaterPalette.current
    val onHero = palette.onHero
    val paused = state.isPausedAt(Instant.now())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroGradient)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusLine(state, paused, modifier = Modifier.weight(1f), color = onHero.copy(alpha = 0.92f))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.ring,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = onHero.copy(alpha = 0.22f),
                        uncheckedBorderColor = Color.Transparent,
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WaterRing(drinks = state.drinksToday, goal = state.dailyGoal)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(text = "${state.remindersToday} reminders") {
                        Dot(palette.ring)
                    }
                    StatPill(text = "${state.skipsToday} skipped") {
                        Dot(onHero.copy(alpha = 0.5f))
                    }
                    val streak = state.streaks
                    StatPill(
                        text = when {
                            streak.current <= 0 -> "No streak yet"
                            streak.current == 1 -> "1-day streak"
                            else -> "${streak.current}-day streak"
                        }
                    ) {
                        AppIcon(R.drawable.ic_flame, tint = palette.streakFlame, size = 14.dp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogWaterButton(onClick = onLogWater, modifier = Modifier.weight(1f))
                PauseButton(
                    enabled = state.enabled,
                    paused = paused,
                    onPause = onPause,
                    onResume = onResume,
                )
                HeroSquareButton(R.drawable.ic_reset_stroke, "Reset today", onClick = onResetToday)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
}

/** Primary action: white on light, glowing cyan on OLED; squishes while pressed. */
@Composable
private fun LogWaterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWaterPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "logPress"
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 12.dp, shape = shape, ambientColor = palette.glow, spotColor = palette.glow)
            .clip(shape)
            .background(palette.logButtonBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(R.drawable.ic_water_drop, tint = palette.logButtonFg, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Log water",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.logButtonFg
        )
    }
}

@Composable
private fun PauseButton(
    enabled: Boolean,
    paused: Boolean,
    onPause: (PauseOption) -> Unit,
    onResume: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        HeroSquareButton(
            id = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            contentDescription = if (paused) "Resume reminders" else "Pause reminders",
            enabled = enabled,
            onClick = { if (paused) onResume() else menuOpen = true }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PauseOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("Pause ${option.label.lowercase()}") },
                    onClick = {
                        menuOpen = false
                        onPause(option)
                    }
                )
            }
        }
    }
}

/** 48 dp square tonal button on the hero (pause / reset). */
@Composable
private fun HeroSquareButton(
    id: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val onHero = LocalWaterPalette.current.onHero
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(onHero.copy(alpha = if (enabled) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            id,
            tint = onHero.copy(alpha = if (enabled) 1f else 0.4f),
            size = 18.dp,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun StatPill(text: String, leading: @Composable () -> Unit) {
    val onHero = LocalWaterPalette.current.onHero
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(onHero.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading()
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = onHero)
    }
}

@Composable
private fun StatusLine(state: ReminderState, paused: Boolean, modifier: Modifier, color: Color) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }

    LaunchedEffect(state.enabled, state.nextReminderAt, state.pausedUntil) {
        while (true) {
            label = statusText(state, paused) { TimeFormat.clock(context, it) }
            delay(1_000)
        }
    }

    Text(label, modifier = modifier, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = color)
}

private fun statusText(state: ReminderState, paused: Boolean, clock: (Instant) -> String): String {
    if (!state.enabled) return "Reminders off"
    val now = Instant.now()
    val pausedUntil = state.pausedUntil
    if (paused && pausedUntil != null) return "Paused until ${clock(pausedUntil)} · tap ▶ to resume"

    val next = state.nextReminderAt ?: return "Reminders on · nothing scheduled"
    val remaining = Duration.between(now, next)
    if (remaining.isNegative) return "Reminders on · due now"

    val totalMinutes = remaining.toMinutes()
    val countdown = when {
        totalMinutes >= 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
        totalMinutes >= 1 -> "$totalMinutes min"
        else -> "${remaining.seconds} s"
    }
    return "Reminders on · Next at ${clock(next)} · in $countdown"
}

/**
 * 150 dp goal ring: 9 dp arc, water rising inside a 112 dp circle with a lit
 * crest, serif numeral on top. Each new glass sends a soft radial ripple out
 * across the card; hitting the goal pops a check in.
 */
@Composable
private fun WaterRing(drinks: Int, goal: Int) {
    val palette = LocalWaterPalette.current
    val onHero = palette.onHero
    val progress = if (goal > 0) drinks.toFloat() / goal else 0f
    val goalDone = drinks >= goal && goal > 0

    val arc by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "goalArc"
    )
    val shownCount by animateIntAsState(targetValue = drinks, animationSpec = tween(500), label = "count")

    val ripple = remember { Animatable(0f) }
    var lastDrinks by remember { mutableIntStateOf(drinks) }
    LaunchedEffect(drinks) {
        if (drinks > lastDrinks) {
            ripple.snapTo(0.01f)
            ripple.animateTo(1f, tween(durationMillis = 800))
            ripple.snapTo(0f)
        }
        lastDrinks = drinks
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        // Ripple is drawn first so it sits under the ring; it grows well past
        // the ring and is clipped by the card's rounded corners.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = ripple.value
            if (r > 0f) {
                val scale = 0.2f + 3.0f * r
                val radius = size.minDimension / 2 * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            onHero.copy(alpha = 0.55f * (1f - r)),
                            onHero.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(onHero.copy(alpha = 0.08f))
        ) {
            WaterWaves(
                progress = progress,
                color = palette.waterFront.copy(alpha = 0.9f),
                backColor = palette.waterBack.copy(alpha = 0.45f),
                crestColor = palette.ring,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = onHero.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (arc > 0f) {
                drawArc(
                    color = palette.ring,
                    startAngle = -90f,
                    sweepAngle = arc * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = goalDone,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                AppIcon(R.drawable.ic_check_stroke, tint = palette.ring, size = 20.dp)
            }
            Text(
                shownCount.toString(),
                style = TextStyle(
                    fontFamily = SerifDisplay,
                    fontSize = 46.sp,
                    lineHeight = 46.sp,
                    color = onHero,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0x59041E24),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f
                    )
                )
            )
            Text(
                if (goalDone) "goal met!" else "of $goal glasses",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = onHero.copy(alpha = 0.9f)
            )
        }
    }
}
