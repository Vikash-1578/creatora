package com.creatora.app.ui.render

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.render.AspectRatio
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File

/** The render controls on the wizard's Generate step. */
@Composable
fun RenderPanel(
    onOpenLibrary: () -> Unit,
    onOpenThumbnail: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RenderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RenderContent(
        state = state,
        onRatio = viewModel::setRatio,
        onCaptions = viewModel::setCaptions,
        onMusic = viewModel::setMusic,
        onRender = viewModel::render,
        onCancel = viewModel::cancel,
        onSaveToGallery = viewModel::saveToGallery,
        onSaveTo = viewModel::saveTo,
        onOpenLibrary = onOpenLibrary,
        onOpenThumbnail = { state.project?.let { onOpenThumbnail(it.id) } },
        modifier = modifier,
    )
}

@Composable
fun RenderContent(
    state: RenderUiState,
    onRatio: (AspectRatio) -> Unit,
    onCaptions: (Boolean) -> Unit,
    onMusic: (Long?) -> Unit,
    onRender: () -> Unit,
    onCancel: () -> Unit,
    onSaveToGallery: (displayName: String) -> Unit,
    onSaveTo: (destinationUri: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenThumbnail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    val colors = CreatoraTheme.colors
    // Stop the screen from turning off while a render is running.
    val view = LocalView.current
    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (state.footage.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.render_no_footage_title),
                body = stringResource(
                    if (state.project?.licensedFootageOnly == true) R.string.render_no_footage_licensed else R.string.render_no_footage_any,
                ),
                actionLabel = stringResource(R.string.wizard_open_library),
                onAction = onOpenLibrary,
            )
        } else {
            Text(
                stringResource(R.string.render_footage_count, state.footage.size),
                style = CreatoraTheme.typography.body,
                color = colors.textMuted,
            )
        }

        Heading(stringResource(R.string.render_size))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AspectRatio.entries.forEach { ratio ->
                ChoiceChip(stringResource(ratio.labelRes()), state.options.ratio == ratio, { onRatio(ratio) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
                .toggleable(state.options.captions, role = Role.Switch, onValueChange = onCaptions),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.render_captions), style = CreatoraTheme.typography.bodySemiBold, color = colors.text, modifier = Modifier.weight(1f))
            Switch(
                checked = state.options.captions,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = colors.onAccent,
                    uncheckedTrackColor = colors.surfaceAlt,
                    uncheckedThumbColor = colors.textMuted,
                ),
            )
        }

        Heading(stringResource(R.string.render_music))
        if (state.music.isEmpty()) {
            Text(stringResource(R.string.render_music_none_available), style = CreatoraTheme.typography.body, color = colors.textMuted)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ChoiceChip(stringResource(R.string.render_music_off), state.options.musicAssetId == null, { onMusic(null) })
                state.music.forEach { track -> ChoiceChip(track.name, state.options.musicAssetId == track.id, { onMusic(track.id) }) }
            }
        }

        when (val phase = state.phase) {
            is RenderPhase.Narrating -> Progress(
                stringResource(R.string.render_narrating, phase.done + 1, phase.total),
                if (phase.total == 0) 0f else phase.done / phase.total.toFloat(),
            )
            is RenderPhase.Rendering -> Progress(stringResource(R.string.render_rendering, phase.percent), phase.percent / 100f)
            is RenderPhase.Failed -> Text(stringResource(phase.problem.messageRes()), style = CreatoraTheme.typography.bodySemiBold, color = colors.danger)
            else -> Unit
        }

        if (state.busy) {
            SecondaryButton(stringResource(R.string.action_cancel), onCancel)
        } else {
            PrimaryButton(
                text = stringResource(if (state.resultUri == null) R.string.render_start else R.string.render_again),
                onClick = onRender,
                enabled = state.footage.isNotEmpty() && state.project?.script?.isNotBlank() == true,
            )
        }

        state.resultUri?.takeIf { !state.busy }?.let { uri ->
            Heading(stringResource(R.string.render_ready))
            Text(File(uri).name, style = CreatoraTheme.typography.caption, color = colors.textMuted)
            ResultActions(
                fileUri = uri,
                displayName = File(uri).name,
                mimeType = "video/mp4",
                message = state.galleryMessage,
                onSaveToGallery = { onSaveToGallery(File(uri).name) },
                onSaveTo = onSaveTo,
            )
        }

        SecondaryButton(stringResource(R.string.thumbnail_make), onOpenThumbnail)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
}

@Composable
private fun Progress(label: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Text(label, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text)
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = CreatoraTheme.colors.accent,
            trackColor = CreatoraTheme.colors.surfaceAlt,
        )
    }
}

private fun AspectRatio.labelRes() = when (this) {
    AspectRatio.PORTRAIT -> R.string.render_ratio_portrait
    AspectRatio.LANDSCAPE -> R.string.render_ratio_landscape
    AspectRatio.SQUARE -> R.string.render_ratio_square
}

private fun RenderProblem.messageRes() = when (this) {
    RenderProblem.NO_SCRIPT -> R.string.render_error_no_script
    RenderProblem.NO_FOOTAGE -> R.string.render_error_no_footage
    RenderProblem.NO_VOICE -> R.string.render_error_no_voice
    RenderProblem.RENDER_FAILED -> R.string.render_error_failed
}

@Composable
private fun RenderPreview(darkTheme: Boolean, state: RenderUiState) {
    CreatoraTheme(darkTheme = darkTheme) {
        RenderContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

private val PreviewProject = Project(id = 1, title = "Preview", format = VideoFormat.SHORTS_REELS, hook = "A hook.")

@Preview(name = "No footage, light", showBackground = true)
@Composable
private fun RenderEmptyLightPreview() = RenderPreview(false, RenderUiState(project = PreviewProject))

@Preview(name = "Rendering, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RenderRunningDarkPreview() = RenderPreview(true, RenderUiState(project = PreviewProject, phase = RenderPhase.Rendering(40)))
