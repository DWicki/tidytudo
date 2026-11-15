package io.github.dwicki.tidytudo.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.util.NotificationAccess

/**
 * Returns a function that switches background monitoring on or off. Switching on first asks for
 * the notification permission, because the monitoring service must show a notification and its
 * alerts are the point of it; if the user declines, monitoring stays off.
 */
@Composable
fun rememberMonitoringSwitch(): (Boolean) -> Unit {
    val context = LocalContext.current
    val container = context.appContainer
    fun apply(enabled: Boolean) {
        container.settings.setBackgroundMonitoring(enabled)
        if (enabled) container.startMonitoring()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) apply(true)
    }
    return remember(launcher) {
        { enabled ->
            if (enabled && !NotificationAccess.isGranted(context)) launcher.launch(NotificationAccess.PERMISSION) else apply(enabled)
        }
    }
}
