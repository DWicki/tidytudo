package io.github.dwicki.tidytudo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.components.SectionCard

/** Fan speed, water usage and operation mode as rows of chips (F-2.5); only reported presets. */
@Composable
fun ModesCard(state: RobotState?, options: Map<String, List<String>>, enabled: Boolean, onSelect: (String, String) -> Unit) {
    if (options.values.all { it.isEmpty() }) return
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.home_modes_title)) {
        val rows = listOf(
            Triple(Capabilities.OPERATION_MODE, R.string.home_mode_operation, state?.operationMode),
            Triple(Capabilities.FAN_SPEED, R.string.home_mode_fan, state?.fanSpeed),
            Triple(Capabilities.WATER_USAGE, R.string.home_mode_water, state?.waterUsage),
        )
        rows.forEach { (capability, label, current) ->
            val values = options[capability].orEmpty()
            if (values.isEmpty()) return@forEach
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    FilterChip(
                        selected = value == current,
                        enabled = enabled,
                        onClick = { onSelect(capability, value) },
                        label = { Text(Labels.preset(context, value)) },
                        leadingIcon = if (value == current) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Room selection with iteration count and one big "clean" button (F-4.1, list variant). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomsCard(
    segments: List<MapSegment>,
    selection: RoomSelection,
    maxIterations: Int,
    enabled: Boolean,
    onToggleRoom: (String) -> Unit,
    onIterations: (Int) -> Unit,
    onClean: () -> Unit,
    onSavePreset: () -> Unit,
    onOpenMap: () -> Unit,
) {
    if (segments.isEmpty()) return
    SectionCard(
        modifier = Modifier.animateContentSize(),
        title = stringResource(R.string.home_rooms_title),
        trailing = {
            TextButton(onClick = onOpenMap) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_rooms_on_map))
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { segment ->
                FilterChip(
                    selected = segment.id in selection.ids,
                    onClick = { onToggleRoom(segment.id) },
                    label = { Text(segment.name ?: stringResource(R.string.room_unnamed, segment.id)) },
                )
            }
        }
        AnimatedVisibility(visible = selection.ids.isNotEmpty()) {
            Column(Modifier.padding(top = 12.dp)) {
                IterationPicker(selection.iterations, maxIterations, onIterations)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onClean, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.home_clean_rooms, selection.ids.size, selection.ids.size))
                    }
                    TextButton(onClick = onSavePreset) { Text(stringResource(R.string.home_save_preset)) }
                }
            }
        }
    }
}

@Composable
fun IterationPicker(iterations: Int, max: Int, onChange: (Int) -> Unit) {
    if (max < 2) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text(stringResource(R.string.home_passes), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        (1..max).forEach { count ->
            FilterChip(
                selected = count == iterations,
                onClick = { onChange(count) },
                label = { Text("×$count") },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Saved one-tap jobs (F-4.6). Tap runs; long-press deletes after confirmation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetsRow(
    presets: List<CleaningPreset>,
    busyAction: String?,
    enabled: Boolean,
    onRun: (CleaningPreset) -> Unit,
    onLongPress: (CleaningPreset) -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.home_presets_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            presets.forEach { preset ->
                Card(
                    modifier = Modifier
                        .width(150.dp)
                        .combinedClickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = { onRun(preset) },
                            onLongClick = { onLongPress(preset) },
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.height(12.dp))
                        Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (busyAction == preset.id) {
                                stringResource(R.string.home_preset_starting)
                            } else {
                                presetSummary(preset)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            OutlinedCard(onClick = onAdd, modifier = Modifier.width(150.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_preset_new), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.home_preset_new_hint), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun presetSummary(preset: CleaningPreset): String =
    if (preset.segmentIds.isEmpty()) {
        stringResource(R.string.home_preset_everything)
    } else {
        pluralStringResource(R.plurals.home_preset_rooms, preset.segmentIds.size, preset.segmentIds.size)
    }

/** Dock buttons for docks that can empty, wash or dry on demand (F-2.8). */
@Composable
fun DockActionsCard(state: RobotState?, capabilities: Set<String>, busyAction: String?, enabled: Boolean, onTrigger: (String) -> Unit) {
    val triggers = Capabilities.dockTriggers.filter { it in capabilities }
    val context = LocalContext.current
    val dockStatus = state?.dockStatus
    if (triggers.isEmpty() && state?.dockComponents.isNullOrEmpty() && state?.attachments.isNullOrEmpty()) return
    SectionCard(
        title = stringResource(R.string.home_dock_title),
        trailing = {
            if (dockStatus != null) {
                Text(Labels.dockStatus(context, dockStatus), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
    ) {
        state?.attachments?.forEach { attachment ->
            StatusRow(
                label = Labels.attachment(context, attachment.type),
                value = stringResource(if (attachment.attached) R.string.attachment_attached else R.string.attachment_missing),
                attention = false,
            )
        }
        state?.dockComponents?.forEach { component ->
            StatusRow(
                label = Labels.dockComponent(context, component.type),
                value = Labels.dockComponentValue(context, component.value),
                attention = component.needsAttention,
            )
        }
        if (triggers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                triggers.forEach { capability ->
                    AssistChip(
                        onClick = { onTrigger(capability) },
                        enabled = enabled && busyAction != capability,
                        label = { Text(stringResource(Labels.dockTrigger(capability))) },
                        leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, attention: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Things that need the user: robot error, missing attachments, events, a pending map change (F-2.7, F-5.2, F-5.5). */
@Composable
fun AttentionCards(
    state: RobotState?,
    events: List<RobotEvent>,
    pendingMapChange: Boolean,
    onInteract: (RobotEvent, String) -> Unit,
    onMapChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state?.status?.error?.let { error ->
            AttentionCard(
                icon = Icons.Outlined.ErrorOutline,
                container = MaterialTheme.colorScheme.errorContainer,
                title = stringResource(Labels.errorSubsystem(error.subsystem)),
                text = error.message.ifBlank { stringResource(R.string.home_error_no_message) },
            )
        }
        if (pendingMapChange) {
            AttentionCard(
                icon = Icons.Outlined.Map,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                title = stringResource(R.string.event_pending_map_change),
                text = stringResource(R.string.event_pending_map_change_text),
                actions = listOf(
                    stringResource(R.string.event_action_accept) to { onMapChange(true) },
                    stringResource(R.string.event_action_reject) to { onMapChange(false) },
                ),
            )
        }
        events.filter { it.kind != RobotEvent.Kind.PENDING_MAP_CHANGE }.forEach { event ->
            AttentionCard(
                icon = Icons.Outlined.WarningAmber,
                container = MaterialTheme.colorScheme.secondaryContainer,
                title = stringResource(Labels.eventTitle(event.kind)),
                text = Labels.eventDescription(context, event),
                actions = event.interactions.map { interaction ->
                    stringResource(Labels.interaction(interaction)) to { onInteract(event, interaction) }
                },
            )
        }
    }
}

@Composable
private fun AttentionCard(
    icon: ImageVector,
    container: Color,
    title: String,
    text: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Card(colors = CardDefaults.cardColors(containerColor = container), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = if (actions.isEmpty()) 16.dp else 4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (actions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
                    }
                }
            }
        }
    }
}
