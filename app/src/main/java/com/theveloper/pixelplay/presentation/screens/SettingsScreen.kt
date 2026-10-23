package com.theveloper.pixelplay.presentation.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import com.theveloper.pixelplay.ui.theme.scenaRise
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController

// SettingsTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {

    val scena = MaterialTheme.scena

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val launchTab = uiState.launchTab
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

    var showCornerRadiusOverlay by remember { mutableStateOf(false) }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
                1f -
                        ((topBarHeight.value - minTopBarHeightPx) /
                                        (maxTopBarHeightPx - minTopBarHeightPx))
                                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    // The screen ground is drenched with the dynamic scheme (Scena). The ground is drawn outside of
    // any animated layer so it never moves while the tiles rise in.
    Box(
            modifier =
                    Modifier
                        .nestedScroll(nestedScrollConnection)
                        .fillMaxSize()
                        .background(scena.bg)
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp + 8.dp,
                    start = Scena.Gutter,
                    end = Scena.Gutter,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsBento(tiles = settingsTiles(navController))

                // for player active:
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        // Scena title: big and tight. The bar owns the font family (it replaces it with its own
        // variable-font setup), so size, tracking and colour are what we can steer from here.
        CollapsibleCommonTopBar(
                title = stringResource(R.string.common_settings),
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackClick = onNavigationIconClick,
                titleStyle = scenaDisplay(40.sp),
                titleScaleRange = 1f to 1f,
                titleFontSizeRange = 40.sp to 22.sp,
                containerColor = scena.bg,
                contentColor = scena.ink
        )

        // Block interaction during transition
        var isTransitioning by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(com.theveloper.pixelplay.presentation.navigation.TRANSITION_DURATION.toLong())
            isTransitioning = false
        }

        if (isTransitioning) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                     awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
            )
        }
    }
}

// ─── Bento grid ──────────────────────────────────────────────────────────────────────────────

/** One entry of the bento grid. [featured] tiles span both columns and use the accent colour. */
private class SettingsBentoEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector?,
    @DrawableRes val iconRes: Int?,
    val featured: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun categoryTile(category: SettingsCategory, onClick: () -> Unit): SettingsBentoEntry =
    SettingsBentoEntry(
        title = stringResource(category.titleRes),
        subtitle = stringResource(category.subtitleRes),
        icon = category.icon,
        iconRes = category.iconRes,
        // The assistant category is the one prominent tile.
        featured = category == SettingsCategory.AI_INTEGRATION,
        onClick = onClick
    )

/** Same categories, same order and same destinations as the previous vertical list. */
@Composable
private fun settingsTiles(navController: NavController): List<SettingsBentoEntry> {
    val mainCategories = SettingsCategory.entries.filter {
        it != SettingsCategory.ABOUT &&
        it != SettingsCategory.DEVICE_CAPABILITIES
    }

    val tiles = ArrayList<SettingsBentoEntry>()

    mainCategories.forEach { category ->
        tiles.add(
            categoryTile(category) {
                if (category == SettingsCategory.EQUALIZER) {
                    navController.navigateSafely(Screen.Equalizer.route)
                } else {
                    navController.navigateSafely(Screen.SettingsCategory.createRoute(category.id))
                }
            }
        )
    }

    tiles.add(
        categoryTile(SettingsCategory.DEVICE_CAPABILITIES) {
            navController.navigateSafely(Screen.DeviceCapabilities.route)
        }
    )

    tiles.add(
        SettingsBentoEntry(
            title = stringResource(R.string.settings_category_accounts_title),
            subtitle = stringResource(R.string.settings_category_accounts_subtitle),
            icon = Icons.Rounded.AccountCircle,
            iconRes = null,
            featured = false,
            onClick = { navController.navigateSafely(Screen.Accounts.route) }
        )
    )

    tiles.add(
        SettingsBentoEntry(
            title = stringResource(R.string.downloads_title),
            subtitle = stringResource(R.string.settings_download_section_summary),
            icon = Icons.Rounded.Download,
            iconRes = null,
            featured = false,
            onClick = { navController.navigateSafely(Screen.Downloads.route) }
        )
    )

    tiles.add(
        categoryTile(SettingsCategory.ABOUT) {
            navController.navigateSafely("about")
        }
    )

    return tiles
}

/**
 * Packs [tiles] into rows, keeping their order: two tiles per row, a featured tile alone on a row,
 * and a trailing odd tile alone on its row (it then spans both columns instead of leaving a hole).
 */
private fun packBentoRows(tiles: List<SettingsBentoEntry>): List<List<SettingsBentoEntry>> {
    val rows = ArrayList<List<SettingsBentoEntry>>()
    var pending: SettingsBentoEntry? = null
    for (tile in tiles) {
        val waiting = pending
        if (tile.featured) {
            if (waiting != null) rows.add(listOf(waiting))
            rows.add(listOf(tile))
            pending = null
        } else if (waiting == null) {
            pending = tile
        } else {
            rows.add(listOf(waiting, tile))
            pending = null
        }
    }
    val last = pending
    if (last != null) rows.add(listOf(last))
    return rows
}

/** Rows of this index and above do not play the entrance (they start below the fold). */
private const val BentoEntranceRows = 5

private val BentoGap = 12.dp
private val BentoTileMinHeight = 148.dp

@Composable
private fun SettingsBento(tiles: List<SettingsBentoEntry>, modifier: Modifier = Modifier) {
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val rows = packBentoRows(tiles)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BentoGap)
    ) {
        rows.forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(if (rowIndex < BentoEntranceRows) Modifier.scenaRise(rowIndex) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(BentoGap)
            ) {
                val wide = rowTiles.size == 1
                rowTiles.forEach { tile ->
                    SettingsBentoTile(
                        tile = tile,
                        wide = wide,
                        animationsEnabled = animationsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsBentoTile(
    tile: SettingsBentoEntry,
    wide: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val scena = MaterialTheme.scena
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    // Springy with a little overshoot; with system animations off the tile just snaps.
    val motionSpec: AnimationSpec<Float> = if (animationsEnabled) Scena.Springy else snap<Float>()
    val iconMorph by animateFloatAsState(
        targetValue = if (pressed || hovered) 1f else 0f,
        animationSpec = motionSpec,
        label = "SettingsTileIconMorph"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motionSpec,
        label = "SettingsTilePressScale"
    )

    val container = if (tile.featured) scena.acc else scena.bg2
    val onContainer = if (tile.featured) scena.onAcc else scena.ink
    val onContainerVariant = if (tile.featured) scena.onAcc.copy(alpha = 0.86f) else scena.ink2

    // The icon holder sits on bg3 (the primary container), so its glyph uses the matching role.
    val holderIconTint = MaterialTheme.colorScheme.onPrimaryContainer
    // Rounded square at rest, a circle while pressed or hovered.
    val holderCorner = lerp(16.dp, 24.dp, iconMorph).coerceAtLeast(0.dp)

    val openLabel = stringResource(R.string.settings_scena_tile_open, tile.title)
    val icon = tile.icon
    val iconRes = tile.iconRes

    CompositionLocalProvider(LocalContentColor provides onContainer) {
        Box(
            modifier = modifier
                .heightIn(min = BentoTileMinHeight)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(Scena.TileShape)
                .background(container)
                .hoverable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = openLabel,
                    role = Role.Button,
                    onClick = tile.onClick
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(holderCorner))
                            .background(scena.bg3)
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = holderIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (iconRes != null) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = holderIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (wide) {
                        Spacer(modifier = Modifier.weight(1f))
                        // Decorative: the whole tile is the button, and it carries the label.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(onContainer.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = onContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainerVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ─── Legacy row composables ──────────────────────────────────────────────────────────────────
// No longer used by the Settings screen (the bento grid above replaced the vertical list). They are
// kept as public API for compatibility.

@Composable
fun ExpressiveNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.first)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    customColors: Pair<Color, Color>? = null
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
        ) {
            // Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(customColors?.first ?: MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
    ) {
        content()
    }
}
