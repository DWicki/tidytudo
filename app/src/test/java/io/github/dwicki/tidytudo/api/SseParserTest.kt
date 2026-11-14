package io.github.dwicki.tidytudo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {

    private fun parseAll(vararg lines: String): List<SseEvent> {
        val parser = SseParser()
        return lines.mapNotNull(parser::feed)
    }

    @Test
    fun `named event with data is emitted on blank line`() {
        val events = parseAll("event: StateAttributesUpdated", "data: [1,2]", "")
        assertEquals(listOf(SseEvent("StateAttributesUpdated", "[1,2]")), events)
    }

    @Test
    fun `keep-alive comments are ignored`() {
        assertEquals(emptyList<SseEvent>(), parseAll(": sse-keep-alive", ""))
    }

    @Test
    fun `multiple data lines are joined with newline`() {
        val events = parseAll("data: a", "data: b", "")
        assertEquals("a\nb", events.single().data)
        assertEquals("message", events.single().name)
    }

    @Test
    fun `event name resets after dispatch`() {
        val events = parseAll("event: MapUpdated", "data: x", "", "data: y", "")
        assertEquals(listOf("MapUpdated", "message"), events.map { it.name })
    }

    @Test
    fun `value without space after colon is kept`() {
        val parser = SseParser()
        assertNull(parser.feed("data:{}"))
        assertEquals("{}", parser.feed("")?.data)
    }
}
