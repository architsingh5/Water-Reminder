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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import com.jaimatadi.waterreminder.reminder.WaterNotification
import com.jaimatadi.waterreminder.ui.ReminderViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ReminderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WaterNotification(this).createChannel()
        val repository = ReminderRepository(applicationContext)
        val scheduler = ReminderScheduler(applicationContext)
        viewModel = ViewModelProvider(
            this,
            ReminderViewModel.Factory(repository, scheduler)
        )[ReminderViewModel::class.java]

        setContent {
            WaterReminderApp(viewModel)
        }
    }
}

@Composable
private fun WaterReminderApp(viewModel: ReminderViewModel) {
    val state by viewModel.state.collectAsState()
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var notificationPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity

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

    LaunchedEffect(Unit) {
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header(state, onToggle = viewModel::setEnabled)
                    TodayCard(
                        state = state,
                        onLogWater = viewModel::logWaterNow,
                        onResetToday = viewModel::resetTodayMetrics,
                    )
                    AnalyticsCard(state)
                    SettingsCard(state, onSave = viewModel::saveSettings)
                    PermissionsCard(
                        hasNotificationPermission = hasNotificationPermission,
                        notificationPermissionPermanentlyDenied = notificationPermissionPermanentlyDenied,
                        canScheduleExactAlarms = viewModel.canScheduleExactAlarms(),
                        canUseFullScreenIntent = viewModel.canUseFullScreenIntent(),
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
                }
            }
        }
    }
}

@Composable
private fun Header(state: ReminderState, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Water Reminder", style = MaterialTheme.typography.headlineMedium)
            Text("Next reminder: ${state.nextReminderAt.formatInstantOrDash()}")
        }
        Switch(checked = state.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun TodayCard(
    state: ReminderState,
    onLogWater: () -> Unit,
    onResetToday: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat("Drank", state.drinksToday.toString())
                Stat("Reminders", state.remindersToday.toString())
                Stat("Skipped", state.skipsToday.toString())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onLogWater, modifier = Modifier.weight(1f)) {
                    Text("Log water now")
                }
                OutlinedButton(onClick = onResetToday, modifier = Modifier.weight(1f)) {
                    Text("Reset today")
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(state: ReminderState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Date-wise analytics", style = MaterialTheme.typography.titleLarge)
            state.dailyMetrics.take(7).forEach { metrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(metrics.date.format(DateFormatter), modifier = Modifier.weight(1.2f))
                    Text("D ${metrics.drinks}", modifier = Modifier.weight(0.7f))
                    Text("R ${metrics.reminders}", modifier = Modifier.weight(0.7f))
                    Text("S ${metrics.skips}", modifier = Modifier.weight(0.7f))
                }
            }
            Text(
                "D = drank, R = reminders, S = skipped",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsCard(
    state: ReminderState,
    onSave: (LocalTime, LocalTime, Long, Long) -> Unit,
) {
    var startText by remember(state.dayStart) { mutableStateOf(state.dayStart.format(TimeFormatter)) }
    var endText by remember(state.dayEnd) { mutableStateOf(state.dayEnd.format(TimeFormatter)) }
    var intervalText by remember(state.intervalMinutes) { mutableStateOf(state.intervalMinutes.toString()) }
    var snoozeText by remember(state.snoozeMinutes) { mutableStateOf(state.snoozeMinutes.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("Day start, HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("Day end, HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter(Char::isDigit) },
                label = { Text("Reminder interval, minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = snoozeText,
                onValueChange = { snoozeText = it.filter(Char::isDigit) },
                label = { Text("Snooze duration, minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    parseSettings(startText, endText, intervalText, snoozeText).fold(
                        onSuccess = { parsed ->
                            error = null
                            onSave(parsed.dayStart, parsed.dayEnd, parsed.intervalMinutes, parsed.snoozeMinutes)
                        },
                        onFailure = { error = it.message }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save settings")
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    hasNotificationPermission: Boolean,
    notificationPermissionPermanentlyDenied: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Reliability", style = MaterialTheme.typography.titleLarge)
            Text("Notifications: ${if (hasNotificationPermission) "allowed" else "blocked"}")
            Text("Exact alarms: ${if (canScheduleExactAlarms) "available" else "needs permission"}")
            Text("Full-screen alerts: ${if (canUseFullScreenIntent) "available" else "needs permission"}")
            if (!hasNotificationPermission) {
                Text(
                    "Without this permission reminders may not be visible at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!hasNotificationPermission) {
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
            Spacer(Modifier.height(2.dp))
            Text(
                "On Samsung phones, keep this app unrestricted in battery settings if reminders arrive late.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class ParsedSettings(
    val dayStart: LocalTime,
    val dayEnd: LocalTime,
    val intervalMinutes: Long,
    val snoozeMinutes: Long,
)

private const val MAX_INTERVAL_MINUTES = 720L
private const val MAX_SNOOZE_MINUTES = 720L

private fun parseSettings(
    startText: String,
    endText: String,
    intervalText: String,
    snoozeText: String,
): Result<ParsedSettings> {
    val start = runCatching { LocalTime.parse(startText, TimeFormatter) }.getOrNull()
        ?: return Result.failure(IllegalArgumentException("Day start must be a valid time, e.g. 07:00."))
    val end = runCatching { LocalTime.parse(endText, TimeFormatter) }.getOrNull()
        ?: return Result.failure(IllegalArgumentException("Day end must be a valid time, e.g. 23:00."))
    if (start == end) {
        return Result.failure(IllegalArgumentException("Day start and day end must be different times."))
    }
    val interval = intervalText.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Reminder interval must be a whole number of minutes."))
    if (interval !in 1..MAX_INTERVAL_MINUTES) {
        return Result.failure(IllegalArgumentException("Reminder interval must be between 1 and $MAX_INTERVAL_MINUTES minutes."))
    }
    val snooze = snoozeText.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Snooze duration must be a whole number of minutes."))
    if (snooze !in 1..MAX_SNOOZE_MINUTES) {
        return Result.failure(IllegalArgumentException("Snooze duration must be between 1 and $MAX_SNOOZE_MINUTES minutes."))
    }
    return Result.success(ParsedSettings(start, end, interval, snooze))
}

private fun appSettingsIntent(context: android.content.Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

private fun Instant?.formatInstantOrDash(): String {
    if (this == null) return "-"
    return atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, HH:mm"))
}
