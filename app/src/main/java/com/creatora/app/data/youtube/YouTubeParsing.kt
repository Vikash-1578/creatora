package com.creatora.app.data.youtube

import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.longOrNull(name: String): Long? = if (has(name) && !isNull(name)) optString(name).toLongOrNull() else null

/** channels.list response. Hidden subscriber counts come back as null. */
fun parseChannels(json: String): List<ChannelPreview> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).map { i ->
        val item = items.getJSONObject(i)
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        val stats = item.optJSONObject("statistics") ?: JSONObject()
        val thumbs = snippet.optJSONObject("thumbnails")
        ChannelPreview(
            platform = ConnectPlatform.YOUTUBE,
            id = item.getString("id"),
            title = snippet.optString("title"),
            handle = snippet.optString("customUrl").ifEmpty { null },
            description = snippet.optString("description"),
            photoUrl = (thumbs?.optJSONObject("medium") ?: thumbs?.optJSONObject("default"))?.optString("url")?.ifEmpty { null },
            subscribers = if (stats.optBoolean("hiddenSubscriberCount")) null else stats.longOrNull("subscriberCount"),
            videoCount = stats.longOrNull("videoCount"),
            viewCount = stats.longOrNull("viewCount"),
        )
    }
}

/** videos.list response (part=snippet,statistics). */
fun parseVideos(json: String): List<VideoStats> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).map { i ->
        val item = items.getJSONObject(i)
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        val stats = item.optJSONObject("statistics") ?: JSONObject()
        VideoStats(
            id = item.getString("id"),
            title = snippet.optString("title"),
            publishedAt = snippet.optString("publishedAt"),
            views = stats.longOrNull("viewCount") ?: 0,
            likes = stats.longOrNull("likeCount"),
            comments = stats.longOrNull("commentCount"),
            channelTitle = snippet.optString("channelTitle"),
            thumbnailUrl = snippet.optJSONObject("thumbnails")?.let { (it.optJSONObject("medium") ?: it.optJSONObject("default"))?.optString("url") }?.ifEmpty { null },
            channelId = snippet.optString("channelId"),
            tags = snippet.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.getString(it) } }.orEmpty(),
        )
    }
}

/** search.list response with type=channel (part=snippet): the channel ids. */
fun parseSearchChannelIds(json: String): List<String> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { items.getJSONObject(it).optJSONObject("id")?.optString("channelId")?.ifEmpty { null } }
}

/** channelSections.list response (part=contentDetails): the channels listed in "featured channels" sections. */
fun parseFeaturedChannelIds(json: String): List<String> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).flatMap { i ->
        val ids = items.getJSONObject(i).optJSONObject("contentDetails")?.optJSONArray("channels")
        if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) }
    }.distinct()
}

/** commentThreads.list response (part=snippet): the top-level comment texts. */
fun parseComments(json: String): List<String> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull {
        items.getJSONObject(it).optJSONObject("snippet")?.optJSONObject("topLevelComment")?.optJSONObject("snippet")?.optString("textDisplay")?.takeIf { text -> text.isNotBlank() }
    }
}

/** search.list response (part=id): the video ids. */
fun parseSearchVideoIds(json: String): List<String> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { items.getJSONObject(it).optJSONObject("id")?.optString("videoId")?.ifEmpty { null } }
}

/**
 * The totals report (no dimensions) as [PeriodMetrics]; the revenue report (or null when it was refused) fills the
 * estimated revenue. Null when the report has no row.
 */
fun reportToPeriodMetrics(core: List<Map<String, Any?>>, revenue: List<Map<String, Any?>>?): com.creatora.app.domain.account.PeriodMetrics? {
    val row = core.firstOrNull() ?: return null
    fun long(name: String) = (row[name] as? Number)?.toLong() ?: 0L
    return com.creatora.app.domain.account.PeriodMetrics(
        views = long("views"),
        watchMinutes = long("estimatedMinutesWatched"),
        subscribersGained = long("subscribersGained"),
        subscribersLost = long("subscribersLost"),
        revenueUsd = (revenue?.firstOrNull()?.get("estimatedRevenue") as? Number)?.toDouble(),
    )
}

/** playlistItems.list response: the video ids, in the order given. */
fun parsePlaylistVideoIds(json: String): List<String> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { items.getJSONObject(it).optJSONObject("contentDetails")?.optString("videoId")?.ifEmpty { null } }
}

/** One analytics report as column name to value, per row. Numbers stay as Double; strings (like video ids) as String. */
fun parseReport(json: String): List<Map<String, Any?>> {
    val root = JSONObject(json)
    val headers = root.optJSONArray("columnHeaders") ?: return emptyList()
    val names = (0 until headers.length()).map { headers.getJSONObject(it).getString("name") }
    val rows = root.optJSONArray("rows") ?: return emptyList()
    return (0 until rows.length()).map { r ->
        val row: JSONArray = rows.getJSONArray(r)
        names.indices.associate { c -> names[c] to row.opt(c).takeUnless { it === JSONObject.NULL } }
    }
}

private fun Any?.asDouble(): Double? = (this as? Number)?.toDouble()

/** Merges the per-video report (core metrics and revenue) into [VideoAnalytics]; a metric missing from the report stays null. */
fun reportToAnalytics(core: List<Map<String, Any?>>, revenue: List<Map<String, Any?>>): List<VideoAnalytics> {
    val revenueByVideo = revenue.associate { it["video"] as? String to it["estimatedRevenue"].asDouble() }
    return core.mapNotNull { row ->
        val id = row["video"] as? String ?: return@mapNotNull null
        VideoAnalytics(
            videoId = id,
            ctrPercent = row["videoThumbnailImpressionsClickRate"].asDouble(),
            averageViewPercent = row["averageViewPercentage"].asDouble(),
            averageViewSeconds = row["averageViewDuration"].asDouble(),
            estimatedRevenue = revenueByVideo[id],
        )
    }
}

/** insightTrafficSourceType report: views per source. */
fun reportToTraffic(rows: List<Map<String, Any?>>): Map<String, Long> =
    rows.mapNotNull { row ->
        val source = row["insightTrafficSourceType"] as? String ?: return@mapNotNull null
        val views = row["views"].asDouble()?.toLong() ?: return@mapNotNull null
        source to views
    }.toMap()
