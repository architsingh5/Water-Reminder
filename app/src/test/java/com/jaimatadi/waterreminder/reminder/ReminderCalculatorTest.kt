package com.jaimatadi.waterreminder.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderCalculatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val config = ReminderConfig(
        dayStart = LocalTime.of(7, 0),
        dayEnd = LocalTime.of(23, 0),
        intervalMinutes = 60,
        snoozeMinutes = 15,
    )

    @Test
    fun snoozedReminderThenDrankSchedulesFromDrinkConfirmationTime() {
        val reminderAt = at(10, 0)
        val snoozedAt = ReminderCalculator.afterSnooze(reminderAt, config)
        val drankAt = snoozedAt

        val next = ReminderCalculator.afterDrink(drankAt, config)

        assertEquals(at(10, 15), snoozedAt)
        assertEquals(at(11, 15), next)
    }

    @Test
    fun manualLogSchedulesFromManualLogTime() {
        val manualLogAt = at(14, 25)

        val next = ReminderCalculator.afterManualLog(manualLogAt, config)

        assertEquals(at(15, 25), next)
    }

    @Test
    fun afterEndMovesToNextDayStart() {
        val drankAt = at(22, 30)

        val next = ReminderCalculator.afterDrink(drankAt, config)

        assertEquals(atNextDay(7, 0), next)
    }

    @Test
    fun beforeStartMovesToTodayStart() {
        val candidate = at(6, 10)

        val next = ReminderCalculator.normalize(candidate, config)

        assertEquals(at(7, 0), next)
    }

    @Test
    fun skipSchedulesFromSkipTimeUsingNormalInterval() {
        val skippedAt = at(9, 45)

        val next = ReminderCalculator.afterSkip(skippedAt, config)

        assertEquals(at(10, 45), next)
    }

    @Test
    fun normalizeTruncatesSecondsInsideActiveWindow() {
        val candidate = ZonedDateTime.of(
            LocalDate.of(2026, 7, 4),
            LocalTime.of(10, 15, 42, 500_000_000),
            zone
        )

        val next = ReminderCalculator.normalize(candidate, config)

        assertEquals(at(10, 15), next)
    }

    @Test
    fun nextDayStartIsTodayWhenStillAhead() {
        assertEquals(at(7, 0), ReminderCalculator.nextDayStart(at(6, 30), config))
    }

    @Test
    fun nextDayStartIsTomorrowOnceTodayStartHasPassed() {
        assertEquals(atNextDay(7, 0), ReminderCalculator.nextDayStart(at(7, 0), config))
        assertEquals(atNextDay(7, 0), ReminderCalculator.nextDayStart(at(15, 0), config))
    }

    @Test
    fun afterPauseInsideWindowResumesAtPauseEnd() {
        assertEquals(at(12, 30), ReminderCalculator.afterPause(at(12, 30), config))
    }

    @Test
    fun afterPauseOutsideWindowMovesToNextStart() {
        assertEquals(atNextDay(7, 0), ReminderCalculator.afterPause(at(23, 30), config))
    }

    private fun at(hour: Int, minute: Int): ZonedDateTime {
        return ZonedDateTime.of(LocalDate.of(2026, 7, 4), LocalTime.of(hour, minute), zone)
    }

    private fun atNextDay(hour: Int, minute: Int): ZonedDateTime {
        return ZonedDateTime.of(LocalDate.of(2026, 7, 5), LocalTime.of(hour, minute), zone)
    }
}
