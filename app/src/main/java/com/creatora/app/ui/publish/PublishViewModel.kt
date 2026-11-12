package com.creatora.app.ui.publish

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.data.publish.PublishWork
import com.creatora.app.data.youtube.YouTubeUploader
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.Limits
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.ChannelOverviewLoader
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.publish.BestTime
import com.creatora.app.domain.publish.PlannedPost
import com.creatora.app.domain.publish.PostStatus
import com.creatora.app.domain.publish.PubFix
import com.creatora.app.domain.publish.PubItem
import com.creatora.app.domain.publish.PublishDraft
import com.creatora.app.domain.publish.PublishGate
import com.creatora.app.domain.publish.Route
import com.creatora.app.domain.publish.TitleScore
import com.creatora.app.domain.publish.bestTime
import com.creatora.app.domain.publish.canPublish
import com.creatora.app.domain.publish.planPosts
import com.creatora.app.domain.publish.publishChecks
import com.creatora.app.domain.publish.publishGate
import com.creatora.app.domain.publish.scoreTitle
import com.creatora.app.domain.publish.suggestedAiAnswer
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.DailyUsageRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Publish button stands. */
sealed interface PublishPhase {
    data object Idle : PublishPhase
    data object Working : PublishPhase
    /** Posts were created; [shares] are the share-sheet ones the screen must open now. */
    data class Started(val count: Int) : PublishPhase
    data class Failed(val reason: PublishProblem) : PublishPhase
}

enum class PublishProblem { NOT_SIGNED_IN, NOTHING_CHOSEN, PLAN_DESTINATIONS, PLAN_DAILY, CHECKS, SAVE_FAILED, UPLOAD_PERMISSION, PAST_TIME }

/** Title options the AI wrote, each with Creatora's score. */
data class TitleOption(val title: String, val score: TitleScore)

data class PublishAiState(val busy: Boolean = false, val errors: List<AiError> = emptyList(), val options: List<TitleOption> = emptyList())

/** A share-sheet post the screen opens (the user's own tap on Publish started it). */
data class ShareRequest(val postId: String, val platform: ConnectPlatform, val caption: String, val videoUri: String?)

data class PublishUiState(
    val project: Project? = null,
    val accounts: List<ConnectedAccount> = emptyList(),
    val selected: Set<String> = emptySet(),
    val title: String = "",
    val description: String = "",
    val hashtags: String = "",
    val tags: String = "",
    val aiContent: Boolean? = null,
    val madeForKids: Boolean? = null,
    val checks: List<PubItem> = emptyList(),
    val canPublish: Boolean = false,
    val limits: Limits = PlanLimits.of(Plan.FREE),
    val gate: PublishGate = PublishGate.NoDestination,
    val scheduledAt: Long? = null,
    val bestTime: BestTime? = null,
    val bestAtMillis: Long? = null,
    val makePublic: Boolean = false,
    val ai: PublishAiState = PublishAiState(),
    val phase: PublishPhase = PublishPhase.Idle,
    val suggestAi: Boolean = false,
)

private data class Edits(
    val title: String? = null, val description: String? = null, val hashtags: String? = null, val tags: String? = null,
    val aiContent: Boolean? = null, val madeForKids: Boolean? = null, val aiAsked: Boolean = false, val kidsAsked: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PublishViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    private val accounts: ConnectedAccountRepository,
    private val auth: AuthService,
    private val plans: PlanResolver,
    private val daily: DailyUsageRepository,
    private val posts: PostRepository,
    private val ai: AiTasks,
    private val loader: ChannelOverviewLoader,
    private val registry: PlatformRegistry,
    private val uploader: YouTubeUploader,
    assets: AssetRepository,
    licences: LicenceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L
    private val edits = MutableStateFlow(Edits())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val schedule = MutableStateFlow<Long?>(null)
    private val makePublic = MutableStateFlow(false)
    private val aiState = MutableStateFlow(PublishAiState())
    private val phase = MutableStateFlow<PublishPhase>(PublishPhase.Idle)
    private val plan = MutableStateFlow(Plan.FREE)
    private val best = MutableStateFlow<Pair<BestTime, Long>?>(null)
    private val _shares = MutableSharedFlow<ShareRequest>(extraBufferCapacity = 8)
    val shares: SharedFlow<ShareRequest> = _shares

    private val userFlow = auth.user
    private val accountList = userFlow.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else accounts.observe(user.uid) }
    private val usage = userFlow.flatMapLatest { user -> if (user == null) flowOf(com.creatora.app.domain.repository.DailyUsage()) else daily.observe(user.uid, dayKey(System.currentTimeMillis())) }
    /** Pictures of this project without a licence entry, and whether any was made by AI (so the AI-content question can be pre-suggested). */
    private val footage = combine(assets.observeAll(), licences.observeAll()) { assetList, licenceList ->
        val licensed = licenceList.mapTo(mutableSetOf()) { it.assetId }
        val mine = assetList.filter { it.projectId == projectId }
        mine.count { it.id !in licensed } to licenceList.any { entry -> entry.licence == "AI generated" && mine.any { it.id == entry.assetId } }
    }

    init {
        viewModelScope.launch { userFlow.first()?.let { plan.value = plans.plan(it.uid) } }
        // Edits are saved a moment after the last keystroke.
        viewModelScope.launch {
            edits.drop(1).debounce(SAVE_DELAY_MILLIS).collect { saveEdits() }
        }
        viewModelScope.launch { loadBestTime() }
    }

    val uiState: StateFlow<PublishUiState> = combine(
        projects.observe(projectId), accountList, combine(edits, selected, ::Pair), combine(schedule, makePublic, best, ::Triple),
        combine(aiState, phase, plan, usage, footage) { a, p, pl, u, f -> Five(a, p, pl, u, f.first, f.second) },
    ) { project, accountItems, (edit, chosen), (scheduledAt, makePub, bestPair), rest ->
        val limits = PlanLimits.of(rest.plan)
        val title = edit.title ?: project?.let { it.publishTitle.ifBlank { it.title } }.orEmpty()
        val description = edit.description ?: project?.publishDescription.orEmpty()
        val hashtags = edit.hashtags ?: project?.publishHashtags.orEmpty().joinToString(" ")
        val tags = edit.tags ?: project?.publishTags.orEmpty().joinToString(", ")
        val aiContent = if (edit.aiAsked) edit.aiContent else project?.aiContent
        val kids = if (edit.kidsAsked) edit.madeForKids else project?.madeForKids
        val draft = PublishDraft(
            rendered = project?.renderUri != null, title = title, description = description, tags = splitTags(tags),
            hasThumbnail = project?.thumbnailUri != null, aiContent = aiContent, madeForKids = kids, unlicensedAssets = rest.unlicensed, usedAi = rest.usedAi,
        )
        val checks = publishChecks(draft)
        PublishUiState(
            project = project, accounts = accountItems, selected = chosen.filter { key -> accountItems.any { it.key == key } }.toSet(),
            title = title, description = description, hashtags = hashtags, tags = tags, aiContent = aiContent, madeForKids = kids,
            checks = checks, canPublish = canPublish(checks), limits = limits,
            gate = publishGate(limits, chosen.count { key -> accountItems.any { it.key == key } }, rest.usage.publishes),
            scheduledAt = scheduledAt, bestTime = bestPair?.first, bestAtMillis = bestPair?.second, makePublic = makePub,
            ai = rest.ai, phase = rest.phase, suggestAi = suggestedAiAnswer(draft),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PublishUiState())

    private data class Five(val ai: PublishAiState, val phase: PublishPhase, val plan: Plan, val usage: com.creatora.app.domain.repository.DailyUsage, val unlicensed: Int, val usedAi: Boolean)

    private fun splitTags(text: String) = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    private fun splitHashtags(text: String) = text.split(' ', ',', '\n').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }

    private suspend fun saveEdits() {
        val state = uiState.value
        val project = state.project ?: return
        projects.save(
            project.copy(
                publishTitle = state.title, publishDescription = state.description, publishHashtags = splitHashtags(state.hashtags),
                publishTags = splitTags(state.tags), aiContent = state.aiContent, madeForKids = state.madeForKids, updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---- destinations and when
    fun toggle(account: ConnectedAccount) {
        selected.update { if (account.key in it) it - account.key else it + account.key }
        phase.value = PublishPhase.Idle
    }

    fun setScheduledAt(millis: Long?) { schedule.value = millis; phase.value = PublishPhase.Idle }
    fun useBestTime() { best.value?.second?.let { schedule.value = it } }
    fun setPublic(on: Boolean) { makePublic.value = on }

    private suspend fun loadBestTime() {
        val account = accountList.first().firstOrNull { it.platform == ConnectPlatform.YOUTUBE } ?: return
        val overview = loader.overview(account)
        val today = LocalDate.now()
        val series = (registry.adapter(ConnectPlatform.YOUTUBE).dailyViews(account.id, today.minusDays(90), today) as? AdapterOutcome.Ok)?.value
        val zone = ZoneId.systemDefault()
        val result = bestTime(overview.videos, series, today.minusDays(1), projects.observe(projectId).first()?.format, zone)
        best.value = result to result.nextAt(System.currentTimeMillis(), zone)
    }

    // ---- fields
    fun setTitle(value: String) = edits.update { it.copy(title = value) }
    fun setDescription(value: String) = edits.update { it.copy(description = value) }
    fun setHashtags(value: String) = edits.update { it.copy(hashtags = value) }
    fun setTags(value: String) = edits.update { it.copy(tags = value) }
    fun answerAi(yes: Boolean) = edits.update { it.copy(aiContent = yes, aiAsked = true) }
    fun answerKids(yes: Boolean) = edits.update { it.copy(madeForKids = yes, kidsAsked = true) }

    /** One AI action: five title options with scores, a description, hashtags and tags. The user picks and edits; nothing is applied silently. */
    fun regenerate() {
        val project = uiState.value.project ?: return
        if (aiState.value.busy) return
        aiState.value = PublishAiState(busy = true)
        viewModelScope.launch {
            when (val outcome = ai.publishText(project.publishTitle.ifBlank { project.title }, project.script)) {
                is AiOutcome.Ok -> {
                    val keyword = com.creatora.app.domain.score.mainWord(project.title)
                    val options = outcome.value.titles.map { TitleOption(it, scoreTitle(it, keyword)) }.sortedByDescending { it.score.points }
                    edits.update {
                        it.copy(
                            description = outcome.value.description.ifBlank { null } ?: it.description,
                            hashtags = outcome.value.hashtags.joinToString(" ").ifBlank { null } ?: it.hashtags,
                            tags = outcome.value.hashtags.joinToString(", ") { tag -> tag.removePrefix("#") }.ifBlank { null } ?: it.tags,
                        )
                    }
                    aiState.value = PublishAiState(options = options)
                }
                is AiOutcome.Failed -> aiState.value = PublishAiState(errors = outcome.errors)
            }
        }
    }

    fun pickTitle(option: TitleOption) = edits.update { it.copy(title = option.title) }

    // ---- publish
    /** The user's tap on Publish or Schedule. Nothing is posted before this. */
    fun publish() {
        val state = uiState.value
        val project = state.project ?: return
        viewModelScope.launch {
            val user = userFlow.first()
            if (user == null) return@launch fail(PublishProblem.NOT_SIGNED_IN)
            val chosen = state.accounts.filter { it.key in state.selected }
            when (val gate = state.gate) {
                PublishGate.Open -> Unit
                PublishGate.NoDestination -> return@launch fail(PublishProblem.NOTHING_CHOSEN)
                is PublishGate.TooManyDestinations -> return@launch fail(PublishProblem.PLAN_DESTINATIONS)
                is PublishGate.DailyLimit -> return@launch fail(PublishProblem.PLAN_DAILY)
            }
            if (!state.canPublish) return@launch fail(PublishProblem.CHECKS)
            val now = System.currentTimeMillis()
            val at = state.scheduledAt
            if (at != null && at <= now) return@launch fail(PublishProblem.PAST_TIME)
            phase.value = PublishPhase.Working
            saveEdits()
            // YouTube needs the upload permission, which Google asks for on screen; ask before anything is created.
            if (chosen.any { it.platform == ConnectPlatform.YOUTUBE && it.link == null } && !uploader.ensureUploadAccess()) return@launch fail(PublishProblem.UPLOAD_PERMISSION)

            val planned = planPosts(UUID.randomUUID().toString(), project.id, state.title.ifBlank { project.title }, chosen, at, state.makePublic, now)
            val saved = runCatching { planned.forEach { posts.save(user.uid, it.post) } }
            if (saved.isFailure) return@launch fail(PublishProblem.SAVE_FAILED)
            // A publish counts once when the user starts it; a retried upload does not count twice.
            runCatching { daily.add(user.uid, dayKey(now), publishes = 1) }
            launchWork(user.uid, planned, project, state)
            phase.value = PublishPhase.Started(planned.size)
        }
    }

    private fun launchWork(uid: String, planned: List<PlannedPost>, project: Project, state: PublishUiState) {
        val caption = com.creatora.app.domain.publish.captionWithTags(state.description, splitHashtags(state.hashtags))
        planned.forEach { (post, route) ->
            when {
                route == Route.API -> PublishWork.upload(context, uid, post.id)
                post.status == PostStatus.SCHEDULED -> PublishWork.remind(context, uid, post.id, post.scheduledAt ?: 0L)
                else -> _shares.tryEmit(ShareRequest(post.id, post.platform, caption, project.renderUri))
            }
        }
    }

    /** The user finished sharing a share-sheet post: it is saved as Shared, which Creatora cannot track further. */
    fun markShared(postId: String) {
        viewModelScope.launch {
            val user = userFlow.first() ?: return@launch
            posts.get(user.uid, postId)?.let { posts.save(user.uid, it.copy(status = PostStatus.SHARED, publishedAt = System.currentTimeMillis())) }
        }
    }

    fun dismissPhase() { phase.value = PublishPhase.Idle }

    private fun fail(problem: PublishProblem) { phase.value = PublishPhase.Failed(problem) }

    /** Which field a failing check's "Fix it" goes to, so the screen can scroll to it. */
    fun fixTarget(item: PubItem): PubFix? = item.fix

    companion object {
        const val ARG_PROJECT_ID = "projectId"
        private const val SAVE_DELAY_MILLIS = 600L
    }
}
