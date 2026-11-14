package io.github.dwicki.tidytudo.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalises what users type into a base URL: `192.168.1.20`, `robot.local:8080`,
 * `https://robot.lan/`, `fe80::1` all become `scheme://host[:port]`. Paths are dropped because
 * Valetudo always serves at the root.
 */
object RobotAddress {

    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = when {
            trimmed.contains("://") -> trimmed
            isBareIpv6(trimmed) -> "http://[$trimmed]"
            else -> "http://$trimmed"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        return format(url)
    }

    /** The host part, for display and for the cleartext hint. */
    fun host(baseUrl: String): String = baseUrl.toHttpUrlOrNull()?.host ?: baseUrl

    private fun format(url: HttpUrl): String {
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        return "${url.scheme}://$host$port"
    }

    /** `fe80::1` without brackets; a single colon means host:port instead. */
    private fun isBareIpv6(text: String): Boolean = text.count { it == ':' } >= 2 && !text.startsWith("[")
}
