package io.github.dwicki.tidytudo.ui

import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.Connection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Battery4Bar
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.Battery2Bar
import androidx.compose.material.icons.outlined.Battery1Bar
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import io.github.dwicki.tidytudo.ui.components.AppListItem
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.home.HomeScreen
import io.github.dwicki.tidytudo.ui.home.RecordingOffer
import io.github.dwicki.tidytudo.ui.map.MapScreen
import io.github.dwicki.tidytudo.ui.robot.RobotDestination
import io.github.dwicki.tidytudo.ui.robot.RobotHubScreen
import io.github.dwicki.tidytudo.util.RobotAddress
import kotlinx.coroutines.launch

private enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    MAP(R.string.tab_map, Icons.Outlined.Map, Icons.Filled.Map),
    ROBOT(R.string.tab_robot, Icons.Outlined.SmartToy, Icons.Filled.SmartToy),
}

/** Tabs for the selected robot; the title doubles as the robot switcher (G-5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(robot: Robot, robots: List<Robot>, onOpen: (RobotDestination) -> Unit, onAddRobot: () -> Unit) {
    val appSettings = LocalContext.current.appContainer.settings
    // The start screen is a setting (App settings); the map by default.
    var tab by rememberSaveable { mutableStateOf(if (appSettings.startOnMap.value) Tab.MAP else Tab.HOME) }
    var mapOpensRooms by rememberSaveable { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // The robot's state in the bar on every tab, under its name.
    val container = LocalContext.current.appContainer
    val live by remember(robot.id) { container.session(robot).live }.collectAsStateWithLifecycle()
    // The bar takes a tint once content scrolls under it, so cut-off cards do not look like a layout bug.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { RobotPill(robot.displayName, live) { switcherOpen = true } },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        icon = { Icon(if (entry == tab) entry.selectedIcon else entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    robot,
                    padding,
                    snackbar,
                    onOpenMap = { mapOpensRooms = true; tab = Tab.MAP },
                    onOpenRooms = { onOpen(RobotDestination.ROOMS) },
                )
                Tab.MAP -> MapScreen(robot, padding, snackbar, openRooms = mapOpensRooms, onOpenRoomsHandled = { mapOpensRooms = false })
                Tab.ROBOT -> RobotHubScreen(robot, padding, onOpen)
            }
        }
    }

    RecordingOffer()

    if (switcherOpen) {
        RobotSwitcherSheet(
            robots = robots,
            selected = robot,
            onDismiss = { switcherOpen = false },
            onAddRobot = { switcherOpen = false; onAddRobot() },
        )
    }
}

/**
 * The robot's name as a pill that opens the switcher, with its state underneath: an icon and word
 * for what it is doing, and the battery. The pill tints while it works and turns red on an error,
 * so the state reads at a glance on every tab.
 */
@Composable
private fun RobotPill(name: String, live: LiveState, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val state = live.state
    val status = state?.status?.value ?: StatusValue.UNKNOWN
    val offline = live.connection is Connection.Offline
    val known = state != null && !offline
    val container by animateColorAsState(
        when {
            known && status == StatusValue.ERROR -> colors.errorContainer
            known && status.isActive -> colors.primaryContainer
            else -> colors.surfaceContainerHigh
        },
        label = "pill container",
    )
    val accent = when {
        !known -> colors.onSurfaceVariant
        status == StatusValue.ERROR -> colors.error
        status == StatusValue.DOCKED -> colors.tertiary
        status.isActive -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val statusText = when {
        offline -> stringResource(R.string.status_offline)
        state == null -> stringResource(R.string.status_connecting)
        else -> context.getString(Labels.status(status))
    }
    val battery = state?.battery?.takeIf { !offline }
    val batteryText = battery?.let { stringResource(R.string.battery_percent, it.level) }
    val spoken = listOfNotNull(name, statusText, batteryText).joinToString(", ")
    val switchLabel = stringResource(R.string.switch_robot)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                onClick(label = switchLabel) { onClick(); true }
            },
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = if (known && status == StatusValue.ERROR) colors.error else colors.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            // Both lines centred on each other, so the pill reads as one label in the centred bar.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon(status, offline, state == null), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(statusText, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (battery != null && batteryText != null) {
                        Text(" · ", style = MaterialTheme.typography.labelMedium, color = colors.outline)
                        Icon(
                            batteryIcon(battery.level, battery.isCharging),
                            contentDescription = null,
                            tint = if (battery.level < LOW_BATTERY && !battery.isCharging) colors.error else colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(batteryText, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun statusIcon(status: StatusValue, offline: Boolean, unknown: Boolean): ImageVector = when {
    offline -> Icons.Outlined.CloudOff
    unknown -> Icons.Outlined.Sync
    else -> when (status) {
        StatusValue.DOCKED -> Icons.Outlined.Home
        StatusValue.CLEANING -> Icons.Outlined.CleaningServices
        StatusValue.RETURNING -> Icons.AutoMirrored.Outlined.Undo
        StatusValue.PAUSED -> Icons.Outlined.Pause
        StatusValue.ERROR -> Icons.Outlined.WarningAmber
        StatusValue.MOVING, StatusValue.MANUAL_CONTROL -> Icons.Outlined.Navigation
        StatusValue.IDLE, StatusValue.UNKNOWN -> Icons.Outlined.PauseCircleOutline
    }
}

private fun batteryIcon(level: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Outlined.BatteryChargingFull
    level >= 90 -> Icons.Outlined.BatteryFull
    level >= 70 -> Icons.Outlined.Battery5Bar
    level >= 50 -> Icons.Outlined.Battery4Bar
    level >= 30 -> Icons.Outlined.Battery3Bar
    level >= 15 -> Icons.Outlined.Battery2Bar
    else -> Icons.Outlined.Battery1Bar
}

private const val LOW_BATTERY = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobotSwitcherSheet(robots: List<Robot>, selected: Robot, onDismiss: () -> Unit, onAddRobot: () -> Unit) {
    val store = LocalContext.current.appContainer.robotStore
    val scope = rememberCoroutineScope()
    val activeLabel = stringResource(R.string.robots_active_short)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.switch_robot),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            robots.forEach { robot ->
                val active = robot.id == selected.id
                // The active robot gets a tinted row, not only a check mark at the far edge.
                AppListItem(
                    title = robot.displayName,
                    supporting = RobotAddress.host(robot.baseUrl),
                    icon = Icons.Outlined.SmartToy,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .semantics { if (active) stateDescription = activeLabel },
                    onClick = {
                        scope.launch { store.select(robot.id) }
                        onDismiss()
                    },
                    trailing = if (active) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
            AppListItem(
                title = stringResource(R.string.robots_add),
                icon = Icons.Outlined.Add,
                modifier = Modifier.padding(horizontal = 8.dp),
                onClick = onAddRobot,
            )
        }
    }
}
