package io.github.dwicki.tidytudo.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.util.AppLog
import io.github.dwicki.tidytudo.widget.RobotWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the live connection to robots while the app is in the background (ADR-6):
 *  - while a robot cleans, an ongoing notification with pause/dock (F-7.3);
 *  - on errors and depleted consumables, an alert (F-7.4);
 *  - keeps the widget fresh as a side effect.
 *
 * Runs while a cleaning job started from the app is active, or always if the user enabled
 * background monitoring. Stops itself when neither applies.
 */
class MonitorService : LifecycleService() {

    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val watchers = mutableMapOf<String, Job>()
    private val activeRobots = mutableSetOf<String>()
    private val notifiedEvents = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        startForeground(
            Notifications.MONITOR_NOTIFICATION_ID,
            Notifications.monitoring(this, 1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        observeRobots()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.getStringExtra(EXTRA_ROBOT_ID)?.let { activeRobots += it }
        return START_STICKY
    }

    private fun observeRobots() {
        val container = appContainer
        lifecycleScope.launch {
            combine(container.robotStore.robots, container.settings.backgroundMonitoring) { robots, monitorAll ->
                robots to monitorAll
            }.collectLatest { (robots, monitorAll) ->
                val watched = if (monitorAll) robots else robots.filter { it.id in activeRobots }
                updateWatchers(watched)
                notifications.notify(Notifications.MONITOR_NOTIFICATION_ID, Notifications.monitoring(this@MonitorService, watched.size))
                if (watched.isEmpty()) stopSelf()
            }
        }
    }

    private fun updateWatchers(robots: List<Robot>) {
        val ids = robots.map { it.id }.toSet()
        (watchers.keys - ids).forEach { watchers.remove(it)?.cancel() }
        robots.filter { it.id !in watchers }.forEach { robot ->
            watchers[robot.id] = lifecycleScope.launch { watchState(robot) }
            lifecycleScope.launch { pollEvents(robot) }
        }
    }

    private suspend fun watchState(robot: Robot) {
        var previous: StatusValue? = null
        var sawCleaning = false
        appContainer.session(robot).live.collect { live ->
            val state = live.state ?: return@collect
            updateCleaningNotification(robot, state)
            if (state.status.value == StatusValue.ERROR && previous != StatusValue.ERROR) notifyError(robot, state)
            if (previous != null && previous != state.status.value) RobotWidgetProvider.refresh(this)
            // A job started from the app may still read "docked" for a moment, so wait until it was seen running.
            if (QuickActions.isCleaning(state)) sawCleaning = true
            if (sawCleaning && !QuickActions.isCleaning(state) && robot.id in activeRobots) finishJob(robot)
            previous = state.status.value
        }
    }

    private fun updateCleaningNotification(robot: Robot, state: RobotState) {
        val id = Notifications.cleaningId(robot)
        if (QuickActions.isCleaning(state)) {
            notifications.notify(id, Notifications.cleaning(this, robot, state))
        } else {
            notifications.cancel(id)
        }
    }

    private fun notifyError(robot: Robot, state: RobotState) {
        val error = state.status.error
        val title = getString(R.string.notification_error_title, robot.displayName)
        val text = error?.message?.ifBlank { null } ?: getString(Labels.errorSubsystem(error?.subsystem))
        notifications.notify(Notifications.alertId(robot, "error"), Notifications.alert(this, robot, title, text))
    }

    private suspend fun pollEvents(robot: Robot) {
        val client = appContainer.session(robot).client
        while (lifecycleScope.isActive && watchers.containsKey(robot.id)) {
            val events = (client.events() as? ApiResult.Success)?.value.orEmpty()
            events.filter { !it.processed && it.id !in notifiedEvents && it.kind in ALERTING_EVENTS }.forEach { event ->
                notifiedEvents += event.id
                val title = getString(Labels.eventTitle(event.kind))
                val text = Labels.eventDescription(this, event)
                notifications.notify(Notifications.alertId(robot, event.id), Notifications.alert(this, robot, title, text))
            }
            delay(EVENT_POLL_INTERVAL_MS)
        }
    }

    private fun finishJob(robot: Robot) {
        activeRobots -= robot.id
        if (!appContainer.settings.backgroundMonitoring.value) {
            watchers.remove(robot.id)?.cancel()
            if (watchers.isEmpty()) stopSelf()
        }
    }

    companion object {
        private const val TAG = "MonitorService"
        private const val EXTRA_ROBOT_ID = "robot_id"
        private const val EVENT_POLL_INTERVAL_MS = 15 * 60 * 1000L
        private val ALERTING_EVENTS = setOf(
            RobotEvent.Kind.CONSUMABLE_DEPLETED,
            RobotEvent.Kind.DUST_BIN_FULL,
            RobotEvent.Kind.MISSING_RESOURCE,
            RobotEvent.Kind.ERROR_STATE,
        )

        /** Follows [robotId] until its current job ends. Silently skipped if Android forbids starting now. */
        fun watch(context: Context, robotId: String) = start(context, Intent(context, MonitorService::class.java).putExtra(EXTRA_ROBOT_ID, robotId))

        /** Starts monitoring all robots, used when the user enables background monitoring. */
        fun startMonitoring(context: Context) = start(context, Intent(context, MonitorService::class.java))

        private fun start(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
                AppLog.w(TAG, "Foreground service start not allowed right now", notAllowed)
            }
        }
    }
}
