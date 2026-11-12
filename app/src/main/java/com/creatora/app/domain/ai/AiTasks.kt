package com.creatora.app.domain.ai

import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.ProfileSource
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

    /** Works out what the creator makes from their channel text. Only the given text is used. */
    suspend fun creatorProfile(samples: List<String>): AiOutcome<CreatorProfile?> =
        ai.complete(
            AiRequest(
                system = "You profile a creator from their channel titles and descriptions. Use only the text given. " +
                    "Reply in exactly this format and nothing else:\n" +
                    "NICHE: a short phrase for the topic they cover\n" +
                    "FORMATS: comma separated video formats they use (for example long videos, shorts)\n" +
                    "LANGUAGE: the language they publish in\n" +
                    "SUMMARY: one sentence about their channel\n" +
                    "If the text is too thin to tell, write NICHE: unknown.",
                messages = listOf(ChatMessage(ChatRole.USER, samples.joinToString("\n---\n").take(SAMPLE_LIMIT))),
            ),
        ).map { parseCreatorProfile(it) }

    /**
     * Explains why each video did worse than the channel's usual. [facts] holds, per video, the real numbers (see
     * [insightFacts]); the AI may only use those and must quote them.
     */
    suspend fun videoInsights(channel: String, facts: String): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You review YouTube videos that got fewer views than the channel's usual. You are given real " +
                    "numbers for each video. Use ONLY those numbers; never invent or estimate any figure. Quote the " +
                    "numbers that support each point. If a metric is marked not available, say it is not available " +
                    "instead of guessing. For each video reply in exactly this format:\n" +
                    "VIDEO: <the video id>\n" +
                    "WHY: two sentences on the likely reasons, citing the numbers\n" +
                    "FIX: two or three concrete changes to try next time\n" +
                    "No other text.",
                messages = listOf(ChatMessage(ChatRole.USER, "Channel: $channel\n\n$facts")),
            ),
        )

    /**
     * The day's ideas for one account, steered by the goal (added by the metered service). Each idea carries an AI
     * estimate of its potential (0 to 100) and a reason that names the real data it rests on.
     */
    suspend fun dailyIdeas(facts: String, count: Int): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You suggest video ideas for one creator. Reply with exactly $count ideas, one per line, in this " +
                    "format and nothing else:\nIDEA: <a specific title-style sentence> | SCORE: <0 to 100, how likely the " +
                    "idea is to do well for this creator> | WHY: <one sentence that cites the data given below>\n" +
                    "Use only the facts given; never invent numbers. The score is an estimate, so keep it modest.",
                messages = listOf(ChatMessage(ChatRole.USER, facts)),
            ),
        )

    /** One concrete tip for today, built only from [facts] (real numbers), quoting them. */
    suspend fun todaysFocus(facts: String): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You give one creator ONE concrete thing to focus on today. Use only the numbers given and quote " +
                    "them. If the numbers do not support a tip, say what is missing. Reply in exactly this format:\n" +
                    "TIP: <two sentences: what to do and why, quoting the numbers>\n" +
                    "SOURCE: <the metrics the tip is based on, with their values>",
                messages = listOf(ChatMessage(ChatRole.USER, facts)),
            ),
        )

    /** Why one video did well or badly, from its real numbers only. */
    suspend fun whyVideo(facts: String): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You explain in three or four sentences why one video did better or worse than the creator's usual. " +
                    "Use ONLY the numbers given and quote them; if a number is marked not available, say so instead of " +
                    "guessing. End with one concrete thing to try next. Plain text.",
                messages = listOf(ChatMessage(ChatRole.USER, facts)),
            ),
        )

    /** A week-by-week plan toward the goal, or this week's re-plan from [progress] (real results). Aims, never promises. */
    suspend fun weeklyPlan(goal: String, progress: String): AiOutcome<String> =
        ai.complete(
            AiRequest(
                system = "You plan a creator's next steps toward their aim. Talk about aims, plans and progress; never promise " +
                    "virality or results. Use only the progress numbers given. Reply with three to five short lines for THIS " +
                    "week: what to publish, what to test, what to change. If progress is behind, say so plainly and kindly.",
                messages = listOf(ChatMessage(ChatRole.USER, "Aim: $goal\nProgress: $progress")),
            ),
        )

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

    companion object {
        const val IDEA_COUNT = 5
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

private const val SAMPLE_LIMIT = 6_000

/** Reads the creator profile reply; null when the niche is missing or "unknown", so nothing made up is stored. */
fun parseCreatorProfile(text: String, now: Long = System.currentTimeMillis()): CreatorProfile? {
    val sections = parseSections(text, listOf("NICHE", "FORMATS", "LANGUAGE", "SUMMARY"))
    val niche = sections["NICHE"].orEmpty().trim()
    if (niche.isEmpty() || niche.equals("unknown", ignoreCase = true)) return null
    return CreatorProfile(
        niche = niche,
        formats = sections["FORMATS"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        language = sections["LANGUAGE"].orEmpty().trim(),
        summary = sections["SUMMARY"].orEmpty().trim(),
        source = ProfileSource.DERIVED,
        updatedAt = now,
    )
}
