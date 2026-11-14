package io.github.dwicki.tidytudo.data

import kotlinx.serialization.Serializable

/** A robot the user added. Credentials are not part of it; they live in [CredentialVault]. */
@Serializable
data class Robot(
    val id: String,
    /** `http://192.168.1.20` or `https://robot.local:8443`, without trailing slash or path. */
    val baseUrl: String,
    /** Name the user gave in the app; falls back to [displayModel] when blank. */
    val nickname: String = "",
    /** Valetudo's stable system id; lets us recognise the robot after an IP change (F-1.7). */
    val systemId: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val pinnedSha256: String? = null,
    val hasCredentials: Boolean = false,
    val presets: List<CleaningPreset> = emptyList(),
) {
    val displayModel: String get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")

    val displayName: String get() = nickname.ifBlank { displayModel.ifBlank { systemId.ifBlank { "Robot" } } }
}

/** A saved one-tap cleaning job (F-4.6): rooms plus optional presets. Empty [segmentIds] means full cleanup. */
@Serializable
data class CleaningPreset(
    val id: String,
    val name: String,
    val segmentIds: List<String> = emptyList(),
    val iterations: Int = 1,
    val fanSpeed: String? = null,
    val waterUsage: String? = null,
    val operationMode: String? = null,
)
