package io.github.dwicki.tidytudo.data.history

import io.github.dwicki.tidytudo.api.JsonCodec
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Cleaning history on the phone (F-8): `index.json` lists sessions; each session has a folder
 * with the gzipped map and obstacle photos. Everything lives in the app's private storage, which
 * is excluded from backups, because maps and photos show the inside of the user's home.
 */
class HistoryStore(private val root: File) {

    private val lock = Mutex()
    private val indexFile = File(root, "index.json")
    private val sessionsState = MutableStateFlow(readIndex())

    /** All sessions, newest first. */
    val sessions: StateFlow<List<CleaningSession>> = sessionsState.asStateFlow()

    suspend fun add(session: CleaningSession, mapJson: String?, images: Map<String, ByteArray>) = lock.withLock {
        withContext(Dispatchers.IO) {
            val folder = folder(session.id).apply { mkdirs() }
            mapJson?.let { writeGzip(File(folder, MAP_FILE), it) }
            images.forEach { (obstacleId, bytes) -> imageFile(session.id, obstacleId).writeBytes(bytes) }
            val updated = (listOf(session) + sessionsState.value).sortedByDescending { it.startedAt }
            val (kept, pruned) = prune(updated)
            pruned.forEach { folder(it.id).deleteRecursively() }
            writeIndex(kept)
            sessionsState.value = kept
        }
    }

    /**
     * Updates a stored session in place, e.g. when the robot carried on with a job after it was
     * stored. Photos already on the phone stay and are marked as present; the map is replaced.
     * Returns false, changing nothing, if the session is gone (deleted or pruned meanwhile).
     *
     * The map is swapped in before the index is written: if the app dies in between, the entry
     * keeps its old fingerprint, so the next attempt does not take the run as known and updates the
     * entry again (its new map's path is still the start of the robot's run).
     */
    suspend fun replace(session: CleaningSession, mapJson: String): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (sessionsState.value.none { it.id == session.id }) return@withContext false
            val folder = folder(session.id).apply { mkdirs() }
            val pending = File(folder, "$MAP_FILE.new")
            writeGzip(pending, mapJson)
            replaceFile(pending, File(folder, MAP_FILE))
            val withPhotos = session.copy(
                obstacles = session.obstacles.map { it.copy(hasImage = imageFile(session.id, it.id).exists()) },
            )
            val updated = sessionsState.value.map { if (it.id == session.id) withPhotos else it }.sortedByDescending { it.startedAt }
            writeIndex(updated)
            sessionsState.value = updated
            true
        }
    }

    /** Adds a photo to a stored session; photos arrive one by one because the robot rate-limits them. */
    suspend fun attachImage(sessionId: String, obstacleId: String, bytes: ByteArray) = lock.withLock {
        withContext(Dispatchers.IO) {
            // The entry may have been deleted or updated without this obstacle while the photo downloaded.
            val session = sessionsState.value.firstOrNull { it.id == sessionId } ?: return@withContext
            if (session.obstacles.none { it.id == obstacleId }) return@withContext
            imageFile(sessionId, obstacleId).writeBytes(bytes)
            val updated = sessionsState.value.map { session ->
                if (session.id != sessionId) {
                    session
                } else {
                    session.copy(obstacles = session.obstacles.map { if (it.id == obstacleId) it.copy(hasImage = true) else it })
                }
            }
            writeIndex(updated)
            sessionsState.value = updated
        }
    }

    suspend fun delete(sessionId: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            folder(sessionId).deleteRecursively()
            val kept = sessionsState.value.filterNot { it.id == sessionId }
            writeIndex(kept)
            sessionsState.value = kept
        }
    }

    /** Removes a robot's sessions, e.g. when the robot is removed from the app. */
    suspend fun deleteRobot(robotId: String) {
        sessionsState.value.filter { it.robotId == robotId }.forEach { delete(it.id) }
    }

    suspend fun mapJson(sessionId: String): String? = withContext(Dispatchers.IO) {
        val file = File(folder(sessionId), MAP_FILE)
        if (file.exists()) runCatching { readGzip(file) }.getOrNull() else null
    }

    fun imageFile(sessionId: String, obstacleId: String): File =
        File(folder(sessionId), "obstacle-${obstacleId.filter { it.isLetterOrDigit() || it == '-' }}.jpg")

    /** Keeps the newest [MAX_SESSIONS_PER_ROBOT] sessions of each robot. */
    private fun prune(sessions: List<CleaningSession>): Pair<List<CleaningSession>, List<CleaningSession>> {
        val kept = sessions.groupBy { it.robotId }.values.flatMap { it.take(MAX_SESSIONS_PER_ROBOT) }.toSet()
        return sessions.partition { it in kept }
    }

    private fun folder(sessionId: String) = File(root, sessionId.filter { it.isLetterOrDigit() || it == '-' })

    private fun readIndex(): List<CleaningSession> = runCatching {
        if (indexFile.exists()) JsonCodec.json.decodeFromString<List<CleaningSession>>(indexFile.readText()) else emptyList()
    }.getOrElse { error ->
        AppLog.e(TAG, "History index unreadable, starting empty", error)
        emptyList()
    }

    private fun writeIndex(sessions: List<CleaningSession>) {
        root.mkdirs()
        val temporary = File(root, "index.json.tmp")
        temporary.writeText(JsonCodec.json.encodeToString(ListSerializer(CleaningSession.serializer()), sessions))
        replaceFile(temporary, indexFile)
    }

    /** Moves [source] over [target]; throws if that fails, so a caller never takes a missing file for success. */
    private fun replaceFile(source: File, target: File) {
        if (source.renameTo(target)) return
        target.delete()
        if (!source.renameTo(target)) throw IOException("Could not replace ${target.name}")
    }

    private fun writeGzip(file: File, text: String) = GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(text) }

    private fun readGzip(file: File): String = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "HistoryStore"
        const val MAP_FILE = "map.json.gz"
        const val MAX_SESSIONS_PER_ROBOT = 50
    }
}
