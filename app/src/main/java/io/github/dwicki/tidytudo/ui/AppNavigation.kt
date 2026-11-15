package io.github.dwicki.tidytudo.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.appsettings.AppSettingsScreen
import io.github.dwicki.tidytudo.ui.care.ConsumablesScreen
import io.github.dwicki.tidytudo.ui.care.EventsScreen
import io.github.dwicki.tidytudo.ui.history.HistoryScreen
import io.github.dwicki.tidytudo.ui.history.SessionDetailScreen
import io.github.dwicki.tidytudo.ui.info.RobotInfoScreen
import io.github.dwicki.tidytudo.ui.onboarding.AddRobotScreen
import io.github.dwicki.tidytudo.ui.robot.RobotDestination
import io.github.dwicki.tidytudo.ui.robots.RobotsScreen
import io.github.dwicki.tidytudo.ui.rooms.RoomsScreen
import io.github.dwicki.tidytudo.ui.schedules.SchedulesScreen
import io.github.dwicki.tidytudo.ui.settings.RobotSettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val ADD_ROBOT = "add_robot"
    const val HISTORY = "history"
    const val SESSION = "history/{id}"
    const val CONSUMABLES = "consumables"
    const val EVENTS = "events"
    const val SCHEDULES = "schedules"
    const val SETTINGS = "settings"
    const val ROOMS = "rooms"
    const val INFO = "info"
    const val ROBOTS = "robots"
    const val APP_SETTINGS = "app_settings"
}

/** Top-level navigation. Robot-specific screens always show the currently selected robot. */
@Composable
fun AppNavigation() {
    val store = LocalContext.current.appContainer.robotStore
    val robots by store.robots.collectAsStateWithLifecycle()
    val selectedId by store.selectedId.collectAsStateWithLifecycle()
    val robot = robots.firstOrNull { it.id == selectedId } ?: robots.firstOrNull()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (robots.isEmpty()) Routes.ONBOARDING else Routes.MAIN,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.ONBOARDING) {
            AddRobotScreen(
                onAdded = { navController.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                onBack = null,
            )
        }
        composable(Routes.ADD_ROBOT) {
            AddRobotScreen(onAdded = { navController.popBackStack(Routes.MAIN, inclusive = false) }, onBack = navController::popBackStack)
        }
        composable(Routes.MAIN) {
            if (robot == null) {
                // The last robot was removed: back to onboarding.
                LaunchedEffect(Unit) { navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.MAIN) { inclusive = true } } }
            } else {
                MainScreen(
                    robot = robot,
                    robots = robots,
                    onOpen = { navController.navigate(it.route) },
                    onAddRobot = { navController.navigate(Routes.ADD_ROBOT) },
                )
            }
        }
        robotScreen(Routes.HISTORY, robot, navController) {
            HistoryScreen(
                robot = it,
                onBack = navController::popBackStack,
                onOpen = { id -> navController.navigate("history/$id") },
            )
        }
        composable(Routes.SESSION) { entry ->
            val id = entry.arguments?.getString("id")
            if (robot != null && id != null) SessionDetailScreen(robot, id, navController::popBackStack)
        }
        robotScreen(Routes.CONSUMABLES, robot, navController) { ConsumablesScreen(it, navController::popBackStack) }
        robotScreen(Routes.EVENTS, robot, navController) { EventsScreen(it, navController::popBackStack) }
        robotScreen(Routes.SCHEDULES, robot, navController) { SchedulesScreen(it, navController::popBackStack) }
        robotScreen(Routes.ROOMS, robot, navController) { RoomsScreen(it, navController::popBackStack) }
        robotScreen(Routes.SETTINGS, robot, navController) { RobotSettingsScreen(it, navController::popBackStack) }
        robotScreen(Routes.INFO, robot, navController) { RobotInfoScreen(it, navController::popBackStack) }
        composable(Routes.ROBOTS) {
            RobotsScreen(onBack = navController::popBackStack, onAddRobot = { navController.navigate(Routes.ADD_ROBOT) })
        }
        composable(Routes.APP_SETTINGS) { AppSettingsScreen(onBack = navController::popBackStack) }
    }
}

private fun NavGraphBuilder.robotScreen(
    route: String,
    robot: Robot?,
    navController: NavHostController,
    content: @Composable (Robot) -> Unit,
) = composable(route) {
    if (robot == null) {
        LaunchedEffect(Unit) { navController.popBackStack(Routes.MAIN, inclusive = false) }
    } else {
        content(robot)
    }
}

private val RobotDestination.route: String
    get() = when (this) {
        RobotDestination.HISTORY -> Routes.HISTORY
        RobotDestination.CONSUMABLES -> Routes.CONSUMABLES
        RobotDestination.EVENTS -> Routes.EVENTS
        RobotDestination.SCHEDULES -> Routes.SCHEDULES
        RobotDestination.ROOMS -> Routes.ROOMS
        RobotDestination.SETTINGS -> Routes.SETTINGS
        RobotDestination.INFO -> Routes.INFO
        RobotDestination.ROBOTS -> Routes.ROBOTS
        RobotDestination.APP_SETTINGS -> Routes.APP_SETTINGS
    }
