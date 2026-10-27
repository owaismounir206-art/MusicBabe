package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlinePreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentPlayingUrl = MutableStateFlow<String?>(null)
    val currentPlayingUrl = _currentPlayingUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun togglePreview(url: String) {
        if (_currentPlayingUrl.value == url && _isPlaying.value) {
            pausePreview()
        } else {
            playPreview(url)
        }
    }

    fun playPreview(url: String) {
        stopPreview()

        _currentPlayingUrl.value = url
        _isLoading.value = true

        // Assigned before setDataSource, so a failure there is still released by stopPreview().
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    _isLoading.value = false
                    _isPlaying.value = true
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _isLoading.value = false
                    _currentPlayingUrl.value = null
                }
                setOnErrorListener { _, what, extra ->
                    Timber.w("Audio preview error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    _isLoading.value = false
                    _currentPlayingUrl.value = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start preview audio playback")
            _isLoading.value = false
            _isPlaying.value = false
            _currentPlayingUrl.value = null
        }
    }

    fun pausePreview() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Timber.e(e, "Error pausing preview")
        }
        _isPlaying.value = false
    }

    fun stopPreview() {
        // release() is valid in every state (stop() throws while preparing or after an error),
        // and the prepared listener goes first so a preview still loading never starts.
        mediaPlayer?.let { mp ->
            mp.setOnPreparedListener(null)
            runCatching { mp.release() }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentPlayingUrl.value = null
    }
}
