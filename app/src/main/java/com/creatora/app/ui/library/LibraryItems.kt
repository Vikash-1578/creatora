package com.creatora.app.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.ui.components.ProjectThumbnail
import com.creatora.app.ui.theme.CreatoraTheme

/** One entry in an item's dropdown menu. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/** The ⋮ button and its dropdown. Cards also open the same menu when tapped. */
@Composable
fun ItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = CreatoraTheme.colors.surface,
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

@Composable
fun ProjectCard(
    item: ProjectItem,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val goesLive = item.goesLiveAt
    Column(
        modifier = modifier.clickable(role = Role.Button) { menuOpen = true },
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(CreatoraTheme.shapes.radius16)
                .background(colors.surfaceAlt)
                .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16),
        ) {
            ProjectThumbnail(item.project.format)
            StatusChip(
                label = stringResource(
                    when {
                        item.project.isReady -> R.string.library_status_ready
                        goesLive != null -> R.string.library_status_scheduled
                        else -> R.string.library_status_draft
                    },
                ),
                color = when {
                    item.project.isReady -> colors.success
                    goesLive != null -> colors.accentText
                    else -> colors.warning
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(CreatoraTheme.spacing.sm),
            )
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = colors.textMuted,
                    )
                }
                ItemMenu(menuOpen, { menuOpen = false }, actions)
            }
        }
        Text(
            text = item.project.title,
            style = CreatoraTheme.typography.bodyBold,
            color = colors.text,
            maxLines = 1,
        )
        Text(
            text = if (goesLive == null) {
                stringResource(R.string.library_project_edited, relativeTime(item.project.updatedAt))
            } else {
                stringResource(R.string.library_project_goes_live, dateTime(context, goesLive))
            },
            style = CreatoraTheme.typography.caption,
            color = colors.textMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = CreatoraTheme.typography.caption,
        color = color,
        modifier = modifier
            .clip(CreatoraTheme.shapes.radius8)
            .background(CreatoraTheme.colors.surface)
            .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
    )
}

/** Dashed "New project" tile from the design. */
@Composable
fun NewProjectTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val dashColor = colors.border
    val shape = CreatoraTheme.shapes.radius16
    val strokeWidth = CreatoraTheme.dimens.borderWidth * 2
    Column(
        modifier = modifier
            .aspectRatio(PREVIEW_RATIO)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(shape.topStart.toPx(size, this)),
                    style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.text)
        Text(
            text = stringResource(R.string.library_new_project),
            style = CreatoraTheme.typography.bodyBold,
            color = colors.text,
            modifier = Modifier.padding(top = CreatoraTheme.spacing.xs),
        )
    }
}

@Composable
fun AssetCard(
    asset: Asset,
    hasLicence: Boolean,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.clickable(role = Role.Button) { menuOpen = true },
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CreatoraTheme.shapes.radius16)
                .background(colors.surfaceAlt)
                .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16),
            contentAlignment = Alignment.Center,
        ) {
            if (asset.type == AssetType.AUDIO) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = colors.textMuted)
            } else {
                AsyncImage(
                    model = asset.uri,
                    contentDescription = asset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                ItemMenu(menuOpen, { menuOpen = false }, actions)
            }
        }
        Text(asset.name, style = CreatoraTheme.typography.bodyBold, color = colors.text, maxLines = 1)
        Text(
            text = stringResource(if (hasLicence) R.string.library_asset_licence_logged else R.string.library_asset_no_licence),
            style = CreatoraTheme.typography.caption,
            color = if (hasLicence) colors.success else colors.warning,
            maxLines = 1,
        )
    }
}

/** A simple row card: title, optional second line, and a ⋮ menu. */
@Composable
fun SimpleItemRow(
    title: String,
    subtitle: String?,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CreatoraTheme.shapes.radius16)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, CreatoraTheme.shapes.radius16)
            .clickable(role = Role.Button) { menuOpen = true }
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .padding(start = CreatoraTheme.spacing.lg, top = CreatoraTheme.spacing.sm, bottom = CreatoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
            Text(title, style = CreatoraTheme.typography.bodyBold, color = colors.text, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = CreatoraTheme.typography.caption, color = colors.textMuted, maxLines = 2)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = colors.textMuted,
                )
            }
            ItemMenu(menuOpen, { menuOpen = false }, actions)
        }
    }
}

private const val PREVIEW_RATIO = 169f / 104f

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun dateTime(context: android.content.Context, millis: Long): String = DateUtils.formatDateTime(
    context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
)
