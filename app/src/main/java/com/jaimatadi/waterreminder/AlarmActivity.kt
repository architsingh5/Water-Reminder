package com.jaimatadi.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderActions
import com.jaimatadi.waterreminder.reminder.ReminderReceiver
import com.jaimatadi.waterreminder.ui.TimeFormat
import com.jaimatadi.waterreminder.ui.components.AppIcon
import com.jaimatadi.waterreminder.ui.components.WaterWaves
import com.jaimatadi.waterreminder.ui.theme.LocalWaterPalette
import com.jaimatadi.waterreminder.ui.theme.SerifDisplay
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
import com.jaimatadi.waterreminder.ui.theme.heroGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

class AlarmActivity : ComponentActivity() {
    private var alarmPlayer: MediaPlayer? = null
    private var isFloating = false
    private val soundTimeoutHandler = Handler(Looper.getMainLooper())

    // Closes this card when the reminder was answered somewhere else (the
    // notification's action buttons or the widget), so it doesn't keep ringing.
    private val handledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ReminderActions.Handled) {
                stopAlarmSound()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isFloating = !intent.getBooleanExtra(EXTRA_LOCKED, true)
        if (isFloating) {
            setTheme(R.style.Theme_WaterReminder_Alarm_Floating)
        }
        super.onCreate(savedInstanceState)

        // Show over the lock screen like an alarm clock. Deliberately no
        // requestDismissKeyguard: on a PIN/pattern phone that would pop the
        // unlock prompt on top of the reminder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        if (isFloating) {
            window.setGravity(Gravity.TOP)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.4f)
        }

        onBackPressedDispatcher.addCallback(this) {
            sendReminderAction(ReminderActions.Skip)
        }

        maybeStartAlarmSound()

        val repository = ReminderRepository(applicationContext)
        setContent {
            val state by repository.state.collectAsState(initial = ReminderState())
            WaterTheme(themeMode = state.themeMode, dynamicColor = state.dynamicColor) {
                if (isFloating) {
                    FloatingAlarmCard(
                        state = state,
                        onDrank = { sendReminderAction(ReminderActions.Drank) },
                        onSnooze = { sendReminderAction(ReminderActions.Snooze) },
                        onSkip = { sendReminderAction(ReminderActions.Skip) },
                    )
                } else {
                    FullScreenAlarm(
                        state = state,
                        onDrank = { sendReminderAction(ReminderActions.Drank) },
                        onSnooze = { sendReminderAction(ReminderActions.Snooze) },
                        onSkip = { sendReminderAction(ReminderActions.Skip) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            handledReceiver,
            IntentFilter(ReminderActions.Handled),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A new reminder fired while this alarm was still showing: ring again.
        stopAlarmSound()
        maybeStartAlarmSound()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(handledReceiver) }
        stopAlarmSound()
        super.onStop()
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
    }

    private fun sendReminderAction(action: String) {
        stopAlarmSound()
        sendBroadcast(Intent(this, ReminderReceiver::class.java).apply {
            this.action = action
        })
        finish()
    }

    // The alarm plays on the ALARM audio stream, which ignores ringer mode by
    // design. When the user opts in, honor vibrate/silent by skipping the
    // sound entirely; the notification still vibrates or shows silently.
    private fun maybeStartAlarmSound() {
        lifecycleScope.launch {
            val respectSilent = runCatching {
                ReminderRepository(applicationContext).snapshot().respectSilentMode
            }.getOrDefault(false)
            if (respectSilent && isRingerMuted()) return@launch
            startAlarmSound()
        }
    }

    private fun isRingerMuted(): Boolean {
        val audioManager = getSystemService(AudioManager::class.java)
        return audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun startAlarmSound() {
        if (alarmPlayer != null) return

        // Ringing forever drains the battery and is hostile when the phone is
        // out of reach; the card itself stays up so the reminder is not lost.
        soundTimeoutHandler.removeCallbacksAndMessages(null)
        soundTimeoutHandler.postDelayed({ stopAlarmSound() }, SOUND_TIMEOUT_MILLIS)

        for (candidate in alarmSoundCandidates()) {
            val player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmActivity, candidate)
                    isLooping = true
                    setOnErrorListener { mp, _, _ ->
                        mp.release()
                        if (alarmPlayer === mp) {
                            alarmPlayer = null
                        }
                        true
                    }
                    prepare()
                    start()
                }
            }.getOrNull()

            if (player != null) {
                alarmPlayer = player
                return
            }
        }
    }

    private fun stopAlarmSound() {
        soundTimeoutHandler.removeCallbacksAndMessages(null)
        alarmPlayer?.run {
            if (isPlaying) {
                stop()
            }
            release()
        }
        alarmPlayer = null
    }

    private fun alarmSoundCandidates(): List<Uri> {
        return listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            Settings.System.DEFAULT_NOTIFICATION_URI,
        )
    }

    companion object {
        const val EXTRA_LOCKED = "locked"
        private const val SOUND_TIMEOUT_MILLIS = 60_000L
    }
}

/**
 * Full-screen alarm from the design: label, big serif clock, a pulsing drop
 * badge, title + progress, Drank pill with glow, Snooze / Skip text actions,
 * and layered waves rising along the bottom third of the screen.
 */
@Composable
private fun FullScreenAlarm(
    state: ReminderState,
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalWaterPalette.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        val screenHeight = maxHeight

        // Soft radial glow behind the clock and badge.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = 210.dp.toPx()
            val center = Offset(size.width / 2, 150.dp.toPx() + radius)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = 0.16f), scheme.primary.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Waves along the bottom third.
        WaterWaves(
            progress = 0.9f,
            color = palette.heroStart.copy(alpha = 0.9f),
            backColor = palette.heroEnd.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * 0.34f)
                .align(Alignment.BottomCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "REMINDER",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = scheme.onSurfaceVariant
            )
            CurrentTime()
            Spacer(Modifier.height(44.dp))
            PulsingBadge()
            Spacer(Modifier.height(32.dp))
            Text(
                "Time to drink water",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
                color = scheme.onBackground
            )
            Text(
                "${state.drinksToday} of ${state.dailyGoal} glasses today",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            DrankButton(onClick = onDrank, height = 60.dp, fontSize = 18)
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                TextAction("Snooze ${state.snoozeMinutes} min", color = scheme.primary, onClick = onSnooze)
                TextAction("Skip", color = scheme.onSurfaceVariant, onClick = onSkip)
            }
        }
    }
}

/** Compact card used when the phone is already unlocked and in use. */
@Composable
private fun FloatingAlarmCard(
    state: ReminderState,
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalWaterPalette.current
    val shape = RoundedCornerShape(24.dp)

    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = palette.glow, spotColor = palette.glow)
                .clip(shape)
                .background(scheme.surface)
                .border(1.dp, palette.cardBorder, shape)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropBadge(size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Time to drink water",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface
                    )
                    Text(
                        "${state.drinksToday} of ${state.dailyGoal} glasses today",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            DrankButton(onClick = onDrank, height = 52.dp, fontSize = 16)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextAction("Snooze ${state.snoozeMinutes} min", color = scheme.primary, onClick = onSnooze)
                Spacer(Modifier.width(24.dp))
                TextAction("Skip", color = scheme.onSurfaceVariant, onClick = onSkip)
            }
        }
    }
}

@Composable
private fun DrankButton(onClick: () -> Unit, height: androidx.compose.ui.unit.Dp, fontSize: Int) {
    val palette = LocalWaterPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .shadow(18.dp, shape, ambientColor = palette.glow, spotColor = palette.glow)
            .clip(shape)
            .background(palette.logButtonBg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(R.drawable.ic_water_drop, tint = palette.logButtonFg, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text("Drank", fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = palette.logButtonFg)
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun DropBadge(size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    val palette = LocalWaterPalette.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(heroGradient)
            .border(1.dp, palette.ring.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_water_drop, tint = palette.ring, size = iconSize)
    }
}

/** 104 dp badge that breathes (scale 1→1.06) with an expanding glow ring; the drop bobs inside. */
@Composable
private fun PulsingBadge() {
    val palette = LocalWaterPalette.current
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "pulseT"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "bob"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Glow ring grows out and fades as the badge swells.
            val extra = 28.dp.toPx() * t
            drawCircle(
                color = palette.ring.copy(alpha = 0.45f * (1f - t)),
                radius = 52.dp.toPx() + extra,
                style = Stroke(width = extra.coerceAtLeast(1f))
            )
        }
        Box(modifier = Modifier.scale(1f + 0.06f * t)) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(heroGradient)
                    .border(1.dp, palette.ring.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    R.drawable.ic_water_drop,
                    tint = palette.ring,
                    size = 44.dp,
                    modifier = Modifier.offset(y = bob.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentTime() {
    val context = LocalContext.current
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(1_000)
        }
    }
    Text(
        text = TimeFormat.clock(context, time),
        modifier = Modifier.padding(top = 8.dp),
        fontFamily = SerifDisplay,
        fontSize = 80.sp,
        lineHeight = 80.sp,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onBackground
    )
}
