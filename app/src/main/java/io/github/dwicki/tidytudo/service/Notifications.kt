package io.github.dwicki.tidytudo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.MainActivity

/** Builds every notification the app shows. All are local; there is no push service (C-1). */
object Notifications {
    const val CHANNEL_CLEANING = "cleaning"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_MONITOR = "monitor"
    const val MONITOR_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_CLEANING,
                    context.getString(R.string.channel_cleaning),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.channel_monitor),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            ),
        )
    }

    fun monitoring(context: Context, robotCount: Int): Notification =
        Notification.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_monitoring_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.notification_monitoring_text, robotCount, robotCount),
            )
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()

    fun cleaning(context: Context, robot: Robot, state: RobotState): Notification {
        val status = context.getString(Labels.status(state.status.value))
        val battery = state.battery?.let { context.getString(R.string.battery_percent, it.level) }
        val busy = state.status.value.isActive
        val toggleLabel = context.getString(if (busy) R.string.action_pause else R.string.action_resume)
        return Notification.Builder(context, CHANNEL_CLEANING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(robot.displayName)
            .setContentText(listOfNotNull(status, battery).joinToString(" · "))
            .setContentIntent(openApp(context))
            .setOngoing(state.status.value != StatusValue.PAUSED)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(action(context, robot.id, CommandReceiver.ACTION_TOGGLE, if (busy) R.drawable.ic_pause else R.drawable.ic_play, toggleLabel))
            .addAction(action(context, robot.id, CommandReceiver.ACTION_DOCK, R.drawable.ic_dock, context.getString(R.string.action_dock)))
            .build()
    }

    fun alert(context: Context, robot: Robot, title: String, text: String): Notification =
        Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(robot.displayName)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

    fun cleaningId(robot: Robot): Int = CLEANING_ID_BASE + Math.floorMod(robot.id.hashCode(), ID_RANGE)
    fun alertId(robot: Robot, key: String): Int = ALERT_ID_BASE + Math.floorMod((robot.id + key).hashCode(), ID_RANGE)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, robotId: String, command: String, icon: Int, label: String): Notification.Action {
        val intent = CommandReceiver.intent(context, robotId, command)
        val pending = PendingIntent.getBroadcast(
            context,
            (robotId + command).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, icon), label, pending).build()
    }

    private const val CLEANING_ID_BASE = 10_000
    private const val ALERT_ID_BASE = 20_000
    private const val ID_RANGE = 5_000
}
