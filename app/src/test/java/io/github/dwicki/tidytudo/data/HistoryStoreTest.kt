package io.github.dwicki.tidytudo.data

import io.github.dwicki.tidytudo.data.history.CleaningSession
import io.github.dwicki.tidytudo.data.history.HistoryStore
import io.github.dwicki.tidytudo.data.history.SessionObstacle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun session(id: String, robotId: String = "r1", startedAt: Long = 1_000L) = CleaningSession(
        id = id,
        robotId = robotId,
        startedAt = startedAt,
        endedAt = startedAt + 60_000,
        obstacles = listOf(SessionObstacle("o-1", "Sock (77%)", 10f, 20f, hasImage = true)),
        hasMap = true,
    )

    @Test
    fun `session with map and photo survives a restart`() = runBlocking {
        val photo = byteArrayOf(1, 2, 3)
        HistoryStore(folder.root).add(session("s1"), mapJson = """{"layers":[]}""", images = mapOf("o-1" to photo))

        val reopened = HistoryStore(folder.root)
        assertEquals(listOf("s1"), reopened.sessions.value.map { it.id })
        assertEquals("""{"layers":[]}""", reopened.mapJson("s1"))
        assertArrayEquals(photo, reopened.imageFile("s1", "o-1").readBytes())
    }

    @Test
    fun `replacing a session keeps downloaded photos and swaps the map`() = runBlocking {
        val store = HistoryStore(folder.root)
        store.add(session("s1"), mapJson = "old", images = mapOf("o-1" to byteArrayOf(1)))
        val continued = session("s1").copy(
            endedAt = 999_000,
            obstacles = listOf(SessionObstacle("o-1", "Sock", 10f, 20f), SessionObstacle("o-2", "Cable", 30f, 40f)),
        )
        assertTrue(store.replace(continued, mapJson = "new"))

        val reopened = HistoryStore(folder.root)
        val stored = reopened.sessions.value.single()
        assertEquals(999_000L, stored.endedAt)
        assertEquals(listOf(true, false), stored.obstacles.map { it.hasImage })
        assertEquals("new", reopened.mapJson("s1"))
    }

    @Test
    fun `a deleted session is not brought back by replace, nor a photo for an obstacle it lacks`() = runBlocking {
        val store = HistoryStore(folder.root)
        assertFalse(store.replace(session("gone"), mapJson = "{}"))
        assertTrue(store.sessions.value.isEmpty())

        store.add(session("s1"), mapJson = "{}", images = emptyMap())
        store.attachImage("s1", "not-in-run", byteArrayOf(1))
        assertFalse(store.imageFile("s1", "not-in-run").exists())
    }

    @Test
    fun `sessions are sorted newest first and deleting removes files`() = runBlocking {
        val store = HistoryStore(folder.root)
        store.add(session("old", startedAt = 1L), null, emptyMap())
        store.add(session("new", startedAt = 2L), "{}", emptyMap())
        assertEquals(listOf("new", "old"), store.sessions.value.map { it.id })

        store.delete("new")
        assertEquals(listOf("old"), store.sessions.value.map { it.id })
        assertNull(store.mapJson("new"))
    }

    @Test
    fun `only the newest fifty sessions per robot are kept`() = runBlocking {
        val store = HistoryStore(folder.root)
        repeat(52) { store.add(session("s$it", startedAt = it.toLong()), null, emptyMap()) }
        store.add(session("other", robotId = "r2"), null, emptyMap())

        val ids = store.sessions.value.map { it.id }
        assertEquals(51, ids.size)
        assertFalse("s0" in ids)
        assertFalse("s1" in ids)
        assertTrue("other" in ids)
    }

    @Test
    fun `file names cannot escape the history folder`() {
        val file = HistoryStore(folder.root).imageFile("../../evil", "../x")
        assertTrue(file.canonicalPath.startsWith(folder.root.canonicalPath))
    }
}
