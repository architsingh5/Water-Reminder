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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderActions
import com.jaimatadi.waterreminder.reminder.ReminderReceiver
import com.jaimatadi.waterreminder.ui.TimeFormat
import com.jaimatadi.waterreminder.ui.components.WaterWaves
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
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
            WaterTheme(dynamicColor = state.dynamicColor) {
                AlarmScreen(
                    floating = isFloating,
                    state = state,
                    onDrank = { sendReminderAction(ReminderActions.Drank) },
                    onSnooze = { sendReminderAction(ReminderActions.Snooze) },
                    onSkip = { sendReminderAction(ReminderActions.Skip) },
                )
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

@Composable
private fun AlarmScreen(
    floating: Boolean,
    state: ReminderState,
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    val progressText = "${state.drinksToday} of ${state.dailyGoal} glasses today"

    if (floating) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropBadge(size = 44.dp, iconSize = 24.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Time to drink water",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AlarmActions(
                    onDrank = onDrank,
                    onSnooze = onSnooze,
                    onSkip = onSkip,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.background))
            )
    ) {
        // Water rising along the bottom of the screen: the more of today's goal
        // is done, the higher it sits.
        WaterWaves(
            progress = 0.18f + 0.3f * (state.drinksToday.toFloat() / state.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f),
            color = scheme.primary.copy(alpha = 0.22f),
            backColor = scheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulsingDrop()
            Spacer(Modifier.height(24.dp))
            CurrentTime()
            Spacer(Modifier.height(6.dp))
            Text(
                "Time to drink water",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = scheme.onBackground
            )
            Text(
                progressText,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(36.dp))
            AlarmActions(onDrank = onDrank, onSnooze = onSnooze, onSkip = onSkip)
        }
    }
}

@Composable
private fun DropBadge(size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_water_drop),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun PulsingDrop() {
    val transition = rememberInfiniteTransition(label = "dropPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dropScale"
    )
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        DropBadge(size = 104.dp, iconSize = 56.dp)
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
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun AlarmActions(
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onDrank,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Drank", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Snooze")
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Skip")
            }
        }
    }
}
