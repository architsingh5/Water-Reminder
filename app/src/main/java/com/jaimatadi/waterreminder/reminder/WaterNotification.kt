package com.jaimatadi.waterreminder.reminder

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jaimatadi.waterreminder.AlarmActivity
import com.jaimatadi.waterreminder.MainActivity
import com.jaimatadi.waterreminder.R

class WaterNotification(private val context: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        // Importance/vibration are immutable after a channel is first created, so
        // the old id is retired here to make sure vibration actually takes effect
        // on devices that already created it without vibration.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Water reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminds you to drink water during your configured day."
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun showReminder() {
        createChannel()

        // Always show the alarm activity: full screen when the phone is locked
        // or asleep, or as a top-anchored floating card (like a real alarm's
        // ringing banner) when the phone is already awake and unlocked.
        val locked = isDeviceLockedOrAsleep()
        launchAlarmActivity(locked)

        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Time to drink water")
            .setContentText("Confirm when you drink so the next reminder starts from that time.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(openAppPendingIntent())
            .setFullScreenIntent(alarmPendingIntent(), true)
            .setDeleteIntent(actionPendingIntent(ReminderActions.Skip, 4))
            .addAction(0, "Drank", actionPendingIntent(ReminderActions.Drank, 1))
            .addAction(0, "Snooze", actionPendingIntent(ReminderActions.Snooze, 2))
            .addAction(0, "Skip", actionPendingIntent(ReminderActions.Skip, 3))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismiss() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun isDeviceLockedOrAsleep(): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        return keyguardManager?.isKeyguardLocked == true || powerManager?.isInteractive == false
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_LOCKED, true)
        }
        return PendingIntent.getActivity(
            context,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchAlarmActivity(locked: Boolean) {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmActivity.EXTRA_LOCKED, locked)
        }

        // Activity launch can be blocked by OEM background-activity-start
        // restrictions; failing here must never prevent the notification below
        // from being posted, since it's the only remaining visible signal.
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Log.w("WaterNotification", "Failed to launch alarm activity", it)
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val LEGACY_CHANNEL_ID = "water_reminders"
        private const val CHANNEL_ID = "water_reminders_v2"
        private const val NOTIFICATION_ID = 2000
    }
}
