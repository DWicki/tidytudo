package io.github.dwicki.tidytudo.map

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/** Hoisted zoom/pan state, so the screen can offer a "fit to screen" button. */
class MapViewState {
    var viewport: MapViewport? by mutableStateOf(null)

    /** Height in pixels of controls drawn over the top of the map; fitting keeps the map below them. */
    var topInset: Float = 0f

    /** Height in pixels of the panel over the bottom of the map, if one is shown. */
    var bottomInset: Float = 0f

    var canvasSize: Size by mutableStateOf(Size.Zero)
        internal set

    fun fit(map: RobotMap) {
        val bounds = map.bounds ?: return
        if (canvasSize.minDimension > 0f) viewport = MapViewport.fit(bounds, canvasSize, topInset, bottomInset)
    }

    /** The point in the middle of the screen, in centimetres; new shapes are placed there. */
    fun visibleCenter(map: RobotMap): CmPoint? {
        val bounds = map.bounds ?: return null
        val current = viewport ?: return null
        val local = current.toLocal(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
        return MapProjection(map.pixelSize, bounds).toCm(local)
    }
}

@Composable
fun rememberMapViewState() = remember { MapViewState() }

/**
 * Draws a Valetudo map with pan and pinch-zoom (F-3.2, F-3.3) and reports taps in centimetres
 * together with the room under the finger. [shapes] are drawn with handles and can be dragged;
 * changes come back through [onShapeChange].
 */
@Composable
fun MapCanvas(
    map: RobotMap,
    selectedSegments: Set<String>,
    shapes: List<EditableShape>,
    viewState: MapViewState,
    description: String,
    onTap: (point: CmPoint, segmentId: String?) -> Unit,
    onShapeChange: (EditableShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberMapPalette()
    val overlay = rememberOverlayStyle()
    val textMeasurer = rememberTextMeasurer()
    val bounds = map.bounds ?: return
    val projection = remember(map) { MapProjection(map.pixelSize, bounds) }
    val bitmap = remember(map, palette, selectedSegments) { MapBitmap.render(map, palette, selectedSegments) }
    val currentShapes by rememberUpdatedState(shapes)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnShapeChange by rememberUpdatedState(onShapeChange)

    val gestureListener = remember(projection, viewState) {
        ShapeGestureHandler(
            projection = projection,
            viewState = viewState,
            shapes = { currentShapes },
            onTap = { point, segment -> currentOnTap(point, segment) },
            onShapeChange = { currentOnShapeChange(it) },
            segmentAt = map::segmentAt,
        )
    }

    Canvas(
        modifier
            .onSizeChanged { size ->
                viewState.canvasSize = size.toSize()
                if (viewState.viewport == null) viewState.fit(map)
            }
            .pointerInput(gestureListener) { mapGestures(gestureListener) }
            .semantics { contentDescription = description },
    ) {
        val viewport = viewState.viewport ?: return@Canvas
        withTransform({
            translate(viewport.offset.x, viewport.offset.y)
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
        }) {
            bitmap?.let {
                drawImage(
                    it,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(it.width, it.height),
                    dstSize = IntSize(it.width, it.height),
                    filterQuality = FilterQuality.None,
                )
            }
            drawEntities(map, projection, overlay, viewport.scale)
        }
        drawRoomLabels(map, projection, viewport, textMeasurer, overlay)
        drawRobotMarker(map, projection, viewport, overlay)
        drawShapes(shapes, projection, viewport, overlay)
    }
}

/** Translates gestures on the canvas into taps and shape edits; all geometry lives here, not in the composable. */
private class ShapeGestureHandler(
    private val projection: MapProjection,
    private val viewState: MapViewState,
    private val shapes: () -> List<EditableShape>,
    private val onTap: (CmPoint, String?) -> Unit,
    private val onShapeChange: (EditableShape) -> Unit,
    private val segmentAt: (Int, Int) -> SegmentLayer?,
) : MapGestureListener {

    private val viewport get() = viewState.viewport

    override fun hitTest(screen: Offset): ShapeHandle? {
        val viewport = viewport ?: return null
        val radius = HANDLE_TOUCH_RADIUS_PX
        fun near(point: CmPoint) = (viewport.toScreen(projection.toLocal(point)) - screen).getDistance() < radius
        for (shape in shapes().asReversed()) {
            when (shape) {
                is EditableShape.Rect -> {
                    if (near(CmPoint(shape.right, shape.bottom))) return ShapeHandle.RectCorner(shape.id)
                    val topLeft = viewport.toScreen(projection.toLocal(CmPoint(shape.left, shape.top)))
                    val bottomRight = viewport.toScreen(projection.toLocal(CmPoint(shape.right, shape.bottom)))
                    if (screen.x in topLeft.x..bottomRight.x && screen.y in topLeft.y..bottomRight.y) return ShapeHandle.Body(shape.id)
                }
                is EditableShape.Line -> {
                    if (near(shape.start)) return ShapeHandle.LineEnd(shape.id, isStart = true)
                    if (near(shape.end)) return ShapeHandle.LineEnd(shape.id, isStart = false)
                }
            }
        }
        return null
    }

    override fun onDragHandle(handle: ShapeHandle, screenDelta: Offset) {
        val viewport = viewport ?: return
        val shape = shapes().firstOrNull { it.id == handle.shapeId } ?: return
        val deltaCm = projection.deltaToCm(screenDelta / viewport.scale)
        val updated = when {
            shape is EditableShape.Rect && handle is ShapeHandle.Body -> shape.moved(deltaCm.x, deltaCm.y)
            shape is EditableShape.Rect && handle is ShapeHandle.RectCorner -> shape.copy(
                right = maxOf(shape.right + deltaCm.x, shape.left + MIN_SHAPE_CM),
                bottom = maxOf(shape.bottom + deltaCm.y, shape.top + MIN_SHAPE_CM),
            )
            shape is EditableShape.Line && handle is ShapeHandle.LineEnd && handle.isStart ->
                shape.copy(start = CmPoint(shape.start.x + deltaCm.x, shape.start.y + deltaCm.y))
            shape is EditableShape.Line && handle is ShapeHandle.LineEnd ->
                shape.copy(end = CmPoint(shape.end.x + deltaCm.x, shape.end.y + deltaCm.y))
            else -> return
        }
        onShapeChange(updated)
    }

    override fun onTransform(zoom: Float, pan: Offset, centroid: Offset) {
        val viewport = viewport ?: return
        val fitScale = viewState.canvasSize.minDimension / 2_000f
        viewState.viewport = viewport.zoomed(zoom, centroid, minScale = fitScale, maxScale = MAX_SCALE).panned(pan)
    }

    override fun onTap(screen: Offset) {
        val viewport = viewport ?: return
        val local = viewport.toLocal(screen)
        val (pixelX, pixelY) = projection.toPixel(local)
        onTap(projection.toCm(local), segmentAt(pixelX, pixelY)?.id)
    }

    private companion object {
        const val HANDLE_TOUCH_RADIUS_PX = 64f
        const val MIN_SHAPE_CM = 30f
        const val MAX_SCALE = 60f
    }
}
