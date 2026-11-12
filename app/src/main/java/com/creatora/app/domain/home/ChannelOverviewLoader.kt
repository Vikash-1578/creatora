package com.creatora.app.domain.home

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PeriodMetrics
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything Home's cards need about one account, fetched once and shared. [error] is set when the channel or its
 * videos could not be read; [metricsError] when only the Analytics numbers could not (the rest still shows).
 */
data class ChannelOverview(
    val account: ConnectedAccount,
    val channel: ChannelPreview?,
    val videos: List<VideoStats>,
    val current: PeriodMetrics?,
    val previous: PeriodMetrics?,
    val error: AdapterError?,
    val metricsError: AdapterError? = null,
)

/**
 * Loads [ChannelOverview]s and keeps them for 15 minutes in memory, so the Snapshot, Focus, Ideas, Goals and Uploads cards
 * make one set of API calls between them. Quota is shared by the whole project, so repeated opens must not re-fetch.
 */
@Singleton
class ChannelOverviewLoader(
    private val registry: PlatformRegistry,
    private val now: () -> Long,
) {
    @Inject
    constructor(registry: PlatformRegistry) : this(registry, System::currentTimeMillis)

    private val lock = Mutex()
    private val overviews = mutableMapOf<String, Pair<Long, ChannelOverview>>()
    private val uploadMetrics = mutableMapOf<String, Pair<Long, List<VideoAnalytics>>>()

    suspend fun overview(account: ConnectedAccount, force: Boolean = false): ChannelOverview = lock.withLock {
        overviews[account.key]?.takeIf { !force && isFresh(it.first, TTL_MILLIS, now()) }?.let { return it.second }
        load(account).also { overviews[account.key] = now() to it }
    }

    /** CTR and retention for the given uploads of an account the user owns; empty when the platform did not return them. */
    suspend fun uploadMetrics(account: ConnectedAccount, videoIds: List<String>): List<VideoAnalytics> = lock.withLock {
        val key = account.key + videoIds.joinToString(",")
        uploadMetrics[key]?.takeIf { isFresh(it.first, TTL_MILLIS, now()) }?.let { return it.second }
        val result = (registry.adapter(account.platform).videoMetrics(account.id, videoIds) as? AdapterOutcome.Ok)?.value.orEmpty()
        result.also { uploadMetrics[key] = now() to it }
    }

    private suspend fun load(account: ConnectedAccount): ChannelOverview {
        if (account.platform.support != PlatformSupport.FULL) {
            return ChannelOverview(account, null, emptyList(), null, null, AdapterError.NotAvailable("Only the link is saved for this platform"))
        }
        val adapter = registry.adapter(account.platform)
        val channel = (adapter.lookup(PlatformRef(account.platform, account.id, null)) as? AdapterOutcome.Ok)?.value
        val videos = when (val result = adapter.recentVideos(account.id, VIDEOS)) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return ChannelOverview(account, channel, emptyList(), null, null, result.error)
        }
        val today = LocalDate.now()
        val current = adapter.channelMetrics(account.id, today.minusDays(27), today)
        val previous = adapter.channelMetrics(account.id, today.minusDays(55), today.minusDays(28))
        return ChannelOverview(
            account = account,
            channel = channel,
            videos = videos,
            current = (current as? AdapterOutcome.Ok)?.value,
            previous = (previous as? AdapterOutcome.Ok)?.value,
            error = null,
            metricsError = (current as? AdapterOutcome.Failed)?.error,
        )
    }

    private companion object {
        const val TTL_MILLIS = 15L * 60 * 1000
        const val VIDEOS = 50
    }
}
