package com.theveloper.pixelplay.data.notification

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NewSongAlert(
    val isSuccess: Boolean,
    val song: Song? = null,
    val message: String = if (isSuccess) "Il brano è stato aggiunto. Riproduci ora" else "Il brano non è stato aggiunto"
)

@Singleton
class NewSongAlertManager @Inject constructor() {
    private val _alert = MutableStateFlow<NewSongAlert?>(null)
    val alert: StateFlow<NewSongAlert?> = _alert.asStateFlow()

    fun postAlert(alert: NewSongAlert) {
        _alert.value = alert
    }

    fun dismiss() {
        _alert.value = null
    }
}
