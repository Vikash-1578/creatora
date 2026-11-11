package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.ui.create.icon
import com.creatora.app.ui.theme.CreatoraTheme

/** Stand-in picture for a project that has no render yet: a soft gradient with its format's icon. */
@Composable
fun ProjectThumbnail(format: VideoFormat?, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(colors.accentSoft, colors.surfaceAlt))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(format?.icon() ?: Icons.Outlined.Movie, contentDescription = null, tint = colors.accentText)
    }
}
