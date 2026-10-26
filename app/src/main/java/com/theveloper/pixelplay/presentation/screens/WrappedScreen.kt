package com.theveloper.pixelplay.presentation.screens

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.DayPart
import com.theveloper.pixelplay.data.stats.ListeningPersonality
import com.theveloper.pixelplay.data.stats.WrappedPeriod
import com.theveloper.pixelplay.data.stats.WrappedStory
import com.theveloper.pixelplay.data.stats.WrappedStoryBuilder
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.WrappedViewModel
import com.theveloper.pixelplay.ui.theme.CookieDisc
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.ScenaColors
import com.theveloper.pixelplay.ui.theme.Sleeve
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import com.theveloper.pixelplay.ui.theme.scenaRise
import com.theveloper.pixelplay.ui.theme.scenaWide
import com.theveloper.pixelplay.utils.WrappedShareCard
import com.theveloper.pixelplay.utils.WrappedShareColors
import com.theveloper.pixelplay.utils.WrappedShareContent
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import timber.log.Timber

/** How long a story stays on screen before the next one, when nobody touches it. */
private const val STORY_DURATION_MS = 7_000

/** The stories of a Wrapped, in order. Those without data for the period are left out. */
private enum class WrappedSlide { INTRO, MINUTES, TOP_SONG, TOP_ARTIST, TOP_ALBUM, CLOCK, PROFILE, SUMMARY }

private fun slidesFor(story: WrappedStory): List<WrappedSlide> = buildList {
    add(WrappedSlide.INTRO)
    add(WrappedSlide.MINUTES)
    if (story.topSongs.isNotEmpty()) add(WrappedSlide.TOP_SONG)
    if (story.topArtists.isNotEmpty()) add(WrappedSlide.TOP_ARTIST)
    if (story.topAlbum != null) add(WrappedSlide.TOP_ALBUM)
    if (story.hourShares.isNotEmpty()) add(WrappedSlide.CLOCK)
    add(WrappedSlide.PROFILE)
    add(WrappedSlide.SUMMARY)
}

/**
 * Roles of one story. Scena drenches every story in a ground taken from the dynamic scheme; two of
 * them are inverted onto the accent, so the sequence has a rhythm without a single fixed colour.
 */
private data class SlideTone(
    val ground: Color,
    val ink: Color,
    val ink2: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color
)

private fun toneFor(slide: WrappedSlide, scena: ScenaColors): SlideTone = when (slide) {
    WrappedSlide.MINUTES, WrappedSlide.PROFILE -> SlideTone(
        ground = scena.acc,
        ink = scena.onAcc,
        ink2 = scena.onAcc.copy(alpha = 0.78f),
        accent = scena.onAcc,
        onAccent = scena.acc,
        track = scena.onAcc.copy(alpha = 0.18f)
    )
    WrappedSlide.TOP_ARTIST, WrappedSlide.CLOCK -> SlideTone(
        ground = scena.bg2,
        ink = scena.ink,
        ink2 = scena.ink2,
        accent = scena.acc,
        onAccent = scena.onAcc,
        track = scena.ink.copy(alpha = 0.12f)
    )
    else -> SlideTone(
        ground = scena.bg,
        ink = scena.ink,
        ink2 = scena.ink2,
        accent = scena.acc,
        onAccent = scena.onAcc,
        track = scena.ink.copy(alpha = 0.12f)
    )
}

@Composable
fun WrappedScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    wrappedViewModel: WrappedViewModel = hiltViewModel()
) {
    val uiState by wrappedViewModel.uiState.collectAsStateWithLifecycle()
    val scena = MaterialTheme.scena
    val story = uiState.story

    when {
        uiState.isLoading -> WrappedMessage(
            tone = toneFor(WrappedSlide.INTRO, scena),
            title = stringResource(R.string.wrapped_loading),
            body = null,
            spinning = true,
            onClose = { navController.popBackStack() }
        )
        story == null || !story.hasListening -> WrappedMessage(
            tone = toneFor(WrappedSlide.INTRO, scena),
            title = stringResource(R.string.wrapped_empty_title),
            body = stringResource(R.string.wrapped_empty_body),
            actionLabel = stringResource(R.string.wrapped_empty_action),
            spinning = false,
            onClose = { navController.popBackStack() }
        )
        else -> WrappedStories(
            story = story,
            availablePeriods = uiState.availablePeriods,
            onSelectPeriod = wrappedViewModel::selectPeriod,
            onClose = { navController.popBackStack() },
            onPlayTopSongs = {
                val songs = wrappedViewModel.topSongsForPlayback()
                if (songs.isNotEmpty()) {
                    playerViewModel.playSongs(
                        songsToPlay = songs,
                        startSong = songs.first(),
                        queueName = it
                    )
                }
            }
        )
    }
}

@Composable
private fun WrappedStories(
    story: WrappedStory,
    availablePeriods: List<WrappedPeriod>,
    onSelectPeriod: (WrappedPeriod) -> Unit,
    onClose: () -> Unit,
    onPlayTopSongs: (queueName: String) -> Unit
) {
    val scena = MaterialTheme.scena
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val animationsEnabled = rememberScenaAnimationsEnabled()
    // Stories that move on by themselves are hostile to screen readers and to "remove animations".
    val autoAdvance = remember(context, animationsEnabled) {
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        animationsEnabled && accessibility?.isTouchExplorationEnabled != true
    }

    val slides = remember(story) { slidesFor(story) }
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val currentSlide = slides.getOrElse(pagerState.currentPage) { WrappedSlide.INTRO }
    val tone = toneFor(currentSlide, scena)

    val progress = remember { Animatable(0f) }
    var progressPage by remember { mutableIntStateOf(-1) }
    var holding by remember { mutableStateOf(false) }
    // Only a finger pauses the story: the effect below must not restart (and cancel itself) when its
    // own page change scrolls the pager.
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val paused = holding || isDragged

    // A new period restarts the story from its first page.
    LaunchedEffect(story.period) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
        progressPage = -1
    }

    LaunchedEffect(pagerState.currentPage, paused, slides.size, autoAdvance) {
        val page = pagerState.currentPage
        if (progressPage != page) {
            progress.snapTo(if (autoAdvance) 0f else 1f)
            progressPage = page
        }
        if (!autoAdvance || paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * STORY_DURATION_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // The last story stays: it holds the actions.
        if (page < slides.lastIndex) scope.launch { pagerState.animateScrollToPage(page + 1) }
    }

    fun go(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, slides.lastIndex)
        if (target != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(target) }
    }

    val nextLabel = stringResource(R.string.wrapped_cd_next_story)
    val previousLabel = stringResource(R.string.wrapped_cd_previous_story)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tone.ground)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { go(1); true },
                    CustomAccessibilityAction(previousLabel) { go(-1); true }
                )
            }
            .pointerInput(isRtl, slides.size) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                    onTap = { offset ->
                        // The start edge goes back, the rest moves on: as in every story viewer.
                        val onStartSide = if (isRtl) offset.x > size.width * 0.7f else offset.x < size.width * 0.3f
                        go(if (onStartSide) -1 else 1)
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { slides[it].name }
        ) { page ->
            val slide = slides[page]
            val slideTone = toneFor(slide, scena)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(slideTone.ground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // Room for the progress bar and the close button above the content.
                    .padding(top = 64.dp)
            ) {
                when (slide) {
                    WrappedSlide.INTRO -> IntroSlide(story, availablePeriods, onSelectPeriod, slideTone)
                    WrappedSlide.MINUTES -> MinutesSlide(story, slideTone)
                    WrappedSlide.TOP_SONG -> TopSongSlide(story, slideTone)
                    WrappedSlide.TOP_ARTIST -> TopArtistSlide(story, slideTone)
                    WrappedSlide.TOP_ALBUM -> TopAlbumSlide(story, slideTone)
                    WrappedSlide.CLOCK -> ClockSlide(story, slideTone)
                    WrappedSlide.PROFILE -> ProfileSlide(story, slideTone)
                    WrappedSlide.SUMMARY -> SummarySlide(
                        story = story,
                        tone = slideTone,
                        onShare = { content ->
                            scope.launch {
                                runCatching {
                                    WrappedShareCard.share(
                                        context = context,
                                        content = content,
                                        colors = WrappedShareColors(
                                            background = scena.bg.toArgb(),
                                            ink = scena.ink.toArgb(),
                                            ink2 = scena.ink2.toArgb(),
                                            accent = scena.acc.toArgb(),
                                            onAccent = scena.onAcc.toArgb()
                                        )
                                    )
                                }.onFailure { Timber.w(it, "Could not share the Wrapped card") }
                            }
                        },
                        onPlayTopSongs = onPlayTopSongs
                    )
                }
            }
        }

        StoryTopBar(
            pageCount = slides.size,
            currentPage = pagerState.currentPage,
            progressProvider = { progress.value },
            tone = tone,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .statusBarsPadding()
        )
    }
}

/** One segment per story: the ones seen are full, the current one fills with time (draw phase only). */
@Composable
private fun StoryTopBar(
    pageCount: Int,
    currentPage: Int,
    progressProvider: () -> Float,
    tone: SlideTone,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = Scena.Gutter, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.wrapped_cd_close),
                tint = tone.ink
            )
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2f)
                            drawRoundRect(color = tone.track, cornerRadius = radius)
                            val fraction = when {
                                index < currentPage -> 1f
                                index == currentPage -> progressProvider().coerceIn(0f, 1f)
                                else -> 0f
                            }
                            if (fraction > 0f) {
                                drawRoundRect(
                                    color = tone.ink,
                                    size = Size(size.width * fraction, size.height),
                                    cornerRadius = radius
                                )
                            }
                        }
                )
            }
        }
    }
}

// ─── Stories ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun StoryColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // At least as tall as the story, so short content can sit in the middle.
                .heightIn(min = maxHeight)
                .padding(horizontal = Scena.Gutter + 8.dp, vertical = 24.dp),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

@Composable
private fun Eyebrow(text: String, tone: SlideTone, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
        color = tone.ink2,
        modifier = modifier.semantics { heading() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroSlide(
    story: WrappedStory,
    availablePeriods: List<WrappedPeriod>,
    onSelectPeriod: (WrappedPeriod) -> Unit,
    tone: SlideTone
) {
    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_brand), tone, Modifier.scenaRise(0))
        Spacer(Modifier.height(16.dp))
        Text(
            text = periodTitle(story),
            style = scenaDisplay(displaySize(periodTitle(story), maxSp = 52f, minSp = 32f)),
            color = tone.ink,
            modifier = Modifier.scenaRise(1)
        )
        periodRangeLabel(story)?.let { range ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = range,
                style = MaterialTheme.typography.bodyLarge.merge(Sleeve.TabularFigures),
                color = tone.ink2,
                modifier = Modifier.scenaRise(2)
            )
        }
        Spacer(Modifier.height(36.dp))
        CoverStack(covers = story.coverUris, tone = tone, modifier = Modifier.scenaRise(3))
        Spacer(Modifier.height(36.dp))
        if (availablePeriods.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.scenaRise(4)
            ) {
                availablePeriods.forEach { period ->
                    PeriodChip(
                        label = stringResource(period.labelRes()),
                        selected = period == story.period,
                        tone = tone,
                        onClick = { onSelectPeriod(period) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = stringResource(R.string.wrapped_intro_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = tone.ink2,
            modifier = Modifier.scenaRise(5)
        )
    }
}

/** Covers fanned out like sleeves on a shelf; without any cover, the Scena record. */
@Composable
private fun CoverStack(covers: List<String>, tone: SlideTone, modifier: Modifier = Modifier) {
    val coverSize = 150.dp
    val step = 48.dp
    if (covers.isEmpty()) {
        CookieDisc(
            modifier = modifier.size(coverSize),
            color = tone.accent,
            spinning = true
        )
        return
    }
    Box(modifier = modifier.height(coverSize).width(coverSize + step * (covers.size - 1))) {
        covers.forEachIndexed { index, uri ->
            SmartImage(
                model = uri,
                contentDescription = null,
                shape = Scena.CoverShape,
                targetSize = coil.size.Size(400, 400),
                modifier = Modifier
                    .zIndex((covers.size - index).toFloat())
                    .offset(x = step * index)
                    .size(coverSize)
                    .clip(Scena.CoverShape)
                    .border(3.dp, tone.ground, Scena.CoverShape)
            )
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, tone: SlideTone, onClick: () -> Unit) {
    val shape = Scena.ControlShape
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(tone.ink, shape)
                else Modifier.border(1.dp, tone.ink2.copy(alpha = 0.5f), shape)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tone.ground else tone.ink,
            maxLines = 1
        )
    }
}

@Composable
private fun MinutesSlide(story: WrappedStory, tone: SlideTone) {
    val minutes = rememberCountUp(story.totalMinutes)
    val formatter = remember { NumberFormat.getIntegerInstance() }
    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_minutes_eyebrow), tone)
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatter.format(minutes),
            // Sized on the final figure, so the number does not shrink while it counts up.
            style = scenaDisplay(displaySize(formatter.format(story.totalMinutes), maxSp = 112f, minSp = 44f))
                .merge(Sleeve.TabularFigures),
            color = tone.ink,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = stringResource(R.string.wrapped_minutes_unit),
            style = scenaWide(24.sp),
            color = tone.ink
        )
        Spacer(Modifier.height(20.dp))
        val hours = story.totalMinutes / 60
        if (hours > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.wrapped_minutes_hours,
                    hours.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    formatter.format(hours)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = tone.ink2
            )
        }
        if (story.minutesPerActiveDay > 0) {
            Text(
                text = stringResource(R.string.wrapped_minutes_per_day, formatter.format(story.minutesPerActiveDay)),
                style = MaterialTheme.typography.bodyLarge,
                color = tone.ink2
            )
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.wrapped_stat_plays), formatter.format(story.totalPlays), tone, Modifier.weight(1f))
            StatTile(stringResource(R.string.wrapped_stat_songs), formatter.format(story.uniqueSongs), tone, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.wrapped_stat_days), formatter.format(story.activeDays), tone, Modifier.weight(1f))
            StatTile(
                stringResource(R.string.wrapped_stat_streak),
                pluralStringResource(R.plurals.wrapped_days, story.longestStreakDays, story.longestStreakDays),
                tone,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, tone: SlideTone, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(Scena.TileShape)
            .background(tone.track)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = value,
            style = scenaWide(26.sp).merge(Sleeve.TabularFigures),
            color = tone.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tone.ink2,
            maxLines = 2
        )
    }
}

@Composable
private fun TopSongSlide(story: WrappedStory, tone: SlideTone) {
    val top = story.topSongs.first()
    StoryColumn(verticalArrangement = Arrangement.Top) {
        Eyebrow(stringResource(R.string.wrapped_top_song_eyebrow), tone)
        Spacer(Modifier.height(20.dp))
        SmartImage(
            model = top.albumArtUri,
            contentDescription = top.title,
            shape = Scena.CoverShape,
            targetSize = coil.size.Size(720, 720),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .clip(Scena.CoverShape)
                .scenaRise(0)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = top.title,
            style = scenaDisplay(displaySize(top.title, maxSp = 38f, minSp = 24f)),
            color = tone.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = top.artist,
            style = MaterialTheme.typography.titleMedium,
            color = tone.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playsAndMinutes(top.playCount, top.minutes),
            style = MaterialTheme.typography.labelLarge.merge(Sleeve.TabularFigures),
            color = tone.accent
        )
        val runnersUp = story.topSongs.drop(1)
        if (runnersUp.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Eyebrow(stringResource(R.string.wrapped_runner_ups), tone)
            Spacer(Modifier.height(8.dp))
            runnersUp.forEachIndexed { index, song ->
                RankRow(
                    rank = index + 2,
                    title = song.title,
                    subtitle = song.artist,
                    trailing = stringResource(R.string.wrapped_minutes_short, NumberFormat.getIntegerInstance().format(song.minutes)),
                    artUri = song.albumArtUri,
                    tone = tone
                )
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    trailing: String,
    artUri: String?,
    tone: SlideTone,
    sharePercent: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = scenaWide(18.sp).merge(Sleeve.TabularFigures),
            color = tone.ink2,
            modifier = Modifier.width(28.dp)
        )
        if (artUri != null) {
            SmartImage(
                model = artUri,
                contentDescription = null,
                shape = Scena.ArtShape,
                targetSize = coil.size.Size(144, 144),
                modifier = Modifier
                    .size(48.dp)
                    .clip(Scena.ArtShape)
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tone.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = tone.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sharePercent != null) {
                Spacer(Modifier.height(6.dp))
                ShareBar(fraction = sharePercent / 100f, tone = tone, height = 4.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelLarge.merge(Sleeve.TabularFigures),
            color = tone.ink2
        )
    }
}

@Composable
private fun ShareBar(fraction: Float, tone: SlideTone, height: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = tone.track, cornerRadius = radius)
                drawRoundRect(
                    color = tone.accent,
                    size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height),
                    cornerRadius = radius
                )
            }
    )
}

@Composable
private fun TopArtistSlide(story: WrappedStory, tone: SlideTone) {
    val top = story.topArtists.first()
    val formatter = remember { NumberFormat.getIntegerInstance() }
    StoryColumn(verticalArrangement = Arrangement.Top) {
        Eyebrow(stringResource(R.string.wrapped_top_artist_eyebrow), tone)
        Spacer(Modifier.height(24.dp))
        Text(
            text = top.name,
            style = scenaDisplay(displaySize(top.name, maxSp = 56f, minSp = 28f)),
            color = tone.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.scenaRise(0)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wrapped_top_artist_share, top.sharePercent),
            style = scenaWide(20.sp),
            color = tone.ink
        )
        Spacer(Modifier.height(10.dp))
        ShareBar(fraction = top.sharePercent / 100f, tone = tone)
        Spacer(Modifier.height(10.dp))
        Text(
            text = pluralStringResource(R.plurals.wrapped_songs, top.uniqueSongs, top.uniqueSongs) + " · " +
                stringResource(R.string.wrapped_minutes_short, formatter.format(top.minutes)),
            style = MaterialTheme.typography.bodyLarge.merge(Sleeve.TabularFigures),
            color = tone.ink2
        )
        val runnersUp = story.topArtists.drop(1)
        if (runnersUp.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            runnersUp.forEachIndexed { index, artist ->
                RankRow(
                    rank = index + 2,
                    title = artist.name,
                    subtitle = pluralStringResource(R.plurals.wrapped_songs, artist.uniqueSongs, artist.uniqueSongs),
                    trailing = "${artist.sharePercent}%",
                    artUri = null,
                    tone = tone,
                    sharePercent = artist.sharePercent
                )
            }
        }
    }
}

@Composable
private fun TopAlbumSlide(story: WrappedStory, tone: SlideTone) {
    val album = story.topAlbum ?: return
    val formatter = remember { NumberFormat.getIntegerInstance() }
    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_top_album_eyebrow), tone)
        Spacer(Modifier.height(20.dp))
        SmartImage(
            model = album.albumArtUri,
            contentDescription = album.title,
            shape = Scena.CoverShape,
            targetSize = coil.size.Size(720, 720),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(Scena.CoverShape)
                .scenaRise(0)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = album.title,
            style = scenaDisplay(displaySize(album.title, maxSp = 40f, minSp = 24f)),
            color = tone.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.wrapped_minutes_short, formatter.format(album.minutes)) + " · " +
                pluralStringResource(R.plurals.wrapped_songs, album.uniqueSongs, album.uniqueSongs),
            style = MaterialTheme.typography.bodyLarge.merge(Sleeve.TabularFigures),
            color = tone.ink2
        )
    }
}

@Composable
private fun ClockSlide(story: WrappedStory, tone: SlideTone) {
    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_clock_eyebrow), tone)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(story.dayPart.headlineRes()),
            style = scenaDisplay(40.sp),
            color = tone.ink
        )
        Spacer(Modifier.height(32.dp))
        HourChart(shares = story.hourShares, peakHour = story.peakHour, tone = tone)
        Spacer(Modifier.height(28.dp))
        story.peakHour?.let { hour ->
            Text(
                text = stringResource(R.string.wrapped_peak_hour, formatHour(hour)),
                style = scenaWide(20.sp).merge(Sleeve.TabularFigures),
                color = tone.ink
            )
        }
        story.peakDayLabel?.takeIf { it.isNotBlank() }?.let { day ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wrapped_peak_day, day),
                style = MaterialTheme.typography.bodyLarge,
                color = tone.ink2
            )
        }
        if (story.longestSessionMinutes > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.wrapped_longest_session,
                    NumberFormat.getIntegerInstance().format(story.longestSessionMinutes)
                ),
                style = MaterialTheme.typography.bodyLarge.merge(Sleeve.TabularFigures),
                color = tone.ink2
            )
        }
    }
}

/** 24 bars, one per hour; the peak takes the accent, the rest stay quiet. */
@Composable
private fun HourChart(shares: List<Float>, peakHour: Int?, tone: SlideTone) {
    val maxShare = shares.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val grow = remember { Animatable(0f) }
    val animationsEnabled = rememberScenaAnimationsEnabled()
    LaunchedEffect(shares) {
        if (animationsEnabled) grow.animateTo(1f, tween(900, easing = Scena.EaseEmphasised)) else grow.snapTo(1f)
    }
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val gap = 3.dp.toPx()
            val barWidth = (size.width - gap * 23) / 24f
            shares.forEachIndexed { hour, share ->
                val height = (size.height * (share / maxShare) * grow.value).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = if (hour == peakHour) tone.accent else tone.ink2.copy(alpha = 0.35f),
                    topLeft = Offset(hour * (barWidth + gap), size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    text = formatHour(hour),
                    style = MaterialTheme.typography.labelSmall.merge(Sleeve.TabularFigures),
                    color = tone.ink2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSlide(story: WrappedStory, tone: SlideTone) {
    val formatter = remember { NumberFormat.getIntegerInstance() }
    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_profile_eyebrow), tone)
        Spacer(Modifier.height(24.dp))
        CookieDisc(
            modifier = Modifier
                .size(96.dp)
                .scenaRise(0),
            color = tone.ink,
            spinning = true,
            periodMillis = 9_000
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(story.personality.titleRes()),
            style = scenaDisplay(44.sp),
            color = tone.ink,
            modifier = Modifier.scenaRise(1)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = personalityBody(story),
            style = MaterialTheme.typography.bodyLarge,
            color = tone.ink2,
            modifier = Modifier.scenaRise(2)
        )
        Spacer(Modifier.height(28.dp))
        FactRow(
            label = stringResource(R.string.wrapped_fact_plays_per_song),
            value = String.format(Locale.getDefault(), "%.1f", story.averagePlaysPerSong),
            tone = tone
        )
        story.topArtists.firstOrNull()?.let { artist ->
            FactRow(
                label = stringResource(R.string.wrapped_fact_top_artist_share),
                value = "${formatter.format(artist.sharePercent)}%",
                tone = tone
            )
        }
        story.peakHour?.let { hour ->
            FactRow(label = stringResource(R.string.wrapped_fact_peak_hour), value = formatHour(hour), tone = tone)
        }
        if (story.topGenres.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Eyebrow(stringResource(R.string.wrapped_top_genres), tone)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                story.topGenres.forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(Scena.ControlShape)
                            .border(1.dp, tone.ink2.copy(alpha = 0.6f), Scena.ControlShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = tone.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, tone: SlideTone) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tone.ink2,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = scenaWide(20.sp).merge(Sleeve.TabularFigures),
                color = tone.ink
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(Sleeve.Hairline)
                .background(tone.track)
        )
    }
}

@Composable
private fun SummarySlide(
    story: WrappedStory,
    tone: SlideTone,
    onShare: (WrappedShareContent) -> Unit,
    onPlayTopSongs: (queueName: String) -> Unit
) {
    val formatter = remember { NumberFormat.getIntegerInstance() }
    val title = periodTitle(story)
    val topArtist = story.topArtists.firstOrNull()?.name
    val topSong = story.topSongs.firstOrNull()
    val topGenre = story.topGenres.firstOrNull()?.name
    val profile = stringResource(story.personality.titleRes())
    val minutesLabel = stringResource(R.string.wrapped_summary_minutes)
    val artistLabel = stringResource(R.string.wrapped_summary_top_artist)
    val songLabel = stringResource(R.string.wrapped_summary_top_song)
    val genreLabel = stringResource(R.string.wrapped_summary_top_genre)
    val profileLabel = stringResource(R.string.wrapped_summary_profile)
    val rows = buildList<Pair<String, String>> {
        add(minutesLabel to formatter.format(story.totalMinutes))
        topArtist?.let { add(artistLabel to it) }
        topSong?.let { add(songLabel to "${it.title} · ${it.artist}") }
        topGenre?.let { add(genreLabel to it) }
        add(profileLabel to profile)
    }
    val brand = stringResource(R.string.wrapped_brand)
    val chooser = stringResource(R.string.wrapped_share_chooser)
    val queueName = stringResource(R.string.wrapped_queue_name)
    val mostly = topArtist ?: topSong?.title.orEmpty()
    val shareText = if (story.period == WrappedPeriod.ALL) {
        stringResource(R.string.wrapped_share_text_all, formatter.format(story.totalMinutes), mostly)
    } else {
        stringResource(R.string.wrapped_share_text, periodName(story), formatter.format(story.totalMinutes), mostly)
    }

    StoryColumn {
        Eyebrow(stringResource(R.string.wrapped_summary_eyebrow), tone)
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Scena.TileShape)
                .background(MaterialTheme.scena.bg2)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmartImage(
                    model = topSong?.albumArtUri,
                    contentDescription = null,
                    shape = Scena.ArtShape,
                    targetSize = coil.size.Size(240, 240),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(Scena.ArtShape)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = scenaWide(displaySize(title, maxSp = 24f, minSp = 17f, widthDp = 200f)),
                    color = MaterialTheme.scena.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            rows.forEach { (label, value) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.scena.ink2
                )
                Text(
                    text = value,
                    style = scenaWide(18.sp).merge(Sleeve.TabularFigures),
                    color = MaterialTheme.scena.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        // One filled action per area: sharing. Playing the top songs is the quiet alternative.
        WrappedButton(
            label = stringResource(R.string.wrapped_share),
            icon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = tone.onAccent) },
            filled = true,
            tone = tone,
            onClick = {
                onShare(
                    WrappedShareContent(
                        eyebrow = brand,
                        title = title,
                        rows = rows,
                        coverUri = topSong?.albumArtUri,
                        shareText = shareText,
                        chooserTitle = chooser
                    )
                )
            }
        )
        if (story.topSongIds.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            WrappedButton(
                label = stringResource(R.string.wrapped_play_top),
                icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tone.ink) },
                filled = false,
                tone = tone,
                onClick = { onPlayTopSongs(queueName) }
            )
        }
    }
}

@Composable
private fun WrappedButton(
    label: String,
    icon: (@Composable () -> Unit)?,
    filled: Boolean,
    tone: SlideTone,
    onClick: () -> Unit
) {
    val shape = Scena.ControlShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(tone.accent, shape)
                else Modifier.border(1.dp, tone.ink2.copy(alpha = 0.6f), shape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) tone.onAccent else tone.ink
        )
    }
}

/** Loading and empty states: the record, a line, and a way back. */
@Composable
private fun WrappedMessage(
    tone: SlideTone,
    title: String,
    body: String?,
    spinning: Boolean,
    onClose: () -> Unit,
    actionLabel: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tone.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.wrapped_cd_close),
                tint = tone.ink
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Scena.Gutter + 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            CookieDisc(modifier = Modifier.size(120.dp), color = tone.accent, spinning = spinning)
            Spacer(Modifier.height(28.dp))
            Text(text = title, style = scenaDisplay(36.sp), color = tone.ink)
            body?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = it, style = MaterialTheme.typography.bodyLarge, color = tone.ink2)
            }
            actionLabel?.let {
                Spacer(Modifier.height(28.dp))
                WrappedButton(
                    label = it,
                    icon = null,
                    filled = true,
                    tone = tone,
                    onClick = onClose
                )
            }
        }
    }
}

// ─── Text helpers ────────────────────────────────────────────────────────────────────────────

/**
 * Size for expanded display type. Scena letters are wide (about 0.75 em each with the horizontal
 * scale), so the longest word must fit the line or it would break in the middle; longer texts also
 * step down so they do not turn into a wall of letters.
 */
@Composable
private fun displaySize(text: String, maxSp: Float, minSp: Float, widthDp: Float = 300f): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    val longestWord = text.split(' ', '\u00A0').maxOfOrNull { it.length } ?: 0
    val byWord = if (longestWord > 0) widthDp / (0.75f * longestWord * fontScale) else maxSp
    val byLength = when {
        text.length <= 12 -> maxSp
        text.length <= 24 -> maxSp * 0.82f
        else -> maxSp * 0.68f
    }
    return minOf(byWord, byLength).coerceIn(minSp, maxSp).sp
}

@Composable
private fun rememberCountUp(target: Long): Long {
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val value = remember(target) { Animatable(if (animationsEnabled) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        if (animationsEnabled) value.animateTo(target.toFloat(), tween(1_400, easing = Scena.EaseEmphasised))
    }
    return value.value.toLong()
}

private fun WrappedPeriod.labelRes(): Int = when (this) {
    WrappedPeriod.YEAR -> R.string.wrapped_period_year
    WrappedPeriod.MONTH -> R.string.wrapped_period_month
    WrappedPeriod.ALL -> R.string.wrapped_period_all
}

/** "2026", "October": the name of the period, used inside sentences. */
private fun periodName(story: WrappedStory): String {
    val end = Instant.ofEpochMilli(story.periodEndMillis).atZone(ZoneId.systemDefault())
    return when (story.period) {
        WrappedPeriod.YEAR -> end.year.toString()
        WrappedPeriod.MONTH -> end.month.getDisplayName(JavaTextStyle.FULL_STANDALONE, Locale.getDefault())
        WrappedPeriod.ALL -> ""
    }
}

@Composable
private fun periodTitle(story: WrappedStory): String = when (story.period) {
    WrappedPeriod.YEAR -> stringResource(R.string.wrapped_intro_title_year, periodName(story))
    WrappedPeriod.MONTH -> stringResource(R.string.wrapped_intro_title_month, periodName(story))
    WrappedPeriod.ALL -> stringResource(R.string.wrapped_intro_title_all)
}

private fun periodRangeLabel(story: WrappedStory): String? {
    val start = story.periodStartMillis ?: return null
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val to = Instant.ofEpochMilli(story.periodEndMillis).atZone(zone).toLocalDate()
    return "${formatter.format(from)} – ${formatter.format(to)}"
}

private fun formatHour(hour: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(LocalTime.of(hour.coerceIn(0, 23), 0))

@Composable
private fun playsAndMinutes(plays: Int, minutes: Long): String =
    pluralStringResource(R.plurals.wrapped_plays, plays, plays) + " · " +
        stringResource(R.string.wrapped_minutes_short, NumberFormat.getIntegerInstance().format(minutes))

private fun DayPart?.headlineRes(): Int = when (this) {
    DayPart.MORNING -> R.string.wrapped_daypart_morning
    DayPart.AFTERNOON -> R.string.wrapped_daypart_afternoon
    DayPart.EVENING -> R.string.wrapped_daypart_evening
    DayPart.NIGHT, null -> R.string.wrapped_daypart_night
}

private fun ListeningPersonality.titleRes(): Int = when (this) {
    ListeningPersonality.SUPERFAN -> R.string.wrapped_profile_superfan
    ListeningPersonality.NIGHT_OWL -> R.string.wrapped_profile_night_owl
    ListeningPersonality.EARLY_BIRD -> R.string.wrapped_profile_early_bird
    ListeningPersonality.LOYALIST -> R.string.wrapped_profile_loyalist
    ListeningPersonality.EXPLORER -> R.string.wrapped_profile_explorer
    ListeningPersonality.MARATHONER -> R.string.wrapped_profile_marathoner
    ListeningPersonality.ALL_ROUNDER -> R.string.wrapped_profile_all_rounder
}

private fun hoursSharePercent(story: WrappedStory, hours: List<Int>): Int =
    (hours.sumOf { story.hourShares.getOrElse(it) { 0f }.toDouble() } * 100).toInt()

@Composable
private fun personalityBody(story: WrappedStory): String = when (story.personality) {
    ListeningPersonality.SUPERFAN -> {
        val artist = story.topArtists.first()
        stringResource(R.string.wrapped_profile_superfan_body, artist.name, artist.sharePercent)
    }
    ListeningPersonality.NIGHT_OWL -> stringResource(
        R.string.wrapped_profile_night_owl_body,
        hoursSharePercent(story, WrappedStoryBuilder.NIGHT_HOURS)
    )
    ListeningPersonality.EARLY_BIRD -> stringResource(
        R.string.wrapped_profile_early_bird_body,
        hoursSharePercent(story, WrappedStoryBuilder.MORNING_HOURS)
    )
    ListeningPersonality.LOYALIST -> stringResource(
        R.string.wrapped_profile_loyalist_body,
        String.format(Locale.getDefault(), "%.1f", story.averagePlaysPerSong)
    )
    ListeningPersonality.EXPLORER -> stringResource(R.string.wrapped_profile_explorer_body, story.uniqueSongs)
    ListeningPersonality.MARATHONER -> stringResource(
        R.string.wrapped_profile_marathoner_body,
        NumberFormat.getIntegerInstance().format(story.longestSessionMinutes)
    )
    ListeningPersonality.ALL_ROUNDER -> stringResource(R.string.wrapped_profile_all_rounder_body)
}
