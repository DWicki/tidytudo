package io.github.dwicki.tidytudo.data.history

import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.Connection
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Fills the cleaning history (F-8.1 – F-8.3) in two ways:
 *  - **afterwards**: the robot keeps statistics, map with path and obstacle photos of its latest
 *    run until the next one starts. Whenever the app (re)connects to a robot that is not cleaning,
 *    it stores that run if it is not in the history yet. The robot does not know when the run
 *    happened, so such entries have no exact time;
 *  - **live**: a run the app sees from start to end additionally gets exact times and errors.
 *
 * Both paths end in [capture], and the run's fingerprint keeps them from storing a run twice.
 * The recorder only watches [RobotSession.observedState] and never opens connections on its own.
 */
class SessionRecorder(
    private val robots: RobotStore,
    private val history: HistoryStore,
    private val sessionFor: (Robot) -> RobotSession,
    private val scope: CoroutineScope,
) {
    /** Runs seen starting, by robot id; kept outside the collectors so a robot list change does not lose them. */
    private val running = mutableMapOf<String, LiveRun>()

    /** Sessions whose photos are being downloaded right now. */
    private val fetching = mutableSetOf<String>()

    /** Robots already checked for an unrecorded run since their connection came up. */
    private val checkedSinceConnect = mutableSetOf<String>()

    private data class LiveRun(val startedAt: Long, val kind: String, val roomNames: List<String>, val errors: List<String>)

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
            if (live.connection is Connection.Offline) synchronized(checkedSinceConnect) { checkedSinceConnect -= robot.id }
            val state = live.state ?: return@collect
            if (live.connection == Connection.Online) onState(robot, session, state)
        }
    }

    private suspend fun onState(robot: Robot, session: RobotSession, state: RobotState) {
        val status = state.status.value
        val run = synchronized(running) { running[robot.id] }
        val atRest = status == StatusValue.DOCKED || status == StatusValue.IDLE
        when {
            run == null && status == StatusValue.CLEANING -> begin(robot, session, state)
            run != null && status == StatusValue.ERROR -> addError(robot.id, run, state)
            run != null && atRest -> {
                synchronized(running) { running.remove(robot.id) }
                capture(robot, session, run, status)
            }
            run == null && atRest && markChecked(robot.id) -> capture(robot, session, live = null, endStatus = status)
        }
    }

    /** True the first time per connection, so the robot's last run is looked up once, not on every update. */
    private fun markChecked(robotId: String): Boolean = synchronized(checkedSinceConnect) { checkedSinceConnect.add(robotId) }

    private suspend fun begin(robot: Robot, session: RobotSession, state: RobotState) {
        synchronized(running) { running[robot.id] = LiveRun(System.currentTimeMillis(), state.status.flag, emptyList(), emptyList()) }
        // Selected rooms are marked active on the map while the job runs; read them once at the start.
        val rooms = session.client.map().getOrNull()?.segments?.filter { it.active }?.mapNotNull { it.name }.orEmpty()
        synchronized(running) { running[robot.id]?.let { running[robot.id] = it.copy(roomNames = rooms) } }
    }

    private fun addError(robotId: String, run: LiveRun, state: RobotState) {
        val message = state.status.error?.message?.ifBlank { null } ?: state.status.error?.subsystem ?: return
        if (message !in run.errors) synchronized(running) { running[robotId] = run.copy(errors = run.errors + message) }
    }

    /** Stores the robot's latest run unless it is already in the history or there is no run to store. */
    private suspend fun capture(robot: Robot, session: RobotSession, live: LiveRun?, endStatus: StatusValue) {
        val client = session.client
        val mapJson = client.mapJson().getOrNull() ?: return
        val map = runCatching { MapParser.parse(mapJson) }.getOrNull() ?: return
        if (map.paths.isEmpty()) return
        val fingerprint = fingerprintOf(map)
        val known = history.sessions.value.firstOrNull { it.robotId == robot.id && it.fingerprint == fingerprint }
        if (known != null) {
            // Same run as before: only fetch photos a previous attempt could not get (rate limit, lost connection).
            launchImageFetch(session, known)
            return
        }

        val statistics = client.currentStatistics().getOrNull().orEmpty()
        val duration = statistics.firstOrNull { it.type == "time" }?.value
        val now = System.currentTimeMillis()
        val cleaning = CleaningSession(
            id = UUID.randomUUID().toString(),
            robotId = robot.id,
            startedAt = live?.startedAt ?: (now - (duration ?: 0) * MILLIS_PER_SECOND),
            endedAt = now,
            timeExact = live != null,
            fingerprint = fingerprint,
            durationSeconds = duration,
            areaCm2 = statistics.firstOrNull { it.type == "area" }?.value,
            kind = live?.kind ?: "none",
            roomNames = live?.roomNames?.ifEmpty { null } ?: map.visitedSegments().mapNotNull { it.name },
            result = resultOf(live, endStatus),
            errors = live?.errors.orEmpty(),
            obstacles = obstaclesOf(map),
            hasMap = true,
        )
        // Store first so the run shows up at once; photos follow, paced to the robot's rate limit.
        history.add(cleaning, mapJson.toString(), images = emptyMap())
        AppLog.d(TAG, "Stored run (live=${live != null}): ${cleaning.obstacles.size} obstacles")
        launchImageFetch(session, cleaning)
    }

    /** Runs in the background so status changes are still processed while photos download. */
    private fun launchImageFetch(session: RobotSession, stored: CleaningSession) {
        if (!synchronized(fetching) { fetching.add(stored.id) }) return
        scope.launch {
            try {
                fetchMissingImages(session, stored)
            } finally {
                synchronized(fetching) { fetching.remove(stored.id) }
            }
        }
    }

    private fun resultOf(live: LiveRun?, endStatus: StatusValue) = when {
        live?.errors?.isNotEmpty() == true -> CleaningSession.Result.ERROR
        endStatus == StatusValue.IDLE -> CleaningSession.Result.STOPPED
        else -> CleaningSession.Result.FINISHED
    }

    private fun obstaclesOf(map: RobotMap): List<SessionObstacle> =
        map.obstacles.mapNotNull { obstacle ->
            val id = obstacle.id ?: return@mapNotNull null
            SessionObstacle(id, obstacle.label.orEmpty(), obstacle.position.x, obstacle.position.y)
        }

    /**
     * Downloads photos one by one. Valetudo allows 3 per second, 10 per 5 s and 30 per 20 s per
     * client (`ObstacleImagesCapabilityRouter`), so one request every [IMAGE_INTERVAL_MS] stays
     * below all three; a 429 still makes it wait and retry. Photos are about 65 kB each, and the
     * cap keeps a run with many detections from filling the phone.
     */
    private suspend fun fetchMissingImages(session: RobotSession, stored: CleaningSession) {
        val client = session.client
        if (Capabilities.OBSTACLE_IMAGES !in client.capabilities().getOrNull().orEmpty()) return
        val missing = stored.obstacles.take(MAX_IMAGES_PER_SESSION).filterNot { it.hasImage }
        for (obstacle in missing) {
            val bytes = downloadWithRetry(session, obstacle.id) ?: continue
            history.attachImage(stored.id, obstacle.id, bytes)
            delay(IMAGE_INTERVAL_MS)
        }
    }

    private suspend fun downloadWithRetry(session: RobotSession, obstacleId: String): ByteArray? {
        repeat(MAX_ATTEMPTS) {
            when (val result = session.client.obstacleImage(obstacleId)) {
                is ApiResult.Success -> return result.value
                is ApiResult.Failure -> {
                    val rateLimited = (result.error as? ApiError.Http)?.code == HTTP_TOO_MANY_REQUESTS
                    if (!rateLimited) return null
                    delay(RATE_LIMIT_PAUSE_MS)
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SessionRecorder"
        private const val MAX_IMAGES_PER_SESSION = 60
        private const val IMAGE_INTERVAL_MS = 700L
        private const val RATE_LIMIT_PAUSE_MS = 20_000L
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1000L

        /**
         * The driven path and the obstacle ids differ for every run and stay unchanged while the
         * robot rests, unlike statistics, which some robots update only after docking.
         */
        fun fingerprintOf(map: RobotMap): String {
            val points = map.paths.sumOf { it.size }
            val last = map.paths.lastOrNull()?.lastOrNull()
            val obstacles = map.obstacles.mapNotNull { it.id }.sorted().hashCode()
            return "$points:${last?.x}:${last?.y}:$obstacles"
        }
    }
}
