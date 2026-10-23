package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
// Scena AI settings (keys, provider chips, model picker)
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaWide
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback

@Composable
fun SettingsSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        onClick: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                        modifier = Modifier.padding(end = 4.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    if (enabled) {
                        performAppCompatHapticFeedback(
                            view,
                            appHapticsConfig,
                            HapticFeedbackConstantsCompat.GESTURE_START
                        )
                        onCheckedChange(newValue)
                    }
                },
                enabled = enabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                        label = "switch_thumb_icon"
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).clickable {
                        showSheet = true
                    }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Selected Value Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                             text = selectedOption,
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.primary,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall, // Larger header
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options.entries.toList()) { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        
                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableModelSelector(
    label: String,
    description: String,
    models: List<GeminiModel>,
    selectedModelName: String,
    onModelSelected: (String) -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    val scena = MaterialTheme.scena
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedDisplayName = models.find { it.name == selectedModelName }?.displayName ?: selectedModelName

    Surface(
        onClick = { showSheet = true },
        color = scena.bg2,
        shape = Scena.TileShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = scenaWide(17.sp),
                    color = scena.ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scena.ink2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = scena.acc,
                    contentColor = scena.onAcc,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = selectedDisplayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = scena.onAcc,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                searchQuery = ""
            },
            containerColor = scena.bg,
            contentColor = scena.ink
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = scenaWide(24.sp),
                    color = scena.ink,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.scena_ai_models_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.scena_ai_models_search_cd)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.scena_ai_models_clear_cd)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = Scena.ControlShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = scena.acc,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = scena.bg2,
                        unfocusedContainerColor = scena.bg2,
                        cursorColor = scena.acc
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                val filteredModels = remember(models, searchQuery) {
                    if (searchQuery.isBlank()) models
                    else models.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                            it.displayName.contains(searchQuery, ignoreCase = true)
                    }
                }

                Text(
                    text = pluralStringResource(
                        R.plurals.scena_ai_models_available,
                        filteredModels.size,
                        filteredModels.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scena.ink2,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 400.dp)
                ) {
                    items(filteredModels, key = { it.name }) { model ->
                        val isSelected = model.name == selectedModelName
                        val rowContent = if (isSelected) scena.bg else scena.ink
                        Surface(
                            onClick = {
                                onModelSelected(model.name)
                                showSheet = false
                                searchQuery = ""
                            },
                            color = if (isSelected) scena.ink else scena.bg2,
                            contentColor = rowContent,
                            shape = Scena.ControlShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = rowContent
                                    )
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) scena.bg.copy(alpha = 0.8f) else scena.ink2
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = scena.bg
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)? = null,
        valueText: (Float) -> String
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        activeOperationLabel: String? = null,
        onFullSync: () -> Unit,
        onRebuild: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.settings_refresh_library_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.settings_refresh_library_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Full Rescan button
            FilledTonalButton(
                    onClick = onFullSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_full_rescan),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }
             
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rebuild Database button - full width, destructive action
            OutlinedButton(
                    onClick = onRebuild,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_rebuild_database),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                val phaseLabel = activeOperationLabel ?: syncPhaseLabel(syncProgress.phase)
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progress_detailed,
                                phaseLabel,
                                (syncProgress.progress * 100).toInt(),
                                syncProgress.currentCount,
                                syncProgress.totalCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progres_indeterminate,
                                phaseLabel
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun syncPhaseLabel(phase: SyncProgress.SyncPhase): String =
        stringResource(
                when (phase) {
                    SyncProgress.SyncPhase.IDLE -> R.string.settings_sync_phase_preparing
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                            R.string.settings_sync_phase_reading_mediastore
                    SyncProgress.SyncPhase.PROCESSING_FILES ->
                            R.string.settings_sync_phase_reading_processing_tracks
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                            R.string.settings_sync_phase_saving_db
                    SyncProgress.SyncPhase.SCANNING_LRC -> R.string.settings_sync_phase_scanning_lrc
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                            R.string.settings_sync_phase_cleaning_cache
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                            R.string.settings_sync_phase_syncing_cloud
                    SyncProgress.SyncPhase.COMPLETING -> R.string.settings_sync_phase_completing
                }
        )

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(primaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Secondary Action (Optional)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ─── AI keys (Scena) ─────────────────────────────────────────────────────────────────────────

/** What the key panel says about the key saved for the selected provider. */
enum class AiKeyStatus { NoKey, NotVerified, Verifying, Verified, Failed }

/** Colour changes ease out; with system animations off they snap. */
@Composable
private fun rememberAiColorSpec(): AnimationSpec<Color> =
    if (rememberScenaAnimationsEnabled()) {
        tween<Color>(durationMillis = 320, easing = Scena.EaseEmphasised)
    } else {
        snap<Color>()
    }

/** Springy press feedback shared by the AI buttons. Stays at 1 when system animations are off. */
@Composable
private fun rememberAiPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val animate = rememberScenaAnimationsEnabled()
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed && animate) 0.96f else 1f,
        animationSpec = Scena.Springy,
        label = "aiPressScale"
    )
}

/** A pill-shaped choice: the selected one is inverted (ink background, ground-coloured text). */
@Composable
private fun AiChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scena = MaterialTheme.scena
    val colorSpec = rememberAiColorSpec()
    val container by animateColorAsState(
        targetValue = if (selected) scena.ink else scena.bg2,
        animationSpec = colorSpec,
        label = "aiChipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) scena.bg else scena.ink,
        animationSpec = colorSpec,
        label = "aiChipContent"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Scena.ControlShape)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = scenaWide(14.sp), color = content, maxLines = 1)
    }
}

/**
 * Provider picker: every [AiProvider] as a chip in a horizontally scrollable row. The row opens
 * scrolled so the selected chip is in view.
 */
@Composable
fun AiProviderChips(
    providers: List<AiProvider>,
    selectedKey: String,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = providers.indexOfFirst { it.name == selectedKey }.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0)
    )
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(providers, key = { it.name }) { provider ->
            AiChip(
                label = provider.displayName,
                selected = provider.name == selectedKey,
                onClick = { onProviderSelected(provider.name) }
            )
        }
    }
}

/**
 * Primary action. Rounded square at rest; while [busy] it collapses into a circle holding a
 * progress indicator, with a springy overshoot.
 */
@Composable
private fun AiMorphButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scena = MaterialTheme.scena
    val animate = rememberScenaAnimationsEnabled()
    val colorSpec = rememberAiColorSpec()
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberAiPressScale(interactionSource)
    val active = enabled || busy
    val corner by animateDpAsState(
        targetValue = if (busy) 28.dp else 20.dp,
        animationSpec = if (animate) {
            spring<Dp>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            snap<Dp>()
        },
        label = "aiMorphCorner"
    )
    val container by animateColorAsState(
        targetValue = if (active) scena.acc else scena.ink.copy(alpha = 0.12f),
        animationSpec = colorSpec,
        label = "aiMorphContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) scena.onAcc else scena.ink.copy(alpha = 0.38f),
        animationSpec = colorSpec,
        label = "aiMorphContent"
    )

    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale.value
            scaleY = pressScale.value
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interactionSource
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = {
                if (animate) {
                    (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(90)))
                        .using(
                            SizeTransform(clip = true) { _, _ ->
                                spring<IntSize>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
                            }
                        )
                } else {
                    (EnterTransition.None togetherWith ExitTransition.None)
                        .using(SizeTransform(clip = false) { _, _ -> snap<IntSize>() })
                }
            },
            label = "aiMorphLabel"
        ) { isBusy ->
            Box(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .widthIn(min = 56.dp)
                    .padding(horizontal = if (isBusy) 0.dp else 28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = label },
                        color = content,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = label,
                        style = scenaWide(15.sp),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Secondary action on a raised tonal surface. */
@Composable
private fun AiSecondaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scena = MaterialTheme.scena
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberAiPressScale(interactionSource)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale.value
            scaleY = pressScale.value
        },
        shape = Scena.ControlShape,
        color = scena.bg3,
        contentColor = if (enabled) scena.ink else scena.ink.copy(alpha = 0.38f),
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = scenaWide(15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A dot and a sentence. The dot takes the accent only when something is positively confirmed. */
@Composable
private fun AiKeyStatusLine(text: String, verified: Boolean, failed: Boolean) {
    val scena = MaterialTheme.scena
    val colorSpec = rememberAiColorSpec()
    val dot by animateColorAsState(
        targetValue = if (verified) scena.acc else scena.ink2,
        animationSpec = colorSpec,
        label = "aiStatusDot"
    )
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                failed -> MaterialTheme.colorScheme.error
                verified -> scena.ink
                else -> scena.ink2
            }
        )
    }
}

/**
 * The editor for a secret (an API key) or, with [masked] false, for a plain value such as a URL.
 *
 * With [keyStatus] set it becomes the full key panel: masked field with a reveal toggle, a status
 * line, a primary button that saves any edit and verifies the key through [onVerify], and a
 * secondary button that removes the key by saving an empty one. Without it, it is a plain field
 * with a Save button.
 *
 * [stateKey] must change when the thing being edited changes (for example the provider) so that an
 * unsaved draft never leaks into another provider.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiApiKeyItem(
    apiKey: String,
    onApiKeySave: (String) -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    masked: Boolean = true,
    placeholder: String? = null,
    keyStatus: AiKeyStatus? = null,
    onVerify: () -> Unit = {}
) {
    val scena = MaterialTheme.scena
    val focusManager = LocalFocusManager.current
    var localApiKey by remember(apiKey, stateKey) { mutableStateOf(apiKey) }
    var revealed by remember(stateKey) { mutableStateOf(false) }
    var confirmRemove by remember(stateKey) { mutableStateOf(false) }
    var showSaved by remember(stateKey) { mutableStateOf(false) }
    val hasChanges = localApiKey.trim() != apiKey.trim()
    val verifying = keyStatus == AiKeyStatus.Verifying

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = scenaWide(18.sp), color = scena.ink)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scena.ink2
            )
        }

        // The field: 64 dp, rounded, on the screen ground. Masked unless revealed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(Scena.ControlShape)
                .background(scena.bg)
                .padding(start = 20.dp, end = if (masked) 8.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = title },
                textStyle = TextStyle(
                    color = scena.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    letterSpacing = if (masked) 0.08.em else 0.em
                ),
                cursorBrush = SolidColor(scena.acc),
                singleLine = true,
                visualTransformation = if (masked && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (masked) KeyboardType.Password else KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (localApiKey.isEmpty()) {
                            Text(
                                text = placeholder ?: stringResource(R.string.scena_ai_key_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scena.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (masked) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.scena_ai_key_hide else R.string.scena_ai_key_show
                        ),
                        tint = scena.ink2
                    )
                }
            }
        }

        if (keyStatus != null) {
            // An edit that is not saved yet says so; otherwise the line reports the saved key.
            val unsaved = hasChanges && apiKey.isNotBlank()
            val statusRes = when {
                unsaved -> R.string.scena_ai_status_unsaved
                keyStatus == AiKeyStatus.NoKey -> R.string.scena_ai_status_no_key
                keyStatus == AiKeyStatus.Verifying -> R.string.scena_ai_status_verifying
                keyStatus == AiKeyStatus.Verified -> R.string.scena_ai_status_verified
                keyStatus == AiKeyStatus.Failed -> R.string.scena_ai_status_failed
                else -> R.string.scena_ai_status_not_verified
            }
            AiKeyStatusLine(
                text = stringResource(statusRes),
                verified = !unsaved && keyStatus == AiKeyStatus.Verified,
                failed = !unsaved && keyStatus == AiKeyStatus.Failed
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiMorphButton(
                    label = stringResource(
                        if (hasChanges) R.string.scena_ai_save_and_verify else R.string.scena_ai_verify
                    ),
                    busy = verifying,
                    enabled = localApiKey.isNotBlank() && !verifying,
                    onClick = {
                        focusManager.clearFocus()
                        // Saving a changed key also runs the model fetch, which is the verification.
                        if (hasChanges) onApiKeySave(localApiKey) else onVerify()
                    }
                )
                if (apiKey.isNotBlank()) {
                    AiSecondaryButton(
                        label = stringResource(R.string.scena_ai_remove),
                        enabled = !verifying,
                        onClick = { confirmRemove = true }
                    )
                }
            }
        } else {
            if (showSaved) {
                AiKeyStatusLine(
                    text = stringResource(R.string.common_saved),
                    verified = true,
                    failed = false
                )
            }
            AiMorphButton(
                label = stringResource(R.string.common_save),
                busy = false,
                enabled = hasChanges,
                onClick = {
                    focusManager.clearFocus()
                    onApiKeySave(localApiKey)
                    showSaved = true
                }
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            shape = Scena.TileShape,
            containerColor = scena.bg,
            title = {
                Text(
                    text = stringResource(R.string.scena_ai_remove_title),
                    style = scenaWide(20.sp),
                    color = scena.ink
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.scena_ai_remove_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scena.ink2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        // An empty key goes through the normal save path, which wipes secure storage.
                        onApiKeySave("")
                    }
                ) {
                    Text(
                        text = stringResource(R.string.scena_ai_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(text = stringResource(R.string.common_cancel), color = scena.ink2)
                }
            }
        )
    }
}

/** States how the key is stored. Only claims what the storage layer actually does. */
@Composable
fun AiKeySecurityNote(modifier: Modifier = Modifier) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.CoverShape)
            .background(scena.bg3)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scena.bg2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = scena.ink,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.scena_ai_security_title),
                style = scenaWide(15.sp),
                color = scena.ink
            )
            Text(
                text = stringResource(R.string.scena_ai_security_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scena.ink
            )
        }
    }
}

/** Shown instead of the key panel for a provider whose [AiProvider.requiresApiKey] is false. */
@Composable
fun AiKeyNotRequiredNote(modifier: Modifier = Modifier) {
    val scena = MaterialTheme.scena
    Text(
        text = stringResource(R.string.scena_ai_no_key_needed),
        style = MaterialTheme.typography.bodyMedium,
        color = scena.ink2,
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .padding(20.dp)
    )
}

/** The model list is being fetched. */
@Composable
fun AiModelsLoadingCard(modifier: Modifier = Modifier) {
    val scena = MaterialTheme.scena
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = scena.acc,
            strokeWidth = 2.5.dp
        )
        Text(
            text = stringResource(R.string.settings_loading_models),
            style = MaterialTheme.typography.bodyMedium,
            color = scena.ink2
        )
    }
}

/** The model list could not be fetched. [message] is the existing error from the view model. */
@Composable
fun AiModelsErrorCard(message: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = message ?: stringResource(R.string.settings_models_fetch_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scheme.errorContainer)
            .padding(20.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSystemPromptItem(
    systemPrompt: String,
    defaultPrompt: String,
    onSystemPromptSave: (String) -> Unit,
    onReset: () -> Unit,
    title: String,
    subtitle: String
) {
    val scena = MaterialTheme.scena
    var localPrompt by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    val hasChanges = localPrompt != systemPrompt
    val isDefault = systemPrompt == defaultPrompt
    var showSaved by remember { mutableStateOf(false) }
    val presets = listOf(
        stringResource(R.string.settings_preset_professional_curator_name) to
            stringResource(R.string.settings_preset_professional_curator_prompt),
        stringResource(R.string.settings_preset_creative_maverick_name) to
            stringResource(R.string.settings_preset_creative_maverick_prompt),
        stringResource(R.string.settings_preset_strict_librarian_name) to
            stringResource(R.string.settings_preset_strict_librarian_prompt),
        stringResource(R.string.settings_preset_atmospheric_guide_name) to
            stringResource(R.string.settings_preset_atmospheric_guide_prompt),
        stringResource(R.string.settings_preset_sonic_enthusiast_name) to
            stringResource(R.string.settings_preset_sonic_enthusiast_prompt),
        stringResource(R.string.settings_preset_energy_catalyst_name) to
            stringResource(R.string.settings_preset_energy_catalyst_prompt)
    )

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Scena.TileShape)
            .background(scena.bg2)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = scenaWide(18.sp), color = scena.ink)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scena.ink2
            )
        }
        Text(
            text = stringResource(R.string.settings_preset_prompts),
            style = MaterialTheme.typography.labelLarge,
            color = scena.ink
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                AiChip(
                    label = preset.first,
                    selected = localPrompt == preset.second,
                    onClick = { localPrompt = preset.second }
                )
            }
        }
        OutlinedTextField(
            value = localPrompt,
            onValueChange = { localPrompt = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            placeholder = { Text(stringResource(R.string.settings_system_prompt_placeholder)) },
            minLines = 3,
            maxLines = 6,
            shape = Scena.CoverShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scena.acc,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = scena.bg,
                unfocusedContainerColor = scena.bg,
                cursorColor = scena.acc
            )
        )
        if (showSaved) {
            AiKeyStatusLine(
                text = stringResource(R.string.common_saved),
                verified = true,
                failed = false
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiMorphButton(
                label = stringResource(R.string.common_save),
                busy = false,
                enabled = hasChanges,
                onClick = {
                    onSystemPromptSave(localPrompt)
                    showSaved = true
                }
            )
            if (!isDefault) {
                AiSecondaryButton(
                    label = stringResource(R.string.common_reset),
                    enabled = true,
                    onClick = { onReset() }
                )
            }
        }
    }
}
