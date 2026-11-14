package io.github.dwicki.tidytudo.ui.robot

import androidx.lifecycle.ViewModel
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.data.Robot
import kotlinx.coroutines.flow.StateFlow

/** Only exposes the capabilities, so the hub can hide rows the robot does not support. */
class HubViewModel(container: AppContainer, robot: Robot) : ViewModel() {
    val live: StateFlow<LiveState> = container.session(robot).live
}
