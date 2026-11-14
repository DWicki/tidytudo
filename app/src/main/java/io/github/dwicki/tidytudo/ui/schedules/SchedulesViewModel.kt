package io.github.dwicki.tidytudo.ui.schedules

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.TimerProperties
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SchedulesData(
    val timers: List<ValetudoTimer>,
    val properties: TimerProperties,
    val segments: List<MapSegment>,
    /** Preset choices per pre-action type, e.g. `fan_speed_control` → `["low", "max"]`. */
    val preActionOptions: Map<String, List<String>>,
)

/** Timers (F-6.1, F-6.2). Times are UTC on the robot; conversion happens in the editor. */
class SchedulesViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val session = container.session(robot)
    private val client = session.client

    private val state = MutableStateFlow<LoadState<SchedulesData>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<SchedulesData>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val timers = client.timers()
            if (timers is ApiResult.Failure) {
                state.value = LoadState.Failed(timers.error)
                return@launch
            }
            val properties = client.timerProperties().getOrNull() ?: TimerProperties()
            val capabilities = client.capabilities().getOrNull().orEmpty().toSet()
            val segments = if (Capabilities.SEGMENT_CLEANING in capabilities) client.segments().getOrNull().orEmpty() else emptyList()
            val options = preActionCapabilities
                .filter { (preAction, capability) -> preAction in properties.supportedPreActions && capability in capabilities }
                .mapValues { (_, capability) -> client.presets(capability).getOrNull().orEmpty() }
            val sorted = timers.getOrNull().orEmpty().sortedWith(compareBy({ it.hour }, { it.minute }))
            state.value = LoadState.Loaded(SchedulesData(sorted, properties, segments.sortedBy { it.name ?: it.id }, options))
        }
    }

    fun save(timer: ValetudoTimer) = launchCommand(block = { client.saveTimer(timer) }, onSuccess = ::load)

    fun setEnabled(timer: ValetudoTimer, enabled: Boolean) = save(timer.copy(enabled = enabled))

    fun delete(timer: ValetudoTimer) = launchCommand(block = { client.deleteTimer(timer.id) }, onSuccess = ::load)

    fun runNow(timer: ValetudoTimer) = launchCommand(block = { client.runTimerNow(timer.id) })

    private companion object {
        val preActionCapabilities = mapOf(
            ValetudoTimer.PRE_FAN_SPEED to Capabilities.FAN_SPEED,
            ValetudoTimer.PRE_WATER_USAGE to Capabilities.WATER_USAGE,
            ValetudoTimer.PRE_OPERATION_MODE to Capabilities.OPERATION_MODE,
        )
    }
}
