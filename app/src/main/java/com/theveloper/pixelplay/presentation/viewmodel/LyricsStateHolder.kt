package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.MbLrcFormat
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback interface for lyrics loading results.
 * Used to update StablePlayerState in PlayerViewModel.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
}

/**
 * Callbacks supplied by [PlayerViewModel] so the AI-translation flow can reach the AI layer and
 * resolve localized strings without [LyricsStateHolder] depending on AiStateHolder or a Context.
 * Mirrors the callback-lambda pattern used elsewhere (e.g. [LyricsStateHolder.fetchLyricsForSong]).
 *
 * @param translate Delegates the raw lyrics to the AI translator (AiStateHolder.translateLyrics).
 * @param getString Resolves a no-arg string resource.
 * @param getErrorString Resolves the generic AI error string (R.string.ai_error_generic) with a detail.
 */
class LyricsTranslationCallbacks(
    val translate: suspend (String) -> Result<String>,
    val generateSynced: (suspend (title: String, artist: String, durationMs: Long, existingLyrics: String?) -> Result<String>)? = null,
    val getString: (Int) -> String,
    val getErrorString: (String) -> String
)


/**
 * Manages lyrics loading, search state, and sync offset.
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val songMetadataEditor: SongMetadataEditor,
    private val audioAligner: com.theveloper.pixelplay.data.lyrics.align.LyricsAudioAlignmentService? = null
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var loadCallback: LyricsLoadCallback? = null

    // Sync offset per song in milliseconds
    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    // Lyrics search UI state
    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    // Event to notify ViewModel of song updates (e.g. lyrics added)
    private val _songUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Song, Lyrics?>>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val songUpdates = _songUpdates.asSharedFlow()

    // Event for Toasts
    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    /**
     * Initialize with coroutine scope and callback from ViewModel.
     */
    fun initialize(
        coroutineScope: CoroutineScope,
        callback: LyricsLoadCallback,
        stablePlayerState: StateFlow<com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState>
    ) {
        scope = coroutineScope
        loadCallback = callback

        coroutineScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != null) {
                        updateSyncOffsetForSong(songId)
                    }
                }
        }
    }

    /**
     * Load lyrics for a song.
     * @param song The song to load lyrics for
     * @param sourcePreference The preferred source for lyrics
     */
    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        loadingJob?.cancel()
        val targetSongId = song.id

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            val fetchedLyrics = try {
                withContext(Dispatchers.IO) {
                    musicRepository.getLyrics(
                        song = song,
                        sourcePreference = sourcePreference
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }

            loadCallback?.onLyricsLoaded(targetSongId, fetchedLyrics)
        }
    }

    /**
     * Cancel any ongoing lyrics loading.
     */
    fun cancelLoading() {
        loadingJob?.cancel()
    }

    /**
     * Set sync offset for a song.
     */
    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    /**
     * Update sync offset from song ID (called when song changes).
     */
    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    /**
     * Set the lyrics search UI state.
     */
    fun setSearchState(state: LyricsSearchUiState) {
        _searchUiState.value = state
    }

    /**
     * Reset the lyrics search state to idle.
     */
    fun resetSearchState() {
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    /**
     * Fetch lyrics for the given song, respecting the user's source preference.
     */
    fun fetchLyricsForSong(
        song: Song,
        forcePickResults: Boolean,
        sourcePreference: LyricsSourcePreference,
        contextHelper: (Int) -> String
    ) {
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            if (!forcePickResults) {
                val storedLyrics = withContext(Dispatchers.IO) {
                    musicRepository.getStoredLyrics(song)
                }
                if (storedLyrics != null) {
                    val (lyrics, rawLyrics) = storedLyrics
                    _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                    _songUpdates.emit(song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri = null) to lyrics)
                    _messageEvents.emit(contextHelper(R.string.lyrics_already_available))
                    return@launch
                }
            }

            // Build ordered list of local source checks based on user preference.
            // API_FIRST: skip local sources, go straight to remote.
            // EMBEDDED_FIRST: check embedded, then local .lrc, then remote.
            // LOCAL_FIRST: check local .lrc, then embedded, then remote.
            val localSourceChecks: List<suspend () -> Pair<String, Int>?> = when (sourcePreference) {
                LyricsSourcePreference.API_FIRST -> emptyList()
                LyricsSourcePreference.EMBEDDED_FIRST -> listOf(
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } },
                    { readLocalLyricsFile(song)?.let { it to R.string.lyrics_local_lrc_already_available } }
                )
                LyricsSourcePreference.LOCAL_FIRST -> listOf(
                    { readLocalLyricsFile(song)?.let { it to R.string.lyrics_local_lrc_already_available } },
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } }
                )
            }

            // Try local sources in priority order.
            for (sourceCheck in localSourceChecks) {
                val result = withContext(Dispatchers.IO) { sourceCheck() }
                if (result != null) {
                    val (rawLyrics, messageResId) = result
                    val parsed = LyricsUtils.parseLyrics(rawLyrics)
                    if (hasValidLyrics(parsed)) {
                        val lyrics = parsed.copy(areFromRemote = false)
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)

                        val songId = song.id.toLongOrNull()
                        if (songId != null) {
                            musicRepository.updateLyrics(songId, rawLyrics)
                        }

                        _songUpdates.emit(song.copy(lyrics = rawLyrics) to lyrics)
                        _messageEvents.emit(contextHelper(messageResId))
                        return@launch
                    }
                }
            }

            // Fall through to remote fetch.
            if (forcePickResults) {
                musicRepository.searchRemoteLyrics(song)
                    .onSuccess { (query, results) ->
                        _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } else {
                musicRepository.getLyricsFromRemote(song)
                    .onSuccess { (lyrics, rawLyrics) ->
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                        val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                        val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                        _songUpdates.emit(updatedSong to lyrics)
                    }
                    .onFailure { error ->
                        if (error is NoLyricsFoundException) {
                            // Fallback to search
                            musicRepository.searchRemoteLyrics(song)
                                .onSuccess { (query, results) ->
                                    _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                }
                                .onFailure { searchError -> handleError(searchError) }
                        } else {
                            handleError(error)
                        }
                    }
            }
        }
    }

    /**
     * Manual search by query.
     */
    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    _searchUiState.value = LyricsSearchUiState.PickResult(q, results)
                }
                .onFailure { error -> handleError(error) }
        }
    }

    /**
     * Accept a search result.
     */
    fun acceptLyricsSearchResult(result: LyricsSearchResult, currentSong: Song) {
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Success(result.lyrics)

            // 1. Update DB cache
            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, result.rawLyrics)
            }

            // 2. Attempt metadata write-back to the audio file
            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, result.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(result.rawLyrics, refreshedAlbumArtUri)

            // 3. Notify
            _songUpdates.emit(updatedSong to result.lyrics)
        }
    }

    /**
     * Import from file.
     */
    /** Progress (0..100) of the audio word alignment, null when none is running. */
    val audioAlignmentProgress: StateFlow<Int?> =
        audioAligner?.progress ?: MutableStateFlow<Int?>(null).asStateFlow()

    private var audioAlignmentJob: Job? = null

    /**
     * Times the current lyrics word by word by listening to the song on the device (Whisper).
     * The words are always the lyrics we already have; only their timings come from the audio.
     */
    /**
     * Times the lyrics word by word from the audio (on-device Whisper). Songs without lyrics get
     * them written from the audio. The result is saved as `.mblrc` (an LRC copy goes in the tags).
     */
    fun alignLyricsToAudio(currentSong: Song, lyricsObj: Lyrics?, getString: (Int) -> String) {
        val aligner = audioAligner
        if (aligner == null || !aligner.isSupported) {
            _messageEvents.tryEmit(getString(R.string.lyrics_audio_align_unsupported))
            return
        }
        val sourceLines = lyricsObj?.synced?.filter { it.line.isNotBlank() }
        val lines = sourceLines?.map { it.line }?.takeIf { it.isNotEmpty() }
            ?: lyricsObj?.plain?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        runAudioLyricsJob(
            currentSong,
            getString,
            startedMessage = if (lines == null) R.string.lyrics_audio_transcribe_started else R.string.lyrics_audio_align_started,
            source = MBLRC_SOURCE_WHISPER
        ) {
            if (lines == null) {
                aligner.transcribe(currentSong)
            } else {
                aligner.align(currentSong, lines).map { alignment ->
                    // Lyrics that already have line times keep them: the heard word times are kept
                    // only where they agree (on sung music the line timing is the reliable part).
                    if (sourceLines != null && sourceLines.size == alignment.lines.size) {
                        com.theveloper.pixelplay.utils.AudioWordTimingMerge.merge(sourceLines, alignment.lines)
                    } else alignment.lines
                }
            }
        }
    }

    /**
     * Lyrics written by Gemini listening to the song (the user's own key; the audio is uploaded).
     * Opt-in alternative to on-device Whisper.
     */
    fun transcribeLyricsWithCloudAi(
        currentSong: Song,
        getString: (Int) -> String,
        transcribe: suspend (Song) -> Result<List<SyncedLine>>
    ) {
        runAudioLyricsJob(currentSong, getString, R.string.lyrics_audio_cloud_started, MBLRC_SOURCE_GEMINI) {
            transcribe(currentSong)
        }
    }

    private fun runAudioLyricsJob(
        currentSong: Song,
        getString: (Int) -> String,
        startedMessage: Int,
        source: String,
        work: suspend () -> Result<List<SyncedLine>>
    ) {
        val songId = currentSong.id.toLongOrNull() ?: return
        if (audioAlignmentJob?.isActive == true) return

        audioAlignmentJob = scope?.launch {
            _messageEvents.emit(getString(startedMessage))
            work()
                .onSuccess { syncedLines ->
                    val document = MbLrcFormat.encode(syncedLines, source = source)
                    val parsed = LyricsUtils.parseLyrics(document)
                    musicRepository.updateLyrics(songId, document)
                    val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, document)
                    val updatedSong = currentSong.withPersistedLyrics(document, refreshedAlbumArtUri)
                    _songUpdates.emit(updatedSong to parsed.takeIf(::hasValidLyrics))
                    _searchUiState.value = LyricsSearchUiState.Success(parsed)
                    _messageEvents.emit(getString(R.string.lyrics_audio_align_done))
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                    val messageRes = when (error) {
                        is com.theveloper.pixelplay.data.lyrics.align.AudioAlignmentException.NotLocal ->
                            R.string.lyrics_audio_align_not_local
                        is com.theveloper.pixelplay.data.lyrics.align.AudioAlignmentException.NoSpeech,
                        is com.theveloper.pixelplay.data.lyrics.align.AudioAlignmentException.TooFewMatches ->
                            R.string.lyrics_audio_align_too_few
                        is com.theveloper.pixelplay.data.lyrics.align.AudioAlignmentException.Unsupported ->
                            R.string.lyrics_audio_align_unsupported
                        else -> null
                    }
                    val message = when {
                        messageRes != null -> getString(messageRes)
                        error is com.theveloper.pixelplay.data.lyrics.align.CloudLyricsException ->
                            getString(error.messageRes) + (error.detail?.let { " ($it)" } ?: "")
                        else -> getString(R.string.lyrics_audio_align_failed)
                    }
                    _messageEvents.emit(message)
                }
        }
    }

    fun cancelAudioAlignment() {
        audioAligner?.cancel()
        audioAlignmentJob?.cancel()
    }

    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport, currentSong: Song?) {
        scope?.launch {
            val sanitizedContent = validatedImport.sanitizedContent
            val parsedLyrics = validatedImport.parsedLyrics

            musicRepository.updateLyrics(songId, sanitizedContent)

            if (currentSong != null && currentSong.id.toLongOrNull() == songId) {
                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, sanitizedContent)
                val updatedSong = currentSong.withPersistedLyrics(sanitizedContent, refreshedAlbumArtUri)
                _songUpdates.emit(updatedSong to parsedLyrics.takeIf(::hasValidLyrics))
            }

            _messageEvents.emit("Lyrics imported successfully!")
        }
    }

    /**
     * Translate the current song's lyrics via AI and import the result.
     * The actual inference is delegated through [LyricsTranslationCallbacks.translate] so this holder
     * stays decoupled from the AI layer. Toasts are surfaced through [messageEvents] as usual.
     */
    fun translateLyricsViaAi(currentSong: Song, lyricsObj: Lyrics?, cb: LyricsTranslationCallbacks) {
        val songId = currentSong.id.toLongOrNull() ?: return

        if (lyricsObj?.synced != null) {
            val hasValidTranslation = lyricsObj.synced.any { !it.translation.isNullOrBlank() }
            if (hasValidTranslation) {
                _messageEvents.tryEmit(cb.getString(R.string.lyrics_translate_already_translated))
                return
            }
        }

        scope?.launch {
            _messageEvents.emit(cb.getString(R.string.lyrics_translate_progress))

            val rawLyrics = withContext(Dispatchers.IO) {
                currentSong.lyrics?.takeIf { it.isNotBlank() }
                    ?: readLocalLyricsFile(currentSong)
                    ?: readEmbeddedLyricsFromFile(currentSong)
                    ?: musicRepository.getStoredLyrics(currentSong)?.second
            }

            if (rawLyrics.isNullOrBlank()) {
                _messageEvents.emit(cb.getString(R.string.lyrics_not_found))
                return@launch
            }

            val result = cb.translate(rawLyrics)
            result.onSuccess { translatedText ->
                if (translatedText.trim() == "ALREADY_IN_TARGET_LANGUAGE") {
                    _messageEvents.emit(cb.getString(R.string.lyrics_translate_already_in_target_language))
                    return@onSuccess
                }

                if (translatedText.isNotBlank()) {
                    val validation = LyricsImportSecurity.validateImportedLrcContent(translatedText)
                    if (validation is LyricsImportValidationResult.Valid) {
                        importLyricsFromFile(songId, validation.value, currentSong)
                        _messageEvents.emit(cb.getString(R.string.lyrics_translate_success))
                    } else {
                        val reason = (validation as LyricsImportValidationResult.Invalid).reason
                        val errorMsg = cb.getString(LyricsImportSecurity.messageResFor(reason))
                        _messageEvents.emit(cb.getErrorString(errorMsg))
                    }
                } else {
                    _messageEvents.emit(cb.getErrorString("Empty response"))
                }
            }.onFailure {
                if (it.message?.contains("key", ignoreCase = true) == true ||
                    it.message?.contains("config", ignoreCase = true) == true
                ) {
                    _messageEvents.emit(cb.getString(R.string.ai_state_error_api_key))
                } else {
                    _messageEvents.emit(cb.getErrorString(it.message ?: ""))
                }
            }
        }
    }

    /**
     * Generates and synchronizes lyrics via the configured AI provider.
     *
     * The AI result is the only accepted source: no synthetic/estimated timing is fabricated,
     * per the app policy that lyrics must be real. When [autoMode] is true (automatic sync
     * triggered by playback) the operation is silent — no progress toast and no failure toasts —
     * so background generation never spams the UI.
     */
    fun generateSyncedLyricsViaAi(
        currentSong: Song,
        cb: LyricsTranslationCallbacks,
        autoMode: Boolean = false
    ) {
        val songId = currentSong.id.toLongOrNull() ?: return

        scope?.launch {
            if (!autoMode) {
                _messageEvents.emit(cb.getString(R.string.ai_lyrics_sync_in_progress))
            }

            val existingPlainLyrics = withContext(Dispatchers.IO) {
                currentSong.lyrics?.takeIf { it.isNotBlank() }
                    ?: readLocalLyricsFile(currentSong)
                    ?: readEmbeddedLyricsFromFile(currentSong)
                    ?: musicRepository.getStoredLyrics(currentSong)?.second
            }

            // Without the real lyrics the model would have to write them from memory: Gemini blocks
            // that for copyrighted songs (empty "RECITATION" answer after a long wait) and other
            // models invent words. Only timing of lyrics we already have is asked from the AI.
            if (existingPlainLyrics.isNullOrBlank()) {
                if (!autoMode) {
                    _messageEvents.emit(cb.getString(R.string.ai_lyrics_need_plain_text))
                }
                return@launch
            }

            val durationMs = currentSong.duration.coerceAtLeast(30_000L)

            val aiResult = cb.generateSynced?.invoke(
                currentSong.title,
                currentSong.displayArtist,
                durationMs,
                existingPlainLyrics
            )

            var lrcToImport: String? = null

            if (aiResult != null && aiResult.isSuccess) {
                val content = aiResult.getOrNull()?.trim()
                if (!content.isNullOrBlank()) {
                    lrcToImport = content
                }
            }

            if (lrcToImport != null) {
                val validation = LyricsImportSecurity.validateImportedLrcContent(lrcToImport)
                if (validation is LyricsImportValidationResult.Valid) {
                    importLyricsFromFile(songId, validation.value, currentSong)
                    _messageEvents.emit(
                        if (autoMode) "Testo sincronizzato generato automaticamente per ${currentSong.title}"
                        else "Testo sincronizzato generato con successo!"
                    )
                    _searchUiState.value = LyricsSearchUiState.Success(validation.value.parsedLyrics)
                } else if (!autoMode) {
                    val reason = (validation as LyricsImportValidationResult.Invalid).reason
                    _messageEvents.emit(
                        cb.getErrorString(cb.getString(LyricsImportSecurity.messageResFor(reason)))
                    )
                }
            } else if (!autoMode) {
                val error = aiResult?.exceptionOrNull()
                if (error?.message?.contains("key", ignoreCase = true) == true ||
                    error?.message?.contains("config", ignoreCase = true) == true
                ) {
                    _messageEvents.emit(cb.getString(R.string.ai_state_error_api_key))
                } else {
                    _messageEvents.emit(cb.getString(R.string.ai_lyrics_sync_failed))
                }
            }
        }
    }


    fun resetLyrics(songId: Long) {

        resetSearchState()
        scope?.launch {
            musicRepository.resetLyrics(songId)
            _songUpdates.emit(Song.emptySong().copy(id = songId.toString()) to null)
        }
    }

    fun resetAllLyrics() {
        resetSearchState()
        scope?.launch {
            musicRepository.resetAllLyrics()
        }
    }

    private fun handleError(error: Throwable) {
        _searchUiState.value = if (error is NoLyricsFoundException) {
            LyricsSearchUiState.NotFound("Lyrics not found")
        } else {
            LyricsSearchUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun hasValidLyrics(lyrics: Lyrics?): Boolean {
        if (lyrics == null) return false
        return !lyrics.synced.isNullOrEmpty() || !lyrics.plain.isNullOrEmpty()
    }

    private fun readEmbeddedLyricsFromFile(song: Song): String? {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return runCatching {
            AudioMetadataReader.read(File(song.path))
                ?.lyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readLocalLyricsFile(song: Song): String? {
        return runCatching {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@runCatching null
            // Same-basename sidecar files in the song's own folder only, tried in the order of
            // supportedFileExtensions(): .lrc, .ttml, then the subtitle formats .srt and .vtt.
            for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                val lyricsFile = File(directory, "${songFile.nameWithoutExtension}.$extension")
                if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                when (val validation = LyricsImportSecurity.validateLocalLyricsFile(lyricsFile)) {
                    is LyricsImportValidationResult.Valid -> return@runCatching validation.value.sanitizedContent
                    is LyricsImportValidationResult.Invalid -> continue
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun persistLyricsToFileMetadataIfPossible(song: Song, rawLyrics: String): String? {
        val songId = song.id.toLongOrNull() ?: return null
        // Tags get the enhanced LRC equivalent of a .mblrc document: other players read LRC.
        val portableLyrics = if (MbLrcFormat.looksLikeMbLrc(rawLyrics)) {
            LyricsUtils.parseLyrics(rawLyrics).synced?.let(LyricsUtils::syncedToLrcString) ?: return null
        } else rawLyrics
        val normalizedLyrics = portableLyrics.trim()
        if (normalizedLyrics.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val existingArtwork = runCatching {
                AudioMetadataReader.read(File(song.path))?.artwork
            }.getOrNull()

            val coverArtUpdate = existingArtwork?.let { artwork ->
                CoverArtUpdate(
                    bytes = artwork.bytes,
                    mimeType = artwork.mimeType ?: "image/jpeg"
                )
            }

            runCatching {
                songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre ?: "",
                    newLyrics = normalizedLyrics,
                    newTrackNumber = song.trackNumber,
                    newDiscNumber = song.discNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }.getOrNull()?.updatedAlbumArtUri
        }
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}

internal fun Song.withPersistedLyrics(rawLyrics: String, refreshedAlbumArtUri: String?): Song {
    return copy(
        lyrics = rawLyrics,
        // Lyrics writes can refresh the cached cover-art file path. Carry it forward immediately
        // so the full player doesn't keep rendering a deleted image URI until the next app reload.
        albumArtUriString = refreshedAlbumArtUri ?: albumArtUriString
    )
}

private const val MBLRC_SOURCE_WHISPER = "whisper"
private const val MBLRC_SOURCE_GEMINI = "gemini"
