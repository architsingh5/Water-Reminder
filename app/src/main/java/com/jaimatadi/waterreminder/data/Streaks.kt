package com.jaimatadi.waterreminder.data

import java.time.LocalDate

data class Streaks(val current: Int, val best: Int)

/**
 * Consecutive days on which the daily goal was met, derived from history so
 * nothing extra has to be stored.
 *
 * Today only counts once its goal is actually met; an unfinished day must not
 * break a streak that is still alive from yesterday.
 */
fun computeStreaks(metrics: List<DailyMetrics>, goal: Int, today: LocalDate): Streaks {
    if (goal <= 0 || metrics.isEmpty()) return Streaks(0, 0)

    val metDates = metrics
        .filter { it.drinks >= goal }
        .map { it.date }
        .toHashSet()

    var best = 0
    for (date in metDates) {
        // Only count runs from their first day so each run is measured once.
        if (metDates.contains(date.minusDays(1))) continue
        var length = 0
        var cursor = date
        while (metDates.contains(cursor)) {
            length++
            cursor = cursor.plusDays(1)
        }
        if (length > best) best = length
    }

    var current = 0
    var cursor = if (metDates.contains(today)) today else today.minusDays(1)
    while (metDates.contains(cursor)) {
        current++
        cursor = cursor.minusDays(1)
    }

    return Streaks(current = current, best = maxOf(best, current))
}
