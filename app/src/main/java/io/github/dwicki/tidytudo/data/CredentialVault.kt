package io.github.dwicki.tidytudo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Basic-auth credentials for one robot. */
data class Credentials(val username: String, val password: String)

/**
 * Keeps basic-auth credentials encrypted with an AES-256-GCM key that never leaves the Android
 * Keystore (Q-3). Ciphertext sits in private SharedPreferences, which are excluded from backup
 * and device transfer (`data_extraction_rules.xml`).
 *
 * `androidx.security:security-crypto` is deprecated, and this is small enough to audit.
 */
class CredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private val key: SecretKey by lazy { loadOrCreateKey() }

    fun save(robotId: String, credentials: Credentials) {
        prefs.edit()
            .putString(usernameKey(robotId), encrypt(credentials.username))
            .putString(passwordKey(robotId), encrypt(credentials.password))
            .apply()
    }

    fun load(robotId: String): Credentials? {
        val username = prefs.getString(usernameKey(robotId), null)?.let(::decrypt) ?: return null
        val password = prefs.getString(passwordKey(robotId), null)?.let(::decrypt) ?: return null
        return Credentials(username, password)
    }

    fun delete(robotId: String) {
        prefs.edit().remove(usernameKey(robotId)).remove(passwordKey(robotId)).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + SEPARATOR + encode(cipherText)
    }

    /** Returns null if the key was lost (e.g. after a device reset of the Keystore); the user re-enters credentials. */
    private fun decrypt(blob: String): String? = runCatching {
        val (iv, cipherText) = blob.split(SEPARATOR, limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decode(iv)))
        String(cipher.doFinal(decode(cipherText)), Charsets.UTF_8)
    }.getOrNull()

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun usernameKey(robotId: String) = "$robotId.user"
    private fun passwordKey(robotId: String) = "$robotId.pass"
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tidytudo_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
