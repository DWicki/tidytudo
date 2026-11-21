package io.github.dwicki.tidytudo.data

import io.github.dwicki.tidytudo.api.model.RobotError
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.Status
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.data.history.JobTracker
import io.github.dwicki.tidytudo.data.history.JobTracker.Decision
import io.github.dwicki.tidytudo.map.CmPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobTrackerTest {
    private var now = 0L
    private val tracker = JobTracker { now }

    private fun state(status: StatusValue, flag: String = "none", error: String? = null) =
        RobotState(status = Status(status, flag, error?.let { RobotError(it) }))

    private fun see(status: StatusValue, flag: String = "none", error: String? = null): Decision {
        now += 1_000
        return tracker.onState(state(status, flag, error))
    }

    /**
     * The "vacuum, then mop" job from the bug report: short stops at the dock during both phases
     * used to end the run each time (7 entries). Now none of them settles, so nothing is stored
     * until the robot has rested after the last phase, and the job keeps its first start.
     */
    @Test
    fun `stops at the dock during a job never finish it`() {
        assertEquals(Decision.Begin, see(StatusValue.CLEANING))
        val started = tracker.job!!.startedAt
        repeat(6) {
            see(StatusValue.RETURNING)
            val decision = see(StatusValue.DOCKED)
            assertTrue(decision is Decision.StartSettle)
            // 14–16 s later, long before the settle time, it cleans again: the timer is dropped.
            now += 15_000
            assertEquals(Decision.CancelSettle, see(StatusValue.CLEANING))
        }
        assertEquals(started, tracker.job!!.startedAt)

        val rest = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        // Further resting updates neither restart nor drop the timer.
        assertEquals(Decision.None, see(StatusValue.DOCKED))
        assertEquals(rest, tracker.currentRest(rest.id))
        assertEquals(started, tracker.job!!.startedAt)
        tracker.stored(rest.id)
        assertNull(tracker.job)
        assertEquals(Decision.None, see(StatusValue.DOCKED))
    }

    @Test
    fun `a lost connection makes resting count only from the next live update`() {
        see(StatusValue.CLEANING)
        val first = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        assertEquals(Decision.CancelSettle, tracker.onConnectionLost())
        // The timer that was running belongs to a rest that no longer counts.
        assertNull(tracker.currentRest(first.id))
        val second = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        assertNotEquals(first.id, second.id)
        assertTrue(second.since > first.since)
        assertNotNull(tracker.job)
    }

    @Test
    fun `charging in the middle of a job keeps it open however long it takes`() {
        see(StatusValue.CLEANING)
        now += 3 * 60 * 60 * 1000L
        assertEquals(Decision.CancelSettle, see(StatusValue.DOCKED, flag = JobTracker.FLAG_RESUMABLE))
        assertEquals(Decision.CancelSettle, see(StatusValue.DOCKED, flag = JobTracker.FLAG_RESUMABLE))
        assertEquals(Decision.CancelSettle, see(StatusValue.CLEANING))
        assertFalse(JobTracker.isResting(state(StatusValue.DOCKED, flag = JobTracker.FLAG_RESUMABLE)))
        assertTrue(JobTracker.isResting(state(StatusValue.IDLE)))
    }

    @Test
    fun `idle then docked keeps the rest's start and ends as docked`() {
        see(StatusValue.CLEANING)
        val rest = (see(StatusValue.IDLE) as Decision.StartSettle).rest
        assertEquals(Decision.None, see(StatusValue.DOCKED))
        val current = tracker.currentRest(rest.id)
        assertEquals(rest.since, current?.since)
        assertEquals(StatusValue.DOCKED, current?.status)
    }

    @Test
    fun `a job the robot carried on while it was being stored stays open`() {
        see(StatusValue.CLEANING)
        val rest = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        see(StatusValue.CLEANING)
        tracker.stored(rest.id)
        assertNotNull(tracker.job)
    }

    @Test
    fun `an unknown status does not count as resting`() {
        val rest = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        assertEquals(Decision.CancelSettle, see(StatusValue.UNKNOWN))
        assertNull(tracker.currentRest(rest.id))
    }

    @Test
    fun `errors are kept once each, and a recovered job does not end in error`() {
        see(StatusValue.CLEANING)
        see(StatusValue.ERROR, error = "Wheel stuck")
        see(StatusValue.ERROR, error = "Wheel stuck")
        assertTrue(tracker.job!!.endedInError)
        see(StatusValue.CLEANING)
        assertEquals(listOf("Wheel stuck"), tracker.job!!.errors)
        assertFalse(tracker.job!!.endedInError)
    }

    @Test
    fun `a robot found resting settles without a job`() {
        val rest = (see(StatusValue.DOCKED) as Decision.StartSettle).rest
        assertNull(tracker.job)
        assertEquals(rest, tracker.currentRest(rest.id))
    }

    private fun path(vararg points: Int) = points.toList().chunked(2).map { (x, y) -> CmPoint(x.toFloat(), y.toFloat()) }

    private fun line(from: Int, count: Int) = path(*IntArray(count * 2) { if (it % 2 == 0) from + it / 2 else 7 })

    @Test
    fun `a path that carries on the stored one continues it, however it is split into lines`() {
        val stored = listOf(line(0, 8), line(8, 6))
        // Same points split differently, the live last point moved, and more driven since.
        val current = listOf(line(0, 5), line(5, 8) + path(99, 99), line(20, 30))
        assertTrue(JobTracker.continues(stored, current))
    }

    @Test
    fun `a new path, a shorter one or a tiny stored one does not continue`() {
        val stored = listOf(line(0, 20))
        assertFalse("mop phase starts a new path", JobTracker.continues(stored, listOf(line(500, 40))))
        assertFalse("shorter", JobTracker.continues(stored, listOf(line(0, 10))))
        assertFalse("too little to tell", JobTracker.continues(listOf(line(0, 3)), listOf(line(0, 30))))
    }
}
