package com.creatora.app.data.youtube

import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtVideo
import org.json.JSONArray
import org.json.JSONObject

/** Reads each element of a JSON array as an object. */
private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }

private fun JSONObject.thumbnail(): String? =
    optJSONObject("thumbnails")?.let { (it.optJSONObject("medium") ?: it.optJSONObject("default"))?.optString("url") }?.ifEmpty { null }

/** search.list: how many videos match, and the ids of this page. */
internal fun parseSearchVideoIds(json: JSONObject): Pair<Long, List<String>> {
    val total = json.optJSONObject("pageInfo")?.optLong("totalResults") ?: 0L
    val ids = json.optJSONArray("items").mapObjects { it.optJSONObject("id")?.optString("videoId")?.ifEmpty { null } }
    return total to ids
}

/** search.list for channels: the first channel id. */
internal fun parseSearchChannelId(json: JSONObject): String? =
    json.optJSONArray("items").mapObjects { it.optJSONObject("id")?.optString("channelId")?.ifEmpty { null } }.firstOrNull()

/** videos.list with snippet and statistics. */
internal fun parseVideos(json: JSONObject): List<YtVideo> = json.optJSONArray("items").mapObjects { item ->
    val snippet = item.optJSONObject("snippet") ?: return@mapObjects null
    YtVideo(
        id = item.optString("id").ifEmpty { return@mapObjects null },
        title = snippet.optString("title"),
        channelId = snippet.optString("channelId"),
        channelTitle = snippet.optString("channelTitle"),
        views = item.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: 0L,
        thumbnailUrl = snippet.thumbnail(),
        description = snippet.optString("description"),
    )
}

/** channels.list with snippet and statistics. Hidden subscriber counts become null. */
internal fun parseChannels(json: JSONObject): List<YtChannel> = json.optJSONArray("items").mapObjects { item ->
    val snippet = item.optJSONObject("snippet") ?: return@mapObjects null
    val stats = item.optJSONObject("statistics")
    YtChannel(
        id = item.optString("id").ifEmpty { return@mapObjects null },
        title = snippet.optString("title"),
        subscribers = if (stats?.optBoolean("hiddenSubscriberCount") == true) null else stats?.optString("subscriberCount")?.toLongOrNull(),
        views = stats?.optString("viewCount")?.toLongOrNull() ?: 0L,
        videos = stats?.optString("videoCount")?.toLongOrNull() ?: 0L,
        thumbnailUrl = snippet.thumbnail(),
    )
}
