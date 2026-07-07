package com.jaimatadi.waterreminder

import android.app.KeyguardManager
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.lifecycle.lifecycleScope
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.reminder.ReminderActions
import com.jaimatadi.waterreminder.reminder.ReminderReceiver
import com.jaimatadi.waterreminder.ui.theme.WaterTheme
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {
    private var alarmPlayer: MediaPlayer? = null
    private var isFloating = false
    private val soundTimeoutHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        isFloating = !intent.getBooleanExtra(EXTRA_LOCKED, true)
        if (isFloating) {
            setTheme(R.style.Theme_WaterReminder_Alarm_Floating)
        }
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
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

        setContent {
            WaterTheme {
                AlarmScreen(
                    floating = isFloating,
                    onDrank = { sendReminderAction(ReminderActions.Drank) },
                    onSnooze = { sendReminderAction(ReminderActions.Snooze) },
                    onSkip = { sendReminderAction(ReminderActions.Skip) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A new reminder fired while this alarm was still showing: ring again.
        stopAlarmSound()
        maybeStartAlarmSound()
    }

    override fun onStop() {
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
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    if (floating) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                AlarmContent(
                    onDrank = onDrank,
                    onSnooze = onSnooze,
                    onSkip = onSkip,
                    modifier = Modifier.padding(20.dp),
                    titleStyle = MaterialTheme.typography.titleLarge,
                    bodyStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PulsingDrop()
                Spacer(Modifier.height(20.dp))
                CurrentTime()
                Spacer(Modifier.height(8.dp))
                AlarmContent(
                    onDrank = onDrank,
                    onSnooze = onSnooze,
                    onSkip = onSkip,
                    titleStyle = MaterialTheme.typography.headlineLarge,
                    bodyStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun PulsingDrop() {
    val transition = rememberInfiniteTransition(label = "dropPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dropScale"
    )
    Box(
        modifier = Modifier
            .size(110.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_water_drop),
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
private fun CurrentTime() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(1_000)
        }
    }
    Text(
        text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun AlarmContent(
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    titleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Time to drink water",
            style = titleStyle,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Confirm after drinking so the next reminder starts from this time.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = bodyStyle,
            textAlign = TextAlign.Center
        )
        Button(onClick = onDrank, modifier = Modifier.fillMaxWidth()) {
            Text("Drank")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                Text("Snooze")
            }
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                Text("Skip")
            }
        }
    }
}
