package com.theveloper.pixelplay.data.preferences

import com.theveloper.pixelplay.data.ai.provider.AiProvider

/**
 * Single source of truth for DataStore keys that hold credentials or credential-adjacent data.
 *
 * These keys must never leave the device through a backup, and must never be overwritten by a
 * backup coming from somewhere else: an imported `*_base_url` would silently route requests
 * (API key included) to an unknown server.
 */
object SensitivePreferenceKeys {

    fun apiKeyName(provider: AiProvider): String = "${provider.name.lowercase()}_api_key"

    fun baseUrlName(provider: AiProvider): String = "${provider.name.lowercase()}_base_url"

    /** Every key name known to hold a credential or a credential destination. */
    val names: Set<String> = AiProvider.entries
        .flatMap { listOf(apiKeyName(it), baseUrlName(it)) }
        .toSet()

    /**
     * Suffixes treated as sensitive even if the owning provider is unknown to this build
     * (e.g. a backup created by a newer version with an additional provider).
     */
    private val sensitiveSuffixes = listOf(
        "_api_key",
        "_base_url",
        "_token",
        "_password",
        "_secret",
    )

    fun isSensitive(keyName: String): Boolean =
        keyName in names || sensitiveSuffixes.any { keyName.endsWith(it) }
}
