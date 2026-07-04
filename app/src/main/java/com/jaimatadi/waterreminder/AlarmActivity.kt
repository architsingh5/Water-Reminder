package com.jaimatadi.waterreminder

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaimatadi.waterreminder.reminder.ReminderActions
import com.jaimatadi.waterreminder.reminder.ReminderReceiver

class AlarmActivity : ComponentActivity() {
    private var alarmPlayer: MediaPlayer? = null
    private var isFloating = false

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

        startAlarmSound()

        setContent {
            MaterialTheme {
                AlarmScreen(
                    floating = isFloating,
                    onDrank = { sendReminderAction(ReminderActions.Drank) },
                    onSnooze = { sendReminderAction(ReminderActions.Snooze) },
                    onSkip = { sendReminderAction(ReminderActions.Skip) },
                )
            }
        }
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

    private fun startAlarmSound() {
        if (alarmPlayer != null) return

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
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
