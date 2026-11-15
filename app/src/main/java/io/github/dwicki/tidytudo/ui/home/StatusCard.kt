package io.github.dwicki.tidytudo.ui.home

import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Connection
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.ui.Labels
import io.github.dwicki.tidytudo.ui.components.areaText
import io.github.dwicki.tidytudo.ui.components.durationText
import kotlinx.coroutines.delay

/**
 * The top of the home screen (F-2.1 – F-2.4): what the robot is doing, in one headline and one
 * supporting line, and the buttons to change it. The card's colour follows the state, so docked,
 * cleaning, paused and error look different at a glance.
 *
 * [compact] is the map's version: a smaller robot and headline, so the map keeps its room. [extra]
 * goes between the status and the buttons (the cleaning mode row) and gets a raised surface colour
 * that reads on every tone of the card. [hint] is shown under the buttons while nothing runs.
 */
@Composable
fun StatusCard(
    live: LiveState,
    progress: JobProgress?,
    canLocate: Boolean,
    busyAction: String?,
    selectedRooms: Int,
    onToggle: () -> Unit,
    onCleanRooms: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    extra: (@Composable (raised: Color) -> Unit)? = null,
    hint: String? = null,
) {
    val state = live.state
    val status = state?.status?.value ?: StatusValue.UNKNOWN
    val offline = live.connection is Connection.Offline
    // A state from an earlier connection waits to be confirmed; usually under a second.
    val stale = live.connection == Connection.Connecting && state != null
    val online = live.connection == Connection.Online
    val tone = statusTone(status, offline || stale || state == null)
    val container by animateColorAsState(tone.container, label = "status container")
    val content by animateColorAsState(tone.content, label = "status content")
    val accent by animateColorAsState(tone.accent, label = "status accent")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobotAvatar(
                    status = status,
                    dimmed = !online,
                    color = accent,
                    cutout = container,
                    modifier = Modifier.size(if (compact) COMPACT_AVATAR else 72.dp),
                )
                Spacer(Modifier.width(16.dp))
                // Polite live region: TalkBack announces "Paused", "Returning to dock" as they happen.
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Headline(state, offline, compact)
                    when {
                        offline -> ConnectionLine(R.string.status_reconnecting)
                        stale -> DelayedConnectionLine(R.string.status_updating)
                        state != null -> SupportingLine(state, progress)
                    }
                }
            }
            if (extra != null) {
                Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                extra(tone.button)
            }
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            Controls(state, online, canLocate, busyAction, selectedRooms, tone.button to tone.onButton, onToggle, onCleanRooms, onStop, onDock, onLocate)
            val idle = state?.status?.value.let { it == null || !(it.isActive || it == StatusValue.PAUSED) }
            if (hint != null && idle) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** [accent] draws the robot: the brand colour on the neutral card, the content colour on tinted ones. */
private data class Tone(
    val container: Color,
    val content: Color,
    val accent: Color = content,
    /** Secondary buttons need their own surface on tinted cards, where tonal buttons would vanish. */
    val button: Color,
    val onButton: Color,
)

@Composable
private fun statusTone(status: StatusValue, unavailable: Boolean): Tone {
    val colors = MaterialTheme.colorScheme
    // A raised surface for the round buttons: white on light cards; on dark cards the lightest
    // surface, because the "lowest" one is near-black and the buttons would look like holes.
    val raised = if (colors.surface.luminance() < 0.5f) colors.surfaceContainerHighest else colors.surfaceContainerLowest
    return when {
        unavailable -> Tone(colors.surfaceContainer, colors.onSurfaceVariant, button = colors.secondaryContainer, onButton = colors.onSecondaryContainer)
        status == StatusValue.ERROR -> Tone(colors.errorContainer, colors.onErrorContainer, button = raised, onButton = colors.onSurface)
        status.isActive -> Tone(colors.primaryContainer, colors.onPrimaryContainer, button = raised, onButton = colors.onSurface)
        status == StatusValue.PAUSED -> Tone(colors.secondaryContainer, colors.onSecondaryContainer, button = raised, onButton = colors.onSurface)
        else -> Tone(colors.surfaceContainerHigh, colors.onSurface, colors.primary, button = raised, onButton = colors.onSurface)
    }
}

@Composable
private fun Headline(state: RobotState?, offline: Boolean, compact: Boolean) {
    val context = LocalContext.current
    val text = when {
        state == null && offline -> stringResource(R.string.status_offline)
        state == null -> stringResource(R.string.status_connecting)
        else -> context.getString(Labels.status(state.status.value))
    }
    AnimatedContent(targetState = text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { value ->
        Text(value, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Selected rooms · 12 minutes · 8 m²" next to the battery; while docked, what the dock is doing. */
@Composable
private fun SupportingLine(state: RobotState, progress: JobProgress?) {
    val context = LocalContext.current
    // While docked, say what the dock is doing (emptying, washing, drying) instead of the job flag.
    val dockActivity = state.dockStatus?.takeIf { state.status.value == StatusValue.DOCKED && it != "idle" }
    val activity = dockActivity?.let { Labels.dockStatus(context, it) }
        ?: Labels.statusFlag(state.status.flag)?.let(context::getString)
    val parts = listOfNotNull(
        activity,
        // Skip "0 minutes · 0 m²" at the start of a run.
        progress?.seconds?.takeIf { it >= SECONDS_PER_MINUTE }?.let { durationText(it) },
        progress?.areaCm2?.takeIf { it >= CM2_PER_M2 }?.let { areaText(it) },
    )
    val battery = state.battery
    // Top-aligned: when the line wraps, the battery icon stays next to the percentage.
    Row(verticalAlignment = Alignment.Top) {
        if (battery != null) {
            Icon(
                if (battery.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
                tint = if (battery.level < LOW_BATTERY) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
            Spacer(Modifier.width(4.dp))
        }
        val batteryText = battery?.let {
            stringResource(if (it.isCharging) R.string.battery_charging else R.string.battery_percent, it.level)
        }
        val text = listOfNotNull(batteryText) + parts
        Text(
            text.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionLine(text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Appears only if confirming takes long, so a normal reconnect does not flash a message. */
@Composable
private fun DelayedConnectionLine(text: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STALE_HINT_DELAY_MS)
        visible = true
    }
    if (visible) ConnectionLine(text)
}

/**
 * One main action plus round buttons that only appear when they make sense: no "dock" while
 * docked, "resume" for a paused job, "stop" only when there is a job. Selected rooms turn
 * "clean all" into "clean n rooms", so the screen has a single start button.
 */
@Composable
private fun Controls(
    state: RobotState?,
    enabled: Boolean,
    canLocate: Boolean,
    busyAction: String?,
    selectedRooms: Int,
    secondary: Pair<Color, Color>,
    onToggle: () -> Unit,
    onCleanRooms: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onLocate: () -> Unit,
) {
    val (secondaryContainer, onSecondary) = secondary
    val status = state?.status?.value ?: StatusValue.UNKNOWN
    val hasJob = status.isActive || status == StatusValue.PAUSED
    val action = when {
        status.isActive -> MainAction.PAUSE
        status == StatusValue.PAUSED -> MainAction.RESUME
        selectedRooms > 0 -> MainAction.CLEAN_ROOMS
        else -> MainAction.CLEAN_ALL
    }
    val busy = busyAction == HomeViewModel.ACTION_TOGGLE || busyAction == HomeViewModel.ACTION_CLEAN_ROOMS
    val onMain = if (action == MainAction.CLEAN_ROOMS) onCleanRooms else onToggle
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val buttonModifier = Modifier.weight(1f).height(BUTTON_HEIGHT)
        val label: @Composable () -> Unit = {
            AnimatedContent(
                targetState = action,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) using SizeTransform(clip = false) },
                label = "main action",
            ) { current -> ButtonContent(current.icon, mainLabel(current, selectedRooms), busy) }
        }
        // A running job should not shout: pausing is a tonal button, starting a filled one.
        if (action == MainAction.PAUSE) {
            FilledTonalButton(
                onClick = onMain,
                enabled = enabled,
                modifier = buttonModifier,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = secondaryContainer, contentColor = onSecondary),
            ) { label() }
        } else {
            Button(onClick = onMain, enabled = enabled, modifier = buttonModifier, shape = CircleShape) { label() }
        }
        if (status != StatusValue.DOCKED && status != StatusValue.UNKNOWN) {
            RoundAction(Icons.Filled.Home, stringResource(R.string.action_dock), enabled, busyAction == HomeViewModel.ACTION_DOCK, secondary, onDock)
        }
        if (hasJob) {
            RoundAction(Icons.Filled.Stop, stringResource(R.string.action_stop), enabled, busyAction == HomeViewModel.ACTION_STOP, secondary, onStop)
        }
        if (canLocate) {
            RoundAction(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.action_locate), enabled, busyAction == HomeViewModel.ACTION_LOCATE, secondary, onLocate)
        }
    }
}

private enum class MainAction(val icon: ImageVector) {
    PAUSE(Icons.Filled.Pause),
    RESUME(Icons.Filled.PlayArrow),
    CLEAN_ALL(Icons.Filled.PlayArrow),
    CLEAN_ROOMS(Icons.Outlined.CleaningServices),
}

@Composable
private fun mainLabel(action: MainAction, rooms: Int): String = when (action) {
    MainAction.PAUSE -> stringResource(R.string.action_pause)
    MainAction.RESUME -> stringResource(R.string.action_resume)
    MainAction.CLEAN_ALL -> stringResource(R.string.action_clean_all)
    MainAction.CLEAN_ROOMS -> pluralStringResource(R.plurals.home_clean_rooms, rooms, rooms)
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String, busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, enabled: Boolean, busy: Boolean, colors: Pair<Color, Color>, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(BUTTON_HEIGHT),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.first, contentColor = colors.second),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(icon, contentDescription = label)
        }
    }
}

/**
 * A robot vacuum seen from above: round body, front bumper seam and the lidar turret. While the robot
 * works, a soft sweep circles it. Decorative; the headline says the same in words.
 */
@Composable
private fun RobotAvatar(status: StatusValue, dimmed: Boolean, color: Color, cutout: Color, modifier: Modifier = Modifier) {
    val ink = color.copy(alpha = if (dimmed) 0.5f else 1f)
    val active = status.isActive && !dimmed
    val sweep by rememberInfiniteTransition(label = "robot").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "sweep",
    )
    Canvas(modifier) {
        val radius = size.minDimension / 2
        // Tonal disc behind the robot, so it sits on every container colour.
        drawCircle(ink.copy(alpha = 0.10f), radius)
        if (active) {
            rotate(sweep) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, ink.copy(alpha = 0.30f))),
                    startAngle = 0f,
                    sweepAngle = 100f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }
        // Filled body; the bumper seam and the lidar turret are cut out in the card colour.
        val body = radius * 0.64f
        drawCircle(ink, body)
        val seam = body * 0.80f
        drawArc(
            color = cutout,
            startAngle = -155f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = center - Offset(seam, seam),
            size = Size(seam * 2, seam * 2),
            style = Stroke(body * 0.07f, cap = StrokeCap.Round),
        )
        val turret = center.copy(y = center.y + body * 0.10f)
        drawCircle(cutout, body * 0.30f, center = turret)
        drawCircle(ink, body * 0.17f, center = turret)
    }
}

private val BUTTON_HEIGHT = 56.dp
private val COMPACT_AVATAR = 48.dp
private const val SECONDARY_ALPHA = 0.8f
private const val LOW_BATTERY = 20
private const val SECONDS_PER_MINUTE = 60L
private const val CM2_PER_M2 = 10_000L
private const val STALE_HINT_DELAY_MS = 1_500L
