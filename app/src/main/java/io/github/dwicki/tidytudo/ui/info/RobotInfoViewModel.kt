package io.github.dwicki.tidytudo.ui.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.DataPoint
import io.github.dwicki.tidytudo.api.model.HostInfo
import io.github.dwicki.tidytudo.api.model.RobotInfo
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RobotDetails(
    val robot: RobotInfo,
    val firmware: String?,
    val valetudoVersion: String,
    val host: HostInfo?,
    val capabilities: List<String>,
    val unknownCapabilities: List<String>,
    val currentStatistics: List<DataPoint>,
    val totalStatistics: List<DataPoint>,
)

/** Robot, firmware and Valetudo information plus statistics (F-5.3, F-5.4). */
class RobotInfoViewModel(container: AppContainer, robot: Robot) : ViewModel() {
    private val client = container.session(robot).client

    private val state = MutableStateFlow<LoadState<RobotDetails>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<RobotDetails>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val info = client.robotInfo()
            if (info is ApiResult.Failure) {
                state.value = LoadState.Failed(info.error)
                return@launch
            }
            val capabilities = client.capabilities().getOrNull().orEmpty().sorted()
            state.value = LoadState.Loaded(
                RobotDetails(
                    robot = (info as ApiResult.Success).value,
                    firmware = client.robotProperties().getOrNull()?.firmwareVersion,
                    valetudoVersion = client.version().getOrNull()?.release.orEmpty(),
                    host = client.hostInfo().getOrNull(),
                    capabilities = capabilities,
                    unknownCapabilities = Capabilities.unknownOf(capabilities),
                    currentStatistics = if (Capabilities.CURRENT_STATISTICS in capabilities) client.currentStatistics().getOrNull().orEmpty() else emptyList(),
                    totalStatistics = if (Capabilities.TOTAL_STATISTICS in capabilities) client.totalStatistics().getOrNull().orEmpty() else emptyList(),
                ),
            )
        }
    }
}
