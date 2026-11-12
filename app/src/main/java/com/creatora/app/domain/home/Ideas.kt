package com.creatora.app.domain.home

import com.creatora.app.domain.ai.parseSections
import com.creatora.app.domain.account.VideoStats

enum class IdeaStatus { NEW, SAVED, DISMISSED, PLANNED, MADE }

/** An idea in the user's cloud idea bank (`users/{uid}/ideas`). [score] is the AI's estimate (0 to 100), not a measurement. */
data class CreatorIdea(
    val id: String = "",
    val title: String,
    val reason: String = "",
    val score: Int? = null,
    val status: IdeaStatus = IdeaStatus.NEW,
    /** Where it came from: daily, trending, research. */
    val source: String = "daily",
    val accountKey: String? = null,
    val day: String = "",
    val createdAt: Long = 0,
    val projectId: Long? = null,
)

data class ParsedIdea(val title: String, val score: Int?, val reason: String)

/** Reads "IDEA: title | SCORE: 82 | WHY: reason" lines; lines without a title are dropped and the list is capped at [max]. */
fun parseDailyIdeas(text: String, max: Int): List<ParsedIdea> =
    text.lines().mapNotNull { raw ->
        val line = raw.trim().trimStart('-', '*', ' ')
        if (!line.startsWith("IDEA:", ignoreCase = true)) return@mapNotNull null
        val parts = line.substringAfter(':').split('|').map { it.trim() }
        val title = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        fun field(name: String) = parts.firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
        ParsedIdea(title, field("SCORE")?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 100), field("WHY").orEmpty())
    }.distinctBy { it.title.lowercase() }.take(max)

// ---------------------------------------------------------------- today's focus

data class FocusTip(val tip: String, val source: String)

/** Reads the reply to the focus prompt (TIP: and SOURCE:); null when there is no tip. */
fun parseFocus(text: String): FocusTip? {
    val sections = parseSections(text, listOf("TIP", "SOURCE"))
    val tip = sections["TIP"].orEmpty().trim()
    return if (tip.isEmpty()) null else FocusTip(tip, sections["SOURCE"].orEmpty().trim())
}

/**
 * The real numbers handed to the AI for today's focus: the five newest videos against the five before them, the best
 * recent video, and the upload rhythm. Null when there are too few videos to say anything honest.
 */
fun focusFacts(videos: List<VideoStats>, nowMillis: Long): String? {
    val sorted = videos.sortedByDescending { parsePublished(it.publishedAt) ?: 0L }
    if (sorted.size < 6) return null
    val last = sorted.take(5)
    val before = sorted.drop(5).take(5)
    val lastAvg = last.map { it.views }.average().toLong()
    val beforeAvg = before.map { it.views }.average().toLong()
    val best = sorted.take(10).maxByOrNull { it.views }!!
    val newest = parsePublished(sorted.first().publishedAt)
    val daysSince = newest?.let { (nowMillis - it) / (24L * 60 * 60 * 1000) }
    return buildString {
        appendLine("Average views of the last ${last.size} videos: $lastAvg")
        appendLine("Average views of the ${before.size} videos before them: $beforeAvg")
        appendLine("Best of the last ${minOf(10, sorted.size)} videos: \"${best.title}\" with ${best.views} views")
        daysSince?.let { appendLine("Days since the newest upload: $it") }
        append("Newest titles: ").append(last.joinToString(" | ") { it.title })
    }
}

// ---------------------------------------------------------------- niche to YouTube category

/** The YouTube video category that fits a niche, when there is an obvious one (for the most popular chart). */
fun categoryForNiche(niche: String): String? {
    val n = niche.lowercase()
    val table = listOf(
        listOf("gaming", "game", "minecraft", "pubg", "free fire") to "20",
        listOf("music", "song", "singing") to "10",
        listOf("education", "study", "exam", "tutorial", "learning") to "27",
        listOf("comedy", "funny", "skit", "prank") to "23",
        listOf("sport", "cricket", "football", "fitness") to "17",
        listOf("news", "politics") to "25",
        listOf("tech", "gadget", "coding", "programming", "science") to "28",
        listOf("vlog", "travel", "lifestyle", "pets", "animal") to "22",
        listOf("how-to", "howto", "diy", "style", "beauty", "fashion", "makeup") to "26",
        listOf("film", "movie", "entertainment", "cartoon", "anime", "story") to "24",
    )
    return table.firstOrNull { (words, _) -> words.any { it in n } }?.second
}
