package io.github.dwicki.tidytudo.data.history

import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.map.CmPoint

/**
 * Decides when one robot's cleaning job is over (F-8.1). A job stays open while the robot works,
 * pauses, reports an error or stops at the dock for a moment (washing or fitting the mop, emptying),
 * while it charges in the middle of a job (reported as resumable), and while the app cannot see the
 * robot. It is over once the robot has rested (docked or idle) for the settle time, seen live the
 * whole time; the caller runs that timer and stores the job.
 *
 * Every rest period gets its own number, so a timer or a store that belongs to an earlier rest can
 * tell that the robot has moved since and must not act. Not thread-safe: the caller serialises access.
 */
internal class JobTracker(private val clock: () -> Long = System::currentTimeMillis) {

    /** What the app saw of a job; null when it found the robot at rest without seeing it work. */
    data class Job(
        val startedAt: Long,
        val kind: String,
        val roomNames: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        /** True while the latest non-resting status was an error, i.e. the robot did not recover. */
        val endedInError: Boolean = false,
    )

    /** A rest period: its number, when the app first saw it live, and the status it rests in. */
    data class Rest(val id: Long, val since: Long, val status: StatusValue)

    sealed interface Decision {
        /** Nothing changes. */
        data object None : Decision

        /** A job began (and any pending settle timer is void); the caller may add the chosen rooms. */
        data object Begin : Decision

        /** The robot works again or can no longer be seen: drop any pending settle timer. */
        data object CancelSettle : Decision

        /** The robot came to rest: store the job if this rest is still current after the settle time. */
        data class StartSettle(val rest: Rest) : Decision
    }

    var job: Job? = null
        private set

    /** The current rest period; it stays set after the job is stored, so further resting updates do nothing. */
    private var rest: Rest? = null
    private var nextRestId = 0L

    fun onState(state: RobotState): Decision {
        val status = state.status.value
        return when {
            status == StatusValue.CLEANING -> {
                rest = null
                val current = job
                if (current == null) {
                    job = Job(startedAt = clock(), kind = state.status.flag)
                    Decision.Begin
                } else {
                    job = current.copy(endedInError = false)
                    Decision.CancelSettle
                }
            }
            status == StatusValue.ERROR -> {
                rest = null
                job = job?.withError(state)
                Decision.CancelSettle
            }
            status == StatusValue.DOCKED || status == StatusValue.IDLE -> onRest(state, status)
            // Unknown says nothing about resting: wait for a status that does.
            status == StatusValue.UNKNOWN -> {
                rest = null
                Decision.CancelSettle
            }
            else -> {
                // Returning, paused, moving or driven by hand: the job (if any) goes on.
                rest = null
                job = job?.copy(endedInError = false)
                Decision.CancelSettle
            }
        }
    }

    /** The state is no longer live (offline, or reconnecting with an old state): resting must be seen afresh. */
    fun onConnectionLost(): Decision {
        rest = null
        return Decision.CancelSettle
    }

    /** Adds the rooms read at the start of the job. */
    fun setRooms(roomNames: List<String>) {
        job = job?.copy(roomNames = roomNames)
    }

    /** The rest with number [restId] with its latest status, or null if the robot moved or was lost since. */
    fun currentRest(restId: Long): Rest? = rest?.takeIf { it.id == restId }

    /**
     * The job of rest [restId] is stored: forget it. If the robot moved meanwhile, the job goes on
     * and is kept; its entry is updated when it ends.
     */
    fun stored(restId: Long) {
        if (currentRest(restId) != null) job = null
    }

    private fun onRest(state: RobotState, status: StatusValue): Decision {
        // The robot says it will carry on (e.g. charging mid-job): keep the job open, however long that takes.
        if (state.status.flag == FLAG_RESUMABLE) {
            rest = null
            return Decision.CancelSettle
        }
        val current = rest
        if (current != null) {
            // Idle → docked keeps the rest's start and number; only the status it ends in changes.
            if (current.status != status) rest = current.copy(status = status)
            return Decision.None
        }
        return Decision.StartSettle(Rest(nextRestId++, clock(), status).also { rest = it })
    }

    private fun Job.withError(state: RobotState): Job {
        val message = state.status.error?.message?.ifBlank { null } ?: state.status.error?.subsystem
        val errors = if (message == null || message in errors) errors else errors + message
        return copy(errors = errors, endedInError = true)
    }

    companion object {
        const val FLAG_RESUMABLE = "resumable"

        /** At least this many points must match before a stored path counts as the start of the current one. */
        private const val MIN_SHARED_POINTS = 10

        /** True if the robot is resting for good: docked or idle and not about to resume. */
        fun isResting(state: RobotState): Boolean =
            (state.status.value == StatusValue.DOCKED || state.status.value == StatusValue.IDLE) && state.status.flag != FLAG_RESUMABLE

        /**
         * True if [current] is the run [stored] carried on: it starts with the stored path. The
         * paths are compared point by point in the order the robot reports them, so how they are
         * split into polylines does not matter. The stored path's last point is left out because
         * the robot moves it while it drives, so an equally long path that differs only there is the
         * same run seen a moment later. A job that starts a new path (the mop phase of "vacuum, then
         * mop", or a new job) never continues the earlier one.
         */
        fun continues(stored: List<List<CmPoint>>, current: List<List<CmPoint>>): Boolean {
            val before = stored.flatten()
            val now = current.flatten()
            val shared = before.size - 1
            if (shared < MIN_SHARED_POINTS || now.size < before.size) return false
            return (0 until shared).all { before[it] == now[it] }
        }
    }
}
