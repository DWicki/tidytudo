package io.github.dwicki.tidytudo.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.dwicki.tidytudo.api.Connection
import io.github.dwicki.tidytudo.ui.cleaning.CleaningModeButton
import io.github.dwicki.tidytudo.ui.cleaning.CleaningModeRow
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.ui.rooms.RenameRoomDialog
import io.github.dwicki.tidytudo.ui.components.rememberJobStarter
import io.github.dwicki.tidytudo.ui.home.ConfirmDialog
import io.github.dwicki.tidytudo.ui.home.HomeViewModel
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.map.EditableShape
import io.github.dwicki.tidytudo.map.MapCanvas
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.map.rememberMapViewState
import io.github.dwicki.tidytudo.ui.components.ErrorSnackbarEffect
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.robotViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Live map with modes for rooms, zones, go-to and no-go editing; each mode only if supported. */
@Composable
fun MapScreen(
    robot: Robot,
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    openRooms: Boolean = false,
    onOpenRoomsHandled: () -> Unit = {},
) {
    val viewModel = robotViewModel(robot) { container, current -> MapViewModel(container, current) }
    val map by viewModel.map.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(viewModel.errors, snackbar)
    // Start, pause, dock and stop come from the home screen's logic, so both screens behave alike.
    val home = robotViewModel(robot) { container, current -> HomeViewModel(container, current) }
    val progress by home.progress.collectAsStateWithLifecycle()
    val busy by home.busy.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(home.errors, snackbar)
    val startJob = rememberJobStarter()
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.stop_confirm_title),
            text = stringResource(R.string.stop_confirm_text),
            confirm = stringResource(R.string.action_stop),
            onConfirm = { home.stop(); confirmStop = false },
            onDismiss = { confirmStop = false },
        )
    }
    val online = live.connection == Connection.Online
    val modeRow: @Composable () -> Unit = {
        CleaningModeRow(robot, snackbar, enabled = online, container = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val status = live.state?.status?.value ?: StatusValue.UNKNOWN
    // View and rooms mode: round buttons over the map instead of a panel; the state is in the top bar.
    val controls: @Composable (onFit: () -> Unit) -> Unit = { onFit ->
        val rooms = if (state.mode == MapMode.ROOMS) {
            RoomsControls(
                count = state.selectedSegments.size,
                iterations = state.iterations,
                maxIterations = state.segmentProperties.iterationCount.max,
                onClear = viewModel::clearRooms,
                onIterations = viewModel::setIterations,
                onRename = if (state.canRename) ({ renaming = true }) else null,
            )
        } else {
            null
        }
        MapControls(
            status = status,
            online = online,
            busy = state.sending || busy == HomeViewModel.ACTION_TOGGLE || busy == HomeViewModel.ACTION_CLEAN_ROOMS,
            progress = progress,
            rooms = rooms,
            onFit = onFit,
            onMain = {
                val running = status.isActive || status == StatusValue.PAUSED
                if (rooms != null && !running) startJob(viewModel::cleanSelectedRooms) else startJob(home::toggle)
            },
            onDock = home::dock,
            onStop = { confirmStop = true },
            modeButton = { CleaningModeButton(robot, snackbar, enabled = online) },
        )
    }

    // Coming from the room card on the home screen: show the rooms (and the shared selection) at once.
    LaunchedEffect(openRooms, live.capabilities) {
        if (openRooms && MapMode.ROOMS in viewModel.availableModes(live.capabilities)) {
            viewModel.setMode(MapMode.ROOMS)
            onOpenRoomsHandled()
        }
    }
    // Back leaves an editing mode first, instead of leaving the app.
    BackHandler(enabled = state.mode != MapMode.VIEW && state.pendingMode == null) { viewModel.setMode(MapMode.VIEW) }
    if (state.pendingMode != null) {
        AlertDialog(
            onDismissRequest = viewModel::keepEditing,
            title = { Text(stringResource(R.string.map_unsaved_title)) },
            text = { Text(stringResource(R.string.map_unsaved_text)) },
            confirmButton = { TextButton(onClick = viewModel::saveAndLeave) { Text(stringResource(R.string.action_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::discardAndLeave) { Text(stringResource(R.string.map_unsaved_discard)) }
                    TextButton(onClick = viewModel::keepEditing) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    if (renaming) {
        val id = state.selectedSegments.singleOrNull()
        RenameRoomDialog(
            currentName = map?.segments?.firstOrNull { it.id == id }?.name,
            onSave = { viewModel.renameSelectedRoom(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    val currentMap = map
    when {
        currentMap == null -> LoadingBox(Modifier.padding(contentPadding))
        currentMap.isEmpty -> MessageBox(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_empty_title),
            text = stringResource(R.string.map_empty_text),
            modifier = Modifier.padding(contentPadding),
        )
        else -> MapContent(currentMap, state, viewModel.availableModes(live.capabilities), viewModel, contentPadding, controls, modeRow)
    }
}

@Composable
private fun MapContent(
    map: RobotMap,
    state: MapUiState,
    modes: List<MapMode>,
    viewModel: MapViewModel,
    contentPadding: PaddingValues,
    controls: @Composable (onFit: () -> Unit) -> Unit,
    modeRow: @Composable () -> Unit,
) {
    val viewState = rememberMapViewState()
    viewState.topInset = with(LocalDensity.current) { MODE_BAR_HEIGHT.toPx() }
    val shapes: List<EditableShape> = when (state.mode) {
        MapMode.ZONES -> state.zones
        MapMode.RESTRICTIONS -> state.restrictions
        else -> emptyList()
    }
    // Re-fit when the mode changes, so the map is not hidden behind the panel that just appeared.
    LaunchedEffect(state.mode) {
        delay(PANEL_SETTLE_MS)
        viewState.animateFit(map)
    }
    // The panel also grows within a mode (first room picked, a save button appears): make room for it,
    // unless the user has zoomed or moved the map themselves.
    val currentMap by rememberUpdatedState(map)
    LaunchedEffect(viewState) {
        snapshotFlow { viewState.bottomInset }.collectLatest {
            delay(INSET_SETTLE_MS)
            if (!viewState.userMoved) viewState.animateFit(currentMap)
        }
    }
    val displayedMap = state.goToTarget?.takeIf { state.mode == MapMode.GO_TO }?.let { map.copy(goToTarget = it) } ?: map
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
    ) {
        MapCanvas(
            map = displayedMap,
            // Outside room mode, highlight the rooms the robot is cleaning right now.
            neutralRooms = state.mode == MapMode.ROOMS,
            selectedSegments = if (state.mode == MapMode.ROOMS) {
                state.selectedSegments.toSet()
            } else {
                map.segments.filter { it.active }.map { it.id }.toSet()
            },
            shapes = shapes,
            viewState = viewState,
            description = stringResource(R.string.map_description),
            onTap = viewModel::onTap,
            onShapeChange = viewModel::updateShape,
            modifier = Modifier.fillMaxSize(),
            labelOrder = if (state.mode == MapMode.ROOMS) state.selectedSegments else emptyList(),
            numberedLabels = state.segmentProperties.customOrderSupport,
            quietPath = state.mode != MapMode.VIEW,
        )
        // No "view" chip: viewing is what happens when no tool is chosen; tapping the active tool again ends it.
        ModeSelector(
            modes = modes - MapMode.VIEW,
            selected = state.mode,
            onSelect = { mode -> viewModel.setMode(if (mode == state.mode) MapMode.VIEW else mode) },
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(vertical = 12.dp),
        )
        val floating = state.mode == MapMode.VIEW || state.mode == MapMode.ROOMS
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .onSizeChanged { viewState.bottomInset = it.height.toFloat() },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(targetState = floating, label = "mapControls") { showControls ->
                if (showControls) {
                    controls { viewState.fit(map) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(
                            onClick = { viewState.fit(map) },
                            modifier = Modifier.align(Alignment.End),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Icon(Icons.Outlined.FitScreen, contentDescription = stringResource(R.string.map_fit))
                        }
                        ModePanel(
                            roomName = { id -> map.segments.firstOrNull { it.id == id }?.name },
                            state = state,
                            onAddShape = { kind -> viewState.visibleCenter(map)?.let { viewModel.addShape(kind, it) } },
                            actions = viewModel,
                            modeRow = modeRow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(modes: List<MapMode>, selected: MapMode, onSelect: (MapMode) -> Unit, modifier: Modifier) {
    if (modes.isEmpty()) return
    // Padding inside the scroll, so chips scroll all the way to the screen edge instead of being cut off.
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.label)) },
                leadingIcon = { Icon(mode.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}

private val MODE_BAR_HEIGHT = 64.dp

/** Time for the mode panel's size animation, so the measured inset is final before fitting. */
private const val PANEL_SETTLE_MS = 350L

/** Quiet time after the panel's last size change before the map makes room for it. */
private const val INSET_SETTLE_MS = 120L
