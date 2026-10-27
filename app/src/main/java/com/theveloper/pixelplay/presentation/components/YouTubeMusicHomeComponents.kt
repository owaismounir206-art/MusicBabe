package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.videos.LocalMusicVideo
import com.theveloper.pixelplay.ui.theme.Fluido
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaWide

/** Category filter chips for the YouTube Music style home feed. */
enum class YouTubeHomeCategory(val labelRes: Int) {
    ALL(R.string.yt_chip_all),
    VIDEOS(R.string.yt_chip_videos),
    SONGS(R.string.yt_chip_songs),
    ALBUMS(R.string.yt_chip_albums),
    ARTISTS(R.string.yt_chip_artists),
    MIXES(R.string.yt_chip_mixes)
}

/**
 * Filter chips row pinned at the top of the Home feed, exactly like YouTube Music.
 */
@Composable
fun YouTubeHomeFilterChips(
    selectedCategory: YouTubeHomeCategory,
    onSelectCategory: (YouTubeHomeCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember { YouTubeHomeCategory.entries }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Scena.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.name }) { category ->
            val selected = category == selectedCategory
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val radius by animateDpAsState(
                if (selected || pressed) 10.dp else 18.dp,
                Fluido.springDefault(),
                label = "chipRadius"
            )
            val shape = RoundedCornerShape(radius)
            val c = MaterialTheme.colorScheme
            val bg by animateColorAsState(
                if (selected) c.secondaryContainer else c.surfaceContainerHigh,
                Fluido.colorSpec(),
                label = "chipBg"
            )
            val contentColor by animateColorAsState(
                if (selected) c.onSecondaryContainer else c.onSurface,
                Fluido.colorSpec(),
                label = "chipContent"
            )

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(shape)
                    .background(bg, shape)
                    .clickable(
                        interactionSource = interaction,
                        indication = androidx.compose.material3.ripple(),
                        role = Role.Button
                    ) { onSelectCategory(category) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Standard YouTube Music section header: overline in uppercase, bold wide title, and optional "See all" action.
 */
@Composable
fun YouTubeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String = stringResource(R.string.yt_see_all),
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = scena.ink2,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = scenaWide(22.sp),
                color = scena.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onActionClick != null) {
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 16:9 widescreen YouTube Music video card with duration pill and center play badge.
 */
@Composable
fun YouTubeMusicVideoCard(
    video: LocalMusicVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 250.dp,
) {
    val scena = MaterialTheme.scena
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Fluido.springDefault(), label = "videoCardScale")

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            SmartImage(
                model = video.thumbnailPath ?: video.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetSize = Size(500, 281)
            )

            // YouTube dark gradient overlay at the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            )

            // YouTube center play badge
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Duration pill in the bottom-right corner
            if (video.durationMs > 0) {
                val totalSec = video.durationMs / 1000
                val durationText = "%d:%02d".format(totalSec / 60, totalSec % 60)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            style = scenaWide(15.sp),
            color = scena.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (video.artist.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = video.artist,
                style = MaterialTheme.typography.bodySmall,
                color = scena.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * YouTube Music Videos section on Home: horizontal carousel of widescreen video cards
 * or an invitation card to import music videos with synced lyrics.
 */
@Composable
fun HomeMusicVideosSection(
    videos: List<LocalMusicVideo>,
    onPlayVideo: (LocalMusicVideo) -> Unit,
    onSeeAll: () -> Unit,
    onImportVideos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        YouTubeSectionHeader(
            title = stringResource(R.string.yt_section_videos),
            overline = stringResource(R.string.yt_section_videos_overline),
            onActionClick = onSeeAll,
            actionLabel = stringResource(R.string.yt_see_all)
        )
        Spacer(Modifier.height(12.dp))

        if (videos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Scena.Gutter),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(videos.take(10), key = { it.uri }) { video ->
                    YouTubeMusicVideoCard(
                        video = video,
                        onClick = { onPlayVideo(video) }
                    )
                }
            }
        } else {
            // YouTube Music style invitation card to import videos
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Scena.Gutter)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClick = onImportVideos)
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.yt_videos_empty_card_title),
                            style = scenaWide(16.sp),
                            color = MaterialTheme.scena.ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.yt_videos_empty_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.scena.ink2
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.yt_videos_import_action),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * YouTube Music Album Card: square 1:1 cover art with rounded corners and subtitle metadata.
 */
@Composable
fun YouTubeAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    val scena = MaterialTheme.scena
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Fluido.springDefault(), label = "albumCardScale")

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetSize = Size(360, 360)
            )

            // Subtle dark overlay on bottom corner for play icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = scenaWide(15.sp),
            color = scena.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        val meta = buildString {
            if (album.artist.isNotBlank()) append(album.artist)
            if (album.year > 0) {
                if (isNotEmpty()) append(" • ")
                append(album.year)
            } else if (album.songCount > 0) {
                if (isNotEmpty()) append(" • ")
                append("${album.songCount} brani")
            }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = scena.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * YouTube Music Albums section on Home: horizontal carousel of square album cards.
 */
@Composable
fun HomeAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        YouTubeSectionHeader(
            title = stringResource(R.string.yt_section_albums),
            overline = stringResource(R.string.yt_section_albums_overline),
            onActionClick = onSeeAll,
            actionLabel = stringResource(R.string.yt_see_all)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Scena.Gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums.take(12), key = { it.id }) { album ->
                YouTubeAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

/**
 * YouTube Music Artist Card: circular avatar with bold name below.
 */
@Composable
fun YouTubeArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 108.dp,
) {
    val scena = MaterialTheme.scena
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Fluido.springDefault(), label = "artistCardScale")

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
        ) {
            SmartImage(
                model = artist.customImageUri ?: artist.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                contentScale = ContentScale.Crop,
                targetSize = Size(280, 280)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = scenaWide(14.sp),
            color = scena.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (artist.songCount > 0) "${artist.songCount} brani" else "Artista",
            style = MaterialTheme.typography.bodySmall,
            color = scena.ink2,
            maxLines = 1
        )
    }
}

/**
 * YouTube Music Featured Artists section on Home: horizontal carousel of circular artist avatars.
 */
@Composable
fun HomeArtistsSection(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        YouTubeSectionHeader(
            title = stringResource(R.string.yt_section_artists),
            overline = stringResource(R.string.yt_section_artists_overline),
            onActionClick = onSeeAll,
            actionLabel = stringResource(R.string.yt_see_all)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Scena.Gutter),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists.take(12), key = { it.id }) { artist ->
                YouTubeArtistCard(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

/**
 * YouTube Music Quick Picks song row with cover, title, artist • duration, and menu.
 */
@Composable
fun YouTubeSongRow(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scena = MaterialTheme.scena
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(52.dp),
            targetSize = Size(156, 156)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scena.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sec = song.duration / 1000
            val meta = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                if (sec > 0) "%d:%02d".format(sec / 60, sec % 60) else null
            ).joinToString(" • ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = scena.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = scena.ink2
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Riproduci come successivo") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    onClick = { menuExpanded = false; onPlayNext(song) }
                )
                DropdownMenuItem(
                    text = { Text("Aggiungi alla coda") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { menuExpanded = false; onAddToQueue(song) }
                )
            }
        }
    }
}

/**
 * YouTube Music "Quick picks" / "Ascolta di nuovo" section on Home:
 * 3-row horizontal columns like YouTube Music's signature Quick Picks shelf.
 */
@Composable
fun HomeQuickPicksSection(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        YouTubeSectionHeader(
            title = stringResource(R.string.yt_section_quick_picks),
            overline = stringResource(R.string.yt_section_quick_picks_overline),
            onActionClick = onPlayAll,
            actionLabel = stringResource(R.string.yt_play_all)
        )
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columnWidth: Dp = maxWidth * 0.86f
            val columns = remember(songs) { songs.take(12).chunked(3) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = Scena.Gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(columns, key = { it.first().id }) { columnSongs ->
                    Column(Modifier.width(columnWidth)) {
                        columnSongs.forEach { song ->
                            YouTubeSongRow(
                                song = song,
                                onClick = { onSongClick(song, songs) },
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue
                            )
                        }
                    }
                }
            }
        }
    }
}
