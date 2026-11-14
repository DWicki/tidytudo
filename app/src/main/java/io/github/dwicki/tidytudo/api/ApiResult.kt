package io.github.dwicki.tidytudo.api

/** Outcome of one call to a robot. Failures are values, so callers must decide what the user sees. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** The robot answered with a non-2xx status that has no more specific meaning. */
    data class Http(val code: Int, val body: String) : ApiError

    /** Unreachable, timed out, connection reset. */
    data class Network(val cause: Throwable) : ApiError

    /** Basic auth is enabled on the robot and our credentials are missing or wrong. */
    data object AuthRequired : ApiError

    /** HTTPS with a certificate that is neither trusted by Android nor pinned for this robot. */
    data class UntrustedCertificate(val sha256: String) : ApiError

    /** Plain HTTP to an address outside the local network (Q-4). */
    data object CleartextNotAllowed : ApiError

    /** The robot does not offer `/api/v2` or a requested capability endpoint. */
    data object NotSupported : ApiError

    /** The response did not have the expected shape. */
    data class UnexpectedResponse(val cause: Throwable) : ApiError
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

fun ApiResult<*>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error
