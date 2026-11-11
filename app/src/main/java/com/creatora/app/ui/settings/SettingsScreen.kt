package com.creatora.app.ui.settings

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.creatora.app.R
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsContent(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column {
            SectionTitle(text = stringResource(R.string.settings_appearance))
            Text(
                text = stringResource(R.string.settings_appearance_description),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = CreatoraTheme.spacing.md),
            )
            ThemeModeSelector(selected = themeMode, onSelected = onThemeModeSelected)
        }
    }
}

/** Segmented System / Light / Dark choice. */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeOptionButton(
                label = mode.labelRes(),
                selected = mode == selected,
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeOptionButton(
    @StringRes label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius12
    Box(
        modifier = modifier
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(
                width = CreatoraTheme.dimens.borderWidth,
                color = if (selected) colors.accent else colors.border,
                shape = shape,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(label),
            style = CreatoraTheme.typography.bodySemiBold,
            color = if (selected) colors.onAccent else colors.text,
        )
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SettingsLightPreview() {
    CreatoraTheme(darkTheme = false) {
        SettingsContent(themeMode = ThemeMode.LIGHT, onThemeModeSelected = {}, onBack = {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    CreatoraTheme(darkTheme = true) {
        SettingsContent(themeMode = ThemeMode.DARK, onThemeModeSelected = {}, onBack = {})
    }
}
