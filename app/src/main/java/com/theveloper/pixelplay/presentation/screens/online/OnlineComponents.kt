package com.theveloper.pixelplay.presentation.screens.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.online.OnlineCollectionItem
import com.theveloper.pixelplay.data.online.OnlineCollectionKind
import com.theveloper.pixelplay.data.online.OnlineItem
import com.theveloper.pixelplay.data.online.OnlineMusicRepository
import com.theveloper.pixelplay.data.online.OnlineShelf
import com.theveloper.pixelplay.data.online.OnlineShelfStyle
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.Fluido
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaWide

/** What the Online screens can do with an item; the screen wires it to the player. */
class OnlineActions(
    val playSongs: (songs: List<OnlineSongItem>, index: Int, queueName: String) -> Unit,
    val playNext: (OnlineSongItem) -> Unit,
    val addToQueue: (OnlineSongItem) -> Unit,
    val open: (OnlineCollectionItem) -> Unit,
    /** Saves the song's own YouTube audio as .m4a. */
    val save: (OnlineSongItem) -> Unit = {},
)

/** Localized title of a shelf built by the app (markers from [OnlineMusicRepository]). */
@Composable
fun shelfTitle(shelf: OnlineShelf): Pair<String?, String> = when {
    shelf.title == OnlineMusicRepository.COVERS_MARKER -> null to stringResource(R.string.online_shelf_covers)
    shelf.title == OnlineMusicRepository.ALBUMS_MARKER -> null to stringResource(R.string.online_shelf_albums)
    shelf.subtitle == OnlineMusicRepository.MORE_FROM_MARKER -> stringResource(R.string.online_shelf_more_from) to shelf.title
    else -> shelf.subtitle to shelf.title
}

/** Section header: an optional small overline, the title, and "Play all" for song shelves. */
@Composable
fun OnlineSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onPlayAll: (() -> Unit)? = null,
) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Scena.Gutter),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            overline?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scena.ink2,
                    maxLines = 1
                )
            }
            Text(
                text = title,
                style = scenaWide(24.sp),
                color = scena.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onPlayAll != null) {
            Spacer(Modifier.width(12.dp))
            FluidPillButton(
                text = stringResource(R.string.online_play_all),
                onClick = onPlayAll,
                outlined = true
            )
        }
    }
}

/**
 * Fluido pill button: changes radius with the spatial spring while pressed, as Material 3
 * Expressive buttons do.
 */
@Composable
fun FluidPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) 12.dp else 24.dp, Fluido.springDefault(), label = "pillRadius")
    val shape = RoundedCornerShape(radius)
    val c = MaterialTheme.colorScheme
    val container = if (outlined) Color.Transparent else c.primary
    val content = if (outlined) c.onSurface else c.onPrimary
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(container, shape)
            .then(if (outlined) Modifier.border(1.dp, c.outline, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** Fluido search bar: a pill on surface-container that lifts to -high while focused. */
@Composable
fun OnlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        if (focused || query.isNotEmpty()) c.surfaceContainerHigh else c.surfaceContainer,
        Fluido.colorSpec(),
        label = "searchContainer"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Scena.Gutter)
            .height(56.dp)
            .clip(Scena.ControlShape)
            .background(container)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.online_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.online_search_clear), tint = c.onSurfaceVariant)
            }
        }
    }
}

/** Mood chips: tapping one searches it. Selected chips use secondary-container, as Google apps do. */
@Composable
fun OnlineMoodChips(
    moods: List<Pair<String, String>>,
    selectedQuery: String,
    onMood: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Scena.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(moods, key = { it.second }) { (label, query) ->
            val selected = query == selectedQuery
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val radius by animateDpAsState(if (selected || pressed) 8.dp else 16.dp, Fluido.springDefault(), label = "chipRadius")
            val shape = RoundedCornerShape(radius)
            val bg by animateColorAsState(if (selected) c.secondaryContainer else c.surfaceContainer, Fluido.colorSpec(), label = "chipBg")
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(shape)
                    .background(bg, shape)
                    .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button) { onMood(query) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) c.onSecondaryContainer else c.onSurface
                )
            }
        }
    }
}

/** "Quick picks": pages of 3×3 square tiles with the title over the art, and page dots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPicksGrid(
    songs: List<OnlineSongItem>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = remember(songs) { songs.chunked(9) }
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState { pages.size }
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = Scena.Gutter),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageSongs = pages[page]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pageSongs.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIndex, song ->
                            val index = page * 9 + rowIndex * 3 + colIndex
                            QuickPickTile(
                                song = song,
                                onClick = { onPlay(index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { i ->
                    val active = pagerState.currentPage == i
                    val width by animateDpAsState(if (active) 20.dp else 8.dp, Fluido.springBouncy(), label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickTile(song: OnlineSongItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Fluido.springDefault(), label = "tileScale")
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Scena.ArtShape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
    ) {
        SmartImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            targetSize = Size(360, 360)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)))
        )
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/** A shelf: song lists scroll sideways in columns of four rows, other items as cards. */
@Composable
fun OnlineShelfSection(
    shelf: OnlineShelf,
    actions: OnlineActions,
    modifier: Modifier = Modifier,
) {
    val (overline, title) = shelfTitle(shelf)
    val songs = shelf.songs
    Column(modifier.fillMaxWidth()) {
        OnlineSectionHeader(
            title = title,
            overline = overline,
            onPlayAll = if (songs.size >= 2) ({ actions.playSongs(songs, 0, title) }) else null
        )
        Spacer(Modifier.height(12.dp))
        if (shelf.style == OnlineShelfStyle.SONG_LIST && songs.isNotEmpty()) {
            SongColumns(songs = songs, onPlay = { actions.playSongs(songs, it, title) }, actions = actions)
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Scena.Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shelf.items, key = { it.key }) { item ->
                    OnlineCard(item = item, onClick = {
                        when (item) {
                            is OnlineSongItem -> actions.playSongs(songs, songs.indexOf(item).coerceAtLeast(0), title)
                            is OnlineCollectionItem -> actions.open(item)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun SongColumns(songs: List<OnlineSongItem>, onPlay: (Int) -> Unit, actions: OnlineActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth: Dp = maxWidth * 0.86f
        val columns = remember(songs) { songs.withIndex().chunked(4) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Scena.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(columns, key = { it.first().value.videoId }) { column ->
                Column(Modifier.width(columnWidth)) {
                    column.forEach { (index, song) ->
                        OnlineSongRow(song = song, onClick = { onPlay(index) }, actions = actions)
                    }
                }
            }
        }
    }
}

/** A song row: small cover, title and artist, and a menu (play next, add to queue). */
@Composable
fun OnlineSongRow(
    song: OnlineSongItem,
    onClick: () -> Unit,
    actions: OnlineActions,
    modifier: Modifier = Modifier,
    artShape: Shape = RoundedCornerShape(8.dp),
) {
    val scena = MaterialTheme.scena
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            shape = artShape,
            modifier = Modifier.size(56.dp),
            targetSize = Size(168, 168)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = scena.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                song.durationSeconds.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
            ).joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = scena.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.online_more_options), tint = scena.ink2)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.online_play_next)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    onClick = { menu = false; actions.playNext(song) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.online_add_to_queue)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; actions.addToQueue(song) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save_stream_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Download, null) },
                    onClick = { menu = false; actions.save(song) }
                )
            }
        }
    }
}

/** A card: square cover (round for artists) and two lines below. */
@Composable
fun OnlineCard(item: OnlineItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 156.dp) {
    val scena = MaterialTheme.scena
    val isArtist = item is OnlineCollectionItem && item.kind == OnlineCollectionKind.ARTIST
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Fluido.springDefault(), label = "cardScale")
    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
    ) {
        Box {
            SmartImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                shape = if (isArtist) CircleShape else Scena.ArtShape,
                modifier = Modifier
                    .width(width)
                    .aspectRatio(1f),
                targetSize = Size(420, 420)
            )
            if (item is OnlineSongItem) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            color = scena.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scena.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/** Placeholders shaped like the home page while it loads. */
@Composable
fun OnlineHomePlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Scena.Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerBox(Modifier.width(180.dp).height(28.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(4.dp))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).aspectRatio(1f).clip(Scena.ArtShape)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        ShimmerBox(Modifier.width(220.dp).height(28.dp).clip(RoundedCornerShape(8.dp)))
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.width(200.dp).height(16.dp).clip(RoundedCornerShape(4.dp)))
                    ShimmerBox(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
