package com.jaimatadi.waterreminder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaimatadi.waterreminder.reminder.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val Context.waterReminderDataStore by preferencesDataStore("water_reminder")

enum class AlertStyle(val storedValue: String) {
    /** Full-screen / floating alarm card with sound (the original behavior). */
    Alarm("alarm"),

    /** A regular heads-up notification with actions, no ringing. */
    Notification("notification");

    companion object {
        fun fromStored(value: String?): AlertStyle =
            entries.firstOrNull { it.storedValue == value } ?: Alarm
    }
}

enum class ThemeMode(val storedValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

data class ReminderState(
    val enabled: Boolean = false,
    val dayStart: LocalTime = LocalTime.of(7, 0),
    val dayEnd: LocalTime = LocalTime.of(23, 0),
    val intervalMinutes: Long = 60,
    val snoozeMinutes: Long = 15,
    val dailyGoal: Int = 8,
    val respectSilentMode: Boolean = false,
    val alertStyle: AlertStyle = AlertStyle.Alarm,
    val dynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val pausedUntil: Instant? = null,
    val nextReminderAt: Instant? = null,
    val lastShownAt: Instant? = null,
    val lastDrinkAt: Instant? = null,
    val todayDate: LocalDate = LocalDate.now(),
    val drinksToday: Int = 0,
    val skipsToday: Int = 0,
    val remindersToday: Int = 0,
    val dailyMetrics: List<DailyMetrics> = emptyList(),
) {
    val config: ReminderConfig
        get() = ReminderConfig(dayStart, dayEnd, intervalMinutes, snoozeMinutes)

    val streaks: Streaks
        get() = computeStreaks(dailyMetrics, dailyGoal, todayDate)

    fun isPausedAt(now: Instant): Boolean = pausedUntil?.isAfter(now) == true
}

data class DailyMetrics(
    val date: LocalDate,
    val drinks: Int,
    val reminders: Int,
    val skips: Int,
)

class ReminderRepository(context: Context) {
    private val dataStore = context.applicationContext.waterReminderDataStore
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: Flow<ReminderState> = dataStore.data.map { preferences ->
        val today = LocalDate.now(zone)
        val storedDate = preferences[Keys.todayDate]?.let(LocalDate::parse) ?: today
        val isToday = storedDate == today
        val drinksToday = if (isToday) preferences[Keys.drinksToday] ?: 0 else 0
        val skipsToday = if (isToday) preferences[Keys.skipsToday] ?: 0 else 0
        val remindersToday = if (isToday) preferences[Keys.remindersToday] ?: 0 else 0
        val history = parseDailyMetrics(preferences[Keys.dailyMetrics])
            .upsert(DailyMetrics(today, drinksToday, remindersToday, skipsToday))
            .sortedByDescending { it.date }

        ReminderState(
            enabled = preferences[Keys.enabled] ?: false,
            dayStart = minutesToTime(preferences[Keys.dayStartMinutes] ?: 420),
            dayEnd = minutesToTime(preferences[Keys.dayEndMinutes] ?: 1380),
            intervalMinutes = preferences[Keys.intervalMinutes] ?: 60L,
            snoozeMinutes = preferences[Keys.snoozeMinutes] ?: 15L,
            dailyGoal = preferences[Keys.dailyGoal] ?: 8,
            respectSilentMode = preferences[Keys.respectSilentMode] ?: false,
            alertStyle = AlertStyle.fromStored(preferences[Keys.alertStyle]),
            dynamicColor = preferences[Keys.dynamicColor] ?: false,
            themeMode = ThemeMode.fromStored(preferences[Keys.themeMode]),
            pausedUntil = preferences[Keys.pausedUntilMillis]?.let(Instant::ofEpochMilli),
            nextReminderAt = preferences[Keys.nextReminderAtMillis]?.let(Instant::ofEpochMilli),
            lastShownAt = preferences[Keys.lastShownAtMillis]?.let(Instant::ofEpochMilli),
            lastDrinkAt = preferences[Keys.lastDrinkAtMillis]?.let(Instant::ofEpochMilli),
            todayDate = today,
            drinksToday = drinksToday,
            skipsToday = skipsToday,
            remindersToday = remindersToday,
            dailyMetrics = history,
        )
    }

    suspend fun snapshot(): ReminderState = state.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.enabled] = enabled
            if (!enabled) {
                preferences.remove(Keys.nextReminderAtMillis)
            }
        }
    }

    suspend fun updateSettings(
        dayStart: LocalTime,
        dayEnd: LocalTime,
        intervalMinutes: Long,
        snoozeMinutes: Long,
        dailyGoal: Int,
    ) {
        require(dayStart != dayEnd) { "Start and end time must differ" }
        require(intervalMinutes > 0) { "Interval must be positive" }
        require(snoozeMinutes > 0) { "Snooze duration must be positive" }
        require(dailyGoal > 0) { "Daily goal must be positive" }

        dataStore.edit { preferences ->
            preferences[Keys.dayStartMinutes] = timeToMinutes(dayStart)
            preferences[Keys.dayEndMinutes] = timeToMinutes(dayEnd)
            preferences[Keys.intervalMinutes] = intervalMinutes
            preferences[Keys.snoozeMinutes] = snoozeMinutes
            preferences[Keys.dailyGoal] = dailyGoal
        }
    }

    suspend fun recordReminderShown(now: Instant) {
        dataStore.edit { preferences ->
            ensureToday(preferences)
            preferences[Keys.remindersToday] = (preferences[Keys.remindersToday] ?: 0) + 1
            preferences[Keys.lastShownAtMillis] = now.toEpochMilli()
            persistCurrentDay(preferences)
        }
    }

    suspend fun recordDrink(now: Instant) {
        dataStore.edit { preferences ->
            ensureToday(preferences)
            preferences[Keys.lastDrinkAtMillis] = now.toEpochMilli()
            preferences[Keys.drinksToday] = (preferences[Keys.drinksToday] ?: 0) + 1
            persistCurrentDay(preferences)
        }
    }

    /** Reverses one "Log water" tap. Returns false when there was nothing to undo. */
    suspend fun undoLastDrink(): Boolean {
        var undone = false
        dataStore.edit { preferences ->
            ensureToday(preferences)
            val drinks = preferences[Keys.drinksToday] ?: 0
            if (drinks > 0) {
                preferences[Keys.drinksToday] = drinks - 1
                undone = true
            }
            persistCurrentDay(preferences)
        }
        return undone
    }

    suspend fun recordSkip() {
        dataStore.edit { preferences ->
            ensureToday(preferences)
            preferences[Keys.skipsToday] = (preferences[Keys.skipsToday] ?: 0) + 1
            persistCurrentDay(preferences)
        }
    }

    suspend fun resetTodayMetrics() {
        dataStore.edit { preferences ->
            ensureToday(preferences)
            preferences[Keys.drinksToday] = 0
            preferences[Keys.skipsToday] = 0
            preferences[Keys.remindersToday] = 0
            persistCurrentDay(preferences)
        }
    }

    suspend fun setRespectSilentMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.respectSilentMode] = enabled
        }
    }

    suspend fun setAlertStyle(style: AlertStyle) {
        dataStore.edit { preferences ->
            preferences[Keys.alertStyle] = style.storedValue
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.dynamicColor] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode.storedValue
        }
    }

    suspend fun setPausedUntil(pausedUntil: Instant?) {
        dataStore.edit { preferences ->
            if (pausedUntil == null) {
                preferences.remove(Keys.pausedUntilMillis)
            } else {
                preferences[Keys.pausedUntilMillis] = pausedUntil.toEpochMilli()
            }
        }
    }

    suspend fun setNextReminder(nextReminderAt: Instant?) {
        dataStore.edit { preferences ->
            if (nextReminderAt == null) {
                preferences.remove(Keys.nextReminderAtMillis)
            } else {
                preferences[Keys.nextReminderAtMillis] = nextReminderAt.toEpochMilli()
            }
        }
    }

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(zone)

    private fun ensureToday(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        val today = LocalDate.now(zone).toString()
        if (preferences[Keys.todayDate] != today) {
            persistCurrentDay(preferences)
            preferences[Keys.todayDate] = today
            preferences[Keys.drinksToday] = 0
            preferences[Keys.skipsToday] = 0
            preferences[Keys.remindersToday] = 0
        }
    }

    private fun persistCurrentDay(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        val date = preferences[Keys.todayDate]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now(zone)
        val current = DailyMetrics(
            date = date,
            drinks = preferences[Keys.drinksToday] ?: 0,
            reminders = preferences[Keys.remindersToday] ?: 0,
            skips = preferences[Keys.skipsToday] ?: 0,
        )
        val updated = parseDailyMetrics(preferences[Keys.dailyMetrics]).upsert(current)
        preferences[Keys.dailyMetrics] = encodeDailyMetrics(updated)
    }

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val dayStartMinutes = intPreferencesKey("day_start_minutes")
        val dayEndMinutes = intPreferencesKey("day_end_minutes")
        val intervalMinutes = longPreferencesKey("interval_minutes")
        val snoozeMinutes = longPreferencesKey("snooze_minutes")
        val dailyGoal = intPreferencesKey("daily_goal")
        val respectSilentMode = booleanPreferencesKey("respect_silent_mode")
        val alertStyle = stringPreferencesKey("alert_style")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val pausedUntilMillis = longPreferencesKey("paused_until_millis")
        val nextReminderAtMillis = longPreferencesKey("next_reminder_at_millis")
        val lastShownAtMillis = longPreferencesKey("last_shown_at_millis")
        val lastDrinkAtMillis = longPreferencesKey("last_drink_at_millis")
        val todayDate = stringPreferencesKey("today_date")
        val drinksToday = intPreferencesKey("drinks_today")
        val skipsToday = intPreferencesKey("skips_today")
        val remindersToday = intPreferencesKey("reminders_today")
        val dailyMetrics = stringPreferencesKey("daily_metrics")
    }
}

private fun List<DailyMetrics>.upsert(metric: DailyMetrics): List<DailyMetrics> {
    return filterNot { it.date == metric.date }
        .plus(metric)
        .sortedByDescending { it.date }
        .take(60)
}

private fun parseDailyMetrics(raw: String?): List<DailyMetrics> {
    if (raw.isNullOrBlank()) return emptyList()

    return raw.lineSequence().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 4) return@mapNotNull null

        runCatching {
            DailyMetrics(
                date = LocalDate.parse(parts[0]),
                drinks = parts[1].toInt(),
                reminders = parts[2].toInt(),
                skips = parts[3].toInt(),
            )
        }.getOrNull()
    }.toList()
}

private fun encodeDailyMetrics(metrics: List<DailyMetrics>): String {
    return metrics
        .sortedByDescending { it.date }
        .take(60)
        .joinToString(separator = "\n") { metric ->
            "${metric.date}|${metric.drinks}|${metric.reminders}|${metric.skips}"
        }
}

private fun minutesToTime(minutes: Int): LocalTime {
    return LocalTime.of(minutes / 60, minutes % 60)
}

private fun timeToMinutes(time: LocalTime): Int {
    return time.hour * 60 + time.minute
}
