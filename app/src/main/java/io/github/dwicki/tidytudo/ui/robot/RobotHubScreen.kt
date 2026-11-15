package io.github.dwicki.tidytudo.ui.robot

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.components.ListCard
import io.github.dwicki.tidytudo.ui.components.NavigationRow
import io.github.dwicki.tidytudo.ui.components.SectionHeader
import io.github.dwicki.tidytudo.ui.robotViewModel

/** Destinations reachable from the robot tab. */
enum class RobotDestination { HISTORY, CONSUMABLES, EVENTS, SCHEDULES, ROOMS, SETTINGS, INFO, ROBOTS, APP_SETTINGS }

/**
 * Entry list for maintenance, schedules, settings and info; rows appear only if the robot supports
 * them. Badges show open messages and worn parts, so they are visible without opening each screen.
 */
@Composable
fun RobotHubScreen(robot: Robot, contentPadding: PaddingValues, onOpen: (RobotDestination) -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> HubViewModel(container, current) }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val capabilities = live.capabilities
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshBadges() }
    LaunchedEffect(capabilities) { if (capabilities.isNotEmpty()) viewModel.refreshBadges() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.hub_care)) }
        item {
            ListCard {
                NavigationRow(Icons.Outlined.History, stringResource(R.string.history_title), stringResource(R.string.hub_history_text)) {
                    onOpen(RobotDestination.HISTORY)
                }
                if (Capabilities.CONSUMABLES in capabilities) {
                    NavigationRow(
                        Icons.Outlined.Build,
                        stringResource(R.string.consumables_title),
                        stringResource(R.string.hub_consumables_text),
                        badge = countBadge(badges.lowConsumables),
                    ) { onOpen(RobotDestination.CONSUMABLES) }
                }
                NavigationRow(
                    Icons.Outlined.Notifications,
                    stringResource(R.string.events_title),
                    stringResource(R.string.hub_events_text),
                    badge = countBadge(badges.openEvents),
                ) { onOpen(RobotDestination.EVENTS) }
                NavigationRow(Icons.Outlined.Schedule, stringResource(R.string.schedules_title), stringResource(R.string.hub_schedules_text)) {
                    onOpen(RobotDestination.SCHEDULES)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.hub_robot), Modifier.padding(top = 8.dp)) }
        item {
            ListCard {
                if (Capabilities.SEGMENT_CLEANING in capabilities) {
                    NavigationRow(Icons.Outlined.MeetingRoom, stringResource(R.string.rooms_title), stringResource(R.string.hub_rooms_text)) {
                        onOpen(RobotDestination.ROOMS)
                    }
                }
                NavigationRow(Icons.Outlined.Tune, stringResource(R.string.settings_title), stringResource(R.string.hub_settings_text)) {
                    onOpen(RobotDestination.SETTINGS)
                }
                NavigationRow(Icons.Outlined.Info, stringResource(R.string.info_title), stringResource(R.string.hub_info_text)) {
                    onOpen(RobotDestination.INFO)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.hub_app), Modifier.padding(top = 8.dp)) }
        item {
            ListCard {
                NavigationRow(Icons.Outlined.SmartToy, stringResource(R.string.robots_title), stringResource(R.string.hub_robots_text)) {
                    onOpen(RobotDestination.ROBOTS)
                }
                // The language row exists only where Android has per-app languages (13+).
                val appSettingsText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    R.string.hub_app_settings_text
                } else {
                    R.string.hub_app_settings_text_no_language
                }
                NavigationRow(Icons.Outlined.Settings, stringResource(R.string.app_settings_title), stringResource(appSettingsText)) {
                    onOpen(RobotDestination.APP_SETTINGS)
                }
            }
        }
    }
}

/** A small error-coloured count, or nothing when there is nothing to count. */
private fun countBadge(count: Int): (@Composable () -> Unit)? = if (count <= 0) {
    null
} else {
    { Badge(containerColor = MaterialTheme.colorScheme.error) { Text(count.toString()) } }
}
