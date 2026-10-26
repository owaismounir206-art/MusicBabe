package com.theveloper.pixelplay.presentation.screens.search.components

import com.theveloper.pixelplay.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight

data class SpotifyCategory(
    val title: String,
    val query: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

val spotifyDefaultCategories = listOf(
    SpotifyCategory(
        title = "Pop",
        query = "Pop",
        icon = Icons.Rounded.Headphones,
        gradient = listOf(Color(0xFF8C1D40), Color(0xFFE91E63))
    ),
    SpotifyCategory(
        title = "Rock",
        query = "Rock",
        icon = Icons.Rounded.Whatshot,
        gradient = listOf(Color(0xFFB71C1C), Color(0xFFF44336))
    ),
    SpotifyCategory(
        title = "Hip-Hop",
        query = "Hip-Hop",
        icon = Icons.Rounded.Mic,
        gradient = listOf(Color(0xFFE65100), Color(0xFFFF9800))
    ),
    SpotifyCategory(
        title = "Elettronica",
        query = "Electronic",
        icon = Icons.Rounded.GraphicEq,
        gradient = listOf(Color(0xFF006064), Color(0xFF00BCD4))
    ),
    SpotifyCategory(
        title = "Relax & Chill",
        query = "Chill",
        icon = Icons.Rounded.SelfImprovement,
        gradient = listOf(Color(0xFF1B5E20), Color(0xFF4CAF50))
    ),
    SpotifyCategory(
        title = "Allenamento",
        query = "Workout",
        icon = Icons.Rounded.DirectionsRun,
        gradient = listOf(Color(0xFFBF360C), Color(0xFFFF5722))
    ),
    SpotifyCategory(
        title = "Jazz & Soul",
        query = "Jazz",
        icon = Icons.Rounded.Piano,
        gradient = listOf(Color(0xFF311B92), Color(0xFF673AB7))
    ),
    SpotifyCategory(
        title = "Indie",
        query = "Indie",
        icon = Icons.Rounded.Nightlife,
        gradient = listOf(Color(0xFF4A148C), Color(0xFF9C27B0))
    ),
    SpotifyCategory(
        title = "Classica",
        query = "Classical",
        icon = Icons.Rounded.LibraryMusic,
        gradient = listOf(Color(0xFF37474F), Color(0xFF607D8B))
    ),
    SpotifyCategory(
        title = "Top Hits",
        query = "Hits",
        icon = Icons.Rounded.TrendingUp,
        gradient = listOf(Color(0xFF004D40), Color(0xFF00BFA5))
    ),
    SpotifyCategory(
        title = "Notte & Sonno",
        query = "Sleep",
        icon = Icons.Rounded.Bedtime,
        gradient = listOf(Color(0xFF0D47A1), Color(0xFF2196F3))
    ),
    SpotifyCategory(
        title = "Musica Italiana",
        query = "Italian",
        icon = Icons.Rounded.MusicNote,
        gradient = listOf(Color(0xFF2E7D32), Color(0xFF81C784))
    )
)

@Composable
fun SpotifyBrowseCategoriesGrid(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarHeight = getNavigationBarHeight()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + navBarHeight + MiniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.search_browse_all),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
            )
        }

        items(spotifyDefaultCategories, key = { it.title }) { category ->
            SpotifyCategoryCard(
                category = category,
                onClick = { onCategoryClick(category.query) }
            )
        }
    }
}

@Composable
private fun SpotifyCategoryCard(
    category: SpotifyCategory,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = category.gradient
                    )
                )
                .padding(14.dp)
        ) {
            // Category Title
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(end = 36.dp)
            )

            // Styled tilted icon on bottom-right corner (Spotify signature look)
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.BottomEnd)
                    .rotate(18f)
            )
        }
    }
}
