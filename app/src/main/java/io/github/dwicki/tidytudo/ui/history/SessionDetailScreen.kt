package io.github.dwicki.tidytudo.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.history.CleaningSession
import io.github.dwicki.tidytudo.data.history.SessionObstacle
import io.github.dwicki.tidytudo.map.CmPoint
import io.github.dwicki.tidytudo.map.MapCanvas
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.map.rememberMapViewState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.LocalImage
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.ui.robotViewModel
import kotlin.math.hypot

/** One past run: map with the driven path and obstacle pins, statistics, errors and photos (F-8.4). */
@Composable
fun SessionDetailScreen(robot: Robot, sessionId: String, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> HistoryViewModel(container, current) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val map by viewModel.selectedMap.collectAsStateWithLifecycle()
    val session = sessions.firstOrNull { it.id == sessionId }
    var shownObstacle by remember { mutableStateOf<SessionObstacle?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId) { viewModel.loadMap(sessionId) }

    if (session == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    DetailScaffold(
        title = sessionTitle(LocalContext.current, session),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            map?.let { item { SessionMap(it, session) { obstacle -> shownObstacle = obstacle } } }
            item { SummaryCard(session) }
            if (session.errors.isNotEmpty()) item { ErrorsCard(session.errors) }
            if (session.obstacles.isNotEmpty()) item { ObstaclesCard(session, viewModel) { shownObstacle = it } }
        }
    }

    shownObstacle?.let { obstacle -> ObstacleDialog(session, obstacle, viewModel) { shownObstacle = null } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(session.id) }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SessionMap(map: RobotMap, session: CleaningSession, onObstacle: (SessionObstacle) -> Unit) {
    val viewState = rememberMapViewState()
    Box(Modifier.fillMaxWidth().height(MAP_HEIGHT).clip(MaterialTheme.shapes.large)) {
        MapCanvas(
            map = map,
            selectedSegments = emptySet(),
            shapes = emptyList(),
            viewState = viewState,
            description = stringResource(R.string.history_map_description),
            onTap = { point, _ -> nearestObstacle(session, point)?.let(onObstacle) },
            onShapeChange = {},
            markers = session.obstacles.map { CmPoint(it.xCm, it.yCm) },
            // Pins would cover room names; the rooms of this run are listed in the summary instead.
            showRoomLabels = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A tap within [TAP_RADIUS_CM] of an obstacle pin opens it; pins are small, so the radius is generous. */
private fun nearestObstacle(session: CleaningSession, point: CmPoint): SessionObstacle? =
    session.obstacles.minByOrNull { hypot(it.xCm - point.x, it.yCm - point.y) }
        ?.takeIf { hypot(it.xCm - point.x, it.yCm - point.y) < TAP_RADIUS_CM }

@Composable
private fun SummaryCard(session: CleaningSession) {
    SectionCard(title = stringResource(R.string.history_summary), trailing = { ResultChip(session.result) }) {
        Text(sessionSummary(session), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorsCard(errors: List<String>) {
    SectionCard(title = stringResource(R.string.history_errors)) {
        errors.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObstaclesCard(session: CleaningSession, viewModel: HistoryViewModel, onOpen: (SessionObstacle) -> Unit) {
    SectionCard(title = pluralStringResource(R.plurals.history_obstacles, session.obstacles.size, session.obstacles.size)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            session.obstacles.forEach { obstacle ->
                Column(Modifier.width(TILE_WIDTH).clickableRow { onOpen(obstacle) }) {
                    if (obstacle.hasImage) {
                        LocalImage(
                            file = viewModel.imageFile(session.id, obstacle.id),
                            contentDescription = obstacle.label,
                            modifier = Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT).clip(MaterialTheme.shapes.small),
                            maxSize = TILE_PIXELS,
                        )
                    }
                    Text(obstacle.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ObstacleDialog(session: CleaningSession, obstacle: SessionObstacle, viewModel: HistoryViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(obstacle.label) },
        text = {
            if (obstacle.hasImage) {
                LocalImage(
                    file = viewModel.imageFile(session.id, obstacle.id),
                    contentDescription = obstacle.label,
                    modifier = Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT).clip(MaterialTheme.shapes.medium),
                )
            } else {
                Text(stringResource(R.string.history_no_photo))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private val MAP_HEIGHT = 360.dp
private val TILE_WIDTH = 104.dp
private const val TILE_PIXELS = 240
/** Robot cameras deliver 4:3 photos (672 × 504 on the tested robot). */
private const val PHOTO_ASPECT = 4f / 3f
private const val TAP_RADIUS_CM = 60f
