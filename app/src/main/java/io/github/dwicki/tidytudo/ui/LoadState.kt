package io.github.dwicki.tidytudo.ui

import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.ApiResult

/** Loading state for screens that fetch their data once and can retry. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val error: ApiError) : LoadState<Nothing>
}

fun <T> ApiResult<T>.toLoadState(): LoadState<T> = when (this) {
    is ApiResult.Success -> LoadState.Loaded(value)
    is ApiResult.Failure -> LoadState.Failed(error)
}
