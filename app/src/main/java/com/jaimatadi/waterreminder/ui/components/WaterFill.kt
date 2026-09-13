package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/** Continuously cycling wave phase in radians (0 → 2π), for gentle water motion. */
@Composable
fun rememberWavePhase(periodMillis: Int = 3_200): State<Float> {
    val transition = rememberInfiniteTransition(label = "wavePhase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhaseValue"
    )
}

/**
 * Fills the drawing area from the bottom up to [level] (0..1) with a sine
 * wave surface. Two calls with different phases stacked give a layered look.
 * [crest] draws a thin highlight along the surface, like the design's stroke.
 */
fun DrawScope.drawWave(
    level: Float,
    phase: Float,
    color: Color,
    amplitude: Float = size.height * 0.035f,
    waves: Float = 1.5f,
    crest: Color? = null,
    crestWidth: Float = 2f,
) {
    val clamped = level.coerceIn(0f, 1f)
    val surfaceY = size.height * (1f - clamped)
    val surface = Path()
    val fill = Path()
    fill.moveTo(0f, size.height)

    val steps = 48
    for (i in 0..steps) {
        val x = size.width * i / steps
        val angle = (x / size.width) * waves * 2f * PI.toFloat() + phase
        val y = surfaceY + sin(angle) * amplitude
        if (i == 0) {
            surface.moveTo(x, y)
            fill.lineTo(x, y)
        } else {
            surface.lineTo(x, y)
            fill.lineTo(x, y)
        }
    }

    fill.lineTo(size.width, size.height)
    fill.close()
    drawPath(fill, color)
    if (crest != null) {
        drawPath(surface, crest, style = Stroke(width = crestWidth))
    }
}

/**
 * Animated water that rises to [progress]. Meant to sit behind text inside a
 * clipped shape (circle for the hero ring, full-width for the alarm screen).
 * The two layers drift in opposite directions so the surfaces cross.
 */
@Composable
fun WaterWaves(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    backColor: Color = color.copy(alpha = 0.45f),
    crestColor: Color? = null,
) {
    val phase by rememberWavePhase()
    val level by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "waterLevel"
    )
    Canvas(modifier = modifier) {
        drawWave(level = level, phase = -phase * 0.7f + 1.3f, color = backColor, waves = 1.2f)
        drawWave(level = level, phase = phase, color = color, crest = crestColor)
    }
}
