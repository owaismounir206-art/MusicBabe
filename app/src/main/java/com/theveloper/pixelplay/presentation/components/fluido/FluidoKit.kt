package com.theveloper.pixelplay.presentation.components.fluido

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.SmartImage
import java.util.Calendar

/**
 * "Fluido rinnovato": the building blocks of the redesigned screens, drawn with the Material 3
 * Expressive roles of the current scheme (Fluido light/dark, Material You or the cover palette).
 * Hierarchy comes from surface containers, the one strong colour is `primary`, controls are pills
 * and a selected or pressed thing gets squarer.
 */
object FluidoShapes {
    val Card = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(32.dp)
    val Art = RoundedCornerShape(12.dp)
    val Cover = RoundedCornerShape(28.dp)
    val Fab = RoundedCornerShape(16.dp)
    val Group = RoundedCornerShape(20.dp)
    val GroupInner = RoundedCornerShape(4.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Chip = RoundedCornerShape(8.dp)

    /** Expressive cover masks used across mixes and shelves, in a fixed rotation. */
    val Expressive: List<Shape> = listOf(
        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 14, bottomEndPercent = 14), // arch
        CircleShape,
        RoundedCornerShape(topStartPercent = 43, topEndPercent = 14, bottomEndPercent = 43, bottomStartPercent = 14), // leaf
        RoundedCornerShape(28.dp),
    )

    fun expressive(index: Int): Shape = Expressive[((index % Expressive.size) + Expressive.size) % Expressive.size]
}

/** The greeting of the hour ("Buongiorno", "Buon pomeriggio", "Buonasera"). */
@Composable
fun fluidoGreeting(hour: Int = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }): String =
    stringResource(
        when (hour) {
            in 5..11 -> R.string.fluido_greeting_morning
            in 12..17 -> R.string.fluido_greeting_afternoon
            else -> R.string.fluido_greeting_evening
        }
    )

/** Page title: headline-large emphasised, with an optional line under it. */
@Composable
fun FluidoPageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/**
 * The search pill at the top of a page (a button: it opens the search screen). [trailing] holds the
 * avatar or a microphone.
 */
@Composable
fun FluidoSearchPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = FluidoShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing()
        }
    }
}

/** A round button with the user's initial (opens settings / account). */
@Composable
fun FluidoAvatarButton(initial: String, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initial.take(1).uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Section title with an optional action on the right ("Tutti"). */
@Composable
fun FluidoSectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(FluidoShapes.Pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The hero card of the home ("Il tuo mix"): primary container, 32 dp corners, title and line on the
 * left, up to three covers in expressive shapes on the right, Play (filled) and Shuffle (tonal).
 */
@Composable
fun FluidoHeroMixCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    covers: List<String?>,
    playLabel: String,
    shuffleDescription: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = FluidoShapes.Hero, color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp))
                    Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                HeroCovers(covers, Modifier)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FluidoPrimaryButton(label = playLabel, icon = Icons.Rounded.PlayArrow, onClick = onPlay)
                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = fluidoCardColor(),
                    contentColor = c.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shuffle, contentDescription = shuffleDescription) }
                }
            }
        }
    }
}

@Composable
private fun HeroCovers(covers: List<String?>, modifier: Modifier) {
    // Three covers in a loose cluster: a squircle, a circle and a leaf.
    val slots = listOf(
        Triple(76.dp, 0.dp to 0.dp, FluidoShapes.Cover),
        Triple(56.dp, (-58).dp to 50.dp, CircleShape),
        Triple(44.dp, 0.dp to 82.dp, FluidoShapes.expressive(2)),
    )
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    Box(modifier.size(width = 120.dp, height = 126.dp)) {
        slots.forEachIndexed { i, (size, offset, shape) ->
            val model = covers.getOrNull(i)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = offset.first, y = offset.second)
                    .size(size)
                    .clip(shape)
                    .background(colors[i])
            ) {
                if (model != null) {
                    SmartImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
        }
    }
}

/** Filled pill with an icon: the one primary action of an area. */
@Composable
fun FluidoPrimaryButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Surface(
        onClick = onClick,
        shape = FluidoShapes.Pill,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.height(height),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Square-ish tonal icon button (16 dp corners) next to a primary pill. */
@Composable
fun FluidoIconTile(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 56.dp,
) {
    Surface(onClick = onClick, shape = FluidoShapes.Fab, color = container, contentColor = content, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription) }
    }
}

/**
 * Connected button group (two or three options): outer corners pill, inner corners 4 dp, the
 * selected one on secondary-container.
 */
@Composable
fun FluidoConnectedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val first = i == 0
            val last = i == options.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 20.dp else 4.dp, bottomStart = if (first) 20.dp else 4.dp,
                topEnd = if (last) 20.dp else 4.dp, bottomEnd = if (last) 20.dp else 4.dp,
            )
            val isSelected = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = shape,
                color = if (isSelected) c.secondaryContainer else c.surfaceContainerHigh,
                contentColor = if (isSelected) c.onSecondaryContainer else c.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .semantics { this.contentDescription = label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}

/** A pinned tile ("Brani che ti piacciono"): primary container, icon in a primary circle, chevron. */
@Composable
fun FluidoPinnedTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = FluidoShapes.Card, color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = c.onPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

/** Cards on the page: white on the light ground, a step above the ground in the dark. */
@Composable
fun fluidoCardColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() > 0.5f) c.surfaceContainerLowest else c.surfaceContainerHigh
}

/** Tonal pairs for the icon circles of settings rows, by position, so groups look varied but calm. */
@Immutable
data class FluidoTone(val container: Color, val content: Color)

@Composable
fun fluidoTone(index: Int): FluidoTone {
    val c = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> FluidoTone(c.primaryContainer, c.onPrimaryContainer)
        1 -> FluidoTone(c.secondaryContainer, c.onSecondaryContainer)
        else -> FluidoTone(c.tertiaryContainer, c.onTertiaryContainer)
    }
}

/**
 * A group of settings rows, Google style: 20 dp outer corners, 4 dp between rows (2 dp gap), each
 * row on surface-container-lowest.
 */
@Composable
fun FluidoGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(FluidoShapes.Group),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun FluidoGroupRow(
    title: String,
    subtitle: String?,
    tone: FluidoTone,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconVector: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(fluidoCardColor(), FluidoShapes.GroupInner)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tone.container),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
                iconVector != null -> Icon(iconVector, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.onSurfaceVariant)
        }
    }
}

/** A cover tile of a shelf (mix, album): expressive mask, two lines under it. */
@Composable
fun FluidoShelfTile(
    title: String,
    subtitle: String?,
    cover: Any?,
    shapeIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Column(modifier.width(size).clip(FluidoShapes.Art).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(FluidoShapes.expressive(shapeIndex))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            SmartImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            overlay()
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
