package com.creatora.app.ui.create

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.creatora.app.R
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

fun VideoFormat.titleRes(): Int = when (this) {
    VideoFormat.LONG_VIDEO -> R.string.create_format_long
    VideoFormat.SHORTS_REELS -> R.string.create_format_shorts
    VideoFormat.CARTOON_STORY -> R.string.create_format_cartoon
    VideoFormat.VIDEO_AD -> R.string.create_format_ad
    VideoFormat.AVATAR_VIDEO -> R.string.create_format_avatar
    VideoFormat.POST_CAROUSEL -> R.string.create_format_post
}

private fun VideoFormat.subtitleRes(): Int = when (this) {
    VideoFormat.LONG_VIDEO -> R.string.create_format_long_hint
    VideoFormat.SHORTS_REELS -> R.string.create_format_shorts_hint
    VideoFormat.CARTOON_STORY -> R.string.create_format_cartoon_hint
    VideoFormat.VIDEO_AD -> R.string.create_format_ad_hint
    VideoFormat.AVATAR_VIDEO -> R.string.create_format_avatar_hint
    VideoFormat.POST_CAROUSEL -> R.string.create_format_post_hint
}

private fun VideoFormat.icon(): ImageVector = when (this) {
    VideoFormat.LONG_VIDEO -> Icons.Outlined.Movie
    VideoFormat.SHORTS_REELS -> Icons.Outlined.PhoneAndroid
    VideoFormat.CARTOON_STORY -> Icons.Outlined.SentimentSatisfied
    VideoFormat.VIDEO_AD -> Icons.Outlined.Campaign
    VideoFormat.AVATAR_VIDEO -> Icons.Outlined.Person
    VideoFormat.POST_CAROUSEL -> Icons.Outlined.ViewCarousel
}

@Composable
fun CreateScreen(
    onProjectStarted: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable { mutableStateOf(VideoFormat.LONG_VIDEO) }
    val title = stringResource(R.string.create_default_title, stringResource(selected.titleRes()))
    CreateContent(
        selected = selected,
        onSelect = { selected = it },
        onStart = { viewModel.start(selected, title, onProjectStarted) },
        modifier = modifier,
    )
}

@Composable
fun CreateContent(
    selected: VideoFormat,
    onSelect: (VideoFormat) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_create),
            subtitle = stringResource(R.string.create_subtitle),
        )
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                VideoFormat.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        pair.forEach { format ->
                            FormatTile(format, format == selected, { onSelect(format) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            StepsCard()
            PrimaryButton(
                text = stringResource(R.string.create_start),
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.lg),
            )
        }
    }
}

@Composable
private fun FormatTile(format: VideoFormat, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(
                if (selected) CreatoraTheme.dimens.borderWidth * 2 else CreatoraTheme.dimens.borderWidth,
                if (selected) colors.accent else colors.border,
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CreatoraTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(CreatoraTheme.dimens.icon + CreatoraTheme.spacing.md)
                .clip(CreatoraTheme.shapes.radius12)
                .background(if (selected) colors.accent else colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(format.icon(), contentDescription = null, tint = if (selected) colors.onAccent else colors.accentText)
        }
        Text(stringResource(format.titleRes()), style = CreatoraTheme.typography.cardTitle, color = colors.text)
        Text(stringResource(format.subtitleRes()), style = CreatoraTheme.typography.caption, color = colors.textMuted)
    }
}

@Composable
private fun StepsCard() {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.create_steps_title, WizardStep.entries.size),
                style = CreatoraTheme.typography.cardTitle,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.create_steps_note), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        }
        ProgressSegments(current = 0, total = WizardStep.entries.size, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.create_steps_list), style = CreatoraTheme.typography.body, color = colors.textMuted)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CreateLightPreview() {
    CreatoraTheme(darkTheme = false) { CreateContent(VideoFormat.LONG_VIDEO, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateDarkPreview() {
    CreatoraTheme(darkTheme = true) { CreateContent(VideoFormat.SHORTS_REELS, {}, {}) }
}
