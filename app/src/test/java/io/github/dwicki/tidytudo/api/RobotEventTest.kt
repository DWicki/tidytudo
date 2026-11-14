package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.api.model.RobotEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RobotEventTest {

    @Test
    fun `events are classified and offer the right interactions`() {
        val events = RobotEvent.parseList(
            Json.parseToJsonElement(
                """[{"__class":"ConsumableDepletedValetudoEvent","id":"c1","timestamp":"2026-11-14T21:36:09.760Z","processed":false,"type":"cleaning","subType":"wheel"},
                   {"__class":"PendingMapChangeValetudoEvent","id":"p1","timestamp":"t","processed":false},
                   {"__class":"BrandNewValetudoEvent","id":"n1","timestamp":"t","processed":true},
                   {"__class":"DustBinFullValetudoEvent","timestamp":"no id"}]""",
            ),
        )
        assertEquals(listOf("c1", "p1", "n1"), events.map { it.id })
        assertEquals(RobotEvent.Kind.CONSUMABLE_DEPLETED, events[0].kind)
        assertEquals("wheel", events[0].consumableSubType)
        assertEquals(listOf("reset", "ok"), events[0].interactions)
        assertEquals(listOf("yes", "no"), events[1].interactions)
        assertEquals(RobotEvent.Kind.OTHER, events[2].kind)
        assertEquals("BrandNewValetudoEvent", events[2].className)
    }
}
