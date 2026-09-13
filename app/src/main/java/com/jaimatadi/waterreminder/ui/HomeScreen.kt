package com.jaimatadi.waterreminder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaimatadi.waterreminder.R
import com.jaimatadi.waterreminder.ui.components.AppIcon
import com.jaimatadi.waterreminder.ui.components.HeroCard
import com.jaimatadi.waterreminder.ui.components.ReliabilityCard
import com.jaimatadi.waterreminder.ui.components.SettingsCard
import com.jaimatadi.waterreminder.ui.components.SettingsDraft
import com.jaimatadi.waterreminder.ui.components.WeekChartCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(viewModel: ReminderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotificationPermission by remember { mutableStateOf(true) }
    var notificationPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    var canUseFullScreenIntent by remember { mutableStateOf(true) }
    var showResetDialog by remember { mutableStateOf(false) }
    // Set when the reminders switch triggered the permission prompt, so the
    // switch can still turn on once the prompt is answered either way.
    var enableAfterPermission by remember { mutableStateOf(false) }

    fun refreshPermissionStates() {
        hasNotificationPermission = hasNotificationPermission(context)
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
        if (enableAfterPermission) {
            enableAfterPermission = false
            viewModel.setEnabled(true)
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.WaterLogged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Logged a glass · ${event.drinksToday} today",
                        actionLabel = "Undo",
                        withDismissAction = false,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
                }
                UiEvent.UndoDone -> snackbarHostState.showSnackbar("Removed the last glass")
                is UiEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Draft of the schedule settings; resets whenever the saved values change.
    var draft by remember(state.dayStart, state.dayEnd, state.intervalMinutes, state.snoozeMinutes, state.dailyGoal) {
        mutableStateOf(SettingsDraft.from(state))
    }
    val dirty = draft.isDirty(state)

    // Cards rise in one after another on first show.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = dirty,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    SaveBar(
                        canSave = draft.validationError == null,
                        onDiscard = { draft = SettingsDraft.from(state) },
                        onSave = {
                            viewModel.saveSettings(
                                draft.dayStart, draft.dayEnd,
                                draft.intervalMinutes, draft.snoozeMinutes, draft.dailyGoal
                            )
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Rise(entered, order = 0) { Header(today = state.todayDate) }
                Rise(entered, order = 1) {
                    HeroCard(
                        state = state,
                        onToggle = { on ->
                            if (on && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (notificationPermissionPermanentlyDenied) {
                                    context.startActivity(appSettingsIntent(context))
                                    viewModel.setEnabled(true)
                                } else {
                                    enableAfterPermission = true
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.setEnabled(on)
                            }
                        },
                        onLogWater = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logWaterNow()
                        },
                        onPause = viewModel::pauseReminders,
                        onResume = viewModel::resumeReminders,
                        onResetToday = { showResetDialog = true },
                    )
                }
                Rise(entered, order = 2) { WeekChartCard(state) }
                Rise(entered, order = 3) {
                    // SettingsCard emits three cards; keep the 12 dp rhythm between them.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsCard(
                            state = state,
                            draft = draft,
                            onDraftChange = { draft = it },
                            onRespectSilentChange = viewModel::setRespectSilentMode,
                            onAlertStyleChange = viewModel::setAlertStyle,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onThemeModeChange = viewModel::setThemeMode,
                        )
                    }
                }
                Rise(entered, order = 4) {
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
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset today?") },
            text = { Text("This clears today's glasses, reminders and skips. Other days are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetTodayMetrics()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** The design's `rise` keyframe: 18 px up + fade, 0.7 s, staggered 70 ms per card. */
@Composable
private fun Rise(visible: Boolean, order: Int, content: @Composable () -> Unit) {
    val delay = order * 70
    val easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)
    val rise = with(LocalDensity.current) { 18.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700, delayMillis = delay, easing = easing)) +
            slideInVertically(tween(700, delayMillis = delay, easing = easing)) { rise },
    ) {
        content()
    }
}

@Composable
private fun Header(today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_water_drop, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Water Reminder",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            today.format(HeaderDateFormatter),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaveBar(canSave: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                Text("Discard")
            }
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1.4f)) {
                Text("Save schedule")
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun appSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}

private val HeaderDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
