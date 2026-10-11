package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BackupSensitiveKeysTest {

    private fun createDataStore(scope: CoroutineScope, dir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dir.resolve("settings.preferences_pb").toFile() }
        )

    @Test
    fun `every provider api key and base url is classified as sensitive`() {
        AiProvider.entries.forEach { provider ->
            assertTrue(SensitivePreferenceKeys.isSensitive("${provider.name.lowercase()}_api_key"))
            assertTrue(SensitivePreferenceKeys.isSensitive("${provider.name.lowercase()}_base_url"))
        }
        assertTrue(SensitivePreferenceKeys.isSensitive("future_provider_api_key"))
        assertTrue(SensitivePreferenceKeys.isSensitive("some_service_token"))
        assertFalse(SensitivePreferenceKeys.isSensitive("ai_max_tokens"))
        assertFalse(SensitivePreferenceKeys.isSensitive("safe_token_limit"))
        assertFalse(SensitivePreferenceKeys.isSensitive("gemini_model"))
        assertFalse(SensitivePreferenceKeys.isSensitive("nav_bar_style"))
    }

    @Test
    fun `export never contains api keys or base urls`() = runTest {
        val tempDir = Files.createTempDirectory("backup-sensitive-keys-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            val repository = UserPreferencesRepository(dataStore, Json)
            dataStore.edit {
                it[stringPreferencesKey("gemini_api_key")] = "secret-gemini"
                it[stringPreferencesKey("custom_api_key")] = "secret-custom"
                it[stringPreferencesKey("custom_base_url")] = "https://my.server/v1"
                it[stringPreferencesKey("gemini_model")] = "gemini-pro"
            }
            repository.setNavBarStyle("compact")

            val exported = repository.exportPreferencesForBackup()
            val exportedKeys = exported.map { it.key }.toSet()

            assertFalse("gemini_api_key" in exportedKeys)
            assertFalse("custom_api_key" in exportedKeys)
            assertFalse("custom_base_url" in exportedKeys)
            assertTrue(exported.none { it.stringValue?.contains("secret") == true })
            assertTrue("gemini_model" in exportedKeys)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `import ignores sensitive entries and keeps local credentials intact`() = runTest {
        val tempDir = Files.createTempDirectory("backup-sensitive-keys-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            val repository = UserPreferencesRepository(dataStore, Json)
            dataStore.edit {
                it[stringPreferencesKey("openai_api_key")] = "local-openai"
                it[stringPreferencesKey("custom_base_url")] = "https://local.example/v1"
            }

            val foreignBackup = listOf(
                PreferenceBackupEntry(key = "openai_api_key", type = "string", stringValue = "foreign-openai"),
                PreferenceBackupEntry(key = "groq_api_key", type = "string", stringValue = "foreign-groq"),
                PreferenceBackupEntry(key = "custom_base_url", type = "string", stringValue = "https://evil.example"),
                PreferenceBackupEntry(key = "gemini_model", type = "string", stringValue = "gemini-flash"),
            )

            // Full restore path (clears existing first) ...
            repository.importPreferencesFromBackup(foreignBackup, clearExisting = true)
            // ... and the module path (clears except owned keys, then merges).
            repository.clearPreferencesExceptKeys(emptySet())
            repository.importPreferencesFromBackup(foreignBackup, clearExisting = false)

            val prefs = dataStore.data.first()
            assertEquals("local-openai", prefs[stringPreferencesKey("openai_api_key")])
            assertEquals("https://local.example/v1", prefs[stringPreferencesKey("custom_base_url")])
            assertEquals(null, prefs[stringPreferencesKey("groq_api_key")])
            assertEquals("gemini-flash", prefs[stringPreferencesKey("gemini_model")])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
