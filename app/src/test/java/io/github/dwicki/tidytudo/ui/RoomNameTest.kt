package io.github.dwicki.tidytudo.ui

import io.github.dwicki.tidytudo.ui.rooms.RoomName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomNameTest {

    @Test
    fun `names are trimmed and inner whitespace collapsed`() {
        assertEquals("Living room", RoomName.normalize("  Living   room "))
        assertNull(RoomName.normalize("   "))
    }

    @Test
    fun `names are cut to the maximum length`() {
        assertEquals(RoomName.MAX_LENGTH, RoomName.normalize("x".repeat(50))!!.length)
    }

    @Test
    fun `save needs a non-empty name that differs from the current one`() {
        assertTrue(RoomName.canSave("Kitchen", "Küche"))
        assertTrue(RoomName.canSave("Kitchen", null))
        assertFalse(RoomName.canSave("Küche ", "Küche"))
        assertFalse(RoomName.canSave("", "Küche"))
    }
}
