package com.theveloper.pixelplay.presentation.screens.account

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.online.OnlineCollectionItem
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.data.youtube.YouTubeAccount
import com.theveloper.pixelplay.data.youtube.YouTubeComment
import com.theveloper.pixelplay.data.youtube.YouTubeExtractor
import com.theveloper.pixelplay.data.youtube.YouTubeRating
import com.theveloper.pixelplay.data.youtube.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.YouTubeVideoDetails
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.online.CenteredLoading
import com.theveloper.pixelplay.presentation.screens.online.OnlineCard
import com.theveloper.pixelplay.presentation.screens.online.OnlineSectionHeader
import com.theveloper.pixelplay.presentation.screens.online.OnlineSongRow
import com.theveloper.pixelplay.presentation.screens.online.rememberOnlineActions
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.icu.text.CompactDecimalFormat
import java.util.Locale
import javax.inject.Inject

/** The YouTube video id of a song that streams from YouTube, or null. */
fun Song.youTubeVideoId(): String? =
    contentUriString.takeIf(YouTubeStreamProxy::isYouTubeUri)
        ?.removePrefix(YouTubeStreamProxy.videoUri(""))
        ?.takeIf(YouTubeExtractor::isValidVideoId)

/** Short numbers as YouTube writes them: 8,7 Mln, 371K. */
fun compactCount(value: Long): String =
    CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(value)

@HiltViewModel
class YouTubeAccountViewModel @Inject constructor(
    val account: YouTubeAccount,
    private val extractor: YouTubeExtractor,
) : ViewModel() {
    val isSignedIn = account.isSignedIn
    val accountName = account.accountName

    private val _playlists = MutableStateFlow<List<OnlineCollectionItem>>(emptyList())
    val playlists: StateFlow<List<OnlineCollectionItem>> = _playlists.asStateFlow()
    private val _liked = MutableStateFlow<List<OnlineSongItem>>(emptyList())
    val liked: StateFlow<List<OnlineSongItem>> = _liked.asStateFlow()
    private val _history = MutableStateFlow<List<OnlineSongItem>>(emptyList())
    val history: StateFlow<List<OnlineSongItem>> = _history.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadLibrary() {
        if (!account.isSignedIn.value) return
        viewModelScope.launch {
            _loading.value = true
            _playlists.value = account.playlists()
            _liked.value = account.likedSongs()
            _history.value = account.history()
            _loading.value = false
        }
    }

    private val _subscribed = MutableStateFlow<Set<String>>(emptySet())
    val subscribed: StateFlow<Set<String>> = _subscribed.asStateFlow()

    fun toggleSubscribe(channelId: String) {
        val now = channelId !in _subscribed.value
        viewModelScope.launch {
            if (account.subscribe(channelId, now)) {
                _subscribed.value = if (now) _subscribed.value + channelId else _subscribed.value - channelId
            }
        }
    }

    fun signIn(cookies: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(account.signIn(cookies)) }
    }

    fun signOut() = account.signOut()

    // ─── Player: likes, counts, comments ─────────────────────────────────────────────────

    private val _details = MutableStateFlow<Pair<String, YouTubeVideoDetails>?>(null)
    val details: StateFlow<Pair<String, YouTubeVideoDetails>?> = _details.asStateFlow()
    private val _ratings = MutableStateFlow<Map<String, YouTubeRating>>(emptyMap())
    val ratings: StateFlow<Map<String, YouTubeRating>> = _ratings.asStateFlow()
    private val _comments = MutableStateFlow<List<YouTubeComment>?>(null)
    val comments: StateFlow<List<YouTubeComment>?> = _comments.asStateFlow()

    fun loadDetails(videoId: String) {
        if (_details.value?.first == videoId) return
        _details.value = null
        _comments.value = null
        viewModelScope.launch {
            runCatching { extractor.details(videoId) }.getOrNull()?.let { _details.value = videoId to it }
        }
    }

    fun loadComments(videoId: String) {
        viewModelScope.launch {
            _comments.value = null
            _comments.value = runCatching { extractor.comments(videoId) }.getOrDefault(emptyList())
        }
    }

    /** 👍/👎: sent to YouTube Music when signed in, always remembered locally. */
    fun rate(videoId: String, rating: YouTubeRating, onLike: (Boolean) -> Unit) {
        val current = _ratings.value[videoId] ?: YouTubeRating.NONE
        val next = if (current == rating) YouTubeRating.NONE else rating
        _ratings.value = _ratings.value + (videoId to next)
        onLike(next == YouTubeRating.LIKE)
        if (account.isSignedIn.value) viewModelScope.launch { account.rate(videoId, next) }
    }
}

/** Sign in to YouTube Music on Google's own page; the session cookies are kept when it ends. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(navController: NavHostController, viewModel: YouTubeAccountViewModel = hiltViewModel()) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(top = top).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.online_back))
            }
            Text(stringResource(R.string.account_sign_in_title), style = MaterialTheme.typography.titleLarge)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (done || !url.startsWith("https://music.youtube.com")) return
                            val cookies = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                            if ("SAPISID" !in cookies) return
                            done = true
                            viewModel.signIn(cookies) { ok -> if (ok) navController.popBackStack() }
                        }
                    }
                    loadUrl(YouTubeAccount.SIGN_IN_URL)
                }
            }
        )
    }
}

/** The account: your YouTube Music playlists, liked songs and history, and signing out. */
@Composable
fun YouTubeAccountScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeAccountViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val name by viewModel.accountName.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val actions = rememberOnlineActions(playerViewModel, navController)
    val scena = MaterialTheme.scena
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(signedIn) { viewModel.loadLibrary() }

    LazyColumn(
        Modifier.fillMaxSize().background(scena.bg),
        contentPadding = PaddingValues(top = top + 8.dp, bottom = 160.dp)
    ) {
        item {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.online_back))
                }
            }
            Column(Modifier.padding(horizontal = Scena.Gutter)) {
                Icon(Icons.Rounded.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (signedIn) name?.ifBlank { null } ?: stringResource(R.string.account_signed_in) else stringResource(R.string.account_signed_out),
                    style = scenaDisplay(28.sp), color = scena.ink
                )
                Text(stringResource(R.string.account_explainer), style = MaterialTheme.typography.bodyMedium, color = scena.ink2)
                Spacer(Modifier.height(16.dp))
                if (signedIn) {
                    com.theveloper.pixelplay.presentation.screens.online.FluidPillButton(
                        text = stringResource(R.string.account_sign_out), icon = Icons.AutoMirrored.Rounded.Logout, outlined = true,
                        onClick = { viewModel.signOut() }
                    )
                } else {
                    com.theveloper.pixelplay.presentation.screens.online.FluidPillButton(
                        text = stringResource(R.string.account_sign_in), icon = Icons.Rounded.AccountCircle,
                        onClick = { navController.navigate(Screen.YouTubeSignIn.route) }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (signedIn && loading) item { CenteredLoading() }
        if (playlists.isNotEmpty()) {
            item {
                OnlineSectionHeader(title = stringResource(R.string.account_playlists))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Scena.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    playlists.forEach { OnlineCard(item = it, onClick = { actions.open(it) }) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (liked.isNotEmpty()) {
            item {
                val title = stringResource(R.string.account_liked)
                OnlineSectionHeader(title = title, onPlayAll = { actions.playSongs(liked, 0, title) })
            }
            items(liked.take(30), key = { "l_" + it.videoId }) { song ->
                OnlineSongRow(song, onClick = { actions.playSongs(liked, liked.indexOf(song), "liked") }, actions = actions,
                    modifier = Modifier.padding(horizontal = Scena.Gutter - 4.dp))
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
        if (history.isNotEmpty()) {
            item {
                OnlineSectionHeader(title = stringResource(R.string.account_history))
            }
            items(history.take(30), key = { "h_" + it.videoId }) { song ->
                OnlineSongRow(song, onClick = { actions.playSongs(history, history.indexOf(song), "history") }, actions = actions,
                    modifier = Modifier.padding(horizontal = Scena.Gutter - 4.dp))
            }
        }
    }
}

/**
 * Under the title of a YouTube song, as on YouTube Music: 👍 with the like count, 👎, comments,
 * views, share and save. Likes go to the account when signed in, and to favourites always.
 */
@Composable
fun YouTubeActionsRow(
    song: Song,
    playerViewModel: PlayerViewModel,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeAccountViewModel = hiltViewModel(),
) {
    val videoId = song.youTubeVideoId() ?: return
    LaunchedEffect(videoId) { viewModel.loadDetails(videoId) }
    val details by viewModel.details.collectAsStateWithLifecycle()
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val rating = ratings[videoId] ?: YouTubeRating.NONE
    val info = details?.takeIf { it.first == videoId }?.second
    val context = LocalContext.current
    var showComments by remember { mutableStateOf(false) }
    val likeBase = info?.likeCount
    val likes = likeBase?.let { if (rating == YouTubeRating.LIKE) it + 1 else it }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(LocalContentColor.current.copy(alpha = 0.1f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionPill(
                icon = if (rating == YouTubeRating.LIKE) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                label = likes?.let(::compactCount),
                transparent = true
            ) {
                viewModel.rate(videoId, YouTubeRating.LIKE) { liked ->
                    if (liked != playerViewModel.isCurrentSongFavorite.value) playerViewModel.toggleFavorite()
                }
            }
            ActionPill(
                icon = if (rating == YouTubeRating.DISLIKE) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                label = null,
                transparent = true
            ) { viewModel.rate(videoId, YouTubeRating.DISLIKE) {} }
        }
        val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
        val subs by viewModel.subscribed.collectAsStateWithLifecycle()
        info?.channelId?.takeIf { signedIn }?.let { channel ->
            ActionPill(
                if (channel in subs) Icons.Rounded.Check else Icons.Rounded.Add,
                stringResource(if (channel in subs) R.string.yt_subscribed else R.string.yt_subscribe)
            ) { viewModel.toggleSubscribe(channel) }
        }
        ActionPill(Icons.AutoMirrored.Rounded.Comment, stringResource(R.string.yt_comments)) {
            viewModel.loadComments(videoId); showComments = true
        }
        info?.viewCount?.let { ActionPill(Icons.Rounded.Visibility, compactCount(it)) {} }
        ActionPill(Icons.Rounded.Share, stringResource(R.string.yt_share)) {
            val text = "${song.title} – ${song.artist}\nhttps://music.youtube.com/watch?v=$videoId"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        }
        ActionPill(Icons.Rounded.Download, stringResource(R.string.save_stream_title), onClick = onSave)
    }
    if (showComments) CommentsSheet(viewModel, onDismiss = { showComments = false })
}

@Composable
private fun ActionPill(icon: ImageVector, label: String?, transparent: Boolean = false, onClick: () -> Unit) {
    val content = LocalContentColor.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (transparent) Modifier else Modifier.background(content.copy(alpha = 0.1f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = content)
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(viewModel: YouTubeAccountViewModel, onDismiss: () -> Unit) {
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.yt_comments), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        val list = comments
        when {
            list == null -> CenteredLoading()
            list.isEmpty() -> Text(stringResource(R.string.yt_no_comments), modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list) { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        SmartImage(model = c.avatarUrl, contentDescription = null, shape = CircleShape,
                            modifier = Modifier.size(36.dp), targetSize = Size(108, 108))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${c.author} · ${c.date}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Rounded.ThumbUpOffAlt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text((c.likeCount ?: 0).toString(), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (c.replyCount > 0) {
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.yt_replies, c.replyCount), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
