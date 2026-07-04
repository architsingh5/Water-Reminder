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
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        }

        onBackPressedDispatcher.addCallback(this) {
            sendReminderAction(ReminderActions.Skip)
        }

        startAlarmSound()

        setContent {
            MaterialTheme {
                AlarmScreen(
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
}

@Composable
private fun AlarmScreen(
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Time to drink water",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Confirm after drinking so the next reminder starts from this time.",
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                style = MaterialTheme.typography.bodyLarge,
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
}
