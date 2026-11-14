package io.github.dwicki.tidytudo.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/** What a finger landed on when a gesture started. */
sealed interface ShapeHandle {
    val shapeId: Int

    data class Body(override val shapeId: Int) : ShapeHandle
    data class RectCorner(override val shapeId: Int) : ShapeHandle
    data class LineEnd(override val shapeId: Int, val isStart: Boolean) : ShapeHandle
}

/** Callbacks of [mapGestures]; positions are in screen pixels. */
interface MapGestureListener {
    fun hitTest(screen: Offset): ShapeHandle?
    fun onDragHandle(handle: ShapeHandle, screenDelta: Offset)
    fun onTransform(zoom: Float, pan: Offset, centroid: Offset)
    fun onTap(screen: Offset)
}

/**
 * One gesture loop for the whole map, because handle dragging and map panning must decide per
 * gesture who gets the finger: a touch that starts on a shape handle drags it, anything else
 * pans/zooms the map, and a short touch without movement is a tap. There is no double-tap,
 * because waiting for it would delay every room selection; a "fit" button resets the view instead.
 */
suspend fun PointerInputScope.mapGestures(listener: MapGestureListener) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val handle = listener.hitTest(down.position)
        var moved = false
        var totalMovement = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.any { it.pressed }
            if (!pressed) break
            if (handle != null && event.changes.size == 1) {
                val change = event.changes.first()
                val delta = change.position - change.previousPosition
                if (delta != Offset.Zero) {
                    listener.onDragHandle(handle, delta)
                    moved = true
                    change.consume()
                }
            } else {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                totalMovement += pan
                if (zoom != 1f || pan != Offset.Zero) {
                    if (moved || abs(zoom - 1f) > 0.01f || totalMovement.getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                        listener.onTransform(zoom, pan, event.calculateCentroid(useCurrent = true))
                    }
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (true)

        if (!moved) listener.onTap(down.position)
    }
}
