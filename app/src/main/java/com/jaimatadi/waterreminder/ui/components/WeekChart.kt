package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaimatadi.waterreminder.data.DailyMetrics
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.ui.theme.LocalWaterPalette
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
    val scheme = MaterialTheme.colorScheme

    SectionCard(title = "Last 7 days") {
        Column {
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
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant
            )
            Text(
                "Avg ${"%.1f".format(average)} glasses a day · goal met on $goalDays of 7 days",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pill bars (18 dp wide, fully rounded) on a 120 dp plot with a dashed goal
 * line and its "goal N" tag, a value bubble over the selected bar, and day
 * letters underneath — all in one canvas so everything shares one scale.
 */
@Composable
private fun WeekBarChart(
    days: List<Pair<LocalDate, DailyMetrics>>,
    goal: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bar = LocalWaterPalette.current.chartBar
    val bubbleText = scheme.onPrimary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    val goalTagStyle = labelStyle.copy(color = bar, fontSize = 10.sp)
    val bubbleStyle = labelStyle.copy(color = bubbleText, fontWeight = FontWeight.ExtraBold)

    // The design plots against a fixed 12-glass scale so a normal day fills
    // about two thirds; only grow it when the goal or a big day needs more.
    val maxValue = maxOf(12, goal, days.maxOf { it.second.drinks })

    val dataKey = days.joinToString { "${it.first}:${it.second.drinks}" }
    val reveal = remember(dataKey) { Animatable(0f) }
    LaunchedEffect(dataKey) { reveal.animateTo(1f, tween(600)) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp + 120.dp + 8.dp + 16.dp)
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val slot = size.width / days.size.toFloat()
                    onSelect((offset.x / slot).toInt().coerceIn(0, days.lastIndex))
                }
            }
    ) {
        val topPadding = 22.dp.toPx()
        val plotHeight = 120.dp.toPx()
        val chartBottom = topPadding + plotHeight
        val labelTop = chartBottom + 8.dp.toPx()
        val sidePadding = 10.dp.toPx()
        val slotWidth = (size.width - sidePadding * 2) / days.size
        val barWidth = 18.dp.toPx()
        val minBar = 6.dp.toPx()

        fun heightFor(value: Int): Float = maxOf(minBar, plotHeight * value / maxValue)

        // Goal line + tag.
        val goalY = chartBottom - minOf(plotHeight, plotHeight * goal / maxValue)
        drawLine(
            color = bar.copy(alpha = 0.5f),
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
        val tag = textMeasurer.measure("goal $goal", goalTagStyle)
        drawText(tag, topLeft = Offset(size.width - tag.size.width, goalY - 4.dp.toPx() - tag.size.height))

        days.forEachIndexed { index, (date, metrics) ->
            val isSelected = index == selectedIndex
            val centerX = sidePadding + slotWidth * index + slotWidth / 2
            val fullHeight = heightFor(metrics.drinks)
            val barHeight = minBar + (fullHeight - minBar) * reveal.value
            val top = chartBottom - barHeight

            drawRoundRect(
                color = bar.copy(alpha = if (isSelected) 1f else 0.32f),
                topLeft = Offset(centerX - barWidth / 2, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )

            val letter = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val letterLayout = textMeasurer.measure(letter, labelStyle)
            drawText(letterLayout, topLeft = Offset(centerX - letterLayout.size.width / 2f, labelTop))

            if (isSelected) {
                val value = textMeasurer.measure(metrics.drinks.toString(), bubbleStyle)
                val padX = 8.dp.toPx()
                val padY = 3.dp.toPx()
                val w = value.size.width + padX * 2
                val h = value.size.height + padY * 2
                val bubbleTop = (top - 8.dp.toPx() - h).coerceAtLeast(0f)
                val bubbleLeft = (centerX - w / 2).coerceIn(0f, size.width - w)
                drawRoundRect(
                    color = bar,
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawText(value, topLeft = Offset(bubbleLeft + padX, bubbleTop + padY))
            }
        }
    }
}

private val FullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
