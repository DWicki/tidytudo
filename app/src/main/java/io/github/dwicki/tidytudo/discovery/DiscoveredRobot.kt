package io.github.dwicki.tidytudo.discovery

/** A Valetudo instance found on the local network, before the user has added it. */
data class DiscoveredRobot(
    val baseUrl: String,
    /** Valetudo's human-readable system id, e.g. "QuietBrownFox"; empty if unknown. */
    val systemId: String,
    val friendlyName: String?,
    val manufacturer: String?,
    val model: String?,
    val version: String?,
    val source: Source,
) {
    enum class Source { MDNS, SUBNET_SCAN }

    val title: String
        get() = friendlyName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(manufacturer, model).joinToString(" ").ifBlank { systemId }
}
