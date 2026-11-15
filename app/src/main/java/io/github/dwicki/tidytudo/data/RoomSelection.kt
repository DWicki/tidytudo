package io.github.dwicki.tidytudo.data

/**
 * Rooms picked for the next cleaning and how many passes. One per robot, shared by the home
 * screen and the map, so "Clean 2 rooms" means the same rooms on both.
 */
data class RoomSelection(val ids: List<String> = emptyList(), val iterations: Int = 1) {
    fun toggle(id: String): RoomSelection = copy(ids = if (id in ids) ids - id else ids + id)

    fun cleared(): RoomSelection = RoomSelection(iterations = iterations)
}
