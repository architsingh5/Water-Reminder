package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaimatadi.waterreminder.R
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.ui.PauseOption
import com.jaimatadi.waterreminder.ui.TimeFormat
import com.jaimatadi.waterreminder.ui.theme.LocalWaterPalette
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.enabled) (if (paused) "Paused" else "Reminders on") else "Reminders off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onHero
                    )
                    NextReminderLabel(state, color = onHero.copy(alpha = 0.85f))
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.heroStart,
                        checkedTrackColor = onHero,
                        uncheckedThumbColor = onHero,
                        uncheckedTrackColor = onHero.copy(alpha = 0.25f),
                        uncheckedBorderColor = onHero.copy(alpha = 0.5f),
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                WaterRing(
                    drinks = state.drinksToday,
                    goal = state.dailyGoal,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(R.drawable.ic_bell, "${state.remindersToday} reminders")
                    StatPill(R.drawable.ic_snooze, "${state.skipsToday} skipped")
                    val streak = state.streaks
                    StatPill(
                        R.drawable.ic_fire,
                        when {
                            streak.current <= 0 -> "No streak yet"
                            streak.current == 1 -> "1-day streak"
                            else -> "${streak.current}-day streak"
                        },
                        supporting = if (streak.best > streak.current) "best ${streak.best}" else null,
                        pulse = streak.current > 0,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogWaterButton(onClick = onLogWater, modifier = Modifier.weight(1f))
                PauseButton(
                    enabled = state.enabled,
                    paused = paused,
                    onPause = onPause,
                    onResume = onResume,
                )
                HeroIconButton(R.drawable.ic_refresh, "Reset today", onClick = onResetToday)
            }
        }
    }
}

/** Primary action; squishes slightly while pressed so the tap feels physical. */
@Composable
private fun LogWaterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWaterPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "logPress"
    )

    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.onHero,
            contentColor = palette.heroStart
        )
    ) {
        AppIcon(R.drawable.ic_water_drop, tint = palette.heroStart, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text("Log water", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        HeroIconButton(
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

@Composable
private fun HeroIconButton(
    id: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val onHero = LocalWaterPalette.current.onHero
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = onHero.copy(alpha = 0.18f),
            contentColor = onHero,
            disabledContainerColor = onHero.copy(alpha = 0.08f),
            disabledContentColor = onHero.copy(alpha = 0.4f),
        )
    ) {
        // LocalContentColor follows the button's enabled/disabled content color.
        AppIcon(id, tint = LocalContentColor.current, size = 22.dp, contentDescription = contentDescription)
    }
}

@Composable
private fun StatPill(id: Int, text: String, supporting: String? = null, pulse: Boolean = false) {
    val onHero = LocalWaterPalette.current.onHero
    val iconScale = if (pulse) {
        val transition = rememberInfiniteTransition(label = "pillPulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pillPulseScale"
        )
        value
    } else 1f

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(onHero.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(id, tint = onHero, size = 16.dp, modifier = Modifier.scale(iconScale))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = onHero
        )
        if (supporting != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = onHero.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NextReminderLabel(state: ReminderState, color: Color) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }

    LaunchedEffect(state.enabled, state.nextReminderAt, state.pausedUntil) {
        while (true) {
            label = nextReminderText(state) { TimeFormat.clock(context, it) }
            delay(1_000)
        }
    }

    Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
}

private fun nextReminderText(state: ReminderState, clock: (Instant) -> String): String {
    if (!state.enabled) return "Turn on to get reminded"
    val now = Instant.now()
    val pausedUntil = state.pausedUntil
    if (pausedUntil != null && pausedUntil.isAfter(now)) return "Until ${clock(pausedUntil)} · tap ▶ to resume"

    val next = state.nextReminderAt ?: return "No reminder scheduled"
    val remaining = Duration.between(now, next)
    if (remaining.isNegative) return "Reminder due now"

    val totalMinutes = remaining.toMinutes()
    val countdown = when {
        totalMinutes >= 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
        totalMinutes >= 1 -> "$totalMinutes min"
        else -> "${remaining.seconds} s"
    }
    return "Next at ${clock(next)} · in $countdown"
}

/**
 * Goal ring with animated water rising inside it. The water level and the arc
 * both track today's progress; at the goal the arc switches to the goal accent
 * and a check pops in. Each new glass sends a ripple outwards from the ring.
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
    val ringColor = if (goalDone) palette.goalAccent else onHero

    // Ripple: 0 = idle, runs 0→1 whenever the count goes up.
    val ripple = remember { Animatable(0f) }
    var lastDrinks by remember { mutableIntStateOf(drinks) }
    LaunchedEffect(drinks) {
        if (drinks > lastDrinks) {
            ripple.snapTo(0.01f)
            ripple.animateTo(1f, tween(durationMillis = 750))
            ripple.snapTo(0f)
        }
        lastDrinks = drinks
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        // Water inside the ring.
        Box(
            modifier = Modifier
                .size(150.dp - 30.dp)
                .clip(CircleShape)
                .background(onHero.copy(alpha = 0.10f))
        ) {
            WaterWaves(
                progress = progress,
                color = onHero.copy(alpha = 0.30f),
                backColor = onHero.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = onHero.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (arc > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = arc * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            val r = ripple.value
            if (r > 0f) {
                drawCircle(
                    color = onHero.copy(alpha = (1f - r) * 0.6f),
                    radius = size.minDimension / 2 * (0.75f + 0.45f * r),
                    style = Stroke(width = 3.dp.toPx() * (1f - r) + 1.dp.toPx())
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = goalDone,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                AppIcon(R.drawable.ic_check, tint = palette.goalAccent, size = 22.dp)
            }
            Text(
                shownCount.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = onHero
            )
            Text(
                if (goalDone) "goal met!" else "of $goal glasses",
                style = MaterialTheme.typography.labelMedium,
                color = onHero.copy(alpha = 0.8f)
            )
        }
    }
}
