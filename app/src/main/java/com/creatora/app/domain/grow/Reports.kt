package com.creatora.app.domain.grow

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.parsePublished
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One analytics report: which numbers, split by what. The platform adapter turns it into its own request. */
data class ReportQuery(val metrics: String, val dimensions: String? = null, val filters: String? = null, val sort: String? = null, val maxResults: Int? = null)

/** The reports Grow asks for. Names follow the YouTube Analytics API; a name the API refuses shows as "not available". */
object Reports {
    val daily = ReportQuery("views,estimatedMinutesWatched,subscribersGained,subscribersLost", "day", sort = "day")
    val dailyRevenue = ReportQuery("estimatedRevenue", "day", sort = "day")
    val traffic = ReportQuery("views", "insightTrafficSourceType", sort = "-views")
    val searchTerms = ReportQuery("views", "insightTrafficSourceDetail", filters = "insightTrafficSourceType==YT_SEARCH", sort = "-views", maxResults = 25)
    val ageGender = ReportQuery("viewerPercentage", "ageGroup,gender", sort = "-viewerPercentage")
    val countries = ReportQuery("views", "country", sort = "-views", maxResults = 10)
    val devices = ReportQuery("views", "deviceType", sort = "-views")
    val subscribed = ReportQuery("estimatedMinutesWatched", "subscribedStatus")
    val contentType = ReportQuery("views", "creatorContentType")
    val watchTime = ReportQuery("estimatedMinutesWatched")
    val topVideos = ReportQuery("views,estimatedMinutesWatched", "video", sort = "-views", maxResults = 10)
}

typealias Rows = List<Map<String, Any?>>

private fun Any?.number(): Double? = (this as? Number)?.toDouble()

/** One column of a day-by-day report as a number per day from [start] to [end], oldest first; a day the report leaves out is 0. */
fun dailyColumn(rows: Rows, column: String, start: LocalDate, end: LocalDate): List<Long> {
    val byDay = rows.mapNotNull { row ->
        val day = (row["day"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        day to (row[column].number()?.toLong() ?: 0L)
    }.toMap()
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
    return (0 until days.coerceAtLeast(0)).map { byDay[start.plusDays(it.toLong())] ?: 0L }
}

/** A report with one label column (or several, joined) and one number, as label to value. */
fun labelled(rows: Rows, labels: List<String>, value: String): Map<String, Double> =
    rows.mapNotNull { row ->
        val label = labels.mapNotNull { row[it] as? String }.takeIf { it.size == labels.size }?.joinToString(" ") ?: return@mapNotNull null
        label to (row[value].number() ?: return@mapNotNull null)
    }.toMap()

/** The numbers of one period with the periods before it, for the chart, the change and "your usual". */
data class Series(val daily: List<Long>, val sums: List<Long>) {
    val total: Long get() = sums.firstOrNull() ?: 0L
}

fun seriesOf(all: List<Long>, days: Int) = Series(all.takeLast(days), periodSums(all, days))

data class Overview(val asOf: LocalDate, val views: Series, val watchMinutes: Series, val subscribersNet: Series, val daysOfData: Int)

data class ContentReport(val traffic: List<Share>, val searchTerms: List<Share>, val topVideos: List<Pair<String, Long>>, val shortsShare: List<Share>)

data class AudienceReport(val ageGender: List<Share>, val countries: List<Share>, val devices: List<Share>, val subscribed: List<Share>, val weekdays: List<Share>)

/** Estimated revenue is YouTube's own estimate in US dollars; [rpm] is computed here from it and the views. */
data class Earnings(val asOf: LocalDate, val revenue: Double, val previousRevenue: Double?, val daily: List<Double>, val views: Long) {
    val rpm: Double? get() = rpm(revenue, views)
}

/**
 * Loads Grow's reports for one account and keeps them in memory (6 hours), so opening a tab again costs no quota.
 * Analytics has its own small quota: each screen asks for its reports once, and a refusal is passed on as it is.
 */
@Singleton
class GrowLoader(
    private val registry: PlatformRegistry,
    private val overviews: ChannelOverviewLoader,
    private val today: () -> LocalDate,
    private val now: () -> Long,
) {
    @Inject
    constructor(registry: PlatformRegistry, overviews: ChannelOverviewLoader) : this(registry, overviews, { LocalDate.now() }, System::currentTimeMillis)

    private val lock = Mutex()
    private val cache = mutableMapOf<String, Pair<Long, Rows>>()

    private suspend fun rows(account: ConnectedAccount, query: ReportQuery, start: LocalDate, end: LocalDate, force: Boolean = false): AdapterOutcome<Rows> = lock.withLock {
        val key = "${account.key}|$query|$start|$end"
        cache[key]?.takeIf { !force && now() - it.first < TTL_MILLIS }?.let { return AdapterOutcome.Ok(it.second) }
        registry.adapter(account.platform).report(account.id, query, start, end).also { if (it is AdapterOutcome.Ok) cache[key] = now() to it.value }
    }

    suspend fun overview(account: ConnectedAccount, period: Period, force: Boolean = false): AdapterOutcome<Overview> {
        val end = dataAsOf(today())
        val start = end.minusDays(period.days * PERIODS - 1L)
        val rows = when (val r = rows(account, Reports.daily, start, end, force)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        val gained = dailyColumn(rows, "subscribersGained", start, end)
        val lost = dailyColumn(rows, "subscribersLost", start, end)
        return AdapterOutcome.Ok(
            Overview(
                asOf = end,
                views = seriesOf(dailyColumn(rows, "views", start, end), period.days),
                watchMinutes = seriesOf(dailyColumn(rows, "estimatedMinutesWatched", start, end), period.days),
                subscribersNet = seriesOf(gained.indices.map { gained[it] - lost[it] }, period.days),
                daysOfData = rows.size.coerceAtMost(period.days),
            ),
        )
    }

    private suspend fun shareOf(account: ConnectedAccount, query: ReportQuery, labels: List<String>, value: String, start: LocalDate, end: LocalDate, take: Int = Int.MAX_VALUE): List<Share> =
        (rows(account, query, start, end) as? AdapterOutcome.Ok)?.value?.let { shares(labelled(it, labels, value), take) }.orEmpty()

    /** Traffic sources, YouTube search terms, top videos and the Shorts and long-video split. A report YouTube refuses comes back empty and its card says so. */
    suspend fun content(account: ConnectedAccount, period: Period): AdapterOutcome<ContentReport> {
        val end = dataAsOf(today())
        val start = end.minusDays(period.days - 1L)
        val traffic = when (val r = rows(account, Reports.traffic, start, end)) {
            is AdapterOutcome.Ok -> shares(labelled(r.value, listOf("insightTrafficSourceType"), "views"))
            is AdapterOutcome.Failed -> return r
        }
        val titles = overviews.overview(account).videos.associate { it.id to it.title }
        val top = (rows(account, Reports.topVideos, start, end) as? AdapterOutcome.Ok)?.value.orEmpty().mapNotNull { row ->
            val id = row["video"] as? String ?: return@mapNotNull null
            (titles[id] ?: id) to (row["views"].number()?.toLong() ?: return@mapNotNull null)
        }
        return AdapterOutcome.Ok(
            ContentReport(
                traffic = traffic,
                searchTerms = shareOf(account, Reports.searchTerms, listOf("insightTrafficSourceDetail"), "views", start, end, take = 25),
                topVideos = top,
                shortsShare = shareOf(account, Reports.contentType, listOf("creatorContentType"), "views", start, end),
            ),
        )
    }

    /** Age and gender, countries, devices, subscribers against non-subscribers, and views by weekday (YouTube shares no viewer hours). */
    suspend fun audience(account: ConnectedAccount, period: Period): AdapterOutcome<AudienceReport> {
        val end = dataAsOf(today())
        val start = end.minusDays(period.days - 1L)
        val ages = when (val r = rows(account, Reports.ageGender, start, end)) {
            is AdapterOutcome.Ok -> shares(labelled(r.value, listOf("ageGroup", "gender"), "viewerPercentage"))
            is AdapterOutcome.Failed -> return r
        }
        val daily = (rows(account, Reports.daily, start, end) as? AdapterOutcome.Ok)?.value?.let { dailyColumn(it, "views", start, end) }.orEmpty()
        val byWeekday = daily.indices.groupBy { start.plusDays(it.toLong()).dayOfWeek }.mapValues { (_, idx) -> idx.sumOf { daily[it] }.toDouble() }
        return AdapterOutcome.Ok(
            AudienceReport(
                ageGender = ages,
                countries = shareOf(account, Reports.countries, listOf("country"), "views", start, end, take = 10),
                devices = shareOf(account, Reports.devices, listOf("deviceType"), "views", start, end),
                subscribed = shareOf(account, Reports.subscribed, listOf("subscribedStatus"), "estimatedMinutesWatched", start, end),
                weekdays = shares(java.time.DayOfWeek.values().associate { it.name to (byWeekday[it] ?: 0.0) }),
            ),
        )
    }

    /** Estimated revenue for the period and the one before. Fails with YouTube's reason when the channel is not monetized: never a zero. */
    suspend fun earnings(account: ConnectedAccount, period: Period): AdapterOutcome<Earnings> {
        val end = dataAsOf(today())
        val start = end.minusDays(period.days * 2 - 1L)
        val revenueRows = when (val r = rows(account, Reports.dailyRevenue, start, end)) {
            is AdapterOutcome.Ok -> r.value
            is AdapterOutcome.Failed -> return r
        }
        if (revenueRows.isEmpty()) return AdapterOutcome.Failed(AdapterError.NotAvailable("YouTube sent no revenue for this period"))
        val byDay = revenueRows.mapNotNull { row -> (row["day"] as? String)?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }?.let { it to (row["estimatedRevenue"].number() ?: 0.0) } }.toMap()
        val current = (0 until period.days).map { byDay[end.minusDays((period.days - 1 - it).toLong())] ?: 0.0 }
        val previous = (0 until period.days).mapNotNull { byDay[end.minusDays((period.days * 2 - 1 - it).toLong())] }
        val views = (rows(account, Reports.daily, end.minusDays(period.days - 1L), end) as? AdapterOutcome.Ok)?.value?.let { dailyColumn(it, "views", end.minusDays(period.days - 1L), end).sum() } ?: 0L
        return AdapterOutcome.Ok(Earnings(end, current.sum(), previous.takeIf { it.isNotEmpty() }?.sum(), current, views))
    }

    /** What the monetization journey needs. Anything YouTube does not return stays null (unknown). */
    suspend fun monetization(account: ConnectedAccount): MonetizationInputs {
        val end = dataAsOf(today())
        val overview = overviews.overview(account)
        val cutoff = now() - 90L * DAY_MILLIS
        val uploads = overview.videos.takeIf { overview.error == null }?.count { (parsePublished(it.publishedAt) ?: 0L) >= cutoff }
        val minutes = (rows(account, Reports.watchTime, end.minusDays(364), end) as? AdapterOutcome.Ok)?.value?.firstOrNull()?.get("estimatedMinutesWatched").number()
        val shorts = (rows(account, Reports.contentType, end.minusDays(89), end) as? AdapterOutcome.Ok)?.value
            ?.let { labelled(it, listOf("creatorContentType"), "views") }?.let { map -> map.entries.firstOrNull { it.key.equals("SHORTS", ignoreCase = true) }?.value?.toLong() ?: if (map.isEmpty()) null else 0L }
        return MonetizationInputs(
            subscribers = overview.channel?.subscribers ?: account.subscribers,
            publicUploads90 = uploads,
            watchHours365 = minutes?.div(60.0),
            shortsViews90 = shorts,
        )
    }

    private companion object {
        const val TTL_MILLIS = 6L * 60 * 60 * 1000
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** The latest period plus six before it, for "your usual". */
        const val PERIODS = 7
    }
}
