package io.github.dwicki.tidytudo.ui.care

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.toLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventsViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val client = container.session(robot).client
    private val state = MutableStateFlow<LoadState<List<RobotEvent>>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<List<RobotEvent>>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Newest first, open ones before handled ones.
            state.value = client.events().toLoadState().let { result ->
                if (result is LoadState.Loaded) {
                    LoadState.Loaded(result.value.sortedWith(compareBy<RobotEvent> { it.processed }.thenByDescending { it.timestamp }))
                } else {
                    result
                }
            }
        }
    }

    fun interact(event: RobotEvent, interaction: String) =
        launchCommand(block = { client.interact(event.id, interaction) }, onSuccess = ::load)
}
