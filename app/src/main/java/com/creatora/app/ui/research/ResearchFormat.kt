package com.creatora.app.ui.research

import java.util.Locale

/** 1234 -> "1.2K", 3_400_000 -> "3.4M". */
fun compactCount(value: Long): String {
    fun scaled(divisor: Double, suffix: String) = String.format(Locale.US, "%.1f", value / divisor).removeSuffix(".0") + suffix
    return when {
        value >= 1_000_000_000 -> scaled(1e9, "B")
        value >= 1_000_000 -> scaled(1e6, "M")
        value >= 1_000 -> scaled(1e3, "K")
        else -> value.toString()
    }
}

/** 3.456 -> "3.5", 12.0 -> "12". */
fun multipleText(multiple: Double): String =
    if (multiple >= 10) multiple.toLong().toString() else String.format(Locale.US, "%.1f", multiple).removeSuffix(".0")
