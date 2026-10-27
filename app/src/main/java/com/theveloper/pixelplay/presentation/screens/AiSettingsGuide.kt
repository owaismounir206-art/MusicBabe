package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.provider.AiGenerationPreset
import com.theveloper.pixelplay.data.ai.provider.AiKeyGuide
import com.theveloper.pixelplay.data.ai.provider.AiKeyProblem
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scena

/** Opens the provider's page where a key is created. Nothing for providers without one. */
@Composable
fun AiGetKeyLink(provider: AiProvider, modifier: Modifier = Modifier) {
    val url = AiKeyGuide.consoleUrl(provider) ?: return
    val uriHandler = LocalUriHandler.current
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(Scena.ControlShape)
            .clickable(role = Role.Button) { runCatching { uriHandler.openUri(url) } }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = scena.acc, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ai_key_get_link, provider.displayName),
            style = MaterialTheme.typography.labelLarge,
            color = scena.acc
        )
    }
}

/** The key check failure in plain words, with the provider's own message as a detail. */
@Composable
fun AiKeyProblemCard(rawMessage: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val explanation = when (AiKeyGuide.classify(rawMessage)) {
        AiKeyProblem.INVALID_KEY -> R.string.ai_key_problem_invalid
        AiKeyProblem.QUOTA -> R.string.ai_key_problem_quota
        AiKeyProblem.NETWORK -> R.string.ai_key_problem_network
        AiKeyProblem.SERVER -> R.string.ai_key_problem_server
        AiKeyProblem.OTHER -> R.string.ai_key_problem_other
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scheme.errorContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onErrorContainer
        )
        rawMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.ai_key_problem_details, it.take(200)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer.copy(alpha = 0.75f)
            )
        }
    }
}

/** Which AI features work with the keys saved right now. */
@Composable
fun AiFeaturesCard(hasAnyKey: Boolean, hasGeminiKey: Boolean, modifier: Modifier = Modifier) {
    val scena = MaterialTheme.scena
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        AiFeatureRow(R.string.ai_feature_playlists, hasAnyKey, R.string.ai_feature_needs_key)
        AiFeatureRow(R.string.ai_feature_translate, hasAnyKey, R.string.ai_feature_needs_key)
        AiFeatureRow(R.string.ai_feature_sync, hasAnyKey, R.string.ai_feature_needs_key)
        AiFeatureRow(R.string.ai_feature_listen, hasGeminiKey, R.string.ai_feature_needs_gemini)
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            text = stringResource(R.string.ai_features_fallback_note),
            style = MaterialTheme.typography.bodySmall,
            color = scena.ink2
        )
    }
}

@Composable
private fun AiFeatureRow(label: Int, ready: Boolean, missing: Int) {
    val scena = MaterialTheme.scena
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (ready) scena.acc else scena.ink2,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = scena.ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(if (ready) R.string.ai_feature_ready else missing),
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) scena.acc else scena.ink2
        )
    }
}

/** Precise / Balanced / Creative instead of raw temperature and top P numbers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStylePresets(temperature: Float, topP: Float, onPreset: (AiGenerationPreset) -> Unit) {
    val scena = MaterialTheme.scena
    val selected = AiGenerationPreset.matching(temperature, topP)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiGenerationPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                val label = when (preset) {
                    AiGenerationPreset.PRECISE -> R.string.ai_style_precise
                    AiGenerationPreset.BALANCED -> R.string.ai_style_balanced
                    AiGenerationPreset.CREATIVE -> R.string.ai_style_creative
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(Scena.ControlShape)
                        .then(
                            if (isSelected) Modifier.background(scena.ink, Scena.ControlShape)
                            else Modifier.border(1.dp, scena.ink2.copy(alpha = 0.5f), Scena.ControlShape)
                        )
                        .clickable(role = Role.RadioButton) { onPreset(preset) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scena.bg else scena.ink
                    )
                }
            }
        }
        if (selected == null) {
            Text(
                text = stringResource(R.string.ai_style_custom),
                style = MaterialTheme.typography.bodySmall,
                color = scena.ink2
            )
        }
    }
}

/** Opens or closes the advanced AI settings. */
@Composable
fun AiAdvancedToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (expanded) R.string.ai_advanced_hide else R.string.ai_advanced_show),
            style = MaterialTheme.typography.titleMedium,
            color = scena.ink,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = scena.ink
        )
    }
}
