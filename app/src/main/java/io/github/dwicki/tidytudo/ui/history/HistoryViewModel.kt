package io.github.dwicki.tidytudo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.JsonCodec
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.history.CleaningSession
import io.github.dwicki.tidytudo.map.MapParser
import io.github.dwicki.tidytudo.map.RobotMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Cleaning history of one robot (F-8.4): the list, and the selected session's map. */
class HistoryViewModel(private val container: AppContainer, private val robot: Robot) : ViewModel() {
    private val store = container.history

    val sessions: StateFlow<List<CleaningSession>> = store.sessions
        .map { all -> all.filter { it.robotId == robot.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.sessions.value.filter { it.robotId == robot.id })

    /** Recording needs a live connection; without background monitoring, runs while the app is closed are missed. */
    val monitoringEnabled: StateFlow<Boolean> = container.settings.backgroundMonitoring

    private val mapState = MutableStateFlow<RobotMap?>(null)
    val selectedMap: StateFlow<RobotMap?> = mapState.asStateFlow()

    fun loadMap(sessionId: String) {
        mapState.value = null
        viewModelScope.launch {
            mapState.value = withContext(Dispatchers.Default) {
                store.mapJson(sessionId)?.let { json ->
                    runCatching { MapParser.parse(JsonCodec.json.parseToJsonElement(json)) }.getOrNull()
                }
            }
        }
    }

    fun imageFile(sessionId: String, obstacleId: String): File = store.imageFile(sessionId, obstacleId)

    fun delete(sessionId: String) {
        viewModelScope.launch { store.delete(sessionId) }
    }
}
