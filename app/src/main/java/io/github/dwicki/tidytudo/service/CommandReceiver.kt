package io.github.dwicki.tidytudo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.widget.RobotWidgetProvider
import kotlinx.coroutines.launch

/** Executes start/pause/dock from notification actions and widget buttons, without opening the app. */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val robotId = intent.getStringExtra(EXTRA_ROBOT_ID)
        val robot = robotId?.let(container.robotStore::robot) ?: container.robotStore.selected() ?: return
        val command = intent.action ?: return
        val pending = goAsync()
        container.appScope.launch {
            try {
                val session = container.session(robot)
                val state = session.client.state().let { (it as? ApiResult.Success)?.value }
                val result = when (command) {
                    ACTION_TOGGLE -> QuickActions.toggle(session.client, state)
                    ACTION_DOCK -> QuickActions.dock(session.client)
                    else -> return@launch
                }
                if (result is ApiResult.Failure) {
                    showFailure(context)
                } else if (command == ACTION_TOGGLE) {
                    MonitorService.watch(context, robot.id)
                }
                RobotWidgetProvider.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showFailure(context: Context) {
        context.mainExecutor.execute {
            Toast.makeText(context, R.string.error_command_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.dwicki.tidytudo.action.TOGGLE"
        const val ACTION_DOCK = "io.github.dwicki.tidytudo.action.DOCK"
        private const val EXTRA_ROBOT_ID = "robot_id"

        fun intent(context: Context, robotId: String?, action: String): Intent =
            Intent(context, CommandReceiver::class.java).setAction(action).putExtra(EXTRA_ROBOT_ID, robotId)
    }
}
