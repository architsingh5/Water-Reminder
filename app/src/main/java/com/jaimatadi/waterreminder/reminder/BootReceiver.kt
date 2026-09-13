package com.jaimatadi.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaimatadi.waterreminder.widget.WaterWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the reminder alarm after anything that makes AlarmManager forget it:
 * reboot, app update, or a wall-clock / timezone change that would leave the
 * stored trigger time outside the awake window.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderReconciler.ensureScheduled(appContext)
            } catch (t: Throwable) {
                // Nothing more to do; the next app launch reconciles again.
            } finally {
                runCatching { WaterWidgetProvider.requestUpdate(appContext) }
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
