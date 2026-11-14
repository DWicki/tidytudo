package io.github.dwicki.tidytudo.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.dwicki.tidytudo.appContainer

/**
 * Returns a wrapper for actions that start a cleaning job. The first time, it asks for the
 * notification permission (needed for the progress notification, F-7.3) and then runs the action
 * whatever the answer; afterwards it runs the action directly. Asking in context explains itself,
 * unlike a prompt at app start.
 */
@Composable
fun rememberJobStarter(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val settings = context.appContainer.settings
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return remember(launcher) {
        { action ->
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted || settings.notificationPromptShown) {
                action()
            } else {
                settings.markNotificationPromptShown()
                pending = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
