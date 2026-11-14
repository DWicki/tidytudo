package io.github.dwicki.tidytudo.ui.map

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Grid4x4
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.map.EditableShape
import io.github.dwicki.tidytudo.ui.components.rememberJobStarter
import io.github.dwicki.tidytudo.ui.home.IterationPicker

val MapMode.label: Int
    @StringRes get() = when (this) {
        MapMode.VIEW -> R.string.map_mode_view
        MapMode.ROOMS -> R.string.map_mode_rooms
        MapMode.ZONES -> R.string.map_mode_zones
        MapMode.GO_TO -> R.string.map_mode_go_to
        MapMode.RESTRICTIONS -> R.string.map_mode_restrictions
    }

val MapMode.icon: ImageVector
    get() = when (this) {
        MapMode.VIEW -> Icons.Outlined.Visibility
        MapMode.ROOMS -> Icons.Outlined.Grid4x4
        MapMode.ZONES -> Icons.Outlined.CropFree
        MapMode.GO_TO -> Icons.Outlined.Explore
        MapMode.RESTRICTIONS -> Icons.Outlined.Block
    }

/** Bottom panel whose content follows the map mode; animates between modes. */
@Composable
fun ModePanel(state: MapUiState, onAddShape: (EditableShape.Kind) -> Unit, actions: MapViewModel, modifier: Modifier = Modifier) {
    if (state.mode == MapMode.VIEW) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedContent(targetState = state.mode, label = "mapMode") { mode ->
            Column(Modifier.padding(20.dp)) {
                when (mode) {
                    MapMode.ROOMS -> RoomsPanel(state, actions)
                    MapMode.ZONES -> ZonesPanel(state, onAddShape, actions)
                    MapMode.GO_TO -> GoToPanel(state, actions)
                    MapMode.RESTRICTIONS -> RestrictionsPanel(state, onAddShape, actions)
                    MapMode.VIEW -> Unit
                }
            }
        }
    }
}

@Composable
private fun RoomsPanel(state: MapUiState, actions: MapViewModel) {
    val count = state.selectedSegments.size
    Hint(if (count == 0) stringResource(R.string.map_rooms_hint) else pluralStringResource(R.plurals.map_rooms_selected, count, count))
    if (count > 0) {
        IterationPicker(state.iterations, state.segmentProperties.iterationCount.max, actions::setIterations)
        val startJob = rememberJobStarter()
        PrimaryAction(pluralStringResource(R.plurals.home_clean_rooms, count, count), !state.sending) { startJob(actions::cleanSelectedRooms) }
    }
}

@Composable
private fun ZonesPanel(state: MapUiState, onAddShape: (EditableShape.Kind) -> Unit, actions: MapViewModel) {
    val max = state.zoneProperties.zoneCount.max
    Hint(stringResource(R.string.map_zones_hint, max))
    ShapeToolbar(
        tools = listOf(Triple(R.string.map_add_zone, Icons.Outlined.CropFree, EditableShape.Kind.ZONE)),
        canUndo = state.zones.isNotEmpty(),
        onAdd = onAddShape,
        onUndo = actions::removeLastShape,
    )
    if (state.zones.isNotEmpty()) {
        IterationPicker(state.iterations, state.zoneProperties.iterationCount.max, actions::setIterations)
        val startJob = rememberJobStarter()
        PrimaryAction(pluralStringResource(R.plurals.map_clean_zones, state.zones.size, state.zones.size), !state.sending) { startJob(actions::cleanZones) }
    }
}

@Composable
private fun GoToPanel(state: MapUiState, actions: MapViewModel) {
    Hint(stringResource(if (state.goToTarget == null) R.string.map_go_to_hint else R.string.map_go_to_ready))
    if (state.goToTarget != null) PrimaryAction(stringResource(R.string.map_go_to_action), !state.sending, actions::goToTarget)
}

@Composable
private fun RestrictionsPanel(state: MapUiState, onAddShape: (EditableShape.Kind) -> Unit, actions: MapViewModel) {
    Hint(stringResource(R.string.map_restrictions_hint))
    val tools = buildList {
        add(Triple(R.string.map_add_no_go, Icons.Outlined.Block, EditableShape.Kind.NO_GO))
        if ("mop" in state.supportedRestrictionTypes) add(Triple(R.string.map_add_no_mop, Icons.Outlined.WaterDrop, EditableShape.Kind.NO_MOP))
        add(Triple(R.string.map_add_wall, Icons.Outlined.HorizontalRule, EditableShape.Kind.WALL))
    }
    ShapeToolbar(tools, canUndo = state.restrictions.isNotEmpty(), onAdd = onAddShape, onUndo = actions::removeLastShape)
    if (state.restrictionsDirty) PrimaryAction(stringResource(R.string.map_save_restrictions), !state.sending, actions::saveRestrictions)
}

@Composable
private fun ShapeToolbar(
    tools: List<Triple<Int, ImageVector, EditableShape.Kind>>,
    canUndo: Boolean,
    onAdd: (EditableShape.Kind) -> Unit,
    onUndo: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { (label, icon, kind) ->
                AssistChip(
                    onClick = { onAdd(kind) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.map_undo))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
}
