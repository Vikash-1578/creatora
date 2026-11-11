package com.creatora.app.ui.thumbnail

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.model.Asset
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.render.ResultActions
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File

@Composable
fun ThumbnailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThumbnailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThumbnailContent(state, onBack, viewModel::setText, viewModel::selectImage, viewModel::create, viewModel::saveToGallery, viewModel::saveTo, modifier)
}

@Composable
fun ThumbnailContent(
    state: ThumbnailUiState,
    onBack: () -> Unit,
    onText: (String) -> Unit,
    onImage: (String?) -> Unit,
    onCreate: () -> Unit,
    onSaveToGallery: (displayName: String) -> Unit,
    onSaveTo: (destinationUri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    SubScreenScaffold(stringResource(R.string.thumbnail_title), onBack, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.thumbnail_text_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.thumbnail_picture), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
            if (state.images.isEmpty()) {
                Text(stringResource(R.string.thumbnail_no_pictures), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ChoiceChip(stringResource(R.string.thumbnail_plain_colour), state.selectedImageUri == null, { onImage(null) })
                state.images.forEach { PictureChoice(it, it.uri == state.selectedImageUri) { onImage(it.uri) } }
            }
            PrimaryButton(stringResource(R.string.thumbnail_create), onCreate, enabled = !state.working && state.text.isNotBlank())
            if (state.working) CircularProgressIndicator(color = CreatoraTheme.colors.accent)
            if (state.failed) Text(stringResource(R.string.thumbnail_failed), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.danger)
            state.resultUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.thumbnail_result_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(THUMBNAIL_RATIO)
                        .clip(CreatoraTheme.shapes.radius16),
                )
                ResultActions(
                    fileUri = uri,
                    displayName = File(uri).name,
                    mimeType = "image/png",
                    message = state.galleryMessage,
                    onSaveToGallery = { onSaveToGallery(File(uri).name) },
                    onSaveTo = onSaveTo,
                )
            }
        }
    }
}

@Composable
private fun PictureChoice(asset: Asset, selected: Boolean, onClick: () -> Unit) {
    val colors = CreatoraTheme.colors
    AsyncImage(
        model = asset.uri,
        contentDescription = asset.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(CreatoraTheme.dimens.minTouchTarget + CreatoraTheme.spacing.xl)
            .clip(CreatoraTheme.shapes.radius12)
            .border(
                if (selected) CreatoraTheme.dimens.borderWidth * 3 else CreatoraTheme.dimens.borderWidth,
                if (selected) colors.accent else colors.border,
                CreatoraTheme.shapes.radius12,
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
    )
}

private const val THUMBNAIL_RATIO = 16f / 9f

@Preview(name = "Light", showBackground = true)
@Composable
private fun ThumbnailLightPreview() {
    CreatoraTheme(darkTheme = false) { ThumbnailContent(ThumbnailUiState(text = "My title"), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThumbnailDarkPreview() {
    CreatoraTheme(darkTheme = true) { ThumbnailContent(ThumbnailUiState(text = "My title", working = true), {}, {}, {}, {}, {}, {}) }
}
