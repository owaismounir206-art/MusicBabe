package com.theveloper.pixelplay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * MusicBabe "sleeve" design tokens.
 *
 * Artwork is shown as a square record sleeve with a barely softened corner, and structure is
 * drawn with hairlines and whitespace instead of a card per row. Colour still comes from the
 * dynamic scheme (Material You / cover art); these tokens only carry shape, rule and figure style.
 */
object Sleeve {
    /** Corner of every artwork thumbnail and cover. */
    val ArtShape = RoundedCornerShape(4.dp)

    /** Corner of large covers (player, album header), proportionally a little softer. */
    val CoverShape = RoundedCornerShape(8.dp)

    /** Background of a row in a pressed, current or selected state. */
    val RowShape = RoundedCornerShape(8.dp)

    /** Thickness of separators. */
    val Hairline = 1.dp

    /** Horizontal page margin. */
    val Gutter = 20.dp

    /** Tabular figures, so durations and counts line up in columns. */
    val TabularFigures = TextStyle(fontFeatureSettings = "tnum")
}

/** Colour of separators: present but quiet on every dynamic scheme. */
val hairlineColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
