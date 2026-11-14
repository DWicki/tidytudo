package io.github.dwicki.tidytudo.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.prettyFingerprint
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.util.RobotAddress

/** Shows the dialog matching the current connection step; nothing for idle/connecting/added. */
@Composable
fun ConnectDialogs(
    step: ConnectStep,
    onCredentials: (String, String) -> Unit,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (step) {
        is ConnectStep.NeedsCredentials -> CredentialsDialog(step.rejected, onCredentials, onDismiss)
        is ConnectStep.NeedsCertificate -> CertificateDialog(step.baseUrl, step.sha256, onTrust, onDismiss)
        is ConnectStep.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.connect_failed_title)) },
            text = { Text(Labels.error(LocalContext.current, step.error)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
        else -> Unit
    }
}

@Composable
private fun CredentialsDialog(rejected: Boolean, onSubmit: (String, String) -> Unit, onDismiss: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.credentials_title)) },
        text = {
            Column {
                Text(stringResource(if (rejected) R.string.credentials_rejected else R.string.credentials_text))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.credentials_username)) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.credentials_password)) },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(
                                    if (visible) R.string.credentials_hide_password else R.string.credentials_show_password,
                                ),
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(username, password) }, enabled = username.isNotEmpty()) {
                Text(stringResource(R.string.action_connect))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CertificateDialog(baseUrl: String, sha256: String, onTrust: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
        title = { Text(stringResource(R.string.certificate_title)) },
        text = {
            Column {
                Text(stringResource(R.string.certificate_text, RobotAddress.host(baseUrl)))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.certificate_fingerprint), style = MaterialTheme.typography.labelMedium)
                Text(
                    prettyFingerprint(sha256),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.certificate_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.certificate_trust)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
