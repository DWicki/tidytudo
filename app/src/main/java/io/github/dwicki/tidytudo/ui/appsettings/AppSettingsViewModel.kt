package io.github.dwicki.tidytudo.ui.appsettings

import androidx.lifecycle.ViewModel
import io.github.dwicki.tidytudo.AppContainer
import kotlinx.coroutines.flow.StateFlow

class AppSettingsViewModel(container: AppContainer) : ViewModel() {
    val monitoring: StateFlow<Boolean> = container.settings.backgroundMonitoring
    val dynamicColor: StateFlow<Boolean> = container.settings.dynamicColor
    private val settings = container.settings

    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
}
