package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val apiKeyStore: AiApiKeyStore
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are 'Vibe-Engine', a professional music curator.
            Analyze the user's request and listening profile to provide perfect music recommendations.
            Always prioritize flow, emotional resonance, and discovery.
        """.trimIndent()
        
        val DEFAULT_DEEPSEEK_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GROQ_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_MISTRAL_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_NVIDIA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_KIMI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GLM_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENAI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENROUTER_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
    }

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val AI_TOP_P = floatPreferencesKey("ai_top_p")
        val AI_TOP_K = intPreferencesKey("ai_top_k")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_PRESENCE_PENALTY = floatPreferencesKey("ai_presence_penalty")
        val AI_FREQUENCY_PENALTY = floatPreferencesKey("ai_frequency_penalty")
        val AI_SAMPLE_SIZE = intPreferencesKey("ai_sample_size")
        val AI_DIGEST_MODE = stringPreferencesKey("ai_digest_mode")
        val AI_INCLUDE_EXTENDED_FIELDS = booleanPreferencesKey("ai_include_extended_fields")
        val AUTO_LYRICS_SYNC = booleanPreferencesKey("auto_lyrics_sync")

        /** Legacy clear-text location, only read to migrate keys into [AiApiKeyStore]. */
        fun getLegacyApiKey(provider: AiProvider) = stringPreferencesKey(SensitivePreferenceKeys.apiKeyName(provider))
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
        fun getBaseUrl(provider: AiProvider) = stringPreferencesKey(SensitivePreferenceKeys.baseUrlName(provider))
    }

    /** Decrypted API keys; `null` until [ensureApiKeysLoaded] has run. */
    private val apiKeys = MutableStateFlow<Map<AiProvider, String>?>(null)
    private val apiKeysLoadMutex = Mutex()

    /**
     * Loads API keys from [apiKeyStore], migrating any clear-text key left in the DataStore by
     * older versions: it is written to secure storage and then removed from the DataStore.
     * If secure storage is unavailable the legacy value stays where it is (and is still used),
     * so an existing setup keeps working, but no new key is ever written in clear text.
     */
    private suspend fun ensureApiKeysLoaded() {
        if (apiKeys.value != null) return
        apiKeysLoadMutex.withLock {
            if (apiKeys.value != null) return
            withContext(Dispatchers.IO) {
                val legacyPrefs = dataStore.data.first()
                val loaded = mutableMapOf<AiProvider, String>()
                val legacyToRemove = mutableListOf<AiProvider>()
                AiProvider.entries.forEach { provider ->
                    val legacyValue = legacyPrefs[Keys.getLegacyApiKey(provider)]?.trim()
                    val secureValue = apiKeyStore.get(provider)
                    when {
                        secureValue != null -> {
                            loaded[provider] = secureValue
                            if (legacyValue != null) legacyToRemove += provider
                        }
                        legacyValue.isNullOrEmpty() -> {
                            if (legacyValue != null) legacyToRemove += provider
                        }
                        apiKeyStore.put(provider, legacyValue) -> {
                            loaded[provider] = legacyValue
                            legacyToRemove += provider
                        }
                        else -> loaded[provider] = legacyValue
                    }
                }
                if (legacyToRemove.isNotEmpty()) {
                    dataStore.edit { preferences ->
                        legacyToRemove.forEach { preferences.remove(Keys.getLegacyApiKey(it)) }
                    }
                }
                apiKeys.value = loaded
            }
        }
    }

    // Generic accessors for AiHandler
    fun getApiKey(provider: AiProvider): Flow<String> = flow {
        ensureApiKeysLoaded()
        emitAll(apiKeys.filterNotNull().map { it[provider].orEmpty() }.distinctUntilChanged())
    }

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

    fun getBaseUrl(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getBaseUrl(provider)] ?: "" }

    /**
     * Stores [apiKey] in secure storage. Returns `false` (and stores nothing) if secure storage is
     * unavailable; the caller must tell the user. A blank key removes the stored one.
     */
    suspend fun setApiKey(provider: AiProvider, apiKey: String): Boolean {
        ensureApiKeysLoaded()
        val trimmed = apiKey.trim()
        val saved = withContext(Dispatchers.IO) {
            if (trimmed.isEmpty()) {
                apiKeyStore.remove(provider)
                true
            } else {
                apiKeyStore.put(provider, trimmed)
            }
        }
        if (!saved) return false
        dataStore.edit { preferences -> preferences.remove(Keys.getLegacyApiKey(provider)) }
        apiKeys.update { current -> current.orEmpty() + (provider to trimmed) }
        return true
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { preferences -> preferences[Keys.getModel(provider)] = model }
    }

    suspend fun setSystemPrompt(provider: AiProvider, prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.getSystemPrompt(provider)] = prompt }
    }

    suspend fun resetSystemPrompt(provider: AiProvider) {
        dataStore.edit { preferences ->
            preferences[Keys.getSystemPrompt(provider)] = DEFAULT_SYSTEM_PROMPT
        }
    }

    suspend fun setBaseUrl(provider: AiProvider, url: String) {
        dataStore.edit { preferences -> preferences[Keys.getBaseUrl(provider)] = url.trim() }
    }

    // Convenience properties for legacy compatibility (e.g. PlayerViewModel)
    val geminiApiKey: Flow<String> = getApiKey(AiProvider.GEMINI)
    val geminiModel: Flow<String> = getModel(AiProvider.GEMINI)
    val geminiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GEMINI)

    val deepseekApiKey: Flow<String> = getApiKey(AiProvider.DEEPSEEK)
    val deepseekModel: Flow<String> = getModel(AiProvider.DEEPSEEK)
    val deepseekSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.DEEPSEEK)

    val groqApiKey: Flow<String> = getApiKey(AiProvider.GROQ)
    val groqModel: Flow<String> = getModel(AiProvider.GROQ)
    val groqSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GROQ)

    val mistralApiKey: Flow<String> = getApiKey(AiProvider.MISTRAL)
    val mistralModel: Flow<String> = getModel(AiProvider.MISTRAL)
    val mistralSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.MISTRAL)

    val nvidiaApiKey: Flow<String> = getApiKey(AiProvider.NVIDIA)
    val nvidiaModel: Flow<String> = getModel(AiProvider.NVIDIA)
    val nvidiaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.NVIDIA)

    val kimiApiKey: Flow<String> = getApiKey(AiProvider.KIMI)
    val kimiModel: Flow<String> = getModel(AiProvider.KIMI)
    val kimiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.KIMI)

    val glmApiKey: Flow<String> = getApiKey(AiProvider.GLM)
    val glmModel: Flow<String> = getModel(AiProvider.GLM)
    val glmSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GLM)

    val openaiApiKey: Flow<String> = getApiKey(AiProvider.OPENAI)
    val openaiModel: Flow<String> = getModel(AiProvider.OPENAI)
    val openaiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENAI)

    val openrouterApiKey: Flow<String> = getApiKey(AiProvider.OPENROUTER)
    val openrouterModel: Flow<String> = getModel(AiProvider.OPENROUTER)
    val openrouterSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENROUTER)

    val ollamaApiKey: Flow<String> = getApiKey(AiProvider.OLLAMA)
    val ollamaModel: Flow<String> = getModel(AiProvider.OLLAMA)
    val ollamaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OLLAMA)

    val customApiKey: Flow<String> = getApiKey(AiProvider.CUSTOM)
    val customModel: Flow<String> = getModel(AiProvider.CUSTOM)
    val customSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.CUSTOM)
    val customBaseUrl: Flow<String> = getBaseUrl(AiProvider.CUSTOM)

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI" }

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

    /** Automatic AI lyrics sync when the 4-tier cascade yields no timestamped lyrics. */
    val autoLyricsSyncEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AUTO_LYRICS_SYNC] ?: true }

    val aiTemperature: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TEMPERATURE] ?: 0.7f }

    val aiTopP: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_P] ?: 0.95f }

    val aiTopK: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_K] ?: 64 }

    val aiMaxTokens: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_MAX_TOKENS] ?: 4096 }

    val aiPresencePenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] ?: 0.0f }

    val aiFrequencyPenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] ?: 0.0f }

    val aiSampleSize: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_SAMPLE_SIZE] ?: 40 }

    val aiDigestMode: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_DIGEST_MODE] ?: "safe" }

    val aiIncludeExtendedFields: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] ?: false }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }

    suspend fun setAutoLyricsSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AUTO_LYRICS_SYNC] = enabled }
    }

    suspend fun setAiTemperature(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TEMPERATURE] = value }
    }

    suspend fun setAiTopP(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_P] = value }
    }

    suspend fun setAiTopK(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_K] = value }
    }

    suspend fun setAiMaxTokens(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_MAX_TOKENS] = value }
    }

    suspend fun setAiPresencePenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] = value }
    }

    suspend fun setAiFrequencyPenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] = value }
    }

    suspend fun setAiSampleSize(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_SAMPLE_SIZE] = value }
    }

    suspend fun setAiDigestMode(mode: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_DIGEST_MODE] = mode }
    }

    suspend fun setAiIncludeExtendedFields(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] = enabled }
    }
}
