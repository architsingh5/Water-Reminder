package com.jaimatadi.waterreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.jaimatadi.waterreminder.MainActivity
import com.jaimatadi.waterreminder.R
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderActions
import com.jaimatadi.waterreminder.reminder.ReminderReceiver
import com.jaimatadi.waterreminder.reminder.ReminderReconciler
import com.jaimatadi.waterreminder.ui.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class WaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = ReminderRepository(appContext).snapshot()
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, state))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget placed: make sure it rolls over at midnight.
        runCatching { ReminderReconciler.ensureWidgetRefresh(context.applicationContext) }
    }

    private fun buildViews(context: Context, state: ReminderState): RemoteViews {
        val dynamic = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val layout = if (dynamic) R.layout.widget_water_dynamic else R.layout.widget_water

        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_count, state.drinksToday.toString())
            setTextViewText(
                R.id.widget_goal,
                context.getString(R.string.widget_of_goal, state.dailyGoal)
            )
            setProgressBar(
                R.id.widget_progress,
                state.dailyGoal,
                state.drinksToday.coerceAtMost(state.dailyGoal),
                false
            )
            setTextViewText(R.id.widget_next, nextReminderLabel(context, state))

            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_log_button, logWaterIntent(context))
        }
    }

    private fun nextReminderLabel(context: Context, state: ReminderState): String {
        val next = state.nextReminderAt
        val pausedUntil = state.pausedUntil
        return when {
            !state.enabled -> context.getString(R.string.widget_reminders_off)
            pausedUntil != null && pausedUntil.isAfter(Instant.now()) ->
                context.getString(R.string.widget_paused_until, TimeFormat.clock(context, pausedUntil))
            next == null -> context.getString(R.string.widget_no_reminder)
            else -> context.getString(R.string.widget_next_at, TimeFormat.clock(context, next))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun logWaterIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderActions.Drank
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_LOG_WATER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_OPEN_APP = 20
        private const val REQUEST_LOG_WATER = 21

        /**
         * Ask the system to redraw every placed widget. Funnels through the
         * standard APPWIDGET_UPDATE broadcast so rendering happens in one place.
         */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WaterWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val intent = Intent(context, WaterWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
