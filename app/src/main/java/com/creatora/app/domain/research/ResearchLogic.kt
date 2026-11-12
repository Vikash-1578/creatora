package com.creatora.app.domain.research

import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.parsePublished
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.log10
import kotlin.math.roundToInt

private const val DAY = 24L * 60 * 60 * 1000

// ---------------------------------------------------------------- which account Research looks at

sealed interface Pick {
    data object NoAccount : Pick
    /** Several accounts and "All accounts" is selected: outlier and trend data is per account. */
    data object PickOne : Pick
    data object Unsupported : Pick
    data class Account(val account: ConnectedAccount) : Pick
}

/** The single account a per-account tab can work on, or why there is none. */
fun pickAccount(accounts: List<ConnectedAccount>): Pick {
    val one = accounts.singleOrNull()
    return when {
        accounts.isEmpty() -> Pick.NoAccount
        one == null -> Pick.PickOne
        one.platform.support != PlatformSupport.FULL || one.platform != ConnectPlatform.YOUTUBE -> Pick.Unsupported
        else -> Pick.Account(one)
    }
}

// ---------------------------------------------------------------- outlier score (estimate)

/** The age bands a video is compared within: 0 to 7 days, 8 to 30, 31 to 90 and over 90. */
enum class AgeBand { WEEK, MONTH, QUARTER, OLDER }

fun ageBandOf(ageDays: Long): AgeBand = when {
    ageDays <= 7 -> AgeBand.WEEK
    ageDays <= 30 -> AgeBand.MONTH
    ageDays <= 90 -> AgeBand.QUARTER
    else -> AgeBand.OLDER
}

/**
 * A video's views compared with the channel's usual. [score] 2.0 means twice the usual. [ageAdjusted] is false when the
 * band had too few videos and the whole channel was the comparison. This is an estimate: the API gives current totals,
 * not each video's views at the same age.
 */
data class ScoredVideo(val video: VideoStats, val score: Double, val band: AgeBand, val ageAdjusted: Boolean, val baseline: Long)

const val MIN_BAND_VIDEOS = 4
const val MIN_VIDEOS_FOR_OUTLIERS = 5
private const val TOO_NEW_DAYS = 2L

fun median(values: List<Long>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * Each video against the median of the channel's other videos of a similar age (its band, when the band holds at least
 * [MIN_BAND_VIDEOS] videos), otherwise against all the other videos. Videos under 48 hours old are left out (they have
 * not had time), and nothing is scored for fewer than [MIN_VIDEOS_FOR_OUTLIERS] videos. Best score first.
 */
fun scoreOutliers(videos: List<VideoStats>, nowMillis: Long): List<ScoredVideo> {
    val aged = videos.mapNotNull { v -> parsePublished(v.publishedAt)?.let { v to ((nowMillis - it) / DAY).coerceAtLeast(0) } }
        .filter { it.second >= TOO_NEW_DAYS }
    if (aged.size < MIN_VIDEOS_FOR_OUTLIERS) return emptyList()
    return aged.mapNotNull { (video, age) ->
        val band = ageBandOf(age)
        val inBand = aged.filter { ageBandOf(it.second) == band }
        val adjusted = inBand.size >= MIN_BAND_VIDEOS
        val others = (if (adjusted) inBand else aged).filter { it.first.id != video.id }.map { it.first.views }
        val baseline = median(others).takeIf { it > 0 } ?: return@mapNotNull null
        ScoredVideo(video, video.views / baseline, band, adjusted, baseline.toLong())
    }.sortedByDescending { it.score }
}

/** The standouts: at least 1.5 times the usual, best first. May be empty, which is an honest answer. */
fun winners(scored: List<ScoredVideo>, take: Int = 3, minScore: Double = 1.5): List<ScoredVideo> =
    scored.filter { it.score >= minScore }.take(take)

/** The weakest: at most half the usual, worst first. */
fun weakest(scored: List<ScoredVideo>, take: Int = 3, maxScore: Double = 0.5): List<ScoredVideo> =
    scored.filter { it.score <= maxScore }.sortedBy { it.score }.take(take)

/** A competitor's viral videos: at least twice the usual. */
fun viral(scored: List<ScoredVideo>, minScore: Double = 2.0): List<ScoredVideo> = scored.filter { it.score >= minScore }

// ---------------------------------------------------------------- competitor numbers

/** Uploads per week over the span from the oldest of the given videos to now; null with fewer than two videos or under a day. */
fun uploadsPerWeek(videos: List<VideoStats>, nowMillis: Long): Double? {
    val times = videos.mapNotNull { parsePublished(it.publishedAt) }
    if (times.size < 2) return null
    val days = (nowMillis - times.min()) / DAY.toDouble()
    return if (days < 1) null else times.size / days * 7
}

/** The mean views of the newest [take] videos; null when there are none. */
fun averageViews(videos: List<VideoStats>, take: Int = 15): Long? =
    videos.sortedByDescending { parsePublished(it.publishedAt) ?: 0L }.take(take).map { it.views }.takeIf { it.isNotEmpty() }?.average()?.toLong()

/** The most viewed among the given (recent) videos. Not all-time. */
fun topVideos(videos: List<VideoStats>, take: Int = 5): List<VideoStats> = videos.sortedByDescending { it.views }.take(take)

/** A competitor's public counts on one day (`yyyyMMdd`), saved by Creatora at most once a day. */
data class CompetitorSnapshot(val day: String, val subscribers: Long?, val views: Long, val videos: Long)

sealed interface Growth {
    /** Fewer than two days of snapshots: no growth number is made up. [sinceDay] is null before the first snapshot. */
    data class Tracking(val sinceDay: String?) : Growth
    data class Known(val days: Int, val subscribers: Long?, val views: Long, val sinceDay: String) : Growth
}

fun growthFrom(snapshots: List<CompetitorSnapshot>): Growth {
    val sorted = snapshots.sortedBy { it.day }
    val first = sorted.firstOrNull() ?: return Growth.Tracking(null)
    val last = sorted.last()
    if (first.day == last.day) return Growth.Tracking(first.day)
    val days = runCatching {
        val f = DateTimeFormatter.BASIC_ISO_DATE
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(first.day, f), LocalDate.parse(last.day, f)).toInt()
    }.getOrNull() ?: return Growth.Tracking(first.day)
    val subs = if (first.subscribers != null && last.subscribers != null) last.subscribers - first.subscribers else null
    return Growth.Known(days, subs, last.views - first.views, first.day)
}

// ---------------------------------------------------------------- keyword estimate

/** What the keyword search returned: the top videos and, where known, each result channel's subscriber count. */
data class KeywordSearch(val videos: List<VideoStats>, val channelSubscribers: Map<String, Long?>)

enum class Level { LOW, MEDIUM, HIGH }

private fun levelOf(points: Int) = when {
    points < 34 -> Level.LOW
    points < 67 -> Level.MEDIUM
    else -> Level.HIGH
}

/**
 * An estimate from YouTube's top results, not real search volume (YouTube gives none).
 * demand = 70% views per day of the results (10,000 a day is the top of the scale) + 30% share uploaded in the last 90 days.
 * competition = 60% share of results from channels bigger than yours + 40% share with the exact words in the title
 * (the bigger-channel part is left out when channel sizes are unknown).
 * score = 60% demand + 40% (100 - competition).
 */
data class KeywordEstimate(
    val demand: Level,
    val demandPoints: Int,
    val medianViewsPerDay: Long,
    val recentPercent: Int,
    val competition: Level,
    val competitionPoints: Int,
    /** Null when the channel sizes (or yours) are not known. */
    val biggerChannelsPercent: Int?,
    val exactMatchPercent: Int,
    val medianAgeDays: Int,
    val score: Int,
    val related: List<String>,
    val sampleSize: Int,
)

const val MIN_KEYWORD_RESULTS = 5

fun keywordEstimate(query: String, search: KeywordSearch, userSubscribers: Long?, nowMillis: Long): KeywordEstimate? {
    val aged = search.videos.mapNotNull { v -> parsePublished(v.publishedAt)?.let { v to ((nowMillis - it) / DAY).coerceAtLeast(0) } }
    if (aged.size < MIN_KEYWORD_RESULTS) return null
    val n = aged.size
    val perDay = median(aged.map { (v, age) -> v.views / maxOf(1L, age) })
    val recent = aged.count { it.second <= 90 } * 100 / n
    val demandPoints = (0.7 * (100 * log10(1 + perDay) / 4).coerceAtMost(100.0) + 0.3 * recent).roundToInt().coerceIn(0, 100)

    val words = query.trim().lowercase()
    val exact = aged.count { it.first.title.lowercase().contains(words) } * 100 / n
    val bigger = userSubscribers?.let { mine ->
        val known = aged.mapNotNull { search.channelSubscribers[it.first.channelId] }
        if (known.isEmpty()) null else known.count { it > mine } * 100 / known.size
    }
    val competitionPoints = (if (bigger != null) 0.6 * bigger + 0.4 * exact else exact.toDouble()).roundToInt().coerceIn(0, 100)
    val score = (0.6 * demandPoints + 0.4 * (100 - competitionPoints)).roundToInt().coerceIn(0, 100)

    return KeywordEstimate(
        demand = levelOf(demandPoints), demandPoints = demandPoints, medianViewsPerDay = perDay.toLong(), recentPercent = recent,
        competition = levelOf(competitionPoints), competitionPoints = competitionPoints, biggerChannelsPercent = bigger,
        exactMatchPercent = exact, medianAgeDays = median(aged.map { it.second }).toInt(), score = score,
        related = relatedTags(query, search.videos), sampleSize = n,
    )
}

/** The tags used by at least two of the results, most used first (real data), without the search words themselves. */
fun relatedTags(query: String, videos: List<VideoStats>, take: Int = 8): List<String> {
    val own = query.trim().lowercase()
    return videos.flatMap { v -> v.tags.map { it.trim().lowercase() }.distinct() }
        .filter { it.isNotEmpty() && it != own }
        .groupingBy { it }.eachCount()
        .filter { it.value >= 2 }
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(take).map { it.key }
}
