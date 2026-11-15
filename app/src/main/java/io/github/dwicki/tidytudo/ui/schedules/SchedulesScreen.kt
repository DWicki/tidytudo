package io.github.dwicki.tidytudo.ui.schedules

import java.time.temporal.WeekFields
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.components.SectionCard
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
    var confirming by remember { mutableStateOf<Pair<ValetudoTimer, Boolean>?>(null) }

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
            is LoadState.Loaded -> TimerList(
                data = current.value,
                padding = padding,
                onToggle = viewModel::setEnabled,
                onEdit = { editing = it },
                onRun = { confirming = it to true },
                onDelete = { confirming = it to false },
            )
        }
    }

    confirming?.let { (timer, run) ->
        // Running starts the robot and deleting cannot be undone, so both ask first.
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (run) R.string.schedules_run_confirm else R.string.schedules_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    if (run) viewModel.runNow(timer) else viewModel.delete(timer)
                    confirming = null
                }) { Text(stringResource(if (run) R.string.schedules_run_now else R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
private fun TimerList(
    data: SchedulesData,
    padding: PaddingValues,
    onToggle: (ValetudoTimer, Boolean) -> Unit,
    onEdit: (ValetudoTimer) -> Unit,
    onRun: (ValetudoTimer) -> Unit,
    onDelete: (ValetudoTimer) -> Unit,
) {
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
                onToggle = { onToggle(timer, it) },
                onRun = { onRun(timer) },
                onDelete = { onDelete(timer) },
            )
        }
    }
}

/**
 * One schedule: time, the weekdays as seven small circles, what it does, and its switch. Run now
 * and Delete sit in a menu; a schedule that is off is dimmed.
 */
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
    var menu by remember { mutableStateOf(false) }
    SectionCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (timer.enabled) 1f else DISABLED_ALPHA), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(TimeFormat.time(context, local.time), style = MaterialTheme.typography.headlineMedium)
                WeekdayDots(local.days)
                Text(
                    listOfNotNull(timer.label?.takeIf { it.isNotBlank() }, actionText(timer, data), preActionText(timer))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = timer.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedules_run_now)) },
                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                        onClick = { menu = false; onRun() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** The week in the locale's order, active days filled; TalkBack reads the days as text. */
@Composable
private fun WeekdayDots(days: Set<DayOfWeek>) {
    val locale = Locale.getDefault()
    val first = WeekFields.of(locale).firstDayOfWeek
    val week = (0L until 7L).map { first.plus(it) }
    val description = daysText(days)
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { day ->
            val active = day in days
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.55f

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

/** "Vacuum, then mop · Max" for the presets a timer applies before it starts. */
@Composable
private fun preActionText(timer: ValetudoTimer): String? {
    val context = LocalContext.current
    return timer.preActions.joinToString(", ") { Labels.preset(context, it.params.value) }.ifEmpty { null }
}

@Composable
fun daysText(days: Set<DayOfWeek>): String {
    if (days.size == 7) return stringResource(R.string.schedules_every_day)
    val locale = Locale.getDefault()
    return days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}
