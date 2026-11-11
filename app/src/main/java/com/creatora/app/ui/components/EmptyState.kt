package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.creatora.app.ui.theme.CreatoraTheme

/** Shown when a list has nothing in it yet: what goes here and the action that adds it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val spacing = CreatoraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(CreatoraTheme.dimens.minTouchTarget + spacing.lg)
                .clip(CircleShape)
                .background(CreatoraTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = CreatoraTheme.colors.accentText)
        }
        Text(
            text = title,
            style = CreatoraTheme.typography.cardTitle,
            color = CreatoraTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = CreatoraTheme.typography.body,
            color = CreatoraTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = spacing.sm))
        }
    }
}

/** Filled accent button, at least 44dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget),
        shape = CreatoraTheme.shapes.radius12,
        colors = ButtonDefaults.buttonColors(
            containerColor = CreatoraTheme.colors.accent,
            contentColor = CreatoraTheme.colors.onAccent,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = CreatoraTheme.spacing.sm))
        }
        Text(text, style = CreatoraTheme.typography.bodySemiBold)
    }
}
