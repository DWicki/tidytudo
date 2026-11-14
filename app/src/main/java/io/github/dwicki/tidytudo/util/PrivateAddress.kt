package io.github.dwicki.tidytudo.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Decides which addresses count as "the user's own network". Plain HTTP is only allowed to
 * these (Q-4), and the subnet scan never leaves them.
 */
object PrivateAddress {

    fun isPrivate(address: InetAddress): Boolean = when {
        address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress -> true
        address is Inet6Address -> isUniqueLocal(address)
        address is Inet4Address -> isCarrierGradeNat(address)
        else -> false
    }

    /** `fc00::/7`, the IPv6 counterpart of RFC 1918. Java's `isSiteLocalAddress` only knows the deprecated `fec0::/10`. */
    private fun isUniqueLocal(address: Inet6Address): Boolean =
        (address.address[0].toInt() and 0xFE) == 0xFC

    /** `100.64.0.0/10` is used by some home VPNs (e.g. Tailscale) to reach the LAN. */
    private fun isCarrierGradeNat(address: Inet4Address): Boolean {
        val bytes = address.address
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 100 && second in 64..127
    }

    /** Host names that resolve via mDNS or are obviously local; checked before DNS for a quick UI hint. */
    fun looksLocal(host: String): Boolean {
        val lower = host.lowercase()
        if (lower.endsWith(".local") || lower.endsWith(".lan") || lower.endsWith(".home.arpa") || !lower.contains('.')) {
            return true
        }
        val literal = parseIpLiteral(lower) ?: return false
        return isPrivate(literal)
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        val candidate = host.removePrefix("[").removeSuffix("]")
        val isIpv4 = candidate.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        val isIpv6 = candidate.contains(':') && candidate.all { it.isLetterOrDigit() || it == ':' || it == '.' }
        // InetAddress.getByName does not touch DNS for literals, so this is safe on any thread.
        return if (isIpv4 || isIpv6) runCatching { InetAddress.getByName(candidate) }.getOrNull() else null
    }
}
