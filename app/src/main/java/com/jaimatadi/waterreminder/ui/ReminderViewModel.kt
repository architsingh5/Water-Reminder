package com.jaimatadi.waterreminder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jaimatadi.waterreminder.data.AlertStyle
import com.jaimatadi.waterreminder.data.ReminderRepository
import com.jaimatadi.waterreminder.data.ReminderState
import com.jaimatadi.waterreminder.data.ThemeMode
import com.jaimatadi.waterreminder.reminder.ReminderCalculator
import com.jaimatadi.waterreminder.reminder.ReminderReconciler
import com.jaimatadi.waterreminder.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** One-off messages for the UI (snackbars); not part of persistent state. */
sealed interface UiEvent {
    data class WaterLogged(val drinksToday: Int) : UiEvent
    data object UndoDone : UiEvent
    data class Error(val message: String) : UiEvent
}

enum class PauseOption(val label: String) {
    OneHour("1 hour"),
    TwoHours("2 hours"),
    UntilTomorrow("Until tomorrow"),
}

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val appContext: Context,
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

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events

    init {
        // App updates and force-stops wipe AlarmManager; make sure an enabled
        // reminder loop is really armed every time the app comes up.
        viewModelScope.launch {
            runCatching { ReminderReconciler.ensureScheduled(appContext) }
            onDataChanged()
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
            if (enabled) {
                val state = repository.snapshot()
                val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            } else {
                repository.setPausedUntil(null)
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
            // The UI blocks invalid combinations, but never let a bad value
            // crash the app: report it instead.
            val saved = runCatching {
                repository.updateSettings(dayStart, dayEnd, intervalMinutes, snoozeMinutes, dailyGoal)
            }
            if (saved.isFailure) {
                _events.tryEmit(UiEvent.Error(saved.exceptionOrNull()?.message ?: "Could not save settings"))
                return@launch
            }
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
            if (state.enabled && !state.isPausedAt(now.toInstant())) {
                val next = ReminderCalculator.afterManualLog(now, state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            _events.tryEmit(UiEvent.WaterLogged(state.drinksToday + 1))
            onDataChanged()
        }
    }

    fun undoLastLog() {
        viewModelScope.launch {
            val undone = repository.undoLastDrink()
            if (!undone) return@launch
            val now = repository.nowZoned()
            val state = repository.snapshot()
            // The undone log had moved the next reminder; put the loop back on
            // a plain interval from now rather than leaving it anchored to a
            // drink that didn't happen.
            if (state.enabled && !state.isPausedAt(now.toInstant())) {
                val next = ReminderCalculator.whenEnabled(now, state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            _events.tryEmit(UiEvent.UndoDone)
            onDataChanged()
        }
    }

    fun pauseReminders(option: PauseOption) {
        viewModelScope.launch {
            val now = repository.nowZoned()
            val state = repository.snapshot()
            val until: Instant = when (option) {
                PauseOption.OneHour -> now.toInstant().plus(Duration.ofHours(1))
                PauseOption.TwoHours -> now.toInstant().plus(Duration.ofHours(2))
                PauseOption.UntilTomorrow -> ReminderCalculator.nextDayStart(now, state.config).toInstant()
            }
            repository.setPausedUntil(until)
            if (state.enabled) {
                val next = ReminderCalculator.afterPause(until.atZone(now.zone), state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            onDataChanged()
        }
    }

    fun resumeReminders() {
        viewModelScope.launch {
            repository.setPausedUntil(null)
            val state = repository.snapshot()
            if (state.enabled) {
                val next = ReminderCalculator.whenEnabled(repository.nowZoned(), state.config).toInstant()
                repository.setNextReminder(next)
                scheduler.schedule(next)
            }
            onDataChanged()
        }
    }

    fun setRespectSilentMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRespectSilentMode(enabled)
        }
    }

    fun setAlertStyle(style: AlertStyle) {
        viewModelScope.launch {
            repository.setAlertStyle(style)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
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
        private val appContext: Context,
        private val onDataChanged: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReminderViewModel(repository, scheduler, appContext, onDataChanged) as T
        }
    }
}
