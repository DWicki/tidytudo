package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.api.model.StateParser
import io.github.dwicki.tidytudo.api.model.StatusValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateParserTest {

    private fun parse(json: String) = StateParser.parse(Json.parseToJsonElement(json))

    /** Recorded from a Valetudo 2026.08.0 robot (no personal data in state attributes). */
    private val recorded = javaClass.getResource("/state_attributes.json")!!.readText()

    @Test
    fun `recorded state is decoded`() {
        val state = parse(recorded)
        assertEquals(StatusValue.DOCKED, state.status.value)
        assertEquals(98, state.battery?.level)
        assertEquals("high", state.fanSpeed)
        assertEquals("high", state.waterUsage)
        assertEquals("vacuum", state.operationMode)
        assertEquals("idle", state.dockStatus)
        assertEquals(4, state.dockComponents.size)
        assertFalse(state.attachments.single().attached)
    }

    @Test
    fun `error details are read only in error state`() {
        val state = parse(
            """[{"__class":"StatusStateAttribute","value":"error","flag":"none",
               "error":{"severity":{"kind":"transient","level":"error"},"subsystem":"navigation","message":"Robot stuck","vendorErrorCode":"12"}}]""",
        )
        assertEquals(StatusValue.ERROR, state.status.value)
        assertEquals("Robot stuck", state.status.error?.message)
        assertEquals("navigation", state.status.error?.subsystem)
    }

    @Test
    fun `unknown classes and values do not break parsing`() {
        val state = parse(
            """[{"__class":"FutureStateAttribute","x":1},
               {"__class":"StatusStateAttribute","value":"teleporting","flag":"warp"},
               {"__class":"BatteryStateAttribute","level":"not a number"},
               "garbage"]""",
        )
        assertEquals(StatusValue.UNKNOWN, state.status.value)
        assertEquals("warp", state.status.flag)
        assertEquals(0, state.battery?.level)
    }

    @Test
    fun `non-array input gives empty state`() {
        val state = parse("""{"oops":true}""")
        assertNull(state.battery)
        assertTrue(state.presets.isEmpty())
    }
}
