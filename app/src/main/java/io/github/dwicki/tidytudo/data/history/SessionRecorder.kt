package io.github.dwicki.tidytudo.data.history

import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.RobotSession
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.RobotStore
import io.github.dwicki.tidytudo.map.MapParser
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Turns observed status changes into [CleaningSession]s (F-8.1 – F-8.3).
 *
 * A run starts when a robot begins cleaning and ends when it is docked or idle again. At the end
 * the recorder fetches the run's statistics, the map with the driven path and the photos of the
 * obstacles on it. It only watches [RobotSession.observedState], so it records what the app or
 * the monitoring service is following anyway and never opens extra connections on its own.
 */
class SessionRecorder(
    private val robots: RobotStore,
    private val history: HistoryStore,
    private val sessionFor: (Robot) -> RobotSession,
    private val scope: CoroutineScope,
) {
    /** In-progress runs by robot id; kept outside the collectors so a robot list change does not lose them. */
    private val running = mutableMapOf<String, RunningSession>()

    private data class RunningSession(
        val startedAt: Long,
        val kind: String,
        val roomNames: List<String>,
        val errors: List<String>,
    )

    fun start() {
        scope.launch {
            robots.robots.map { list -> list.map { it.id to it.baseUrl } }.distinctUntilChanged().collectLatest {
                coroutineScope {
                    robots.robots.value.forEach { robot -> launch { follow(robot) } }
                }
            }
        }
    }

    private suspend fun follow(robot: Robot) {
        val session = sessionFor(robot)
        session.observedState.collect { live ->
            val state = live.state ?: return@collect
            onState(robot, session, state)
        }
    }

    private suspend fun onState(robot: Robot, session: RobotSession, state: RobotState) {
        val status = state.status.value
        val current = synchronized(running) { running[robot.id] }
        when {
            current == null && status == StatusValue.CLEANING -> begin(robot, session, state)
            current != null && status == StatusValue.ERROR -> addError(robot.id, current, state)
            current != null && (status == StatusValue.DOCKED || status == StatusValue.IDLE) -> finish(robot, session, current, status)
        }
    }

    private suspend fun begin(robot: Robot, session: RobotSession, state: RobotState) {
        val start = RunningSession(System.currentTimeMillis(), state.status.flag, roomNames = emptyList(), errors = emptyList())
        synchronized(running) { running[robot.id] = start }
        // Selected rooms are marked active on the map while the job runs; read them once at the start.
        val rooms = session.client.map().getOrNull()?.segments?.filter { it.active }?.mapNotNull { it.name }.orEmpty()
        synchronized(running) { running[robot.id]?.let { running[robot.id] = it.copy(roomNames = rooms) } }
    }

    private fun addError(robotId: String, current: RunningSession, state: RobotState) {
        val message = state.status.error?.message?.ifBlank { null } ?: state.status.error?.subsystem ?: return
        if (message !in current.errors) synchronized(running) { running[robotId] = current.copy(errors = current.errors + message) }
    }

    private suspend fun finish(robot: Robot, session: RobotSession, run: RunningSession, endStatus: StatusValue) {
        synchronized(running) { running.remove(robot.id) }
        val client = session.client
        val statistics = client.currentStatistics().getOrNull().orEmpty()
        val mapJson = client.mapJson().getOrNull()
        val map = mapJson?.let { runCatching { MapParser.parse(it) }.getOrNull() }
        val canFetchImages = Capabilities.OBSTACLE_IMAGES in client.capabilities().getOrNull().orEmpty()
        val images = if (canFetchImages && map != null) downloadImages(session, map) else emptyMap()
        val result = when {
            run.errors.isNotEmpty() -> CleaningSession.Result.ERROR
            endStatus == StatusValue.IDLE -> CleaningSession.Result.STOPPED
            else -> CleaningSession.Result.FINISHED
        }
        val cleaning = CleaningSession(
            id = UUID.randomUUID().toString(),
            robotId = robot.id,
            startedAt = run.startedAt,
            endedAt = System.currentTimeMillis(),
            durationSeconds = statistics.firstOrNull { it.type == "time" }?.value,
            areaCm2 = statistics.firstOrNull { it.type == "area" }?.value,
            kind = run.kind,
            roomNames = run.roomNames,
            result = result,
            errors = run.errors,
            obstacles = map?.obstacles.orEmpty().filter { it.id != null }.map { obstacle ->
                SessionObstacle(
                    id = obstacle.id!!,
                    label = obstacle.label.orEmpty(),
                    xCm = obstacle.position.x,
                    yCm = obstacle.position.y,
                    hasImage = obstacle.id in images,
                )
            },
            hasMap = mapJson != null,
        )
        history.add(cleaning, mapJson?.toString(), images)
        AppLog.d(TAG, "Recorded session: ${cleaning.result}, ${cleaning.obstacles.size} obstacles")
    }

    /** Photos are about 50 kB each; the cap keeps a run with many detections from filling the phone. */
    private suspend fun downloadImages(session: RobotSession, map: RobotMap): Map<String, ByteArray> =
        map.obstacles.mapNotNull { it.id }.take(MAX_IMAGES_PER_SESSION).mapNotNull { id ->
            session.client.obstacleImage(id).getOrNull()?.let { id to it }
        }.toMap()

    private companion object {
        const val TAG = "SessionRecorder"
        const val MAX_IMAGES_PER_SESSION = 60
    }
}
