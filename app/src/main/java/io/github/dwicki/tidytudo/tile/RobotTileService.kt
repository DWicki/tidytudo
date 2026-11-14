package io.github.dwicki.tidytudo.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.service.MonitorService
import io.github.dwicki.tidytudo.service.QuickActions
import io.github.dwicki.tidytudo.ui.Labels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick-settings tile for the selected robot (F-7.2): active while cleaning, tap starts or pauses.
 * Long-press opens the app via the system's default tile behaviour.
 */
class RobotTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var lastState: RobotState? = null

    override fun onStartListening() {
        super.onStartListening()
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        val (robot, session) = appContainer.selectedSession() ?: return showNoRobot()
        listening.launch {
            session.live.collect { live ->
                lastState = live.state
                render(robot.displayName, live.state)
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val (robot, session) = appContainer.selectedSession() ?: return
        val currentScope = scope ?: return
        currentScope.launch {
            val result = QuickActions.toggle(session.client, lastState)
            if (result is ApiResult.Success) MonitorService.watch(this@RobotTileService, robot.id)
        }
    }

    private fun render(name: String, state: RobotState?) {
        val tile = qsTile ?: return
        tile.label = name
        tile.subtitle = state?.let { current ->
            listOfNotNull(
                getString(Labels.status(current.status.value)),
                current.battery?.let { getString(R.string.battery_percent, it.level) },
            ).joinToString(" · ")
        } ?: getString(R.string.status_offline)
        tile.state = when {
            state == null -> Tile.STATE_UNAVAILABLE
            state.status.value.isActive -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun showNoRobot() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(R.string.widget_no_robot)
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }
}
