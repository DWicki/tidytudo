package io.github.dwicki.tidytudo.data.history

import kotlinx.serialization.Serializable

/**
 * One cleaning run (F-8). Valetudo keeps no history, only the data of its latest run, so the
 * app stores each run it sees: live while it happens, or afterwards from what the robot still holds.
 */
@Serializable
data class CleaningSession(
    val id: String,
    val robotId: String,
    /** Wall-clock millis. For runs found afterwards ([timeExact] false), [endedAt] is when the app found it. */
    val startedAt: Long,
    val endedAt: Long,
    /** False for runs found afterwards: the robot does not store when a run happened. */
    val timeExact: Boolean = true,
    /** Identifies the robot's run (driven path and obstacles), so the same run is never stored twice. */
    val fingerprint: String = "",
    /** From `CurrentStatisticsCapability` at the end of the run; null if the robot does not report it. */
    val durationSeconds: Long? = null,
    val areaCm2: Long? = null,
    /** Valetudo's status flag at the start: `none` (full), `segment`, `zone`, `spot`, `target`. */
    val kind: String = "none",
    val roomNames: List<String> = emptyList(),
    val result: Result = Result.FINISHED,
    /** Error messages seen during the run, in order, without repeats. */
    val errors: List<String> = emptyList(),
    val obstacles: List<SessionObstacle> = emptyList(),
    val hasMap: Boolean = false,
) {
    enum class Result { FINISHED, STOPPED, ERROR }

    /** Robot-reported duration if known, otherwise the time the app observed. */
    val effectiveDurationSeconds: Long get() = durationSeconds ?: ((endedAt - startedAt) / MILLIS_PER_SECOND)
}

// Top level, not in a companion: a private companion would hide the generated serializer().
private const val MILLIS_PER_SECOND = 1000L

@Serializable
data class SessionObstacle(
    val id: String,
    val label: String,
    val xCm: Float,
    val yCm: Float,
    /** True when the photo was downloaded and stored with the session. */
    val hasImage: Boolean = false,
)
