package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scenaWide

/** Share of the bar the active item claims, relative to 1 for each inactive item. */
private const val ActiveSlotWeight = 2f

/**
 * Scena navigation item.
 *
 * The inactive item is just its icon. The active item is a pill filled with the accent that
 * widens (spring) to show the label next to the icon. The whole slot, full bar height, is the
 * touch target. Labels are only ever shown inside the active pill, so [alwaysShowLabel] is kept
 * for source compatibility and ignored; the accessible name always comes from [contentDescription].
 */
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compactMode: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") alwaysShowLabel: Boolean = false,
    selectedIconColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // With system animations off every transition below snaps.
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val floatSpec: AnimationSpec<Float> = if (animationsEnabled) Scena.Springy else snap<Float>()
    val colorSpec: AnimationSpec<Color> = if (animationsEnabled) {
        tween<Color>(durationMillis = 220, easing = Scena.EaseEmphasised)
    } else {
        snap<Color>()
    }

    val pillColor by animateColorAsState(
        targetValue = if (selected) indicatorColor else indicatorColor.copy(alpha = 0f),
        animationSpec = colorSpec,
        label = "pillColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = colorSpec,
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = colorSpec,
        label = "textColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = floatSpec,
        label = "iconScale"
    )

    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = floatSpec,
        label = "pressScale"
    )

    // Compact bars carry no labels, so every slot keeps the same width.
    val showLabel = label != null && !compactMode && selected
    val slotWeight by animateFloatAsState(
        targetValue = if (showLabel) ActiveSlotWeight else 1f,
        animationSpec = floatSpec,
        label = "slotWeight"
    )

    val pillHeight = if (compactMode) 40.dp else 48.dp
    // When the item has an explicit accessible name the visible label is not read a second time.
    val labelSemantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier
    }
    val currentPageDescription = stringResource(R.string.scena_nav_current_page)

    val labelEnter = if (animationsEnabled) {
        expandHorizontally(
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntSize.VisibilityThreshold
            ),
            expandFrom = Alignment.Start
        ) + fadeIn(animationSpec = tween(180, delayMillis = 40))
    } else {
        fadeIn(animationSpec = snap<Float>())
    }
    val labelExit = if (animationsEnabled) {
        shrinkHorizontally(
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                visibilityThreshold = IntSize.VisibilityThreshold
            ),
            shrinkTowards = Alignment.Start
        ) + fadeOut(animationSpec = tween(90))
    } else {
        fadeOut(animationSpec = snap<Float>())
    }

    // The slot: full height, so the touch target is far above 48 dp.
    Box(
        modifier = modifier
            .weight(slotWeight.coerceAtLeast(0.1f))
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                this.selected = selected
                if (selected) {
                    this.stateDescription = currentPageDescription
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(pillHeight)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(Scena.ControlShape)
                .background(pillColor)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) selectedIcon() else icon()
                }
            }

            AnimatedVisibility(
                visible = showLabel,
                enter = labelEnter,
                exit = labelExit
            ) {
                Row(
                    modifier = labelSemantics,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ProvideTextStyle(value = scenaWide(13.sp).copy(color = textColor)) {
                        label?.invoke()
                    }
                }
            }
        }
    }
}
