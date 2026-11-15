package io.github.dwicki.tidytudo.ui.appsettings

import androidx.compose.material.icons.outlined.Info
import io.github.dwicki.tidytudo.ui.components.AppListItem
import io.github.dwicki.tidytudo.ui.components.SwitchListItem
import io.github.dwicki.tidytudo.ui.components.ListCard
import androidx.compose.material.icons.outlined.Palette
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import io.github.dwicki.tidytudo.ui.components.rememberMonitoringSwitch
import kotlinx.coroutines.flow.StateFlow

/** App-wide settings: background alerts (F-7.4), language (F-7.6), privacy and about. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { AppSettingsViewModel(it) }
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val setMonitoring = rememberMonitoringSwitch()

    DetailScaffold(title = stringResource(R.string.app_settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ListCard {
                    SwitchListItem(
                        title = stringResource(R.string.app_settings_monitoring),
                        supporting = stringResource(R.string.app_settings_monitoring_text),
                        icon = Icons.Outlined.NotificationsActive,
                        checked = monitoring,
                        onCheckedChange = setMonitoring,
                    )
                    SwitchListItem(
                        title = stringResource(R.string.app_settings_dynamic_color),
                        supporting = stringResource(R.string.app_settings_dynamic_color_text),
                        icon = Icons.Outlined.Palette,
                        checked = dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        NavigationRow(Icons.Outlined.Language, stringResource(R.string.app_settings_language), stringResource(R.string.app_settings_language_text)) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    }
                }
            }
            item {
                ListCard {
                    AppListItem(
                        title = stringResource(R.string.app_settings_privacy),
                        supporting = stringResource(R.string.app_settings_privacy_text),
                        icon = Icons.Outlined.Shield,
                    )
                }
            }
            item {
                ListCard(title = stringResource(R.string.app_settings_about)) {
                    AppListItem(
                        title = stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME),
                        supporting = stringResource(R.string.app_settings_about_text),
                        icon = Icons.Outlined.Info,
                    )
                    NavigationRow(Icons.Outlined.Code, stringResource(R.string.app_settings_source), SOURCE_URL.removePrefix("https://")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    }
                }
            }
        }
    }
}

private const val SOURCE_URL = "https://github.com/DWicki/tidytudo"
