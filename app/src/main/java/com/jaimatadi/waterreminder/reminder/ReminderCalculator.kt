package com.jaimatadi.waterreminder.reminder

import java.time.LocalTime
import java.time.ZonedDateTime

data class ReminderConfig(
    val dayStart: LocalTime,
    val dayEnd: LocalTime,
    val intervalMinutes: Long,
    val snoozeMinutes: Long,
)

object ReminderCalculator {
    fun afterDrink(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return normalize(now.plusMinutes(config.intervalMinutes), config)
    }

    fun afterManualLog(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return afterDrink(now, config)
    }

    fun afterSnooze(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return normalize(now.plusMinutes(config.snoozeMinutes), config)
    }

    fun afterSkip(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return normalize(now.plusMinutes(config.intervalMinutes), config)
    }

    fun whenEnabled(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return normalize(now.plusMinutes(config.intervalMinutes), config)
    }

    /** First reminder once a pause ends: at the pause end itself, moved into the active window. */
    fun afterPause(pausedUntil: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        return normalize(pausedUntil, config)
    }

    /** Next day-start after [now]; used for "pause until tomorrow". */
    fun nextDayStart(now: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        val todayStart = now.with(config.dayStart).withSecond(0).withNano(0)
        return if (todayStart.isAfter(now)) todayStart else todayStart.plusDays(1)
    }

    fun normalize(candidate: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        require(config.intervalMinutes > 0) { "intervalMinutes must be positive" }
        require(config.snoozeMinutes > 0) { "snoozeMinutes must be positive" }
        require(config.dayStart != config.dayEnd) { "dayStart and dayEnd must differ" }

        if (isInsideActiveWindow(candidate.toLocalTime(), config)) {
            return candidate.withSecond(0).withNano(0)
        }

        val time = candidate.toLocalTime()
        val start = config.dayStart
        val end = config.dayEnd

        return if (start.isBefore(end)) {
            if (time.isBefore(start)) {
                candidate.with(start)
            } else {
                candidate.plusDays(1).with(start)
            }
        } else {
            candidate.with(start)
        }.withSecond(0).withNano(0)
    }

    fun isInsideActiveWindow(time: LocalTime, config: ReminderConfig): Boolean {
        val start = config.dayStart
        val end = config.dayEnd

        return if (start.isBefore(end)) {
            !time.isBefore(start) && time.isBefore(end)
        } else if (start.isAfter(end)) {
            !time.isBefore(start) || time.isBefore(end)
        } else {
            false
        }
    }
}
