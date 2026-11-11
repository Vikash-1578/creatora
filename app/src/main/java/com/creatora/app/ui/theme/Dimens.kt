package com.creatora.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii. Read via CreatoraTheme.shapes.<size>. */
@Immutable
data class CreatoraShapes(
    val radius8: RoundedCornerShape = RoundedCornerShape(8.dp),
    val radius12: RoundedCornerShape = RoundedCornerShape(12.dp),
    val radius14: RoundedCornerShape = RoundedCornerShape(14.dp),
    val radius16: RoundedCornerShape = RoundedCornerShape(16.dp),
    val radius20: RoundedCornerShape = RoundedCornerShape(20.dp),
    val radius24: RoundedCornerShape = RoundedCornerShape(24.dp),
)

/** Spacing scale. Read via CreatoraTheme.spacing.<size>. */
@Immutable
data class CreatoraSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
)

/** Fixed sizes. Read via CreatoraTheme.dimens.<name>. */
@Immutable
data class CreatoraDimens(
    /** Minimum height/width of anything tappable. */
    val minTouchTarget: Dp = 44.dp,
    val borderWidth: Dp = 1.dp,
)
