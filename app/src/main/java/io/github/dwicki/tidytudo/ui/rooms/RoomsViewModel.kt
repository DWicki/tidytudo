package io.github.dwicki.tidytudo.ui.rooms

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomsData(val rooms: List<MapSegment>, val canRename: Boolean)

/** The robot's rooms with renaming (F-4.5). */
class RoomsViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val client = container.session(robot).client

    private val state = MutableStateFlow<LoadState<RoomsData>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<RoomsData>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val capabilities = client.capabilities().getOrNull().orEmpty()
            state.value = when (val rooms = client.segments()) {
                is ApiResult.Success -> LoadState.Loaded(
                    RoomsData(rooms.value.sortedBy { it.name ?: it.id }, canRename = Capabilities.SEGMENT_RENAME in capabilities),
                )
                is ApiResult.Failure -> LoadState.Failed(rooms.error)
            }
        }
    }

    /** Shows the new name at once and reverts by reloading if the robot refuses it. */
    fun rename(room: MapSegment, name: String) {
        updateName(room.id, name)
        viewModelScope.launch {
            val result = client.renameSegment(room.id, name)
            if (result is ApiResult.Failure) {
                report(result.error)
                load()
            }
        }
    }

    private fun updateName(id: String, name: String) = state.update { current ->
        if (current !is LoadState.Loaded) return@update current
        val rooms = current.value.rooms.map { if (it.id == id) it.copy(name = name) else it }
        LoadState.Loaded(current.value.copy(rooms = rooms.sortedBy { it.name ?: it.id }))
    }
}
