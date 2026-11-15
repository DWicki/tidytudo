package io.github.dwicki.tidytudo.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours for vector overlays; kept apart from [MapPalette] because these do not need a re-render. */
data class OverlayStyle(
    val path: Color,
    val robot: Color,
    val robotOutline: Color,
    val charger: Color,
    val noGo: Color,
    val noMop: Color,
    val wall: Color,
    val zone: Color,
    val obstacle: Color,
    val carpet: Color,
    val label: Color,
    val labelBackground: Color,
)

@Composable
fun rememberMapPalette(): MapPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        // Rooms in a few light tints of the two brand colours, all about equally light, so no room
        // looks disabled and the selection colour (a much stronger primary) is never mistaken for one.
        MapPalette(
            floor = colors.surfaceContainerHighest,
            wall = colors.onSurfaceVariant,
            segments = listOf(
                lerp(colors.surface, colors.primary, 0.20f),
                lerp(colors.surface, colors.tertiary, 0.24f),
                lerp(colors.surface, colors.primary, 0.32f),
                lerp(colors.surface, colors.tertiary, 0.36f),
                lerp(colors.surface, colors.primary, 0.26f),
                lerp(colors.surface, colors.tertiary, 0.30f),
            ),
            selectedSegment = lerp(colors.surface, colors.primary, 0.62f),
            neutralSegment = lerp(colors.surface, colors.onSurface, 0.10f),
        )
    }
}

@Composable
fun rememberOverlayStyle(): OverlayStyle {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        OverlayStyle(
            path = colors.primary.copy(alpha = 0.6f),
            robot = colors.surface,
            robotOutline = colors.primary,
            charger = colors.tertiary,
            // Fixed colours, as on other Valetudo clients: restrictions must read as "forbidden" in every theme.
            noGo = NO_GO_RED,
            noMop = NO_MOP_BLUE,
            wall = NO_GO_RED,
            zone = colors.primary,
            obstacle = colors.error,
            carpet = colors.onSurface.copy(alpha = 0.12f),
            label = colors.onSurface,
            labelBackground = colors.surface.copy(alpha = 0.9f),
        )
    }
}

/**
 * Draws entities in local pixel space; [scale] keeps line widths and icons constant on screen.
 * [dp] is one density-independent pixel in that space, so sizes match on every phone.
 */
fun DrawScope.drawEntities(map: RobotMap, projection: MapProjection, style: OverlayStyle, scale: Float) {
    val px = 1f / scale
    val dp = density * px
    map.carpets.forEach { drawPolygon(it, projection, style.carpet, fill = true, strokeWidth = 0f) }
    map.paths.forEach { drawPolyline(it, projection, style.path, PATH_WIDTH_DP * dp) }
    if (map.predictedPath.isNotEmpty()) {
        drawPolyline(map.predictedPath, projection, style.zone, 2f * dp, PathEffect.dashPathEffect(floatArrayOf(6f * dp, 4f * dp)))
    }
    map.noGoAreas.forEach { drawPolygon(it, projection, style.noGo, fill = true, strokeWidth = 1.5f * dp) }
    map.noMopAreas.forEach { drawPolygon(it, projection, style.noMop, fill = true, strokeWidth = 1.5f * dp) }
    map.activeZones.forEach { drawPolygon(it, projection, style.zone, fill = true, strokeWidth = 1.5f * dp) }
    map.virtualWalls.forEach { wall ->
        drawLine(style.wall, projection.toLocal(wall.start), projection.toLocal(wall.end), 3f * dp, StrokeCap.Round)
    }
    // Obstacles as small red dots with a light ring, visible on any room colour.
    map.obstacles.forEach {
        val center = projection.toLocal(it.position)
        drawCircle(style.robot, (OBSTACLE_RADIUS_DP + 1.5f) * dp, center)
        drawCircle(style.obstacle, OBSTACLE_RADIUS_DP * dp, center)
    }
    map.charger?.let { drawCharger(projection.toLocal(it.position), style, px) }
    map.goToTarget?.let { drawTarget(projection.toLocal(it), style.zone, px) }
}

/** The robot goes last, in screen space, so room labels never hide it. */
fun DrawScope.drawRobotMarker(map: RobotMap, projection: MapProjection, viewport: MapViewport, style: OverlayStyle) {
    val robot = map.robot ?: return
    drawRobot(viewport.toScreen(projection.toLocal(robot.position)), robot.angle, style, px = 1f)
}

/** Room names, drawn in screen space so text stays readable at every zoom level (F-3.5). */
fun DrawScope.drawRoomLabels(
    map: RobotMap,
    projection: MapProjection,
    viewport: MapViewport,
    textMeasurer: TextMeasurer,
    style: OverlayStyle,
) {
    if (viewport.scale < MIN_LABEL_SCALE) return
    val textStyle = TextStyle(color = style.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val padX = LABEL_PADDING_X_DP * density
    val padY = LABEL_PADDING_Y_DP * density
    map.segments.forEach { segment ->
        val name = segment.name ?: return@forEach
        val center = viewport.toScreen(projection.centroidLocal(segment.pixels))
        val layout = textMeasurer.measure(name, textStyle)
        val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
        val height = layout.size.height + padY * 2
        drawRoundRect(
            style.labelBackground,
            topLeft = topLeft - Offset(padX, padY),
            size = Size(layout.size.width + padX * 2, height),
            cornerRadius = CornerRadius(height / 2),
        )
        drawText(layout, topLeft = topLeft)
    }
}

/** Highlighted points, e.g. obstacles of a past run, as pins in screen space so they stay tappable. */
fun DrawScope.drawMarkers(markers: List<CmPoint>, projection: MapProjection, viewport: MapViewport, style: OverlayStyle) {
    val radius = MARKER_RADIUS_DP * density
    markers.forEach { point ->
        val center = viewport.toScreen(projection.toLocal(point))
        drawCircle(style.robot, radius + SHAPE_STROKE_DP * density, center)
        drawCircle(NO_GO_RED, radius, center)
        drawCircle(Color.White, radius * 0.35f, center)
    }
}

/** User-drawn shapes with drag handles, in screen space so handles stay finger-sized. */
fun DrawScope.drawShapes(shapes: List<EditableShape>, projection: MapProjection, viewport: MapViewport, style: OverlayStyle) {
    shapes.forEach { shape ->
        val color = when (shape.kind) {
            EditableShape.Kind.ZONE -> style.zone
            EditableShape.Kind.NO_GO, EditableShape.Kind.WALL -> style.noGo
            EditableShape.Kind.NO_MOP -> style.noMop
        }
        when (shape) {
            is EditableShape.Rect -> {
                val topLeft = viewport.toScreen(projection.toLocal(CmPoint(shape.left, shape.top)))
                val bottomRight = viewport.toScreen(projection.toLocal(CmPoint(shape.right, shape.bottom)))
                val rect = Rect(topLeft, bottomRight)
                drawRect(color.copy(alpha = 0.22f), rect.topLeft, rect.size)
                drawRect(color, rect.topLeft, rect.size, style = Stroke(SHAPE_STROKE_DP * density))
                drawHandle(bottomRight, color)
            }
            is EditableShape.Line -> {
                val start = viewport.toScreen(projection.toLocal(shape.start))
                val end = viewport.toScreen(projection.toLocal(shape.end))
                drawLine(color, start, end, SHAPE_STROKE_DP * 2 * density, StrokeCap.Round)
                drawHandle(start, color)
                drawHandle(end, color)
            }
        }
    }
}

private fun DrawScope.drawHandle(center: Offset, color: Color) {
    val radius = HANDLE_RADIUS_DP * density
    drawCircle(Color.White, radius, center)
    drawCircle(color, radius, center, style = Stroke(SHAPE_STROKE_DP * density))
}

private fun DrawScope.drawRobot(center: Offset, angle: Float, style: OverlayStyle, px: Float) {
    val radius = ROBOT_RADIUS_DP * px * density
    drawCircle(style.robotOutline.copy(alpha = 0.18f), radius * 1.8f, center)
    // A ring in the surface colour keeps the robot visible on a selected (primary-coloured) room.
    drawCircle(style.robot, radius * 1.3f, center)
    drawCircle(style.robot, radius, center)
    drawCircle(style.robotOutline, radius, center, style = Stroke(radius * 0.22f))
    // Valetudo's angle: 0° points up, clockwise, like its web UI's rotate().
    rotate(angle, pivot = center) {
        drawCircle(style.robotOutline, radius * 0.32f, center.copy(y = center.y - radius * 0.45f))
    }
}

private fun DrawScope.drawCharger(center: Offset, style: OverlayStyle, px: Float) {
    val radius = CHARGER_RADIUS_DP * px * density
    drawCircle(style.charger, radius, center)
    val bolt = Path().apply {
        moveTo(center.x + radius * 0.15f, center.y - radius * 0.6f)
        lineTo(center.x - radius * 0.35f, center.y + radius * 0.1f)
        lineTo(center.x, center.y + radius * 0.1f)
        lineTo(center.x - radius * 0.15f, center.y + radius * 0.6f)
        lineTo(center.x + radius * 0.35f, center.y - radius * 0.1f)
        lineTo(center.x, center.y - radius * 0.1f)
        close()
    }
    drawPath(bolt, style.robot)
}

private fun DrawScope.drawTarget(center: Offset, color: Color, px: Float) {
    val radius = TARGET_RADIUS_DP * px * density
    drawCircle(color.copy(alpha = 0.25f), radius * 1.6f, center)
    drawCircle(color, radius, center)
    drawCircle(Color.White, radius * 0.4f, center)
}

private fun DrawScope.drawPolyline(points: List<CmPoint>, projection: MapProjection, color: Color, width: Float, effect: PathEffect? = null) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val local = projection.toLocal(point)
        if (index == 0) path.moveTo(local.x, local.y) else path.lineTo(local.x, local.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect))
}

private fun DrawScope.drawPolygon(points: List<CmPoint>, projection: MapProjection, color: Color, fill: Boolean, strokeWidth: Float) {
    if (points.size < 3) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val local = projection.toLocal(point)
        if (index == 0) path.moveTo(local.x, local.y) else path.lineTo(local.x, local.y)
    }
    path.close()
    if (fill) drawPath(path, color.copy(alpha = color.alpha * 0.3f))
    if (strokeWidth > 0f) drawPath(path, color, style = Stroke(strokeWidth))
}

private val NO_GO_RED = Color(0xFFE5484D)
private val NO_MOP_BLUE = Color(0xFF3E8BFF)
private const val ROBOT_RADIUS_DP = 9f
private const val CHARGER_RADIUS_DP = 8f
private const val TARGET_RADIUS_DP = 7f
private const val HANDLE_RADIUS_DP = 11f
private const val MARKER_RADIUS_DP = 8f
private const val SHAPE_STROKE_DP = 2f
private const val LABEL_PADDING_X_DP = 8f
private const val LABEL_PADDING_Y_DP = 3f
private const val PATH_WIDTH_DP = 1.5f
private const val OBSTACLE_RADIUS_DP = 3.5f
private const val MIN_LABEL_SCALE = 0.8f
