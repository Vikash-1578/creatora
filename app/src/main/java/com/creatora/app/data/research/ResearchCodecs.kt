package com.creatora.app.data.research

import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.research.KeywordSearch
import org.json.JSONArray
import org.json.JSONObject

// JSON for the cloud cache. Each decoder returns null for text it cannot read, which counts as "nothing cached".

private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotEmpty() } else null
private fun JSONObject.lng(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

private fun <T> JSONArray.toList(read: (JSONObject) -> T): List<T> = (0 until length()).map { read(getJSONObject(it)) }

private fun videoJson(v: VideoStats) = JSONObject().put("id", v.id).put("t", v.title).put("p", v.publishedAt).put("v", v.views)
    .put("l", v.likes ?: JSONObject.NULL).put("cm", v.comments ?: JSONObject.NULL).put("ct", v.channelTitle)
    .put("u", v.thumbnailUrl ?: JSONObject.NULL).put("ci", v.channelId).put("tg", JSONArray(v.tags))

private fun videoOf(o: JSONObject) = VideoStats(
    id = o.getString("id"), title = o.getString("t"), publishedAt = o.getString("p"), views = o.getLong("v"),
    likes = o.lng("l"), comments = o.lng("cm"), channelTitle = o.optString("ct"), thumbnailUrl = o.str("u"),
    channelId = o.optString("ci"), tags = o.optJSONArray("tg")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
)

private fun channelJson(c: ChannelPreview) = JSONObject().put("id", c.id).put("t", c.title).put("h", c.handle ?: JSONObject.NULL)
    .put("d", c.description).put("u", c.photoUrl ?: JSONObject.NULL).put("s", c.subscribers ?: JSONObject.NULL)
    .put("vc", c.videoCount ?: JSONObject.NULL).put("v", c.viewCount ?: JSONObject.NULL)

private fun channelOf(o: JSONObject) = ChannelPreview(
    platform = ConnectPlatform.YOUTUBE, id = o.getString("id"), title = o.getString("t"), handle = o.str("h"), description = o.optString("d"),
    photoUrl = o.str("u"), subscribers = o.lng("s"), videoCount = o.lng("vc"), viewCount = o.lng("v"),
)

fun encodeVideos(items: List<VideoStats>): String = JSONArray().also { a -> items.forEach { a.put(videoJson(it)) } }.toString()
fun decodeVideos(json: String): List<VideoStats>? = runCatching { JSONArray(json).toList(::videoOf) }.getOrNull()

fun encodeChannels(items: List<ChannelPreview>): String = JSONArray().also { a -> items.forEach { a.put(channelJson(it)) } }.toString()
fun decodeChannels(json: String): List<ChannelPreview>? = runCatching { JSONArray(json).toList(::channelOf) }.getOrNull()

fun encodeChannel(item: ChannelPreview): String = channelJson(item).toString()
fun decodeChannel(json: String): ChannelPreview? = runCatching { channelOf(JSONObject(json)) }.getOrNull()

fun encodeStrings(items: List<String>): String = JSONArray(items).toString()
fun decodeStrings(json: String): List<String>? = runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()

fun encodeKeywordSearch(search: KeywordSearch): String {
    val subs = JSONObject().also { o -> search.channelSubscribers.forEach { (id, count) -> if (count != null) o.put(id, count) } }
    return JSONObject().put("videos", JSONArray(encodeVideos(search.videos))).put("subs", subs).toString()
}

fun decodeKeywordSearch(json: String): KeywordSearch? = runCatching {
    val o = JSONObject(json)
    val subs = o.getJSONObject("subs")
    KeywordSearch(
        videos = o.getJSONArray("videos").toList(::videoOf),
        channelSubscribers = subs.keys().asSequence().associateWith { subs.getLong(it) },
    )
}.getOrNull()
