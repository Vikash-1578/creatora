package com.creatora.app.domain.account

/** A video that did much worse than the channel's usual videos, with the numbers that show it. */
data class Underperformer(val video: VideoStats, val medianViews: Long, val percentOfMedian: Int)

/**
 * Picks recent videos with fewer than [belowFraction] of the channel's median views, worst first. With fewer than
 * [minVideos] videos there is no fair comparison, so the list is empty.
 */
fun findUnderperformers(
    videos: List<VideoStats>,
    maxCount: Int = 3,
    belowFraction: Double = 0.5,
    minVideos: Int = 4,
): List<Underperformer> {
    if (videos.size < minVideos) return emptyList()
    val sorted = videos.map { it.views }.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    if (median <= 0) return emptyList()
    return videos.filter { it.views < median * belowFraction }
        .sortedBy { it.views }
        .take(maxCount)
        .map { Underperformer(it, median, (it.views * 100 / median).toInt()) }
}

/** The AI's explanation for one video. */
data class VideoInsight(val videoId: String, val why: String, val fix: String)

/**
 * Reads the reply to the insights prompt: blocks that start with "VIDEO: <id>", then "WHY:" and "FIX:". Blocks for
 * ids that were not asked about, or without both parts, are dropped.
 */
fun parseInsights(text: String, askedIds: Set<String>): List<VideoInsight> {
    val result = mutableListOf<VideoInsight>()
    var id: String? = null
    var why = StringBuilder()
    var fix = StringBuilder()
    var target: StringBuilder? = null
    fun flush() {
        val current = id
        if (current != null && current in askedIds && why.isNotBlank() && fix.isNotBlank()) {
            result += VideoInsight(current, why.toString().trim(), fix.toString().trim())
        }
        why = StringBuilder(); fix = StringBuilder(); target = null
    }
    for (raw in text.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("VIDEO:", ignoreCase = true) -> { flush(); id = line.substringAfter(':').trim() }
            line.startsWith("WHY:", ignoreCase = true) -> { target = why; why.append(line.substringAfter(':').trim()) }
            line.startsWith("FIX:", ignoreCase = true) -> { target = fix; fix.append(line.substringAfter(':').trim()) }
            line.isNotEmpty() -> target?.append(' ')?.append(line)
        }
    }
    flush()
    return result
}

/** The creator profile kept in the cloud and used to personalise AI answers. */
enum class ProfileSource { DERIVED, ASKED }

data class CreatorProfile(
    val niche: String,
    val formats: List<String> = emptyList(),
    val language: String = "",
    val summary: String = "",
    val source: ProfileSource = ProfileSource.DERIVED,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** The numbers handed to the AI for each underperforming video. Missing values are written as "not available". */
fun insightFacts(items: List<Underperformer>, analytics: Map<String, VideoAnalytics>): String = items.joinToString("\n\n") { item ->
    val v = item.video
    val a = analytics[v.id]
    fun num(value: Long?) = value?.toString() ?: "not available"
    fun dbl(value: Double?, unit: String) = value?.let { "%.1f%s".format(java.util.Locale.US, it, unit) } ?: "not available"
    buildString {
        appendLine("VIDEO: ${v.id}")
        appendLine("Title: ${v.title}")
        appendLine("Views: ${v.views} (${item.percentOfMedian}% of the channel's median of ${item.medianViews} views)")
        appendLine("Likes: ${num(v.likes)}")
        appendLine("Comments: ${num(v.comments)}")
        appendLine("Click-through rate: ${dbl(a?.ctrPercent, "%")}")
        appendLine("Average percentage viewed: ${dbl(a?.averageViewPercent, "%")}")
        appendLine("Average view duration: ${dbl(a?.averageViewSeconds, " seconds")}")
        append("Traffic sources (views): ")
        append(a?.trafficSources?.takeIf { it.isNotEmpty() }?.entries?.joinToString { "${it.key} ${it.value}" } ?: "not available")
    }
}
