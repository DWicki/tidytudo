package io.github.dwicki.tidytudo.data.history

import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.Connection
import io.github.dwicki.tidytudo.api.JsonCodec
import io.github.dwicki.tidytudo.api.RobotSession
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.RobotStore
import io.github.dwicki.tidytudo.map.MapParser
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.Job as CoroutineJob

/**
 * Fills the cleaning history (F-8.1 – F-8.3). A job is stored once, when it is over: after the
 * robot has rested in the dock (or idle) for [SETTLE_MS], seen live the whole time ([JobTracker]).
 * Short stops at the dock during a job and lost connections therefore never end it.
 *
 *  - **Seen live**: a job the app sees start gets exact times, the chosen rooms and errors.
 *  - **Found afterwards**: the robot keeps the statistics, map with path and obstacle photos of its
 *    latest run until the next one starts. When the app finds the robot resting without having seen
 *    the job, it stores that run the same way, without exact times.
 *
 * The robot's data is read when the job is over, so the entry holds the last phase of a job: for
 * "vacuum, then mop" the robot starts a new path and new statistics for the mop phase, and the
 * vacuum phase is not kept. If an earlier entry turns out to be the start of the same job (the
 * robot carried on after the app had stored it), that entry is updated instead of adding another;
 * the run's fingerprint keeps the same run from being stored twice.
 *
 * The recorder only watches [RobotSession.observedState] and never opens a stream on its own.
 */
class SessionRecorder(
    private val robots: RobotStore,
    private val history: HistoryStore,
    private val sessionFor: (Robot) -> RobotSession,
    private val scope: CoroutineScope,
) {
    /** Per robot id; kept outside the collectors so a change to the robot list does not lose a running job. */
    private val trackers = mutableMapOf<String, JobTracker>()

    private val openJobsState = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Ids of robots with a job that is seen but not stored yet. Background monitoring keeps watching
     * such a robot, so the job is stored even when nothing else keeps the connection open.
     */
    val openJobs: StateFlow<Set<String>> = openJobsState.asStateFlow()

    fun start() {
        scope.launch {
            robots.robots.map { list -> list.map { it.id to it.baseUrl } }.distinctUntilChanged().collectLatest {
                coroutineScope {
                    robots.robots.value.forEach { robot -> launch { follow(robot) } }
                }
            }
        }
    }

    /**
     * Follows one robot until the robot list changes. Settle timers and photo downloads run inside
     * this call, so they stop with it; the tracker survives, and resting is seen afresh next time.
     */
    private suspend fun follow(robot: Robot): Unit = coroutineScope {
        val session = sessionFor(robot)
        val tracker = synchronized(trackers) { trackers.getOrPut(robot.id) { JobTracker() } }
        // One photo worker per robot, taking requests one after another, so the robot's rate limit
        // holds across all its entries and no entry's downloads are cut short.
        val photoRequests = MutableSharedFlow<String>(extraBufferCapacity = PHOTO_QUEUE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        launch { photoRequests.collect { sessionId -> fetchMissingImages(session, sessionId) } }
        val recording = Recording(robot, session, tracker, photoRequests)
        var settle: CoroutineJob? = null
        synchronized(tracker) { tracker.onConnectionLost() }

        session.observedState.collect { live ->
            val state = live.state
            val decision = synchronized(tracker) {
                // Only a live state counts: an old one kept from an earlier connection is marked Connecting.
                if (live.connection != Connection.Online || state == null) tracker.onConnectionLost() else tracker.onState(state)
            }
            recording.publishOpenJob()
            when (decision) {
                JobTracker.Decision.None -> Unit
                JobTracker.Decision.CancelSettle -> settle = settle?.let { it.cancel(); null }
                JobTracker.Decision.Begin -> {
                    settle = settle?.let { it.cancel(); null }
                    launch { recording.readRooms() }
                }
                is JobTracker.Decision.StartSettle -> {
                    settle?.cancel()
                    settle = launch { recording.settleThenStore(decision.rest.id) }
                }
            }
        }
    }

    /** One robot's recording; [tracker] is guarded by its own monitor. */
    private inner class Recording(
        val robot: Robot,
        val session: RobotSession,
        val tracker: JobTracker,
        val photoRequests: MutableSharedFlow<String>,
    ) {
        fun publishOpenJob() {
            val open = synchronized(tracker) { tracker.job != null }
            openJobsState.update { if (open) it + robot.id else it - robot.id }
        }

        /** Rooms picked for the job are marked active on the map while it runs; read them once at the start. */
        suspend fun readRooms() {
            val rooms = session.client.map().getOrNull()?.segments?.filter { it.active }?.mapNotNull { it.name }.orEmpty()
            synchronized(tracker) { tracker.setRooms(rooms) }
        }

        /**
         * Waits [SETTLE_MS], then stores the job if rest [restId] still holds. The timer is cancelled
         * as soon as an update shows the robot working again; it ends by itself only when stored.
         * A failed read or store is retried every [RETRY_MS] while the robot keeps resting: an
         * unchanged state sends no updates that could start a new attempt.
         */
        suspend fun settleThenStore(restId: Long) {
            var wait = SETTLE_MS
            while (true) {
                delay(wait)
                // Ask the robot itself: the last update may be stale if nobody streams its state.
                val fresh = session.client.state().getOrNull()
                if (fresh != null && !JobTracker.isResting(fresh)) {
                    // Working again although no update said so (yet): it must rest a full settle time again.
                    wait = SETTLE_MS
                    continue
                }
                wait = RETRY_MS
                if (fresh == null) continue
                val target = synchronized(tracker) { tracker.currentRest(restId)?.let { rest -> tracker.job to rest } } ?: return
                if (store(target.first, target.second)) {
                    synchronized(tracker) { tracker.stored(restId) }
                    publishOpenJob()
                    return
                }
            }
        }

        private suspend fun store(job: JobTracker.Job?, rest: JobTracker.Rest): Boolean = try {
            capture(job, rest)
        } catch (cancelled: CancellationException) {
            // The robot started again or the robot list changed: the job stays open.
            throw cancelled
        } catch (failure: Exception) {
            AppLog.w(TAG, "Storing the run failed", failure)
            false
        }

        /** True while rest [rest] still holds, checked right before writing. */
        private fun stillResting(rest: JobTracker.Rest) = synchronized(tracker) { tracker.currentRest(rest.id) != null }

        /**
         * Stores the robot's latest run. Returns true if it is in the history afterwards (stored now,
         * updated, or already there) or there is nothing to store, false if it should be tried again.
         */
        private suspend fun capture(job: JobTracker.Job?, rest: JobTracker.Rest): Boolean {
            val client = session.client
            val mapJson = client.mapJson().getOrNull() ?: return false
            val map = runCatching { MapParser.parse(mapJson) }.getOrNull() ?: return false
            // Nothing driven: no run to store (a new robot, or the robot discarded its last run).
            if (map.paths.isEmpty()) return true
            val fingerprint = fingerprintOf(map)
            val robotsSessions = history.sessions.value.filter { it.robotId == robot.id }
            robotsSessions.firstOrNull { it.fingerprint == fingerprint }?.let { known ->
                // Same run as before: only fetch photos a previous attempt could not get (rate limit, lost connection).
                photoRequests.tryEmit(known.id)
                return true
            }

            val statistics = client.currentStatistics().getOrNull().orEmpty()
            val duration = statistics.firstOrNull { it.type == "time" }?.value
            val earlier = robotsSessions.firstOrNull()?.takeIf { previous -> continuesStoredRun(previous, map) }
            val estimatedStart = rest.since - (duration ?: 0) * MILLIS_PER_SECOND
            // The earliest start wins: a continued entry may have seen the job begin before this phase.
            val start = listOfNotNull(
                job?.let { it.startedAt to true },
                earlier?.let { it.startedAt to it.timeExact },
            ).minByOrNull { it.first } ?: (estimatedStart to false)
            val cleaning = CleaningSession(
                id = earlier?.id.orEmpty(),
                robotId = robot.id,
                startedAt = start.first,
                endedAt = rest.since,
                timeExact = start.second,
                fingerprint = fingerprint,
                durationSeconds = duration,
                areaCm2 = statistics.firstOrNull { it.type == "area" }?.value,
                // The entry that saw the job start knows best what kind of job it was.
                kind = earlier?.kind ?: job?.kind ?: "none",
                // The rooms picked for the job (perhaps seen by the entry it continues); otherwise
                // where the whole driven path went.
                roomNames = job?.roomNames?.ifEmpty { null }
                    ?: earlier?.takeIf { it.kind == KIND_ROOMS }?.roomNames?.ifEmpty { null }
                    ?: map.visitedSegments().mapNotNull { it.name },
                result = resultOf(job, rest.status),
                errors = (earlier?.errors.orEmpty() + job?.errors.orEmpty()).distinct(),
                obstacles = obstaclesOf(map),
                hasMap = true,
            )
            // The robot may have started again while the map and statistics were read: then the job goes on.
            if (!stillResting(rest)) return false
            // Store first so the run shows up at once; photos follow, paced to the robot's rate limit.
            // If the earlier entry was deleted meanwhile, it is not brought back: the run becomes a new entry.
            val updated = earlier != null && history.replace(cleaning, mapJson.toString())
            val stored = if (updated) cleaning else cleaning.copy(id = UUID.randomUUID().toString()).also { history.add(it, mapJson.toString(), images = emptyMap()) }
            AppLog.d(TAG, "Stored run (live=${job != null}, continued=$updated): ${cleaning.obstacles.size} obstacles")
            photoRequests.tryEmit(stored.id)
            return true
        }

        /** True if [current] carries on the stored run [previous]: the robot resumed after the app had stored it. */
        private suspend fun continuesStoredRun(previous: CleaningSession, current: RobotMap): Boolean {
            val json = history.mapJson(previous.id) ?: return false
            val stored = runCatching { MapParser.parse(JsonCodec.json.parseToJsonElement(json)) }.getOrNull() ?: return false
            return JobTracker.continues(stored.paths, current.paths)
        }
    }

    private fun resultOf(job: JobTracker.Job?, endStatus: StatusValue) = when {
        job?.endedInError == true -> CleaningSession.Result.ERROR
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
     * cap keeps a run with many detections from filling the phone. The stored entry is read anew
     * for every photo, so obstacles added by an update of the entry are fetched in the same pass.
     */
    private suspend fun fetchMissingImages(session: RobotSession, sessionId: String) {
        val client = session.client
        if (Capabilities.OBSTACLE_IMAGES !in client.capabilities().getOrNull().orEmpty()) return
        val failed = mutableSetOf<String>()
        while (true) {
            val stored = history.sessions.value.firstOrNull { it.id == sessionId } ?: return
            val next = stored.obstacles.take(MAX_IMAGES_PER_SESSION).firstOrNull { !it.hasImage && it.id !in failed } ?: return
            val bytes = downloadWithRetry(session, next.id)
            val attached = bytes != null && try {
                history.attachImage(sessionId, next.id, bytes)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLog.w(TAG, "Saving a photo failed", failure)
                false
            }
            if (!attached) failed += next.id
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

        /**
         * How long the robot must rest before its job counts as over. Stops at the dock during a job
         * (mop washing, emptying) take seconds; a recharge in the middle of a job is reported as
         * resumable and keeps the job open regardless.
         */
        const val SETTLE_MS = 5 * 60 * 1000L

        /** Valetudo's status flag for a job limited to chosen rooms. */
        private const val KIND_ROOMS = "segment"

        /** Photo requests waiting for the robot's worker; more than a few means the same entries again. */
        private const val PHOTO_QUEUE = 8

        /** Pause before trying again when reading or storing a finished run failed. */
        private const val RETRY_MS = 60 * 1000L
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
