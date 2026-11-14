package io.github.dwicki.tidytudo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide preferences that are not about a single robot. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val monitoringState = MutableStateFlow(prefs.getBoolean(KEY_MONITORING, false))

    /** Opt-in background monitoring for error notifications (F-7.4). */
    val backgroundMonitoring: StateFlow<Boolean> = monitoringState.asStateFlow()

    fun setBackgroundMonitoring(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING, enabled).apply()
        monitoringState.value = enabled
    }

    private companion object {
        const val KEY_MONITORING = "background_monitoring"
    }
}
