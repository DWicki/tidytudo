package io.github.dwicki.tidytudo.ui.onboarding

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.discovery.DiscoveredRobot
import io.github.dwicki.tidytudo.ui.appViewModel
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.util.RobotAddress

/** Onboarding and "add another robot" (F-1.1 – F-1.5, F-1.9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRobotScreen(onAdded: () -> Unit, onBack: (() -> Unit)?) {
    val viewModel = appViewModel { AddRobotViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step is ConnectStep.Added) onAdded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header() }
            item { DiscoveryCard(state, onConnect = viewModel::connect, onScan = viewModel::scanSubnet) }
            item {
                ManualEntryCard(
                    address = state.manualAddress,
                    invalid = state.manualAddressInvalid,
                    connecting = state.step is ConnectStep.Connecting,
                    onChange = viewModel::onManualAddressChange,
                    onConnect = viewModel::connectManual,
                )
            }
            item { PrivacyNote() }
        }
    }
    ConnectDialogs(
        step = state.step,
        onCredentials = viewModel::submitCredentials,
        onTrust = viewModel::trustCertificate,
        onDismiss = viewModel::dismissStep,
    )
}

@Composable
private fun Header() {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.add_robot_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.add_robot_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoveryCard(state: AddRobotUiState, onConnect: (DiscoveredRobot) -> Unit, onScan: () -> Unit) {
    val searching = state.mdnsSearching || state.subnetScan == ScanState.RUNNING
    SectionCard(
        modifier = Modifier.animateContentSize(),
        title = stringResource(R.string.add_robot_found_title),
        trailing = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
    ) {
        if (state.found.isEmpty()) {
            EmptyDiscovery(searching = searching)
        } else {
            state.found.forEach { robot ->
                FoundRobotRow(
                    robot = robot,
                    alreadyAdded = robot.baseUrl in state.knownUrls,
                    connecting = (state.step as? ConnectStep.Connecting)?.baseUrl == robot.baseUrl,
                    onClick = { onConnect(robot) },
                )
            }
        }
        val scanAvailable = !state.mdnsSearching && state.subnetScan == ScanState.IDLE
        AnimatedVisibility(visible = scanAvailable && state.found.isEmpty()) { ScanSuggestion(onScan) }
        // With robots found, the scan stays available but quiet, for a second robot that does not announce itself.
        AnimatedVisibility(visible = scanAvailable && state.found.isNotEmpty()) {
            TextButton(onClick = onScan) { Text(stringResource(R.string.add_robot_scan_more)) }
        }
        AnimatedVisibility(visible = state.subnetScan == ScanState.RUNNING) {
            Column(Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.add_robot_scanning), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EmptyDiscovery(searching: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            if (searching) Icons.Outlined.Radar else Icons.Outlined.WifiFind,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(if (searching) R.string.add_robot_searching else R.string.add_robot_none_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanSuggestion(onScan: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            stringResource(R.string.add_robot_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onScan) {
            Icon(Icons.Outlined.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_robot_scan_button))
        }
    }
}

@Composable
private fun FoundRobotRow(robot: DiscoveredRobot, alreadyAdded: Boolean, connecting: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickableRow(onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Router, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        headlineContent = { Text(robot.title) },
        supportingContent = {
            Text(listOfNotNull(RobotAddress.host(robot.baseUrl), robot.version?.let { "Valetudo $it" }).joinToString(" · "))
        },
        trailingContent = {
            when {
                connecting -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                alreadyAdded -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.add_robot_already_added),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualEntryCard(
    address: String,
    invalid: Boolean,
    connecting: Boolean,
    onChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    // Keeps the whole card (field and button) above the keyboard while typing. It follows the
    // keyboard's height rather than waiting a fixed time, because keyboards animate at different speeds.
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboardHeight) {
        if (focused && keyboardHeight > 0) bringIntoView.bringIntoView()
    }
    SectionCard(modifier = Modifier.bringIntoViewRequester(bringIntoView), title = stringResource(R.string.add_robot_manual_title)) {
        OutlinedTextField(
            value = address,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { focused = it.isFocused },
            singleLine = true,
            label = { Text(stringResource(R.string.add_robot_address_label)) },
            placeholder = { Text(stringResource(R.string.add_robot_address_placeholder)) },
            isError = invalid,
            supportingText = { if (invalid) Text(stringResource(R.string.add_robot_address_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onConnect() }),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onConnect, enabled = address.isNotBlank() && !connecting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_connect))
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        stringResource(R.string.add_robot_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Waits for the keyboard animation, otherwise the card is scrolled to where the keyboard is about to appear. */
