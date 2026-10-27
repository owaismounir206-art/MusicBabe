package com.theveloper.pixelplay.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

/**
 * "Fluido", the MusicBabe design system: Material 3 Expressive as in today's Google apps.
 * Blue-white surfaces and Google blue for actions, Google Sans Flex, pill shapes that change when
 * touched. Two motion voices: a logarithmic curve to appear and move, springs for everything that
 * is touched or changes shape; anything that leaves fades out quickly.
 */
object Fluido {

    // ─── Curves ──────────────────────────────────────────────────────────────────────────────

    /** p(t) = ln(1 + k·t) / ln(1 + k): very fast at first, then settles slowly. */
    class LogEasing(private val k: Float) : Easing {
        private val norm = ln(1f + k)
        override fun transform(fraction: Float): Float = ln(1f + k * fraction) / norm
    }

    /** Mirror of the log curve: accelerates towards the end. Used only for fade outs. */
    class ExpEasing(private val k: Float) : Easing {
        private val norm = ln(1f + k)
        override fun transform(fraction: Float): Float = ((exp(norm * fraction) - 1f) / k).coerceIn(0f, 1f)
    }

    /** Entrances and moves (k = 60): half way in the first 12% of the time. */
    val EaseLog: Easing = LogEasing(60f)

    /** Dialogs, sheets and large surfaces (k = 10). */
    val EaseLogSoft: Easing = LogEasing(10f)

    /** Fade outs only. */
    val EaseExit: Easing = ExpEasing(25f)

    // ─── Durations (ms) ──────────────────────────────────────────────────────────────────────

    const val FadeOut = 180
    const val Short = 200
    const val Medium = 400
    const val Long = 600
    const val XLong = 900

    // ─── Springs (Material 3 Expressive) ─────────────────────────────────────────────────────

    /** Switch thumbs, checks, small objects: 9% bounce. */
    fun <T> springFast(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Shape changes of buttons and button groups: the default spatial spring. */
    fun <T> springDefault(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** FAB press, navigation indicator, selections: 16% bounce. */
    fun <T> springBouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 500f)

    /** Colour and state layers. */
    fun <T> colorSpec(): FiniteAnimationSpec<T> = tween(Short, easing = Scena.EaseEmphasised)

    fun <T> enterAlpha(delayMillis: Int = 0): FiniteAnimationSpec<T> = tween(Medium, delayMillis, EaseLog)
    fun <T> enterMove(delayMillis: Int = 0): FiniteAnimationSpec<T> = tween(Long, delayMillis, EaseLog)
    fun <T> exitFade(): FiniteAnimationSpec<T> = tween(FadeOut, easing = EaseExit)

    /** Delay of the i-th list item: 90 ms · ln(1 + i), so even 20 rows enter in under 300 ms. */
    fun staggerMillis(index: Int, stepMillis: Float = 90f): Long =
        (stepMillis * ln(1f + index.coerceAtLeast(0))).toLong()

    /** How far an entering element travels. */
    val EnterOffset = 24.dp

    // ─── Colour ──────────────────────────────────────────────────────────────────────────────

    val Light = lightColorScheme(
        primary = Color(0xFF0B57D0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF041E49),
        inversePrimary = Color(0xFFA8C7FA),
        secondary = Color(0xFF00639B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC2E7FF),
        onSecondaryContainer = Color(0xFF001D35),
        tertiary = Color(0xFF146C2E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC4EED0),
        onTertiaryContainer = Color(0xFF072711),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF8FAFD),
        onBackground = Color(0xFF1F1F1F),
        surface = Color(0xFFF8FAFD),
        onSurface = Color(0xFF1F1F1F),
        surfaceVariant = Color(0xFFE1E3E1),
        onSurfaceVariant = Color(0xFF444746),
        surfaceTint = Color(0xFF0B57D0),
        inverseSurface = Color(0xFF303030),
        inverseOnSurface = Color(0xFFF2F2F2),
        outline = Color(0xFF747775),
        outlineVariant = Color(0xFFC4C7C5),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF8FAFD),
        surfaceDim = Color(0xFFD9DDE3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F6FB),
        surfaceContainer = Color(0xFFF0F4F9),
        surfaceContainerHigh = Color(0xFFE9EEF6),
        surfaceContainerHighest = Color(0xFFDDE3EA),
    )

    val Dark = darkColorScheme(
        primary = Color(0xFFA8C7FA),
        onPrimary = Color(0xFF062E6F),
        primaryContainer = Color(0xFF0842A0),
        onPrimaryContainer = Color(0xFFD3E3FD),
        inversePrimary = Color(0xFF0B57D0),
        secondary = Color(0xFF7FCFFF),
        onSecondary = Color(0xFF003355),
        secondaryContainer = Color(0xFF004A77),
        onSecondaryContainer = Color(0xFFC2E7FF),
        tertiary = Color(0xFF6DD58C),
        onTertiary = Color(0xFF0A3818),
        tertiaryContainer = Color(0xFF0F5223),
        onTertiaryContainer = Color(0xFFC4EED0),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF131314),
        onBackground = Color(0xFFE3E3E3),
        surface = Color(0xFF131314),
        onSurface = Color(0xFFE3E3E3),
        surfaceVariant = Color(0xFF444746),
        onSurfaceVariant = Color(0xFFC4C7C5),
        surfaceTint = Color(0xFFA8C7FA),
        inverseSurface = Color(0xFFE3E3E3),
        inverseOnSurface = Color(0xFF303030),
        outline = Color(0xFF8E918F),
        outlineVariant = Color(0xFF444746),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF37393B),
        surfaceDim = Color(0xFF131314),
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = Color(0xFF1B1B1B),
        surfaceContainer = Color(0xFF1E1F20),
        surfaceContainerHigh = Color(0xFF282A2C),
        surfaceContainerHighest = Color(0xFF333537),
    )

    /** The four brand colours: illustrations, charts and the multicolour loader only. */
    val BrandBlue = Color(0xFF4285F4)
    val BrandRed = Color(0xFFEA4335)
    val BrandYellow = Color(0xFFFBBC04)
    val BrandGreen = Color(0xFF34A853)
}

/**
 * Fluido entrance: opacity in 400 ms and a 24 dp rise in 600 ms, both on the log curve, after the
 * log stagger of [index]. Opacity arrives first, so the element is readable while it brakes.
 * Use it on headers and a handful of cards, never on lazy list rows that replay while scrolling.
 */
fun Modifier.fluidEnter(index: Int = 0, offset: androidx.compose.ui.unit.Dp = Fluido.EnterOffset): Modifier = composed {
    val enabled = rememberScenaAnimationsEnabled()
    val alpha = remember { Animatable(if (enabled) 0f else 1f) }
    val move = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!enabled) return@LaunchedEffect
        delay(Fluido.staggerMillis(index))
        launch { alpha.animateTo(1f, Fluido.enterAlpha()) }
        move.animateTo(1f, Fluido.enterMove())
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = (1f - move.value) * offset.toPx()
        val scale = 0.96f + 0.04f * move.value
        scaleX = scale
        scaleY = scale
    }
}

/** Text changes enter on the log curve from below; the old text fades out in 180 ms. */
fun fluidTextTransform(): ContentTransform =
    (fadeIn(Fluido.enterAlpha()) + slideInVertically(Fluido.enterMove()) { it / 2 }) togetherWith
        fadeOut(Fluido.exitFade())

/**
 * A [Text] whose changes animate with [fluidTextTransform]: titles, artist names, counters.
 * With system animations off it swaps instantly.
 */
@Composable
fun FluidText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    val enabled = rememberScenaAnimationsEnabled()
    if (!enabled) {
        Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
        return
    }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { fluidTextTransform() },
        contentAlignment = Alignment.CenterStart,
        label = "FluidText"
    ) { value ->
        Box {
            Text(value, color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
        }
    }
}

/** Spring used by [Scena.Springy] and pressed shapes. */
internal val FluidoSpatialSpring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)
