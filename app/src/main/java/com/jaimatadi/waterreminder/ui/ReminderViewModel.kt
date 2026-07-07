package com.jaimatadi.waterreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.reminder.ReminderCalculator
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val onDataChanged: () -> Unit = {},
) : ViewModel() {
    // Re-subscribing every minute forces the repository to re-evaluate
    // "today", so the Today card rolls over at midnight even if no data
    // is written while the app stays open.
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReminderState> = minuteTicker
        .flatMapLatest { repository.state }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderState(),
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
            if (enabled) {
                val state = repository.snapshot()
                val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            } else {
                scheduler.cancel()
            }
            onDataChanged()
        }
    }

    fun saveSettings(
        dayStart: LocalTime,
        dayEnd: LocalTime,
        intervalMinutes: Long,
        snoozeMinutes: Long,
        dailyGoal: Int,
    ) {
        viewModelScope.launch {
            repository.updateSettings(dayStart, dayEnd, intervalMinutes, snoozeMinutes, dailyGoal)
            val state = repository.snapshot()
            if (state.enabled) {
                val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            onDataChanged()
        }
    }

    fun logWaterNow() {
        viewModelScope.launch {
            val now = repository.nowZoned()
            val state = repository.snapshot()
            repository.recordDrink(now.toInstant())
            if (state.enabled) {
                val next = ReminderCalculator.afterManualLog(now, state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            onDataChanged()
        }
    }

    fun resetTodayMetrics() {
        viewModelScope.launch {
            repository.resetTodayMetrics()
            onDataChanged()
        }
    }

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    fun exactAlarmSettingsIntent() = scheduler.exactAlarmSettingsIntent()

    fun canUseFullScreenIntent(): Boolean = scheduler.canUseFullScreenIntent()

    fun fullScreenIntentSettingsIntent() = scheduler.fullScreenIntentSettingsIntent()

    class Factory(
        private val repository: ReminderRepository,
        private val scheduler: ReminderScheduler,
        private val onDataChanged: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReminderViewModel(repository, scheduler, onDataChanged) as T
        }
    }
}
