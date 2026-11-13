package com.creatora.app.domain.create

import com.creatora.app.domain.ai.parseSections

/** What the AI suggests for the Brief step. [seconds] is null when it gave no usable number. */
data class BriefSuggestion(val angle: String, val seconds: Int?, val tone: String, val audience: String)

/** Reads ANGLE, LENGTH, TONE and AUDIENCE; null when none of them is there. */
fun parseBrief(text: String, maxSeconds: Int): BriefSuggestion? {
    val sections = parseSections(text, listOf("ANGLE", "LENGTH", "TONE", "AUDIENCE"))
    if (sections.isEmpty()) return null
    return BriefSuggestion(
        angle = sections["ANGLE"].orEmpty().trim(),
        seconds = sections["LENGTH"].orEmpty().filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(maxSeconds),
        tone = sections["TONE"].orEmpty().trim(),
        audience = sections["AUDIENCE"].orEmpty().trim(),
    )
}

/** The script's sentences in order, as the editor shows them. */
fun scriptLines(script: String): List<String> = com.creatora.app.domain.render.splitSentences(script)

/** Replaces the first occurrence of [line] in the three script parts; null when it is not found (the user edited it meanwhile). */
fun replaceLine(hook: String, structure: String, cta: String, line: String, replacement: String): Triple<String, String, String>? = when {
    line.isBlank() -> null
    hook.contains(line) -> Triple(hook.replaceFirst(line, replacement), structure, cta)
    structure.contains(line) -> Triple(hook, structure.replaceFirst(line, replacement), cta)
    cta.contains(line) -> Triple(hook, structure, cta.replaceFirst(line, replacement))
    else -> null
}
