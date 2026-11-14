package io.github.dwicki.tidytudo.ui.map

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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Live map with modes for rooms, zones, go-to and no-go editing; each mode only if supported. */
@Composable
fun MapScreen(robot: Robot, contentPadding: PaddingValues, snackbar: SnackbarHostState) {
    val viewModel = robotViewModel(robot) { container, current -> MapViewModel(container, current) }
    val map by viewModel.map.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(viewModel.errors, snackbar)

    val currentMap = map
    when {
        currentMap == null -> LoadingBox(Modifier.padding(contentPadding))
        currentMap.isEmpty -> MessageBox(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_empty_title),
            text = stringResource(R.string.map_empty_text),
            modifier = Modifier.padding(contentPadding),
        )
        else -> MapContent(currentMap, state, viewModel.availableModes(live.capabilities), viewModel, contentPadding)
    }
}

@Composable
private fun MapContent(
    map: RobotMap,
    state: MapUiState,
    modes: List<MapMode>,
    viewModel: MapViewModel,
    contentPadding: PaddingValues,
) {
    val viewState = rememberMapViewState()
    viewState.topInset = with(LocalDensity.current) { MODE_BAR_HEIGHT.toPx() }
    val shapes: List<EditableShape> = when (state.mode) {
        MapMode.ZONES -> state.zones
        MapMode.RESTRICTIONS -> state.restrictions
        else -> emptyList()
    }
    val displayedMap = state.goToTarget?.takeIf { state.mode == MapMode.GO_TO }?.let { map.copy(goToTarget = it) } ?: map
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
    ) {
        MapCanvas(
            map = displayedMap,
            selectedSegments = state.selectedSegments.toSet(),
            shapes = shapes,
            viewState = viewState,
            description = stringResource(R.string.map_description),
            onTap = viewModel::onTap,
            onShapeChange = viewModel::updateShape,
            modifier = Modifier.fillMaxSize(),
        )
        ModeSelector(
            modes = modes,
            selected = state.mode,
            onSelect = viewModel::setMode,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        SmallFloatingActionButton(
            onClick = { viewState.fit(map) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp),
        ) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = stringResource(R.string.map_fit))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            ModePanel(
                state = state,
                onAddShape = { kind -> viewState.visibleCenter(map)?.let { viewModel.addShape(kind, it) } },
                actions = viewModel,
            )
        }
    }
}

@Composable
private fun ModeSelector(modes: List<MapMode>, selected: MapMode, onSelect: (MapMode) -> Unit, modifier: Modifier) {
    if (modes.size < 2) return
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(end = 56.dp),
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
