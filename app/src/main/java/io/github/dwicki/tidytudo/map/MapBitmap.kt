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
        val slots = colorSlots(map.segments, palette.segments.size)
        map.segments.forEachIndexed { index, segment ->
            val color = when {
                segment.id in selectedSegments -> palette.selectedSegment
                neutralRooms -> palette.neutralSegment
                else -> palette.segments[slots[index]]
            }
            fill(pixels, bounds, segment.pixels, color.toArgb())
        }
        map.walls?.let { fill(pixels, bounds, it, palette.wall.toArgb()) }
        val bitmap = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, bounds.width, 0, 0, bounds.width, bounds.height)
        return bitmap.asImageBitmap()
    }

    /**
     * One palette slot per room, so rooms that touch never share a colour (as far as the slots
     * allow), spreading the slots evenly otherwise. Rooms touch when their bounding boxes come within
     * a wall's width of each other; an over-estimate for L-shaped rooms, which is harmless here.
     */
    internal fun colorSlots(segments: List<SegmentLayer>, slots: Int): IntArray {
        val boxes = segments.map { if (it.pixels.count > 0) it.pixels.bounds else null }
        val result = IntArray(segments.size) { -1 }
        val uses = IntArray(slots)
        segments.indices.forEach { i ->
            val box = boxes[i]
            val taken = segments.indices
                .filter { j -> result[j] >= 0 && box != null && boxes[j]?.let { box.touches(it, NEIGHBOUR_GAP_PX) } == true }
                .map { result[it] }
                .toSet()
            val slot = (0 until slots).filter { it !in taken }.minByOrNull { uses[it] } ?: (i % slots)
            result[i] = slot
            uses[slot]++
        }
        return result
    }

    private const val NEIGHBOUR_GAP_PX = 4

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
