package com.jaimatadi.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.widget.WaterWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIntent(appContext, intent.action)
            } catch (t: Throwable) {
                runCatching { fallbackReschedule(appContext) }
            } finally {
                runCatching { WaterWidgetProvider.requestUpdate(appContext) }
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIntent(context: Context, action: String?) {
        val repository = ReminderRepository(context)
        val scheduler = ReminderScheduler(context)
        val notification = WaterNotification(context)
        val state = repository.snapshot()
        val now = repository.nowZoned()
        val nowInstant = now.toInstant()

        when (action) {
            ReminderActions.Show -> {
                if (!state.enabled) {
                    scheduler.cancel()
                    notification.dismiss()
                    repository.setNextReminder(null)
                    return
                }

                if (ReminderCalculator.isInsideActiveWindow(now.toLocalTime(), state.config)) {
                    repository.recordReminderShown(nowInstant)
                    notification.showReminder()
                    // Schedule the next occurrence immediately so the reminder loop
                    // keeps going even if the user never acts on this alert.
                    scheduleNext(repository, scheduler, ReminderCalculator.whenEnabled(now, state.config).toInstant())
                } else {
                    scheduleNext(repository, scheduler, ReminderCalculator.normalize(now, state.config).toInstant())
                }
            }

            ReminderActions.Drank -> {
                repository.recordDrink(nowInstant)
                notification.dismiss()
                if (state.enabled) {
                    scheduleNext(repository, scheduler, ReminderCalculator.afterDrink(now, state.config).toInstant())
                } else {
                    scheduler.cancel()
                    repository.setNextReminder(null)
                }
            }

            ReminderActions.Snooze -> {
                notification.dismiss()
                if (state.enabled) {
                    scheduleNext(repository, scheduler, ReminderCalculator.afterSnooze(now, state.config).toInstant())
                } else {
                    scheduler.cancel()
                    repository.setNextReminder(null)
                }
            }

            ReminderActions.Skip -> {
                repository.recordSkip()
                notification.dismiss()
                if (state.enabled) {
                    scheduleNext(repository, scheduler, ReminderCalculator.afterSkip(now, state.config).toInstant())
                } else {
                    scheduler.cancel()
                    repository.setNextReminder(null)
                }
            }
        }
    }

    private suspend fun fallbackReschedule(context: Context) {
        val repository = ReminderRepository(context)
        val scheduler = ReminderScheduler(context)
        val state = repository.snapshot()
        if (state.enabled) {
            val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
            repository.setNextReminder(next)
            scheduler.schedule(next)
        }
    }

    private suspend fun scheduleNext(
        repository: ReminderRepository,
        scheduler: ReminderScheduler,
        next: Instant,
    ) {
        repository.setNextReminder(next)
        scheduler.schedule(next)
    }
}
