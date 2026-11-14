package io.github.dwicki.tidytudo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.Robot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Creates a view model with access to the [AppContainer]; [key] separates instances per robot. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** One view model instance per robot, so switching robots starts fresh. */
@Composable
inline fun <reified VM : ViewModel> robotViewModel(robot: Robot, crossinline create: (AppContainer, Robot) -> VM): VM =
    appViewModel(key = "${VM::class.simpleName}-${robot.id}") { container -> create(container, robot) }

/** Base for screens that send commands and report failures as one-shot snackbar messages. */
abstract class CommandViewModel : ViewModel() {
    private val errorChannel = Channel<ApiError>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    /** Runs [block]; a failure is reported once via [errors]. [onSuccess] runs on the main thread. */
    protected fun launchCommand(block: suspend () -> ApiResult<*>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Failure -> errorChannel.send(result.error)
                is ApiResult.Success -> onSuccess()
            }
        }
    }

    protected suspend fun report(error: ApiError) = errorChannel.send(error)
}
