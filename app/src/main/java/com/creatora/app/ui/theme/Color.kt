package com.creatora.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// The ONLY file where raw colour values are allowed.
// Screens read colours through CreatoraTheme.colors.<token>.

/** Semantic colour tokens for Creatora. Every token exists in both the light and dark palette. */
@Immutable
data class CreatoraColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    /** Content (text/icons) drawn on top of [accent], e.g. primary button labels. */
    val onAccent: Color,
    val navBar: Color,
    val inverse: Color,
    val onInverse: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val chartBar: Color,
    val isDark: Boolean,
)

internal val LightCreatoraColors = CreatoraColors(
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF0F6),
    border = Color(0xFFE1E4EE),
    text = Color(0xFF14151A),
    textMuted = Color(0xFF5A6072),
    accent = Color(0xFF5B47F0),
    accentText = Color(0xFF4A37D6),
    accentSoft = Color(0xFFECEAFE),
    onAccent = Color(0xFFFFFFFF),
    navBar = Color(0xFFFFFFFF),
    inverse = Color(0xFF14151A),
    onInverse = Color(0xFFFFFFFF),
    success = Color(0xFF15803D),
    warning = Color(0xFFA16207),
    danger = Color(0xFFB91C1C),
    chartBar = Color(0xFFD5D8E6),
    isDark = false,
)

internal val DarkCreatoraColors = CreatoraColors(
    background = Color(0xFF0E0F14),
    surface = Color(0xFF181A22),
    surfaceAlt = Color(0xFF22252F),
    border = Color(0xFF2A2E3B),
    text = Color(0xFFF4F5F7),
    textMuted = Color(0xFFA3A8B8),
    accent = Color(0xFF6D5BF6),
    accentText = Color(0xFFA99BFF),
    accentSoft = Color(0xFF1D1B33),
    onAccent = Color(0xFFFFFFFF),
    navBar = Color(0xFF14161D),
    inverse = Color(0xFFF4F5F7),
    onInverse = Color(0xFF0E0F14),
    success = Color(0xFF6EE7A0),
    warning = Color(0xFFFCD34D),
    danger = Color(0xFFFCA5A5),
    chartBar = Color(0xFF343849),
    isDark = true,
)

internal val NoTint = Color.Transparent
