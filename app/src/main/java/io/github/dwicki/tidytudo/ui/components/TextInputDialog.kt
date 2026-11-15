package io.github.dwicki.tidytudo.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/*
 * Dialogs with a text field. With the default properties the dialog window does not move on
 * Android 15+ and the keyboard hides its buttons. Drawing the window edge to edge and padding the
 * dialog by the keyboard height lifts it above the keyboard instead.
 */

/** Pass to `AlertDialog(properties = …)` of a dialog with a text field. */
val TextInputDialogProperties = DialogProperties(decorFitsSystemWindows = false)

/** Pass as the `modifier` of the same dialog. */
fun Modifier.aboveKeyboard(): Modifier = imePadding()
