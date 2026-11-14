package io.github.dwicki.tidytudo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.Connection
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.ui.Labels

/** The big card at the top of the home screen: animated robot, status in words, battery ring. */
@Composable
fun StatusHero(live: LiveState, modifier: Modifier = Modifier) {
    val state = live.state
    val offline = live.connection is Connection.Offline
    val colors = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RobotAvatar(
                status = state?.status?.value ?: StatusValue.UNKNOWN,
                batteryLevel = state?.battery?.level,
                offline = offline,
                modifier = Modifier.size(104.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusHeadline(state, offline)
                BatteryLine(state)
                if (offline) OfflineChip()
            }
        }
    }
}

@Composable
private fun StatusHeadline(state: RobotState?, offline: Boolean) {
    val context = LocalContext.current
    val headline = when {
        state == null && offline -> stringResource(R.string.status_offline)
        state == null -> stringResource(R.string.status_connecting)
        else -> context.getString(Labels.status(state.status.value))
    }
    val flag = state?.status?.flag?.let(Labels::statusFlag)?.let(context::getString)
    AnimatedContent(targetState = headline, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { text ->
        Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    if (flag != null) {
        Text(flag, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun BatteryLine(state: RobotState?) {
    val battery = state?.battery ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (battery.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(4.dp))
        val text = if (battery.isCharging) {
            stringResource(R.string.battery_charging, battery.level)
        } else {
            stringResource(R.string.battery_percent, battery.level)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun OfflineChip() {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.status_reconnecting), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Top-down robot drawn on a canvas, surrounded by a battery ring. While cleaning, a sweep arc
 * circles it; while charging, the ring breathes. Decorative, so TalkBack hears only the battery.
 */
@Composable
fun RobotAvatar(status: StatusValue, batteryLevel: Int?, offline: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "robot")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "breathe",
    )
    val level by animateFloatAsState((batteryLevel ?: 0) / 100f, label = "battery")
    val active = status.isActive && !offline
    val charging = status == StatusValue.DOCKED && (batteryLevel ?: 100) < 100
    val body = if (offline) colors.outline else colors.primary
    val description = batteryLevel?.let { stringResource(R.string.battery_percent, it) }.orEmpty()

    Canvas(modifier.semantics { contentDescription = description }) {
        val ringWidth = size.minDimension * 0.07f
        val ringInset = ringWidth / 2
        val ringSize = Size(size.width - ringWidth, size.height - ringWidth)
        val ringOffset = Offset(ringInset, ringInset)
        drawArc(colors.surface.copy(alpha = 0.5f), 0f, 360f, false, ringOffset, ringSize, style = Stroke(ringWidth))
        drawArc(
            color = colors.tertiary.copy(alpha = if (charging) breathe else 1f),
            startAngle = -90f,
            sweepAngle = 360f * level,
            useCenter = false,
            topLeft = ringOffset,
            size = ringSize,
            style = Stroke(ringWidth, cap = StrokeCap.Round),
        )
        val robotRadius = size.minDimension * 0.30f
        if (active) {
            rotate(sweep) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, colors.primary.copy(alpha = 0.35f))),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = center - Offset(robotRadius * 1.35f, robotRadius * 1.35f),
                    size = Size(robotRadius * 2.7f, robotRadius * 2.7f),
                )
            }
        }
        drawCircle(colors.surface, robotRadius)
        drawCircle(body, robotRadius, style = Stroke(robotRadius * 0.16f))
        drawCircle(body, robotRadius * 0.30f, center = center.copy(y = center.y - robotRadius * 0.28f))
    }
}
