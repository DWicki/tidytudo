package io.github.dwicki.tidytudo.ui.home

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

data class RoomSelection(val ids: List<String> = emptyList(), val iterations: Int = 1)

class HomeViewModel(private val container: AppContainer, private val robot: Robot) : CommandViewModel() {

    private val session = container.session(robot)
    val live: StateFlow<LiveState> = session.live

    private val extrasState = MutableStateFlow(HomeExtras())
    val extras: StateFlow<HomeExtras> = extrasState.asStateFlow()

    private val selectionState = MutableStateFlow(RoomSelection())
    val selection: StateFlow<RoomSelection> = selectionState.asStateFlow()

    /** Name of the action whose request is in flight, to show a spinner on exactly that button. */
    private val busyState = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = busyState.asStateFlow()

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
    fun locate() = run(ACTION_LOCATE) { session.client.locate() }
    fun triggerDock(capability: String) = run(capability) { session.client.trigger(capability) }

    fun setPreset(capability: String, value: String) = run(capability) { session.client.setPreset(capability, value) }

    fun toggleRoom(id: String) = selectionState.update { current ->
        current.copy(ids = if (id in current.ids) current.ids - id else current.ids + id)
    }

    fun setIterations(iterations: Int) = selectionState.update { it.copy(iterations = iterations) }

    fun clearSelection() = selectionState.update { RoomSelection(iterations = it.iterations) }

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

    fun deletePreset(preset: CleaningPreset) {
        viewModelScope.launch {
            container.robotStore.update(robot.id) { stored -> stored.copy(presets = stored.presets.filterNot { it.id == preset.id }) }
        }
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
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_STOP = "stop"
        const val ACTION_DOCK = "dock"
        const val ACTION_LOCATE = "locate"
        const val ACTION_CLEAN_ROOMS = "clean_rooms"
        const val ACTION_MAP_CHANGE = "map_change"
    }
}
