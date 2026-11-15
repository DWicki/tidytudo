package io.github.dwicki.tidytudo.ui.settings

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.ui.cleaning.CleaningModeViewModel
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.DoNotDisturb
import io.github.dwicki.tidytudo.api.model.VoicePackState
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the settings screen shows; a null field means the robot does not support it. */
data class RobotSettings(
    val doNotDisturb: DoNotDisturb? = null,
    val volume: Int? = null,
    val canTestSound: Boolean = false,
    val voicePack: VoicePackState? = null,
    /** Capability name → enabled, for the simple switches the robot reports. */
    val switches: Map<String, Boolean> = emptyMap(),
    val carpetSensorMode: String? = null,
    val carpetSensorModes: List<String> = emptyList(),
    val washTemperature: String? = null,
    val washTemperatures: List<String> = emptyList(),
    val autoEmptyInterval: String? = null,
    val autoEmptyIntervals: List<String> = emptyList(),
)

/** Do-not-disturb, sound and all capability switches (F-6.3 – F-6.7). */
class RobotSettingsViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val client = container.session(robot).client

    private val state = MutableStateFlow<LoadState<RobotSettings>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<RobotSettings>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.value = when (val capabilities = client.capabilities()) {
                is ApiResult.Success -> LoadState.Loaded(read(capabilities.value.toSet()))
                is ApiResult.Failure -> LoadState.Failed(capabilities.error)
            }
        }
    }

    private suspend fun read(capabilities: Set<String>): RobotSettings {
        fun has(name: String) = name in capabilities
        // Mop switches are part of the cleaning mode (Home and Map), not of the robot's general settings.
        val switches = Capabilities.switches.filter(::has).filterNot { it in CleaningModeViewModel.MOP_SWITCHES }
            .associateWith { client.toggle(it).getOrNull()?.enabled }
        return RobotSettings(
            doNotDisturb = if (has(Capabilities.DO_NOT_DISTURB)) client.doNotDisturb().getOrNull() else null,
            volume = if (has(Capabilities.SPEAKER_VOLUME)) client.volume().getOrNull()?.volume else null,
            canTestSound = has(Capabilities.SPEAKER_TEST),
            voicePack = if (has(Capabilities.VOICE_PACK)) client.voicePack().getOrNull() else null,
            switches = switches.filterValues { it != null }.mapValues { it.value == true },
            carpetSensorMode = if (has(Capabilities.CARPET_SENSOR_MODE)) client.carpetSensorMode().getOrNull()?.mode else null,
            carpetSensorModes = if (has(Capabilities.CARPET_SENSOR_MODE)) {
                client.carpetSensorModeProperties().getOrNull()?.supportedModes.orEmpty()
            } else {
                emptyList()
            },
            washTemperature = if (has(Capabilities.WASH_TEMPERATURE)) client.washTemperature().getOrNull()?.temperature else null,
            washTemperatures = if (has(Capabilities.WASH_TEMPERATURE)) {
                client.washTemperatureProperties().getOrNull()?.supportedTemperatures.orEmpty()
            } else {
                emptyList()
            },
            autoEmptyInterval = if (has(Capabilities.AUTO_EMPTY_INTERVAL)) client.autoEmptyInterval().getOrNull()?.interval else null,
            autoEmptyIntervals = if (has(Capabilities.AUTO_EMPTY_INTERVAL)) {
                client.autoEmptyIntervalProperties().getOrNull()?.supportedIntervals.orEmpty()
            } else {
                emptyList()
            },
        )
    }

    /** Updates the UI at once (optimistic) and rolls back via reload if the robot refuses. */
    private fun optimistic(change: (RobotSettings) -> RobotSettings, command: suspend () -> ApiResult<Unit>) {
        state.update { current -> if (current is LoadState.Loaded) LoadState.Loaded(change(current.value)) else current }
        viewModelScope.launch {
            val result = command()
            if (result is ApiResult.Failure) {
                report(result.error)
                load()
            }
        }
    }

    fun setSwitch(capability: String, enabled: Boolean) =
        optimistic({ it.copy(switches = it.switches + (capability to enabled)) }) { client.setToggle(capability, enabled) }

    fun setDoNotDisturb(value: DoNotDisturb) =
        optimistic({ it.copy(doNotDisturb = value) }) { client.setDoNotDisturb(value) }

    fun setVolume(percent: Int) = optimistic({ it.copy(volume = percent) }) { client.setVolume(percent) }

    fun testSound() = launchCommand(block = { client.playTestSound() })

    fun setCarpetSensorMode(mode: String) =
        optimistic({ it.copy(carpetSensorMode = mode) }) { client.setCarpetSensorMode(mode) }

    fun setWashTemperature(value: String) =
        optimistic({ it.copy(washTemperature = value) }) { client.setWashTemperature(value) }

    fun setAutoEmptyInterval(value: String) =
        optimistic({ it.copy(autoEmptyInterval = value) }) { client.setAutoEmptyInterval(value) }
}
