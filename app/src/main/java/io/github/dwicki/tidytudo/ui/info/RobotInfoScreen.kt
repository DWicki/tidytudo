package io.github.dwicki.tidytudo.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.model.DataPoint
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.ErrorBox
import io.github.dwicki.tidytudo.ui.components.LoadingBox
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.robotViewModel
import io.github.dwicki.tidytudo.util.RobotAddress

@Composable
fun RobotInfoScreen(robot: Robot, onBack: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> RobotInfoViewModel(container, current) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScaffold(title = stringResource(R.string.info_title), onBack = onBack) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> ErrorBox(current.error, viewModel::load)
            is LoadState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { DeviceCard(robot, current.value) }
                if (current.value.currentStatistics.isNotEmpty() || current.value.totalStatistics.isNotEmpty()) {
                    item { StatisticsCard(current.value) }
                }
                item { CapabilitiesCard(current.value) }
            }
        }
    }
}

@Composable
private fun DeviceCard(robot: Robot, details: RobotDetails) {
    SectionCard(title = stringResource(R.string.info_device)) {
        InfoRow(stringResource(R.string.info_model), listOf(details.robot.manufacturer, details.robot.modelName).joinToString(" "))
        details.firmware?.let { InfoRow(stringResource(R.string.info_firmware), it) }
        InfoRow(stringResource(R.string.info_valetudo), details.valetudoVersion)
        InfoRow(stringResource(R.string.info_address), RobotAddress.host(robot.baseUrl))
        details.host?.let { host ->
            InfoRow(stringResource(R.string.info_uptime), formatUptime(host.uptime))
            if (host.mem.total > 0) {
                InfoRow(stringResource(R.string.info_memory), stringResource(R.string.info_memory_value, host.mem.free / MEBIBYTE, host.mem.total / MEBIBYTE))
            }
        }
        if (robot.pinnedSha256 != null) InfoRow(stringResource(R.string.info_tls), stringResource(R.string.info_tls_pinned))
    }
}

@Composable
private fun StatisticsCard(details: RobotDetails) {
    SectionCard(title = stringResource(R.string.info_statistics)) {
        if (details.currentStatistics.isNotEmpty()) {
            Text(stringResource(R.string.info_statistics_last), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            details.currentStatistics.forEach { StatisticRow(it) }
        }
        if (details.totalStatistics.isNotEmpty()) {
            Text(
                stringResource(R.string.info_statistics_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            details.totalStatistics.forEach { StatisticRow(it) }
        }
    }
}

@Composable
private fun StatisticRow(point: DataPoint) {
    when (point.type) {
        // Valetudo reports time in seconds and area in cm².
        "time" -> InfoRow(stringResource(R.string.info_stat_time), formatUptime(point.value))
        "area" -> InfoRow(stringResource(R.string.info_stat_area), stringResource(R.string.info_area_value, point.value / CM2_PER_M2))
        "count" -> InfoRow(stringResource(R.string.info_stat_count), point.value.toString())
        else -> InfoRow(Labels.humanize(point.type), point.value.toString())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilitiesCard(details: RobotDetails) {
    SectionCard(title = pluralStringResource(R.plurals.info_capabilities, details.capabilities.size, details.capabilities.size)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            details.capabilities.forEach { SuggestionChip(onClick = {}, label = { Text(Labels.capabilityName(it)) }) }
        }
        if (details.unknownCapabilities.isNotEmpty()) {
            Text(
                stringResource(R.string.info_unknown_capabilities, details.unknownCapabilities.joinToString(", ") { Labels.capabilityName(it) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun formatUptime(seconds: Long): String {
    val hours = (seconds / SECONDS_PER_HOUR).toInt()
    val minutes = ((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
    }
}

private const val MEBIBYTE = 1024 * 1024
private const val CM2_PER_M2 = 10_000
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
