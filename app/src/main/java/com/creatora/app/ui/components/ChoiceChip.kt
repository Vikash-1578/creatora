package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.creatora.app.ui.theme.CreatoraTheme

/** Filter chip from the design: accent when selected, outlined surface otherwise. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius14
    Box(
        modifier = modifier
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, if (selected) colors.accent else colors.border, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = CreatoraTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CreatoraTheme.typography.bodySemiBold,
            color = if (selected) colors.onAccent else colors.text,
        )
    }
}
