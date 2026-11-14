package io.github.dwicki.tidytudo.data

import io.github.dwicki.tidytudo.data.history.SessionRecorder
import io.github.dwicki.tidytudo.map.MapParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SessionFingerprintTest {

    /** Two rooms side by side (pixels 0–9 and 10–19 on rows 0–9), pixel size 5 cm. */
    private fun map(pathCm: List<Int>, obstacleIds: List<String> = emptyList()) = MapParser.parse(
        Json.parseToJsonElement(
            """{"pixelSize":5,"layers":[
                {"type":"segment","metaData":{"segmentId":"1","name":"Kitchen"},"compressedPixels":[0,0,10,0,1,10,0,2,10,0,3,10,0,4,10,0,5,10,0,6,10,0,7,10,0,8,10,0,9,10]},
                {"type":"segment","metaData":{"segmentId":"2","name":"Office"},"compressedPixels":[10,0,10,10,1,10,10,2,10,10,3,10,10,4,10,10,5,10,10,6,10,10,7,10,10,8,10,10,9,10]}],
               "entities":[
                {"__class":"PathMapEntity","type":"path","points":${pathCm}}
                ${obstacleIds.joinToString("") { """,{"__class":"PointMapEntity","type":"obstacle","points":[10,10],"metaData":{"id":"$it"}}""" }}]}""",
        ),
    )

    private fun pathIn(room: Int, points: Int): List<Int> =
        List(points) { index -> listOf(room * 50 + 5 + index % 40, 5 + index % 40) }.flatten()

    @Test
    fun `same run gives the same fingerprint, a new run a different one`() {
        val run = map(pathIn(0, 30), listOf("b", "a"))
        assertEquals(SessionRecorder.fingerprintOf(run), SessionRecorder.fingerprintOf(map(pathIn(0, 30), listOf("a", "b"))))
        assertNotEquals(SessionRecorder.fingerprintOf(run), SessionRecorder.fingerprintOf(map(pathIn(0, 31), listOf("a", "b"))))
        assertNotEquals(SessionRecorder.fingerprintOf(run), SessionRecorder.fingerprintOf(map(pathIn(0, 30), listOf("a", "c"))))
    }

    @Test
    fun `rooms are taken from where the path went, ignoring a few stray points`() {
        val mostlyKitchen = map(pathIn(0, 50) + pathIn(1, 5))
        assertEquals(listOf("Kitchen"), mostlyKitchen.visitedSegments().map { it.name })

        val both = map(pathIn(0, 30) + pathIn(1, 30))
        assertEquals(listOf("Kitchen", "Office"), both.visitedSegments().map { it.name })
    }

    @Test
    fun `segment lookup uses the grid and handles points outside the map`() {
        val rooms = map(pathIn(0, 1))
        assertEquals("Office", rooms.segmentAt(15, 3)?.name)
        assertEquals(null, rooms.segmentAt(25, 3))
        assertEquals(null, rooms.segmentAt(-1, 0))
    }
}
