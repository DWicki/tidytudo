package io.github.dwicki.tidytudo.ui.robots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.data.Robot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Several robots (F-1.6): select, rename, remove. Removing also deletes the stored credentials. */
class RobotsViewModel(private val container: AppContainer) : ViewModel() {
    val robots: StateFlow<List<Robot>> = container.robotStore.robots
    val selectedId: StateFlow<String?> = container.robotStore.selectedId

    fun select(robot: Robot) = viewModelScope.launch { container.robotStore.select(robot.id) }

    fun rename(robot: Robot, nickname: String) = viewModelScope.launch {
        container.robotStore.update(robot.id) { it.copy(nickname = nickname.trim()) }
    }

    fun remove(robot: Robot) = viewModelScope.launch {
        container.vault.delete(robot.id)
        container.sessions.invalidate(robot.id)
        container.robotStore.remove(robot.id)
        container.history.deleteRobot(robot.id)
    }
}
