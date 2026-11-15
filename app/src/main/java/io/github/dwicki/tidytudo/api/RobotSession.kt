package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.data.RoomSelection
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StateParser
import io.github.dwicki.tidytudo.map.MapParser
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the UI needs to know about the link to a robot. */
sealed interface Connection {
    data object Connecting : Connection
    data object Online : Connection

    /** [since] is wall-clock millis of the first failure, so the UI can say "offline for 2 min". */
    data class Offline(val error: ApiError, val since: Long) : Connection
}

data class LiveState(
    val connection: Connection = Connection.Connecting,
    val state: RobotState? = null,
    val capabilities: Set<String> = emptySet(),
)

/**
 * The live link to one robot: an attribute SSE stream while anyone observes [live], and a map
 * stream while anyone observes [map]. Both stop five seconds after the last observer leaves,
 * which keeps us within Valetudo's limit of five SSE clients per hub (Q-8).
 */
class RobotSession(val client: ValetudoClient, scope: CoroutineScope) {

    private val liveState = MutableStateFlow(LiveState())

    /** Rooms picked for the next cleaning, shared by the home screen and the map. */
    val roomSelection = MutableStateFlow(RoomSelection())

    val live: StateFlow<LiveState> = channelFlow {
        // The loop writes to liveState (so refresh() can too); this child forwards every value.
        // A state kept from an earlier connection may be hours old (the robot may have finished since):
        // mark it as not current until the robot answers again.
        liveState.update { if (it.connection == Connection.Online) it.copy(connection = Connection.Connecting) else it }
        launch { liveState.collect { send(it) } }
        runLiveLoop()
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveState())

    /**
     * The latest known state without keeping a connection open: collecting it does not start the
     * SSE stream. Used by observers such as the history recorder, which should record only what
     * other parts of the app are watching anyway.
     */
    val observedState: StateFlow<LiveState> = liveState.asStateFlow()

    val map: StateFlow<RobotMap?> = flow { runMapLoop() }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Runs a command; on success the next SSE update confirms the new state, so no manual refresh is needed. */
    suspend fun command(action: suspend ValetudoClient.() -> ApiResult<Unit>): ApiResult<Unit> = client.action()

    /** Re-reads the state immediately, e.g. after a command whose effect does not change any attribute. */
    suspend fun refresh() {
        client.state().getOrNull()?.let { state -> liveState.update { it.copy(state = state) } }
    }

    private suspend fun runLiveLoop() {
        var failureSince: Long? = null
        var attempt = 0
        while (true) {
            val error = streamAttributes(onConnected = { failureSince = null; attempt = 0 })
            val since = failureSince ?: System.currentTimeMillis().also { failureSince = it }
            liveState.update { it.copy(connection = Connection.Offline(error, since)) }
            delay(backoffMillis(attempt++, error))
        }
    }

    /** Returns the error that ended the stream. */
    private suspend fun streamAttributes(onConnected: () -> Unit): ApiError {
        val initial = client.state()
        if (initial is ApiResult.Failure) return initial.error
        loadCapabilitiesIfNeeded()
        liveState.update { it.copy(connection = Connection.Online, state = initial.getOrNull()) }
        onConnected()
        return try {
            client.sse.events(SseConnector.ATTRIBUTES)
                .filter { it.name == SseConnector.EVENT_ATTRIBUTES }
                .collect { event ->
                    val state = StateParser.parse(JsonCodec.json.parseToJsonElement(event.data))
                    liveState.update { it.copy(connection = Connection.Online, state = state) }
                }
            ApiError.Network(IllegalStateException("Stream ended"))
        } catch (failure: SseException) {
            failure.error
        }
    }

    private suspend fun loadCapabilitiesIfNeeded() {
        if (liveState.value.capabilities.isNotEmpty()) return
        client.capabilities().getOrNull()?.let { names -> liveState.update { it.copy(capabilities = names.toSet()) } }
    }

    private suspend fun FlowCollector<RobotMap?>.runMapLoop() {
        var attempt = 0
        while (true) {
            client.map().getOrNull()?.let { emit(it); attempt = 0 }
            val error = try {
                client.sse.events(SseConnector.MAP)
                    .filter { it.name == SseConnector.EVENT_MAP }
                    .collect { event -> parseMap(event.data)?.let { emit(it) } }
                null
            } catch (failure: SseException) {
                failure.error
            }
            delay(backoffMillis(attempt++, error))
        }
    }

    private fun parseMap(data: String): RobotMap? = runCatching {
        MapParser.parse(JsonCodec.json.parseToJsonElement(data))
    }.onFailure { AppLog.w(TAG, "Map update could not be parsed", it) }.getOrNull()

    /** Fast retries for flaky Wi-Fi, slow ones when user action is needed (credentials, certificate). */
    private fun backoffMillis(attempt: Int, error: ApiError?): Long = when (error) {
        ApiError.AuthRequired, ApiError.CleartextNotAllowed, is ApiError.UntrustedCertificate -> MAX_BACKOFF_MS
        else -> (MIN_BACKOFF_MS shl attempt.coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_MS)
    }

    private companion object {
        const val TAG = "RobotSession"
        const val STOP_TIMEOUT_MS = 5_000L
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}

