package com.creatora.app.domain.account

import java.net.URI

/** How far a platform is supported. */
enum class PlatformSupport { FULL, COMING_SOON, LINK_ONLY }

enum class ConnectPlatform(val support: PlatformSupport) {
    YOUTUBE(PlatformSupport.FULL),
    INSTAGRAM(PlatformSupport.COMING_SOON),
    FACEBOOK(PlatformSupport.COMING_SOON),
    TIKTOK(PlatformSupport.COMING_SOON),
    MOJ(PlatformSupport.LINK_ONLY),
    JOSH(PlatformSupport.LINK_ONLY),
    SHARECHAT(PlatformSupport.LINK_ONLY),
    SNAPCHAT(PlatformSupport.LINK_ONLY),
}

/** What the user pasted, understood: the platform and the id or handle (or just the link for link-only platforms). */
data class PlatformRef(val platform: ConnectPlatform, val handleOrId: String, val url: String?)

private val Hosts = mapOf(
    "youtube.com" to ConnectPlatform.YOUTUBE,
    "instagram.com" to ConnectPlatform.INSTAGRAM,
    "facebook.com" to ConnectPlatform.FACEBOOK,
    "fb.com" to ConnectPlatform.FACEBOOK,
    "tiktok.com" to ConnectPlatform.TIKTOK,
    "mojapp.in" to ConnectPlatform.MOJ,
    "joshapp.com" to ConnectPlatform.JOSH,
    "myjosh.in" to ConnectPlatform.JOSH,
    "sharechat.com" to ConnectPlatform.SHARECHAT,
    "snapchat.com" to ConnectPlatform.SNAPCHAT,
)

private val YouTubeChannelId = Regex("UC[A-Za-z0-9_-]{22}")

/**
 * Reads a pasted link, id or handle. A link decides the platform by itself; plain text (an id or @handle) uses the
 * platform the user picked, or YouTube for a bare channel id or @handle. Returns null when nothing sensible is found,
 * including for links to a single video, which are not profiles.
 */
fun parseReference(input: String, chosen: ConnectPlatform? = null): PlatformRef? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val looksLikeLink = text.contains("://") || text.contains("/") || Hosts.keys.any { text.lowercase().contains(it) }
    if (looksLikeLink) {
        val uri = runCatching { URI(if (text.contains("://")) text else "https://$text") }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val platform = Hosts.entries.firstOrNull { host == it.key || host.endsWith(".${it.key}") }?.value ?: return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val handle = when (platform) {
            ConnectPlatform.YOUTUBE -> youTubeHandle(parts)
            ConnectPlatform.SHARECHAT -> parts.getOrNull(1)?.takeIf { parts[0] == "profile" } ?: parts.firstOrNull()
            ConnectPlatform.SNAPCHAT -> parts.getOrNull(1)?.takeIf { parts[0] == "add" } ?: parts.firstOrNull()
            else -> parts.firstOrNull()
        }?.trim()
        if (platform == ConnectPlatform.YOUTUBE && handle == null) return null
        return PlatformRef(platform, handle.orEmpty(), text)
    }
    val platform = chosen ?: when {
        YouTubeChannelId.matches(text) || text.startsWith("@") -> ConnectPlatform.YOUTUBE
        else -> return null
    }
    return PlatformRef(platform, text, null)
}

/** A channel id, @handle or custom name from a YouTube path; null for watch, shorts and other video links. */
private fun youTubeHandle(parts: List<String>): String? = when (parts.firstOrNull()) {
    null, "watch", "shorts", "live", "playlist", "embed" -> null
    "channel", "c", "user" -> parts.getOrNull(1)
    else -> parts[0]
}

/** What the "Is this you?" card shows. Numbers are null when the platform hides them. */
data class ChannelPreview(
    val platform: ConnectPlatform,
    val id: String,
    val title: String,
    val handle: String? = null,
    val description: String = "",
    val photoUrl: String? = null,
    val subscribers: Long? = null,
    val videoCount: Long? = null,
    val viewCount: Long? = null,
)

/** A connected account, saved in the user's cloud profile. Link-only platforms only have a [link]. */
data class ConnectedAccount(
    val platform: ConnectPlatform,
    val id: String,
    val title: String,
    val photoUrl: String? = null,
    val subscribers: Long? = null,
    val videoCount: Long? = null,
    val link: String? = null,
    val description: String = "",
    val connectedAt: Long = System.currentTimeMillis(),
) {
    /** The Firestore document id, safe for any characters in the id. */
    val key: String get() = "${platform.name}_${id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "link" }}"
}

fun ChannelPreview.toAccount() = ConnectedAccount(platform, id, title, photoUrl, subscribers, videoCount, description = description)

data class VideoStats(
    val id: String,
    val title: String,
    val publishedAt: String,
    val views: Long,
    /** Null when the creator hides likes or the platform does not give them. */
    val likes: Long? = null,
    val comments: Long? = null,
    val channelTitle: String = "",
    val thumbnailUrl: String? = null,
    val channelId: String = "",
    /** The tags the uploader set; empty when none or not requested. */
    val tags: List<String> = emptyList(),
)

/**
 * A channel's totals for a period. [revenueUsd] is null when the channel is not monetized or the revenue scope was
 * not granted: unknown, never zero. Revenue is the platform's estimate, in US dollars.
 */
data class PeriodMetrics(
    val views: Long,
    val watchMinutes: Long,
    val subscribersGained: Long,
    val subscribersLost: Long,
    val revenueUsd: Double? = null,
) {
    val netSubscribers: Long get() = subscribersGained - subscribersLost
}

/** Analytics for one video. Each value is null when the platform did not return it (for example CTR, or revenue when not monetized). */
data class VideoAnalytics(
    val videoId: String,
    val ctrPercent: Double? = null,
    val averageViewPercent: Double? = null,
    val averageViewSeconds: Double? = null,
    val estimatedRevenue: Double? = null,
    /** Views by traffic source, e.g. YT_SEARCH to 120. Empty when not returned. */
    val trafficSources: Map<String, Long> = emptyMap(),
)

sealed interface AdapterError {
    /** The user has not allowed access (or the sign-in has to be redone). */
    data object NotAuthorized : AdapterError
    data object Network : AdapterError
    data object QuotaExceeded : AdapterError
    data object NotFound : AdapterError
    /** The platform is not built yet. Nothing is fetched. */
    data object ComingSoon : AdapterError
    /** The platform said no to this data for this account (for example revenue when not monetized). */
    data class NotAvailable(val reason: String) : AdapterError
}

sealed interface AdapterOutcome<out T> {
    data class Ok<T>(val value: T) : AdapterOutcome<T>
    data class Failed(val error: AdapterError) : AdapterOutcome<Nothing>
}

/** One implementation per platform. All data comes from the platform; nothing is invented. */
interface PlatformAdapter {
    val platform: ConnectPlatform

    /** Channels owned by the account the user authorised. */
    suspend fun ownChannels(): AdapterOutcome<List<ChannelPreview>>

    /** Public lookup of a channel by id, handle or link. */
    suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview>

    suspend fun recentVideos(channelId: String, limit: Int = 15): AdapterOutcome<List<VideoStats>>

    suspend fun analytics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>>

    /** Totals for [startDate] to [endDate] (inclusive) of a channel the user owns. */
    suspend fun channelMetrics(channelId: String, startDate: java.time.LocalDate, endDate: java.time.LocalDate): AdapterOutcome<PeriodMetrics>

    /** CTR, average percentage viewed and average view duration for videos of an owned channel (no traffic or revenue). */
    suspend fun videoMetrics(channelId: String, videoIds: List<String>): AdapterOutcome<List<VideoAnalytics>>

    /** The most popular videos right now in a region, optionally in one video category. */
    suspend fun trending(region: String, categoryId: String?, limit: Int = 15): AdapterOutcome<List<VideoStats>>

    /** Recent videos matching a search, most viewed first. A search is expensive in quota. */
    suspend fun searchVideos(query: String, limit: Int = 10): AdapterOutcome<List<VideoStats>>

    /** The top results for a keyword by relevance, any age (what a viewer sees). A search costs 100 quota units. */
    suspend fun keywordSearch(query: String, limit: Int = 25): AdapterOutcome<List<VideoStats>>

    /** Channels that match a niche search (100 quota units), with their public numbers. */
    suspend fun searchChannels(query: String, limit: Int = 10): AdapterOutcome<List<ChannelPreview>>

    /** Channels a channel features on its page (its "featured channels" sections). */
    suspend fun featuredChannels(channelId: String): AdapterOutcome<List<ChannelPreview>>

    /** Public details for several channels at once. */
    suspend fun channels(ids: List<String>): AdapterOutcome<List<ChannelPreview>>

    /** The top comments of a video, plain text. Fails when the uploader turned comments off. */
    suspend fun topComments(videoId: String, limit: Int = 50): AdapterOutcome<List<String>>
}

/** Adapter for a platform that is not built yet: every call says so. */
class ComingSoonAdapter(override val platform: ConnectPlatform) : PlatformAdapter {
    private val soon = AdapterOutcome.Failed(AdapterError.ComingSoon)
    override suspend fun ownChannels() = soon
    override suspend fun lookup(ref: PlatformRef) = soon
    override suspend fun recentVideos(channelId: String, limit: Int) = soon
    override suspend fun analytics(channelId: String, videoIds: List<String>) = soon
    override suspend fun channelMetrics(channelId: String, startDate: java.time.LocalDate, endDate: java.time.LocalDate) = soon
    override suspend fun videoMetrics(channelId: String, videoIds: List<String>) = soon
    override suspend fun trending(region: String, categoryId: String?, limit: Int) = soon
    override suspend fun searchVideos(query: String, limit: Int) = soon
    override suspend fun keywordSearch(query: String, limit: Int) = soon
    override suspend fun searchChannels(query: String, limit: Int) = soon
    override suspend fun featuredChannels(channelId: String) = soon
    override suspend fun channels(ids: List<String>) = soon
    override suspend fun topComments(videoId: String, limit: Int) = soon
}

object YouTubeScopes {
    const val READ = "https://www.googleapis.com/auth/youtube.readonly"
    const val ANALYTICS = "https://www.googleapis.com/auth/yt-analytics.readonly"
    const val MONETARY = "https://www.googleapis.com/auth/yt-analytics-monetary.readonly"

    /** Asked for at sign-in. `youtube.upload` is asked later, when publishing needs it. */
    val SIGN_IN = listOf(READ, ANALYTICS, MONETARY)
}
