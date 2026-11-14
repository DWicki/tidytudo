package io.github.dwicki.tidytudo.ui.care

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.Consumable
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.CommandViewModel
import io.github.dwicki.tidytudo.ui.LoadState
import io.github.dwicki.tidytudo.ui.toLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A consumable with its maximum lifetime, so the UI can show a percentage. */
data class ConsumableItem(val consumable: Consumable, val maxValue: Int?) {
    /** 0–1, or null when the robot reports no maximum (e.g. percent-based consumables already are). */
    val fraction: Float?
        get() = when {
            consumable.remaining.unit == "percent" -> consumable.remaining.value / 100f
            maxValue != null && maxValue > 0 -> consumable.remaining.value.toFloat() / maxValue
            else -> null
        }?.coerceIn(0f, 1f)
}

class ConsumablesViewModel(container: AppContainer, robot: Robot) : CommandViewModel() {
    private val client = container.session(robot).client

    private val state = MutableStateFlow<LoadState<List<ConsumableItem>>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<List<ConsumableItem>>> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val limits = client.consumableProperties().getOrNull()?.availableConsumables.orEmpty()
            val result = client.consumables()
            state.value = when (result) {
                is ApiResult.Success -> LoadState.Loaded(
                    result.value.map { consumable ->
                        val spec = limits.firstOrNull { it.type == consumable.type && it.subType == consumable.subType }
                        ConsumableItem(consumable, spec?.maxValue)
                    },
                )
                is ApiResult.Failure -> result.toLoadState()
            }
        }
    }

    fun reset(consumable: Consumable) = launchCommand(
        block = { client.resetConsumable(consumable.type, consumable.subType) },
        onSuccess = ::load,
    )
}
