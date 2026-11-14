package io.github.dwicki.tidytudo.ui.schedules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.components.TimePickerDialog
import io.github.dwicki.tidytudo.util.LocalSchedule
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Editor for one timer, in local time; converted to UTC only when saving. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerEditorDialog(initial: ValetudoTimer, data: SchedulesData, onSave: (ValetudoTimer) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = initial.id.isEmpty()
    val local = if (isNew) LocalSchedule(DayOfWeek.entries.toSet(), LocalTime.of(DEFAULT_HOUR, 0)) else TimeFormat.toLocal(initial.dow, initial.hour, initial.minute)
    var time by remember { mutableStateOf(local.time) }
    var pickingTime by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(local.days) }
    var label by remember { mutableStateOf(initial.label.orEmpty()) }
    var rooms by remember { mutableStateOf(initial.action.params.segmentIds.orEmpty()) }
    var preActions by remember { mutableStateOf(initial.preActions.associate { it.type to it.params.value }) }
    val canChooseRooms = ValetudoTimer.SEGMENT_CLEANUP in data.properties.supportedActions && data.segments.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.schedules_new else R.string.schedules_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // A tappable time instead of TimeInput: TimeInput focuses itself and the keyboard hides the dialog.
                TextButton(onClick = { pickingTime = true }) {
                    Text(TimeFormat.time(context, time), style = MaterialTheme.typography.displayMedium)
                }
                DayPicker(days) { days = it }
                Spacer(Modifier.height(12.dp))
                if (canChooseRooms) {
                    SectionLabel(stringResource(R.string.schedules_rooms_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.segments.forEach { segment ->
                            FilterChip(
                                selected = segment.id in rooms,
                                onClick = { rooms = if (segment.id in rooms) rooms - segment.id else rooms + segment.id },
                                label = { Text(segment.name ?: segment.id) },
                            )
                        }
                    }
                    Text(stringResource(R.string.schedules_rooms_hint), style = MaterialTheme.typography.bodySmall)
                }
                data.preActionOptions.forEach { (type, options) ->
                    PreActionPicker(type, options, preActions[type]) { value ->
                        preActions = if (value == null) preActions - type else preActions + (type to value)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(MAX_LABEL) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.schedules_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = {
                    val (utcDays, hour, minute) = TimeFormat.toUtc(LocalSchedule(days, time))
                    val action = if (rooms.isEmpty()) {
                        ValetudoTimer.Action(ValetudoTimer.FULL_CLEANUP)
                    } else {
                        ValetudoTimer.Action(ValetudoTimer.SEGMENT_CLEANUP, ValetudoTimer.Params(segmentIds = rooms, iterations = 1))
                    }
                    onSave(
                        initial.copy(
                            label = label.ifBlank { null },
                            dow = utcDays,
                            hour = hour,
                            minute = minute,
                            action = action,
                            preActions = preActions.map { (type, value) -> ValetudoTimer.PreAction(type, ValetudoTimer.PreActionParams(value)) },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingTime) {
        TimePickerDialog(initial = time, onConfirm = { time = it; pickingTime = false }, onDismiss = { pickingTime = false })
    }
}

@Composable
private fun DayPicker(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = Locale.getDefault()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
            )
        }
    }
}

@Composable
private fun PreActionPicker(type: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    val title = when (type) {
        ValetudoTimer.PRE_FAN_SPEED -> R.string.home_mode_fan
        ValetudoTimer.PRE_WATER_USAGE -> R.string.home_mode_water
        else -> R.string.home_mode_operation
    }
    Spacer(Modifier.height(12.dp))
    SectionLabel(stringResource(title))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.schedules_keep_current)) })
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(Labels.preset(context, option)) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val DEFAULT_HOUR = 9
private const val MAX_LABEL = 24
