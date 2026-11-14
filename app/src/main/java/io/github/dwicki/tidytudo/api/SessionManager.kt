package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.data.CredentialVault
import io.github.dwicki.tidytudo.data.Robot
import kotlinx.coroutines.CoroutineScope

/**
 * Hands out one [RobotSession] per robot, shared by the UI, the widget, the tile and the
 * monitoring service. A session is rebuilt only when how we reach the robot changes
 * (address, pin, credentials), not when the nickname or presets change.
 */
class SessionManager(
    private val vault: CredentialVault,
    private val scope: CoroutineScope,
) {
    private data class Key(val baseUrl: String, val pinnedSha256: String?, val hasCredentials: Boolean, val generation: Int)

    private val sessions = mutableMapOf<String, Pair<Key, RobotSession>>()
    private val generations = mutableMapOf<String, Int>()

    @Synchronized
    fun session(robot: Robot): RobotSession {
        val key = Key(robot.baseUrl, robot.pinnedSha256, robot.hasCredentials, generations[robot.id] ?: 0)
        sessions[robot.id]?.let { (existingKey, session) -> if (existingKey == key) return session }
        val session = RobotSession(createClient(robot), scope)
        sessions[robot.id] = key to session
        return session
    }

    /** Forces a new session next time, e.g. after the user re-entered the same credentials. */
    @Synchronized
    fun invalidate(robotId: String) {
        sessions.remove(robotId)
        generations[robotId] = (generations[robotId] ?: 0) + 1
    }

    fun createClient(robot: Robot): ValetudoClient {
        val credentials = if (robot.hasCredentials) vault.load(robot.id) else null
        val endpoint = Endpoint(
            baseUrl = robot.baseUrl,
            username = credentials?.username,
            password = credentials?.password,
            pinnedSha256 = robot.pinnedSha256,
        )
        return ValetudoClient(robot.baseUrl, HttpClientFactory.create(endpoint))
    }
}
