package com.grindbell.app.utils

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object DateTimeUtils {

    fun getStartOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun getEndOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun getCurrentTimeFormatted(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))

    fun getDateFormatted(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    fun millisToLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun localDateTimeToMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun getMinutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
        val fromMillis = localDateTimeToMillis(from)
        val toMillis = localDateTimeToMillis(to)
        return TimeUnit.MILLISECONDS.toMinutes(toMillis - fromMillis)
    }

    fun formatRelativeTime(dateTime: LocalDateTime): String {
        val now = LocalDateTime.now()
        val minutes = getMinutesBetween(dateTime, now)
        return when {
            minutes < 0 -> "in ${-minutes} min"
            minutes == 0L -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    fun getGreeting(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
}
