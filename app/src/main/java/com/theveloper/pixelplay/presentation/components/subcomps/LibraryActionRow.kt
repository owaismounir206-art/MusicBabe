package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaWide
import java.io.File
import androidx.compose.ui.res.stringResource

val defaultShape = Scena.ControlShape // Fallback shape
// Scena: every action is at least 48 dp tall (touch target) and softly rounded.
val genHeight = 48.dp

// Grouped actions (New + Import, Locate + Filter + Sort) keep a tight 2 dp inner corner so they read
// as one piece; the outer corners use the Scena control radius.
private val LibraryActionOuterCorner = 26.dp
private val LibraryActionInnerCorner = 2.dp

@Composable
fun LibraryActionRow(
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    onSortClick: () -> Unit,
    onLocateClick: () -> Unit = {},
    showSortButton: Boolean,
    showLocateButton: Boolean = false,
    showImportButton: Boolean = true,
    isPlaylistTab: Boolean,
    onImportM3uClick: () -> Unit = {},
    isFoldersTab: Boolean,
    modifier: Modifier = Modifier,
    // Breadcrumb parameters
    currentFolder: MusicFolder?,
    folderRootPath: String,
    folderRootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isShuffleEnabled: Boolean = false,
    // Storage Filter
    showStorageFilterButton: Boolean = false,
    currentStorageFilter: com.theveloper.pixelplay.data.model.StorageFilter = com.theveloper.pixelplay.data.model.StorageFilter.ALL,
    onStorageFilterClick: () -> Unit = {}
) {
    val shouldShowImport = isPlaylistTab && showImportButton
    val scena = MaterialTheme.scena
    // Secondary actions sit on the ground as soft tiles (bg2); the primary action is the accent.
    val tileIconColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = scena.bg2,
        contentColor = scena.ink
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFoldersTab,
            label = "ActionRowContent",
            transitionSpec = {
                if (targetState) { // Transition to Folders (Breadcrumbs)
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else { // Transition to other tabs (Buttons)
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f)
        ) { isFolders ->
            if (isFolders) {
                Breadcrumbs(
                    currentFolder = currentFolder,
                    rootPath = folderRootPath,
                    rootLabel = folderRootLabel,
                    onFolderClick = onFolderClick,
                    onNavigateBack = onNavigateBack
                )
            } else {
                val outerCorner = LibraryActionOuterCorner
                val innerCorner = LibraryActionInnerCorner

                val newButtonEndCorner by animateDpAsState(
                    targetValue = if (shouldShowImport) innerCorner else outerCorner,
                    label = "NewButtonEndCorner"
                )

                val importButtonStartCorner by animateDpAsState(
                    targetValue = innerCorner,
                    label = "ImportButtonStartCorner"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The main action (shuffle / new playlist) is the one accent on the row.
                    val buttonContainerColor = scena.acc
                    val buttonContentColor = scena.onAcc

                    FilledTonalButton(
                        onClick = onMainActionClick,
                        shape = RoundedCornerShape(
                            topStart = outerCorner, bottomStart = outerCorner,
                            topEnd =  newButtonEndCorner, bottomEnd = newButtonEndCorner
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        elevation = null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.height(genHeight)
                    ) {
                        val icon = if (isPlaylistTab) Icons.AutoMirrored.Rounded.PlaylistAdd else Icons.Rounded.Shuffle
                        val text = if (isPlaylistTab) {
                            stringResource(R.string.library_action_new)
                        } else {
                            stringResource(R.string.common_shuffle)
                        }
                        val contentDesc = if (isPlaylistTab) {
                            stringResource(R.string.library_cd_create_new_playlist)
                        } else {
                            stringResource(R.string.common_shuffle_play)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = contentDesc,
                                modifier = Modifier.size(20.dp).rotate(iconRotation)
                            )
                            Text(
                                modifier = Modifier.animateContentSize(),
                                text = text,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = shouldShowImport,
                        enter = fadeIn() + expandHorizontally(
                            expandFrom = Alignment.Start,
                            clip = false, // <— evita el 「corte」 durante la expansión
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        exit = fadeOut() + shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            clip = false, // <— evita el 「corte」 durante la expansión
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Row(modifier = Modifier.height(genHeight), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))

                            FilledTonalButton(
                                onClick = onImportM3uClick,
                                shape = RoundedCornerShape(
                                    topStart = importButtonStartCorner,
                                    bottomStart = importButtonStartCorner,
                                    topEnd = outerCorner,
                                    bottomEnd = outerCorner
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scena.bg2,
                                    contentColor = scena.ink
                                ),
                                elevation = null,
                                contentPadding = PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 10.dp
                                ),
                                modifier = Modifier.height(genHeight)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_upload_file_24),
                                        contentDescription = stringResource(R.string.library_cd_import_m3u_playlist),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.common_import),
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.width(8.dp))

        if (showSortButton) {
            val outerCorner = LibraryActionOuterCorner

            // Logic for Sort Button (Rightmost)
            val sortStartCorner by animateDpAsState(
                targetValue = if (showLocateButton || showStorageFilterButton) LibraryActionInnerCorner else outerCorner,
                label = "SortStartCorner"
            )

            // Logic for Filter Button (Middle or Left if Locate hidden)
            // Filter is visible if showStorageFilterButton is true
            val filterEndCorner = LibraryActionInnerCorner // Connected to Sort
            val filterStartCorner by animateDpAsState(
                targetValue = if (showLocateButton) LibraryActionInnerCorner else outerCorner,
                label = "FilterStartCorner"
            )
            
            // Logic for Locate Button (Leftmost)
            val locateEndCorner = LibraryActionInnerCorner // Connected to Filter or Sort

            // Gaps: a hairline of ground between the connected tiles, none when a tile is hidden.
            val gapBetweenLocateAndNext by animateDpAsState(
                targetValue = if (showLocateButton) 2.dp else 0.dp,
                label = "GapLocate"
            )
            val gapBetweenFilterAndSort by animateDpAsState(
                targetValue = if (showStorageFilterButton) 2.dp else 0.dp,
                label = "GapFilter"
            )


            Row(verticalAlignment = Alignment.CenterVertically) {
                // Locate Button
                AnimatedVisibility(
                    visible = showLocateButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    FilledTonalIconButton(
                        onClick = onLocateClick,
                        shape = RoundedCornerShape(
                            topStart = outerCorner,
                            bottomStart = outerCorner,
                            topEnd = locateEndCorner,
                            bottomEnd = locateEndCorner
                        ),
                        colors = tileIconColors,
                        modifier = Modifier.size(genHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = stringResource(R.string.library_cd_locate_current_song)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(gapBetweenLocateAndNext))

                // Storage Filter Button
                AnimatedVisibility(
                    visible = showStorageFilterButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                     val finalIcon = when(currentStorageFilter) {
                         com.theveloper.pixelplay.data.model.StorageFilter.ALL -> Icons.Rounded.Dataset
                         com.theveloper.pixelplay.data.model.StorageFilter.ONLINE -> Icons.Rounded.Cloud
                         com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE -> Icons.Rounded.PhoneAndroid
                     }
                     val tooltipText = when(currentStorageFilter) {
                         com.theveloper.pixelplay.data.model.StorageFilter.ALL -> stringResource(R.string.library_storage_filter_all_songs)
                         com.theveloper.pixelplay.data.model.StorageFilter.ONLINE -> stringResource(R.string.library_storage_filter_online)
                         com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE -> stringResource(R.string.library_storage_filter_offline)
                     }
                     val tooltipState = rememberTooltipState()

                    @OptIn(ExperimentalMaterial3Api::class)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(tooltipText)
                            }
                        },
                        state = tooltipState
                    ) {
                        FilledTonalIconButton(
                            onClick = onStorageFilterClick,
                            shape = RoundedCornerShape(
                                topStart = filterStartCorner,
                                bottomStart = filterStartCorner,
                                topEnd = filterEndCorner,
                                bottomEnd = filterEndCorner
                            ),
                            colors = tileIconColors,
                            modifier = Modifier.size(genHeight)
                        ) {
                             Icon(
                                imageVector = finalIcon,
                                contentDescription = tooltipText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(gapBetweenFilterAndSort))

                // Sort Button
                FilledTonalIconButton(
                    onClick = onSortClick,
                    shape = RoundedCornerShape(
                        topStart = sortStartCorner,
                        bottomStart = sortStartCorner,
                        topEnd = outerCorner,
                        bottomEnd = outerCorner
                    ),
                    colors = tileIconColors,
                    modifier = Modifier.size(genHeight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.library_cd_sort_options),
                    )
                }
            }
        }
    }
}

@Composable
fun Breadcrumbs(
    currentFolder: MusicFolder?,
    rootPath: String,
    rootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rowState = rememberLazyListState()
    val pathSegments = remember(currentFolder?.path, rootPath, rootLabel) {
        val path = currentFolder?.path ?: rootPath
        val normalizedRoot = rootPath.removeSuffix("/")
        val normalizedPath = path.removeSuffix("/")
        val relativePath = normalizedPath
            .removePrefix(normalizedRoot)
            .removePrefix("/")

        if (!normalizedPath.startsWith(normalizedRoot) || relativePath.isEmpty() || normalizedPath == normalizedRoot) {
            listOf(rootLabel to rootPath)
        } else {
            listOf(rootLabel to rootPath) + relativePath.split("/").scan("") { acc, segment ->
                "$acc/$segment"
            }.drop(1).map {
                val file = File(rootPath, it)
                file.name to file.path
            }
        }
    }

    val showStartFade by remember { derivedStateOf { rowState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { rowState.canScrollForward } }
    val scena = MaterialTheme.scena
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(pathSegments.size) {
        if (pathSegments.isNotEmpty()) {
            rowState.animateScrollToItem(pathSegments.lastIndex + 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(genHeight),
            enabled = currentFolder != null,
            shape = Scena.ControlShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = scena.bg2,
                contentColor = scena.ink
            )
        ) {
            val icon = if (currentFolder == null) Icons.Rounded.Home else Icons.AutoMirrored.Rounded.ArrowBack
            Icon(
                imageVector = icon,
                contentDescription = stringResource(if (currentFolder == null) R.string.nav_bar_home else R.string.common_back)
            )
        }
        Spacer(Modifier.width(8.dp))

        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // 1. Forzamos que el contenido se dibuje en una capa separada.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // 2. Dibujamos el contenido original (el LazyRow).
                    drawContent()

                    // 3. Dibujamos los gradientes que actúan como "máscaras de borrado".
                    val gradientWidth = 24.dp.toPx()

                    // Máscara para el borde IZQUIERDO (en RTL el inicio de la lista está a la derecha)
                    if (if (isRtl) showEndFade else showStartFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de transparente a opaco (negro)
                                colors = listOf(Color.Transparent, Color.Black),
                                endX = gradientWidth
                            ),
                            // DstIn mantiene el contenido del LazyRow solo donde esta capa es opaca.
                            blendMode = BlendMode.DstIn
                        )
                    }

                    // Máscara para el borde DERECHO
                    if (if (isRtl) showStartFade else showEndFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de opaco (negro) a transparente
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = this.size.width - gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(12.dp)) }

            items(pathSegments.size, key = { pathSegments[it].second }) { index ->
                val (name, path) = pathSegments[index]
                val isLast = index == pathSegments.lastIndex
                // 48 dp touch target around the wide Scena label.
                Box(
                    modifier = Modifier
                        .heightIn(min = genHeight)
                        .clip(CircleShape)
                        .clickable(enabled = !isLast) { onFolderClick(path) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        style = scenaWide(16.sp),
                        color = if (isLast) scena.ink else scena.ink2,
                        maxLines = 1
                    )
                }
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scena.ink2.copy(alpha = 0.6f),
                        // The chevron points along the reading direction.
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = if (isRtl) -1f else 1f }
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(12.dp)) }
        }
    }
}
