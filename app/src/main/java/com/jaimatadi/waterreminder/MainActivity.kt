package com.jaimatadi.waterreminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaimatadi.waterreminder.data.DailyMetrics
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import com.jaimatadi.waterreminder.reminder.WaterNotification
import com.jaimatadi.waterreminder.ui.ReminderViewModel
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
import com.jaimatadi.waterreminder.ui.theme.chartBarColor
import com.jaimatadi.waterreminder.widget.WaterWidgetProvider
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ReminderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WaterNotification(this).createChannel()
        val repository = ReminderRepository(applicationContext)
        val scheduler = ReminderScheduler(applicationContext)
        val appContext = applicationContext
        viewModel = ViewModelProvider(
            this,
            ReminderViewModel.Factory(repository, scheduler) {
                WaterWidgetProvider.requestUpdate(appContext)
            }
        )[ReminderViewModel::class.java]

        setContent {
            WaterTheme {
                WaterReminderApp(viewModel)
            }
        }
    }
}

@Composable
private fun WaterReminderApp(viewModel: ReminderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var hasNotificationPermission by remember { mutableStateOf(true) }
    var notificationPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    var canUseFullScreenIntent by remember { mutableStateOf(true) }

    fun refreshPermissionStates() {
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        canScheduleExactAlarms = viewModel.canScheduleExactAlarms()
        canUseFullScreenIntent = viewModel.canUseFullScreenIntent()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted && activity != null) {
            notificationPermissionPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // Permission states can change while the user is away in system settings,
    // so re-read them every time the screen comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(
                    state = state,
                    onToggle = viewModel::setEnabled,
                    onLogWater = viewModel::logWaterNow,
                    onResetToday = viewModel::resetTodayMetrics,
                )
                WeekChartCard(state)
                SettingsCard(
                    state,
                    onSave = viewModel::saveSettings,
                    onRespectSilentChange = viewModel::setRespectSilentMode,
                )
                ReliabilityCard(
                    hasNotificationPermission = hasNotificationPermission,
                    notificationPermissionPermanentlyDenied = notificationPermissionPermanentlyDenied,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    canUseFullScreenIntent = canUseFullScreenIntent,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (notificationPermissionPermanentlyDenied) {
                                context.startActivity(appSettingsIntent(context))
                            } else {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    },
                    onOpenExactAlarmSettings = {
                        context.startActivity(viewModel.exactAlarmSettingsIntent())
                    },
                    onOpenFullScreenSettings = {
                        context.startActivity(viewModel.fullScreenIntentSettingsIntent())
                    }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// --- Hero: progress ring, countdown, quick actions ---

@Composable
private fun HeroCard(
    state: ReminderState,
    onToggle: (Boolean) -> Unit,
    onLogWater: () -> Unit,
    onResetToday: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Water Reminder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    NextReminderLabel(state)
                }
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ProgressRing(
                    progress = if (state.dailyGoal > 0) {
                        state.drinksToday.toFloat() / state.dailyGoal
                    } else 0f,
                    centerTop = state.drinksToday.toString(),
                    centerBottom = "of ${state.dailyGoal}",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStat("Reminders today", state.remindersToday.toString())
                    HeroStat("Skipped today", state.skipsToday.toString())
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onLogWater, modifier = Modifier.weight(1.4f)) {
                    Text("Log water")
                }
                OutlinedButton(onClick = onResetToday, modifier = Modifier.weight(1f)) {
                    Text("Reset today")
                }
            }
        }
    }
}

@Composable
private fun NextReminderLabel(state: ReminderState) {
    var label by remember { mutableStateOf(nextReminderText(state.enabled, state.nextReminderAt)) }

    LaunchedEffect(state.enabled, state.nextReminderAt) {
        while (true) {
            label = nextReminderText(state.enabled, state.nextReminderAt)
            delay(1_000)
        }
    }

    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    )
}

private fun nextReminderText(enabled: Boolean, next: Instant?): String {
    if (!enabled) return "Reminders are off"
    if (next == null) return "No reminder scheduled"

    val remaining = Duration.between(Instant.now(), next)
    if (remaining.isNegative) return "Reminder due now"

    val clock = next.atZone(ZoneId.systemDefault()).format(ClockFormatter)
    val totalMinutes = remaining.toMinutes()
    val countdown = when {
        totalMinutes >= 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
        totalMinutes >= 1 -> "$totalMinutes min"
        else -> "${remaining.seconds} s"
    }
    return "Next at $clock, in $countdown"
}

@Composable
private fun ProgressRing(
    progress: Float,
    centerTop: String,
    centerBottom: String,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "goalProgress"
    )
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    val ringColor = MaterialTheme.colorScheme.primary
    val goalDone = progress >= 1f

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTop,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (goalDone) "goal met!" else centerBottom,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

// --- Last 7 days chart ---

@Composable
private fun WeekChartCard(state: ReminderState) {
    val today = state.todayDate
    val byDate = remember(state.dailyMetrics) { state.dailyMetrics.associateBy { it.date } }
    val days = remember(today, state.dailyMetrics) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to (byDate[date] ?: DailyMetrics(date, 0, 0, 0))
        }
    }
    var selected by remember { mutableIntStateOf(days.lastIndex) }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "goal ${state.dailyGoal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
        }
    }
}

@Composable
private fun WeekBarChart(
    days: List<Pair<LocalDate, DailyMetrics>>,
    goal: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val barColor = chartBarColor
    val dimBar = barColor.copy(alpha = 0.45f)
    val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val maxValue = maxOf(goal, days.maxOf { it.second.drinks }, 1)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Dashed goal reference line behind the bars. Bar height for value v is
        // (100 * v / maxValue + 4) dp anchored 22dp above the row's bottom edge
        // (weekday label + spacer), so the line uses the same mapping.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 22.dp)
        ) {
            val y = size.height - (100f * goal / maxValue + 4f).dp.toPx()
            drawLine(
                color = goalLineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, (date, metrics) ->
                val isSelected = index == selectedIndex
                val heightFraction by animateFloatAsState(
                    targetValue = (metrics.drinks.toFloat() / maxValue).coerceIn(0f, 1f),
                    animationSpec = tween(500),
                    label = "barHeight$index"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (isSelected) {
                        Text(
                            metrics.drinks.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height((100 * heightFraction).dp + 4.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isSelected) barColor else dimBar)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Settings ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(
    state: ReminderState,
    onSave: (LocalTime, LocalTime, Long, Long, Int) -> Unit,
    onRespectSilentChange: (Boolean) -> Unit,
) {
    var dayStart by remember(state.dayStart) { mutableStateOf(state.dayStart) }
    var dayEnd by remember(state.dayEnd) { mutableStateOf(state.dayEnd) }
    var interval by remember(state.intervalMinutes) { mutableStateOf(state.intervalMinutes) }
    var snooze by remember(state.snoozeMinutes) { mutableStateOf(state.snoozeMinutes) }
    var goal by remember(state.dailyGoal) { mutableIntStateOf(state.dailyGoal) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val dirty = dayStart != state.dayStart || dayEnd != state.dayEnd ||
        interval != state.intervalMinutes || snooze != state.snoozeMinutes ||
        goal != state.dailyGoal

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimeField("Day start", dayStart, Modifier.weight(1f)) { pickingStart = true }
                TimeField("Day end", dayEnd, Modifier.weight(1f)) { pickingEnd = true }
            }

            StepperRow(
                label = "Reminder interval",
                value = "$interval min",
                onDecrement = { interval = (interval - 5).coerceAtLeast(5) },
                onIncrement = { interval = (interval + 5).coerceAtMost(720) },
            )
            StepperRow(
                label = "Snooze duration",
                value = "$snooze min",
                onDecrement = { snooze = (snooze - 5).coerceAtLeast(5) },
                onIncrement = { snooze = (snooze + 5).coerceAtMost(720) },
            )
            StepperRow(
                label = "Daily goal",
                value = "$goal glasses",
                onDecrement = { goal = (goal - 1).coerceAtLeast(1) },
                onIncrement = { goal = (goal + 1).coerceAtMost(30) },
            )

            // Applies immediately, unlike the fields above: it changes alert
            // behavior, not the reminder schedule, so no reschedule is needed.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Respect silent mode", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Skip the alarm sound when the phone is on vibrate or silent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.respectSilentMode,
                    onCheckedChange = onRespectSilentChange
                )
            }

            AnimatedVisibility(visible = dirty) {
                Button(
                    onClick = { onSave(dayStart, dayEnd, interval, snooze, goal) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save settings")
                }
            }
        }
    }

    if (pickingStart) {
        TimePickerDialog(
            title = "Day start",
            initial = dayStart,
            onConfirm = { dayStart = it; pickingStart = false },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        TimePickerDialog(
            title = "Day end",
            initial = dayEnd,
            onConfirm = { dayEnd = it; pickingEnd = false },
            onDismiss = { pickingEnd = false },
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    value: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value.format(TimeFormatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onDecrement, shape = CircleShape) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalIconButton(onClick = onIncrement, shape = CircleShape) {
                Text("+", style = MaterialTheme.typography.titleMedium)
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
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
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

// --- Reliability ---

@Composable
private fun ReliabilityCard(
    hasNotificationPermission: Boolean,
    notificationPermissionPermanentlyDenied: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
) {
    val allGood = hasNotificationPermission && canScheduleExactAlarms && canUseFullScreenIntent

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reliability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (allGood) {
                    Text(
                        "All good",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            StatusRow("Notifications", hasNotificationPermission)
            StatusRow("Exact alarms", canScheduleExactAlarms)
            StatusRow("Full-screen alerts", canUseFullScreenIntent)

            if (!hasNotificationPermission) {
                Text(
                    "Without notification permission reminders may not be visible at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(if (notificationPermissionPermanentlyDenied) "Open notification settings" else "Allow notifications")
                }
            }
            if (!canScheduleExactAlarms) {
                TextButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open alarm permission settings")
                }
            }
            if (!canUseFullScreenIntent) {
                TextButton(onClick = onOpenFullScreenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open full-screen alert settings")
                }
            }
            if (!allGood) {
                Text(
                    "On Samsung phones, keep this app unrestricted in battery settings if reminders arrive late.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (ok) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (ok) "ready" else "needs attention",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Helpers ---

private fun appSettingsIntent(context: android.content.Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
