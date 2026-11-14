package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object JsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
}

/** Runs requests against `<base>/api/v2/…` and maps every outcome to an [ApiResult]. */
class HttpTransport(baseUrl: String, val client: OkHttpClient) {

    val apiRoot: HttpUrl = baseUrl.trimEnd('/').plus("/api/v2/").toHttpUrl()

    suspend fun get(path: String): ApiResult<JsonElement> = execute(request(path).get().build()) { body ->
        JsonCodec.json.parseToJsonElement(body.string())
    }

    /** Binary download, e.g. an obstacle photo. */
    suspend fun getBytes(path: String): ApiResult<ByteArray> = execute(request(path).get().build()) { body -> body.bytes() }

    suspend fun put(path: String, body: JsonObject): ApiResult<Unit> =
        execute(request(path).put(body.toRequestBody()).build()) { }

    suspend fun post(path: String, body: JsonObject): ApiResult<Unit> =
        execute(request(path).post(body.toRequestBody()).build()) { }

    suspend fun delete(path: String): ApiResult<Unit> = execute(request(path).delete().build()) { }

    fun request(path: String): Request.Builder = Request.Builder().url(apiRoot.resolve(path)!!)

    private suspend fun <T> execute(request: Request, readBody: (ResponseBody) -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).await().use { response -> handle(response, readBody) }
            } catch (error: IOException) {
                AppLog.d(TAG, "${request.method} ${request.url.encodedPath} failed: ${error.javaClass.simpleName}")
                ApiResult.Failure(classify(error))
            }
        }

    private fun <T> handle(response: Response, readBody: (ResponseBody) -> T): ApiResult<T> {
        val body = response.body ?: return ApiResult.Failure(ApiError.UnexpectedResponse(IOException("Empty response")))
        return when {
            response.isSuccessful -> runCatching { readBody(body) }.fold(
                onSuccess = { ApiResult.Success(it) },
                onFailure = { ApiResult.Failure(ApiError.UnexpectedResponse(it)) },
            )
            response.code == 401 -> ApiResult.Failure(ApiError.AuthRequired)
            response.code == 404 -> ApiResult.Failure(ApiError.NotSupported)
            else -> ApiResult.Failure(ApiError.Http(response.code, body.string().take(MAX_ERROR_BODY)))
        }
    }

    private companion object {
        const val TAG = "HttpTransport"
        const val MAX_ERROR_BODY = 300
    }
}

/** Finds our own exceptions inside OkHttp's wrapping (e.g. an SSLHandshakeException around the trust failure). */
fun classify(error: Throwable): ApiError {
    var current: Throwable? = error
    while (current != null) {
        when (current) {
            is CleartextNotAllowedException -> return ApiError.CleartextNotAllowed
            is UntrustedCertificateException -> return ApiError.UntrustedCertificate(current.sha256)
        }
        current = current.cause
    }
    return ApiError.Network(error)
}

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private fun JsonObject.toRequestBody(): RequestBody = toString().toRequestBody(jsonMediaType)

/** Bridges OkHttp's callback API to coroutines and cancels the call when the coroutine is cancelled. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
