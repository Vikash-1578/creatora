package com.creatora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCreatoraColors = staticCompositionLocalOf { LightCreatoraColors }
private val LocalCreatoraTypography = staticCompositionLocalOf { CreatoraTypography() }
private val LocalCreatoraShapes = staticCompositionLocalOf { CreatoraShapes() }
private val LocalCreatoraSpacing = staticCompositionLocalOf { CreatoraSpacing() }
private val LocalCreatoraDimens = staticCompositionLocalOf { CreatoraDimens() }

/**
 * Creatora's design system. Uses a fixed custom palette (no Material dynamic colour).
 *
 * @param darkTheme which palette to use. The app passes the user's saved theme setting;
 * previews pass true/false explicitly.
 */
@Composable
fun CreatoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkCreatoraColors else LightCreatoraColors
    val typography = CreatoraTypography()
    val shapes = CreatoraShapes()

    CompositionLocalProvider(
        LocalCreatoraColors provides colors,
        LocalCreatoraTypography provides typography,
        LocalCreatoraShapes provides shapes,
        LocalCreatoraSpacing provides CreatoraSpacing(),
        LocalCreatoraDimens provides CreatoraDimens(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = shapes.radius8,
                small = shapes.radius12,
                medium = shapes.radius16,
                large = shapes.radius20,
                extraLarge = shapes.radius24,
            ),
            content = content,
        )
    }
}

/** Access point for Creatora design tokens, e.g. `CreatoraTheme.colors.accent`. */
object CreatoraTheme {
    val colors: CreatoraColors
        @Composable @ReadOnlyComposable get() = LocalCreatoraColors.current
    val typography: CreatoraTypography
        @Composable @ReadOnlyComposable get() = LocalCreatoraTypography.current
    val shapes: CreatoraShapes
        @Composable @ReadOnlyComposable get() = LocalCreatoraShapes.current
    val spacing: CreatoraSpacing
        @Composable @ReadOnlyComposable get() = LocalCreatoraSpacing.current
    val dimens: CreatoraDimens
        @Composable @ReadOnlyComposable get() = LocalCreatoraDimens.current
}

/** Maps Creatora tokens onto Material 3 slots so built-in components match the design. */
private fun CreatoraColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accentText,
        inversePrimary = accentText,
        secondary = accentText,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = accentText,
        tertiary = accentText,
        onTertiary = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = accentText,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textMuted,
        surfaceTint = NoTint,
        inverseSurface = inverse,
        inverseOnSurface = onInverse,
        error = danger,
        onError = onInverse,
        errorContainer = surfaceAlt,
        onErrorContainer = danger,
        outline = border,
        outlineVariant = border,
        surfaceBright = surface,
        surfaceDim = surfaceAlt,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = navBar,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
    )
}
