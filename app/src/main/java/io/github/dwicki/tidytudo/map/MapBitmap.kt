package io.github.dwicki.tidytudo.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/** Colours for the pixel layers, derived from the current Material colour scheme. */
data class MapPalette(
    val floor: Color,
    val wall: Color,
    val segments: List<Color>,
    val selectedSegment: Color,
    /** Unselected rooms while choosing rooms: one neutral tone, so the selected ones stand out. */
    val neutralSegment: Color,
)

/**
 * Rasterises floor, rooms and walls into one bitmap at one map pixel per bitmap pixel (ADR-4).
 * Pixel layers change rarely, so this runs only on map updates or selection changes, and each
 * frame just draws the scaled bitmap plus a few vector entities.
 */
object MapBitmap {

    fun render(map: RobotMap, palette: MapPalette, selectedSegments: Set<String>, neutralRooms: Boolean = false): ImageBitmap? {
        val bounds = map.bounds ?: return null
        val pixels = IntArray(bounds.width * bounds.height)
        map.floor?.let { fill(pixels, bounds, it, palette.floor.toArgb()) }
        map.segments.forEachIndexed { index, segment ->
            val color = when {
                segment.id in selectedSegments -> palette.selectedSegment
                neutralRooms -> palette.neutralSegment
                else -> palette.segments[index % palette.segments.size]
            }
            fill(pixels, bounds, segment.pixels, color.toArgb())
        }
        map.walls?.let { fill(pixels, bounds, it, palette.wall.toArgb()) }
        val bitmap = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, bounds.width, 0, 0, bounds.width, bounds.height)
        return bitmap.asImageBitmap()
    }

    private fun fill(target: IntArray, bounds: PixelBounds, layer: PixelLayer, color: Int) {
        layer.forEachRun { x, y, length ->
            val row = y - bounds.minY
            if (row < 0 || row >= bounds.height) return@forEachRun
            val start = (x - bounds.minX).coerceAtLeast(0)
            val end = (x - bounds.minX + length).coerceAtMost(bounds.width)
            if (start < end) target.fill(color, row * bounds.width + start, row * bounds.width + end)
        }
    }
}
