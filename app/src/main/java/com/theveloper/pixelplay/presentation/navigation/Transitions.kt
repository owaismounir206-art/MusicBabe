package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

const val TRANSITION_DURATION = 350

// Pixel Motion Specs: Spring physics without bounce for smooth, organic page transitions
val NavSpringSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)
val NavFloatSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// Shared axis enter transition powered by core motion springs
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = NavSpringSpec
    ) + fadeIn(
        animationSpec = NavFloatSpringSpec
    )
}

// Shared axis exit transition
fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = NavSpringSpec
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = NavFloatSpringSpec
    )
}

// Shared axis pop enter transition
fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = NavSpringSpec
    ) + fadeIn(
        animationSpec = NavFloatSpringSpec
    )
}

// Shared axis pop exit transition
fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = NavSpringSpec
    ) + scaleOut(
        targetScale = 0.88f,
        animationSpec = NavFloatSpringSpec
    )
}

// Push: Enter from Right — smooth spring slide + subtle scale up
fun enterTransition() = slideInHorizontally(
    animationSpec = NavSpringSpec,
    initialOffsetX = { (it * 0.45f).toInt() }
) + scaleIn(
    animationSpec = NavFloatSpringSpec,
    initialScale = 0.94f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = NavFloatSpringSpec
)

// Push: Exit to Left — parallax spring slide-out
fun exitTransition() = slideOutHorizontally(
    animationSpec = NavSpringSpec,
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = NavFloatSpringSpec
)

// Pop: Enter from Left — parallax slide-in + subtle scale up
fun popEnterTransition() = slideInHorizontally(
    animationSpec = NavSpringSpec,
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = NavFloatSpringSpec,
    initialScale = 0.95f
) + fadeIn(
    animationSpec = NavFloatSpringSpec
)

// Pop: Exit to Right — slides out with spring deceleration
fun popExitTransition() = slideOutHorizontally(
    animationSpec = NavSpringSpec,
    targetOffsetX = { (it * 0.45f).toInt() }
) + scaleOut(
    animationSpec = NavFloatSpringSpec,
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = NavFloatSpringSpec
)
