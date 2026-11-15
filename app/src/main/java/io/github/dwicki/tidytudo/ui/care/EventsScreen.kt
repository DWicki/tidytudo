package io.github.dwicki.tidytudo.ui.care

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
import io.github.dwicki.tidytudo.ui.components.SectionCard
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

@Composable
private fun EventCard(event: RobotEvent, onInteract: (RobotEvent, String) -> Unit) {
    val context = LocalContext.current
    SectionCard(title = stringResource(Labels.eventTitle(event.kind))) {
        Text(Labels.eventDescription(context, event), style = MaterialTheme.typography.bodyMedium)
        Text(
            TimeFormat.timestamp(event.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!event.processed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                event.interactions.forEach { interaction ->
                    TextButton(onClick = { onInteract(event, interaction) }) { Text(stringResource(Labels.interaction(interaction))) }
                }
            }
        }
    }
}
