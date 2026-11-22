package com.theveloper.pixelplay.presentation.components.fluido

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

/** A round tonal button of a page header (cloud, news, settings). */
@Composable
fun FluidoHeaderAction(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                painter != null -> Icon(painter, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Top of the home: the greeting of the hour with the header actions, and the search pill that
 * opens the search tab. It scrolls with the page.
 */
@Composable
fun FluidoHomeHeader(
    onSearch: () -> Unit,
    onCloud: () -> Unit,
    onNews: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FluidoSearchPill(
            placeholder = stringResource(R.string.fluido_search_everything),
            onClick = onSearch,
            leading = Icons.Rounded.Search,
            trailing = {
                FluidoHeaderAction(
                    stringResource(R.string.common_settings), onSettings,
                    painter = painterResource(R.drawable.rounded_settings_24),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        )
        FluidoPageTitle(fluidoGreeting()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FluidoHeaderAction(stringResource(R.string.topbar_cd_cloud_streaming), onCloud, icon = Icons.Rounded.Cloud)
                FluidoHeaderAction(stringResource(R.string.topbar_cd_changelog), onNews, painter = painterResource(R.drawable.round_newspaper_24))
            }
        }
    }
}

/**
 * M3 Expressive filter chip: 32 dp, 8 dp corners, a check when selected on secondary-container,
 * an outline when not.
 */
@Composable
fun FluidoFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = FluidoShapes.Chip,
        color = if (selected) c.secondaryContainer else c.surface,
        contentColor = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, c.outlineVariant),
        modifier = modifier
            .height(32.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
    ) {
        Row(
            Modifier.padding(start = if (selected) 8.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
        }
    }
}
