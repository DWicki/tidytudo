package io.github.dwicki.tidytudo.ui.robots

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.MoreVert
import io.github.dwicki.tidytudo.ui.components.TonalIcon
import io.github.dwicki.tidytudo.ui.components.AppListItem
import io.github.dwicki.tidytudo.ui.components.ListCard
import io.github.dwicki.tidytudo.ui.components.aboveKeyboard
import io.github.dwicki.tidytudo.ui.components.TextInputDialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.appViewModel
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
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
            item {
                ListCard {
                    robots.forEach { robot ->
                        RobotRow(
                            robot = robot,
                            active = robot.id == (selectedId ?: robots.firstOrNull()?.id),
                            onSelect = { viewModel.select(robot) },
                            onRename = { renaming = robot },
                            onRemove = { removing = robot },
                        )
                    }
                }
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

/**
 * One robot. Tapping opens its menu instead of switching robots on the spot, so a stray tap next to
 * "Remove" does nothing surprising; the active robot is labelled as such.
 */
@Composable
private fun RobotRow(robot: Robot, active: Boolean, onSelect: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val details = listOf(robot.displayModel, RobotAddress.host(robot.baseUrl)).filter { it.isNotBlank() }.joinToString(" · ")
    AppListItem(
        title = robot.displayName,
        supporting = if (active) stringResource(R.string.robots_active, details) else details,
        supportingColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        leading = {
            TonalIcon(
                Icons.Outlined.SmartToy,
                container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        onClick = { menu = true },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.robots_use)) },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                            onClick = { menu = false; onSelect() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.robots_rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        },
    )
}

@Composable
private fun RenameDialog(robot: Robot, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(robot.nickname) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = TextInputDialogProperties,
        modifier = Modifier.aboveKeyboard(),
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
