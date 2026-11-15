package io.github.dwicki.tidytudo.ui.rooms

import io.github.dwicki.tidytudo.ui.components.TextInputDialogProperties
import io.github.dwicki.tidytudo.ui.components.aboveKeyboard
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import io.github.dwicki.tidytudo.R

/**
 * The one rename dialog, used from the rooms list, the home screen and the map. It opens with the
 * current name selected, so typing replaces it and a small correction needs no retyping.
 */
@Composable
fun RenameRoomDialog(currentName: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = currentName.orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canSave = RoomName.canSave(field.text, currentName)
    fun save() {
        RoomName.normalize(field.text)?.takeIf { canSave }?.let(onSave)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = TextInputDialogProperties,
        modifier = Modifier.aboveKeyboard(),
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.rooms_rename_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.copy(text = it.text.take(RoomName.MAX_LENGTH)) },
                singleLine = true,
                label = { Text(stringResource(R.string.map_room_name)) },
                supportingText = { Text("${field.text.length} / ${RoomName.MAX_LENGTH}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
