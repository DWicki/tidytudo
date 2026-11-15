package io.github.dwicki.tidytudo.ui

import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.ui.schedules.TimerDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

class TimerDraftTest {
    private val zurich = ZoneId.of("Europe/Zurich")

    private val robotTimer = ValetudoTimer(
        id = "t1",
        enabled = false,
        dow = listOf(2),
        hour = 7,
        minute = 30,
        action = ValetudoTimer.Action(ValetudoTimer.FULL_CLEANUP),
        preActions = listOf(ValetudoTimer.PreAction(ValetudoTimer.PRE_OPERATION_MODE, ValetudoTimer.PreActionParams("vacuum_then_mop"))),
    )

    @Test
    fun `robot timer opens in local time with its pre-actions`() {
        val draft = TimerDraft.from(robotTimer, zurich)
        assertEquals(LocalTime.of(8, 30), draft.time)
        assertEquals(setOf(DayOfWeek.TUESDAY), draft.days)
        assertEquals(mapOf(ValetudoTimer.PRE_OPERATION_MODE to "vacuum_then_mop"), draft.preActions)
    }

    @Test
    fun `saving keeps id and enabled state and converts back to utc`() {
        val saved = TimerDraft.from(robotTimer, zurich).applyTo(robotTimer, zurich)
        assertEquals("t1", saved.id)
        assertEquals(false, saved.enabled)
        assertEquals(listOf(2), saved.dow)
        assertEquals(7, saved.hour)
        assertEquals(30, saved.minute)
        assertNull(saved.label)
    }

    @Test
    fun `rooms turn a full cleanup into a room cleanup`() {
        val draft = TimerDraft.from(robotTimer, zurich).copy(rooms = listOf("4", "6"), label = "Kitchen")
        val saved = draft.applyTo(robotTimer, zurich)
        assertEquals(ValetudoTimer.SEGMENT_CLEANUP, saved.action.type)
        assertEquals(listOf("4", "6"), saved.action.params.segmentIds)
        assertEquals("Kitchen", saved.label)
    }

    @Test
    fun `a new timer starts at nine on every day`() {
        val draft = TimerDraft.from(ValetudoTimer(), zurich)
        assertEquals(LocalTime.of(9, 0), draft.time)
        assertEquals(DayOfWeek.entries.toSet(), draft.days)
    }
}
