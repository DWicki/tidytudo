package io.github.dwicki.tidytudo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.dwicki.tidytudo.appContainer

/**
 * Restarts background monitoring after a reboot or an app update, both of which stop the
 * service. Without this, the setting would say "on" while nothing is recorded.
 */
class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (relevant && context.appContainer.settings.backgroundMonitoring.value) MonitorService.startMonitoring(context)
    }
}
