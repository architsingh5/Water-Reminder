package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaimatadi.waterreminder.R
import com.jaimatadi.waterreminder.data.AlertStyle
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.data.ThemeMode
import com.jaimatadi.waterreminder.ui.TimeFormat
import com.jaimatadi.waterreminder.ui.theme.supportsDynamicColor
import java.time.LocalTime

/** Unsaved edits to the schedule settings; hoisted so the Save bar can live outside the card. */
data class SettingsDraft(
    val dayStart: LocalTime,
    val dayEnd: LocalTime,
    val intervalMinutes: Long,
    val snoozeMinutes: Long,
    val dailyGoal: Int,
) {
    val validationError: String?
        get() = when {
            dayStart == dayEnd -> "Day start and day end can't be the same time."
            else -> null
        }

    fun isDirty(state: ReminderState): Boolean = this != from(state)

    companion object {
        fun from(state: ReminderState) = SettingsDraft(
            dayStart = state.dayStart,
            dayEnd = state.dayEnd,
            intervalMinutes = state.intervalMinutes,
            snoozeMinutes = state.snoozeMinutes,
            dailyGoal = state.dailyGoal,
        )
    }
}

@Composable
fun SettingsCard(
    state: ReminderState,
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    onRespectSilentChange: (Boolean) -> Unit,
    onAlertStyleChange: (AlertStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    SectionCard(title = "Schedule", modifier = Modifier.animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimePill("Day start", draft.dayStart, Modifier.weight(1f)) { pickingStart = true }
                TimePill("Day end", draft.dayEnd, Modifier.weight(1f)) { pickingEnd = true }
            }
            AnimatedVisibility(visible = draft.validationError != null) {
                Text(
                    draft.validationError.orEmpty(),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            SettingRow(
                icon = R.drawable.ic_clock_outline,
                label = "Reminder interval",
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            ) {
                StepperGroup(
                    value = formatMinutes(draft.intervalMinutes),
                    onDecrement = { onDraftChange(draft.copy(intervalMinutes = (draft.intervalMinutes - 5).coerceAtLeast(5))) },
                    onIncrement = { onDraftChange(draft.copy(intervalMinutes = (draft.intervalMinutes + 5).coerceAtMost(720))) },
                )
            }
            RowDivider()
            SettingRow(
                icon = R.drawable.ic_moon_outline,
                label = "Snooze",
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                StepperGroup(
                    value = formatMinutes(draft.snoozeMinutes),
                    onDecrement = { onDraftChange(draft.copy(snoozeMinutes = (draft.snoozeMinutes - 5).coerceAtLeast(5))) },
                    onIncrement = { onDraftChange(draft.copy(snoozeMinutes = (draft.snoozeMinutes + 5).coerceAtMost(720))) },
                )
            }
            RowDivider()
            SettingRow(
                icon = R.drawable.ic_glass_outline,
                label = "Daily goal",
                modifier = Modifier.padding(top = 10.dp)
            ) {
                StepperGroup(
                    value = "${draft.dailyGoal} glasses",
                    minValueWidth = 64.dp,
                    onDecrement = { onDraftChange(draft.copy(dailyGoal = (draft.dailyGoal - 1).coerceAtLeast(1))) },
                    onIncrement = { onDraftChange(draft.copy(dailyGoal = (draft.dailyGoal + 1).coerceAtMost(30))) },
                )
            }
        }
    }

    // These apply immediately: they change how alerts look, not when they fire,
    // so nothing needs rescheduling.
    SectionCard(title = "Alerts", modifier = Modifier.animateContentSize()) {
        Column {
            SlidingSegments(
                options = listOf(AlertStyle.Alarm, AlertStyle.Notification),
                selected = state.alertStyle,
                label = { if (it == AlertStyle.Alarm) "Alarm" else "Notification" },
                onSelect = onAlertStyleChange,
            )
            AnimatedVisibility(visible = state.alertStyle == AlertStyle.Alarm) {
                SettingRow(
                    icon = null,
                    label = "Respect silent mode",
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    WaterSwitch(checked = state.respectSilentMode, onCheckedChange = onRespectSilentChange)
                }
            }
        }
    }

    SectionCard(title = "Appearance", modifier = Modifier.animateContentSize()) {
        Column {
            SlidingSegments(
                options = ThemeMode.entries,
                selected = state.themeMode,
                label = { it.label },
                onSelect = onThemeModeChange,
            )
            if (supportsDynamicColor()) {
                SettingRow(
                    icon = null,
                    label = "Wallpaper colors",
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    WaterSwitch(checked = state.dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }
        }
    }

    if (pickingStart) {
        TimePickerDialog(
            title = "Day start",
            initial = draft.dayStart,
            onConfirm = { onDraftChange(draft.copy(dayStart = it)); pickingStart = false },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        TimePickerDialog(
            title = "Day end",
            initial = draft.dayEnd,
            onConfirm = { onDraftChange(draft.copy(dayEnd = it)); pickingEnd = false },
            onDismiss = { pickingEnd = false },
        )
    }
}

private fun formatMinutes(minutes: Long): String = when {
    minutes % 60 == 0L -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/** Tonal pill: muted label on the left, bold primary-colored time on the right. */
@Composable
private fun TimePill(
    label: String,
    value: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
        Text(
            TimeFormat.clock(context, value),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = scheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = TimeFormat.is24Hour(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
