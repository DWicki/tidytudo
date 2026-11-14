package io.github.dwicki.tidytudo.data

import io.github.dwicki.tidytudo.api.JsonCodec
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * The list of robots and which one is selected, kept in memory and written to a small JSON file.
 * Reads are instant (the UI needs the list at launch); writes go to disk atomically.
 */
class RobotStore(private val file: File) {

    @Serializable
    private data class Snapshot(val robots: List<Robot> = emptyList(), val selectedId: String? = null)

    private val writeLock = Mutex()
    private val snapshot = MutableStateFlow(readFile())

    private val robotsState = MutableStateFlow(snapshot.value.robots)
    val robots: StateFlow<List<Robot>> = robotsState.asStateFlow()

    private val selectedState = MutableStateFlow(snapshot.value.selectedId)
    val selectedId: StateFlow<String?> = selectedState.asStateFlow()

    fun robot(id: String): Robot? = robots.value.firstOrNull { it.id == id }

    fun selected(): Robot? = selectedId.value?.let(::robot) ?: robots.value.firstOrNull()

    suspend fun upsert(robot: Robot) = modify { current ->
        val others = current.robots.filterNot { it.id == robot.id }
        val index = current.robots.indexOfFirst { it.id == robot.id }
        val updated = if (index < 0) others + robot else others.toMutableList().apply { add(index, robot) }
        current.copy(robots = updated, selectedId = current.selectedId ?: robot.id)
    }

    suspend fun update(id: String, change: (Robot) -> Robot) = modify { current ->
        current.copy(robots = current.robots.map { if (it.id == id) change(it) else it })
    }

    suspend fun remove(id: String) = modify { current ->
        val remaining = current.robots.filterNot { it.id == id }
        val selected = current.selectedId.takeIf { it != id } ?: remaining.firstOrNull()?.id
        current.copy(robots = remaining, selectedId = selected)
    }

    suspend fun select(id: String) = modify { it.copy(selectedId = id) }

    /** Finds a robot added earlier by its Valetudo system id, so re-adding updates instead of duplicating. */
    fun findBySystemId(systemId: String): Robot? =
        robots.value.firstOrNull { systemId.isNotEmpty() && it.systemId == systemId }

    private suspend fun modify(change: (Snapshot) -> Snapshot) = writeLock.withLock {
        val next = change(snapshot.value)
        snapshot.value = next
        robotsState.value = next.robots
        selectedState.value = next.selectedId
        withContext(Dispatchers.IO) { writeFile(next) }
    }

    private fun readFile(): Snapshot = runCatching {
        if (file.exists()) JsonCodec.json.decodeFromString<Snapshot>(file.readText()) else Snapshot()
    }.getOrElse { error ->
        AppLog.e(TAG, "Robot list unreadable, starting empty", error)
        Snapshot()
    }

    private fun writeFile(value: Snapshot) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(JsonCodec.json.encodeToString(Snapshot.serializer(), value))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    private companion object {
        const val TAG = "RobotStore"
    }
}
