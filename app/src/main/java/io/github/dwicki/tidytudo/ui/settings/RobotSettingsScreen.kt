package io.github.dwicki.tidytudo.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.DoNotDisturb
import io.github.dwicki.tidytudo.api.model.TimeOfDay
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.LocalTime

@Composable
fun RobotSettingsScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> RobotSettingsViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScaffold(title = stringResource(R.string.settings_title), onBack = onBack, errors = viewModel.errors) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                current.value.doNotDisturb?.let { dnd -> item { DoNotDisturbCard(dnd, viewModel::setDoNotDisturb) } }
                if (current.value.volume != null || current.value.voicePack != null) {
                    item { SoundCard(current.value, viewModel::setVolume, viewModel::testSound) }
                }
                if (current.value.carpetSensorMode != null) {
                    item { CarpetSensorCard(current.value.carpetSensorMode, current.value.carpetSensorModes, viewModel::setCarpetSensorMode) }
                }
                if (current.value.switches.isNotEmpty()) {
                    item { SwitchesCard(current.value.switches, viewModel::setSwitch) }
                }
            }
        }
    }
}

@Composable
private fun DoNotDisturbCard(dnd: DoNotDisturb, onChange: (DoNotDisturb) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val start = TimeFormat.utcTimeToLocal(dnd.start.hour, dnd.start.minute)
    val end = TimeFormat.utcTimeToLocal(dnd.end.hour, dnd.end.minute)
    SectionCard(title = stringResource(R.string.settings_dnd_title), trailing = {
        Switch(checked = dnd.enabled, onCheckedChange = { onChange(dnd.copy(enabled = it)) })
    }) {
        Text(stringResource(R.string.settings_dnd_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.settings_dnd_from, TimeFormat.time(context, start))) }
            TextButton(onClick = { editing = false }) { Text(stringResource(R.string.settings_dnd_until, TimeFormat.time(context, end))) }
        }
    }
    editing?.let { isStart ->
        TimePickerDialog(
            initial = if (isStart) start else end,
            onConfirm = { picked ->
                val utc = TimeFormat.localTimeToUtc(picked).let { TimeOfDay(it.hour, it.minute) }
                onChange(if (isStart) dnd.copy(start = utc) else dnd.copy(end = utc))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SoundCard(settings: RobotSettings, onVolume: (Int) -> Unit, onTest: () -> Unit) {
    SectionCard(title = stringResource(R.string.settings_sound_title)) {
        settings.volume?.let { volume ->
            // Local slider value while dragging; the robot only receives the final value.
            var position by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    onValueChangeFinished = { onVolume(position.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                if (settings.canTestSound) {
                    IconButton(onClick = onTest) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = stringResource(R.string.settings_test_sound))
                    }
                }
            }
            Text(stringResource(R.string.settings_volume, position.toInt()), style = MaterialTheme.typography.bodySmall)
        }
        settings.voicePack?.let { pack ->
            Text(
                stringResource(R.string.settings_voice_pack, pack.currentLanguage.uppercase()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CarpetSensorCard(current: String, modes: List<String>, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.settings_carpet_sensor_title)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.ifEmpty { listOf(current) }.forEach { mode ->
                FilterChip(selected = mode == current, onClick = { onSelect(mode) }, label = { Text(Labels.carpetSensorMode(context, mode)) })
            }
        }
    }
}

@Composable
private fun SwitchesCard(switches: Map<String, Boolean>, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.settings_behaviour_title)) {
        Column {
            switches.forEach { (capability, enabled) ->
                ListItem(
                    modifier = Modifier.clickableRow { onChange(capability, !enabled) },
                    headlineContent = { Text(Labels.switchName(context, capability)) },
                    supportingContent = Labels.switchDescription(context, capability)?.let { { Text(it) } },
                    trailingContent = { Switch(checked = enabled, onCheckedChange = { onChange(capability, it) }) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
    }
}
