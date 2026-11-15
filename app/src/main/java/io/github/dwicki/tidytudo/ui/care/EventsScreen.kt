package io.github.dwicki.tidytudo.ui.care

import io.github.dwicki.tidytudo.ui.components.TonalIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.ui.toLoadState
import io.github.dwicki.tidytudo.util.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Robot events in plain language with their actions (F-5.2). */
@Composable
fun EventsScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> EventsViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScaffold(title = stringResource(R.string.events_title), onBack = onBack, errors = viewModel.errors) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> if (current.value.isEmpty()) {
                MessageBox(icon = Icons.Outlined.NotificationsNone, title = stringResource(R.string.events_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.value, key = { it.id }) { event -> EventCard(event, viewModel::interact) }
                }
            }
        }
    }
}

/**
 * One message, in the same layout as the notices on the home screen. Messages the robot marks as
 * processed are dimmed and labelled, so open ones stand out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(event: RobotEvent, onInteract: (RobotEvent, String) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val done = event.processed
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (done) DONE_ALPHA else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = if (done || event.interactions.isEmpty()) 16.dp else 8.dp)) {
            TonalIcon(if (done) Icons.Outlined.Check else Icons.Outlined.WarningAmber)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Labels.eventTitle(event.kind)), style = MaterialTheme.typography.titleSmall)
                Text(Labels.eventDescription(context, event), style = MaterialTheme.typography.bodyMedium)
                val time = TimeFormat.timestamp(event.timestamp)
                Text(
                    if (done) stringResource(R.string.events_done, time) else time,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (!done && event.interactions.isNotEmpty()) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        // The first interaction is the expected answer; the others stay quiet.
                        event.interactions.drop(1).forEach { interaction ->
                            TextButton(onClick = { onInteract(event, interaction) }) { Text(stringResource(Labels.interaction(interaction))) }
                        }
                        event.interactions.first().let { interaction ->
                            FilledTonalButton(onClick = { onInteract(event, interaction) }) { Text(stringResource(Labels.interaction(interaction))) }
                        }
                    }
                }
            }
        }
    }
}

private const val DONE_ALPHA = 0.6f
