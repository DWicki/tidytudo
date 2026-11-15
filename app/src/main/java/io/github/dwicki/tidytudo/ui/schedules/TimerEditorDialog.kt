package io.github.dwicki.tidytudo.ui.schedules

import io.github.dwicki.tidytudo.ui.components.aboveKeyboard
import io.github.dwicki.tidytudo.ui.components.TextInputDialogProperties
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
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.components.TimePickerDialog
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Editor for one timer, in local time; converted to UTC only when saving. */
@Composable
fun TimerEditorDialog(initial: ValetudoTimer, data: SchedulesData, onSave: (ValetudoTimer) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(TimerDraft.from(initial)) }
    var pickingTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = TextInputDialogProperties,
        modifier = Modifier.aboveKeyboard(),
        title = { Text(stringResource(if (initial.id.isEmpty()) R.string.schedules_new else R.string.schedules_edit)) },
        text = { TimerEditorContent(draft, data, onChange = { draft = it }, onPickTime = { pickingTime = true }) },
        confirmButton = {
            TextButton(enabled = draft.days.isNotEmpty(), onClick = { onSave(draft.applyTo(initial)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingTime) {
        TimePickerDialog(
            initial = draft.time,
            onConfirm = { draft = draft.copy(time = it); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun TimerEditorContent(draft: TimerDraft, data: SchedulesData, onChange: (TimerDraft) -> Unit, onPickTime: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // A tappable time instead of TimeInput: TimeInput focuses itself and the keyboard hides the dialog.
        TextButton(onClick = onPickTime) {
            Text(TimeFormat.time(context, draft.time), style = MaterialTheme.typography.displayMedium)
        }
        DayPicker(draft.days) { onChange(draft.copy(days = it)) }
        Spacer(Modifier.height(12.dp))
        if (ValetudoTimer.SEGMENT_CLEANUP in data.properties.supportedActions && data.segments.isNotEmpty()) {
            RoomPicker(data.segments, draft.rooms) { onChange(draft.copy(rooms = it)) }
        }
        data.preActionOptions.forEach { (type, options) ->
            PreActionPicker(type, options, draft.preActions[type]) { value ->
                onChange(draft.copy(preActions = if (value == null) draft.preActions - type else draft.preActions + (type to value)))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft.label,
            onValueChange = { onChange(draft.copy(label = it.take(MAX_LABEL))) },
            singleLine = true,
            label = { Text(stringResource(R.string.schedules_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomPicker(segments: List<MapSegment>, selected: List<String>, onChange: (List<String>) -> Unit) {
    SectionLabel(stringResource(R.string.schedules_rooms_label))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            FilterChip(
                selected = segment.id in selected,
                onClick = { onChange(if (segment.id in selected) selected - segment.id else selected + segment.id) },
                label = { Text(segment.name ?: segment.id) },
            )
        }
    }
    Text(stringResource(R.string.schedules_rooms_hint), style = MaterialTheme.typography.bodySmall)
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

private const val MAX_LABEL = 24
