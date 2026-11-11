package com.creatora.app.domain.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Average speaking pace used for reading time and video length. */
const val WORDS_PER_MINUTE = 150

fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

fun readingSeconds(words: Int): Int = (words * 60f / WORDS_PER_MINUTE).roundToInt()

/**
 * Applies pronunciation notes to [text] so the voice says them as written. Each note line is
 * "word = say it like this"; whole words only, ignoring case. Lines without "=" are skipped.
 */
fun applyPronunciations(text: String, notes: String): String =
    notes.lines().fold(text) { result, line ->
        val word = line.substringBefore('=', "").trim()
        val spoken = line.substringAfter('=', "").trim()
        if (word.isEmpty() || spoken.isEmpty()) {
            result
        } else {
            result.replace(Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)) { spoken }
        }
    }

/** Rough output of a render, shown in the Review step. Estimates only. */
data class RenderEstimate(val spokenSeconds: Int, val approxSizeMb: Int, val approxRenderMinutes: Int)

// shortcut: typical H.264 1080p phone render (~8 Mbit/s video + audio) at about 1.5x real time.
// Replace with measured values once the M8 renderer exists.
private const val MB_PER_SECOND = 1.0
private const val RENDER_TIME_FACTOR = 1.5

fun estimateRender(spokenSeconds: Int): RenderEstimate = RenderEstimate(
    spokenSeconds = spokenSeconds,
    approxSizeMb = ceil(spokenSeconds * MB_PER_SECOND).toInt(),
    approxRenderMinutes = if (spokenSeconds == 0) 0 else ceil(spokenSeconds * RENDER_TIME_FACTOR / 60).toInt().coerceAtLeast(1),
)
