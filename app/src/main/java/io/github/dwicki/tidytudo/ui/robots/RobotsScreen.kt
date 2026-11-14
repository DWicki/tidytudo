package io.github.dwicki.tidytudo.ui.robots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.appViewModel
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.util.RobotAddress

@Composable
fun RobotsScreen(onBack: () -> Unit, onAddRobot: () -> Unit) {
    val viewModel = appViewModel { RobotsViewModel(it) }
    val robots by viewModel.robots.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Robot?>(null) }
    var removing by remember { mutableStateOf<Robot?>(null) }

    DetailScaffold(
        title = stringResource(R.string.robots_title),
        onBack = onBack,
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = onAddRobot,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.robots_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(robots, key = { it.id }) { robot ->
                RobotRow(
                    robot = robot,
                    selected = robot.id == (selectedId ?: robots.firstOrNull()?.id),
                    onSelect = { viewModel.select(robot) },
                    onRename = { renaming = robot },
                    onRemove = { removing = robot },
                )
            }
        }
    }
    renaming?.let { robot -> RenameDialog(robot, onSave = { viewModel.rename(robot, it); renaming = null }, onDismiss = { renaming = null }) }
    removing?.let { robot ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.robots_remove_title, robot.displayName)) },
            text = { Text(stringResource(R.string.robots_remove_text)) },
            confirmButton = { TextButton(onClick = { viewModel.remove(robot); removing = null }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RobotRow(robot: Robot, selected: Boolean, onSelect: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickableRow(onSelect),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.SmartToy, contentDescription = null)
                }
            }
        },
        headlineContent = { Text(robot.displayName) },
        supportingContent = { Text(listOf(robot.displayModel, RobotAddress.host(robot.baseUrl)).filter { it.isNotBlank() }.joinToString(" · ")) },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.robots_rename)) }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_remove)) }
            }
        },
    )
}

@Composable
private fun RenameDialog(robot: Robot, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(robot.nickname) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.robots_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                singleLine = true,
                placeholder = { Text(robot.displayModel) },
                label = { Text(stringResource(R.string.robots_name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_NAME = 40
