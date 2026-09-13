package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.jaimatadi.waterreminder.data.DailyMetrics
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.ui.theme.chartBarColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekChartCard(state: ReminderState) {
    val today = state.todayDate
    val byDate = remember(state.dailyMetrics) { state.dailyMetrics.associateBy { it.date } }
    val days = remember(today, state.dailyMetrics) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to (byDate[date] ?: DailyMetrics(date, 0, 0, 0))
        }
    }
    var selected by remember { mutableIntStateOf(days.lastIndex) }

    val totalDrinks = days.sumOf { it.second.drinks }
    val goalDays = days.count { it.second.drinks >= state.dailyGoal }
    val average = totalDrinks / 7f

    SectionCard(
        title = "Last 7 days",
        trailing = {
            Text(
                "goal ${state.dailyGoal}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        WeekBarChart(
            days = days,
            goal = state.dailyGoal,
            selectedIndex = selected,
            onSelect = { selected = it },
        )

        val metrics = days[selected].second
        Text(
            "${days[selected].first.format(FullDateFormatter)} — " +
                "${metrics.drinks} drank, ${metrics.reminders} reminders, ${metrics.skips} skipped",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Avg ${"%.1f".format(average)} glasses a day · goal met on $goalDays of 7 days",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Bars, goal line, value bubble and weekday labels all drawn in one canvas
 * with one coordinate mapping, so the goal line lands exactly at the goal bar
 * height regardless of font scale.
 */
@Composable
private fun WeekBarChart(
    days: List<Pair<LocalDate, DailyMetrics>>,
    goal: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val barColor = chartBarColor
    val dimBar = barColor.copy(alpha = 0.35f)
    val goalLineColor = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val todayLabelStyle = labelStyle.copy(color = scheme.primary, fontWeight = FontWeight.Bold)
    val bubbleTextStyle = MaterialTheme.typography.labelMedium.copy(
        color = scheme.onPrimary,
        fontWeight = FontWeight.SemiBold
    )
    val textMeasurer = rememberTextMeasurer()
    val maxValue = maxOf(goal, days.maxOf { it.second.drinks }, 1)

    // Bars grow in when the data set changes (first show, new day).
    val dataKey = days.joinToString { "${it.first}:${it.second.drinks}" }
    val reveal = remember(dataKey) { Animatable(0f) }
    LaunchedEffect(dataKey) { reveal.animateTo(1f, tween(600)) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val slot = size.width / days.size.toFloat()
                    onSelect((offset.x / slot).toInt().coerceIn(0, days.lastIndex))
                }
            }
    ) {
        val slotWidth = size.width / days.size
        val labelHeight = 18.dp.toPx()
        val labelGap = 8.dp.toPx()
        val topPadding = 30.dp.toPx() // room for the value bubble
        val chartBottom = size.height - labelHeight - labelGap
        val chartHeight = chartBottom - topPadding
        val minBar = 4.dp.toPx()

        fun yFor(value: Float): Float = chartBottom - chartHeight * (value / maxValue)

        // Goal reference line.
        val goalY = yFor(goal.toFloat())
        drawLine(
            color = goalLineColor,
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        days.forEachIndexed { index, (date, metrics) ->
            val isSelected = index == selectedIndex
            val centerX = slotWidth * index + slotWidth / 2
            val barWidth = slotWidth * 0.48f
            val fullHeight = (chartBottom - yFor(metrics.drinks.toFloat())).coerceAtLeast(minBar)
            val barHeight = minBar + (fullHeight - minBar) * reveal.value
            val top = chartBottom - barHeight
            val color = if (isSelected) barColor else dimBar

            val radius = CornerRadius(barWidth / 2.6f)
            val bar = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(centerX - barWidth / 2, top), Size(barWidth, barHeight)),
                        topLeft = radius,
                        topRight = radius,
                        bottomLeft = CornerRadius.Zero,
                        bottomRight = CornerRadius.Zero,
                    )
                )
            }
            drawPath(
                path = bar,
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = color.alpha * 0.75f)),
                    startY = top,
                    endY = chartBottom
                )
            )

            // Weekday label; today is emphasised.
            val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val labelLayout = textMeasurer.measure(label, if (index == days.lastIndex) todayLabelStyle else labelStyle)
            drawText(
                labelLayout,
                topLeft = Offset(
                    centerX - labelLayout.size.width / 2f,
                    size.height - labelHeight + (labelHeight - labelLayout.size.height) / 2f
                )
            )

            if (isSelected) {
                val valueLayout = textMeasurer.measure(metrics.drinks.toString(), bubbleTextStyle)
                val padX = 8.dp.toPx()
                val padY = 3.dp.toPx()
                val bubbleWidth = valueLayout.size.width + padX * 2
                val bubbleHeight = valueLayout.size.height + padY * 2
                val bubbleTop = (top - 8.dp.toPx() - bubbleHeight).coerceAtLeast(0f)
                val bubbleLeft = (centerX - bubbleWidth / 2).coerceIn(0f, size.width - bubbleWidth)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = CornerRadius(bubbleHeight / 2)
                )
                drawText(valueLayout, topLeft = Offset(bubbleLeft + padX, bubbleTop + padY))
            }
        }
    }
}

private val FullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
