package io.github.dwicki.tidytudo.ui.home

import io.github.dwicki.tidytudo.data.RoomSelection
import io.github.dwicki.tidytudo.api.Connection
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.dwicki.tidytudo.R
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.SegmentCleaningProperties
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.service.QuickActions
import io.github.dwicki.tidytudo.shortcuts.RoomShortcut
import io.github.dwicki.tidytudo.ui.CommandViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Data the home screen loads once per visit, on top of the live state. */
data class HomeExtras(
    /** Preset choices per capability (fan speed, water usage, operation mode). */
    val presetOptions: Map<String, List<String>> = emptyMap(),
    val segments: List<MapSegment> = emptyList(),
    val segmentProperties: SegmentCleaningProperties = SegmentCleaningProperties(),
    val openEvents: List<RobotEvent> = emptyList(),
    val pendingMapChange: Boolean = false,
)

/** Time and area of the running job, from `CurrentStatisticsCapability`. */
data class JobProgress(val seconds: Long?, val areaCm2: Long?)


class HomeViewModel(private val container: AppContainer, private val robot: Robot) : CommandViewModel() {

    private val session = container.session(robot)
    val live: StateFlow<LiveState> = session.live

    private val extrasState = MutableStateFlow(HomeExtras())
    val extras: StateFlow<HomeExtras> = extrasState.asStateFlow()

    private val selectionState = session.roomSelection
    val selection: StateFlow<RoomSelection> = selectionState.asStateFlow()

    /** Name of the action whose request is in flight, to show a spinner on exactly that button. */
    private val busyState = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = busyState.asStateFlow()

    /** Polled while the robot works and the home screen is visible; null otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<JobProgress?> = live
        .map { Capabilities.CURRENT_STATISTICS in it.capabilities && it.connection == Connection.Online && QuickActions.isCleaning(it.state) }
        .distinctUntilChanged()
        .flatMapLatest { working -> if (working) pollProgress() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun pollProgress() = flow {
        while (true) {
            session.client.currentStatistics().getOrNull()?.let { points ->
                emit(JobProgress(points.firstOrNull { it.type == "time" }?.value, points.firstOrNull { it.type == "area" }?.value))
            }
            delay(PROGRESS_POLL_MS)
        }
    }

    private val presetsState = MutableStateFlow(robot.presets)
    val presets: StateFlow<List<CleaningPreset>> = presetsState.asStateFlow()

    init {
        viewModelScope.launch {
            val capabilities = live.first { it.capabilities.isNotEmpty() }.capabilities
            loadExtras(capabilities)
        }
        viewModelScope.launch {
            container.robotStore.robots.collect { robots ->
                robots.firstOrNull { it.id == robot.id }?.let { presetsState.value = it.presets }
            }
        }
    }

    fun refreshExtras() {
        val capabilities = live.value.capabilities
        if (capabilities.isNotEmpty()) viewModelScope.launch { loadExtras(capabilities) }
    }

    private suspend fun loadExtras(capabilities: Set<String>) {
        val client = session.client
        val options = listOf(Capabilities.FAN_SPEED, Capabilities.WATER_USAGE, Capabilities.OPERATION_MODE)
            .filter { it in capabilities }
            .associateWith { client.presets(it).getOrNull().orEmpty() }
        val segments = if (Capabilities.SEGMENT_CLEANING in capabilities) client.segments().getOrNull().orEmpty() else emptyList()
        val properties = if (Capabilities.SEGMENT_CLEANING in capabilities) {
            client.segmentProperties().getOrNull() ?: SegmentCleaningProperties()
        } else {
            SegmentCleaningProperties()
        }
        val events = client.events().getOrNull().orEmpty().filterNot { it.processed }
        val pending = Capabilities.PENDING_MAP_CHANGE in capabilities && client.pendingMapChange().getOrNull()?.pending == true
        extrasState.value = HomeExtras(options, segments.sortedBy { it.name ?: it.id }, properties, events, pending)
        container.publishShortcuts(robot, segments.map { RoomShortcut(it.id, it.name ?: it.id) })
    }

    fun toggle() = run(ACTION_TOGGLE, startsJob = true) { QuickActions.toggle(session.client, live.value.state) }
    fun stop() = run(ACTION_STOP) { session.client.basicControl("stop") }
    fun dock() = run(ACTION_DOCK, startsJob = true) { QuickActions.dock(session.client) }
    /** One-off confirmations for commands whose effect cannot be seen in the app, e.g. locate. */
    private val noticeChannel = Channel<Int>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()

    fun locate() = run(ACTION_LOCATE, onSuccess = { noticeChannel.trySend(R.string.locate_sent) }) { session.client.locate() }
    fun triggerDock(capability: String) = run(capability) { session.client.trigger(capability) }

    fun setPreset(capability: String, value: String) = run(capability) { session.client.setPreset(capability, value) }

    private val roomsHintState = MutableStateFlow(!container.settings.roomsHintDone)

    /** The hint under the rooms explains tapping and long-pressing until the user has done it once. */
    val roomsHint: StateFlow<Boolean> = roomsHintState.asStateFlow()

    private fun roomsHintDone() {
        if (!roomsHintState.value) return
        roomsHintState.value = false
        container.settings.markRoomsHintDone()
    }

    fun toggleRoom(id: String) {
        roomsHintDone()
        selectionState.update { it.toggle(id) }
    }

    fun setIterations(iterations: Int) = selectionState.update { it.copy(iterations = iterations) }

    fun clearSelection() = selectionState.update { it.cleared() }

    fun cleanSelectedRooms() {
        val selection = selectionState.value
        if (selection.ids.isEmpty()) return
        val customOrder = extrasState.value.segmentProperties.customOrderSupport
        run(ACTION_CLEAN_ROOMS, startsJob = true, onSuccess = ::clearSelection) {
            session.client.cleanSegments(selection.ids, selection.iterations, customOrder)
        }
    }

    fun runPreset(preset: CleaningPreset) = run(preset.id, startsJob = true) { QuickActions.runPreset(session, preset) }

    /** Saves the current room selection and modes as a named preset (F-4.6). */
    fun savePreset(name: String) {
        val state = live.value.state
        val preset = CleaningPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            segmentIds = selectionState.value.ids,
            iterations = selectionState.value.iterations,
            fanSpeed = state?.fanSpeed,
            waterUsage = state?.waterUsage,
            operationMode = state?.operationMode,
        )
        viewModelScope.launch {
            container.robotStore.update(robot.id) { it.copy(presets = it.presets + preset) }
            clearSelection()
        }
    }

    fun renamePreset(preset: CleaningPreset, name: String) = updateStoredPreset(preset.id) { it.copy(name = name.trim()) }

    /** Overwrites a preset with the rooms selected now (or full cleanup if none) and the current modes. */
    fun updatePreset(preset: CleaningPreset) {
        val state = live.value.state
        val selection = selectionState.value
        updateStoredPreset(preset.id) {
            it.copy(
                segmentIds = selection.ids,
                iterations = selection.iterations,
                fanSpeed = state?.fanSpeed,
                waterUsage = state?.waterUsage,
                operationMode = state?.operationMode,
            )
        }
        clearSelection()
    }

    private fun updateStoredPreset(id: String, change: (CleaningPreset) -> CleaningPreset) {
        viewModelScope.launch {
            container.robotStore.update(robot.id) { stored ->
                stored.copy(presets = stored.presets.map { if (it.id == id) change(it) else it })
            }
        }
    }

    fun deletePreset(preset: CleaningPreset) {
        viewModelScope.launch {
            container.robotStore.update(robot.id) { stored -> stored.copy(presets = stored.presets.filterNot { it.id == preset.id }) }
        }
    }

    /**
     * Renames a room. The robot applies the name a moment after confirming, so reloading at once
     * would bring back the old name; the chip takes the new name directly instead.
     */
    fun renameRoom(id: String, name: String) = run(ACTION_RENAME, onSuccess = {
        roomsHintDone()
        extrasState.update { extras ->
            val renamed = extras.segments.map { if (it.id == id) it.copy(name = name) else it }
            extras.copy(segments = renamed.sortedBy { it.name ?: it.id })
        }
        container.publishShortcuts(robot, extrasState.value.segments.map { RoomShortcut(it.id, it.name ?: it.id) })
    }) {
        session.client.renameSegment(id, name)
    }

    fun interact(event: RobotEvent, interaction: String) = run(event.id, onSuccess = ::refreshExtras) {
        session.client.interact(event.id, interaction)
    }

    fun answerMapChange(accept: Boolean) = run(ACTION_MAP_CHANGE, onSuccess = ::refreshExtras) {
        session.client.answerPendingMapChange(accept)
    }

    private fun run(
        action: String,
        startsJob: Boolean = false,
        onSuccess: () -> Unit = {},
        block: suspend () -> ApiResult<Unit>,
    ) {
        busyState.value = action
        launchCommand(
            block = {
                block().also { busyState.update { current -> if (current == action) null else current } }
            },
            onSuccess = {
                if (startsJob) container.followJob(robot)
                onSuccess()
            },
        )
    }

    companion object {
        private const val PROGRESS_POLL_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_STOP = "stop"
        const val ACTION_DOCK = "dock"
        const val ACTION_LOCATE = "locate"
        const val ACTION_CLEAN_ROOMS = "clean_rooms"
        const val ACTION_MAP_CHANGE = "map_change"
        const val ACTION_RENAME = "rename_room"
    }
}
