package com.creatora.app.domain.model

private val HEX_COLOR = Regex("^#?[0-9A-Fa-f]{6}$")

/** Returns [text] as "#RRGGBB" in upper case, or null if it isn't a six-digit hex colour. */
fun normalizeHexColor(text: String): String? {
    val trimmed = text.trim()
    return if (HEX_COLOR.matches(trimmed)) "#" + trimmed.removePrefix("#").uppercase() else null
}
