package io.github.dwicki.tidytudo.ui.home

import io.github.dwicki.tidytudo.data.RoomSelection
import io.github.dwicki.tidytudo.api.model.RobotEvent
import androidx.activity.compose.BackHandler
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
    var presetMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var presetToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var presetToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var roomToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var eventToReset by rememberSaveable { mutableStateOf<String?>(null) }

    // Back first undoes a room selection instead of leaving the app.
    BackHandler(enabled = selection.ids.isNotEmpty()) { viewModel.clearSelection() }

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
        onPresetMenu = { presetMenu = it.id },
        onStop = { confirmStop = true },
        onInteract = { event, interaction ->
            // Resetting a counter cannot be undone, so it asks first, as on the Consumables screen.
            if (interaction == RobotEvent.INTERACTION_RESET) eventToReset = event.id else viewModel.interact(event, interaction)
        },
    )
    extras.segments.firstOrNull { it.id == roomToRename }?.let { room ->
        RenameRoomDialog(
            currentName = room.name,
            onSave = { name -> viewModel.renameRoom(room.id, name); roomToRename = null },
            onDismiss = { roomToRename = null },
        )
    }
    HomeDialogs(
        viewModel = viewModel,
        presets = presets,
        roomCount = selection.ids.size,
        showNewPreset = presetDialog,
        presetMenu = presetMenu,
        presetToRename = presetToRename,
        presetToDelete = presetToDelete,
        confirmStop = confirmStop,
        eventToReset = extras.openEvents.firstOrNull { it.id == eventToReset },
        onRunPreset = { preset -> presetMenu = null; startJob { viewModel.runPreset(preset) } },
        onRenamePreset = { presetMenu = null; presetToRename = it.id },
        onDeletePreset = { presetMenu = null; presetToDelete = it.id },
        onClose = {
            presetDialog = false
            presetMenu = null
            presetToRename = null
            presetToDelete = null
            confirmStop = false
            eventToReset = null
        },
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
    onPresetMenu: (CleaningPreset) -> Unit,
    onStop: () -> Unit,
    onInteract: (RobotEvent, String) -> Unit,
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
                onStop = onStop,
                onDock = viewModel::dock,
                onLocate = viewModel::locate,
            )
        }
        if (hasAttention(live.state, extras.openEvents, extras.pendingMapChange)) {
            item {
                AttentionCards(live.state, extras.openEvents, extras.pendingMapChange, onInteract, viewModel::answerMapChange)
            }
        }
        item {
            PresetsRow(
                presets = presets,
                busyAction = busy,
                enabled = online,
                onRun = { preset -> startJob { viewModel.runPreset(preset) } },
                onLongPress = onPresetMenu,
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
private fun HomeDialogs(
    viewModel: HomeViewModel,
    presets: List<CleaningPreset>,
    roomCount: Int,
    showNewPreset: Boolean,
    presetMenu: String?,
    presetToRename: String?,
    presetToDelete: String?,
    confirmStop: Boolean,
    eventToReset: RobotEvent?,
    onRunPreset: (CleaningPreset) -> Unit,
    onRenamePreset: (CleaningPreset) -> Unit,
    onDeletePreset: (CleaningPreset) -> Unit,
    onClose: () -> Unit,
) {
    if (showNewPreset) {
        PresetNameDialog(roomCount, initialName = null, onSave = { name -> viewModel.savePreset(name); onClose() }, onDismiss = onClose)
    }
    presets.firstOrNull { it.id == presetMenu }?.let { preset ->
        PresetActionsSheet(
            preset = preset,
            onRun = { onRunPreset(preset) },
            onRename = { onRenamePreset(preset) },
            onUpdate = { viewModel.updatePreset(preset); onClose() },
            onDelete = { onDeletePreset(preset) },
            onDismiss = onClose,
        )
    }
    presets.firstOrNull { it.id == presetToRename }?.let { preset ->
        PresetNameDialog(
            roomCount,
            initialName = preset.name,
            onSave = { name -> viewModel.renamePreset(preset, name); onClose() },
            onDismiss = onClose,
        )
    }
    presets.firstOrNull { it.id == presetToDelete }?.let { preset ->
        ConfirmDialog(
            title = stringResource(R.string.preset_delete_title, preset.name),
            text = stringResource(R.string.preset_delete_text),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deletePreset(preset); onClose() },
            onDismiss = onClose,
        )
    }
    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.stop_confirm_title),
            text = stringResource(R.string.stop_confirm_text),
            confirm = stringResource(R.string.action_stop),
            onConfirm = { viewModel.stop(); onClose() },
            onDismiss = onClose,
        )
    }
    eventToReset?.let { event ->
        ConfirmDialog(
            title = stringResource(R.string.event_reset_title),
            text = stringResource(R.string.consumables_reset_text),
            confirm = stringResource(R.string.consumables_reset),
            onConfirm = { viewModel.interact(event, RobotEvent.INTERACTION_RESET); onClose() },
            onDismiss = onClose,
        )
    }
}
