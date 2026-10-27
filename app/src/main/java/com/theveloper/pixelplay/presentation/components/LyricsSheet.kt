package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.combinedClickable
import com.theveloper.pixelplay.utils.LyricShareCard
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import android.widget.Toast
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsProvider
import com.theveloper.pixelplay.R
import androidx.activity.compose.BackHandler
import com.theveloper.pixelplay.presentation.components.scoped.LyricsPredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.em
import com.theveloper.pixelplay.ui.theme.Fluido
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.consumePositionChange
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import com.theveloper.pixelplay.presentation.components.snapping.ExperimentalSnapperApi
import com.theveloper.pixelplay.presentation.components.snapping.SnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberSnapperFlingBehavior
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.presentation.components.subcomps.LyricsMoreBottomSheet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.preferences.dataStore

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.utils.MultiLangRomanizer
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import com.theveloper.pixelplay.ui.theme.CookieDisc
import com.theveloper.pixelplay.ui.theme.CookieShape
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import com.theveloper.pixelplay.ui.theme.scenaRise
import com.theveloper.pixelplay.ui.theme.scenaWide

internal data class LyricsSheetColors(
    val container: Color,
    val content: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val accent: Color,
    val accentContent: Color,
    val lyricHighlight: Color,
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val syncButtonContainer: Color,
    val syncButtonContent: Color
)

internal fun lyricsSheetColors(colorScheme: ColorScheme): LyricsSheetColors {
    val container = colorScheme.primaryContainer
    val content = colorScheme.onPrimaryContainer
    val accent = colorScheme.primary
    val accentContent = colorScheme.onPrimary

    return LyricsSheetColors(
        container = container,
        content = content,
        controlContainer = colorScheme.surfaceContainerLowest,
        controlContent = colorScheme.onSurface,
        accent = accent,
        accentContent = accentContent,
        lyricHighlight = preferredContrastColor(
            background = container,
            preferred = accent,
            fallback = content
        ),
        playPauseContainer = colorScheme.tertiaryFixedDim,
        playPauseContent = colorScheme.onTertiaryFixed,
        syncButtonContainer = colorScheme.secondaryFixedDim,
        syncButtonContent = colorScheme.onSecondaryFixed
    )
}

private fun preferredContrastColor(
    background: Color,
    preferred: Color,
    fallback: Color,
    minContrastRatio: Double = 4.5
): Color {
    if (contrastRatio(preferred, background) >= minContrastRatio) return preferred
    if (contrastRatio(fallback, background) >= minContrastRatio) return fallback

    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val argb = encodedSrgbArgb()
    val red = linearizedChannel((argb shr 16) and 0xFF)
    val green = linearizedChannel((argb shr 8) and 0xFF)
    val blue = linearizedChannel(argb and 0xFF)
    return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
}

private fun Color.encodedSrgbArgb(): Int = (value shr 32).toInt()

private fun linearizedChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    lyricsTextStyle: TextStyle,
    colorScheme: ColorScheme,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    onSaveLyricsToFile: (Song, Lyrics, Boolean) -> Unit,
    onTranslateViaAi: () -> Unit,
    onGenerateAiLyrics: () -> Unit = {},
    onAlignLyricsToAudio: (() -> Unit)? = null,
    onWriteLyricsWithCloudAi: (() -> Unit)? = null,
    onSaveLyricsAsMbLrc: ((Song, Lyrics) -> Unit)? = null,
    isKaraokeEnabled: Boolean = false,
    onToggleKaraoke: (() -> Unit)? = null,
    audioAlignmentProgress: Int? = null,
    onCancelAudioAlignment: () -> Unit = {},
    // BottomToggleRow Params

    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Dp = 100.dp,
    highlightZoneFraction: Float = 0.08f, // Reduced from 0.22 for less padding
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null // null = auto-detect from preference
) {
    // ─── Enter / Exit animation state ────────────────────────────────────────
    // Mirrors the player-sheet pattern: a plain Float in state drives graphicsLayer
    // at draw-phase (no recomposition per frame). 0f = fully visible, 1f = dismissed.
    var backProgress by remember { mutableFloatStateOf(1f) }

    // Draw-phase lambda provider — read only inside graphicsLayer so layout is never
    // re-triggered during the gesture (same technique as SheetVisualState).
    val backProgressProvider = rememberUpdatedState(backProgress)

    // Enter animation: slide up from +6 % height + fade in.
    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    // Predictive-back (Android 13+) or plain back on older devices.
    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    val stablePlayerState by stablePlayerStateFlow.collectAsStateWithLifecycle()
    // Scena: the sheet colours are derived from the album scheme *inside* the MaterialTheme wrapper that
    // surrounds the Scaffold below (see `scena`), so they follow the cover art, light or dark.

    val isLoadingLyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSong by remember(stablePlayerState) { derivedStateOf { stablePlayerState.currentSong } }

    val hasTranslatedLyrics = remember(lyrics) {
        // Translated lyrics read same timestamp on the lrc, not possible in plain type lyrics
        lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
    }

    val hasRomanizedLyrics = remember(lyrics) {
        val hasSynced = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true
        val hasPlain = lyrics?.plain?.any { line ->
            MultiLangRomanizer.isScriptThatNeedsRomanization(line)
        } == true
        hasSynced || hasPlain
    }

    val context = LocalContext.current

    // Read lyrics alignment preference internally from DataStore
    val lyricsAlignmentFlow = remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }
    val lyricsAlignment by lyricsAlignmentFlow.collectAsStateWithLifecycle(initialValue = "left")

    // Read lyrics translation preference internally from DataStore
    val showLyricsTranslationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }
    val showLyricsTranslation by showLyricsTranslationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read lyrics romanization preference internally from DataStore
    val showLyricsRomanizationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_romanization")] ?: true }
    }
    val showLyricsRomanization by showLyricsRomanizationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read animated lyrics preference internally from DataStore
    val useAnimatedLyricsFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: false }
    }
    val useAnimatedLyrics by useAnimatedLyricsFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurEnabledFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
    }
    val animatedLyricsBlurEnabled by animatedLyricsBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    val disableBlurAllOverFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("disable_blur_all_over")] ?: false }
    }
    val disableBlurAllOver by disableBlurAllOverFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurStrengthFlow = remember(context) {
        context.dataStore.data.map { it[androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
    }
    val animatedLyricsBlurStrength by animatedLyricsBlurStrengthFlow.collectAsStateWithLifecycle(initialValue = 2.5f)

    // Read keep-screen-on preference from DataStore
    val keepScreenOnFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    // Sync DataStore → local state
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }
    val coroutineScope = rememberCoroutineScope()

    // Apply FLAG_KEEP_SCREEN_ON via the window when enabled
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedAutoscrollSpec = autoscrollAnimationSpec ?: if (useAnimatedLyrics) {
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        )
    } else {
        tween(durationMillis = 450, easing = FastOutSlowInEasing)
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    // Flag to prevent dialog from showing briefly after reset
    var wasResetTriggered by remember { mutableStateOf(false) }
    // Save lyrics dialog state
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    LaunchedEffect(showSyncControls, lyricsSyncOffset) {
        if (showSyncControls) {
            delay(5_000L)
            showSyncControls = false
        }
    }
    var previewSeekPositionMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf(
            when {
                !lyrics?.synced.isNullOrEmpty() -> true
                !lyrics?.plain.isNullOrEmpty() -> false
                else -> null
            }
        )
    }

    val hasSyncedLyrics = remember(lyrics) {
        !lyrics?.synced.isNullOrEmpty()
    }

    // Immersive Mode State
    var immersiveMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Swipe Gesture State
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var hasTriggeredAction by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val overlayTranslation = remember { Animatable(0f) }
    val swipeProgress = remember { Animatable(0f) }

    // Reset keep-screen-on when the physical screen goes off (power button / OEM sleep gesture).
    // ACTION_SCREEN_OFF is a guaranteed platform broadcast; no OEM can suppress it.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Auto-hide controls logic
    LaunchedEffect(immersiveLyricsEnabled, lastInteractionTime, showSyncedLyrics, isImmersiveTemporarilyDisabled) {
        if (immersiveLyricsEnabled && showSyncedLyrics == true && !isImmersiveTemporarilyDisabled) {
            delay(immersiveLyricsTimeout)
            immersiveMode = true
        } else {
            immersiveMode = false
        }
    }

    // Font Scaling
    val fontScale by animateFloatAsState(
        targetValue = if (immersiveMode) 1.4f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fontScale"
    )
    
    val scaledTextStyle = lyricsTextStyle.copy(
        fontSize = lyricsTextStyle.fontSize * fontScale,
        lineHeight = lyricsTextStyle.lineHeight * fontScale
    )

    fun resetImmersiveTimer() {
        lastInteractionTime = System.currentTimeMillis()
        immersiveMode = false
    }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            // Only show dialog if reset was not just triggered
            if (!wasResetTriggered) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false // Reset the flag when lyrics are loaded
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = onSearchLyrics,
                onPickResult = onPickResult,
                onManualSearch = onManualSearch,
                onDismiss = {
                    showFetchLyricsDialog = false
                    onDismissLyricsSearch()
                    if (lyrics == null && !isLoadingLyrics) {
                        onBackClick()
                    }
                },
                onImport = onImportLyrics,
                onGenerateAiLyrics = onGenerateAiLyrics
            )
        }
    }


    // Save Lyrics Dialog
    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.lyrics_save_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.lyrics_save_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_synced))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_plain))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }



    // Scena: re-theme the sheet with the album scheme so that `MaterialTheme.scena` (the drenched ground,
    // ink, ink2 and the single accent) is derived from the cover of the current track, not from the
    // wallpaper scheme of the app. The dialogs above and the "more" sheet below keep their own wrapper.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
    val scena = MaterialTheme.scena
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val containerColor = scena.bg              // ground of the whole sheet
    val contentColor = scena.ink
    val backgroundColor = scena.bg2            // raised surfaces: header pill, controls, seek bar
    val onBackgroundColor = scena.ink
    val accentColor = scena.acc                // the single accent
    val onAccentColor = scena.onAcc
    val playPauseColor = scena.acc
    val onPlayPauseColor = scena.onAcc
    // Words that light up use the accent when it reads well on the ground (large text: 3:1), ink otherwise.
    // Lit words: the cover's accent when it reads on the ground, otherwise pure white or near
    // black (never the grey ink, which made the whole screen monochrome).
    // Glowing lyrics: lit words are white with a white glow on dark grounds (near black on light).
    val lyricHighlightColor = remember(scena) {
        if (scena.bg.luminance() < 0.5f) Color.White else Color.Black.copy(alpha = 0.92f)
    }
    val hasWordTimings = remember(lyrics) { lyrics?.synced?.any { it.words != null } == true }
    // Lines without real word timings get estimated ones (shared in proportion to word length),
    // so every song lights word by word; the chip says when the timings are an estimate.
    val displaySyncedLines = remember(lyrics) {
        lyrics?.synced
            ?.let { com.theveloper.pixelplay.utils.WordTimingEstimator.estimate(it) }
            ?.let(::withInstrumentalBreaks)
    }
    val hasEstimatedWordTimings = remember(lyrics) {
        lyrics?.synced?.let { com.theveloper.pixelplay.utils.WordTimingEstimator.canEstimate(it) } == true
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // ─── Enter / Predictive-back exit transformation ──────────────────
            // Read backProgressProvider inside graphicsLayer (draw-phase) — no layout
            // pass is triggered per gesture frame, same pattern as SheetVisualState.
            // 0f = fully visible, 1f = fully dismissed.
            // Effect: scale down to 92 % + slide down 8 % of height + fade to 72 % alpha.
            // Matches Android predictive back spec for full-screen destinations and
            // mirrors the scale+alpha treatment used across the rest of the app.
            .graphicsLayer {
                val p = backProgressProvider.value
                val scale = lerp(1f, 0.92f, p)
                scaleX = scale
                scaleY = scale
                translationY = lerp(0f, size.height * 0.08f, p)
            }
            .clip(Scena.TileShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isSwipeActive = true
                        hasTriggeredAction = false
                        dragOffset = 0f
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            swipeProgress.snapTo(0f)
                        }
                    },
                    onDragEnd = {
                        isSwipeActive = false
                        val committed = abs(dragOffset) > swipeThresholdPx && !hasTriggeredAction 
                        
                        if (committed) {
                            if (dragOffset > 0) onPrev() else onNext()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        coroutineScope.launch {
                             swipeProgress.animateTo(0f, tween(200))
                             dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isSwipeActive = false
                        dragOffset = 0f
                        coroutineScope.launch {
                            swipeProgress.animateTo(0f, tween(200))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        resetImmersiveTimer()
                        
                        if (!hasTriggeredAction) {
                            dragOffset += dragAmount.x
                            val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            
                            coroutineScope.launch {
                                swipeProgress.snapTo(progress)
                            }
                        }
                    }
                )
            },
        containerColor = containerColor,
        contentColor = contentColor,
        // Removed TopBar and FAB
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Stage: the cover, blurred and dimmed, fills the screen behind the glowing lines.
            LyricsStageBackdrop(artUri = currentSong?.albumArtUriString, ground = containerColor)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    resetImmersiveTimer()
                }
        ) {
            val initialSyncedLineIndex = remember(displaySyncedLines, playbackPositionFlow, lyricsSyncOffset) {
                resolveCurrentLineIndex(
                    lines = displaySyncedLines.orEmpty(),
                    position = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                ).coerceAtLeast(0)
            }
            val syncedListState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialSyncedLineIndex
            )
            val staticListState = rememberLazyListState()

            // Lyrics Content (Weight 1)
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                // Track Info Header (Fixed at top)
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) +
                         scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(2f)
                        .scenaRise(0)
                        .wrapContentWidth(),
                    label = "headerAnimation"
                ) { song ->
                    LyricsTrackInfo(
                        song = song,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                            )
                            .background(
                                color = backgroundColor,
                                shape = Scena.ControlShape
                            )
                            .wrapContentWidth()
                            .animateContentSize(), // Animate width changes
                        backgroundColor = backgroundColor, // Distinct solid background
                        contentColor = onBackgroundColor,
                        isPlaying = isPlaying
                    )
                }

                // Scena: says whether the lyrics are timed per word or per line. Derived from the data.
                if (showSyncedLyrics == true && !lyrics?.synced.isNullOrEmpty()) {
                    LyricsTimingChip(
                        perWord = hasWordTimings || hasEstimatedWordTimings,
                        estimated = hasEstimatedWordTimings,
                        modifier = Modifier
                            // Under the header, never over the title.
                            .align(Alignment.TopStart)
                            .zIndex(2f)
                            .padding(top = 104.dp, start = 22.dp)
                            .scenaRise(1)
                    )
                }

                if (audioAlignmentProgress != null) {
                    AudioAlignmentBanner(
                        progress = audioAlignmentProgress,
                        onCancel = onCancelAudioAlignment,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(3f)
                            .padding(horizontal = Scena.Gutter, vertical = 12.dp)
                    )
                }

                when (showSyncedLyrics) {
                    null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 110.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                        ) {
                            item(key = "loader_or_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingLyrics) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.lyrics_loading),
                                                style = scenaWide(20.sp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearWavyProgressIndicator(
                                                trackColor = accentColor.copy(alpha = 0.4f),
                                                color = accentColor,
                                                modifier = Modifier.width(100.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    true -> {
                        lyrics?.synced?.let { synced ->
                            SyncedLyricsList(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // The rows keep 8 dp of their own padding (room for the word pop), so the
                                    // text edge lands at Scena.Gutter + 8 dp.
                                    .padding(horizontal = Scena.Gutter - 4.dp),
                                contentPadding = PaddingValues(top = 160.dp, bottom = 100.dp),
                                lines = displaySyncedLines ?: synced,
                                listState = syncedListState,
                                playbackPositionFlow = playbackPositionFlow,
                                lyricsSyncOffset = lyricsSyncOffset,
                                positionOverrideMs = previewSeekPositionMs,
                                accentColor = lyricHighlightColor,
                                textStyle = scaledTextStyle,
                                onLineClick = { syncedLine -> 
                                    onSeekTo(
                                        resolveSeekPositionMs(
                                            lineTimeMs = syncedLine.time.toLong(),
                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                        )
                                    )
                                    resetImmersiveTimer()
                                },
                                onLineLongClick = currentSong?.let { song ->
                                    { syncedLine: SyncedLine ->
                                        coroutineScope.launch {
                                            LyricShareCard.share(
                                                context = context,
                                                song = song,
                                                lineText = sanitizeLyricLineText(syncedLine.line),
                                                background = scena.bg.toArgb(),
                                                ink = scena.ink.toArgb(),
                                                accent = scena.acc.toArgb()
                                            )
                                        }
                                        Unit
                                    }
                                },
                                highlightZoneFraction = highlightZoneFraction,
                                highlightOffsetDp = highlightOffsetDp,
                                autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                useAnimatedLyrics = useAnimatedLyrics,
                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled && !disableBlurAllOver,
                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                immersiveMode = immersiveMode,
                                lyricsAlignment = lyricsAlignment,
                                showTranslation = showLyricsTranslation,
                                showRomanization = showLyricsRomanization,
                                footer = {
                                    // Credit the provider the lyrics really came from; none when unknown.
                                    val provider = lyricsProviderCredit(lyrics?.source)
                                    if (lyrics?.areFromRemote == true && provider != null) {
                                        item(key = "provider_text") {
                                            ProviderText(
                                                providerText = stringResource(R.string.lyrics_provided_by),
                                                uri = provider.second,
                                                providerName = provider.first,
                                                textAlign = TextAlign.Center,
                                                accentColor = lyricHighlightColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    false -> {
                        lyrics?.plain?.let { plain ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = staticListState,
                                contentPadding = PaddingValues(
                                    start = Scena.Gutter + 8.dp,
                                    end = Scena.Gutter + 8.dp,
                                    top = 160.dp,
                                    bottom = 24.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = plain,
                                    key = { index, line -> "$index-$line" }
                                ) { _, line ->
                                    PlainLyricsLine(
                                        line = line,
                                        style = lyricsTextStyle,
                                        lyricsAlignment = lyricsAlignment,
                                        showTranslation = if (hasTranslatedLyrics) showLyricsTranslation else true,
                                        showRomanization = if (hasRomanizedLyrics) showLyricsRomanization else true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                val plainProvider = lyricsProviderCredit(lyrics?.source)
                                if (lyrics?.areFromRemote == true && plainProvider != null) {
                                    item(key = "provider_text") {
                                        ProviderText(
                                            providerText = stringResource(R.string.lyrics_provided_by),
                                            uri = plainProvider.second,
                                            providerName = plainProvider.first,
                                            textAlign = TextAlign.Center,
                                            accentColor = lyricHighlightColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Top Gradient for fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            // Solid behind the header, then a fade: lines never show through it.
                            brush = Brush.verticalGradient(
                                0f to containerColor,
                                0.62f to containerColor,
                                1f to Color.Transparent
                            )
                        )
                )

                // Bottom Gradient for fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, containerColor)
                            )
                        )
                )
            }

            // Controls Section (Auto-hide in immersive mode)
            AnimatedVisibility(
                visible = !immersiveMode,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerColor)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp, end = 16.dp, start = 16.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // Reset timer on any touch down or move in this area
                                    if (event.changes.any { it.pressed }) {
                                         resetImmersiveTimer()
                                    }
                                }
                            }
                        }
                ) {
                                AnimatedVisibility(
                    visible = showSyncedLyrics == true && lyrics?.synced != null && showSyncControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LyricsSyncControls(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        offsetMillis = lyricsSyncOffset,
                        onOffsetChange = onLyricsSyncOffsetChange,
                        backgroundColor = backgroundColor,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        onBackgroundColor = onBackgroundColor
                    )
                }

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Scena: the play button is a rounded square while paused and morphs, springily, into
                    // a circle while playing.
                    val playPauseCornerRadius by animateDpAsState(
                        targetValue = if (isPlaying) 36.dp else 22.dp,
                        animationSpec = if (animationsEnabled) {
                            spring(
                                dampingRatio = Scena.Springy.dampingRatio,
                                stiffness = Scena.Springy.stiffness
                            )
                        } else {
                            snap()
                        },
                        label = "playPauseShape"
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(playPauseCornerRadius.coerceAtLeast(0.dp)))
                            .background(playPauseColor)
                            .clickable(role = Role.Button) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            label = "playPauseIconAnimation"
                        ) { playing ->
                            if (playing) {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = stringResource(R.string.common_pause),
                                    tint = onPlayPauseColor
                                )
                            } else {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.common_play),
                                    tint = onPlayPauseColor
                                )
                            }
                        }
                    }

                    // Progress Bar
                    LyricsPlaybackSeekBar(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        playbackPositionFlow = playbackPositionFlow,
                        backgroundColor = backgroundColor,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        totalDuration = stablePlayerState.totalDuration,
                        onSeekTo = onSeekTo,
                        onSeekPreviewChange = { previewSeekPositionMs = it },
                        isPlaying = isPlaying
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Floating Toolbar
                LyricsFloatingToolbar(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    showSyncedLyrics = showSyncedLyrics,
                    hasSyncedLyrics = hasSyncedLyrics,
                    onShowSyncedLyricsChange = { showSyncedLyrics = it },
                    onNavigateBack = {
                        onBackClick()
                    },
                    onMoreClick = { showMoreSheet = true },
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    // Scena: the selected segment inverts (ink on ground) instead of taking the accent,
                    // which stays reserved for the play button and the words that light up.
                    accentColor = onBackgroundColor,
                    onAccentColor = backgroundColor,
                    // Pass progress so the back button animates with the gesture (draw-phase).
                    backProgressProvider = { backProgressProvider.value },
                )
             }
            }
        }

        if (showMoreSheet) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                LyricsMoreBottomSheet(
                    onDismissRequest = { showMoreSheet = false },
                    sheetState = moreSheetState,
                    lyrics = lyrics,
                    showSyncedLyrics = showSyncedLyrics == true,
                    isSyncControlsVisible = showSyncControls,
                    onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                    onResetImportedLyrics = {
                        wasResetTriggered = true
                        resetLyricsForCurrentSong()
                    },
                    onTranslateViaAi = onTranslateViaAi,
                    onGenerateSyncedLyricsViaAi = onGenerateAiLyrics,
                    onAlignLyricsToAudio = onAlignLyricsToAudio,
                    onWriteLyricsWithCloudAi = onWriteLyricsWithCloudAi,
                    isKaraokeEnabled = isKaraokeEnabled,
                    onToggleKaraoke = onToggleKaraoke,
                    onSaveLyricsAsMbLrc = onSaveLyricsAsMbLrc?.let { save ->
                        val song = currentSong
                        val currentLyrics = lyrics
                        if (song != null && currentLyrics != null) ({ save(song, currentLyrics) }) else null
                    },
                    onToggleSyncControls = {

                        resetImmersiveTimer()
                        showSyncControls = !showSyncControls
                    },
                    isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                    onSetImmersiveTemporarilyDisabled = {
                        resetImmersiveTimer()
                        onSetImmersiveTemporarilyDisabled(it)
                    },
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { enabled ->
                        keepScreenOn = enabled
                        coroutineScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = enabled
                            }
                        }
                    },
                    lyricsAlignment = lyricsAlignment,
                    onLyricsAlignmentChange = { newAlignment ->
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[stringPreferencesKey("lyrics_alignment")] = newAlignment
                            }
                        }
                    },
                    hasTranslatedLyrics = hasTranslatedLyrics,
                    hasRomanizedLyrics = hasRomanizedLyrics,
                    showTranslation = showLyricsTranslation,
                    showRomanization = showLyricsRomanization,
                    onShowTranslationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                            }
                        }
                    },
                    onShowRomanizationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                            }
                        }
                    },
                    immersiveLyricsEnabled = immersiveLyricsEnabled,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = {
                        resetImmersiveTimer()
                        onShuffleToggle()
                    },
                    onRepeatToggle = {
                        resetImmersiveTimer()
                        onRepeatToggle()
                    },
                    onFavoriteToggle = {
                        resetImmersiveTimer()
                        onFavoriteToggle()
                    },
                )
            }
        }

       // Show Controls Button (Overlay)
       AnimatedVisibility(
            visible = immersiveMode,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FilledIconButton(
                onClick = { resetImmersiveTimer() },
                modifier = Modifier.size(52.dp),
                shape = Scena.ControlShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.lyrics_scena_cd_show_controls)
                )
            }
        }
       
       // Swipe Feedback Overlay
       if (isSwipeActive || swipeProgress.value > 0f) {
           val isNext = dragOffset < 0
           val overlayAlignment = if (isNext) Alignment.CenterEnd else Alignment.CenterStart
           val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious
           
           Box(
               modifier = Modifier
                   .align(overlayAlignment)
                   .size(100.dp) // Base size
                   .padding(
                       start = if(isNext) 0.dp else 6.dp,
                       end = if(isNext) 6.dp else 0.dp
                   )
                   .graphicsLayer {
                        val widthPx = size.width
                        val initialOffset = if(isNext) widthPx else -widthPx
                        translationX = initialOffset * (1f - swipeProgress.value)

                        scaleX = 0.8f + (swipeProgress.value * 0.2f)
                        scaleY = 0.8f + (swipeProgress.value * 0.2f)
                   }
                   .background(
                        color = accentColor, // No alpha modulation
                        shape = RoundedCornerShape(
                            topStart = if(isNext) 360.dp else 8.dp,
                            bottomStart = if(isNext) 360.dp else 8.dp,
                            topEnd = if(isNext) 8.dp else 360.dp,
                            bottomEnd = if(isNext) 8.dp else 360.dp
                        )
                   ),
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = icon,
                   contentDescription = null,
                   modifier = Modifier.size(48.dp),
                   tint = onAccentColor
               )
           }
       }

      }
    }
    } // MaterialTheme (album scheme -> Scena colours)
}

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

/** Where a lyric line sits relative to the playback position. */
enum class LyricLinePhase { Past, Active, Future }

/** Size, in sp, of the lines that are not active. The caller's [TextStyle] scales it (immersive mode). */
private const val ScenaBaseLyricSp = 22f

/** After an auto-scroll, how long to wait for the active line to finish growing before re-centring it. */
private const val ScenaLineSettleMillis = 300L

private fun TextStyle.lyricBaseSizeSp(): Float =
    if (fontSize.isSpecified) fontSize.value else ScenaBaseLyricSp

/** Longest run of Latin letters between spaces: the word that decides how big a line can be before it breaks. */
private fun longestLatinWordChars(text: String): Int =
    text.split(' ', '\u00A0')
        .filter { word -> word.isNotEmpty() && word.all { it.code < 0x250 } }
        .maxOfOrNull { it.length } ?: 0

/**
 * Size of the active line. Expanded heavy letterforms are wide (about 0.7 em per letter), so long lines
 * step down instead of turning into a wall of text, and a long single word is kept from breaking in
 * the middle. [userScale] (the caller's text scale, e.g. immersive mode) is carried halfway so the
 * active line never explodes.
 */
private fun scenaActiveLyricSizeSp(charCount: Int, longestWordChars: Int, userScale: Float): Float {
    val byLength = when {
        charCount <= 22 -> 40f
        charCount <= 36 -> 36f
        charCount <= 56 -> 32f
        else -> 28f
    }
    val byWord = if (longestWordChars > 0) {
        (280f / (0.7f * longestWordChars)).coerceAtLeast(ScenaBaseLyricSp)
    } else {
        byLength
    }
    return minOf(byLength, byWord) * (1f + (userScale - 1f) * 0.5f)
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    onLineLongClick: ((SyncedLine) -> Unit)? = null,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: LazyListScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val animationsEnabled = rememberScenaAnimationsEnabled()
    // Duets (.mblrc singers): the first voice keeps the user's side, the second takes the other
    // one, lines sung together are centred.
    val singers = remember(lines) { duetVoices(lines) }
    val duetAlignment: (SyncedLine) -> String = remember(singers, lyricsAlignment) {
        { line -> duetLineAlignment(singers, line.singer, lyricsAlignment) }
    }

    // The playback position stays a State on purpose: nothing at list level reads its value in
    // composition. Only the derived line indexes (they change once per line) and the words of the
    // active line (through [positionProvider]) depend on it, so a position tick never recomposes
    // the whole list.
    val playbackPositionState = playbackPositionFlow.collectAsStateWithLifecycle()
    val latestSyncOffset by rememberUpdatedState(lyricsSyncOffset)
    val latestPositionOverride by rememberUpdatedState(positionOverrideMs)
    val positionState = remember {
        derivedStateOf {
            latestPositionOverride ?: (playbackPositionState.value + latestSyncOffset).coerceAtLeast(0L)
        }
    }
    val positionProvider: () -> Long = remember(positionState) { { positionState.value } }
    val isPreviewSeeking = positionOverrideMs != null
    val currentLineIndex by remember(lines) {
        derivedStateOf {
            resolveCurrentLineIndex(lines = lines, position = positionState.value)
        }
    }
    // Last line that has started (it can have ended already): tells past lines from future ones.
    val passedLineIndex by remember(lines) {
        derivedStateOf {
            val positionMs = positionState.value
            lines.indexOfLast { it.time.toLong() <= positionMs }
        }
    }
    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }

    // The user reading ahead (or back) must not be yanked to the current line at the next line
    // change: autoscroll waits while they browse, comes back by itself after a few quiet seconds,
    // and right away from the "back to the current line" button, a tap on a line or a seek.
    var isUserBrowsing by remember(lines) { mutableStateOf(false) }
    var browseEndedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserBrowsing = true
                    browseEndedAt = 0L
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> browseEndedAt = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(isUserBrowsing, browseEndedAt) {
        if (!isUserBrowsing || browseEndedAt == 0L) return@LaunchedEffect
        delay(UserBrowseResumeMillis)
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        isUserBrowsing = false
    }
    LaunchedEffect(isPreviewSeeking) {
        if (isPreviewSeeking) isUserBrowsing = false
    }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        // Snapshot of the active line for the effect below: it must not see the index change under its feet.
        val activeLineIndex = currentLineIndex

        LaunchedEffect(activeLineIndex, lines.size, metrics, isPreviewSeeking, isUserBrowsing) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (activeLineIndex !in lines.indices) return@LaunchedEffect
            if (isUserBrowsing && !isPreviewSeeking) return@LaunchedEffect

            if (listState.layoutInfo.totalItemsCount == 0) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            }

            if (!hasAlignedInitialLine) {
                listState.scrollToItem(activeLineIndex)
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = activeLineIndex
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = activeLineIndex
                return@LaunchedEffect
            }

            if (listState.isScrollInProgress && !isPreviewSeeking) return@LaunchedEffect

            // Back from browsing, the current line can be far off screen: jump close, then glide.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == activeLineIndex }) {
                listState.scrollToItem(activeLineIndex)
            }

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(activeLineIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2 || !animationsEnabled) {
                    listState.scrollToItem(activeLineIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLineIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLineIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = activeLineIndex
                return@LaunchedEffect
            }

            // Music Style Dynamic Velocity
            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                val currentLineTime = lines.getOrNull(activeLineIndex)?.time ?: 0
                val nextLineTime = lines.getOrNull(activeLineIndex + 1)?.time ?: (currentLineTime + 1000)
                val timeDiff = (nextLineTime - currentLineTime).coerceIn(250, 2000) // Bound the duration

                tween<Float>(
                    durationMillis = timeDiff,
                    easing = FastOutSlowInEasing
                )
            } else {
                autoscrollAnimationSpec
            }

            if (animationsEnabled) {
                animateToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = activeLineIndex,
                    animationSpec = dynamicAnimationSpec
                )
            } else {
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = activeLineIndex
                )
            }
            lastAutoScrolledLineIndex = activeLineIndex

            // Scena: the active line grows (and the previous one shrinks) while the list scrolls, so the
            // distance measured when the scroll started is a little off. Once the sizes have settled,
            // re-centre the line. Skipped when the user grabbed the list in the meantime.
            delay(if (animationsEnabled) ScenaLineSettleMillis else 64L)
            if (!listState.isScrollInProgress) {
                if (animationsEnabled) {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLineIndex,
                        animationSpec = tween(durationMillis = 240, easing = Scena.EaseEmphasised)
                    )
                } else {
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLineIndex
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
                    val activeIndex = currentLineIndex
                    val distanceFromCurrent = if (activeIndex != -1) abs(activeIndex - index) else 100
                    val phase = when {
                        index == activeIndex -> LyricLinePhase.Active
                        index <= passedLineIndex -> LyricLinePhase.Past
                        else -> LyricLinePhase.Future
                    }

                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            // Calculate translation dynamically inside graphicsLayer to avoid recomposing the row during scroll
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == index }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f

                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f

                            val maxTranslation = 40f
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation
                        }
                    } else Modifier

                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            nextTime = nextTime,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = duetAlignment(line),
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            accentColor = accentColor,
                            style = textStyle,
                            positionProvider = positionProvider,
                            phase = phase,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = {
                                isUserBrowsing = false
                                onLineClick(line)
                            },
                            onLongClick = onLineLongClick?.let { longClick -> { longClick(line) } }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            positionProvider = positionProvider,
                            time = line.time,
                            color = accentColor.copy(alpha = 0.7f),
                            nextTime = nextTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
// 16 dp Spacer removed to allow dynamic padding in LyricLineRow
                }
                footer()
            }

            AnimatedVisibility(
                visible = isUserBrowsing && currentLineIndex >= 0,
                enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() / 2)
            ) {
                ResumeLyricsSyncButton(onClick = { isUserBrowsing = false })
            }

//            if (metrics.zoneHeight > 0.dp) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .offset(y = metrics.topPadding)
//                        .height(metrics.zoneHeight)
//                        .align(Alignment.TopCenter)
//                        .clip(RoundedCornerShape(18.dp))
//                        .background(accentColor.copy(alpha = 0.12f))
//                        .testTag("synced_highlight_zone")
//                )
//            }
        }
    }
}

/**
 * One synced line.
 *
 * Scena type: the active line is large, expanded and heavy ([scenaDisplay]); the others are
 * [scenaWide] at reduced alpha, past lines more present than future ones. Lines with word timings
 * light up word by word (dim ink2 to the accent, with a small springy pop on the word that just
 * lit); lines without word timings light up all at once when they become active. Word timings are
 * never invented.
 *
 * [positionProvider] lets the caller hand over the playback position as a lazily-read State, and
 * [phase] the already-resolved relation of the line to the playback position; both keep the row from
 * recomposing on every position tick. Without them the row falls back to [position].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineRow(
    line: SyncedLine,
    nextTime: Int,
    position: Long = 0L,
    distanceFromCurrent: Int = 100,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    accentColor: Color,
    style: TextStyle,
    positionProvider: (() -> Long)? = null,
    phase: LyricLinePhase? = null,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val scena = MaterialTheme.scena
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val seekLabel = stringResource(R.string.lyrics_scena_cd_seek_line)
    val shareLabel = stringResource(R.string.lyrics_share_line)

    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val sanitizedWords = remember(line.words) {
        line.words?.let(::sanitizeSyncedWords)
    }
    val sanitizedWordClusters = remember(sanitizedWords) {
        sanitizedWords?.takeIf { it.isNotEmpty() }?.let(::clusterSyncedWords)
    }
    val lineEndTime = remember(line, nextTime) {
        resolveLineEndTimeMs(line, nextTime)
    }
    val readPosition: () -> Long = positionProvider ?: { position }
    val resolvedPhase = phase ?: when {
        position < line.time.toLong() -> LyricLinePhase.Future
        position < lineEndTime -> LyricLinePhase.Active
        else -> LyricLinePhase.Past
    }
    val isCurrentLine = resolvedPhase == LyricLinePhase.Active

    // ─── Type: big, expanded and heavy when active; wide semibold otherwise ───────────────────────
    val baseSp = style.lyricBaseSizeSp()
    val activeSp = remember(sanitizedLine, baseSp) {
        scenaActiveLyricSizeSp(
            charCount = sanitizedLine.length,
            longestWordChars = longestLatinWordChars(sanitizedLine),
            userScale = baseSp / ScenaBaseLyricSp
        )
    }
    val growth by animateFloatAsState(
        targetValue = if (isCurrentLine) 1f else 0f,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 420, easing = Scena.EaseEmphasised)
        } else {
            snap()
        },
        label = "lineGrowth"
    )
    val fontSp = lerp(baseSp, activeSp, growth)
    val lineTextStyle = if (isCurrentLine) {
        scenaDisplay(fontSp.sp).copy(lineHeight = (fontSp * 1.15f).sp)
    } else {
        scenaWide(fontSp.sp)
    }

    // ─── Colour: ink2 (dim) until the line, or the word, is reached; then the single accent ──────
    val restingAlpha = when (resolvedPhase) {
        LyricLinePhase.Active -> 0.48f
        LyricLinePhase.Past -> if (useAnimatedLyrics) 0.8f else 0.70f
        LyricLinePhase.Future -> if (useAnimatedLyrics) 0.8f else 0.45f
    }
    // Every line is the lit colour at a lower opacity: unsung words of the active line at 48%,
    // past lines a bit more present than the coming ones.
    val unlitColor = accentColor.copy(alpha = restingAlpha)
    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unlitColor,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 260, easing = Scena.EaseEmphasised)
        } else {
            snap()
        },
        label = "lineColor"
    )

    // Animated mode (user preference): fisheye scaling + alpha based on distance from current line
    val targetScale = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> if (immersiveMode) 1f else 1.04f; 1 -> 0.95f; else -> 0.85f
    } else 1f
    val targetPadding = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> 28.dp; 1 -> 16.dp; else -> 8.dp
    } else if (isCurrentLine) 16.dp else 10.dp
    val targetAlpha = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> 1.0f; 1 -> 0.6f; else -> 0.3f
    } else 1f

    val floatSpec: AnimationSpec<Float> = when {
        !animationsEnabled -> snap()
        useAnimatedLyrics -> spring(
            stiffness = Spring.StiffnessVeryLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )
        else -> tween(durationMillis = 200)
    }
    val dpSpec: AnimationSpec<Dp> = when {
        !animationsEnabled -> snap()
        useAnimatedLyrics -> spring(
            stiffness = Spring.StiffnessVeryLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )
        else -> tween(durationMillis = 420, easing = Scena.EaseEmphasised)
    }
    val alphaSpec: AnimationSpec<Float> = when {
        !animationsEnabled -> snap()
        useAnimatedLyrics -> spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        )
        else -> tween(durationMillis = 200)
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = floatSpec,
        label = "lineScale"
    )
    val verticalPadding by animateDpAsState(
        targetValue = targetPadding,
        animationSpec = dpSpec,
        label = "linePadding"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = alphaSpec,
        label = "lineAlpha"
    )

    // Blur Effect
    val targetBlur = if (useAnimatedLyrics && animatedLyricsBlurEnabled && distanceFromCurrent > 0) {
        (distanceFromCurrent * animatedLyricsBlurStrength).coerceAtMost(10f).dp
    } else 0.dp

    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = if (useAnimatedLyrics) 400 else 200)
        } else {
            snap()
        },
        label = "lineBlur"
    )

    // Animated mode: apply graphicsLayer for scale/alpha transforms
    val baseModifier = if (useAnimatedLyrics && !immersiveMode) {
        when (lyricsAlignment) {
            "center" -> modifier.padding(horizontal = 36.dp)
            "right" -> modifier.padding(start = 36.dp)
            else -> modifier.padding(end = 36.dp)
        }
    } else {
        modifier
    }
    val animatedModifier = if (useAnimatedLyrics) {
        baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (lyricsAlignment) {
                        "center" -> 0.5f
                        "right" -> if (isRtl) 0f else 1f
                        else -> if (isRtl) 1f else 0f
                    },
                    pivotFractionY = 0.5f
                )
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
    } else baseModifier

    // Roman or Translate Logic
    val translationText = line.translation
    val romanizationText = line.romanization

    val secondaryStyle = remember(style, baseSp) {
        style.copy(
            fontSize = (baseSp * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }

    val romanizationColor = scena.ink2.copy(alpha = if (isCurrentLine) 0.95f else 0.6f)
    val translationColor = scena.ink2.copy(alpha = if (isCurrentLine) 0.8f else 0.45f)

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }

    // 48 dp touch target; the 8 dp of inner padding leave room for the pop of a word that just lit
    // (it is drawn in a graphics layer, inside the clip of the row).
    val rowModifier = animatedModifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .semantics { selected = isCurrentLine }
        .clip(Scena.ControlShape)
        .combinedClickable(
            onClickLabel = seekLabel,
            onLongClickLabel = if (onLongClick != null) shareLabel else null,
            onLongClick = onLongClick,
            onClick = onClick
        )
        .padding(vertical = verticalPadding, horizontal = 8.dp)

    if (sanitizedWordClusters.isNullOrEmpty()) {
        // No word timings (SRT, line-level LRC): the whole line lights at once.
        Column(
            modifier = rowModifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                text = sanitizedLine,
                style = lineTextStyle,
                color = lineColor,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            LyricSecondaryLines(
                romanization = romanizationText.takeIf { showRomanization },
                translation = translationText.takeIf { showTranslation },
                style = secondaryStyle,
                romanizationColor = romanizationColor,
                translationColor = translationColor,
                textAlign = textAlign
            )
        }
    } else {
        // Word timings: every word lights as playback reaches its timestamp, and stays lit for the
        // rest of the line. Read lazily: only the active line looks at the position.
        val highlightedWordIndex by remember(sanitizedWords, line.time, lineEndTime, readPosition) {
            derivedStateOf {
                resolveHighlightedWordIndex(
                    words = requireNotNull(sanitizedWords),
                    positionMs = readPosition(),
                    lineStartTimeMs = line.time.toLong(),
                    lineEndTimeMs = lineEndTime
                )
            }
        }
        val litUpTo = if (isCurrentLine) highlightedWordIndex else -1
        // A space of the active face; syllables of one word are glued together by their cluster Row.
        val wordGap = with(density) { (fontSp * 0.26f).sp.toDp() }

        Column(
            modifier = rowModifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = horizontalAlignment
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (lyricsAlignment) {
                    "center" -> Arrangement.spacedBy(wordGap, Alignment.CenterHorizontally)
                    "right" -> Arrangement.spacedBy(wordGap, Alignment.End)
                    else -> Arrangement.spacedBy(wordGap, Alignment.Start)
                },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                sanitizedWordClusters.forEach { cluster ->
                    key("${line.time}_cluster_${cluster.startIndex}") {
                        Row(verticalAlignment = Alignment.Bottom) {
                            cluster.words.forEachIndexed { clusterOffset, word ->
                                val wordIndex = cluster.startIndex + clusterOffset
                                key("${line.time}_${word.time}_${word.word}_$wordIndex") {
                                    LyricWordSpan(
                                        word = word,
                                        isHighlighted = wordIndex <= litUpTo,
                                        isCurrentWord = wordIndex == litUpTo,
                                        endTimeMs = sanitizedWords?.getOrNull(wordIndex + 1)?.time?.toLong()
                                            ?.coerceAtMost(lineEndTime) ?: lineEndTime,
                                        useAnimatedLyrics = useAnimatedLyrics,
                                        style = lineTextStyle,
                                        glow = isCurrentLine,
                                        highlightedColor = accentColor,
                                        unhighlightedColor = unlitColor,
                                        positionProvider = readPosition
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LyricSecondaryLines(
                romanization = romanizationText.takeIf { showRomanization },
                translation = translationText.takeIf { showTranslation },
                style = secondaryStyle,
                romanizationColor = romanizationColor,
                translationColor = translationColor,
                textAlign = textAlign
            )
        }
    }
}

/** How long autoscroll waits after the user stops scrolling the lyrics. */
private const val UserBrowseResumeMillis = 4_000L

/** Brings the list back to the line being sung after the user scrolled away from it. */
@Composable
private fun ResumeLyricsSyncButton(onClick: () -> Unit) {
    val scena = MaterialTheme.scena
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Scena.ControlShape)
            .background(scena.bg2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.VerticalAlignCenter,
            contentDescription = null,
            tint = scena.ink,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.lyrics_resume_sync),
            style = MaterialTheme.typography.labelLarge,
            color = scena.ink
        )
    }
}

/** Romanization and translation under a synced line (null or blank = not shown). */
@Composable
private fun LyricSecondaryLines(
    romanization: String?,
    translation: String?,
    style: TextStyle,
    romanizationColor: Color,
    translationColor: Color,
    textAlign: TextAlign
) {
    if (!romanization.isNullOrBlank()) {
        Text(
            text = romanization,
            style = style,
            color = romanizationColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }

    if (!translation.isNullOrBlank()) {
        Text(
            text = translation,
            style = style,
            color = translationColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

/**
 * One word (or syllable) of a word-timed line. [isHighlighted] means the word has been reached, so it is
 * lit; [isCurrentWord] marks the one that lit last: it gets a springy scale pop (about 1.08x) when it
 * has just lit, restoring the beloved v1.7.0 lyric animation ("le parole si ingrandivano e il font era giusto").
 */
@Composable
fun LyricWordSpan(
    word: SyncedWord,
    isHighlighted: Boolean,
    useAnimatedLyrics: Boolean = false,
    style: TextStyle,
    highlightedColor: Color,
    unhighlightedColor: Color,
    modifier: Modifier = Modifier,
    isCurrentWord: Boolean = isHighlighted,
    positionProvider: (() -> Long)? = null,
    endTimeMs: Long? = null,
    glow: Boolean = false
) {
    val animationsEnabled = rememberScenaAnimationsEnabled()

    val color by animateColorAsState(
        targetValue = if (isHighlighted) highlightedColor else unhighlightedColor,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 220, easing = Scena.EaseEmphasised)
        } else {
            snap()
        },
        label = "wordColor"
    )

    // Pop: up to ~1.08 when the word lights, then settle back to 1. Applied in the graphics layer, so
    // the layout is untouched and nothing recomposes while it runs.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isCurrentWord, animationsEnabled) {
        if (!animationsEnabled) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        if (isCurrentWord) {
            val sinceWordStart = (positionProvider?.invoke() ?: word.time.toLong()) - word.time.toLong()
            if (sinceWordStart in 0L..600L) {
                pop.animateTo(
                    targetValue = 1.08f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessHigh
                    )
                )
            }
        }
        pop.animateTo(1f, Scena.Springy)
    }

    Text(
        text = word.word,
        style = style,
        color = color,
        // Scale applied at draw phase — zero layout impact per frame.
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        }
    )
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier
) {
    val sanitizedLines = remember(line) { line.split("\n") }
    val primaryText = remember(sanitizedLines) { if (sanitizedLines.isNotEmpty()) sanitizeLyricLineText(sanitizedLines[0]) else "" }

    val isRomanizedScript = remember(primaryText) {
        MultiLangRomanizer.isScriptThatNeedsRomanization(primaryText)
    }

    val translationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
        if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val rest = if (sanitizedLines.size > 2) sanitizedLines.drop(2).joinToString("\n") { sanitizeLyricLineText(it) } else ""
            
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin

            if (isFirstRomanization) rest else sanitizedLines.drop(1).joinToString("\n") { sanitizeLyricLineText(it) }
        } else ""
    }

    val romanizationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
         if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin
            
            if (isFirstRomanization) sanitizeLyricLineText(firstExtra) else ""
        } else ""
    }
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    // Scena: static lyrics are wide semibold ink, translations and romanizations ink2.
    val scena = MaterialTheme.scena
    val baseSp = style.lyricBaseSizeSp()
    val primaryStyle = remember(baseSp) { scenaWide(baseSp.sp) }
    val translationStyle = remember(style, baseSp) {
        style.copy(
            fontSize = (baseSp * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val translationColor = scena.ink2.copy(alpha = 0.8f)

    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (primaryText.isNotBlank()) {
            Text(
                text = primaryText,
                style = primaryStyle,
                color = scena.ink.copy(alpha = 0.9f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )

            if (showRomanization && romanizationText.isNotBlank()) {
                Text(
                    text = romanizationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && translationText.isNotBlank()) {
                Text(
                    text = translationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = if (showRomanization && romanizationText.isNotBlank()) 2.dp else 4.dp)
                )
            }
        }
    }
}

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

/**
 * The two lead voices of a duet, in order of appearance: the two singers with most lines, so a
 * joint label such as "AB" (sung together) never takes a side.
 */
internal fun duetVoices(lines: List<SyncedLine>): List<String> {
    val counts = lines.mapNotNull { it.singer }.groupingBy { it }.eachCount()
    if (counts.size < 2) return emptyList()
    val leads = counts.entries.sortedByDescending { it.value }.take(2).map { it.key }.toSet()
    return lines.mapNotNull { it.singer }.distinct().filter { it in leads }
}

/** Alignment of a duet line; with fewer than two singers it is just [userAlignment]. */
internal fun duetLineAlignment(singers: List<String>, singer: String?, userAlignment: String): String {
    if (singers.size < 2) return userAlignment
    val first = if (userAlignment == "right") "right" else "left"
    val second = if (first == "left") "right" else "left"
    return when (singer) {
        singers[0] -> first
        singers[1] -> second
        else -> "center"
    }
}

/** Display name and home page of an online lyrics provider ([Lyrics.source]), null when unknown. */
internal fun lyricsProviderCredit(source: String?): Pair<String, String>? = when (source) {
    LyricsProvider.LRCLIB -> "LRCLIB" to "https://lrclib.net/"
    LyricsProvider.NETEASE -> "NetEase Cloud Music" to "https://music.163.com/"
    LyricsProvider.AMLL -> "AMLL TTML DB" to "https://github.com/Steve-xmh/amll-ttml-db"
    LyricsProvider.KUGOU -> "KuGou" to "https://www.kugou.com/"
    else -> null
}

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

internal fun resolveLineEndTimeMs(line: SyncedLine, nextLineStartMs: Int): Long {
    // A blank line is a pause (an intro, a solo): it lasts until the singing starts again.
    if (line.line.isBlank() && nextLineStartMs != Int.MAX_VALUE && nextLineStartMs > line.time) {
        return nextLineStartMs.toLong()
    }
    val lastWordStart = line.words?.maxOfOrNull { it.time.toLong() } ?: line.time.toLong()
    val minEnd = lastWordStart + 1L
    val baseEnd = if (nextLineStartMs == Int.MAX_VALUE) {
        line.time.toLong() + 8_000L
    } else {
        val durationToNext = nextLineStartMs.toLong() - line.time.toLong()
        if (durationToNext > 12_000L) {
            line.time.toLong() + 8_000L
        } else {
            nextLineStartMs.toLong()
        }
    }
    return maxOf(baseEnd, minEnd)
}

/** Silence before the first line long enough to show the "pause" dots. */
private const val IntroBreakMs = 5_000

/** Gap between the end of a line and the next one long enough to show the "pause" dots. */
private const val InstrumentalBreakMs = 6_000L

/**
 * Adds a blank line (drawn as animated dots) where nothing is sung for a while: before the first
 * line and in long instrumental gaps, so the screen never looks stuck. Lines already blank count as
 * a pause of their own.
 */
internal fun withInstrumentalBreaks(lines: List<SyncedLine>): List<SyncedLine> {
    if (lines.isEmpty()) return lines
    val result = ArrayList<SyncedLine>(lines.size + 4)
    val first = lines.first()
    if (first.line.isNotBlank() && first.time >= IntroBreakMs) {
        result += SyncedLine(time = 0, line = "")
    }
    lines.forEachIndexed { index, line ->
        result += line
        val next = lines.getOrNull(index + 1) ?: return@forEachIndexed
        if (line.line.isBlank() || next.line.isBlank()) return@forEachIndexed
        val end = resolveLineEndTimeMs(line, next.time)
        if (next.time - end >= InstrumentalBreakMs) {
            result += SyncedLine(time = end.toInt(), line = "")
        }
    }
    return result
}

internal fun resolveHighlightedWordIndex(
    words: List<SyncedWord>,
    positionMs: Long,
    lineStartTimeMs: Long,
    lineEndTimeMs: Long
): Int {
    if (positionMs < lineStartTimeMs || positionMs >= lineEndTimeMs) return -1
    return words.indexOfLast { it.time.toLong() <= positionMs }
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long
): Int {
    if (lines.isEmpty()) return -1

    return lines.withIndex().lastOrNull { (index, line) ->
        val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
        val lineEndTime = resolveLineEndTimeMs(line, nextTime)
        position in line.time.toLong()..<lineEndTime
    }?.index ?: -1
}

/** Progress of the on-device audio word alignment, with a cancel action. */
@Composable
private fun AudioAlignmentBanner(
    progress: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = scena.bg2, shape = Scena.ControlShape)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.lyrics_audio_align_progress, progress),
                style = MaterialTheme.typography.labelLarge,
                color = scena.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = scena.acc,
                trackColor = scena.bg3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.lyrics_audio_align_cancel), color = scena.acc)
        }
    }
}

/** Scena: tells whether the current lyrics are timed per word or per line (derived from the data). */
@Composable
private fun LyricsTimingChip(
    perWord: Boolean,
    modifier: Modifier = Modifier,
    estimated: Boolean = false
) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .background(color = scena.bg2, shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color = if (perWord) scena.acc else scena.ink2, shape = CircleShape)
        )
        Text(
            text = stringResource(
                when {
                    estimated -> R.string.lyrics_scena_timing_estimated
                    perWord -> R.string.lyrics_scena_timing_word
                    else -> R.string.lyrics_scena_timing_line
                }
            ),
            style = scenaWide(12.sp),
            color = scena.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    isPlaying: Boolean
) {
    if (song == null) return

    val scena = MaterialTheme.scena
    val animationsEnabled = rememberScenaAnimationsEnabled()

    // Scena: the cover is the record, a cookie that turns slowly and only while the music plays. A small
    // notch near the rim makes the spin visible. The rotation lives in the graphics layer.
    val currentRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, animationsEnabled) {
        if (isPlaying && animationsEnabled) {
            // Spin forever, 16 s per revolution. The angle is kept when paused, so the record resumes
            // where it stopped.
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(16_000, easing = LinearEasing)
                )
            }
        } else {
            currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = CookieShape,
            contentDescription = stringResource(R.string.lyrics_scena_cd_cover),
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(CookieShape)
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        color = backgroundColor,
                        radius = size.minDimension * 0.045f,
                        center = Offset(size.width / 2f, size.height * 0.13f)
                    )
                },
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false) // Allow shrinking if content is small
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = scenaWide(16.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = scena.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )
    }
}

/** The cover, very blurred and dimmed, behind the lyrics; plain [ground] where blur is not available. */
@Composable
internal fun LyricsStageBackdrop(artUri: String?, ground: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(ground)) {
        if (artUri != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            SmartImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f; scaleX = 1.3f; scaleY = 1.3f }
                    .blur(70.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to ground.copy(alpha = 0.55f),
                        0.5f to ground.copy(alpha = 0.35f),
                        1f to ground.copy(alpha = 0.75f)
                    )
                )
        )
    }
}
