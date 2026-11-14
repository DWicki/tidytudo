package io.github.dwicki.tidytudo.ui.appsettings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.BuildConfig
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.ui.appViewModel
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.NavigationRow
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import kotlinx.coroutines.flow.StateFlow

class AppSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val monitoring: StateFlow<Boolean> = container.settings.backgroundMonitoring

    fun setMonitoring(enabled: Boolean) {
        container.settings.setBackgroundMonitoring(enabled)
        if (enabled) container.startMonitoring()
    }
}

/** App-wide settings: background alerts (F-7.4), language (F-7.6), privacy and about. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { AppSettingsViewModel(it) }
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setMonitoring(true)
    }
    fun enableMonitoring(enabled: Boolean) {
        val needsPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (enabled && needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setMonitoring(enabled)
    }

    DetailScaffold(title = stringResource(R.string.app_settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.clip(MaterialTheme.shapes.large)) {
                    ListItem(
                        modifier = Modifier.clickableRow { enableMonitoring(!monitoring) },
                        leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.app_settings_monitoring)) },
                        supportingContent = { Text(stringResource(R.string.app_settings_monitoring_text)) },
                        trailingContent = { Switch(checked = monitoring, onCheckedChange = ::enableMonitoring) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    NavigationRow(Icons.Outlined.Language, stringResource(R.string.app_settings_language), stringResource(R.string.app_settings_language_text)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.app_settings_privacy)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.app_settings_privacy_text), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.app_settings_about)) {
                    Text(stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.app_settings_about_text), style = MaterialTheme.typography.bodySmall)
                    NavigationRow(Icons.Outlined.Code, stringResource(R.string.app_settings_source), SOURCE_URL) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    }
                }
            }
        }
    }
}

private const val SOURCE_URL = "https://github.com/DWicki/Tidytudo"
