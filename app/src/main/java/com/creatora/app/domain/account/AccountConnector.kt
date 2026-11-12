package com.creatora.app.domain.account

import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What looking up a pasted link or id found. */
sealed interface LookupResult {
    /** A channel with a preview card to confirm. */
    data class Found(val preview: ChannelPreview) : LookupResult
    /** A link-only platform: only the link is saved, nothing is fetched. */
    data class LinkOnly(val ref: PlatformRef) : LookupResult
    /** The text is not a link or id Creatora understands. */
    data object Unrecognised : LookupResult
    data class Failed(val error: AdapterError) : LookupResult
}

sealed interface ConnectOutcome {
    data object Connected : ConnectOutcome
    data object AlreadyConnected : ConnectOutcome
    /** The plan allows [max] accounts. */
    data class PlanLimit(val max: Int) : ConnectOutcome
}

/** Connects accounts for the signed-in user, respecting the plan's account limit. */
class AccountConnector @Inject constructor(
    private val registry: PlatformRegistry,
    private val accounts: ConnectedAccountRepository,
    private val users: UserRepository,
    private val billing: BillingRepository,
    private val logins: com.creatora.app.domain.platform.LoginRegistry = com.creatora.app.domain.platform.LoginRegistry(emptyList()),
) {
    /** The YouTube channels of the account the user just authorised. */
    suspend fun ownChannels(): AdapterOutcome<List<ChannelPreview>> = registry.adapter(ConnectPlatform.YOUTUBE).ownChannels()

    /** Signs in to Instagram, Facebook or TikTok with the platform's own screen. YouTube uses [ownChannels] (Google sign-in). */
    suspend fun login(platform: ConnectPlatform): com.creatora.app.domain.platform.LoginOutcome =
        logins.login(platform)?.signIn() ?: com.creatora.app.domain.platform.LoginOutcome.Failed("There is no sign-in for this platform")

    /** Understands [input] (a link, id or handle) for the platform the user picked, and fetches a preview where possible. */
    suspend fun lookup(input: String, chosen: ConnectPlatform?): LookupResult {
        val ref = parseReference(input, chosen) ?: return LookupResult.Unrecognised
        return when (ref.platform.support) {
            PlatformSupport.LINK_ONLY -> LookupResult.LinkOnly(ref)
            PlatformSupport.FULL -> when (val result = registry.adapter(ref.platform).lookup(ref)) {
                is AdapterOutcome.Ok -> LookupResult.Found(result.value)
                is AdapterOutcome.Failed -> LookupResult.Failed(result.error)
            }
        }
    }

    suspend fun connect(uid: String, account: ConnectedAccount): ConnectOutcome {
        val current = accounts.observe(uid).first()
        if (current.any { it.key == account.key }) return ConnectOutcome.AlreadyConnected
        val plan = planOf(uid)
        if (current.size >= plan.maxAccounts) return ConnectOutcome.PlanLimit(plan.maxAccounts)
        accounts.save(uid, account)
        return ConnectOutcome.Connected
    }

    private suspend fun planOf(uid: String): Plan =
        if (billing.state.first().hasPro) Plan.PRO else users.observe(uid).first()?.plan ?: Plan.FREE
}

/** The text sent to the AI to work out what the creator makes: channel descriptions and recent video titles. */
fun profileSamples(previews: List<ChannelPreview>, videos: List<VideoStats>): List<String> =
    previews.flatMap { listOf("${it.title}: ${it.description}".trim()) }.filter { it.length > 2 } +
        videos.map { it.title }.filter { it.isNotBlank() }
