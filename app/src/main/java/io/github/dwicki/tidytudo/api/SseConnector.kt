package io.github.dwicki.tidytudo.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when an SSE stream ends or cannot be opened; carries the classified cause. */
class SseException(val error: ApiError) : IOException("SSE stream failed: $error")

/**
 * Opens Valetudo's server-sent event streams. The flow completes only by failure or cancellation;
 * reconnecting is the caller's job, because the right backoff depends on what the stream is for.
 */
class SseConnector(private val transport: HttpTransport) {

    // Valetudo sends keep-alive comments well within this window, so a silent connection is dead.
    private val streamClient = transport.client.newBuilder()
        .readTimeout(KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun events(path: String): Flow<SseEvent> = flow {
        val request = transport.request(path).header("Accept", "text/event-stream").build()
        val call = streamClient.newCall(request)
        val response = try {
            call.await()
        } catch (error: IOException) {
            throw SseException(classify(error))
        }
        response.use {
            when {
                response.code == 401 -> throw SseException(ApiError.AuthRequired)
                response.code == 404 -> throw SseException(ApiError.NotSupported)
                !response.isSuccessful -> throw SseException(ApiError.Http(response.code, ""))
            }
            val source = response.body!!.source()
            val parser = SseParser()
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: throw SseException(ApiError.Network(IOException("Stream closed")))
                    parser.feed(line)?.let { emit(it) }
                }
            } catch (error: IOException) {
                if (error is SseException) throw error
                throw SseException(classify(error))
            } finally {
                call.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val ATTRIBUTES = "robot/state/attributes/sse"
        const val MAP = "robot/state/map/sse"
        const val EVENT_ATTRIBUTES = "StateAttributesUpdated"
        const val EVENT_MAP = "MapUpdated"
        private const val KEEP_ALIVE_TIMEOUT_SECONDS = 90L
    }
}
