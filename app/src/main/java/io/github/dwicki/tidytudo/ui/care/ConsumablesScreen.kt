package io.github.dwicki.tidytudo.ui.care

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.MoreVert
import io.github.dwicki.tidytudo.ui.components.TonalIcon
import io.github.dwicki.tidytudo.ui.components.AppListItem
import io.github.dwicki.tidytudo.ui.components.ListCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.Consumable
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.robotViewModel

/** Brushes, filters and sensors with remaining life and reset (F-5.1). */
@Composable
fun ConsumablesScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> ConsumablesViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var resetCandidate by remember { mutableStateOf<Consumable?>(null) }

    DetailScaffold(title = stringResource(R.string.consumables_title), onBack = onBack, errors = viewModel.errors) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ListCard {
                        current.value.forEach { item -> ConsumableRow(item, onReset = { resetCandidate = item.consumable }) }
                    }
                }
            }
        }
    }

    resetCandidate?.let { consumable ->
        val name = Labels.consumable(LocalContext.current, consumable.type, consumable.subType)
        AlertDialog(
            onDismissRequest = { resetCandidate = null },
            title = { Text(stringResource(R.string.consumables_reset_title, name)) },
            text = { Text(stringResource(R.string.consumables_reset_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(consumable); resetCandidate = null }) {
                    Text(stringResource(R.string.consumables_reset))
                }
            },
            dismissButton = { TextButton(onClick = { resetCandidate = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * One part: icon, name, time left and a bar. Reset sits in a menu, because it should only follow
 * replacing or cleaning the part, not be the most prominent thing on the screen.
 */
@Composable
private fun ConsumableRow(item: ConsumableItem, onReset: () -> Unit) {
    val context = LocalContext.current
    val consumable = item.consumable
    val fraction = item.fraction
    val low = item.isLow
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    AppListItem(
        title = Labels.consumable(context, consumable.type, consumable.subType),
        supporting = remainingText(consumable),
        supportingColor = if (low) colors.error else colors.onSurfaceVariant,
        leading = {
            TonalIcon(
                consumableIcon(consumable.type),
                container = if (low) colors.errorContainer else colors.secondaryContainer,
                content = if (low) colors.onErrorContainer else colors.onSecondaryContainer,
            )
        },
        below = fraction?.let {
            {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                    color = if (low) colors.error else colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.consumables_reset)) },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                        onClick = { menu = false; onReset() },
                    )
                }
            }
        },
    )
}

private fun consumableIcon(type: String): ImageVector = when (type) {
    "brush" -> Icons.Outlined.CleaningServices
    "filter" -> Icons.Outlined.FilterAlt
    "mop" -> Icons.Outlined.WaterDrop
    "bin", "dustbag" -> Icons.Outlined.Delete
    "cleaning", "sensor" -> Icons.Outlined.Sensors
    else -> Icons.Outlined.Build
}

@Composable
private fun remainingText(consumable: Consumable): String {
    val value = consumable.remaining.value
    return when (consumable.remaining.unit) {
        "percent" -> stringResource(R.string.consumables_percent_left, value)
        "minutes" -> if (value >= MINUTES_PER_HOUR) {
            val hours = value / MINUTES_PER_HOUR
            pluralStringResource(R.plurals.consumables_hours_left, hours, hours)
        } else {
            pluralStringResource(R.plurals.consumables_minutes_left, value, value)
        }
        else -> "$value ${consumable.remaining.unit}"
    }
}

private const val MINUTES_PER_HOUR = 60
