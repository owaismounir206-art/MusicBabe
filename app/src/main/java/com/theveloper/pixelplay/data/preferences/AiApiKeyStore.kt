package com.theveloper.pixelplay.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import timber.log.Timber

/**
 * At-rest storage for AI provider API keys.
 *
 * Implementations must never fall back to clear-text storage: when secure storage is unavailable
 * [put] returns `false` and the caller is expected to tell the user the key was not saved.
 */
interface AiApiKeyStore {
    /** Returns the stored key, or `null` if none is stored or secure storage is unavailable. */
    fun get(provider: AiProvider): String?

    /** Returns `true` only if the key was durably written to secure storage. */
    fun put(provider: AiProvider, apiKey: String): Boolean

    fun remove(provider: AiProvider)
}

/** [AiApiKeyStore] backed by [EncryptedSharedPreferences] with a Keystore-held master key. */
class EncryptedAiApiKeyStore(private val context: Context) : AiApiKeyStore {

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Secure storage for AI API keys is unavailable")
            null
        }
    }

    override fun get(provider: AiProvider): String? = try {
        prefs?.getString(SensitivePreferenceKeys.apiKeyName(provider), null)
    } catch (e: Exception) {
        Timber.e(e, "Failed to read AI API key for %s", provider.name)
        null
    }

    override fun put(provider: AiProvider, apiKey: String): Boolean = try {
        prefs?.edit()?.putString(SensitivePreferenceKeys.apiKeyName(provider), apiKey)?.commit() ?: false
    } catch (e: Exception) {
        Timber.e(e, "Failed to store AI API key for %s", provider.name)
        false
    }

    override fun remove(provider: AiProvider) {
        try {
            prefs?.edit()?.remove(SensitivePreferenceKeys.apiKeyName(provider))?.commit()
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove AI API key for %s", provider.name)
        }
    }

    companion object {
        /** Keep in sync with res/xml/backup_rules.xml and res/xml/data_extraction_rules.xml. */
        const val PREFS_NAME = "ai_credentials_prefs"
    }
}
