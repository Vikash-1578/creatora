package com.creatora.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.creatora.app.R

/** Headings: Plus Jakarta Sans Bold. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** Body text: Inter Regular / SemiBold / Bold. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Creatora type scale. Read via CreatoraTheme.typography.<style>. */
@Immutable
data class CreatoraTypography(
    val screenTitle: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    val cardTitle: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    val bodySemiBold: TextStyle = body.copy(fontWeight = FontWeight.SemiBold),
    val bodyBold: TextStyle = body.copy(fontWeight = FontWeight.Bold),
    val caption: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    val tabLabel: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/** Maps the Creatora scale onto Material 3 slots so built-in components use our fonts. */
internal fun CreatoraTypography.toMaterialTypography(): Typography = Typography(
    headlineLarge = screenTitle,
    headlineMedium = screenTitle,
    headlineSmall = screenTitle,
    titleLarge = sectionTitle,
    titleMedium = cardTitle,
    titleSmall = bodySemiBold,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = bodySemiBold,
    labelMedium = caption.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = tabLabel,
)
