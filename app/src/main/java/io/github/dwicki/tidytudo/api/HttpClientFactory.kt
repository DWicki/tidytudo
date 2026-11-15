package io.github.dwicki.tidytudo.api

import android.annotation.SuppressLint
import io.github.dwicki.tidytudo.util.PrivateAddress
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Everything needed to talk to one robot. */
data class Endpoint(
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val pinnedSha256: String? = null,
)

/** Thrown inside OkHttp when plain HTTP would leave the local network; mapped to [ApiError.CleartextNotAllowed]. */
class CleartextNotAllowedException : IOException("Plain HTTP is only allowed to local addresses")

/** Thrown by the trust manager so the caller learns the fingerprint without a second connection. */
class UntrustedCertificateException(val sha256: String) : CertificateException("Certificate not trusted")

/**
 * Builds one OkHttp client per robot.
 *
 * Trust model: Android's trust store first; if that rejects the certificate, the leaf's SHA-256
 * must equal the pin the user confirmed. With a matching pin we skip hostname verification,
 * because self-signed robot certificates rarely list the IP or `.local` name the phone uses,
 * and the pin already binds this exact certificate.
 */
object HttpClientFactory {

    private val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun create(endpoint: Endpoint): OkHttpClient {
        val builder = shared.newBuilder()
            .addNetworkInterceptor(CleartextGuard)
        if (endpoint.username != null && endpoint.password != null) {
            builder.addInterceptor(BasicAuth(Credentials.basic(endpoint.username, endpoint.password)))
        }
        val trustManager = PinningTrustManager(platformTrustManager(), endpoint.pinnedSha256)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier { host, session ->
            val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
            val pinMatches = leaf != null && endpoint.pinnedSha256 != null &&
                sha256(leaf).equals(endpoint.pinnedSha256, ignoreCase = true)
            pinMatches || OkHostnameVerifier.verify(host, session)
        }
        return builder.build()
    }

    fun sha256(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    private fun platformTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Checks the address the socket actually connected to, so DNS names pointing outside the LAN are caught too. */
    private object CleartextGuard : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!request.url.isHttps) {
                val address = chain.connection()?.route()?.socketAddress?.address
                if (address == null || !PrivateAddress.isPrivate(address)) throw CleartextNotAllowedException()
            }
            return chain.proceed(request)
        }
    }

    private class BasicAuth(private val header: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response =
            chain.proceed(chain.request().newBuilder().header("Authorization", header).build())
    }

    // Deliberate: it delegates to the platform trust manager and only additionally accepts the one
    // certificate the user confirmed by fingerprint (F-1.5); it never disables validation.
    @SuppressLint("CustomX509TrustManager")
    private class PinningTrustManager(
        private val platform: X509TrustManager,
        private val pinnedSha256: String?,
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            platform.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("Empty certificate chain")
            val fingerprint = sha256(leaf)
            if (pinnedSha256 != null && fingerprint.equals(pinnedSha256, ignoreCase = true)) return
            try {
                platform.checkServerTrusted(chain, authType)
            } catch (rejected: CertificateException) {
                throw UntrustedCertificateException(fingerprint).apply { initCause(rejected) }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
    }
}

/** `AB:CD:…` grouping, the way browsers show fingerprints. */
fun prettyFingerprint(hex: String): String = hex.uppercase().chunked(2).joinToString(":")
