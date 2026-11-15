package io.github.dwicki.tidytudo.ui.cleaning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.components.ErrorSnackbarEffect
import io.github.dwicki.tidytudo.ui.components.ROW_HORIZONTAL_PADDING
import io.github.dwicki.tidytudo.ui.components.SwitchListItem
import io.github.dwicki.tidytudo.ui.components.TonalIcon
import io.github.dwicki.tidytudo.ui.robotViewModel

/**
 * The cleaning mode in one row ("Vacuum and mop" / "Suction: High · Water: Medium"); tapping it opens
 * [CleaningModeSheet]. The same row sits under the main button on Home and in the map's panel, so
 * the mode is always in sight where a cleaning is started. Draws nothing on robots without presets.
 */
@Composable
fun CleaningModeRow(
    robot: Robot,
    snackbar: SnackbarHostState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val viewModel = robotViewModel(robot) { app, current -> CleaningModeViewModel(app, current) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(viewModel.errors, snackbar)
    if (state.isEmpty) return
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    val headline = headline(state)
    val details = details(state)
    val changeLabel = stringResource(R.string.cleaning_mode_change)
    val spoken = stringResource(R.string.cleaning_mode_spoken, listOfNotNull(headline, details).joinToString(", "))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                if (enabled) onClick(label = changeLabel) { open = true; true }
            },
    ) {
        Row(
            Modifier
                .clickable(enabled = enabled) { open = true }
                .heightIn(min = 64.dp)
                .padding(start = ROW_HORIZONTAL_PADDING, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TonalIcon(modeIcon(state.operationMode))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details != null) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
    if (open) {
        CleaningModeSheet(
            state = state,
            onPreset = viewModel::setPreset,
            onSwitch = viewModel::setSwitch,
            onDismiss = { open = false },
        )
    }
}

/** The mode's name; on a robot without modes, the suction instead ("Suction: High"). */
@Composable
private fun headline(state: CleaningModeState): String {
    val context = LocalContext.current
    state.operationMode?.let { return Labels.preset(context, it) }
    return details(state) ?: stringResource(R.string.home_modes_title)
}

/** "Suction: High · Water: Medium", leaving out what the current mode does not use. */
@Composable
private fun details(state: CleaningModeState): String? {
    val context = LocalContext.current
    val parts = buildList {
        if (state.usesSuction) state.current[Capabilities.FAN_SPEED]?.takeIf { Capabilities.FAN_SPEED in state.options }?.let {
            add(context.getString(R.string.cleaning_mode_setting, context.getString(R.string.home_mode_fan), Labels.preset(context, it)))
        }
        if (state.usesWater) state.current[Capabilities.WATER_USAGE]?.takeIf { Capabilities.WATER_USAGE in state.options }?.let {
            add(context.getString(R.string.cleaning_mode_setting, context.getString(R.string.home_mode_water), Labels.preset(context, it)))
        }
    }
    // Without a mode the suction is already the headline.
    if (state.operationMode == null && parts.size <= 1) return null
    return parts.joinToString(" · ").ifEmpty { null }
}

fun modeIcon(mode: String?): ImageVector = when (mode) {
    CleaningModeState.MODE_MOP -> Icons.Outlined.WaterDrop
    CleaningModeState.MODE_VACUUM -> Icons.Outlined.Air
    "vacuum_then_mop" -> Icons.AutoMirrored.Outlined.TrendingFlat
    else -> Icons.Outlined.CleaningServices
}

/**
 * Mode as tiles, then suction and water, then the mop switches. Every change is sent at once; there
 * is nothing to save. Settings the chosen mode does not use stay visible but disabled, with a reason,
 * so the sheet does not jump around while switching modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleaningModeSheet(
    state: CleaningModeState,
    onPreset: (String, String) -> Unit,
    onSwitch: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Full height at once: a half-open sheet that grows while you tap a tile swallows the tap.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.home_modes_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 8.dp),
            )
            state.options[Capabilities.OPERATION_MODE]?.takeIf { it.isNotEmpty() }?.let { modes ->
                ModeTiles(modes, state.operationMode) { onPreset(Capabilities.OPERATION_MODE, it) }
            }
            state.options[Capabilities.FAN_SPEED]?.takeIf { it.isNotEmpty() }?.let { values ->
                PresetChoice(
                    title = stringResource(R.string.home_mode_fan),
                    values = values,
                    selected = state.current[Capabilities.FAN_SPEED],
                    enabled = state.usesSuction,
                    unusedReason = stringResource(R.string.cleaning_mode_suction_unused),
                ) { onPreset(Capabilities.FAN_SPEED, it) }
            }
            state.options[Capabilities.WATER_USAGE]?.takeIf { it.isNotEmpty() }?.let { values ->
                PresetChoice(
                    title = stringResource(R.string.home_mode_water),
                    values = values,
                    selected = state.current[Capabilities.WATER_USAGE],
                    enabled = state.usesWater,
                    unusedReason = stringResource(R.string.cleaning_mode_water_unused),
                ) { onPreset(Capabilities.WATER_USAGE, it) }
            }
            if (state.mopSwitches.isNotEmpty()) {
                val context = LocalContext.current
                SectionTitle(stringResource(R.string.cleaning_mode_mop_title))
                CleaningModeViewModel.MOP_SWITCHES.forEach { capability ->
                    val checked = state.mopSwitches[capability] ?: return@forEach
                    SwitchListItem(
                        title = Labels.switchName(context, capability),
                        supporting = if (state.usesWater) Labels.switchDescription(context, capability) else stringResource(R.string.cleaning_mode_mop_unused),
                        checked = checked,
                        enabled = state.usesWater,
                        onCheckedChange = { onSwitch(capability, it) },
                    )
                }
            }
        }
    }
}

/** Two or three modes side by side; four as a 2 × 2 grid, which leaves room for "Erst saugen, dann wischen". */
@Composable
private fun ModeTiles(modes: List<String>, selected: String?, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val perRow = if (modes.size <= 3) modes.size else 2
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 8.dp).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    ModeTile(Labels.preset(context, mode), modeIcon(mode), mode == selected, Modifier.weight(1f)) { onSelect(mode) }
                }
                // Keep tile widths equal when the last row is short.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeTile(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier
            .heightIn(min = 76.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A check as well as the colour, so the choice does not rely on colour alone.
            Icon(if (selected) Icons.Outlined.CheckCircle else icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Up to four values as a segmented row; more (some robots have six suction levels) as wrapping chips. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetChoice(
    title: String,
    values: List<String>,
    selected: String?,
    enabled: Boolean,
    unusedReason: String,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    SectionTitle(title, if (enabled) null else unusedReason)
    val content = Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING).alpha(if (enabled) 1f else DISABLED_ALPHA)
    if (values.size <= SEGMENTED_MAX) {
        SingleChoiceSegmentedButtonRow(content) {
            values.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                    label = { Text(Labels.preset(context, value), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    } else {
        FlowRow(content.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(Labels.preset(context, value)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String? = null) {
    Column(Modifier.padding(start = ROW_HORIZONTAL_PADDING, end = ROW_HORIZONTAL_PADDING, top = 16.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val SEGMENTED_MAX = 4
private const val DISABLED_ALPHA = 0.6f
