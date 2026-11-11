package com.creatora.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.creatora.app.ui.theme.CreatoraTheme

/** 44dp round icon button used in screen headers and top bars. */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
) {
    val colors = CreatoraTheme.colors
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(CreatoraTheme.dimens.minTouchTarget)
            .then(
                if (outlined) {
                    Modifier.border(CreatoraTheme.dimens.borderWidth, colors.border, CircleShape)
                } else {
                    Modifier
                },
            ),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (outlined) colors.surface else colors.background,
            contentColor = colors.text,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CreatoraTheme.dimens.icon),
        )
    }
}
