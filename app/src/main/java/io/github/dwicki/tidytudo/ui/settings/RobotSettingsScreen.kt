package io.github.dwicki.tidytudo.ui.settings

import io.github.dwicki.tidytudo.ui.components.AppListItem
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
                // Grouped by what they are about, instead of one long "Behaviour" list.
                val switches = current.value.switches
                val navigation = switches.filterKeys { it in NAVIGATION_SWITCHES }
                val camera = switches.filterKeys { it in CAMERA_SWITCHES }
                val robotSwitches = switches.filterKeys { it !in NAVIGATION_SWITCHES && it !in CAMERA_SWITCHES && it != AUTO_DRYING }
                if (navigation.isNotEmpty()) {
                    item { SwitchesCard(stringResource(R.string.settings_navigation_title), navigation, viewModel::setSwitch) }
                }
                if (current.value.carpetSensorMode != null) {
                    item { CarpetSensorCard(current.value.carpetSensorMode, current.value.carpetSensorModes, viewModel::setCarpetSensorMode) }
                }
                if (camera.isNotEmpty()) {
                    item { SwitchesCard(stringResource(R.string.settings_camera_title), camera, viewModel::setSwitch) }
                }
                if (AUTO_DRYING in switches || current.value.washTemperature != null || current.value.autoEmptyInterval != null) {
                    item {
                        DockSettingsCard(
                            settings = current.value,
                            onSwitch = viewModel::setSwitch,
                            onWashTemperature = viewModel::setWashTemperature,
                            onAutoEmptyInterval = viewModel::setAutoEmptyInterval,
                        )
                    }
                }
                if (robotSwitches.isNotEmpty()) {
                    item { SwitchesCard(stringResource(R.string.settings_robot_title), robotSwitches, viewModel::setSwitch) }
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
        // All segments as tall as the tallest, so a label on two lines ("Detach mop") keeps the row even.
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 8.dp),
        ) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    modifier = Modifier.fillMaxHeight(),
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    // No check mark: with four options it squeezed the chosen label onto two lines and
                    // made that one segment taller than the others. The fill marks the choice.
                    icon = {},
                    label = { Text(Labels.carpetSensorMode(context, mode), maxLines = 2, textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

/**
 * How the dock looks after the robot: settings that last. The one-tap dock actions (wash, dry,
 * empty) stay on the home screen, where they are used.
 */
@Composable
private fun DockSettingsCard(
    settings: RobotSettings,
    onSwitch: (String, Boolean) -> Unit,
    onWashTemperature: (String) -> Unit,
    onAutoEmptyInterval: (String) -> Unit,
) {
    val context = LocalContext.current
    ListCard(title = stringResource(R.string.settings_dock_title)) {
        settings.switches[AUTO_DRYING]?.let { enabled ->
            SwitchListItem(
                title = Labels.switchName(context, AUTO_DRYING),
                checked = enabled,
                onCheckedChange = { onSwitch(AUTO_DRYING, it) },
            )
        }
        settings.washTemperature?.let { value ->
            ChoiceRow(
                title = stringResource(R.string.settings_wash_temperature),
                value = value,
                options = settings.washTemperatures,
                label = { Labels.washTemperature(context, it) },
                onSelect = onWashTemperature,
            )
        }
        settings.autoEmptyInterval?.let { value ->
            ChoiceRow(
                title = stringResource(R.string.settings_auto_empty),
                value = value,
                options = settings.autoEmptyIntervals,
                label = { Labels.autoEmptyInterval(context, it) },
                onSelect = onAutoEmptyInterval,
            )
        }
    }
}

/** A setting with a few named values: the current one on the right, the rest in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(title: String, value: String, options: List<String>, label: (String) -> String, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val choices = options.ifEmpty { listOf(value) }
    AppListItem(
        title = title,
        onClick = { open = true },
        enabled = choices.size > 1,
        trailing = {
            Text(
                label(value),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Column(Modifier.selectableGroup().padding(bottom = 24.dp)) {
                choices.forEach { option ->
                    ListItem(
                        modifier = Modifier
                            .selectable(selected = option == value, role = Role.RadioButton) { onSelect(option); open = false }
                            .padding(horizontal = 8.dp),
                        headlineContent = { Text(label(option)) },
                        leadingContent = { RadioButton(selected = option == value, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchesCard(title: String, switches: Map<String, Boolean>, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    ListCard(title = title) {
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

private val NAVIGATION_SWITCHES = setOf(
    "ObstacleAvoidanceControlCapability",
    "PetObstacleAvoidanceControlCapability",
    "CollisionAvoidantNavigationControlCapability",
    "FloorMaterialDirectionAwareNavigationControlCapability",
    "CarpetModeControlCapability",
)
private val CAMERA_SWITCHES = setOf("ObstacleImagesCapability", "CameraLightControlCapability")
private const val AUTO_DRYING = "MopDockMopAutoDryingControlCapability"
