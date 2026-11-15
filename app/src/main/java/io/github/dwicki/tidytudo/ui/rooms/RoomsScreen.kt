package io.github.dwicki.tidytudo.ui.rooms

import io.github.dwicki.tidytudo.ui.components.ROW_HORIZONTAL_PADDING
import io.github.dwicki.tidytudo.ui.components.AppListItem
import io.github.dwicki.tidytudo.ui.components.ListCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.robotViewModel

/** All rooms of the robot; tapping one renames it (F-4.5). */
@Composable
fun RoomsScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> RoomsViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<MapSegment?>(null) }

    DetailScaffold(title = stringResource(R.string.rooms_title), onBack = onBack, errors = viewModel.errors) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> if (current.value.rooms.isEmpty()) {
                MessageBox(icon = Icons.Outlined.MeetingRoom, title = stringResource(R.string.rooms_empty), text = stringResource(R.string.map_empty_text))
            } else {
                RoomList(current.value, padding, onRename = { renaming = it })
            }
        }
    }
    renaming?.let { room ->
        RenameRoomDialog(
            currentName = room.name,
            onSave = { name -> viewModel.rename(room, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun RoomList(data: RoomsData, padding: PaddingValues, onRename: (MapSegment) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(if (data.canRename) R.string.rooms_hint else R.string.rooms_rename_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ROW_HORIZONTAL_PADDING, end = ROW_HORIZONTAL_PADDING, bottom = 4.dp),
            )
        }
        item {
            ListCard {
                data.rooms.forEach { room -> RoomRow(room, canRename = data.canRename, onRename = { onRename(room) }) }
            }
        }
    }
}

@Composable
private fun RoomRow(room: MapSegment, canRename: Boolean, onRename: () -> Unit) {
    val name = room.name ?: stringResource(R.string.room_unnamed, room.id)
    AppListItem(
        title = name,
        icon = Icons.Outlined.MeetingRoom,
        onClick = if (canRename) onRename else null,
        trailing = if (canRename) {
            {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rooms_rename_action, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}
