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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    regionViewModel: RegionViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by languageViewModel.language.collectAsStateWithLifecycle()
    val region by regionViewModel.region.collectAsStateWithLifecycle()
    AppearanceContent(
        themeMode = themeMode,
        onThemeMode = viewModel::onThemeModeSelected,
        language = language,
        onLanguage = languageViewModel::choose,
        region = region,
        onRegion = regionViewModel::select,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun AppearanceContent(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    language: AppLanguage?,
    onLanguage: (AppLanguage) -> Unit,
    region: String = DEFAULT_REGION,
    onRegion: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(title = stringResource(R.string.settings_appearance_language), onBack = onBack, modifier = modifier) {
        Column {
            SectionTitle(text = stringResource(R.string.settings_appearance))
            Text(
                text = stringResource(R.string.settings_appearance_description),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = CreatoraTheme.spacing.md),
            )
            ThemeModeSelector(selected = themeMode, onSelected = onThemeMode)
        }
        Column {
            SectionTitle(text = stringResource(R.string.settings_language))
            Text(
                text = stringResource(R.string.settings_language_description),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = CreatoraTheme.spacing.md),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                AppLanguage.entries.forEach { entry ->
                    val label = when (entry) {
                        AppLanguage.ENGLISH -> R.string.language_english
                        AppLanguage.HINDI -> R.string.language_hindi
                        AppLanguage.HINGLISH -> R.string.language_hinglish
                    }
                    ChoiceChip(stringResource(label), entry == language, { onLanguage(entry) })
                }
            }
        }
        Column {
            SectionTitle(text = stringResource(R.string.settings_region))
            Text(
                text = stringResource(R.string.settings_region_description),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = CreatoraTheme.spacing.md),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
            ) {
                REGION_OPTIONS.forEach { option ->
                    ChoiceChip(
                        label = stringResource(option.label),
                        selected = region == option.code,
                        onClick = { onRegion(option.code) },
                    )
                }
            }
        }
    }
}

private data class RegionOption(val code: String, @StringRes val label: Int)

private val REGION_OPTIONS = listOf(
    RegionOption("IN", R.string.region_india),
    RegionOption("US", R.string.region_united_states),
    RegionOption("GB", R.string.region_united_kingdom),
    RegionOption("AU", R.string.region_australia),
)

/** Segmented System / Light / Dark choice. */
@Composable
internal fun ThemeModeSelector(
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
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppearanceLightPreview() {
    CreatoraTheme(darkTheme = false) {
        AppearanceContent(ThemeMode.LIGHT, {}, AppLanguage.ENGLISH, {}, DEFAULT_REGION, {}, {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearanceDarkPreview() {
    CreatoraTheme(darkTheme = true) {
        AppearanceContent(ThemeMode.DARK, {}, AppLanguage.HINDI, {}, "IN", {}, {})
    }
}
