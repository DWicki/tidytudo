package io.github.dwicki.tidytudo.map

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MapColorSlotsTest {

    /** A filled rectangle of map pixels, one run per row. */
    private fun room(id: String, left: Int, top: Int, right: Int, bottom: Int): SegmentLayer {
        val rows = (top..bottom).toList()
        val layer = PixelLayer(
            IntArray(rows.size) { left },
            rows.toIntArray(),
            IntArray(rows.size) { right - left + 1 },
        )
        return SegmentLayer(id, id, active = false, material = null, pixels = layer)
    }

    @Test
    fun `rooms that share a wall get different colours`() {
        // Living room on top of three rooms in a row, separated by two-pixel walls.
        val rooms = listOf(
            room("living", 0, 0, 98, 40),
            room("bed", 0, 43, 30, 80),
            room("bath", 33, 43, 60, 80),
            room("office", 63, 43, 98, 80),
        )
        val slots = MapBitmap.colorSlots(rooms, 6)
        val touching = listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2, 2 to 3)
        touching.forEach { (a, b) -> assertNotEquals("${rooms[a].id} / ${rooms[b].id}", slots[a], slots[b]) }
    }

    @Test
    fun `rooms far apart may share a colour but slots are spread`() {
        val rooms = listOf(room("a", 0, 0, 10, 10), room("b", 100, 100, 110, 110))
        val slots = MapBitmap.colorSlots(rooms, 6)
        assertEquals(listOf(0, 1), slots.toList())
    }

    @Test
    fun `more neighbours than colours still gives every room a slot`() {
        val rooms = (0 until 4).map { room("r$it", 0, 0, 10, 10) }
        val slots = MapBitmap.colorSlots(rooms, 2)
        assert(slots.all { it in 0 until 2 })
    }
}
