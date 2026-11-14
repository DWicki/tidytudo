package io.github.dwicki.tidytudo.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimeFormatTest {
    private val zurich = ZoneId.of("Europe/Zurich")
    private val winterDay = LocalDate.of(2026, 11, 16)

    @Test
    fun `utc timer is shown in local time`() {
        // Valetudo day 2 = Tuesday; 07:30 UTC is 08:30 in Zurich in winter.
        val local = TimeFormat.toLocal(listOf(2), 7, 30, zurich, winterDay)
        assertEquals(setOf(DayOfWeek.TUESDAY), local.days)
        assertEquals(LocalTime.of(8, 30), local.time)
    }

    @Test
    fun `local time shortly after midnight moves to the previous utc day`() {
        val (days, hour, minute) = TimeFormat.toUtc(LocalSchedule(setOf(DayOfWeek.MONDAY), LocalTime.of(0, 30)), zurich, winterDay)
        assertEquals(listOf(0), days) // Sunday in Valetudo numbering
        assertEquals(23, hour)
        assertEquals(30, minute)
    }

    @Test
    fun `round trip keeps days and time`() {
        val schedule = LocalSchedule(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), LocalTime.of(9, 15))
        val (days, hour, minute) = TimeFormat.toUtc(schedule, zurich, winterDay)
        assertEquals(schedule, TimeFormat.toLocal(days, hour, minute, zurich, winterDay))
    }
}
