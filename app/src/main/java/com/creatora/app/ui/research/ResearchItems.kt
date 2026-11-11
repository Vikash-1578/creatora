package com.creatora.app.ui.research

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.research.Competition
import com.creatora.app.domain.research.Outlier
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.ui.library.ItemMenu
import com.creatora.app.ui.library.MenuAction
import com.creatora.app.ui.theme.CreatoraTheme

private const val THUMBNAIL_RATIO = 16f / 9f

/** The ⋮ menu every video offers. */
@Composable
fun videoActions(video: YtVideo, onSwipe: () -> Unit, onIdea: () -> Unit): List<MenuAction> {
    val context = LocalContext.current
    return listOf(
        MenuAction(stringResource(R.string.research_save_swipe)) { onSwipe() },
        MenuAction(stringResource(R.string.research_save_idea)) { onIdea() },
        MenuAction(stringResource(R.string.research_open_youtube)) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
        },
    )
}

@Composable
fun VideoRow(video: YtVideo, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CreatoraTheme.shapes.radius16)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16)
            .clickable(role = Role.Button) { menuOpen = true }
            .padding(CreatoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        Thumbnail(video.thumbnailUrl, Modifier.weight(0.4f))
        Column(Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
            Text(video.title, style = CreatoraTheme.typography.bodyBold, color = colors.text, maxLines = 2)
            Text(video.channelTitle, style = CreatoraTheme.typography.caption, color = colors.textMuted, maxLines = 1)
            Text(stringResource(R.string.research_views, compactCount(video.views)), style = CreatoraTheme.typography.caption, color = colors.accentText)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more), tint = colors.textMuted)
            }
            ItemMenu(menuOpen, { menuOpen = false }, actions)
        }
    }
}

/** An outlier: thumbnail with "N.N× usual views", title and channel. */
@Composable
fun OutlierCard(outlier: Outlier, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(CreatoraTheme.shapes.radius16)
            .clickable(role = Role.Button) { menuOpen = true },
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Box {
            Thumbnail(outlier.video.thumbnailUrl, Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.research_times_usual, multipleText(outlier.multiple)),
                style = CreatoraTheme.typography.caption,
                color = colors.accentText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(CreatoraTheme.spacing.sm)
                    .clip(CreatoraTheme.shapes.radius20)
                    .background(colors.surface)
                    .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
            )
            Box(Modifier.align(Alignment.TopEnd)) { ItemMenu(menuOpen, { menuOpen = false }, actions) }
        }
        Text(outlier.video.title, style = CreatoraTheme.typography.bodyBold, color = colors.text, maxLines = 2)
        Text(
            stringResource(R.string.research_outlier_detail, outlier.video.channelTitle, compactCount(outlier.video.views), compactCount(outlier.channelAverage)),
            style = CreatoraTheme.typography.caption,
            color = colors.textMuted,
            maxLines = 2,
        )
    }
}

@Composable
private fun Thumbnail(url: String?, modifier: Modifier) {
    val shape = CreatoraTheme.shapes.radius12
    Box(
        modifier = modifier
            .aspectRatio(THUMBNAIL_RATIO)
            .clip(shape)
            .background(CreatoraTheme.colors.surfaceAlt),
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun CompetitionTag(competition: Competition) {
    val colors = CreatoraTheme.colors
    val (label, color) = when (competition) {
        Competition.LOW -> R.string.research_competition_low to colors.success
        Competition.MEDIUM -> R.string.research_competition_medium to colors.warning
        Competition.HIGH -> R.string.research_competition_high to colors.danger
    }
    Text(
        text = stringResource(label),
        style = CreatoraTheme.typography.caption,
        color = color,
        modifier = Modifier
            .clip(CreatoraTheme.shapes.radius8)
            .background(colors.surfaceAlt)
            .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
    )
}
