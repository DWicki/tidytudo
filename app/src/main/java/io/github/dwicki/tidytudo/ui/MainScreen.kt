package io.github.dwicki.tidytudo.ui

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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import io.github.dwicki.tidytudo.ui.components.clickableRow
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
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var switcherOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TextButton(onClick = { switcherOpen = true }) {
                        Text(robot.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.switch_robot))
                    }
                },
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
                    onOpenMap = { tab = Tab.MAP },
                    onOpenRooms = { onOpen(RobotDestination.ROOMS) },
                )
                Tab.MAP -> MapScreen(robot, padding, snackbar)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobotSwitcherSheet(robots: List<Robot>, selected: Robot, onDismiss: () -> Unit, onAddRobot: () -> Unit) {
    val store = LocalContext.current.appContainer.robotStore
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.switch_robot),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            robots.forEach { robot ->
                ListItem(
                    modifier = Modifier.clickableRow {
                        scope.launch { store.select(robot.id) }
                        onDismiss()
                    },
                    leadingContent = {
                        Icon(
                            if (robot.id == selected.id) Icons.Outlined.CheckCircle else Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(robot.displayName) },
                    supportingContent = { Text(RobotAddress.host(robot.baseUrl)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
            ListItem(
                modifier = Modifier.clickableRow(onAddRobot),
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.robots_add)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

