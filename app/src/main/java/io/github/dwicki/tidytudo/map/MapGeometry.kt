package io.github.dwicki.tidytudo.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Converts between the three coordinate systems involved in drawing the map:
 * centimetres (API), local map pixels (bitmap, origin at the map's bounding box) and screen pixels.
 */
data class MapViewport(val scale: Float, val offset: Offset) {

    fun toScreen(local: Offset): Offset = local * scale + offset

    fun toLocal(screen: Offset): Offset = (screen - offset) / scale

    /** Zooms around [focus] (screen pixels) so the point under the fingers stays put. */
    fun zoomed(zoom: Float, focus: Offset, minScale: Float, maxScale: Float): MapViewport {
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        val factor = newScale / scale
        return MapViewport(newScale, focus - (focus - offset) * factor)
    }

    fun panned(delta: Offset) = copy(offset = offset + delta)

    companion object {
        /** Fits [bounds] into [canvas] with a margin, centred. */
        fun fit(bounds: PixelBounds, canvas: Size, margin: Float = 0.92f): MapViewport {
            val scale = min(canvas.width / bounds.width, canvas.height / bounds.height) * margin
            val offset = Offset(
                (canvas.width - bounds.width * scale) / 2f,
                (canvas.height - bounds.height * scale) / 2f,
            )
            return MapViewport(scale, offset)
        }
    }
}

/** Centimetre ↔ local-pixel conversion for one map. */
class MapProjection(private val pixelSize: Int, private val bounds: PixelBounds) {

    fun toLocal(point: CmPoint): Offset =
        Offset(point.x / pixelSize - bounds.minX, point.y / pixelSize - bounds.minY)

    fun toCm(local: Offset): CmPoint =
        CmPoint((local.x + bounds.minX) * pixelSize, (local.y + bounds.minY) * pixelSize)

    /** A movement in local pixels as a movement in centimetres (no origin shift). */
    fun deltaToCm(local: Offset): CmPoint = CmPoint(local.x * pixelSize, local.y * pixelSize)

    /** The map pixel (absolute, as used in layers) under a local position. */
    fun toPixel(local: Offset): Pair<Int, Int> = (local.x + bounds.minX).toInt() to (local.y + bounds.minY).toInt()

    fun centroidLocal(layer: PixelLayer): Offset {
        val (x, y) = layer.centroid
        return Offset(x - bounds.minX + 0.5f, y - bounds.minY + 0.5f)
    }
}
