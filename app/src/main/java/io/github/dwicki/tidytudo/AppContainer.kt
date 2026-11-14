package io.github.dwicki.tidytudo

import android.content.Context
import io.github.dwicki.tidytudo.api.RobotSession
import io.github.dwicki.tidytudo.api.SessionManager
import io.github.dwicki.tidytudo.data.AppSettings
import io.github.dwicki.tidytudo.data.CredentialVault
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.RobotStore
import io.github.dwicki.tidytudo.discovery.MdnsDiscovery
import io.github.dwicki.tidytudo.discovery.SubnetScanner
import io.github.dwicki.tidytudo.service.MonitorService
import io.github.dwicki.tidytudo.shortcuts.RoomShortcut
import io.github.dwicki.tidytudo.shortcuts.Shortcuts
import io.github.dwicki.tidytudo.widget.RobotWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency wiring (ADR-1). One instance per process, owned by [TidytudoApp]. */
class AppContainer(context: Context) {
    /** Application context only, so holding it here cannot leak an activity. */
    private val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault = CredentialVault(context)
    val robotStore = RobotStore(File(context.filesDir, "robots.json"))
    val settings = AppSettings(context)
    val sessions = SessionManager(vault, appScope)
    val mdnsDiscovery = MdnsDiscovery(appContext)
    val subnetScanner = SubnetScanner(appContext)

    fun session(robot: Robot): RobotSession = sessions.session(robot)

    fun selectedSession(): Pair<Robot, RobotSession>? = robotStore.selected()?.let { it to session(it) }

    /** Called after a cleaning job was started, so its progress shows in a notification (F-7.3). */
    fun followJob(robot: Robot) {
        MonitorService.watch(appContext, robot.id)
        RobotWidgetProvider.refresh(appContext)
    }

    fun publishShortcuts(robot: Robot, rooms: List<RoomShortcut>) = Shortcuts.publish(appContext, robot, rooms)

    fun startMonitoring() = MonitorService.startMonitoring(appContext)
}
