package io.github.dwicki.tidytudo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.dwicki.tidytudo.R

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private const val CM2_PER_M2 = 10_000L

/** "1 h 47 min" or "12 minutes". */
@Composable
fun durationText(seconds: Long): String = if (seconds >= SECONDS_PER_HOUR) {
    stringResource(
        R.string.duration_hours_minutes,
        (seconds / SECONDS_PER_HOUR).toInt(),
        ((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt(),
    )
} else {
    val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
    pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
}

/** Valetudo reports areas in cm²; shown as whole m². */
@Composable
fun areaText(areaCm2: Long): String = stringResource(R.string.info_area_value, (areaCm2 / CM2_PER_M2).toInt())
