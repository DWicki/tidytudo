package io.github.dwicki.tidytudo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue

/**
 * Main actions (F-2.3, F-2.4). Only actions that make sense right now are offered: no "dock"
 * while docked, "resume" instead of "start" for a paused job, "stop" only when there is a job.
 */
@Composable
fun ControlBar(
    state: RobotState?,
    enabled: Boolean,
    canLocate: Boolean,
    busyAction: String?,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onLocate: () -> Unit,
) {
    val status = state?.status?.value ?: StatusValue.UNKNOWN
    val active = status.isActive
    val hasJob = active || status == StatusValue.PAUSED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onToggle,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(64.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            val label = when {
                active -> R.string.action_pause
                status == StatusValue.PAUSED -> R.string.action_resume
                else -> R.string.action_start
            }
            AnimatedContent(targetState = active, label = "toggle") { pausing ->
                ButtonContent(
                    icon = if (pausing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = stringResource(label),
                    busy = busyAction == HomeViewModel.ACTION_TOGGLE,
                )
            }
        }
        if (status != StatusValue.DOCKED && hasJob) {
            // Four controls share the row while a job runs; a round dock button leaves room for "Resume".
            RoundAction(Icons.Filled.Home, stringResource(R.string.action_dock), enabled, onDock)
        } else if (status != StatusValue.DOCKED) {
            FilledTonalButton(
                onClick = onDock,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(64.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                ButtonContent(Icons.Filled.Home, stringResource(R.string.action_dock), busyAction == HomeViewModel.ACTION_DOCK)
            }
        }
        if (hasJob) {
            RoundAction(Icons.Filled.Stop, stringResource(R.string.action_stop), enabled, onStop)
        }
        if (canLocate) {
            RoundAction(Icons.Outlined.NotificationsActive, stringResource(R.string.action_locate), enabled, onLocate)
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String, busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
        Icon(icon, contentDescription = label)
    }
}
