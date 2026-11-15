package io.github.dwicki.tidytudo.ui.rooms

/** Rules for room names typed by the user; kept apart from the UI so they are unit-tested. */
object RoomName {
    /** Long enough for "Schlafzimmer Kinder", short enough to fit a chip and a map label. */
    const val MAX_LENGTH = 30

    /** Trims and collapses whitespace; null when nothing is left. */
    fun normalize(input: String): String? =
        input.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).ifEmpty { null }

    /** Saving makes sense only for a non-empty name that differs from the current one. */
    fun canSave(input: String, current: String?): Boolean {
        val name = normalize(input) ?: return false
        return name != current
    }
}
