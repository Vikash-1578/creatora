package com.creatora.app.ui.home

import java.text.NumberFormat
import java.util.Locale

/** 1,234 becomes 1.2K, 1,500,000 becomes 1.5M. Small numbers stay whole. */
fun compactCount(value: Long, locale: Locale = Locale.US): String {
    fun scaled(divisor: Double, suffix: String) = String.format(locale, "%.1f", value / divisor).removeSuffix(".0").removeSuffix(",0") + suffix
    return when {
        value >= 1_000_000_000 -> scaled(1e9, "B")
        value >= 1_000_000 -> scaled(1e6, "M")
        value >= 10_000 -> scaled(1e3, "K")
        else -> NumberFormat.getIntegerInstance(locale).format(value)
    }
}

/** A change as +1.2K or -300; zero is "0". */
fun signedCompact(value: Long, locale: Locale = Locale.US): String =
    when {
        value > 0 -> "+" + compactCount(value, locale)
        value < 0 -> "-" + compactCount(-value, locale)
        else -> "0"
    }

fun percentText(percent: Double, locale: Locale = Locale.US): String =
    String.format(locale, "%+.0f%%", percent)
