package com.jaimatadi.waterreminder.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderReconcilerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val config = ReminderConfig(
        dayStart = LocalTime.of(7, 0),
        dayEnd = LocalTime.of(23, 0),
        intervalMinutes = 60,
        snoozeMinutes = 15,
    )
    private val now = ZonedDateTime.of(LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), zone)

    @Test
    fun disabledRemindersResolveToNothing() {
        assertNull(ReminderReconciler.resolveNext(now, false, now.plusHours(1).toInstant(), null, config))
    }

    @Test
    fun futureStoredReminderIsKeptAsIs() {
        val stored = now.plusMinutes(25).toInstant()

        assertEquals(stored, ReminderReconciler.resolveNext(now, true, stored, null, config))
    }

    @Test
    fun missedOrMissingReminderRestartsFromNow() {
        val expected = now.plusHours(1).toInstant()

        assertEquals(expected, ReminderReconciler.resolveNext(now, true, null, null, config))
        assertEquals(expected, ReminderReconciler.resolveNext(now, true, now.minusMinutes(5).toInstant(), null, config))
    }

    @Test
    fun activePauseDefersToPauseEnd() {
        val pausedUntil = now.plus(Duration.ofHours(2)).toInstant()

        val next = ReminderReconciler.resolveNext(now, true, now.plusMinutes(10).toInstant(), pausedUntil, config)

        assertEquals(pausedUntil, next)
    }

    @Test
    fun activePauseKeepsLaterStoredReminder() {
        val pausedUntil = now.plus(Duration.ofHours(1)).toInstant()
        val stored = now.plusHours(3).toInstant()

        assertEquals(stored, ReminderReconciler.resolveNext(now, true, stored, pausedUntil, config))
    }

    @Test
    fun pauseEndingOutsideAwakeHoursRollsToNextDayStart() {
        val lateEvening = now.with(LocalTime.of(22, 30))
        val pausedUntil = lateEvening.plusHours(2).toInstant()

        val next = ReminderReconciler.resolveNext(lateEvening, true, null, pausedUntil, config)

        assertEquals(lateEvening.plusDays(1).with(LocalTime.of(7, 0)).toInstant(), next)
    }

    @Test
    fun expiredPauseIsIgnored() {
        val pausedUntil = now.minusMinutes(1).toInstant()

        assertEquals(now.plusHours(1).toInstant(), ReminderReconciler.resolveNext(now, true, null, pausedUntil, config))
    }
}
