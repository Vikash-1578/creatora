package com.creatora.app.ui.ai

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiProviderConfig
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun AiProvidersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiProvidersViewModel = hiltViewModel(),
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val youTubeHasKey by viewModel.youTubeHasKey.collectAsStateWithLifecycle()
    AiProvidersContent(
        configs, youTubeHasKey, onBack, viewModel::save, viewModel::removeKey,
        viewModel::saveYouTubeKey, viewModel::removeYouTubeKey, modifier,
    )
}

@Composable
fun AiProvidersContent(
    configs: List<AiProviderConfig>,
    youTubeHasKey: Boolean,
    onBack: () -> Unit,
    onSave: (AiProvider, key: String, model: String) -> Unit,
    onRemove: (AiProvider) -> Unit,
    onSaveYouTube: (String) -> Unit,
    onRemoveYouTube: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(stringResource(R.string.ai_providers_title), onBack, modifier) {
        Text(stringResource(R.string.ai_providers_intro), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
        configs.forEach { config -> ProviderCard(config, onSave, onRemove) }
        YouTubeKeyCard(youTubeHasKey, onSaveYouTube, onRemoveYouTube)
    }
}

@Composable
private fun YouTubeKeyCard(hasKey: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    var key by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Row {
            Text(stringResource(R.string.youtube_key_title), style = CreatoraTheme.typography.cardTitle, color = colors.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (hasKey) R.string.ai_key_saved else R.string.ai_key_missing),
                style = CreatoraTheme.typography.caption,
                color = if (hasKey) colors.success else colors.textMuted,
            )
        }
        Text(stringResource(R.string.youtube_key_hint), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(if (hasKey) R.string.ai_key_replace_label else R.string.ai_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            PrimaryButton(stringResource(R.string.action_save), { onSave(key); key = "" }, enabled = key.isNotBlank())
            if (hasKey) SecondaryButton(stringResource(R.string.ai_key_remove), onRemove)
        }
    }
}

@Composable
private fun ProviderCard(
    config: AiProviderConfig,
    onSave: (AiProvider, String, String) -> Unit,
    onRemove: (AiProvider) -> Unit,
) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    var key by rememberSaveable(config.provider) { mutableStateOf("") }
    var model by rememberSaveable(config.provider, config.model) { mutableStateOf(config.model) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Row {
            Text(stringResource(config.provider.nameRes()), style = CreatoraTheme.typography.cardTitle, color = colors.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (config.hasKey) R.string.ai_key_saved else R.string.ai_key_missing),
                style = CreatoraTheme.typography.caption,
                color = if (config.hasKey) colors.success else colors.textMuted,
            )
        }
        Text(stringResource(config.provider.hintRes()), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(if (config.hasKey) R.string.ai_key_replace_label else R.string.ai_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.ai_model_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(config.provider, key, model)
                    key = ""
                },
                enabled = key.isNotBlank() || model.trim() != config.model,
            )
            if (config.hasKey) SecondaryButton(stringResource(R.string.ai_key_remove), { onRemove(config.provider) })
        }
    }
}

private val PreviewConfigs = listOf(
    AiProviderConfig(AiProvider.GEMINI, hasKey = true, model = AiProvider.GEMINI.defaultModel),
    AiProviderConfig(AiProvider.GROQ, hasKey = false, model = AiProvider.GROQ.defaultModel),
    AiProviderConfig(AiProvider.OPENROUTER, hasKey = false, model = AiProvider.OPENROUTER.defaultModel),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun AiProvidersLightPreview() {
    CreatoraTheme(darkTheme = false) { AiProvidersContent(PreviewConfigs, true, {}, { _, _, _ -> }, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiProvidersDarkPreview() {
    CreatoraTheme(darkTheme = true) { AiProvidersContent(PreviewConfigs, false, {}, { _, _, _ -> }, {}, {}, {}) }
}
