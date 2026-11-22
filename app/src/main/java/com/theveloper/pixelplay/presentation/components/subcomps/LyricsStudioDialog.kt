package com.theveloper.pixelplay.presentation.components.subcomps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.editor.LyricsTextTools
import com.theveloper.pixelplay.data.lyrics.editor.LyricsTimingSession
import com.theveloper.pixelplay.data.lyrics.editor.TimingLine
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.fluido.FluidoConnectedToggle
import com.theveloper.pixelplay.presentation.components.fluido.FluidoShapes
import com.theveloper.pixelplay.utils.MbLrcFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** The three steps of making lyrics by hand. */
enum class StudioTab { Text, Timing, Preview }

/** Speeds offered while tapping: slower songs are easier to follow. */
private val STUDIO_SPEEDS = listOf(0.5f, 0.75f, 1f)

/** How much earlier than the tap a mark lands (people tap a little after they hear the line). */
private val STUDIO_ANTICIPATION = listOf(0L, 100L, 200L, 300L)

private const val STUDIO_REWIND_MS = 3_000L
private const val STUDIO_NUDGE_MS = 100L

/**
 * Snapshot-observable wrapper of [LyricsTimingSession], so every tap, undo or nudge recomposes
 * the list. A text edit replaces the session ([retext]), carrying over the times that still fit.
 */
@Stable
private class StudioTiming(initial: LyricsTimingSession) {
    private var session = initial
    var lines by mutableStateOf(initial.lines)
        private set
    var cursorLine by mutableIntStateOf(initial.cursorLine)
        private set
    var cursorWord by mutableIntStateOf(initial.cursorWord)
        private set
    var canUndo by mutableStateOf(initial.canUndo)
        private set

    fun edit(block: LyricsTimingSession.() -> Unit) {
        session.block()
        sync()
    }

    fun retext(text: String) {
        val newLines = LyricsTimingSession.retext(session.lines, text.lines())
        if (newLines != session.lines) replace(LyricsTimingSession(newLines))
    }

    fun replace(newSession: LyricsTimingSession) {
        session = newSession
        sync()
    }

    fun outOfOrder(): Set<Int> = session.outOfOrder()
    fun synced(): List<SyncedLine> = session.toSyncedLines()

    private fun sync() {
        lines = session.lines
        cursorLine = session.cursorLine
        cursorWord = session.cursorWord
        canUndo = session.canUndo
    }
}

/**
 * Lyrics studio: write or paste the text (cleaned of web-page noise, or dictated by voice), then
 * dictate the timing by tapping along with the song, a line or a word at a time, slowed down if
 * needed, with the volume keys as a clicker; then watch the result before saving it.
 */
@Composable
fun LyricsStudioDialog(
    song: Song,
    initialLyrics: Lyrics?,
    initialTab: StudioTab,
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    playbackSpeedProvider: () -> Float = { 1f },
    onSetPlaybackSpeed: ((Float) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val initialSession = remember(initialLyrics) {
        LyricsTimingSession.from(initialLyrics?.synced, initialLyrics?.plain)
    }
    val initialText = remember(initialSession) { initialSession.lines.joinToString("\n") { it.text } }
    val initialLines = remember(initialSession) { initialSession.lines }
    val timing = remember(initialSession) { StudioTiming(initialSession) }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    var tab by rememberSaveable { mutableStateOf(if (initialText.isBlank()) StudioTab.Text else initialTab) }
    var wordMode by rememberSaveable { mutableStateOf(initialSession.lines.any { it.wordTimes != null }) }
    var anticipationMs by rememberSaveable { mutableLongStateOf(100L) }
    var volumeKeys by rememberSaveable { mutableStateOf(true) }
    var focusLine by remember { mutableIntStateOf(-1) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saveCheck by remember { mutableStateOf<SaveCheck?>(null) }

    // Playback speed: restored to what it was when the studio closes.
    val originalSpeed = remember { playbackSpeedProvider() }
    var speed by remember { mutableFloatStateOf(originalSpeed) }
    DisposableEffect(Unit) {
        onDispose { if (speed != originalSpeed) onSetPlaybackSpeed?.invoke(originalSpeed) }
    }

    var positionMs by remember { mutableLongStateOf(currentPositionProvider()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            positionMs = currentPositionProvider()
            delay(40L)
        }
    }
    val isPlaying = isPlayingProvider()
    val durationMs = song.duration.coerceAtLeast(0L)

    // Leaving the text tab: the lines follow the text, keeping the times that still match.
    LaunchedEffect(tab) {
        if (tab != StudioTab.Text) timing.retext(text.text)
    }

    val messages = studioMessages()

    fun mark() {
        if (timing.cursorLine !in timing.lines.indices) return
        val at = (currentPositionProvider() - (anticipationMs * speed).toLong()).coerceAtLeast(0L)
        val line = timing.cursorLine
        timing.edit { if (wordMode) markWord(at) else markLine(at) }
        focusLine = line
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun undo() {
        if (!timing.canUndo) return
        timing.edit { undo() }
        focusLine = timing.cursorLine
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun undoableTextChange(newText: String, message: String) {
        val before = text
        text = TextFieldValue(newText, TextRange(newText.length))
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = messages.undo, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) text = before
        }
    }

    // Voice dictation of the text: the song pauses so the microphone hears only the voice.
    var resumeAfterDictation by remember { mutableStateOf(false) }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            text = insertLines(text, LyricsTextTools.linesFromDictation(spoken))
        }
        if (resumeAfterDictation) {
            resumeAfterDictation = false
            onPlayPause()
        }
    }
    fun dictate() {
        if (isPlaying) {
            resumeAfterDictation = true
            onPlayPause()
        }
        try {
            dictation.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, messages.dictationPrompt)
            )
        } catch (_: ActivityNotFoundException) {
            if (resumeAfterDictation) {
                resumeAfterDictation = false
                onPlayPause()
            }
            scope.launch { snackbar.showSnackbar(messages.noDictation) }
        }
    }

    val dirty = text.text.trim() != initialText.trim() || timing.lines != initialLines

    fun requestClose() {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    fun trySave(fill: Boolean = false, ignoreOrder: Boolean = false) {
        timing.retext(text.text)
        if (fill) timing.edit { fillGaps(durationMs) }
        val lines = timing.lines
        val timed = lines.count { it.timeMs != null }
        val disorder = timing.outOfOrder().size
        when {
            lines.isEmpty() -> onDismiss()
            timed == 0 -> onSave(lines.joinToString("\n") { it.text })
            timed < lines.size -> saveCheck = SaveCheck(missing = lines.size - timed, outOfOrder = disorder)
            disorder > 0 && !ignoreOrder -> saveCheck = SaveCheck(missing = 0, outOfOrder = disorder)
            else -> onSave(encodeStudioLyrics(timing.synced()))
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(tab) {
        if (tab == StudioTab.Timing) runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    // Volume keys as a clicker while timing: down marks, up undoes.
                    if (!volumeKeys || tab != StudioTab.Timing) return@onPreviewKeyEvent false
                    val isDown = event.key == Key.VolumeDown
                    if (!isDown && event.key != Key.VolumeUp) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        if (isDown) mark() else undo()
                    }
                    true
                },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding()
                ) {
                    StudioTopBar(song = song, canSave = text.text.isNotBlank(), onClose = ::requestClose, onSave = { trySave() })
                    FluidoConnectedToggle(
                        options = listOf(
                            stringResource(R.string.studio_tab_text),
                            stringResource(R.string.studio_tab_timing),
                            stringResource(R.string.studio_tab_preview),
                        ),
                        selected = tab.ordinal,
                        onSelect = { tab = StudioTab.entries[it] },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    when (tab) {
                        StudioTab.Text -> StudioTextTab(
                            value = text,
                            onValueChange = { text = it },
                            onPaste = {
                                val pasted = clipboard.getText()?.text.orEmpty()
                                if (pasted.isBlank()) {
                                    scope.launch { snackbar.showSnackbar(messages.clipboardEmpty) }
                                    return@StudioTextTab
                                }
                                val timed = LyricsTimingSession.parseTimed(pasted)
                                if (timed != null) {
                                    val newText = timed.joinToString("\n") { it.text }
                                    text = TextFieldValue(newText, TextRange(newText.length))
                                    timing.replace(LyricsTimingSession(timed))
                                    scope.launch { snackbar.showSnackbar(messages.pastedTimed(timed.size)) }
                                } else {
                                    val cleaned = LyricsTextTools.clean(pasted)
                                    text = insertLines(text, cleaned.text.lines())
                                    if (cleaned.removedLines > 0) scope.launch { snackbar.showSnackbar(messages.cleaned(cleaned.removedLines)) }
                                }
                            },
                            onDictate = ::dictate,
                            onClean = {
                                val cleaned = LyricsTextTools.clean(text.text)
                                if (cleaned.text != text.text) undoableTextChange(cleaned.text, messages.cleaned(cleaned.removedLines))
                                else scope.launch { snackbar.showSnackbar(messages.alreadyClean) }
                            },
                            onSplit = {
                                val split = LyricsTextTools.splitLongLines(text.text)
                                if (split != text.text) undoableTextChange(split, messages.split)
                                else scope.launch { snackbar.showSnackbar(messages.noLongLines) }
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(text.text))
                                scope.launch { snackbar.showSnackbar(messages.copied) }
                            },
                            onClear = { undoableTextChange("", messages.cleared) },
                            onStartTiming = { tab = StudioTab.Timing },
                        )

                        StudioTab.Timing -> StudioTimingTab(
                            timing = timing,
                            wordMode = wordMode,
                            onWordMode = { wordMode = it },
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isPlaying = isPlaying,
                            speed = speed,
                            canChangeSpeed = onSetPlaybackSpeed != null,
                            onSpeed = {
                                speed = it
                                onSetPlaybackSpeed?.invoke(it)
                            },
                            anticipationMs = anticipationMs,
                            onAnticipation = { anticipationMs = it },
                            volumeKeys = volumeKeys,
                            onVolumeKeys = { volumeKeys = it },
                            focusLine = focusLine,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onMark = ::mark,
                            onUndo = ::undo,
                            onSelect = { index ->
                                timing.edit { select(index) }
                                focusLine = index
                                timing.lines.getOrNull(index)?.timeMs?.let { onSeek((it - 2_000L).coerceAtLeast(0L)) }
                            },
                            onNudge = { index, delta -> timing.edit { nudge(index, delta) } },
                            onClearLine = { index -> timing.edit { clear(index) } },
                            onShiftAll = { delta ->
                                timing.edit { shiftAll(delta) }
                                scope.launch { snackbar.showSnackbar(messages.shifted(delta)) }
                            },
                            onFillGaps = {
                                timing.edit { fillGaps(durationMs) }
                                scope.launch { snackbar.showSnackbar(messages.filled) }
                            },
                            onClearAll = {
                                timing.edit { clearAll() }
                                focusLine = -1
                                scope.launch {
                                    if (snackbar.showSnackbar(messages.timesCleared, actionLabel = messages.undo, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                                        timing.edit { undo() }
                                    }
                                }
                            },
                        )

                        StudioTab.Preview -> StudioPreviewTab(
                            lines = remember(timing.lines) { timing.synced() },
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isPlaying = isPlaying,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onGoToTiming = { tab = StudioTab.Timing },
                        )
                    }
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 120.dp)
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.studio_discard_title)) },
            text = { Text(stringResource(R.string.studio_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.studio_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.studio_keep_editing)) }
            },
        )
    }

    saveCheck?.let { check ->
        AlertDialog(
            onDismissRequest = { saveCheck = null },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = {
                Text(
                    if (check.missing > 0) pluralStringResource(R.plurals.studio_missing_title, check.missing, check.missing)
                    else pluralStringResource(R.plurals.studio_out_of_order_title, check.outOfOrder, check.outOfOrder)
                )
            },
            text = {
                Text(
                    stringResource(if (check.missing > 0) R.string.studio_missing_body else R.string.studio_out_of_order_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    saveCheck = null
                    if (check.missing > 0) trySave(fill = true) else trySave(ignoreOrder = true)
                }) {
                    Text(stringResource(if (check.missing > 0) R.string.studio_fill_and_save else R.string.studio_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    saveCheck = null
                    tab = StudioTab.Timing
                    val target = if (check.missing > 0) timing.lines.indexOfFirst { it.timeMs == null } else timing.outOfOrder().minOrNull() ?: 0
                    timing.edit { select(target.coerceAtLeast(0)) }
                    focusLine = target
                }) { Text(stringResource(R.string.studio_keep_tapping)) }
            },
        )
    }
}

private data class SaveCheck(val missing: Int, val outOfOrder: Int)

/** Word-timed lines are saved as .mblrc (karaoke), the others as LRC. */
internal fun encodeStudioLyrics(lines: List<SyncedLine>): String =
    if (lines.any { !it.words.isNullOrEmpty() }) {
        MbLrcFormat.encode(lines, source = "editor")
    } else {
        lines.joinToString("\n") { "[${formatStudioTime(it.time.toLong())}]${it.line}" }
    }

/** m:ss.cc, the LRC way (minutes not padded past two digits). */
internal fun formatStudioTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe / 1000L) % 60L
    val hundredths = (safe % 1000L) / 10L
    return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths)
}

private fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

/** Lines inserted at the caret, on lines of their own. */
private fun insertLines(value: TextFieldValue, lines: List<String>): TextFieldValue {
    val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (clean.isEmpty()) return value
    val pos = value.selection.end.coerceIn(0, value.text.length)
    val before = value.text.substring(0, pos)
    val after = value.text.substring(pos)
    val prefix = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
    val suffix = if (after.isEmpty() || after.startsWith("\n")) "" else "\n"
    val inserted = prefix + clean.joinToString("\n") + suffix
    return TextFieldValue(before + inserted + after, TextRange(pos + inserted.length - suffix.length))
}

private class StudioMessages(
    val dictationPrompt: String,
    val noDictation: String,
    val clipboardEmpty: String,
    val alreadyClean: String,
    val noLongLines: String,
    val split: String,
    val copied: String,
    val cleared: String,
    val filled: String,
    val timesCleared: String,
    val undo: String,
    private val pastedTimedFormat: (Int) -> String,
    private val cleanedFormat: (Int) -> String,
    private val shiftedFormat: (String) -> String,
) {
    fun pastedTimed(count: Int) = pastedTimedFormat(count)
    fun cleaned(count: Int) = cleanedFormat(count)
    fun shifted(deltaMs: Long) = shiftedFormat(String.format(Locale.getDefault(), "%+.1f s", deltaMs / 1000.0))
}

@Composable
private fun studioMessages(): StudioMessages {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return StudioMessages(
        dictationPrompt = stringResource(R.string.studio_dictation_prompt),
        noDictation = stringResource(R.string.studio_dictation_missing),
        clipboardEmpty = stringResource(R.string.studio_clipboard_empty),
        alreadyClean = stringResource(R.string.studio_already_clean),
        noLongLines = stringResource(R.string.studio_no_long_lines),
        split = stringResource(R.string.studio_split_done),
        copied = stringResource(R.string.studio_copied),
        cleared = stringResource(R.string.studio_text_cleared),
        filled = stringResource(R.string.studio_filled),
        timesCleared = stringResource(R.string.studio_times_cleared),
        undo = stringResource(R.string.studio_undo),
        pastedTimedFormat = { res.getQuantityString(R.plurals.studio_pasted_timed, it, it) },
        cleanedFormat = { if (it > 0) res.getQuantityString(R.plurals.studio_cleaned, it, it) else res.getString(R.string.studio_already_clean) },
        shiftedFormat = { res.getString(R.string.studio_shifted, it) },
    )
}

@Composable
private fun StudioTopBar(song: Song, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.studio_close)) }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.studio_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(song.title, song.displayArtist).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = onSave,
            enabled = canSave,
            shape = FluidoShapes.Pill,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.height(40.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.studio_save), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun StudioToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, selected: Boolean = false) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun StudioTextTab(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onPaste: () -> Unit,
    onDictate: () -> Unit,
    onClean: () -> Unit,
    onSplit: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onStartTiming: () -> Unit,
) {
    val lineCount = value.text.lines().count { it.isNotBlank() }
    val longLines = value.text.lines().count { it.trim().length > 44 }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioToolChip(stringResource(R.string.studio_paste), Icons.Rounded.ContentPaste, onPaste)
            StudioToolChip(stringResource(R.string.studio_dictate), Icons.Rounded.Mic, onDictate)
            StudioToolChip(stringResource(R.string.studio_clean), Icons.Rounded.CleaningServices, onClean)
            StudioToolChip(stringResource(R.string.studio_split), Icons.AutoMirrored.Rounded.WrapText, onSplit)
            StudioToolChip(stringResource(R.string.studio_copy), Icons.Rounded.ContentCopy, onCopy)
            StudioToolChip(stringResource(R.string.studio_clear), Icons.Rounded.DeleteSweep, onClear)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.studio_text_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            shape = RoundedCornerShape(20.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.studio_line_count, lineCount, lineCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (longLines > 0) pluralStringResource(R.plurals.studio_long_lines, longLines, longLines)
                    else stringResource(R.string.studio_dictation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onStartTiming,
                enabled = lineCount > 0,
                shape = FluidoShapes.Pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.height(48.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.studio_start_timing), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun StudioTransport(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val playPauseLabel = stringResource(if (isPlaying) R.string.studio_pause else R.string.studio_play)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(dragging?.toLong() ?: positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = (dragging ?: positionMs.toFloat()).coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toLong()) }
                        dragging = null
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(formatClock(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { onSeek((positionMs - STUDIO_REWIND_MS).coerceAtLeast(0L)) },
                    shape = FluidoShapes.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.height(40.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.studio_rewind), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(
                    onClick = onPlayPause,
                    shape = FluidoShapes.Fab,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .semantics { contentDescription = playPauseLabel },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                }
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
    }
}

@Composable
private fun StudioTimingTab(
    timing: StudioTiming,
    wordMode: Boolean,
    onWordMode: (Boolean) -> Unit,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    speed: Float,
    canChangeSpeed: Boolean,
    onSpeed: (Float) -> Unit,
    anticipationMs: Long,
    onAnticipation: (Long) -> Unit,
    volumeKeys: Boolean,
    onVolumeKeys: (Boolean) -> Unit,
    focusLine: Int,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onMark: () -> Unit,
    onUndo: () -> Unit,
    onSelect: (Int) -> Unit,
    onNudge: (Int, Long) -> Unit,
    onClearLine: (Int) -> Unit,
    onShiftAll: (Long) -> Unit,
    onFillGaps: () -> Unit,
    onClearAll: () -> Unit,
) {
    val lines = timing.lines
    val outOfOrder = remember(lines) { timing.outOfOrder() }
    val timedCount = lines.count { it.timeMs != null }
    val playingIndex = remember(lines, positionMs) {
        lines.indices.lastOrNull { i -> lines[i].timeMs?.let { it <= positionMs } == true }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(timing.cursorLine) { scrollToKeep(listState, timing.cursorLine) }

    Column(Modifier.fillMaxSize()) {
        FluidoConnectedToggle(
            options = listOf(stringResource(R.string.studio_mode_line), stringResource(R.string.studio_mode_word)),
            selected = if (wordMode) 1 else 0,
            onSelect = { onWordMode(it == 1) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        StudioTransport(positionMs, durationMs, isPlaying, onPlayPause, onSeek) {
            if (canChangeSpeed) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    STUDIO_SPEEDS.forEachIndexed { i, s ->
                        val selected = kotlin.math.abs(s - speed) < 0.01f
                        val shape = RoundedCornerShape(
                            topStart = if (i == 0) 20.dp else 4.dp, bottomStart = if (i == 0) 20.dp else 4.dp,
                            topEnd = if (i == STUDIO_SPEEDS.lastIndex) 20.dp else 4.dp, bottomEnd = if (i == STUDIO_SPEEDS.lastIndex) 20.dp else 4.dp,
                        )
                        Surface(
                            onClick = { onSpeed(s) },
                            shape = shape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(40.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(speedLabel(s), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        // Tools for the whole song.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioToolChip(stringResource(R.string.studio_fill_gaps), Icons.Rounded.AutoFixHigh, onFillGaps)
            StudioToolChip(stringResource(R.string.studio_shift_earlier), Icons.Rounded.FastRewind, { onShiftAll(-STUDIO_NUDGE_MS) })
            StudioToolChip(stringResource(R.string.studio_shift_later), Icons.Rounded.FastForward, { onShiftAll(STUDIO_NUDGE_MS) })
            val nextAnticipation = STUDIO_ANTICIPATION[(STUDIO_ANTICIPATION.indexOf(anticipationMs) + 1).mod(STUDIO_ANTICIPATION.size)]
            StudioToolChip(
                stringResource(R.string.studio_anticipation, anticipationMs.toInt()),
                Icons.Rounded.TouchApp,
                { onAnticipation(nextAnticipation) },
                selected = anticipationMs > 0
            )
            StudioToolChip(stringResource(R.string.studio_volume_keys), Icons.Rounded.Check, { onVolumeKeys(!volumeKeys) }, selected = volumeKeys)
            StudioToolChip(stringResource(R.string.studio_clear_times), Icons.Rounded.DeleteSweep, onClearAll)
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.studio_progress, timedCount, lines.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (outOfOrder.isNotEmpty()) {
                Surface(
                    onClick = { onSelect(outOfOrder.min()) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(pluralStringResource(R.plurals.studio_out_of_order_chip, outOfOrder.size, outOfOrder.size), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
                TimingRow(
                    line = line,
                    isCursor = index == timing.cursorLine,
                    cursorWord = if (index == timing.cursorLine && wordMode) timing.cursorWord else -1,
                    showWords = wordMode,
                    isPlaying = index == playingIndex,
                    isOutOfOrder = index in outOfOrder,
                    showTools = index == focusLine && line.timeMs != null,
                    onClick = { onSelect(index) },
                    onNudge = { onNudge(index, it) },
                    onClear = { onClearLine(index) },
                )
            }
        }
        TapBar(
            timing = timing,
            wordMode = wordMode,
            canUndo = timing.canUndo,
            volumeKeys = volumeKeys,
            onMark = onMark,
            onUndo = onUndo,
        )
    }
}

private suspend fun scrollToKeep(state: LazyListState, index: Int) {
    if (index < 0) return
    val visible = state.layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: 0
    val last = visible.lastOrNull()?.index ?: 0
    // Keep the next line to tap in the upper part of the list, with two lines of context above it.
    if (index < first + 1 || index > last - 3) state.animateScrollToItem((index - 2).coerceAtLeast(0))
}

private fun speedLabel(speed: Float): String = java.text.NumberFormat.getInstance().format(speed) + "×"

@Composable
private fun TimingRow(
    line: TimingLine,
    isCursor: Boolean,
    cursorWord: Int,
    showWords: Boolean,
    isPlaying: Boolean,
    isOutOfOrder: Boolean,
    showTools: Boolean,
    onClick: () -> Unit,
    onNudge: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val background = when {
        isCursor -> c.secondaryContainer
        isPlaying -> c.surfaceContainerHigh
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                line.timeMs?.let { formatStudioTime(it) } ?: "– –",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = when {
                    isOutOfOrder -> c.error
                    line.timeMs != null -> c.primary
                    else -> c.onSurfaceVariant
                },
                modifier = Modifier
                    .width(76.dp)
                    .padding(top = 2.dp),
            )
            Text(
                text = timingText(line, cursorWord, showWords),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isCursor) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isCursor) c.onSecondaryContainer else c.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (showTools) {
            Row(
                Modifier.padding(start = 70.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTool(stringResource(R.string.studio_nudge_earlier)) { onNudge(-STUDIO_NUDGE_MS) }
                SmallTool(stringResource(R.string.studio_nudge_later)) { onNudge(STUDIO_NUDGE_MS) }
                SmallTool(stringResource(R.string.studio_clear_line)) { onClear() }
            }
        }
    }
}

@Composable
private fun SmallTool(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = FluidoShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The line with its marked words in the accent colour and the next word to tap underlined. */
@Composable
private fun timingText(line: TimingLine, cursorWord: Int, showWords: Boolean): AnnotatedString {
    val c = MaterialTheme.colorScheme
    if (!showWords) return AnnotatedString(line.text)
    val words = line.words
    val times = line.wordTimes
    return buildAnnotatedString {
        words.forEachIndexed { i, word ->
            if (i > 0) append(' ')
            val marked = times?.getOrNull(i) != null
            val style = when {
                i == cursorWord -> SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
                marked -> SpanStyle(color = c.primary)
                else -> SpanStyle()
            }
            withStyle(style) { append(word) }
        }
    }
}

@Composable
private fun TapBar(
    timing: StudioTiming,
    wordMode: Boolean,
    canUndo: Boolean,
    volumeKeys: Boolean,
    onMark: () -> Unit,
    onUndo: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val line = timing.lines.getOrNull(timing.cursorLine)
    val finished = line == null
    val next = when {
        line == null -> null
        wordMode -> line.words.getOrNull(timing.cursorWord) ?: line.text
        else -> line.text
    }
    Surface(color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onUndo,
                    enabled = canUndo,
                    shape = RoundedCornerShape(24.dp),
                    color = c.secondaryContainer,
                    contentColor = c.onSecondaryContainer,
                    modifier = Modifier.size(width = 72.dp, height = 88.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                        Text(stringResource(R.string.studio_undo), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Surface(
                    onClick = onMark,
                    enabled = !finished,
                    shape = RoundedCornerShape(28.dp),
                    color = if (finished) c.surfaceContainerHighest else c.primary,
                    contentColor = if (finished) c.onSurfaceVariant else c.onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp),
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(
                                when {
                                    finished -> R.string.studio_all_timed
                                    wordMode -> R.string.studio_tap_word
                                    else -> R.string.studio_tap_line
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        if (next != null) {
                            Text(
                                next,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = c.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(if (volumeKeys) R.string.studio_tap_hint_volume else R.string.studio_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StudioPreviewTab(
    lines: List<SyncedLine>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onGoToTiming: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val current = remember(lines, positionMs) { lines.indexOfLast { it.time <= positionMs } }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        StudioTransport(positionMs, durationMs, isPlaying, onPlayPause, onSeek)
        if (lines.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.studio_preview_empty), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onGoToTiming) { Text(stringResource(R.string.studio_start_timing)) }
            }
            return
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
                val isCurrent = index == current
                val distance = if (current < 0) 3 else kotlin.math.abs(index - current)
                val alpha = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.6f
                    else -> 0.38f
                }
                Text(
                    text = previewText(line, positionMs, isCurrent),
                    style = if (isCurrent) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                    else MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.onSurface.copy(alpha = alpha),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.time.toLong()) },
                )
            }
        }
    }
}

/** The current line with the words already sung in the accent colour. */
@Composable
private fun previewText(line: SyncedLine, positionMs: Long, isCurrent: Boolean): AnnotatedString {
    val words = line.words
    if (!isCurrent || words.isNullOrEmpty()) return AnnotatedString(line.line)
    val sung = SpanStyle(color = MaterialTheme.colorScheme.primary)
    return buildAnnotatedString {
        words.forEachIndexed { i, w ->
            if (i > 0) append(' ')
            if (w.time <= positionMs) withStyle(sung) { append(w.word) } else append(w.word)
        }
    }
}
