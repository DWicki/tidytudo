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

    /** Whether the app already asked for the notification permission when a job was started. */
    val notificationPromptShown: Boolean get() = prefs.getBoolean(KEY_NOTIFICATION_PROMPT, false)

    fun markNotificationPromptShown() = prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPT, true).apply()

    /** Whether the one-time offer of background recording was shown (after the first robot was added). */
    val monitoringOffered: Boolean get() = prefs.getBoolean(KEY_MONITORING_OFFERED, false)

    fun markMonitoringOffered() = prefs.edit().putBoolean(KEY_MONITORING_OFFERED, true).apply()

    private companion object {
        const val KEY_MONITORING = "background_monitoring"
        const val KEY_MONITORING_OFFERED = "monitoring_offered"
        const val KEY_NOTIFICATION_PROMPT = "notification_prompt_shown"
    }
}
