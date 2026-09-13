package com.jaimatadi.waterreminder.reminder

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.widget.WaterWidgetProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Brings the AlarmManager state back in line with what is stored.
 *
 * AlarmManager silently drops alarms on app update, force-stop and reboot, so
 * every entry point (boot/package-replaced broadcasts, app launch) runs this to
 * make sure an enabled reminder loop is actually armed.
 */
object ReminderReconciler {
    /**
     * Pure decision: what the next reminder should be given the stored state.
     * Returns null when reminders are off.
     */
    fun resolveNext(
        now: ZonedDateTime,
        enabled: Boolean,
        storedNext: Instant?,
        pausedUntil: Instant?,
        config: ReminderConfig,
    ): Instant? {
        if (!enabled) return null

        val nowInstant = now.toInstant()
        if (pausedUntil != null && pausedUntil.isAfter(nowInstant)) {
            val resume = ReminderCalculator.afterPause(pausedUntil.atZone(now.zone), config).toInstant()
            return if (storedNext != null && !storedNext.isBefore(resume)) storedNext else resume
        }

        return if (storedNext != null && storedNext.isAfter(nowInstant)) {
            storedNext
        } else {
            ReminderCalculator.whenEnabled(now, config).toInstant()
        }
    }

    suspend fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val repository = ReminderRepository(appContext)
        val scheduler = ReminderScheduler(appContext)
        val state = repository.snapshot()
        val now = repository.nowZoned()

        val next = resolveNext(now, state.enabled, state.nextReminderAt, state.pausedUntil, state.config)
        if (next == null) {
            scheduler.cancel()
            repository.setNextReminder(null)
        } else {
            repository.setNextReminder(next)
            scheduler.schedule(next)
        }

        // A pause that already ended is just noise in the UI; clear it.
        if (state.pausedUntil != null && !state.pausedUntil.isAfter(now.toInstant())) {
            repository.setPausedUntil(null)
        }

        ensureWidgetRefresh(appContext)
    }

    /**
     * Widgets only redraw on their 30-minute system tick, so without this a
     * widget keeps showing yesterday's count for a while after midnight.
     */
    fun ensureWidgetRefresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZonedDateTime.now().zone).toInstant()
        ReminderScheduler(context).scheduleWidgetRefresh(midnight)
    }
}
