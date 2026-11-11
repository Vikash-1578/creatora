package com.creatora.app.ui.coach

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun CoachScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CoachContent(state, onBack, onOpenProviders, viewModel::send, viewModel::retry, modifier)
}

@Composable
fun CoachContent(
    state: CoachUiState,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.sending, state.errors) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back), onBack, outlined = false)
            Text(
                stringResource(R.string.coach_title),
                style = CreatoraTheme.typography.screenTitle,
                color = CreatoraTheme.colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.coach_empty_title),
                        body = stringResource(R.string.coach_empty_body),
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        listOf(R.string.coach_prompt_ideas, R.string.coach_prompt_hook, R.string.coach_prompt_grow).forEach { prompt ->
                            val text = stringResource(prompt)
                            ChoiceChip(text, selected = false, onClick = { onSend(text) })
                        }
                    }
                }
            }
            items(state.messages) { Bubble(it) }
            if (state.sending) {
                item { CircularProgressIndicator(color = CreatoraTheme.colors.accent, modifier = Modifier.padding(spacing.sm)) }
            }
            state.errors?.let { errors ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        AiErrors(errors, onOpenProviders)
                        if (!errors.any { it is AiError.NoKey }) SecondaryButton(stringResource(R.string.coach_retry), onRetry)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.coach_input_label)) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            HeaderIconButton(
                icon = Icons.AutoMirrored.Outlined.Send,
                contentDescription = stringResource(R.string.coach_send),
                onClick = {
                    onSend(input)
                    input = ""
                },
            )
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val colors = CreatoraTheme.colors
    val mine = message.role == ChatRole.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.text,
            style = CreatoraTheme.typography.body,
            color = if (mine) colors.onAccent else colors.text,
            modifier = Modifier
                .widthIn(max = CreatoraTheme.dimens.chatBubbleMaxWidth)
                .clip(CreatoraTheme.shapes.radius16)
                .background(if (mine) colors.accent else colors.surface)
                .padding(CreatoraTheme.spacing.md),
        )
    }
}

private val PreviewState = CoachUiState(
    messages = listOf(ChatMessage(ChatRole.USER, "How do I start a channel?"), ChatMessage(ChatRole.ASSISTANT, "Pick one topic and post weekly.")),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun CoachLightPreview() {
    CreatoraTheme(darkTheme = false) { CoachContent(PreviewState, {}, {}, {}, {}) }
}

@Preview(name = "Dark, no key", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CoachDarkPreview() {
    CreatoraTheme(darkTheme = true) { CoachContent(CoachUiState(errors = listOf(AiError.NoKey)), {}, {}, {}, {}) }
}
