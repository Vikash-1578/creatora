package com.creatora.app.domain.publish

import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.parsePublished
import com.creatora.app.domain.model.VideoFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The part of the day a post goes out in; the suggested hour is the middle of it. */
enum class DayPart(val hour: Int) { MORNING(9), AFTERNOON(14), EVENING(19), NIGHT(22) }

fun dayPartOf(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..20 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

/** Where a suggestion comes from. Guidance is labelled as such: it is not from the creator's data. */
sealed interface BestBasis {
    data class OwnData(val videos: Int, val usedWeekdayViews: Boolean) : BestBasis
    data object Guidance : BestBasis
}

data class BestTime(val day: DayOfWeek, val part: DayPart, val basis: BestBasis) {
    /** The next time that weekday and hour come around, at least an hour from [now]. */
    fun nextAt(now: Long, zone: ZoneId): Long {
        var at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusHours(1).withHour(part.hour).withMinute(0).withSecond(0).withNano(0)
        while (at.dayOfWeek != day || at.toInstant().toEpochMilli() < now + HOUR) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }
}

private const val HOUR = 3_600_000L
const val MIN_VIDEOS_FOR_BEST_TIME = 8

private fun median(values: List<Long>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * When the creator's own videos did best. YouTube gives no "viewers online" data, so this uses what it does give: the
 * account's views by weekday over the last 90 days ([dailyViews], oldest first, ending [lastDay]) and the hour each of
 * its videos went out against its views. Needs [MIN_VIDEOS_FOR_BEST_TIME] videos; with fewer it returns general
 * guidance for the [format], labelled as not from the creator's data. A suggestion only: the user picks the time.
 */
fun bestTime(videos: List<VideoStats>, dailyViews: List<Long>?, lastDay: java.time.LocalDate, format: VideoFormat?, zone: ZoneId): BestTime {
    val dated = videos.mapNotNull { v -> parsePublished(v.publishedAt)?.let { v to ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone) } }
    if (dated.size < MIN_VIDEOS_FOR_BEST_TIME) return guidance(format)

    val part = dated.groupBy { dayPartOf(it.second.hour) }
        .mapValues { (_, list) -> median(list.map { it.first.views }) }
        .maxByOrNull { it.value }!!.key

    // The weekday comes from daily account views when there are enough days, otherwise from the videos' own weekdays.
    val dayFromViews = dailyViews?.takeIf { it.size >= 28 }?.let { series ->
        series.indices.groupBy { lastDay.minusDays((series.size - 1 - it).toLong()).dayOfWeek }
            .mapValues { (_, idx) -> idx.sumOf { series[it] }.toDouble() / idx.size }
            .maxByOrNull { it.value }?.key
    }
    val day = dayFromViews ?: dated.groupBy { it.second.dayOfWeek }.mapValues { (_, list) -> median(list.map { it.first.views }) }.maxByOrNull { it.value }!!.key
    return BestTime(day, part, BestBasis.OwnData(dated.size, dayFromViews != null))
}

/** General guidance for a format (short videos in the evening, long ones at the weekend morning), not from the creator's data. */
fun guidance(format: VideoFormat?): BestTime = when (format) {
    VideoFormat.LONG_VIDEO -> BestTime(DayOfWeek.SATURDAY, DayPart.MORNING, BestBasis.Guidance)
    else -> BestTime(DayOfWeek.FRIDAY, DayPart.EVENING, BestBasis.Guidance)
}
