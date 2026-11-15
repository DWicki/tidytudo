package io.github.dwicki.tidytudo.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.data.Robot

/** Launcher shortcut entry for a room of the selected robot. */
data class RoomShortcut(val segmentId: String, val name: String)

/**
 * Publishes dynamic launcher shortcuts for the selected robot: presets first, then rooms, up to
 * the launcher's limit (usually 4–5 visible).
 */
object Shortcuts {
    const val EXTRA_ROBOT_ID = "robot_id"
    const val EXTRA_PRESET_ID = "preset_id"
    const val EXTRA_SEGMENT_ID = "segment_id"
    const val EXTRA_LABEL = "label"
    const val EXTRA_SHORTCUT_ID = "shortcut_id"

    fun publish(context: Context, robot: Robot, rooms: List<RoomShortcut>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val limit = manager.maxShortcutCountPerActivity
        val presetShortcuts = robot.presets.map { preset ->
            build(context, "preset-${preset.id}", preset.name, R.drawable.ic_shortcut_preset) {
                putExtra(EXTRA_ROBOT_ID, robot.id).putExtra(EXTRA_PRESET_ID, preset.id)
            }
        }
        val roomShortcuts = rooms.map { room ->
            build(context, "room-${room.segmentId}", room.name, R.drawable.ic_shortcut_room) {
                putExtra(EXTRA_ROBOT_ID, robot.id).putExtra(EXTRA_SEGMENT_ID, room.segmentId).putExtra(EXTRA_LABEL, room.name)
            }
        }
        val shortcuts = (presetShortcuts + roomShortcuts).take(limit).mapIndexed { rank, builder -> builder.setRank(rank).build() }
        runCatching { manager.dynamicShortcuts = shortcuts }
    }

    fun reportUsed(context: Context, shortcutId: String) {
        context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(shortcutId)
    }

    private fun build(context: Context, id: String, label: String, icon: Int, extras: Intent.() -> Intent): ShortcutInfo.Builder {
        val intent = Intent(context, CommandActivity::class.java).setAction(Intent.ACTION_VIEW).extras().putExtra(EXTRA_SHORTCUT_ID, id)
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(label.take(MAX_SHORT_LABEL))
            .setLongLabel(context.getString(R.string.shortcut_clean_label, label))
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(intent)
    }

    private const val MAX_SHORT_LABEL = 25
}
