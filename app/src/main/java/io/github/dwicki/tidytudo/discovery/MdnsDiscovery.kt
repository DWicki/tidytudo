package io.github.dwicki.tidytudo.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.dwicki.tidytudo.util.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet6Address
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.mapNotNull

/**
 * Browses for `_valetudo._tcp`, which every Valetudo instance advertises (see
 * docs/valetudo-api.md §Discovery), and resolves each hit to an address plus TXT data.
 * Uses the platform's NsdManager, so no multicast permission or library is needed.
 */
class MdnsDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    // Before Android 14, NsdManager can only resolve one service at a time.
    private val resolveLock = Mutex()

    fun discover(): Flow<DiscoveredRobot> = foundServices().mapNotNull { service ->
        resolveLock.withLock { resolve(service) }?.let(::toRobot)
    }

    private fun foundServices(): Flow<NsdServiceInfo> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                trySendBlocking(service)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLog.w(TAG, "mDNS discovery failed to start: $errorCode")
                close()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback needs API 34; resolveService still works there.
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { continuation ->
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) = continuation.resume(resolved)
            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                AppLog.d(TAG, "Resolve failed: $errorCode")
                continuation.resume(null)
            }
        })
    }

    @Suppress("DEPRECATION") // NsdServiceInfo.host is deprecated on API 34 in favour of hostAddresses.
    private fun toRobot(service: NsdServiceInfo): DiscoveredRobot? {
        val host = service.host ?: return null
        val literal = host.hostAddress?.substringBefore('%') ?: return null
        val hostPart = if (host is Inet6Address) "[$literal]" else literal
        val portPart = if (service.port == DEFAULT_PORT) "" else ":${service.port}"
        val txt = service.attributes.mapValues { (_, bytes) -> bytes?.toString(Charsets.UTF_8) }
        return DiscoveredRobot(
            baseUrl = "http://$hostPart$portPart",
            systemId = txt["id"].orEmpty(),
            friendlyName = txt["name"],
            manufacturer = txt["manufacturer"],
            model = txt["model"],
            version = txt["version"],
            source = DiscoveredRobot.Source.MDNS,
        )
    }

    private companion object {
        const val TAG = "MdnsDiscovery"
        const val SERVICE_TYPE = "_valetudo._tcp"
        const val DEFAULT_PORT = 80
    }
}
