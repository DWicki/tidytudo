package io.github.dwicki.tidytudo.api

/** One server-sent event. Valetudo always sets an event name and puts JSON into `data`. */
data class SseEvent(val name: String, val data: String)

/**
 * Incremental parser for the `text/event-stream` format (WHATWG HTML §9.2), reduced to what
 * Valetudo sends: `event:` and `data:` fields, comment lines for keep-alive, blank line ends an event.
 */
class SseParser {
    private var eventName = DEFAULT_EVENT
    private val data = StringBuilder()

    /** Feeds one line without its line terminator; returns an event when the line completes one. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> eventName = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val event = if (data.isEmpty()) null else SseEvent(eventName, data.toString())
        eventName = DEFAULT_EVENT
        data.clear()
        return event
    }

    private companion object {
        const val DEFAULT_EVENT = "message"
    }
}
