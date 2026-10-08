package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private const val TOTAL_WRAPPED_STORIES = 6

@Composable
fun WrappedScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    statsViewModel: StatsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statsState by statsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        statsViewModel.onRangeSelected(StatsTimeRange.ALL)
    }

    val summary = statsState.summary
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TOTAL_WRAPPED_STORIES })

    // Auto-advance timer (5.5 seconds per card, paused on user touch)
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, isPaused) {
        if (!isPaused && pagerState.currentPage < TOTAL_WRAPPED_STORIES - 1) {
            delay(5500)
            if (!isPaused && pagerState.currentPage < TOTAL_WRAPPED_STORIES - 1) {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // Dynamic background gradient according to current page
    val backgroundBrush = remember(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> Brush.verticalGradient(
                colors = listOf(Color(0xFF1E0826), Color(0xFF4A154B), Color(0xFFE91E63))
            )
            1 -> Brush.verticalGradient(
                colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF415A77))
            )
            2 -> Brush.verticalGradient(
                colors = listOf(Color(0xFF240046), Color(0xFF5A189A), Color(0xFF9D4EDD))
            )
            3 -> Brush.verticalGradient(
                colors = listOf(Color(0xFF1F1D36), Color(0xFF3F3351), Color(0xFF864879))
            )
            4 -> Brush.verticalGradient(
                colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
            )
            else -> Brush.verticalGradient(
                colors = listOf(Color(0xFF1A0933), Color(0xFF5B1865), Color(0xFFD63384))
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val screenWidth = size.width
                        scope.launch {
                            if (offset.x < screenWidth * 0.35f) {
                                // Tap left -> previous
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            } else {
                                // Tap right -> next
                                if (pagerState.currentPage < TOTAL_WRAPPED_STORIES - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Story Progress Bars + Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Chiudi Wrapped",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Progress indicators for all 6 stories
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until TOTAL_WRAPPED_STORIES) {
                        val isCurrent = pagerState.currentPage == i
                        val isPast = pagerState.currentPage > i

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isPast -> Color.White
                                        isCurrent -> Color.White.copy(alpha = 0.9f)
                                        else -> Color.White.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> IntroStoryCard()
                    1 -> ListeningTimeStoryCard(summary)
                    2 -> TopSongsStoryCard(summary, playerViewModel)
                    3 -> TopArtistsStoryCard(summary)
                    4 -> MusicVibeStoryCard(summary)
                    5 -> FinalShareStoryCard(summary, onShare = {
                        val shareText = buildString {
                            append("🔊 Turn up the volume babe! Ecco il mio MusicBabe Wrapped ✨\n\n")
                            if (summary != null) {
                                val minutes = summary.totalDurationMs / (1000 * 60)
                                append("⏱️ Ho ascoltato ${minutes} minuti di musica!\n")
                                if (summary.topSongs.isNotEmpty()) {
                                    append("🎵 Canzone #1: ${summary.topSongs.first().title} - ${summary.topSongs.first().artist}\n")
                                }
                                if (summary.topArtists.isNotEmpty()) {
                                    append("👑 Artista preferito: ${summary.topArtists.first().artist}\n")
                                }
                            }
                            append("\nAscoltato con MusicBabe 💖")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Condividi il tuo Wrapped!"))
                    })
                }
            }
        }
    }
}

@Composable
private fun IntroStoryCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.musicbabe_icon),
                contentDescription = "MusicBabe Logo",
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.height(36.dp))

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.25f)
        ) {
            Text(
                text = "MUSICBABE WRAPPED",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Turn up the volume babe!",
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 42.sp
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "È ora di scoprire come hai fatto vibrare le casse quest'anno. Tocca lo schermo per iniziare il viaggio 🔊✨",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ListeningTimeStoryCard(summary: PlaybackStatsRepository.PlaybackStatsSummary?) {
    val totalMinutes = (summary?.totalDurationMs ?: 0L) / (1000 * 60)
    val totalHours = totalMinutes / 60
    val totalPlays = summary?.totalPlayCount ?: 0
    val activeDays = summary?.activeDays ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            tint = Color(0xFF64DFDF),
            modifier = Modifier.size(56.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Hai passato ore indimenticabili con la tua musica",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "$totalMinutes",
            color = Color(0xFF64DFDF),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            fontSize = 72.sp
        )

        Text(
            text = "MINUTI DI ASCOLTO",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontSize = 14.sp
        )

        if (totalHours > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "(Circa $totalHours ore di vibrazioni continue!)",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "$totalPlays", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(text = "Riproduzioni", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "$activeDays", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(text = "Giorni Attivi", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TopSongsStoryCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    playerViewModel: PlayerViewModel
) {
    val topSongs = summary?.topSongs?.take(5) ?: emptyList()
    val topSong = topSongs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Text(
                text = "LA TUA CANZONE #1",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        if (topSong != null) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = topSong.albumArtUri,
                    contentDescription = topSong.title,
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = topSong.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${topSong.artist} • Ascoltata ${topSong.playCount} volte",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Inizia ad ascoltare per sbloccare la tua #1!",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        // Remaining top songs
        if (topSongs.size > 1) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topSongs.drop(1).take(4).forEachIndexed { index, song ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 2}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${song.playCount}x",
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopArtistsStoryCard(summary: PlaybackStatsRepository.PlaybackStatsSummary?) {
    val topArtists = summary?.topArtists?.take(5) ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Text(
                text = "I TUOI ARTISTI PREFERITI",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        if (topArtists.isNotEmpty()) {
            val medals = listOf("🥇", "🥈", "🥉", "4", "5")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topArtists.forEachIndexed { index, artist ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == 0) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = medals.getOrElse(index) { "${index + 1}" },
                                fontSize = if (index < 3) 24.sp else 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist.artist,
                                    color = Color.White,
                                    fontWeight = if (index == 0) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontSize = if (index == 0) 18.sp else 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${artist.uniqueSongs} brani diversi",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "${artist.playCount} ascolti",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Nessun artista ancora registrato.\nFai risuonare la tua musica!",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MusicVibeStoryCard(summary: PlaybackStatsRepository.PlaybackStatsSummary?) {
    val topGenre = summary?.topGenres?.firstOrNull()?.genre ?: "Music Lover"

    val vibeTitle = remember(topGenre) {
        when {
            topGenre.contains("pop", ignoreCase = true) -> "Babe Energy ⚡💖"
            topGenre.contains("rock", ignoreCase = true) -> "Pure Rebel Flame 🔥🎸"
            topGenre.contains("hip", ignoreCase = true) || topGenre.contains("rap", ignoreCase = true) -> "Urban Flow Master 🎤"
            topGenre.contains("electro", ignoreCase = true) || topGenre.contains("dance", ignoreCase = true) -> "Club Euphoria 🕺✨"
            topGenre.contains("jazz", ignoreCase = true) || topGenre.contains("classic", ignoreCase = true) -> "Soulful Connoisseur ☕🎻"
            else -> "Babe Vibe Energy ⚡🎵"
        }
    }

    val vibeDescription = remember(topGenre) {
        "Hai un'energia vibrante e contagiosa. Quando parte il beat, non ti ferma più nessuno: volume al massimo e adrenalina pura!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Color(0xFFFFD166),
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Text(
                text = "IL TUO MUSIC VIBE",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = vibeTitle,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = vibeDescription,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(28.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Genere dominante: $topGenre",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FinalShareStoryCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    onShare: () -> Unit
) {
    val totalMinutes = (summary?.totalDurationMs ?: 0L) / (1000 * 60)
    val topSong = summary?.topSongs?.firstOrNull()?.title ?: "La tua musica"
    val topArtist = summary?.topArtists?.firstOrNull()?.artist ?: "I tuoi artisti"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Poster Card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.musicbabe_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "MusicBabe Wrapped",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Turn up the volume babe!",
                    color = Color(0xFFFFD166),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecapRow(label = "Minuti Totali", value = "$totalMinutes min")
                    RecapRow(label = "Top Brano", value = topSong)
                    RecapRow(label = "Top Artista", value = topArtist)
                    RecapRow(label = "Vibe", value = "Babe Energy ⚡")
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onShare,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1E0826)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Condividi il tuo Wrapped",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun RecapRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
