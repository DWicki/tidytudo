package io.github.dwicki.tidytudo.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.ui.components.rememberMonitoringSwitch

/**
 * Asked once, right after the first robot was added: the robot discards a run's path, obstacles
 * and photos a few hours after cleaning, so only background recording guarantees a complete
 * history (F-8.6). The trade-offs are stated so the choice is informed.
 */
@Composable
fun RecordingOffer() {
    val settings = LocalContext.current.appContainer.settings
    var visible by remember { mutableStateOf(!settings.monitoringOffered && !settings.backgroundMonitoring.value) }
    val setMonitoring = rememberMonitoringSwitch()
    if (!visible) return
    fun answer(enable: Boolean) {
        settings.markMonitoringOffered()
        visible = false
        if (enable) setMonitoring(true)
    }
    AlertDialog(
        onDismissRequest = { answer(false) },
        icon = { Icon(Icons.Outlined.History, contentDescription = null) },
        title = { Text(stringResource(R.string.recording_offer_title)) },
        text = { Text(stringResource(R.string.recording_offer_text)) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.recording_offer_yes)) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.recording_offer_no)) } },
    )
}
