package io.github.dwicki.tidytudo.ui.robot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.ui.care.ConsumableItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What needs the user's attention, shown as badges on the hub rows. */
data class HubBadges(val openEvents: Int = 0, val lowConsumables: Int = 0)

/** Capabilities (to hide rows the robot does not support) and the counts behind the badges. */
class HubViewModel(container: AppContainer, robot: Robot) : ViewModel() {
    private val session = container.session(robot)
    val live: StateFlow<LiveState> = session.live

    private val badgeState = MutableStateFlow(HubBadges())
    val badges: StateFlow<HubBadges> = badgeState.asStateFlow()

    /** Called whenever the hub becomes visible, so a reset or dismissed message clears its badge. */
    fun refreshBadges() {
        viewModelScope.launch {
            val client = session.client
            val events = client.events().getOrNull().orEmpty().count { !it.processed }
            val low = if (Capabilities.CONSUMABLES in live.value.capabilities) {
                val limits = client.consumableProperties().getOrNull()?.availableConsumables.orEmpty()
                client.consumables().getOrNull().orEmpty().count { consumable ->
                    val spec = limits.firstOrNull { it.type == consumable.type && it.subType == consumable.subType }
                    ConsumableItem(consumable, spec?.maxValue).isLow
                }
            } else {
                0
            }
            badgeState.value = HubBadges(events, low)
        }
    }
}
