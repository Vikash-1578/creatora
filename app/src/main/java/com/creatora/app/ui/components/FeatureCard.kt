package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.creatora.app.ui.theme.CreatoraTheme

/**
 * A section of a tab: icon, title, what it does, and one clear action. Used where a feature has no
 * data yet, so the screen says what is missing and how to fix it instead of showing blank space.
 */
@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(
                modifier = Modifier
                    .size(CreatoraTheme.dimens.icon + spacing.md)
                    .clip(CreatoraTheme.shapes.radius12)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.accentText)
            }
            Text(title, style = CreatoraTheme.typography.cardTitle, color = colors.text)
        }
        Text(body, style = CreatoraTheme.typography.body, color = colors.textMuted)
        SecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = spacing.xs))
    }
}
