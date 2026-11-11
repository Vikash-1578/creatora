package com.creatora.app.ui.themepreview

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creatora.app.R
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.theme.CreatoraTheme

/** Demo screen for checking the design system in light and dark theme. */
@Composable
fun ThemePreviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ThemePreviewViewModel = viewModel(factory = ThemePreviewViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    ThemePreviewContent(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        modifier = modifier,
    )
}

@Composable
fun ThemePreviewContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val type = CreatoraTheme.typography
    val spacing = CreatoraTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.text,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = spacing.lg, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(R.string.theme_preview_title),
                    style = type.screenTitle,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.theme_preview_subtitle),
                    style = type.body,
                    color = colors.textMuted,
                )
            }

            AppearanceSection(themeMode = themeMode, onThemeModeSelected = onThemeModeSelected)

            SampleCard()

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CreatoraTheme.dimens.minTouchTarget),
                shape = CreatoraTheme.shapes.radius12,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(text = stringResource(R.string.theme_preview_primary_button), style = type.bodySemiBold)
            }

            Text(
                text = stringResource(R.string.theme_preview_muted_text),
                style = type.caption,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val spacing = CreatoraTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.theme_preview_appearance),
            style = CreatoraTheme.typography.sectionTitle,
            color = CreatoraTheme.colors.text,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeOptionButton(
                    label = mode.labelRes(),
                    selected = mode == themeMode,
                    onClick = { onThemeModeSelected(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
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

@Composable
private fun SampleCard() {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.theme_preview_card_title),
            style = CreatoraTheme.typography.cardTitle,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.theme_preview_card_body),
            style = CreatoraTheme.typography.body,
            color = colors.textMuted,
        )
        Row(
            modifier = Modifier.padding(top = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            StatusChip(R.string.theme_preview_status_success, colors.success)
            StatusChip(R.string.theme_preview_status_warning, colors.warning)
            StatusChip(R.string.theme_preview_status_danger, colors.danger)
        }
        Text(
            text = stringResource(R.string.theme_preview_accent_text),
            style = CreatoraTheme.typography.bodySemiBold,
            color = colors.accentText,
            modifier = Modifier
                .clip(CreatoraTheme.shapes.radius8)
                .background(colors.accentSoft)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        )
    }
}

@Composable
private fun StatusChip(@StringRes label: Int, dotColor: Color) {
    val spacing = CreatoraTheme.spacing
    Row(
        modifier = Modifier
            .clip(CreatoraTheme.shapes.radius20)
            .background(CreatoraTheme.colors.surfaceAlt)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(spacing.sm)
                .clip(CreatoraTheme.shapes.radius8)
                .background(dotColor),
        )
        Text(
            text = stringResource(label),
            style = CreatoraTheme.typography.caption,
            color = dotColor,
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
private fun ThemePreviewLightPreview() {
    CreatoraTheme(darkTheme = false) {
        ThemePreviewContent(themeMode = ThemeMode.LIGHT, onThemeModeSelected = {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThemePreviewDarkPreview() {
    CreatoraTheme(darkTheme = true) {
        ThemePreviewContent(themeMode = ThemeMode.DARK, onThemeModeSelected = {})
    }
}
