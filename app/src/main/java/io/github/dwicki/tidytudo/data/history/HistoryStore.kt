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
        if (!temporary.renameTo(indexFile)) {
            indexFile.delete()
            temporary.renameTo(indexFile)
        }
    }

    private fun writeGzip(file: File, text: String) = GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(text) }

    private fun readGzip(file: File): String = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "HistoryStore"
        const val MAP_FILE = "map.json.gz"
        const val MAX_SESSIONS_PER_ROBOT = 50
    }
}
