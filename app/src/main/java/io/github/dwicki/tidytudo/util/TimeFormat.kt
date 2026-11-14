package io.github.dwicki.tidytudo.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** A local wall-clock time on a set of weekdays (ISO numbering, Monday = 1). */
data class LocalSchedule(val days: Set<DayOfWeek>, val time: LocalTime)

/**
 * Valetudo stores timers and do-not-disturb windows in UTC with Sunday = 0 (docs/valetudo-api.md).
 * These helpers translate to the phone's zone; a time near midnight may move to a neighbouring day.
 */
object TimeFormat {

    fun toLocal(utcDays: List<Int>, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): LocalSchedule {
        val localDays = utcDays.map { valetudoDay ->
            utcDateTime(valetudoDay, hour, minute, today).withZoneSameInstant(zone).dayOfWeek
        }.toSet()
        val localTime = utcDateTime(0, hour, minute, today).withZoneSameInstant(zone).toLocalTime()
        return LocalSchedule(localDays, localTime)
    }

    /** Returns (valetudo days, UTC hour, UTC minute). */
    fun toUtc(schedule: LocalSchedule, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): Triple<List<Int>, Int, Int> {
        val utcTimes = schedule.days.map { day ->
            val date = today.with(TemporalAdjusters.nextOrSame(day))
            ZonedDateTime.of(date, schedule.time, zone).withZoneSameInstant(ZoneOffset.UTC)
        }
        val reference = utcTimes.firstOrNull() ?: ZonedDateTime.of(today, schedule.time, zone).withZoneSameInstant(ZoneOffset.UTC)
        val days = utcTimes.map { it.dayOfWeek.value % 7 }.distinct().sorted()
        return Triple(days, reference.hour, reference.minute)
    }

    /** Converts a UTC hour/minute (DND window) to local time today. */
    fun utcTimeToLocal(hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        ZonedDateTime.of(LocalDate.now(), LocalTime.of(hour, minute), ZoneOffset.UTC).withZoneSameInstant(zone).toLocalTime()

    fun localTimeToUtc(time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        ZonedDateTime.of(LocalDate.now(), time, zone).withZoneSameInstant(ZoneOffset.UTC).toLocalTime()

    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern))
    }

    /** "5 minutes ago" style for ISO timestamps from the robot; the raw text if it cannot be parsed. */
    fun timestamp(context: Context, iso: String): String = runCatching {
        val millis = Instant.parse(iso).toEpochMilli()
        DateUtils.getRelativeDateTimeString(context, millis, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
    }.getOrDefault(iso)

    /** Valetudo's day numbering (Sunday = 0) to a date in the week of [today], at UTC. */
    private fun utcDateTime(valetudoDay: Int, hour: Int, minute: Int, today: LocalDate): ZonedDateTime {
        val dayOfWeek = DayOfWeek.of(if (valetudoDay == 0) 7 else valetudoDay)
        val date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek))
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ZoneOffset.UTC)
    }
}
