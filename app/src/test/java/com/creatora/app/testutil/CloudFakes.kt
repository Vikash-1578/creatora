package com.creatora.app.testutil

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlatformAdapter
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.repository.AiHistoryEntry
import com.creatora.app.domain.repository.AiHistoryRepository
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.DeleteOutcome
import com.creatora.app.domain.repository.GoalRepository
import com.creatora.app.domain.repository.LaunchOutcome
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.SignInOutcome
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.domain.repository.UserRepository
import com.creatora.app.domain.repository.YouTubeAuthorizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

val TestUser = AuthUser("uid1", "Asha Rao", "asha@example.com", null)

class FakeAuthService(initial: AuthUser? = null) : AuthService {
    val current = MutableStateFlow(initial)
    override val user: Flow<AuthUser?> = current
    var signInOutcome: SignInOutcome = SignInOutcome.Ok(TestUser)
    var deleteOutcome = DeleteOutcome.DELETED
    var signInCalls = 0

    override suspend fun signInWithGoogle(): SignInOutcome {
        signInCalls++
        (signInOutcome as? SignInOutcome.Ok)?.let { current.value = it.user }
        return signInOutcome
    }

    override suspend fun signOut() {
        current.value = null
    }

    override suspend fun deleteAccount() = deleteOutcome
}

class FakeUserRepository : UserRepository {
    val docs = MutableStateFlow<Map<String, UserDoc>>(emptyMap())
    val deleted = mutableListOf<String>()

    fun put(doc: UserDoc) {
        docs.value = docs.value + (doc.uid to doc)
    }

    override fun observe(uid: String): Flow<UserDoc?> = docs.map { it[uid] }

    override suspend fun createIfMissing(user: AuthUser, language: AppLanguage) {
        if (user.uid !in docs.value) put(UserDoc(user.uid, user.name, user.email, user.photoUrl, Plan.FREE, language.tag, createdAt = 1))
    }

    override suspend fun setLanguage(uid: String, language: AppLanguage) {
        docs.value[uid]?.let { put(it.copy(language = language.tag)) }
    }

    override suspend fun setCreatorProfile(uid: String, profile: CreatorProfile) {
        docs.value[uid]?.let { put(it.copy(creatorProfile = profile)) }
    }

    override suspend fun deleteAll(uid: String) {
        deleted += uid
        docs.value = docs.value - uid
    }
}

class FakeUsageRepository : UsageRepository {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    override fun observe(uid: String, monthKey: String): Flow<Int> = counts.map { it["$uid/$monthKey"] ?: 0 }
    override suspend fun increment(uid: String, monthKey: String, amount: Int) {
        counts.value = counts.value + ("$uid/$monthKey" to ((counts.value["$uid/$monthKey"] ?: 0) + amount))
    }

    fun set(uid: String, monthKey: String, value: Int) {
        counts.value = counts.value + ("$uid/$monthKey" to value)
    }
}

class FakeConnectedAccountRepository : ConnectedAccountRepository {
    val byUser = MutableStateFlow<Map<String, List<ConnectedAccount>>>(emptyMap())
    /** Makes saving fail like Firestore does when the rules refuse the write. */
    var failSave = false
    override fun observe(uid: String): Flow<List<ConnectedAccount>> = byUser.map { it[uid].orEmpty() }
    override suspend fun save(uid: String, account: ConnectedAccount) {
        if (failSave) throw IllegalStateException("PERMISSION_DENIED")
        byUser.value = byUser.value + (uid to (byUser.value[uid].orEmpty().filterNot { it.key == account.key } + account))
    }

    override suspend fun remove(uid: String, key: String) {
        byUser.value = byUser.value + (uid to byUser.value[uid].orEmpty().filterNot { it.key == key })
    }
}

class FakeAiHistoryRepository : AiHistoryRepository {
    val entries = MutableStateFlow<List<AiHistoryEntry>>(emptyList())
    override fun observe(uid: String): Flow<List<AiHistoryEntry>> = entries
    override suspend fun add(uid: String, prompt: String, createdAt: Long) {
        entries.value = listOf(AiHistoryEntry("${entries.value.size}", prompt, createdAt)) + entries.value
    }

    override suspend fun clear(uid: String) {
        entries.value = emptyList()
    }
}

class FakeBillingRepository(initial: BillingState = BillingState()) : BillingRepository {
    val current = MutableStateFlow(initial)
    override val state: Flow<BillingState> = current
    var launch = LaunchOutcome.UNAVAILABLE
    var refreshes = 0
    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun launchUpgrade() = launch
}

class FakeNotificationPrefs : NotificationPrefsRepository {
    val current = MutableStateFlow(NotificationPrefs())
    val last = mutableMapOf<String, Long>()
    override val prefs: Flow<NotificationPrefs> = current
    override suspend fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        current.value = current.value.copy(enabled = if (enabled) current.value.enabled + category else current.value.enabled - category)
    }

    override suspend fun setMilestone(views: Long) {
        current.value = current.value.copy(milestoneViews = views)
    }

    override suspend fun lastValue(key: String) = last[key]
    override suspend fun setLastValue(key: String, value: Long) {
        last[key] = value
    }
}

class FakeYouTubeAuthorizer(var outcome: AuthorizationOutcome = AuthorizationOutcome.Granted("token")) : YouTubeAuthorizer {
    var calls = 0
    override suspend fun authorize(scopes: List<String>): AuthorizationOutcome {
        calls++
        return outcome
    }
}

/** A platform adapter with canned answers. */
class FakePlatformAdapter(override val platform: ConnectPlatform = ConnectPlatform.YOUTUBE) : PlatformAdapter {
    var own: AdapterOutcome<List<ChannelPreview>> = AdapterOutcome.Ok(emptyList())
    var lookupResult: AdapterOutcome<ChannelPreview> = AdapterOutcome.Failed(AdapterError.NotFound)
    var videos: AdapterOutcome<List<VideoStats>> = AdapterOutcome.Ok(emptyList())
    var analyticsResult: AdapterOutcome<List<VideoAnalytics>> = AdapterOutcome.Ok(emptyList())
    val lookups = mutableListOf<PlatformRef>()
    var metrics: AdapterOutcome<com.creatora.app.domain.account.PeriodMetrics> = AdapterOutcome.Failed(AdapterError.NotFound)
    var previousMetrics: AdapterOutcome<com.creatora.app.domain.account.PeriodMetrics>? = null
    var videoMetricsResult: AdapterOutcome<List<VideoAnalytics>> = AdapterOutcome.Ok(emptyList())
    var trendingResult: AdapterOutcome<List<VideoStats>> = AdapterOutcome.Ok(emptyList())
    var searchResult: AdapterOutcome<List<VideoStats>> = AdapterOutcome.Ok(emptyList())
    val metricCalls = mutableListOf<Pair<java.time.LocalDate, java.time.LocalDate>>()
    val trendingCalls = mutableListOf<Pair<String, String?>>()
    val searchCalls = mutableListOf<String>()
    val videoCalls = mutableListOf<String>()
    var keywordResult: AdapterOutcome<List<VideoStats>> = AdapterOutcome.Ok(emptyList())
    var searchChannelsResult: AdapterOutcome<List<ChannelPreview>> = AdapterOutcome.Ok(emptyList())
    var featuredResult: AdapterOutcome<List<ChannelPreview>> = AdapterOutcome.Ok(emptyList())
    var channelsResult: AdapterOutcome<List<ChannelPreview>> = AdapterOutcome.Ok(emptyList())
    var commentsResult: AdapterOutcome<List<String>> = AdapterOutcome.Ok(emptyList())
    val keywordCalls = mutableListOf<String>()
    val channelSearchCalls = mutableListOf<String>()
    val featuredCalls = mutableListOf<String>()
    val channelsCalls = mutableListOf<List<String>>()
    val commentCalls = mutableListOf<String>()

    override suspend fun ownChannels() = own
    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        lookups += ref
        return lookupResult
    }

    override suspend fun recentVideos(channelId: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        videoCalls += channelId
        return videos
    }
    override suspend fun analytics(channelId: String, videoIds: List<String>) = analyticsResult
    override suspend fun channelMetrics(channelId: String, startDate: java.time.LocalDate, endDate: java.time.LocalDate): AdapterOutcome<com.creatora.app.domain.account.PeriodMetrics> {
        metricCalls += startDate to endDate
        // The first call is the current period; a second answer can be set for the previous one.
        return if (metricCalls.size % 2 == 0) previousMetrics ?: metrics else metrics
    }

    override suspend fun videoMetrics(channelId: String, videoIds: List<String>) = videoMetricsResult
    override suspend fun trending(region: String, categoryId: String?, limit: Int): AdapterOutcome<List<VideoStats>> {
        trendingCalls += region to categoryId
        return trendingResult
    }

    override suspend fun searchVideos(query: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        searchCalls += query
        return searchResult
    }

    override suspend fun keywordSearch(query: String, limit: Int): AdapterOutcome<List<VideoStats>> {
        keywordCalls += query
        return keywordResult
    }

    override suspend fun searchChannels(query: String, limit: Int): AdapterOutcome<List<ChannelPreview>> {
        channelSearchCalls += query
        return searchChannelsResult
    }

    override suspend fun featuredChannels(channelId: String): AdapterOutcome<List<ChannelPreview>> {
        featuredCalls += channelId
        return featuredResult
    }

    override suspend fun channels(ids: List<String>): AdapterOutcome<List<ChannelPreview>> {
        channelsCalls += ids
        return channelsResult
    }

    override suspend fun topComments(videoId: String, limit: Int): AdapterOutcome<List<String>> {
        commentCalls += videoId
        return commentsResult
    }
}

fun preview(id: String = "UC1", title: String = "My channel", subs: Long? = 1_200) =
    ChannelPreview(ConnectPlatform.YOUTUBE, id, title, handle = "@mine", description = "I make short films", subscribers = subs, videoCount = 20, viewCount = 50_000)

class FakeGoalRepository : GoalRepository {
    val byUser = MutableStateFlow<Map<String, List<Goal>>>(emptyMap())
    private var next = 1
    override fun observe(uid: String): Flow<List<Goal>> = byUser.map { it[uid].orEmpty() }
    override suspend fun save(uid: String, goal: Goal): String {
        val id = goal.id.ifBlank { "g${next++}" }
        byUser.value = byUser.value + (uid to (byUser.value[uid].orEmpty().filterNot { it.id == id } + goal.copy(id = id)))
        return id
    }

    override suspend fun delete(uid: String, id: String) {
        byUser.value = byUser.value + (uid to byUser.value[uid].orEmpty().filterNot { it.id == id })
    }
}

class FakeUpcomingPostRepository : com.creatora.app.domain.repository.UpcomingPostRepository {
    val posts = MutableStateFlow<List<com.creatora.app.domain.repository.UpcomingPost>>(emptyList())
    override fun observe(uid: String): Flow<List<com.creatora.app.domain.repository.UpcomingPost>> = posts
}

class FakeCreatorIdeaRepository : com.creatora.app.domain.repository.CreatorIdeaRepository {
    val ideas = MutableStateFlow<List<com.creatora.app.domain.home.CreatorIdea>>(emptyList())
    private var next = 1
    override fun observe(uid: String): Flow<List<com.creatora.app.domain.home.CreatorIdea>> = ideas
    override suspend fun save(uid: String, idea: com.creatora.app.domain.home.CreatorIdea): String {
        val id = idea.id.ifBlank { "i${next++}" }
        ideas.value = ideas.value.filterNot { it.id == id } + idea.copy(id = id)
        return id
    }

    override suspend fun setStatus(uid: String, id: String, status: com.creatora.app.domain.home.IdeaStatus, projectId: Long?) {
        ideas.value = ideas.value.map { if (it.id == id) it.copy(status = status, projectId = projectId ?: it.projectId) else it }
    }
}

class FakeCacheRepository : com.creatora.app.domain.repository.CacheRepository {
    val entries = mutableMapOf<String, com.creatora.app.domain.repository.CachedText>()
    var puts = 0
    override suspend fun get(uid: String, key: String) = entries[key]
    override suspend fun put(uid: String, key: String, text: String, nowMillis: Long) {
        puts++
        entries[key] = com.creatora.app.domain.repository.CachedText(text, nowMillis)
    }
}

class FakeHomePrefs : com.creatora.app.domain.repository.HomePrefsRepository {
    override val selectedKey = MutableStateFlow<String?>(null)
    override suspend fun setSelectedKey(key: String) { selectedKey.value = key }
    override val hideAmounts = MutableStateFlow(false)
    override suspend fun setHideAmounts(hidden: Boolean) { hideAmounts.value = hidden }
    override val streakUnit = MutableStateFlow(com.creatora.app.domain.home.StreakUnit.WEEKS)
    override suspend fun setStreakUnit(unit: com.creatora.app.domain.home.StreakUnit) { streakUnit.value = unit }
    override val region = MutableStateFlow<String?>(null)
    override suspend fun setRegion(code: String) { region.value = code }
}

class FakeNotificationInbox : com.creatora.app.domain.repository.NotificationInboxRepository {
    override val entries = MutableStateFlow<List<com.creatora.app.domain.repository.InboxEntry>>(emptyList())
    override suspend fun add(category: NotificationCategory, title: String, text: String, at: Long) {
        entries.value = listOf(com.creatora.app.domain.repository.InboxEntry(at, category, title, text, at)) + entries.value
    }

    override suspend fun clear() { entries.value = emptyList() }
}

class FakeTrendingRepository : com.creatora.app.domain.repository.TrendingRepository {
    var result: com.creatora.app.domain.repository.TrendingResult = com.creatora.app.domain.repository.TrendingResult.Ok(emptyList(), 0, fromCache = false)
    val calls = mutableListOf<Triple<String, String?, Boolean>>()
    override suspend fun trending(uid: String, region: String, niche: String?, force: Boolean): com.creatora.app.domain.repository.TrendingResult {
        calls += Triple(region, niche, force)
        return result
    }
}

/** The pieces most Home ViewModels need, wired together. */
class HomeKit(signedIn: Boolean = true) {
    val auth = FakeAuthService(if (signedIn) TestUser else null)
    val accounts = FakeConnectedAccountRepository()
    val prefs = FakeHomePrefs()
    val context = com.creatora.app.domain.home.AccountContext(auth, accounts, prefs)
    val youtube = FakePlatformAdapter()
    val registry = com.creatora.app.domain.account.PlatformRegistry(listOf(youtube))
    val loader = com.creatora.app.domain.home.ChannelOverviewLoader(registry) { System.currentTimeMillis() }
    val cache = FakeCacheRepository()
    val users = FakeUserRepository().apply { put(UserDoc("uid1", "Asha", "a@x.com", null, Plan.FREE, "en", 1)) }
    val billing = FakeBillingRepository()
    val ai = FakeAiService(okReply(""))
    val plans = com.creatora.app.domain.account.PlanResolver(users, billing)

    suspend fun connect(id: String = "UC1", title: String = "My channel", subs: Long? = 1_000) {
        accounts.save("uid1", ConnectedAccount(ConnectPlatform.YOUTUBE, id, title, subscribers = subs))
    }
}

/** [count] videos, newest first, one every three days; the views fall from [topViews] by [step]. */
fun sampleVideos(count: Int, topViews: Long = 5_000, step: Long = 200): List<VideoStats> = (0 until count).map { i ->
    VideoStats(
        id = "v$i", title = "Video $i",
        publishedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(3L * (i + 1))).toString(),
        views = topViews - step * i, likes = 10, comments = 2,
    )
}


// ---------------------------------------------------------------- Research

class FakeResearchRepository : com.creatora.app.domain.research.ResearchRepository {
    private fun <T> failed() = com.creatora.app.domain.research.Fetched.Failed(AdapterError.NotFound) as com.creatora.app.domain.research.Fetched<T>
    var videos: com.creatora.app.domain.research.Fetched<List<VideoStats>> = com.creatora.app.domain.research.Fetched.Ok(emptyList(), 0, false)
    val videosByChannel = mutableMapOf<String, com.creatora.app.domain.research.Fetched<List<VideoStats>>>()
    var info: com.creatora.app.domain.research.Fetched<ChannelPreview> = failed()
    var discoverResult: com.creatora.app.domain.research.Fetched<List<ChannelPreview>> = com.creatora.app.domain.research.Fetched.Ok(emptyList(), 0, false)
    var keywordResult: com.creatora.app.domain.research.Fetched<com.creatora.app.domain.research.KeywordSearch> = failed()
    var commentsResult: com.creatora.app.domain.research.Fetched<List<String>> = failed()
    var lookupResult: AdapterOutcome<ChannelPreview> = AdapterOutcome.Failed(AdapterError.NotFound)
    var analytics: VideoAnalytics? = null
    val peeked = mutableMapOf<String, List<VideoStats>>()
    val calls = mutableListOf<String>()

    override suspend fun channelVideos(uid: String, channelId: String, force: Boolean): com.creatora.app.domain.research.Fetched<List<VideoStats>> {
        calls += "videos:$channelId:$force"
        return videosByChannel[channelId] ?: videos
    }

    override suspend fun peekChannelVideos(uid: String, channelId: String): List<VideoStats>? = peeked[channelId]

    override suspend fun channelInfo(uid: String, channelId: String, force: Boolean): com.creatora.app.domain.research.Fetched<ChannelPreview> {
        calls += "info:$channelId:$force"
        return info
    }

    override suspend fun discover(uid: String, niche: String, sources: List<String>, force: Boolean): com.creatora.app.domain.research.Fetched<List<ChannelPreview>> {
        calls += "discover:$niche:${sources.joinToString(",")}:$force"
        return discoverResult
    }

    override suspend fun keyword(uid: String, query: String, force: Boolean): com.creatora.app.domain.research.Fetched<com.creatora.app.domain.research.KeywordSearch> {
        calls += "keyword:$query:$force"
        return keywordResult
    }

    override suspend fun comments(uid: String, videoId: String, force: Boolean): com.creatora.app.domain.research.Fetched<List<String>> {
        calls += "comments:$videoId"
        return commentsResult
    }

    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        calls += "lookup:${ref.handleOrId}"
        return lookupResult
    }

    override suspend fun ownAnalytics(channelId: String, videoId: String): VideoAnalytics? = analytics
}

class FakeCompetitorRepository : com.creatora.app.domain.repository.CompetitorRepository {
    val list = MutableStateFlow<List<com.creatora.app.domain.repository.Competitor>>(emptyList())
    val snapshotsByChannel = mutableMapOf<String, List<com.creatora.app.domain.research.CompetitorSnapshot>>()
    override fun observe(uid: String): Flow<List<com.creatora.app.domain.repository.Competitor>> = list
    override suspend fun add(uid: String, competitor: com.creatora.app.domain.repository.Competitor) {
        list.value = list.value.filterNot { it.channelId == competitor.channelId } + competitor
    }

    override suspend fun remove(uid: String, channelId: String) { list.value = list.value.filterNot { it.channelId == channelId } }
    override suspend fun snapshots(uid: String, channelId: String) = snapshotsByChannel[channelId].orEmpty()
    override suspend fun addSnapshot(uid: String, channelId: String, snapshot: com.creatora.app.domain.research.CompetitorSnapshot) {
        snapshotsByChannel[channelId] = snapshotsByChannel[channelId].orEmpty().filterNot { it.day == snapshot.day } + snapshot
    }
}

class FakeSearchLog : com.creatora.app.domain.repository.SearchLogRepository {
    val queries = mutableListOf<String>()
    override suspend fun record(uid: String, query: String, at: Long) { queries += query }
}


class FakeDailyUsage : com.creatora.app.domain.repository.DailyUsageRepository {
    val byDay = MutableStateFlow<Map<String, com.creatora.app.domain.repository.DailyUsage>>(emptyMap())
    override fun observe(uid: String, day: String): Flow<com.creatora.app.domain.repository.DailyUsage> = byDay.map { it[day] ?: com.creatora.app.domain.repository.DailyUsage() }
    override suspend fun add(uid: String, day: String, videos: Int, aiImages: Int) {
        val now = byDay.value[day] ?: com.creatora.app.domain.repository.DailyUsage()
        byDay.value = byDay.value + (day to now.copy(videos = now.videos + videos, aiImages = now.aiImages + aiImages))
    }
}

class FakeErrorReporter : com.creatora.app.domain.repository.ErrorReporter {
    val recorded = mutableListOf<Throwable>()
    override fun record(error: Throwable) { recorded += error }
}
