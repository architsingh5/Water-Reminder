package com.jaimatadi.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 9, 14)

    private fun day(offset: Long, drinks: Int) = DailyMetrics(today.minusDays(offset), drinks, 0, 0)

    @Test
    fun emptyHistoryHasNoStreak() {
        assertEquals(Streaks(0, 0), computeStreaks(emptyList(), 8, today))
    }

    @Test
    fun consecutiveGoalDaysEndingYesterdayKeepStreakAliveWhileTodayUnfinished() {
        val history = listOf(day(0, 3), day(1, 8), day(2, 9))

        assertEquals(Streaks(current = 2, best = 2), computeStreaks(history, 8, today))
    }

    @Test
    fun todayCountsOnceGoalIsMet() {
        val history = listOf(day(0, 8), day(1, 8), day(2, 9))

        assertEquals(Streaks(current = 3, best = 3), computeStreaks(history, 8, today))
    }

    @Test
    fun missedDayBeforeYesterdayBreaksCurrentStreakButBestRemembersLongerRun() {
        val history = listOf(day(0, 8), day(1, 2), day(2, 8), day(3, 8), day(4, 10))

        assertEquals(Streaks(current = 1, best = 3), computeStreaks(history, 8, today))
    }

    @Test
    fun gapDaysMissingFromHistoryCountAsMissed() {
        val history = listOf(day(0, 8), day(2, 8), day(3, 8))

        assertEquals(Streaks(current = 1, best = 2), computeStreaks(history, 8, today))
    }

    @Test
    fun nonPositiveGoalNeverStreaks() {
        assertEquals(Streaks(0, 0), computeStreaks(listOf(day(0, 5)), 0, today))
    }
}
