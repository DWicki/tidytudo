package io.github.dwicki.tidytudo.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SchedulesScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> SchedulesViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ValetudoTimer?>(null) }

    DetailScaffold(
        title = stringResource(R.string.schedules_title),
        onBack = onBack,
        errors = viewModel.errors,
        floatingAction = {
            if (state is LoadState.Loaded) {
                ExtendedFloatingActionButton(
                    onClick = { editing = ValetudoTimer() },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.schedules_add)) },
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> TimerList(current.value, padding, viewModel, onEdit = { editing = it })
        }
    }

    val data = (state as? LoadState.Loaded)?.value
    val timer = editing
    if (data != null && timer != null) {
        TimerEditorDialog(
            initial = timer,
            data = data,
            onSave = { viewModel.save(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimerList(data: SchedulesData, padding: PaddingValues, viewModel: SchedulesViewModel, onEdit: (ValetudoTimer) -> Unit) {
    if (data.timers.isEmpty()) {
        MessageBox(icon = Icons.Outlined.Schedule, title = stringResource(R.string.schedules_empty), text = stringResource(R.string.schedules_empty_text))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(data.timers, key = { it.id }) { timer ->
            TimerCard(
                timer = timer,
                data = data,
                onEdit = { onEdit(timer) },
                onToggle = { viewModel.setEnabled(timer, it) },
                onRun = { viewModel.runNow(timer) },
                onDelete = { viewModel.delete(timer) },
            )
        }
    }
}

@Composable
private fun TimerCard(
    timer: ValetudoTimer,
    data: SchedulesData,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val local = TimeFormat.toLocal(timer.dow, timer.hour, timer.minute)
    SectionCard(modifier = Modifier.clickableRow(onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(TimeFormat.time(context, local.time), style = MaterialTheme.typography.displaySmall)
                Text(daysText(local.days), style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(timer.label?.takeIf { it.isNotBlank() }, actionText(timer, data)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = timer.enabled, onCheckedChange = onToggle)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.align(Alignment.End)) {
            IconButton(onClick = onRun) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.schedules_run_now)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
private fun actionText(timer: ValetudoTimer, data: SchedulesData): String {
    val ids = timer.action.params.segmentIds.orEmpty()
    return if (timer.action.type == ValetudoTimer.SEGMENT_CLEANUP && ids.isNotEmpty()) {
        val names = ids.mapNotNull { id -> data.segments.firstOrNull { it.id == id }?.name }
        names.joinToString(", ").ifEmpty { pluralStringResource(R.plurals.home_preset_rooms, ids.size, ids.size) }
    } else {
        stringResource(R.string.home_preset_everything)
    }
}

@Composable
fun daysText(days: Set<DayOfWeek>): String {
    if (days.size == 7) return stringResource(R.string.schedules_every_day)
    val locale = Locale.getDefault()
    return days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}
