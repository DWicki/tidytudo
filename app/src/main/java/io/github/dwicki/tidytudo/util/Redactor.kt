package io.github.dwicki.tidytudo.util

/**
 * Removes everything from log text that could identify the user's home: credentials, addresses,
 * host names and URLs. Map coordinates are never passed to the logger in the first place.
 */
object Redactor {
    private val rules = listOf(
        Regex("""(?i)(authorization:\s*basic\s+)\S+""") to "$1<redacted>",
        Regex("""(?i)("?(password|username|user|pass)"?\s*[:=]\s*"?)[^",\s}]+""") to "$1<redacted>",
        Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s"']+""") to "<url>",
        Regex("""\b\d{1,3}(\.\d{1,3}){3}(:\d+)?\b""") to "<ip>",
        Regex("""(?i)\b[0-9a-f]{0,4}(:[0-9a-f]{0,4}){2,7}\b""") to "<ip6>",
        Regex("""(?i)\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(local|lan|home|arpa|internal|me|com|net|org|de|ch)\b""") to "<host>",
    )

    fun redact(text: String): String = rules.fold(text) { current, (pattern, replacement) ->
        pattern.replace(current, replacement)
    }
}
