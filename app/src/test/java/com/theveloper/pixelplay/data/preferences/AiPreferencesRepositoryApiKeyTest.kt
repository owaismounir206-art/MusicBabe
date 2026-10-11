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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AiPreferencesRepositoryApiKeyTest {

    private class FakeApiKeyStore(var available: Boolean = true) : AiApiKeyStore {
        val values = mutableMapOf<AiProvider, String>()

        override fun get(provider: AiProvider): String? = if (available) values[provider] else null

        override fun put(provider: AiProvider, apiKey: String): Boolean {
            if (!available) return false
            values[provider] = apiKey
            return true
        }

        override fun remove(provider: AiProvider) {
            if (available) values.remove(provider)
        }
    }

    private fun createDataStore(scope: CoroutineScope, dir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dir.resolve("settings.preferences_pb").toFile() }
        )

    private val legacyGeminiKey = stringPreferencesKey("gemini_api_key")

    @Test
    fun `legacy clear-text keys are migrated to secure storage and removed from DataStore`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            dataStore.edit { it[legacyGeminiKey] = "  legacy-secret  " }
            val store = FakeApiKeyStore()
            val repository = AiPreferencesRepository(dataStore, store)

            assertEquals("legacy-secret", repository.getApiKey(AiProvider.GEMINI).first())
            assertEquals("legacy-secret", store.values[AiProvider.GEMINI])
            assertNull(dataStore.data.first()[legacyGeminiKey])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setApiKey writes only to secure storage`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            val store = FakeApiKeyStore()
            val repository = AiPreferencesRepository(dataStore, store)

            assertTrue(repository.setApiKey(AiProvider.OPENAI, " sk-new "))

            assertEquals("sk-new", repository.getApiKey(AiProvider.OPENAI).first())
            assertEquals("sk-new", store.values[AiProvider.OPENAI])
            assertTrue(dataStore.data.first().asMap().values.none { it == "sk-new" })
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setApiKey fails without writing clear text when secure storage is unavailable`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            val repository = AiPreferencesRepository(dataStore, FakeApiKeyStore(available = false))

            assertFalse(repository.setApiKey(AiProvider.GROQ, "gsk-123"))

            assertEquals("", repository.getApiKey(AiProvider.GROQ).first())
            assertTrue(dataStore.data.first().asMap().isEmpty())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `blank key clears the stored key`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-test")
        try {
            val dataStore = createDataStore(backgroundScope, tempDir)
            val store = FakeApiKeyStore().apply { values[AiProvider.MISTRAL] = "old" }
            val repository = AiPreferencesRepository(dataStore, store)

            assertTrue(repository.setApiKey(AiProvider.MISTRAL, "   "))

            assertEquals("", repository.getApiKey(AiProvider.MISTRAL).first())
            assertNull(store.values[AiProvider.MISTRAL])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
