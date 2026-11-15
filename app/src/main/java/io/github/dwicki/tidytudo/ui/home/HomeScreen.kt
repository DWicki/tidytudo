package io.github.dwicki.tidytudo.ui.home

import io.github.dwicki.tidytudo.ui.components.aboveKeyboard
import io.github.dwicki.tidytudo.ui.components.TextInputDialogProperties
import androidx.compose.ui.platform.LocalContext
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
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.components.ErrorSnackbarEffect
import io.github.dwicki.tidytudo.ui.components.rememberJobStarter
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.ui.rooms.RenameRoomDialog

/** Daily control: status, main actions, rooms, presets, modes, dock (G-1: ≤ 2 taps to clean). */
@Composable
fun HomeScreen(
    robot: Robot,
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    onOpenMap: () -> Unit,
    onOpenRooms: () -> Unit,
) {
    val viewModel = robotViewModel(robot) { container, current -> HomeViewModel(container, current) }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var presetDialog by rememberSaveable { mutableStateOf(false) }
    var presetToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var roomToRename by rememberSaveable { mutableStateOf<String?>(null) }

    val startJob = rememberJobStarter()
    ErrorSnackbarEffect(viewModel.errors, snackbar)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshExtras() }
    LaunchedEffect(live.connection) {
        if (live.connection == Connection.Online) viewModel.refreshExtras()
    }

    HomeContent(
        viewModel = viewModel,
        live = live,
        extras = extras,
        selection = selection,
        busy = busy,
        presets = presets,
        contentPadding = contentPadding,
        startJob = startJob,
        onOpenMap = onOpenMap,
        onOpenRooms = onOpenRooms,
        onRenameRoom = { roomToRename = it.id },
        onNewPreset = { presetDialog = true },
        onDeletePreset = { presetToDelete = it.id },
    )
    extras.segments.firstOrNull { it.id == roomToRename }?.let { room ->
        RenameRoomDialog(
            currentName = room.name,
            onSave = { name -> viewModel.renameRoom(room.id, name); roomToRename = null },
            onDismiss = { roomToRename = null },
        )
    }
    PresetDialogs(
        viewModel = viewModel,
        presets = presets,
        showNameDialog = presetDialog,
        roomCount = selection.ids.size,
        presetToDelete = presetToDelete,
        onClose = { presetDialog = false; presetToDelete = null },
    )
}

/** The scrolling card list; state lives in [HomeScreen] and the view model. */
@Composable
private fun HomeContent(
    viewModel: HomeViewModel,
    live: LiveState,
    extras: HomeExtras,
    selection: RoomSelection,
    busy: String?,
    presets: List<CleaningPreset>,
    contentPadding: PaddingValues,
    startJob: (() -> Unit) -> Unit,
    onOpenMap: () -> Unit,
    onOpenRooms: () -> Unit,
    onRenameRoom: (MapSegment) -> Unit,
    onNewPreset: () -> Unit,
    onDeletePreset: (CleaningPreset) -> Unit,
) {
    val online = live.connection == Connection.Online
    val capabilities = live.capabilities
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val roomsHint by viewModel.roomsHint.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                live = live,
                progress = progress,
                canLocate = Capabilities.LOCATE in capabilities,
                busyAction = busy,
                selectedRooms = selection.ids.size,
                onToggle = { startJob(viewModel::toggle) },
                onCleanRooms = { startJob(viewModel::cleanSelectedRooms) },
                onStop = viewModel::stop,
                onDock = viewModel::dock,
                onLocate = viewModel::locate,
            )
        }
        if (hasAttention(live.state, extras.openEvents, extras.pendingMapChange)) {
            item {
                AttentionCards(live.state, extras.openEvents, extras.pendingMapChange, viewModel::interact, viewModel::answerMapChange)
            }
        }
        item {
            PresetsRow(
                presets = presets,
                busyAction = busy,
                enabled = online,
                onRun = { preset -> startJob { viewModel.runPreset(preset) } },
                onLongPress = onDeletePreset,
                onAdd = onNewPreset,
            )
        }
        item {
            RoomsCard(
                segments = extras.segments,
                selection = selection,
                maxIterations = extras.segmentProperties.iterationCount.max,
                showHint = roomsHint,
                onToggleRoom = viewModel::toggleRoom,
                onIterations = viewModel::setIterations,
                onClearSelection = viewModel::clearSelection,
                onSavePreset = onNewPreset,
                onOpenMap = onOpenMap,
                canRename = Capabilities.SEGMENT_RENAME in capabilities,
                onOpenRooms = onOpenRooms,
                onRenameRoom = onRenameRoom,
            )
        }
        item { CleaningSettings(live.state, extras.presetOptions, online, viewModel::setPreset) }
        item { DockCard(live.state, capabilities, busy, online, viewModel::triggerDock) }
    }
}

@Composable
private fun PresetDialogs(
    viewModel: HomeViewModel,
    presets: List<CleaningPreset>,
    showNameDialog: Boolean,
    roomCount: Int,
    presetToDelete: String?,
    onClose: () -> Unit,
) {
    if (showNameDialog) {
        PresetNameDialog(roomCount = roomCount, onSave = { name -> viewModel.savePreset(name); onClose() }, onDismiss = onClose)
    }
    presets.firstOrNull { it.id == presetToDelete }?.let { preset ->
        DeletePresetDialog(preset = preset, onDelete = { viewModel.deletePreset(preset); onClose() }, onDismiss = onClose)
    }
}

@Composable
private fun PresetNameDialog(roomCount: Int, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = TextInputDialogProperties,
        modifier = Modifier.aboveKeyboard(),
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
