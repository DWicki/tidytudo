package io.github.dwicki.tidytudo.ui.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.ui.components.NavigationRow

/** Destinations reachable from the robot tab. */
enum class RobotDestination { HISTORY, CONSUMABLES, EVENTS, SCHEDULES, SETTINGS, INFO, ROBOTS, APP_SETTINGS }

/** Entry list for maintenance, schedules, settings and info; rows appear only if the robot supports them. */
@Composable
fun RobotHubScreen(robot: Robot, contentPadding: PaddingValues, onOpen: (RobotDestination) -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> HubViewModel(container, current) }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val capabilities = live.capabilities
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Group(stringResource(R.string.hub_care)) {
                NavigationRow(Icons.Outlined.History, stringResource(R.string.history_title), stringResource(R.string.hub_history_text)) {
                    onOpen(RobotDestination.HISTORY)
                }
                if (Capabilities.CONSUMABLES in capabilities) {
                    NavigationRow(Icons.Outlined.Build, stringResource(R.string.consumables_title), stringResource(R.string.hub_consumables_text)) {
                        onOpen(RobotDestination.CONSUMABLES)
                    }
                }
                NavigationRow(Icons.Outlined.Notifications, stringResource(R.string.events_title), stringResource(R.string.hub_events_text)) {
                    onOpen(RobotDestination.EVENTS)
                }
                NavigationRow(Icons.Outlined.Schedule, stringResource(R.string.schedules_title), stringResource(R.string.hub_schedules_text)) {
                    onOpen(RobotDestination.SCHEDULES)
                }
            }
        }
        item {
            Group(stringResource(R.string.hub_robot)) {
                NavigationRow(Icons.Outlined.Tune, stringResource(R.string.settings_title), stringResource(R.string.hub_settings_text)) {
                    onOpen(RobotDestination.SETTINGS)
                }
                NavigationRow(Icons.Outlined.Info, stringResource(R.string.info_title), stringResource(R.string.hub_info_text)) {
                    onOpen(RobotDestination.INFO)
                }
            }
        }
        item {
            Group(stringResource(R.string.hub_app)) {
                NavigationRow(Icons.Outlined.SmartToy, stringResource(R.string.robots_title), stringResource(R.string.hub_robots_text)) {
                    onOpen(RobotDestination.ROBOTS)
                }
                NavigationRow(Icons.Outlined.Settings, stringResource(R.string.app_settings_title), stringResource(R.string.hub_app_settings_text)) {
                    onOpen(RobotDestination.APP_SETTINGS)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.clip(MaterialTheme.shapes.large), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}
