package com.jaimatadi.waterreminder.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Water reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminds you to drink water during your configured day."
        }

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder() {
        createChannel()
        launchAlarmActivity()

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
        }
        return PendingIntent.getActivity(
            context,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchAlarmActivity() {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // Full-screen launch can be blocked by OEM background-activity-start
        // restrictions; failing here must never prevent the notification below
        // from being posted, since it's the only remaining visible signal.
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Log.w("WaterNotification", "Failed to launch full-screen alarm activity", it)
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
        private const val CHANNEL_ID = "water_reminders"
        private const val NOTIFICATION_ID = 2000
    }
}
