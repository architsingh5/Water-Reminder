package com.jaimatadi.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaimatadi.waterreminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ReminderRepository(appContext)
                val state = repository.snapshot()
                val scheduler = ReminderScheduler(appContext)

                if (state.enabled) {
                    val now = repository.nowZoned()
                    val storedNext = state.nextReminderAt
                    val next = if (storedNext != null && storedNext.isAfter(now.toInstant())) {
                        storedNext
                    } else {
                        ReminderCalculator.whenEnabled(now, state.config).toInstant()
                    }
                    repository.setNextReminder(next)
                    scheduler.schedule(next)
                } else {
                    scheduler.cancel()
                }
            } catch (t: Throwable) {
                runCatching {
                    val repository = ReminderRepository(appContext)
                    val state = repository.snapshot()
                    if (state.enabled) {
                        val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
                        repository.setNextReminder(next)
                        ReminderScheduler(appContext).schedule(next)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
