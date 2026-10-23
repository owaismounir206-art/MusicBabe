package com.theveloper.pixelplay.ui.theme

import android.graphics.Matrix
import android.graphics.RectF
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.theveloper.pixelplay.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * MusicBabe "Scena" design layer.
 *
 * Colour is always the dynamic Material You scheme (wallpaper, or the cover of the current track in
 * the player). Scena only decides how that colour is *used*: whole screens are drenched in a tonal
 * ground derived from the scheme's primary container, text is large and wide, covers become
 * squircles that morph into spinning records, and the disc is a 12-lobed "cookie".
 */
object Scena {
    /** Cover thumbnails in lists. */
    val ArtShape = RoundedCornerShape(percent = 34)

    /** Large covers (player, album header). */
    val CoverShape = RoundedCornerShape(28.dp)

    /** Round-ish controls: skip buttons, chips. */
    val ControlShape = RoundedCornerShape(26.dp)

    /** Groups and tiles (settings bento, mix deck). */
    val TileShape = RoundedCornerShape(32.dp)

    /** Arch: the tallest mix card shape. */
    val ArchShape = RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp, bottomStart = 36.dp, bottomEnd = 36.dp)

    /** Horizontal page margin. */
    val Gutter = 16.dp

    /** Motion: springy, a little overshoot. */
    val Springy: SpringSpec<Float> = spring(dampingRatio = 0.62f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)

    /** Motion: emphasised ease-out for entrances and colour changes. */
    val EaseEmphasised = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Tonal roles used by Scena screens, derived from the dynamic Material scheme. */
@Immutable
data class ScenaColors(
    /** Screen ground, drenched with the primary container. */
    val bg: Color,
    /** Cards, rows that are current, the mini player. */
    val bg2: Color,
    /** Raised surfaces on top of [bg2]. */
    val bg3: Color,
    val ink: Color,
    /** Secondary text and icons. */
    val ink2: Color,
    /** The one accent: current track, play button, selection. */
    val acc: Color,
    val onAcc: Color,
)

/**
 * The Scena palette for the current theme. Every value is a blend of Material scheme roles, so it
 * follows Material You and the cover-art scheme automatically, light or dark.
 */
val MaterialTheme.scena: ScenaColors
    @Composable
    get() {
        val c = colorScheme
        val dark = LocalPixelPlayDarkTheme.current
        return remember(c, dark) {
            if (dark) {
                ScenaColors(
                    bg = lerp(c.surface, c.primaryContainer, 0.45f),
                    bg2 = lerp(c.surface, c.primaryContainer, 0.72f),
                    bg3 = c.primaryContainer,
                    ink = c.onSurface,
                    ink2 = c.onSurfaceVariant,
                    acc = c.primary,
                    onAcc = c.onPrimary,
                )
            } else {
                ScenaColors(
                    bg = lerp(c.surface, c.primaryContainer, 0.5f),
                    bg2 = lerp(c.surface, c.primaryContainer, 0.78f),
                    bg3 = c.primaryContainer,
                    ink = c.onSurface,
                    ink2 = c.onSurfaceVariant,
                    acc = c.primary,
                    onAcc = c.onPrimary,
                )
            }
        }
    }

/** False when the user turned system animations off (Settings > Developer / Accessibility). */
@Composable
fun rememberScenaAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

// ─── Type ────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTextApi::class)
private fun scenaFamily(weight: Int, width: Float): FontFamily = FontFamily(
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("ROND", 0f)
        )
    )
)

/** Expanded, heavy: screen titles ("Stasera", "Libreria"). */
val ScenaDisplayFamily: FontFamily by lazy { scenaFamily(weight = 800, width = 130f) }

/** Expanded, semibold: section titles, tabs, names on cards. */
val ScenaWideFamily: FontFamily by lazy { scenaFamily(weight = 720, width = 122f) }

/** Screen title style. The line height is tighter than 1 on purpose: titles are one or two lines. */
fun scenaDisplay(fontSize: TextUnit): TextStyle = TextStyle(
    fontFamily = ScenaDisplayFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = fontSize,
    lineHeight = fontSize * 1.0f,
    letterSpacing = (-0.02).em
)

/** Section title / tab style. */
fun scenaWide(fontSize: TextUnit): TextStyle = TextStyle(
    fontFamily = ScenaWideFamily,
    fontWeight = FontWeight.Bold,
    fontSize = fontSize,
    lineHeight = fontSize * 1.15f,
    letterSpacing = (-0.01).em
)

// ─── Shapes ──────────────────────────────────────────────────────────────────────────────────

private const val COOKIE_BODY = 0.84f
private const val COOKIE_CORNER = 0.26f

/** Fills [path] with the three rounded squares (0°, 30°, 60°) that make a 12-lobed cookie. */
private fun cookiePath(size: Size): Path {
    val side = size.minDimension * COOKIE_BODY
    val radius = side * COOKIE_CORNER
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rect = RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    fun lobe(degrees: Float): Path {
        val p = android.graphics.Path()
        p.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        p.transform(Matrix().apply { setRotate(degrees, cx, cy) })
        return p.asComposePath()
    }
    val a = Path.combine(PathOperation.Union, lobe(0f), lobe(30f))
    return Path.combine(PathOperation.Union, a, lobe(60f))
}

/** The cookie as a [Shape], to clip cover art or backgrounds to it. */
val CookieShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(cookiePath(size))
}

/**
 * The record: a cookie that turns slowly while [spinning]. The rotation lives in the graphics
 * layer, so it never recomposes. With system animations off the disc stays still.
 */
@Composable
fun CookieDisc(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.scena.acc,
    spinning: Boolean = false,
    periodMillis: Int = 18_000,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val enabled = rememberScenaAnimationsEnabled()
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning, enabled, periodMillis) {
        if (spinning && enabled) {
            while (coroutineContext.isActive) {
                angle.animateTo(angle.value + 360f, tween(periodMillis, easing = LinearEasing))
                angle.snapTo(angle.value % 360f)
            }
        }
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = angle.value }
        ) {
            drawPath(path = cookiePath(size), color = color)
        }
        content()
    }
}

// ─── Motion ──────────────────────────────────────────────────────────────────────────────────

/**
 * Entrance: rises 24 dp and fades in, [index] * 55 ms after first composition. Use it on headers
 * and a handful of cards, never on lazy list rows (they would replay while scrolling).
 */
fun Modifier.scenaRise(index: Int = 0): Modifier = composed {
    val enabled = rememberScenaAnimationsEnabled()
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay(index * 55L)
            progress.animateTo(1f, tween(650, easing = Scena.EaseEmphasised))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}
