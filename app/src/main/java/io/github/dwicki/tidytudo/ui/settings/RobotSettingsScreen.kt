package io.github.dwicki.tidytudo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
import io.github.dwicki.tidytudo.ui.components.ListCard
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.ROW_HORIZONTAL_PADDING
import io.github.dwicki.tidytudo.ui.components.SwitchListItem
import io.github.dwicki.tidytudo.ui.components.TimePickerDialog
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.LocalTime
import java.util.Locale

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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

/** Quiet hours: a switch row and two time tiles, dimmed while quiet hours are off. */
@Composable
private fun DoNotDisturbCard(dnd: DoNotDisturb, onChange: (DoNotDisturb) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val start = TimeFormat.utcTimeToLocal(dnd.start.hour, dnd.start.minute)
    val end = TimeFormat.utcTimeToLocal(dnd.end.hour, dnd.end.minute)
    ListCard {
        SwitchListItem(
            title = stringResource(R.string.settings_dnd_title),
            supporting = stringResource(R.string.settings_dnd_text),
            icon = Icons.Outlined.Bedtime,
            checked = dnd.enabled,
            onCheckedChange = { onChange(dnd.copy(enabled = it)) },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeTile(stringResource(R.string.settings_dnd_from_label), TimeFormat.time(context, start), dnd.enabled, Modifier.weight(1f)) {
                editing = true
            }
            TimeTile(stringResource(R.string.settings_dnd_until_label), TimeFormat.time(context, end), dnd.enabled, Modifier.weight(1f)) {
                editing = false
            }
        }
    }
    editing?.let { isStart ->
        TimePickerDialog(
            initial = if (isStart) start else end,
            onConfirm = { picked: LocalTime ->
                val utc = TimeFormat.localTimeToUtc(picked).let { TimeOfDay(it.hour, it.minute) }
                onChange(if (isStart) dnd.copy(start = utc) else dnd.copy(end = utc))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** A tappable time with a small label above it; reads as a value to change, not as a link. */
@Composable
private fun TimeTile(label: String, time: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = if (active) colors.onSurface else colors.onSurfaceVariant,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(time, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SoundCard(settings: RobotSettings, onVolume: (Int) -> Unit, onTest: () -> Unit) {
    ListCard(title = stringResource(R.string.settings_sound_title)) {
        Column(Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 4.dp)) {
            settings.volume?.let { volume ->
                // Local slider value while dragging; the robot only receives the final value.
                var position by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_volume_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.battery_percent, position.toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                    )
                }
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
            }
            settings.voicePack?.let { pack ->
                // "en" → "English" in the app's language; unknown codes stay as they are.
                val language = Locale.forLanguageTag(pack.currentLanguage).getDisplayLanguage(Locale.getDefault())
                    .ifBlank { pack.currentLanguage.uppercase() }
                Text(
                    stringResource(R.string.settings_voice_pack, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** One choice out of a few: a segmented control, like the passes on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarpetSensorCard(current: String, modes: List<String>, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val options = modes.ifEmpty { listOf(current) }
    ListCard(title = stringResource(R.string.settings_carpet_sensor_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 8.dp)) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(Labels.carpetSensorMode(context, mode), maxLines = 2, textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

@Composable
private fun SwitchesCard(switches: Map<String, Boolean>, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    ListCard(title = stringResource(R.string.settings_behaviour_title)) {
        switches.forEach { (capability, enabled) ->
            SwitchListItem(
                title = Labels.switchName(context, capability),
                supporting = Labels.switchDescription(context, capability),
                checked = enabled,
                onCheckedChange = { onChange(capability, it) },
            )
        }
    }
}
