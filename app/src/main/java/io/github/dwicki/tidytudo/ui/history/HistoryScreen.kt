package io.github.dwicki.tidytudo.ui.history

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.data.history.CleaningSession
import io.github.dwicki.tidytudo.ui.components.DetailScaffold
import io.github.dwicki.tidytudo.ui.components.LocalImage
import io.github.dwicki.tidytudo.ui.components.MessageBox
import io.github.dwicki.tidytudo.ui.components.SectionCard
import io.github.dwicki.tidytudo.ui.components.clickableRow
import io.github.dwicki.tidytudo.ui.robotViewModel

/** Past cleaning runs, newest first (F-8.4). */
@Composable
fun HistoryScreen(robot: Robot, onBack: () -> Unit, onOpen: (String) -> Unit, onOpenAppSettings: () -> Unit) {
    val viewModel = robotViewModel(robot) { container, current -> HistoryViewModel(container, current) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val monitoring by viewModel.monitoringEnabled.collectAsStateWithLifecycle()

    DetailScaffold(title = stringResource(R.string.history_title), onBack = onBack) { padding ->
        if (sessions.isEmpty()) {
            MessageBox(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty),
                text = stringResource(if (monitoring) R.string.history_empty_text else R.string.history_empty_text_monitoring_off),
                actionLabel = if (monitoring) null else stringResource(R.string.history_enable_monitoring),
                onAction = onOpenAppSettings,
            )
            return@DetailScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!monitoring) item { MonitoringHint(onOpenAppSettings) }
            items(sessions, key = { it.id }) { session ->
                SessionCard(session, viewModel, onClick = { onOpen(session.id) })
            }
        }
    }
}

@Composable
private fun MonitoringHint(onOpenAppSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            Modifier.clickableRow(onOpenAppSettings).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.history_monitoring_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.history_enable_monitoring)) }
        }
    }
}

@Composable
private fun SessionCard(session: CleaningSession, viewModel: HistoryViewModel, onClick: () -> Unit) {
    val context = LocalContext.current
    SectionCard(modifier = Modifier.clickableRow(onClick), title = sessionTitle(context, session), trailing = { ResultChip(session.result) }) {
        Text(sessionSummary(session), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val photos = session.obstacles.filter { it.hasImage }
        if (session.obstacles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                photos.take(THUMBNAILS).forEach { obstacle ->
                    LocalImage(
                        file = viewModel.imageFile(session.id, obstacle.id),
                        contentDescription = obstacle.label,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                        maxSize = THUMBNAIL_PIXELS,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.history_obstacles, session.obstacles.size, session.obstacles.size),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Coloured status label; a chip would look like a button that does nothing. */
@Composable
fun ResultChip(result: CleaningSession.Result) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (result) {
        CleaningSession.Result.FINISHED -> Triple(R.string.history_result_finished, colors.tertiaryContainer, colors.onTertiaryContainer)
        CleaningSession.Result.STOPPED -> Triple(R.string.history_result_stopped, colors.secondaryContainer, colors.onSecondaryContainer)
        CleaningSession.Result.ERROR -> Triple(R.string.history_result_error, colors.errorContainer, colors.onErrorContainer)
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** "1 h 47 min · 83 m² · Kitchen, Office" */
@Composable
fun sessionSummary(session: CleaningSession): String {
    val seconds = session.effectiveDurationSeconds
    val duration = if (seconds >= SECONDS_PER_HOUR) {
        stringResource(R.string.duration_hours_minutes, (seconds / SECONDS_PER_HOUR).toInt(), ((seconds % SECONDS_PER_HOUR) / 60).toInt())
    } else {
        pluralStringResource(R.plurals.duration_minutes, (seconds / 60).toInt(), (seconds / 60).toInt())
    }
    val area = session.areaCm2?.let { stringResource(R.string.info_area_value, (it / CM2_PER_M2).toInt()) }
    val rooms = session.roomNames.joinToString(", ").ifEmpty {
        stringResource(if (session.kind == "zone") R.string.flag_zone else R.string.home_preset_everything)
    }
    return listOfNotNull(duration, area, rooms).joinToString(" · ")
}

fun sessionTitle(context: Context, session: CleaningSession): String = DateUtils.formatDateTime(
    context,
    session.startedAt,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
)

private const val THUMBNAILS = 3
private const val THUMBNAIL_PIXELS = 160
private const val SECONDS_PER_HOUR = 3600L
private const val CM2_PER_M2 = 10_000L
