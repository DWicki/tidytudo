package io.github.dwicki.tidytudo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.service.CommandReceiver
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Home-screen widget for the selected robot (F-7.1): name, status, battery, start/pause and dock.
 * Plain RemoteViews instead of Glance to avoid another library (ADR-5).
 */
class RobotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                val robot = container.robotStore.selected()
                val state = robot?.let { (container.session(it).client.state() as? ApiResult.Success)?.value }
                val views = render(context, robot, state)
                widgetIds.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, robot: Robot?, state: RobotState?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_robot)
        views.setTextViewText(R.id.widget_name, robot?.displayName ?: context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_status, statusLine(context, robot, state))
        val busy = state?.status?.value?.isActive == true
        views.setImageViewResource(R.id.widget_toggle, if (busy) R.drawable.ic_pause else R.drawable.ic_play)
        views.setContentDescription(
            R.id.widget_toggle,
            context.getString(if (busy) R.string.action_pause else R.string.action_start),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_toggle, command(context, robot, CommandReceiver.ACTION_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_dock, command(context, robot, CommandReceiver.ACTION_DOCK))
        return views
    }

    private fun statusLine(context: Context, robot: Robot?, state: RobotState?): String = when {
        robot == null -> context.getString(R.string.widget_no_robot)
        state == null -> context.getString(R.string.status_offline)
        else -> listOfNotNull(
            context.getString(Labels.status(state.status.value)),
            state.battery?.let { context.getString(R.string.battery_percent, it.level) },
        ).joinToString(" · ")
    }

    private fun command(context: Context, robot: Robot?, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        CommandReceiver.intent(context, robot?.id, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** Asks all widget instances to redraw, e.g. after a command or a status change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RobotWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, RobotWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
