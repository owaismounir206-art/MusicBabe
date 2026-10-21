package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Qualità richiesta alle sorgenti NetEase/KuGou, in ordine di preferenza. */
enum class DownloadQuality(val storageKey: String) {
    AUTO("auto"),
    HIGH("high"),
    STANDARD("standard");

    /** Qualità NetEase da provare in cascata. */
    val neteaseQualities: List<String>
        get() = when (this) {
            AUTO -> listOf("exhigh", "standard")
            HIGH -> listOf("exhigh", "standard")
            STANDARD -> listOf("standard")
        }

    /** Bitrate KuGou richiesto; `null` lascia decidere alla sorgente. */
    val kugouBitrate: String?
        get() = when (this) {
            AUTO -> null
            HIGH -> "320"
            STANDARD -> "128"
        }

    companion object {
        fun fromStorageKey(value: String?): DownloadQuality =
            entries.firstOrNull { it.storageKey == value } ?: AUTO
    }
}

/**
 * Preferenze del motore di download: limite "solo Wi-Fi" e qualità audio richiesta
 * alle sorgenti interne. Persistite su DataStore, osservabili come Flow.
 */
@Singleton
class DownloadPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only_v1")
        val QUALITY = stringPreferencesKey("download_quality_v1")
    }

    val wifiOnlyFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.WIFI_ONLY] ?: false
    }

    val qualityFlow: Flow<DownloadQuality> = dataStore.data.map { preferences ->
        DownloadQuality.fromStorageKey(preferences[Keys.QUALITY])
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setQuality(quality: DownloadQuality) {
        dataStore.edit { preferences -> preferences[Keys.QUALITY] = quality.storageKey }
    }
}
