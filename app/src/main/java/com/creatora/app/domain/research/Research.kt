package com.creatora.app.domain.research

/** A YouTube video with the public numbers the Research tab needs. */
data class YtVideo(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val views: Long,
    val thumbnailUrl: String?,
    val description: String = "",
) {
    val url: String get() = "https://youtu.be/$id"
}

data class YtChannel(
    val id: String,
    val title: String,
    val subscribers: Long?,
    val views: Long,
    val videos: Long,
    val thumbnailUrl: String?,
) {
    /** Average views per video, the "usual" level outliers are measured against. */
    val averageViews: Long get() = if (videos > 0) views / videos else 0L
}

data class VideoSearch(
    /** How many videos match the query, as reported by YouTube (it stops counting at 1,000,000). */
    val totalResults: Long,
    val videos: List<YtVideo>,
)

/** A video that got [multiple] times its channel's usual views. */
data class Outlier(val video: YtVideo, val channelAverage: Long, val multiple: Double)

enum class Competition { LOW, MEDIUM, HIGH }

/**
 * Rough competition from the number of matching videos. YouTube gives no search volume or competition
 * score, so this is only a guide.
 */
fun competitionFor(totalResults: Long): Competition = when {
    totalResults < LOW_BELOW -> Competition.LOW
    totalResults < MEDIUM_BELOW -> Competition.MEDIUM
    else -> Competition.HIGH
}

private const val LOW_BELOW = 10_000L
private const val MEDIUM_BELOW = 200_000L

/** Videos with at least [minMultiple] times their channel's average views, best first. */
fun findOutliers(videos: List<YtVideo>, channels: List<YtChannel>, minMultiple: Double = 2.0): List<Outlier> {
    val byId = channels.associateBy { it.id }
    return videos.mapNotNull { video ->
        val average = byId[video.channelId]?.averageViews?.takeIf { it > 0 } ?: return@mapNotNull null
        Outlier(video, average, video.views.toDouble() / average).takeIf { it.multiple >= minMultiple }
    }.sortedByDescending { it.multiple }
}

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val VIDEO_URL = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/))([A-Za-z0-9_-]{11})")

/** The video id from a YouTube link or a bare id; null if [text] is neither. */
fun parseVideoId(text: String): String? {
    val trimmed = text.trim()
    return if (VIDEO_ID.matches(trimmed)) trimmed else VIDEO_URL.find(trimmed)?.groupValues?.get(1)
}

/** How a channel was typed: by id, by @handle, or by name to search for. */
sealed interface ChannelQuery {
    data class Id(val id: String) : ChannelQuery
    data class Handle(val handle: String) : ChannelQuery
    data class Name(val name: String) : ChannelQuery
}

private val CHANNEL_ID_URL = Regex("youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})")
private val HANDLE = Regex("(?:youtube\\.com/)?(@[A-Za-z0-9._-]+)")

fun parseChannelQuery(text: String): ChannelQuery {
    val trimmed = text.trim()
    CHANNEL_ID_URL.find(trimmed)?.let { return ChannelQuery.Id(it.groupValues[1]) }
    if (trimmed.matches(Regex("UC[A-Za-z0-9_-]{22}"))) return ChannelQuery.Id(trimmed)
    HANDLE.find(trimmed)?.let { return ChannelQuery.Handle(it.groupValues[1]) }
    return ChannelQuery.Name(trimmed)
}

/** Why a YouTube call failed. Never contains the API key. */
sealed interface YtError {
    data object NoKey : YtError
    data object InvalidKey : YtError
    data object QuotaExceeded : YtError
    data object Network : YtError
    data class Server(val code: Int) : YtError
    data class BadRequest(val message: String) : YtError
    data object BadResponse : YtError
}

class YouTubeException(val error: YtError) : Exception()

sealed interface YtOutcome<out T> {
    data class Ok<T>(val value: T) : YtOutcome<T>
    data class Failed(val error: YtError) : YtOutcome<Nothing>
}

data class Competitor(
    val id: Long = 0,
    val channelId: String,
    val title: String,
    val subscribers: Long?,
    val views: Long,
    val videos: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A video or channel worth learning from, with the user's own note. */
data class SwipeItem(
    val id: Long = 0,
    val title: String,
    val url: String,
    val channelTitle: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
