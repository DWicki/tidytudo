package io.github.dwicki.tidytudo.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.dwicki.tidytudo.appContainer

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
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (enabled && !granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else apply(enabled)
        }
    }
}
