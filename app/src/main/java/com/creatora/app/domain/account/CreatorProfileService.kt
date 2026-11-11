package com.creatora.app.domain.account

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface DeriveResult {
    data class Updated(val profile: CreatorProfile) : DeriveResult
    /** No connected account has text to learn from. */
    data object NothingConnected : DeriveResult
    /** The channel text is too thin to say what the creator makes; nothing is stored. */
    data object NotEnoughData : DeriveResult
    data class AiFailed(val errors: List<AiError>) : DeriveResult
}

/** Works out and stores the creator profile from the user's connected accounts. No questions asked. */
class CreatorProfileService @Inject constructor(
    private val registry: PlatformRegistry,
    private val accounts: ConnectedAccountRepository,
    private val users: UserRepository,
    private val ai: AiTasks,
) {
    suspend fun deriveFor(uid: String): DeriveResult {
        val connected = accounts.observe(uid).first().filter { it.platform.support == PlatformSupport.FULL }
        if (connected.isEmpty()) return DeriveResult.NothingConnected
        val previews = connected.map { ChannelPreview(it.platform, it.id, it.title, description = it.description) }
        val videos = connected.flatMap { account ->
            (registry.adapter(account.platform).recentVideos(account.id, VIDEOS) as? AdapterOutcome.Ok)?.value.orEmpty()
        }
        return when (val outcome = ai.creatorProfile(profileSamples(previews, videos))) {
            is AiOutcome.Failed -> DeriveResult.AiFailed(outcome.errors)
            is AiOutcome.Ok -> outcome.value?.let {
                users.setCreatorProfile(uid, it)
                DeriveResult.Updated(it)
            } ?: DeriveResult.NotEnoughData
        }
    }

    private companion object {
        const val VIDEOS = 10
    }
}
