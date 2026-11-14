package io.github.dwicki.tidytudo.shortcuts

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.appContainer
import io.github.dwicki.tidytudo.data.CleaningPreset
import io.github.dwicki.tidytudo.service.MonitorService
import io.github.dwicki.tidytudo.service.QuickActions
import kotlinx.coroutines.launch

/**
 * Invisible target of launcher shortcuts (F-7.5): runs a preset or cleans one room, shows a
 * toast with the result and finishes. Being an activity lets it start the monitoring service,
 * which Android forbids from the background.
 */
class CommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        val robot = intent.getStringExtra(Shortcuts.EXTRA_ROBOT_ID)?.let(container.robotStore::robot)
        val preset = robot?.let { presetFrom(it.presets) }
        if (robot == null || preset == null) {
            Toast.makeText(this, R.string.shortcut_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Toast.makeText(this, getString(R.string.shortcut_starting, preset.name), Toast.LENGTH_SHORT).show()
        MonitorService.watch(this, robot.id)
        val applicationContext = applicationContext
        container.appScope.launch {
            val result = QuickActions.runPreset(container.session(robot), preset)
            if (result is ApiResult.Failure) {
                applicationContext.mainExecutor.execute {
                    Toast.makeText(applicationContext, R.string.error_command_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        finish()
    }

    /** A room shortcut is a one-room preset built on the fly, so both paths share [QuickActions.runPreset]. */
    private fun presetFrom(presets: List<CleaningPreset>): CleaningPreset? {
        intent.getStringExtra(Shortcuts.EXTRA_PRESET_ID)?.let { id -> return presets.firstOrNull { it.id == id } }
        val segmentId = intent.getStringExtra(Shortcuts.EXTRA_SEGMENT_ID) ?: return null
        val name = intent.getStringExtra(Shortcuts.EXTRA_LABEL) ?: segmentId
        return CleaningPreset(id = "room-$segmentId", name = name, segmentIds = listOf(segmentId))
    }
}
