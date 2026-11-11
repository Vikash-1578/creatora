package com.creatora.app.ui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.creatora.app.R
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.components.InfoListRow
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiProviders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsContent(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        version = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        },
        onBack = onBack,
        onOpenAiProviders = onOpenAiProviders,
        onOpenLanguage = { openSystemSettings(context, languageIntent(context)) },
        onOpenNotifications = {
            openSystemSettings(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = modifier,
    )
}

/** Android 13 and newer have a per-app language screen; older versions only have the device's. */
private fun languageIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
    } else {
        Intent(Settings.ACTION_LOCALE_SETTINGS)
    }

private fun openSystemSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    version: String,
    onBack: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Section(R.string.settings_appearance) {
            Text(
                text = stringResource(R.string.settings_appearance_description),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = CreatoraTheme.spacing.md),
            )
            ThemeModeSelector(selected = themeMode, onSelected = onThemeModeSelected)
        }
        Section(R.string.settings_ai) {
            ListGroup {
                NavigationListRow(
                    title = stringResource(R.string.ai_providers_title),
                    value = stringResource(R.string.ai_providers_row_value),
                    onClick = onOpenAiProviders,
                )
            }
        }
        Section(R.string.settings_language) {
            ListGroup { NavigationListRow(title = stringResource(R.string.settings_language_row), onClick = onOpenLanguage) }
        }
        Section(R.string.settings_notifications) {
            ListGroup { NavigationListRow(title = stringResource(R.string.settings_notifications_row), onClick = onOpenNotifications) }
        }
        Section(R.string.settings_about) {
            ListGroup {
                InfoListRow(stringResource(R.string.settings_version), version.ifEmpty { stringResource(R.string.profile_version_unknown) })
            }
        }
    }
}

/** A section header with its content below it. */
@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionTitle(text = stringResource(title))
        content()
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
        SettingsContent(ThemeMode.LIGHT, {}, "0.1.0", {}, {}, {}, {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    CreatoraTheme(darkTheme = true) {
        SettingsContent(ThemeMode.DARK, {}, "0.1.0", {}, {}, {}, {})
    }
}
