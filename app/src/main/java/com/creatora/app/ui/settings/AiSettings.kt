package com.creatora.app.ui.settings

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.DeriveResult
import com.creatora.app.domain.repository.AiHistoryEntry
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AiSettingsContent(state, viewModel::setTone, viewModel::setPersonalization, viewModel::refreshProfile, viewModel::clearHistory, onBack, modifier)
}

private fun AiTone.labelRes() = when (this) {
    AiTone.FRIENDLY -> R.string.ai_tone_friendly
    AiTone.PROFESSIONAL -> R.string.ai_tone_professional
    AiTone.FUNNY -> R.string.ai_tone_funny
}

@Composable
fun AiSettingsContent(
    state: AiSettingsUiState,
    onTone: (AiTone) -> Unit,
    onPersonalization: (Boolean) -> Unit,
    onRefreshProfile: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    SubScreenScaffold(title = stringResource(R.string.settings_ai), onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.ai_language))
            Text(
                stringResource(
                    R.string.ai_language_body,
                    stringResource(
                        when (state.language) {
                            AppLanguage.HINDI -> R.string.language_hindi
                            AppLanguage.HINGLISH -> R.string.language_hinglish
                            else -> R.string.language_english
                        },
                    ),
                ),
                style = CreatoraTheme.typography.body, color = colors.textMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.ai_tone))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                AiTone.entries.forEach { ChoiceChip(stringResource(it.labelRes()), it == state.tone, { onTone(it) }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.ai_personalization))
            Row(
                Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Switch) { onPersonalization(!state.personalization) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(stringResource(R.string.ai_personalization_body), style = CreatoraTheme.typography.body, color = colors.text, modifier = Modifier.weight(1f))
                Switch(checked = state.personalization, onCheckedChange = null)
            }
            ProfileCard(state.profile)
            SecondaryButton(stringResource(R.string.ai_profile_refresh), onRefreshProfile, Modifier.fillMaxWidth(), enabled = !state.refreshing)
            state.refreshMessage?.let {
                Text(
                    stringResource(
                        when (it) {
                            is DeriveResult.Updated -> R.string.ai_profile_updated
                            DeriveResult.NothingConnected -> R.string.ai_profile_nothing_connected
                            DeriveResult.NotEnoughData -> R.string.ai_profile_not_enough
                            is DeriveResult.AiFailed -> R.string.ai_profile_failed
                        },
                    ),
                    style = CreatoraTheme.typography.caption, color = colors.textMuted,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.ai_history))
            Text(stringResource(R.string.ai_history_body), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            if (state.history.isEmpty()) {
                Text(stringResource(R.string.ai_history_empty), style = CreatoraTheme.typography.body, color = colors.textMuted)
            } else {
                ListGroup {
                    state.history.forEachIndexed { index, entry ->
                        if (index > 0) ListRowDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md)) {
                            Text(entry.prompt.ifBlank { stringResource(R.string.ai_history_no_text) }, style = CreatoraTheme.typography.body, color = colors.text, maxLines = 2)
                            Text(
                                DateUtils.getRelativeTimeSpanString(entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = CreatoraTheme.typography.caption, color = colors.textMuted,
                            )
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.ai_history_clear), onClearHistory, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: CreatorProfile?) {
    val colors = CreatoraTheme.colors
    if (profile == null) {
        Text(stringResource(R.string.ai_profile_none), style = CreatoraTheme.typography.body, color = colors.textMuted)
        return
    }
    ListGroup {
        Column(Modifier.fillMaxWidth().padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
            Text(profile.niche, style = CreatoraTheme.typography.cardTitle, color = colors.text)
            if (profile.formats.isNotEmpty()) Text(profile.formats.joinToString(), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            if (profile.summary.isNotBlank()) Text(profile.summary, style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AiSettingsLightPreview() {
    CreatoraTheme(darkTheme = false) {
        AiSettingsContent(
            AiSettingsUiState(profile = CreatorProfile("Street food films", listOf("shorts"), "Hindi", "Short films about street food."), history = listOf(AiHistoryEntry("1", "Give me 5 ideas", 0))),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiSettingsDarkPreview() {
    CreatoraTheme(darkTheme = true) { AiSettingsContent(AiSettingsUiState(), {}, {}, {}, {}, {}) }
}
