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
    override suspend fun increment(uid: String, monthKey: String) {
        counts.value = counts.value + ("$uid/$monthKey" to ((counts.value["$uid/$monthKey"] ?: 0) + 1))
    }

    fun set(uid: String, monthKey: String, value: Int) {
        counts.value = counts.value + ("$uid/$monthKey" to value)
    }
}

class FakeConnectedAccountRepository : ConnectedAccountRepository {
    val byUser = MutableStateFlow<Map<String, List<ConnectedAccount>>>(emptyMap())
    override fun observe(uid: String): Flow<List<ConnectedAccount>> = byUser.map { it[uid].orEmpty() }
    override suspend fun save(uid: String, account: ConnectedAccount) {
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

    override suspend fun ownChannels() = own
    override suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview> {
        lookups += ref
        return lookupResult
    }

    override suspend fun recentVideos(channelId: String, limit: Int) = videos
    override suspend fun analytics(channelId: String, videoIds: List<String>) = analyticsResult
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
