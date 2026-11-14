package io.github.dwicki.tidytudo.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.dwicki.tidytudo.ui.components.SectionCard
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
                items(current.value, key = { "${it.consumable.type}/${it.consumable.subType}" }) { item ->
                    ConsumableCard(item, onReset = { resetCandidate = item.consumable })
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

@Composable
private fun ConsumableCard(item: ConsumableItem, onReset: () -> Unit) {
    val context = LocalContext.current
    val consumable = item.consumable
    val fraction = item.fraction
    val low = fraction != null && fraction < LOW_THRESHOLD
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Labels.consumable(context, consumable.type, consumable.subType), style = MaterialTheme.typography.titleMedium)
                Text(
                    remainingText(consumable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.consumables_reset)) }
        }
        if (fraction != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
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

private const val LOW_THRESHOLD = 0.1f
private const val MINUTES_PER_HOUR = 60
