package io.github.dwicki.tidytudo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.ui.components.TextInputDialogProperties
import io.github.dwicki.tidytudo.ui.components.aboveKeyboard

/** A yes/no question before an action that cannot be undone (stop a job, reset a counter). */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Name for a new preset, or a new name for an existing one when [initialName] is given. */
@Composable
fun PresetNameDialog(roomCount: Int, initialName: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = TextInputDialogProperties,
        modifier = Modifier.aboveKeyboard(),
        title = { Text(stringResource(if (initialName == null) R.string.preset_dialog_title else R.string.preset_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initialName == null) {
                    Text(
                        if (roomCount == 0) {
                            stringResource(R.string.preset_dialog_text_everything)
                        } else {
                            stringResource(R.string.preset_dialog_text_rooms)
                        },
                    )
                }
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

/**
 * What a long press on a preset offers: run, rename, overwrite with the current rooms and modes,
 * delete. Before, a long press deleted straight away (after a confirmation) and presets could not
 * be changed at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetActionsSheet(
    preset: CleaningPreset,
    onRun: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            preset.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetAction(Icons.Outlined.PlayArrow, stringResource(R.string.preset_action_run), onRun)
        SheetAction(Icons.Outlined.Edit, stringResource(R.string.preset_action_rename), onRename)
        SheetAction(
            Icons.Outlined.Sync,
            stringResource(R.string.preset_action_update),
            onUpdate,
            supporting = stringResource(R.string.preset_action_update_text),
        )
        SheetAction(Icons.Outlined.Delete, stringResource(R.string.action_delete), onDelete, destructive = true)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = { Text(label, color = color) },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private const val MAX_PRESET_NAME = 30
