package com.creatora.app.domain.ai

import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.domain.model.PublishText
import com.creatora.app.domain.model.ScriptDraft
import com.creatora.app.domain.model.map
import com.creatora.app.domain.repository.AiService
import javax.inject.Inject

/**
 * What Creatora asks the AI to do. Replies are plain text with simple markers (HOOK:, TITLES:, ...)
 * that the parsers below read; models follow that more reliably than strict JSON.
 */
class AiTasks @Inject constructor(private val ai: AiService) {

    suspend fun coach(history: List<ChatMessage>): AiOutcome<String> =
        ai.complete(AiRequest(COACH_SYSTEM, history))

    suspend fun ideas(topic: String, count: Int = IDEA_COUNT): AiOutcome<List<String>> =
        ai.complete(
            AiRequest(
                system = "You suggest video ideas for creators. Reply with exactly $count ideas, one per line, " +
                    "each starting with \"- \". Each idea is a specific title-style sentence. No other text.",
                messages = listOf(ChatMessage(ChatRole.USER, "Topic or niche: ${topic.ifBlank { "anything that is trending" }}")),
            ),
        ).map { parseIdeas(it, count) }

    suspend fun scriptDraft(title: String, brief: String, format: String): AiOutcome<ScriptDraft> =
        ai.complete(
            AiRequest(
                system = "You write scripts for creators. Reply in exactly this format and nothing else:\n" +
                    "HOOK: the opening lines that make people stay\n" +
                    "STRUCTURE: the main points in the order they are spoken\n" +
                    "CTA: one closing call to action\n" +
                    "Write plain spoken sentences, no stage directions, no markdown.",
                messages = listOf(ChatMessage(ChatRole.USER, "Format: $format\nTitle: $title\nBrief: $brief")),
            ),
        ).map { parseScript(it) }

    suspend fun publishText(title: String, script: String): AiOutcome<PublishText> =
        ai.complete(
            AiRequest(
                system = "You write titles and descriptions for videos. Reply in exactly this format and nothing else:\n" +
                    "TITLES: five title options, one per line starting with \"- \"\n" +
                    "DESCRIPTION: a short description\n" +
                    "HASHTAGS: up to eight hashtags separated by spaces\n" +
                    "CHAPTERS: chapter lines like \"00:00 Intro\", one per line, estimated from the script",
                messages = listOf(ChatMessage(ChatRole.USER, "Working title: $title\nScript:\n$script")),
            ),
        ).map { parsePublishText(it) }

    /** Plain-language notes on why a video works and what to borrow from its style. */
    suspend fun styleNotes(title: String, channel: String, description: String): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You analyse the style of online videos for creators. Using only the title, channel and description given, " +
                    "write 4 short bullet points (each starting with \"- \"): the hook style, how the title makes people click, " +
                    "the likely structure, and one idea to borrow. Say so if the description is too short to tell.",
                messages = listOf(ChatMessage(ChatRole.USER, "Title: $title\nChannel: $channel\nDescription: ${description.take(DESCRIPTION_LIMIT)}")),
            ),
        )

    companion object {
        const val IDEA_COUNT = 5
        const val DESCRIPTION_LIMIT = 1_500
        const val COACH_SYSTEM =
            "You are Creatora's AI Coach, a friendly and practical coach for people who make videos and posts. " +
                "Give short, concrete advice: hooks, structure, titles, posting and growth. Ask one question if you need more detail."
    }
}

private val BULLET = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+")

/** Ideas from list-style text: bullets or numbers first, otherwise one idea per non-empty line. */
fun parseIdeas(text: String, max: Int): List<String> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullets = lines.filter { BULLET.containsMatchIn(it) }
    return (bullets.ifEmpty { lines }).map { it.replace(BULLET, "").trim('*', '"', ' ') }
        .filter { it.isNotEmpty() }.distinct().take(max)
}

/**
 * Splits [text] at lines that start with one of [markers] followed by a colon (case-insensitive,
 * ignoring markdown bold). Returns marker (upper case) to its text. Text before the first marker is dropped.
 */
fun parseSections(text: String, markers: List<String>): Map<String, String> {
    val markerLine = Regex("^[\\s*#_]*(${markers.joinToString("|") { Regex.escape(it) }})[\\s*_]*:[\\s*_]*(.*)$", RegexOption.IGNORE_CASE)
    val sections = linkedMapOf<String, StringBuilder>()
    var current: StringBuilder? = null
    for (line in text.lines()) {
        val match = markerLine.matchEntire(line)
        if (match != null) {
            current = StringBuilder(match.groupValues[2]).also { sections[match.groupValues[1].uppercase()] = it }
        } else {
            current?.append('\n')?.append(line)
        }
    }
    return sections.mapValues { it.value.toString().trim() }
}

fun parseScript(text: String): ScriptDraft {
    val sections = parseSections(text, listOf("HOOK", "STRUCTURE", "CTA"))
    // No markers at all: treat the whole reply as the body so nothing is lost.
    if (sections.isEmpty()) return ScriptDraft("", text.trim(), "")
    return ScriptDraft(sections["HOOK"].orEmpty(), sections["STRUCTURE"].orEmpty(), sections["CTA"].orEmpty())
}

fun parsePublishText(text: String): PublishText {
    val sections = parseSections(text, listOf("TITLES", "DESCRIPTION", "HASHTAGS", "CHAPTERS"))
    return PublishText(
        titles = parseIdeas(sections["TITLES"].orEmpty(), max = 10),
        description = sections["DESCRIPTION"].orEmpty(),
        hashtags = sections["HASHTAGS"].orEmpty().split(Regex("[\\s,]+")).filter { it.startsWith("#") && it.length > 1 }.distinct(),
        chapters = sections["CHAPTERS"].orEmpty().lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() },
    )
}
