package io.github.dwicki.tidytudo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.ui.Labels

/** Inset of section titles that sit outside a card, so they line up with the card titles' optical edge. */
private val SECTION_TITLE_INSET = 4.dp

/** A section title above content that is not wrapped in a card (presets, cleaning settings). */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = SECTION_TITLE_INSET, bottom = 8.dp),
    )
}

/** A card with a title row of fixed height, so every card's title sits at the same distance from its content. */
@Composable
private fun HomeCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing()
            }
            Column(Modifier.padding(end = 12.dp)) { content() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Presets
// ---------------------------------------------------------------------------------------------

/** Saved one-tap jobs (F-4.6) as pills. Tap runs; long-press deletes after confirmation. Hidden while there are none. */
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
    if (presets.isEmpty()) return
    Column {
        SectionTitle(stringResource(R.string.home_presets_title))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val summary = presetSummary(preset)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { contentDescription = "${preset.name}, $summary" }
                        .combinedClickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = { onRun(preset) },
                            onLongClick = { onLongPress(preset) },
                        ),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (busyAction == preset.id) stringResource(R.string.home_preset_starting) else preset.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Surface(
                onClick = onAdd,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(48.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_preset_new), style = MaterialTheme.typography.labelLarge)
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

// ---------------------------------------------------------------------------------------------
// Rooms
// ---------------------------------------------------------------------------------------------

/**
 * Room selection (F-4.1, list variant). Selected rooms turn the main button into "Clean n rooms";
 * while rooms are selected, the header shows how many and offers to clear them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomsCard(
    segments: List<MapSegment>,
    selection: RoomSelection,
    maxIterations: Int,
    showHint: Boolean,
    onToggleRoom: (String) -> Unit,
    onIterations: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onSavePreset: () -> Unit,
    onOpenMap: () -> Unit,
    canRename: Boolean,
    onOpenRooms: () -> Unit,
    onRenameRoom: (MapSegment) -> Unit,
) {
    if (segments.isEmpty()) return
    val selected = selection.ids.size
    HomeCard(
        modifier = Modifier.animateContentSize(),
        title = if (selected == 0) {
            stringResource(R.string.home_rooms_title)
        } else {
            pluralStringResource(R.plurals.home_rooms_selected, selected, selected)
        },
        trailing = {
            if (selected == 0) {
                IconButton(onClick = onOpenMap) {
                    Icon(Icons.Outlined.Map, contentDescription = stringResource(R.string.home_rooms_on_map), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canRename) {
                    IconButton(onClick = onOpenRooms) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rooms_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_rooms_clear))
                }
            }
        },
    ) {
        if (showHint && selected == 0) {
            Text(
                stringResource(if (canRename) R.string.home_rooms_hint_rename else R.string.home_rooms_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        // Chips are 40dp high with 48dp touch targets; the extra 8dp is the visual gap between rows.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { segment ->
                RoomChip(
                    name = segment.name ?: stringResource(R.string.room_unnamed, segment.id),
                    selected = segment.id in selection.ids,
                    onClick = { onToggleRoom(segment.id) },
                    onLongClick = if (canRename) ({ onRenameRoom(segment) }) else null,
                )
            }
        }
        AnimatedVisibility(visible = selected > 0) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IterationPicker(selection.iterations, maxIterations, onIterations)
                AssistChip(
                    onClick = onSavePreset,
                    label = { Text(stringResource(R.string.home_save_preset)) },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/**
 * A filter chip that can also be long-pressed (to rename the room). Material's FilterChip has no
 * long-press, so this draws a filled chip itself: neutral fill when off, primary container when on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomChip(name: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val longPressLabel = stringResource(R.string.rooms_rename_title)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .combinedClickable(
                role = Role.Checkbox,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longPressLabel,
            ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Number of passes as a segmented control; hidden when the robot only supports one. Also used on the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IterationPicker(iterations: Int, max: Int, onChange: (Int) -> Unit) {
    if (max < 2) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_passes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            (1..max).forEach { count ->
                SegmentedButton(
                    selected = count == iterations,
                    onClick = { onChange(count) },
                    shape = SegmentedButtonDefaults.itemShape(index = count - 1, count = max),
                    label = { Text("×$count") },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cleaning settings
// ---------------------------------------------------------------------------------------------

/**
 * Operation mode, suction and water (F-2.5) as three tiles showing the current value; each opens
 * a picker. Suction is hidden in mop-only mode and water in vacuum-only mode, where they do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleaningSettings(state: RobotState?, options: Map<String, List<String>>, enabled: Boolean, onSelect: (String, String) -> Unit) {
    val context = LocalContext.current
    val mode = state?.operationMode
    val settings = listOf(
        Setting(Capabilities.OPERATION_MODE, R.string.home_mode_operation, Icons.Outlined.CleaningServices, mode),
        Setting(Capabilities.FAN_SPEED, R.string.home_mode_fan, Icons.Outlined.Air, state?.fanSpeed),
        Setting(Capabilities.WATER_USAGE, R.string.home_mode_water, Icons.Outlined.WaterDrop, state?.waterUsage),
    ).filter { options[it.capability].orEmpty().isNotEmpty() }
        .filterNot { it.capability == Capabilities.FAN_SPEED && mode == MODE_MOP }
        .filterNot { it.capability == Capabilities.WATER_USAGE && mode == MODE_VACUUM }
    if (settings.isEmpty()) return
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Column {
        SectionTitle(stringResource(R.string.home_modes_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.forEach { setting ->
                Card(
                    onClick = { open = setting.capability },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(setting.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(setting.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            setting.current?.let { Labels.preset(context, it) } ?: "–",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    settings.firstOrNull { it.capability == open }?.let { setting ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Text(
                stringResource(setting.label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options[setting.capability].orEmpty().forEach { value ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.RadioButton) { onSelect(setting.capability, value); open = null },
                    headlineContent = { Text(Labels.preset(context, value)) },
                    leadingContent = { RadioButton(selected = value == setting.current, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class Setting(val capability: String, val label: Int, val icon: ImageVector, val current: String?)

private const val MODE_MOP = "mop"
private const val MODE_VACUUM = "vacuum"

// ---------------------------------------------------------------------------------------------
// Dock
// ---------------------------------------------------------------------------------------------

/**
 * The dock (F-2.8) in one line while all is well; it opens to show attachments, tanks and the dock
 * buttons, and opens by itself when something needs attention.
 */
@Composable
fun DockCard(state: RobotState?, capabilities: Set<String>, busyAction: String?, enabled: Boolean, onTrigger: (String) -> Unit) {
    val triggers = Capabilities.dockTriggers.filter { it in capabilities }
    if (triggers.isEmpty() && state?.dockComponents.isNullOrEmpty() && state?.attachments.isNullOrEmpty()) return
    val context = LocalContext.current
    val problems = state?.dockComponents.orEmpty().filter { it.needsAttention }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded || problems.isNotEmpty()
    val activity = state?.dockStatus?.takeIf { it != "idle" }?.let { Labels.dockStatus(context, it) }
    val summary = when {
        problems.isNotEmpty() -> problems.joinToString(", ") {
            "${Labels.dockComponent(context, it.type)}: ${Labels.dockComponentValue(context, it.value)}"
        }
        activity != null -> activity
        else -> stringResource(R.string.home_dock_all_ok)
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
            leadingContent = {
                Icon(
                    if (problems.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (problems.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            headlineContent = { Text(stringResource(R.string.home_dock_title), style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(summary, color = if (problems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            },
            trailingContent = {
                if (problems.isEmpty()) Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
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
                                leadingIcon = { Icon(triggerIcon(capability), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun triggerIcon(capability: String): ImageVector = when (capability) {
    Capabilities.AUTO_EMPTY_TRIGGER -> Icons.Outlined.DeleteSweep
    Capabilities.MOP_CLEAN_TRIGGER -> Icons.Outlined.WaterDrop
    else -> Icons.Outlined.WbSunny
}

@Composable
private fun StatusRow(label: String, value: String, attention: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Attention
// ---------------------------------------------------------------------------------------------

/** True if [AttentionCards] would show anything, so the screen can skip the item (and its spacing). */
fun hasAttention(state: RobotState?, events: List<RobotEvent>, pendingMapChange: Boolean): Boolean =
    state?.status?.error != null || pendingMapChange || events.any { it.kind != RobotEvent.Kind.PENDING_MAP_CHANGE }

/** Things that need the user: robot error, events, a pending map change (F-2.7, F-5.2, F-5.5). */
@Composable
fun AttentionCards(
    state: RobotState?,
    events: List<RobotEvent>,
    pendingMapChange: Boolean,
    onInteract: (RobotEvent, String) -> Unit,
    onMapChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state?.status?.error?.let { error ->
            AttentionCard(
                icon = Icons.Outlined.ErrorOutline,
                container = colors.errorContainer,
                content = colors.onErrorContainer,
                title = stringResource(Labels.errorSubsystem(error.subsystem)),
                text = error.message.ifBlank { stringResource(R.string.home_error_no_message) },
            )
        }
        if (pendingMapChange) {
            AttentionCard(
                icon = Icons.Outlined.Map,
                container = colors.surfaceContainerHigh,
                content = colors.onSurface,
                iconTint = colors.primary,
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
                container = colors.surfaceContainerHigh,
                content = colors.onSurface,
                iconTint = colors.primary,
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
    content: Color,
    title: String,
    text: String,
    iconTint: Color = content,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = if (actions.isEmpty()) 16.dp else 12.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (actions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        // The first action is the expected answer; the others stay quiet.
                        actions.drop(1).forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
                        actions.first().let { (label, action) -> FilledTonalButton(onClick = action) { Text(label) } }
                    }
                }
            }
        }
    }
}
