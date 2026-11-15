package io.github.dwicki.tidytudo.ui.cleaning

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything about how the robot cleans: mode, suction, water and the mop switches. */
data class CleaningModeState(
    /** Capability → the presets the robot offers, only for the preset capabilities it has. */
    val options: Map<String, List<String>> = emptyMap(),
    /** Capability → current preset; the robot's reported value, or the user's choice until it reports back. */
    val current: Map<String, String> = emptyMap(),
    /** Mop switch capability → enabled, only for the switches the robot has. */
    val mopSwitches: Map<String, Boolean> = emptyMap(),
) {
    val operationMode: String? get() = current[Capabilities.OPERATION_MODE]
    val isEmpty: Boolean get() = options.values.all { it.isEmpty() }
    val usesSuction: Boolean get() = operationMode != MODE_MOP
    val usesWater: Boolean get() = operationMode != MODE_VACUUM

    companion object {
        const val MODE_MOP = "mop"
        const val MODE_VACUUM = "vacuum"
    }
}

/**
 * One source for the cleaning mode on Home and Map (UX review: the mode used to live only on Home,
 * while rooms were started from the map). Changes are sent at once; the robot's state comes back
 * through the live stream, and until then the chosen value is shown, so the sheet feels instant.
 */
class CleaningModeViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val session = container.session(robot)
    private val live: StateFlow<LiveState> = session.live

    private val options = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val pending = MutableStateFlow<Map<String, String>>(emptyMap())
    private val switches = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val state: StateFlow<CleaningModeState> = combine(live, options, pending, switches) { live, options, pending, switches ->
        val reported = buildMap {
            live.state?.operationMode?.let { put(Capabilities.OPERATION_MODE, it) }
            live.state?.fanSpeed?.let { put(Capabilities.FAN_SPEED, it) }
            live.state?.waterUsage?.let { put(Capabilities.WATER_USAGE, it) }
        }
        CleaningModeState(options, reported + pending, switches)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CleaningModeState())

    init {
        // Load once the robot has told us what it can do.
        viewModelScope.launch {
            val capabilities = live.map { it.capabilities }.distinctUntilChanged().filter { it.isNotEmpty() }.first()
            load(capabilities)
        }
        // Drop a pending choice once the robot reports it, so later changes made elsewhere show up.
        viewModelScope.launch {
            live.collect { current ->
                val state = current.state ?: return@collect
                val reported = mapOf(
                    Capabilities.OPERATION_MODE to state.operationMode,
                    Capabilities.FAN_SPEED to state.fanSpeed,
                    Capabilities.WATER_USAGE to state.waterUsage,
                )
                pending.update { choices -> choices.filterNot { (capability, value) -> reported[capability] == value } }
            }
        }
    }

    private suspend fun load(capabilities: Set<String>) {
        val client = session.client
        options.value = PRESET_CAPABILITIES.filter { it in capabilities }
            .associateWith { capability -> client.presets(capability).getOrNull().orEmpty().filter { it != CUSTOM } }
        switches.value = MOP_SWITCHES.filter { it in capabilities }
            .mapNotNull { capability -> client.toggle(capability).getOrNull()?.let { capability to it.enabled } }
            .toMap()
    }

    fun setPreset(capability: String, value: String) {
        pending.update { it + (capability to value) }
        viewModelScope.launch {
            val result = session.client.setPreset(capability, value)
            if (result is ApiResult.Failure) {
                pending.update { it - capability }
                report(result.error)
            }
        }
    }

    fun setSwitch(capability: String, enabled: Boolean) {
        switches.update { it + (capability to enabled) }
        viewModelScope.launch {
            val result = session.client.setToggle(capability, enabled)
            if (result is ApiResult.Failure) {
                switches.update { it + (capability to !enabled) }
                report(result.error)
            }
        }
    }

    companion object {
        val PRESET_CAPABILITIES = listOf(Capabilities.OPERATION_MODE, Capabilities.FAN_SPEED, Capabilities.WATER_USAGE)

        /** Mop switches belong with the mode, not in the robot's general settings. */
        val MOP_SWITCHES = listOf(
            "MopExtensionControlCapability",
            "MopExtensionFurnitureLegHandlingControlCapability",
            "MopTwistControlCapability",
        )

        /** Valetudo's "custom" means "set per room in the vendor app"; choosing it here does nothing useful. */
        private const val CUSTOM = "custom"
    }
}
