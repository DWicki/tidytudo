package io.github.dwicki.tidytudo.discovery

import android.content.Context
import android.net.ConnectivityManager
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Endpoint
import io.github.dwicki.tidytudo.api.HttpClientFactory
import io.github.dwicki.tidytudo.api.ValetudoClient
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.util.PrivateAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Fallback discovery (F-1.9) for networks that block mDNS: probes every address of the phone's
 * own IPv4 subnet for `GET /api/v2/valetudo/version` on port 80. Limited to private /24-or-smaller
 * networks so it never sweeps anything but the user's own LAN.
 */
class SubnetScanner(private val context: Context) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun scan(): Flow<DiscoveredRobot> = candidateHosts().asFlow()
        .flatMapMerge(PARALLEL_PROBES) { address -> flow { probe(address)?.let { emit(it) } } }
        .flowOn(Dispatchers.IO)

    /** All host addresses of the current network, or nothing if it is not a small private IPv4 network. */
    fun candidateHosts(): List<InetAddress> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return emptyList()
        val link = connectivity.getLinkProperties(network) ?: return emptyList()
        val ipv4 = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return emptyList()
        if (!PrivateAddress.isPrivate(ipv4.address)) return emptyList()
        return hostsInSubnet(ipv4.address as Inet4Address, maxOf(ipv4.prefixLength, MIN_PREFIX_LENGTH))
    }

    private suspend fun probe(address: InetAddress): DiscoveredRobot? {
        val baseUrl = "http://${address.hostAddress}"
        val client = ValetudoClient(baseUrl, probeClient)
        val version = (client.version() as? ApiResult.Success)?.value ?: return null
        val info = client.valetudoInfo().getOrNull()
        val robot = client.robotInfo().getOrNull()
        return DiscoveredRobot(
            baseUrl = baseUrl,
            systemId = info?.systemId.orEmpty(),
            friendlyName = null,
            manufacturer = robot?.manufacturer,
            model = robot?.modelName,
            version = version.release,
            source = DiscoveredRobot.Source.SUBNET_SCAN,
        )
    }

    private val probeClient by lazy {
        HttpClientFactory.create(Endpoint(baseUrl = "")).newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        private const val PARALLEL_PROBES = 32
        private const val PROBE_TIMEOUT_MS = 1_200L
        private const val MIN_PREFIX_LENGTH = 24

        /** Every usable host address in the subnet, excluding network and broadcast address. */
        fun hostsInSubnet(address: Inet4Address, prefixLength: Int): List<InetAddress> {
            val ip = address.address.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
            val hostBits = 32 - prefixLength
            val network = ip and (0xFFFFFFFFL shl hostBits and 0xFFFFFFFFL)
            val size = 1L shl hostBits
            return (1 until size - 1).map { offset -> toInet4(network + offset) }.filter { it != address }
        }

        private fun toInet4(value: Long): InetAddress = InetAddress.getByAddress(
            byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()),
        )
    }
}
