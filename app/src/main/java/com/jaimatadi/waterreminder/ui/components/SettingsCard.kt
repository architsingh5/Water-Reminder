package com.jaimatadi.waterreminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
        SettingRow(icon = R.drawable.ic_schedule, label = "Awake hours", supporting = "Reminders only ring between these times") {}
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TimeField("Day start", draft.dayStart, Modifier.weight(1f)) { pickingStart = true }
            TimeField("Day end", draft.dayEnd, Modifier.weight(1f)) { pickingEnd = true }
        }
        AnimatedVisibility(visible = draft.validationError != null) {
            Text(
                draft.validationError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        StepperRow(
            icon = R.drawable.ic_timer,
            label = "Reminder interval",
            value = formatMinutes(draft.intervalMinutes),
            onDecrement = { onDraftChange(draft.copy(intervalMinutes = (draft.intervalMinutes - 5).coerceAtLeast(5))) },
            onIncrement = { onDraftChange(draft.copy(intervalMinutes = (draft.intervalMinutes + 5).coerceAtMost(720))) },
        )
        StepperRow(
            icon = R.drawable.ic_snooze,
            label = "Snooze duration",
            value = formatMinutes(draft.snoozeMinutes),
            onDecrement = { onDraftChange(draft.copy(snoozeMinutes = (draft.snoozeMinutes - 5).coerceAtLeast(5))) },
            onIncrement = { onDraftChange(draft.copy(snoozeMinutes = (draft.snoozeMinutes + 5).coerceAtMost(720))) },
        )
        StepperRow(
            icon = R.drawable.ic_flag,
            label = "Daily goal",
            value = "${draft.dailyGoal} glasses",
            onDecrement = { onDraftChange(draft.copy(dailyGoal = (draft.dailyGoal - 1).coerceAtLeast(1))) },
            onIncrement = { onDraftChange(draft.copy(dailyGoal = (draft.dailyGoal + 1).coerceAtMost(30))) },
        )
    }

    // These apply immediately: they change how alerts look, not when they fire,
    // so nothing needs rescheduling.
    SectionCard(title = "Alerts", modifier = Modifier.animateContentSize()) {
        SettingRow(icon = R.drawable.ic_bell, label = "Alert style", supporting = "How a reminder gets your attention") {}
        AlertStyleSelector(selected = state.alertStyle, onSelect = onAlertStyleChange)

        AnimatedVisibility(visible = state.alertStyle == AlertStyle.Alarm) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                SettingRow(
                    icon = R.drawable.ic_volume_off,
                    label = "Respect silent mode",
                    supporting = "Don't ring when the phone is on vibrate or silent"
                ) {
                    Switch(checked = state.respectSilentMode, onCheckedChange = onRespectSilentChange)
                }
            }
        }
    }

    SectionCard(title = "Appearance", modifier = Modifier.animateContentSize()) {
        SettingRow(icon = R.drawable.ic_palette, label = "Theme", supporting = "Dark uses pure black for OLED screens") {}
        ThemeModeSelector(selected = state.themeMode, onSelect = onThemeModeChange)

        if (supportsDynamicColor()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(
                icon = R.drawable.ic_palette,
                label = "Wallpaper colors",
                supporting = "Use your phone's Material You palette instead of aqua"
            ) {
                Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColorChange)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(mode.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertStyleSelector(selected: AlertStyle, onSelect: (AlertStyle) -> Unit) {
    val options = listOf(
        AlertStyle.Alarm to "Alarm",
        AlertStyle.Notification to "Notification",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = style == selected,
                onClick = { onSelect(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
    Text(
        when (selected) {
            AlertStyle.Alarm -> "Full-screen alarm card with sound, like an alarm clock."
            AlertStyle.Notification -> "A normal notification with Drank / Snooze / Skip. No ringing."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingRow(
    icon: Int,
    label: String,
    supporting: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        RowLabel(label, supporting, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun TimeField(
    label: String,
    value: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            TimeFormat.clock(context, value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StepperRow(
    icon: Int,
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onDecrement, shape = CircleShape) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
            }
            FilledTonalIconButton(onClick = onIncrement, shape = CircleShape) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
            }
        }
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
