package com.creatora.app.domain.research

import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.ai.parseSections
import com.creatora.app.domain.home.parsePublished
import java.util.Locale

private const val DAY = 24L * 60 * 60 * 1000

private fun pct(x: Double?) = x?.let { "%.1f%%".format(Locale.US, it) } ?: "not available"

private fun band(b: AgeBand) = when (b) {
    AgeBand.WEEK -> "0 to 7 days old"
    AgeBand.MONTH -> "8 to 30 days old"
    AgeBand.QUARTER -> "31 to 90 days old"
    AgeBand.OLDER -> "over 90 days old"
}

/** Weekday and hour (UTC) of the upload, as plain words for the AI; the real publish time from the API. */
private fun publishTiming(iso: String): String {
    val millis = parsePublished(iso) ?: return "not available"
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    val day = c.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.LONG, Locale.US)
    return "$day, %02d:00 UTC".format(c.get(java.util.Calendar.HOUR_OF_DAY))
}

/**
 * The real numbers behind one video of the user's own channel, for "why it did well" or "what is not working".
 * Anything the API did not return is marked "not available"; the thumbnail and audience details are not analysed yet.
 */
fun outlierFacts(item: ScoredVideo, analytics: VideoAnalytics?, nowMillis: Long): String {
    val v = item.video
    val days = parsePublished(v.publishedAt)?.let { (nowMillis - it) / DAY }
    val traffic = analytics?.trafficSources?.takeIf { it.isNotEmpty() }?.entries?.sortedByDescending { it.value }?.take(4)
        ?.joinToString { "${it.key} ${it.value}" } ?: "not available"
    return buildString {
        appendLine("Title: ${v.title}")
        appendLine("Views: ${v.views}, which is ${"%.1f".format(Locale.US, item.score)} times the channel's usual (${item.baseline}) for videos ${band(item.band)}" + if (item.ageAdjusted) "" else " (not age-adjusted: too few videos of that age)")
        days?.let { appendLine("Days since upload: $it") }
        appendLine("Published: ${publishTiming(v.publishedAt)}")
        appendLine("Likes: ${v.likes ?: "not available"}")
        appendLine("Comments: ${v.comments ?: "not available"}")
        appendLine("Click-through rate: ${pct(analytics?.ctrPercent)}")
        appendLine("Average percentage viewed: ${pct(analytics?.averageViewPercent)}")
        appendLine("Traffic sources (views): $traffic")
        append("Thumbnail: not analysed")
    }
}

/** A competitor's viral video: public numbers plus a summary basis of its top comments (labelled as such). */
fun competitorFacts(item: ScoredVideo, channelTitle: String, comments: List<String>?, nowMillis: Long): String {
    val v = item.video
    val days = parsePublished(v.publishedAt)?.let { (nowMillis - it) / DAY }
    return buildString {
        appendLine("Channel: $channelTitle")
        appendLine("Title: ${v.title}")
        appendLine("Views: ${v.views}, which is ${"%.1f".format(Locale.US, item.score)} times that channel's usual (${item.baseline}) for videos ${band(item.band)}")
        days?.let { appendLine("Days since upload: $it") }
        appendLine("Published: ${publishTiming(v.publishedAt)}")
        appendLine("Likes: ${v.likes ?: "not available"}, comments: ${v.comments ?: "not available"}")
        appendLine("Retention and thumbnail are not available for other people's videos.")
        if (comments == null) {
            append("Top comments: not available (comments may be off)")
        } else {
            appendLine("Top comments (only these ${comments.size}):")
            comments.take(50).forEach { appendLine("- " + it.replace('\n', ' ').take(200)) }
        }
    }.trimEnd()
}

/** What the "Upload this next" suggestions rest on: real trending titles and recent competitor outliers. */
fun uploadNextFacts(niche: String?, trending: List<String>, outliers: List<String>): String? {
    if (trending.isEmpty() && outliers.isEmpty()) return null
    return buildString {
        appendLine("Niche: ${niche ?: "not known"}")
        if (trending.isNotEmpty()) {
            appendLine("Trending now (YouTube most popular):")
            trending.take(8).forEach { appendLine("- $it") }
        }
        if (outliers.isNotEmpty()) {
            appendLine("Competitor videos from the last 7 days that did far better than usual:")
            outliers.take(5).forEach { appendLine("- $it") }
        }
    }.trimEnd()
}

/** The AI reply for a competitor's viral video: the concept in one sentence (not the original title) and why it worked. */
data class ViralConcept(val concept: String, val why: String)

fun parseViralConcept(text: String): ViralConcept? {
    val sections = parseSections(text, listOf("CONCEPT", "WHY"))
    val concept = sections["CONCEPT"].orEmpty().trim()
    val why = sections["WHY"].orEmpty().trim()
    return if (concept.isEmpty() || why.isEmpty()) null else ViralConcept(concept, why)
}
