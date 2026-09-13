package com.jaimatadi.waterreminder.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/** Clock formatting that follows the phone's 12/24-hour preference. */
object TimeFormat {
    private val twentyFour: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val twelve: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun is24Hour(context: Context): Boolean = DateFormat.is24HourFormat(context)

    fun formatter(context: Context): DateTimeFormatter =
        if (is24Hour(context)) twentyFour else twelve

    /** Formats a [java.time.LocalTime] or zoned date-time. */
    fun clock(context: Context, time: TemporalAccessor): String = formatter(context).format(time)

    fun clock(context: Context, instant: Instant): String =
        clock(context, instant.atZone(ZoneId.systemDefault()))
}
