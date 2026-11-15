package io.github.dwicki.tidytudo.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.ui.components.MAP_MAIN_BUTTON
import io.github.dwicki.tidytudo.ui.components.MapRoundButton
import io.github.dwicki.tidytudo.ui.components.areaText
import io.github.dwicki.tidytudo.ui.components.durationText
import io.github.dwicki.tidytudo.ui.home.JobProgress

/**
 * The map's controls as small round buttons over the map, so the map keeps the screen (the robot's
 * state is in the top bar). Left: fit, or while choosing rooms clear / passes / rename. Middle: the
 * one main action (clean all, clean the picked rooms, pause, resume) with dock and stop beside it
 * while a job runs. Right: the cleaning mode.
 */
@Composable
fun MapControls(
    status: StatusValue,
    online: Boolean,
    busy: Boolean,
    progress: JobProgress?,
    rooms: RoomsControls?,
    onFit: () -> Unit,
    onMain: () -> Unit,
    onDock: () -> Unit,
    onStop: () -> Unit,
    modeButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasJob = status.isActive || status == StatusValue.PAUSED
    val label = when {
        hasJob -> listOfNotNull(
            progress?.seconds?.takeIf { it >= SECONDS_PER_MINUTE }?.let { durationText(it) },
            progress?.areaCm2?.takeIf { it >= CM2_PER_M2 }?.let { areaText(it) },
        ).joinToString(" · ").ifEmpty { null }
        rooms != null && rooms.count == 0 -> stringResource(R.string.map_rooms_hint)
        else -> null
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = label != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = LABEL_ALPHA),
                modifier = Modifier.padding(bottom = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(label.orEmpty(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rooms != null && rooms.count > 0) {
                    MapRoundButton(Icons.Outlined.Close, stringResource(R.string.home_rooms_clear), rooms.onClear)
                    if (rooms.maxIterations > 1) {
                        MapRoundButton(
                            icon = null,
                            description = stringResource(R.string.map_passes_button, rooms.iterations),
                            onClick = { rooms.onIterations(rooms.iterations % rooms.maxIterations + 1) },
                        ) { Text("×${rooms.iterations}", style = MaterialTheme.typography.titleSmall) }
                    }
                    if (rooms.count == 1 && rooms.onRename != null) {
                        MapRoundButton(Icons.Outlined.Edit, stringResource(R.string.map_rename_room), rooms.onRename)
                    }
                } else {
                    MapRoundButton(Icons.Outlined.FitScreen, stringResource(R.string.map_fit), onFit)
                }
            }
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status != StatusValue.DOCKED && status != StatusValue.UNKNOWN && hasJob) {
                    MapRoundButton(Icons.Filled.Home, stringResource(R.string.action_dock), onDock, enabled = online)
                }
                MainButton(status, rooms, online, busy, onMain)
                if (hasJob) MapRoundButton(Icons.Filled.Stop, stringResource(R.string.action_stop), onStop, enabled = online)
            }
            Box(Modifier.align(Alignment.CenterEnd)) { modeButton() }
        }
    }
}

/** Rooms mode: what the left buttons need. [onRename] is null when the robot cannot rename rooms. */
class RoomsControls(
    val count: Int,
    val iterations: Int,
    val maxIterations: Int,
    val onClear: () -> Unit,
    val onIterations: (Int) -> Unit,
    val onRename: (() -> Unit)?,
)

@Composable
private fun MainButton(status: StatusValue, rooms: RoomsControls?, online: Boolean, busy: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (icon, description) = when {
        status.isActive -> Icons.Filled.Pause to stringResource(R.string.action_pause)
        status == StatusValue.PAUSED -> Icons.Filled.PlayArrow to stringResource(R.string.action_resume)
        rooms != null -> Icons.Filled.PlayArrow to pluralStringResource(R.plurals.home_clean_rooms, rooms.count, rooms.count)
        else -> Icons.Filled.PlayArrow to stringResource(R.string.action_clean_all)
    }
    // Choosing rooms with none picked yet: nothing to start.
    val enabled = online && !(rooms != null && rooms.count == 0 && !status.isActive && status != StatusValue.PAUSED)
    val button = @Composable {
        MapRoundButton(
            icon = icon,
            description = description,
            onClick = onClick,
            enabled = enabled,
            busy = busy,
            size = MAP_MAIN_BUTTON,
            container = colors.primary,
            contentColor = colors.onPrimary,
        )
    }
    val count = rooms?.count ?: 0
    if (count > 0 && !status.isActive && status != StatusValue.PAUSED) {
        BadgedBox(badge = { Badge(containerColor = colors.tertiary, contentColor = colors.onTertiary) { Text("$count") } }) { button() }
    } else {
        button()
    }
}

private const val LABEL_ALPHA = 0.92f
private const val SECONDS_PER_MINUTE = 60L
private const val CM2_PER_M2 = 10_000L
