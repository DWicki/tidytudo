package io.github.dwicki.tidytudo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.Connection
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.components.ErrorSnackbarEffect
import io.github.dwicki.tidytudo.ui.components.rememberJobStarter
import io.github.dwicki.tidytudo.ui.robotViewModel

/** Daily control: status, main actions, rooms, presets, modes, dock (G-1: ≤ 2 taps to clean). */
@Composable
fun HomeScreen(robot: Robot, contentPadding: PaddingValues, snackbar: SnackbarHostState, onOpenMap: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> HomeViewModel(container, current) }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var presetDialog by rememberSaveable { mutableStateOf(false) }
    var presetToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    val startJob = rememberJobStarter()
    ErrorSnackbarEffect(viewModel.errors, snackbar)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshExtras() }
    LaunchedEffect(live.connection) {
        if (live.connection == Connection.Online) viewModel.refreshExtras()
    }

    val online = live.connection == Connection.Online
    val capabilities = live.capabilities
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { StatusHero(live) }
        item {
            ControlBar(
                state = live.state,
                enabled = online,
                canLocate = Capabilities.LOCATE in capabilities,
                busyAction = busy,
                onToggle = { startJob(viewModel::toggle) },
                onStop = viewModel::stop,
                onDock = viewModel::dock,
                onLocate = viewModel::locate,
            )
        }
        item {
            AttentionCards(live.state, extras.openEvents, extras.pendingMapChange, viewModel::interact, viewModel::answerMapChange)
        }
        item {
            RoomsCard(
                segments = extras.segments,
                selection = selection,
                maxIterations = extras.segmentProperties.iterationCount.max,
                enabled = online,
                onToggleRoom = viewModel::toggleRoom,
                onIterations = viewModel::setIterations,
                onClean = { startJob(viewModel::cleanSelectedRooms) },
                onSavePreset = { presetDialog = true },
                onOpenMap = onOpenMap,
            )
        }
        item {
            PresetsRow(
                presets = presets,
                busyAction = busy,
                enabled = online,
                onRun = { preset -> startJob { viewModel.runPreset(preset) } },
                onLongPress = { presetToDelete = it.id },
                onAdd = { presetDialog = true },
            )
        }
        item { ModesCard(live.state, extras.presetOptions, online, viewModel::setPreset) }
        item { DockActionsCard(live.state, capabilities, busy, online, viewModel::triggerDock) }
    }

    if (presetDialog) {
        PresetNameDialog(
            roomCount = selection.ids.size,
            onSave = { name -> viewModel.savePreset(name); presetDialog = false },
            onDismiss = { presetDialog = false },
        )
    }
    presets.firstOrNull { it.id == presetToDelete }?.let { preset ->
        DeletePresetDialog(
            preset = preset,
            onDelete = { viewModel.deletePreset(preset); presetToDelete = null },
            onDismiss = { presetToDelete = null },
        )
    }
}

@Composable
private fun PresetNameDialog(roomCount: Int, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (roomCount == 0) {
                        stringResource(R.string.preset_dialog_text_everything)
                    } else {
                        stringResource(R.string.preset_dialog_text_rooms)
                    },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_PRESET_NAME) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.preset_dialog_name)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeletePresetDialog(preset: CleaningPreset, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_delete_title, preset.name)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_PRESET_NAME = 30
