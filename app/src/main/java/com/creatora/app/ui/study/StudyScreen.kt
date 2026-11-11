package com.creatora.app.ui.study

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.research.ErrorText
import com.creatora.app.ui.research.compactCount
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun StudyScreen(
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StudyContent(state, onBack, onOpenKeys, viewModel::study, viewModel::save, modifier)
}

@Composable
fun StudyContent(
    state: StudyUiState,
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    onStudy: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var link by rememberSaveable { mutableStateOf("") }
    val colors = CreatoraTheme.colors
    SubScreenScaffold(stringResource(R.string.study_title), onBack, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            Text(stringResource(R.string.study_intro), style = CreatoraTheme.typography.body, color = colors.textMuted)
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.study_link_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(stringResource(R.string.study_button), { onStudy(link) }, enabled = link.isNotBlank() && !state.loading)
            if (state.loading) CircularProgressIndicator(color = colors.accent)
            if (state.notLink) Text(stringResource(R.string.study_not_link), style = CreatoraTheme.typography.bodySemiBold, color = colors.danger)
            if (state.notFound) Text(stringResource(R.string.study_not_found), style = CreatoraTheme.typography.bodySemiBold, color = colors.danger)
            state.youTubeError?.let { ErrorText(it, onOpenKeys) }
            state.video?.let { video ->
                Text(video.title, style = CreatoraTheme.typography.cardTitle, color = colors.text)
                Text(
                    stringResource(R.string.study_video_detail, video.channelTitle, compactCount(video.views)),
                    style = CreatoraTheme.typography.caption,
                    color = colors.textMuted,
                )
            }
            state.notes?.let { Text(it, style = CreatoraTheme.typography.body, color = colors.text) }
            state.aiErrors?.let { AiErrors(it, onOpenKeys) }
            if (state.video != null && !state.loading) {
                if (state.saved) {
                    Text(stringResource(R.string.study_saved), style = CreatoraTheme.typography.bodySemiBold, color = colors.success)
                } else {
                    SecondaryButton(stringResource(R.string.research_save_swipe), onSave)
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun StudyLightPreview() {
    CreatoraTheme(darkTheme = false) {
        StudyContent(StudyUiState(video = YtVideo("a", "A video", "c", "A channel", 5_400), notes = "- Hook: a question."), {}, {}, {}, {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StudyDarkPreview() {
    CreatoraTheme(darkTheme = true) { StudyContent(StudyUiState(notLink = true), {}, {}, {}, {}) }
}
