package com.creatora.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme

fun AiProvider.nameRes(): Int = when (this) {
    AiProvider.GEMINI -> R.string.ai_provider_gemini
    AiProvider.GROQ -> R.string.ai_provider_groq
    AiProvider.OPENROUTER -> R.string.ai_provider_openrouter
}

fun AiProvider.hintRes(): Int = when (this) {
    AiProvider.GEMINI -> R.string.ai_provider_gemini_hint
    AiProvider.GROQ -> R.string.ai_provider_groq_hint
    AiProvider.OPENROUTER -> R.string.ai_provider_openrouter_hint
}

/** True when the fix is in Settings > AI providers: no key yet, or a key that was rejected. */
fun List<AiError>.needsKey(): Boolean = any { it is AiError.NoKey || it is AiError.InvalidKey }

/** One plain sentence per error, naming the provider. */
@Composable
fun AiError.message(): String = when (this) {
    AiError.NoKey -> stringResource(R.string.ai_error_no_key)
    is AiError.InvalidKey -> stringResource(R.string.ai_error_invalid_key, stringResource(provider.nameRes()))
    is AiError.RateLimited -> stringResource(R.string.ai_error_rate_limited, stringResource(provider.nameRes()))
    is AiError.Network -> stringResource(R.string.ai_error_network, stringResource(provider.nameRes()))
    is AiError.Server -> stringResource(R.string.ai_error_server, stringResource(provider.nameRes()), code)
    is AiError.BadRequest -> stringResource(R.string.ai_error_bad_request, stringResource(provider.nameRes()))
    is AiError.BadResponse -> stringResource(R.string.ai_error_bad_response, stringResource(provider.nameRes()))
}

/** The errors of a failed AI call, with a button to Settings when a key is the problem. */
@Composable
fun AiErrors(
    errors: List<AiError>,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        errors.forEach {
            Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)
        }
        if (errors.needsKey()) {
            SecondaryButton(stringResource(R.string.ai_open_providers), onOpenProviders)
        }
    }
}
