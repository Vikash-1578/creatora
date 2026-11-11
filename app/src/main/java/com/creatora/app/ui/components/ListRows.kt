package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.creatora.app.ui.theme.CreatoraTheme

/** Rounded surface card that groups list rows. Put [ListRowDivider] between rows. */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape),
        content = content,
    )
}

/** Tappable row with a title and a trailing chevron. */
@Composable
fun NavigationListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val spacing = CreatoraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = title,
            style = CreatoraTheme.typography.bodySemiBold,
            color = CreatoraTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = CreatoraTheme.typography.caption,
                color = CreatoraTheme.colors.textMuted,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = CreatoraTheme.colors.textMuted,
            modifier = Modifier.size(CreatoraTheme.dimens.icon),
        )
    }
}

/** Row with a title and a value on the right, for facts that are not tappable. */
@Composable
fun InfoListRow(title: String, value: String, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(title, style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
        Text(value, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
    }
}

@Composable
fun ListRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CreatoraTheme.spacing.lg)
            .height(CreatoraTheme.dimens.borderWidth)
            .background(CreatoraTheme.colors.border),
    )
}

/** Small section label above a [ListGroup], e.g. "Appearance". */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CreatoraTheme.typography.sectionTitle,
        color = CreatoraTheme.colors.text,
        modifier = modifier.padding(bottom = CreatoraTheme.spacing.sm),
    )
}
