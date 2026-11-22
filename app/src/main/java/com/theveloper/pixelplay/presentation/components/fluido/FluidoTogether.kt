package com.theveloper.pixelplay.presentation.components.fluido

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.JoinInner
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.recommend.GeneratedMix
import com.theveloper.pixelplay.data.recommend.MixKind
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight

/** The title of a generated mix in the user's language. */
@Composable
fun mixTitle(mix: GeneratedMix): String = when (mix.kind) {
    MixKind.ON_REPEAT -> stringResource(R.string.mix_on_repeat)
    MixKind.ARTIST -> stringResource(R.string.mix_artist, mix.label)
    MixKind.GENRE -> stringResource(R.string.mix_genre, mix.label)
    MixKind.DECADE -> stringResource(R.string.mix_decade, mix.label)
    MixKind.REDISCOVER -> stringResource(R.string.mix_rediscover)
}

/** "Your mixes": one tile per generated mix, in expressive shapes; a tap plays it. */
@Composable
fun FluidoMixesShelf(mixes: List<GeneratedMix>, onPlay: (GeneratedMix, String) -> Unit, modifier: Modifier = Modifier) {
    if (mixes.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidoSectionHeader(stringResource(R.string.mixes_title), modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(mixes, key = { _, mix -> mix.key }) { index, mix ->
                val title = mixTitle(mix)
                FluidoShelfTile(
                    title = title,
                    subtitle = pluralStringResource(R.plurals.mix_songs, mix.songIds.size, mix.songIds.size),
                    cover = mix.covers.firstOrNull(),
                    shapeIndex = index,
                    onClick = { onPlay(mix, title) },
                    size = 136.dp,
                )
            }
        }
    }
}

/** "Together": Jam, Blend and podcasts. */
@Composable
fun FluidoTogetherRow(onJam: () -> Unit, onBlend: () -> Unit, onPodcasts: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidoSectionHeader(stringResource(R.string.together_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TogetherCard(stringResource(R.string.jam_title), stringResource(R.string.together_jam_desc), Icons.Rounded.Groups, 0, onJam)
            TogetherCard(stringResource(R.string.blend_title), stringResource(R.string.together_blend_desc), Icons.Rounded.JoinInner, 1, onBlend)
            TogetherCard(stringResource(R.string.podcasts_title), stringResource(R.string.together_podcasts_desc), Icons.Rounded.Podcasts, 2, onPodcasts)
        }
    }
}

@Composable
private fun RowScope.TogetherCard(title: String, subtitle: String, icon: ImageVector, tone: Int, onClick: () -> Unit) {
    val colors = fluidoTone(tone)
    Surface(
        onClick = onClick,
        shape = FluidoShapes.Card,
        color = fluidoCardColor(),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 132.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.container),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(22.dp)) }
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A full page in the Fluido style: back button, big title, then [content]; room left at the
 * bottom for the navigation bar and the mini player.
 */
@Composable
fun FluidoPage(
    title: String,
    onBack: () -> Unit,
    hasMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp +
        (if (hasMiniPlayer) MiniPlayerHeight else 0.dp)
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(top = top + 4.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "page_bar") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.podcast_back))
                }
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        item(key = "page_title") {
            FluidoPageTitle(title, subtitle = subtitle, modifier = Modifier.padding(horizontal = 16.dp))
        }
        content()
        item(key = "page_end") { Spacer(Modifier.height(8.dp)) }
    }
}
